package laughing.man.commits.filter;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartMapper;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.computed.internal.ComputedFieldSupport;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.RawQueryRow;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.internal.builder.FilterQueryBuilder;
import laughing.man.commits.internal.builder.QueryMetric;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import laughing.man.commits.telemetry.internal.QueryTelemetrySupport;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.ReflectionUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateful filter executor.
 * <p>
 * Query execution methods run against per-invocation snapshots. The
 * {@link #join()} method updates the internal pipeline state atomically.
 */
public class FilterImpl implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(FilterImpl.class);

    private volatile FilterQueryBuilder builderState;
    private volatile FilterExecutionPlanCacheKey planCacheKey;
    private volatile long planCacheKeyVersion = Long.MIN_VALUE;
    private volatile FastArrayQuerySupport.FastArrayState fastArrayState;
    private volatile FastStatsQuerySupport.FastStatsState fastStatsState;
    private volatile SourceIndexCache sourceIndexCache;
    private volatile ReusableFastJoinState reusableFastJoinState;

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "FilterImpl intentionally keeps the live builder snapshot that drives query execution."
    )
    public FilterImpl(FilterQueryBuilder query) {
        this.builderState = query;
    }

    /**
     * Executes the configured query and returns grouped results.
     */
    @Override
    public <T> Map<String, List<T>> filterGroups(Class<T> cls) {
        materializeFastRowsIfPresent();
        FilterQueryBuilder executionBuilder = builderState;
        FilterCore core = new FilterCore(executionBuilder);
        List<QueryRow> rows = core.getBuilder().getRows();
        try {
            if (rows != null && !rows.isEmpty()) {
                FilterStageResult stage = runFilterStage(
                        executionBuilder,
                        core,
                        true,
                        true,
                        false,
                        QueryTelemetrySupport.metadata("grouped", true)
                );
                // Project configured display fields.
                List<QueryRow> displayFields = core.filterDisplayFields(stage.filteredRows(), stage.plan());
                // Group rows using configured grouping fields.
                Map<String, List<QueryRow>> groupedClasses =
                        core.groupByFields(stage.filteredRows(), displayFields, stage.plan());
                // Convert grouped rows back to caller type.
                Map<String, List<T>> map = new HashMap<>();
                for (Map.Entry<String, List<QueryRow>> entry : groupedClasses.entrySet()) {
                    List<T> list = ReflectionUtil.toClassList(cls, entry.getValue());
                    map.put(entry.getKey(), list);
                }

                return map;
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to compare core.getBuilder().getRows()[" + rows + "] ", e);
            throw new IllegalStateException("Failed to run grouped filter", e);
        }
        return new HashMap<>();
    }

    /**
     * Executes the configured query and returns flat results.
     */
    @Override
    public <T> List<T> filter(Class<T> cls) {
        return filter(null, cls);
    }

    /**
     * Executes the configured query and returns sorted flat results.
     */
    @Override
    public <T> List<T> filter(Sort sortMethod, Class<T> cls) {
        FilterQueryBuilder executionBuilder = builderState;
        boolean hasWindowOrQualify = hasWindowOrQualify(executionBuilder);
        return resolveExecutionMaterialization(executionBuilder, sortMethod, cls, hasWindowOrQualify)
                .toTypedRows(cls);
    }

    @Override
    public <T> Iterator<T> iterator(Class<T> cls) {
        return iterator(null, cls);
    }

    @Override
    public <T> Stream<T> stream(Class<T> cls) {
        return stream(null, cls);
    }

    @Override
    public <T> Iterator<T> iterator(Sort sortMethod, Class<T> cls) {
        return stream(sortMethod, cls).iterator();
    }

    @Override
    public <T> Stream<T> stream(Sort sortMethod, Class<T> cls) {
        if (sortMethod == null && FastPojoStreamSupport.isApplicable(builderState)) {
            return FastPojoStreamSupport.stream(builderState, cls);
        }
        return filter(sortMethod, cls).stream();
    }

    /**
     * @param aggregateStage the aggregation telemetry to emit once HAVING has run, or
     *                       {@code null} for the synthetic empty global-aggregate row
     */
    private List<QueryRow> havingOrderAndPage(FilterQueryBuilder executionBuilder,
                                              List<QueryRow> aggregated,
                                              Sort sortMethod,
                                              Integer paginationWindow,
                                              AggregateStage aggregateStage) {
        FilterQueryBuilder aggregateBuilder = executionBuilder.snapshotForRows(aggregated);
        FilterCore aggregateCore = new FilterCore(aggregateBuilder);
        FilterExecutionPlan aggregatePlan = aggregateCore.buildExecutionPlan();
        boolean havingApplied = hasHavingPredicates(executionBuilder);
        List<QueryRow> kept = havingApplied
                ? aggregateCore.filterHavingFields(aggregated, aggregatePlan)
                : aggregated;
        if (aggregateStage != null) {
            emitStage(executionBuilder,
                    QueryTelemetryStage.AGGREGATE,
                    aggregateStage.startedNanos(),
                    aggregateStage.inputRows(),
                    aggregated.size(),
                    QueryTelemetrySupport.metadata("havingApplied", havingApplied, "rowsAfterHaving", kept.size()));
        }
        if (hasWindowOrQualify(executionBuilder)) {
            // Windows and QUALIFY run over grouped rows after HAVING (SQL logical order), so
            // ORDER BY is planned against the windowed schema.
            kept = applyWindowsAndQualify(executionBuilder, kept);
            aggregateCore = new FilterCore(executionBuilder.snapshotForRows(kept));
            aggregatePlan = aggregateCore.buildExecutionPlan();
        }
        // ORDER BY for stats queries is evaluated on post-aggregation rows.
        long orderStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
        List<QueryRow> ordered = aggregateCore.orderByFields(kept, sortMethod, aggregatePlan, paginationWindow);
        emitOrderStage(executionBuilder, orderStarted, kept.size(), ordered.size());
        List<QueryRow> output = executionBuilder.isDistinctRows() ? DistinctRowSupport.distinct(ordered) : ordered;
        return CollectionUtil.applyOffsetAndLimit(output, executionBuilder.getOffset(), executionBuilder.getLimit());
    }

    /**
     * When aggregation started and how many filtered rows it grouped.
     */
    private record AggregateStage(long startedNanos, int inputRows) {
    }

    private static List<QueryRow> applyWindowsAndQualify(FilterQueryBuilder builder, List<QueryRow> rows) {
        List<QueryRow> windowed = builder.getWindows().isEmpty()
                ? rows
                : FluentWindowSupport.apply(rows, builder.getWindows());
        return FluentQualifySupport.hasPredicates(builder)
                ? FluentQualifySupport.apply(builder, windowed)
                : windowed;
    }

    private static boolean isGlobalAggregate(FilterQueryBuilder builder) {
        return !builder.getMetrics().isEmpty() && builder.getGroupFields().isEmpty();
    }

    private static QueryRow emptyGlobalAggregateRow(FilterQueryBuilder builder) {
        List<QueryMetric> metrics = builder.getMetrics();
        Object[] values = new Object[metrics.size()];
        ArrayList<String> schema = new ArrayList<>(metrics.size());
        for (int i = 0; i < metrics.size(); i++) {
            QueryMetric metric = metrics.get(i);
            values[i] = metric.getMetric() == Metric.COUNT || metric.getMetric() == Metric.COUNT_DISTINCT ? 0L : null;
            schema.add(metric.getAlias());
        }
        return new RawQueryRow(values, schema);
    }

    private List<QueryRow> filterRows(Sort sortMethod) {
        FilterQueryBuilder executionBuilder = builderState;
        boolean hasWindowOrQualify = hasWindowOrQualify(executionBuilder);
        if (hasWindowOrQualify) {
            fastStatsState = null;
        }
        FastStatsQuerySupport.FastStatsState statsState = resolveFastStatsState(executionBuilder, !hasWindowOrQualify);
        if (statsState != null) {
            return FastStatsQuerySupport.toQueryRows(statsState);
        }

        List<QueryRow> results = new ArrayList<>();

        // Fast path: for POJO-source simple filter queries, materialize only matching rows.
        boolean fastPojoFilterApplied = false;
        if (FastPojoFilterSupport.isApplicable(executionBuilder)) {
            List<?> indexedCandidates = FastPojoIndexSupport.indexedCandidates(
                    executionBuilder,
                    (fieldName, value) -> lookupIndexedSourceRows(executionBuilder, fieldName, value)
            );
            List<QueryRow> fastRows = FastPojoFilterSupport.tryFilterRows(executionBuilder, indexedCandidates);
            if (fastRows != null) {
                executionBuilder.setMaterializedRows(fastRows, executionBuilder.getSourceFieldTypesForExecution());
                fastPojoFilterApplied = true;
            }
        }

        FilterCore core = new FilterCore(executionBuilder);
        try {
            validateWindowShape(executionBuilder);
            FluentQualifySupport.validate(executionBuilder);
            boolean distinct = executionBuilder.isDistinctRows();
            // DISTINCT needs every ordered row before paging, so no top-N sort window.
            Integer paginationWindow = distinct
                    ? null
                    : CollectionUtil.pagingWindow(executionBuilder.getOffset(), executionBuilder.getLimit());
            if (core.getBuilder().getRows() != null && !core.getBuilder().getRows().isEmpty()) {
                FilterStageResult stage = runFilterStage(
                        executionBuilder,
                        core,
                        false,
                        !fastPojoFilterApplied,
                        fastPojoFilterApplied,
                        null
                );
                FilterExecutionPlan plan = stage.plan();
                List<QueryRow> filterClasses = stage.filteredRows();
                if (!executionBuilder.getMetrics().isEmpty()) {
                    // Compute aggregate metrics (global or grouped based on GROUP BY config).
                    long aggregateStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
                    List<QueryRow> aggregated = core.aggregateMetrics(filterClasses, plan);
                    results = havingOrderAndPage(executionBuilder, aggregated, sortMethod, paginationWindow,
                            new AggregateStage(aggregateStarted, filterClasses.size()));
                } else {
                    if (hasHavingPredicates(executionBuilder)) {
                        throw new IllegalStateException("HAVING requires grouped/aggregate query context");
                    }
                    boolean hasWindows = hasWindows(executionBuilder);
                    boolean hasQualify = FluentQualifySupport.hasPredicates(executionBuilder);
                    List<QueryRow> qualifiedRows = applyWindowsAndQualify(executionBuilder, filterClasses);
                    long orderStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
                    List<QueryRow> sortedList;
                    if ((hasWindows || hasQualify) && !executionBuilder.getOrderFields().isEmpty()) {
                        FilterQueryBuilder orderBuilder = executionBuilder.snapshotForRows(qualifiedRows);
                        FilterCore orderCore = new FilterCore(orderBuilder);
                        FilterExecutionPlan orderPlan = orderCore.buildExecutionPlan();
                        sortedList = orderCore.orderByFields(qualifiedRows, sortMethod, orderPlan, paginationWindow);
                    } else if (hasWindows || hasQualify) {
                        sortedList = qualifiedRows;
                    } else {
                        sortedList = core.orderByFields(filterClasses, sortMethod, plan, paginationWindow);
                    }
                    emitOrderStage(executionBuilder, orderStarted, qualifiedRows.size(), sortedList.size());
                    // Apply offset/limit before display projection to avoid projecting rows that will be
                    // discarded; DISTINCT compares projected rows, so it projects first and pages after.
                    List<QueryRow> limited = distinct
                            ? sortedList
                            : CollectionUtil.applyOffsetAndLimit(
                                    sortedList,
                                    executionBuilder.getOffset(),
                                    executionBuilder.getLimit()
                            );
                    if (hasWindows || hasQualify) {
                        FilterQueryBuilder displayBuilder = executionBuilder.snapshotForRows(limited);
                        FilterCore displayCore = new FilterCore(displayBuilder);
                        FilterExecutionPlan displayPlan = displayCore.buildExecutionPlan();
                        results = displayCore.filterDisplayFields(limited, displayPlan);
                    } else {
                        results = core.filterDisplayFields(limited, plan);
                    }
                    if (distinct) {
                        results = CollectionUtil.applyOffsetAndLimit(
                                DistinctRowSupport.distinct(results),
                                executionBuilder.getOffset(),
                                executionBuilder.getLimit()
                        );
                    }
                }
            } else if (isGlobalAggregate(executionBuilder)) {
                // SQL semantics: an aggregate without GROUP BY yields one row even over no input,
                // matching what a filter that removes every row already produces.
                results = havingOrderAndPage(
                        executionBuilder, List.of(emptyGlobalAggregateRow(executionBuilder)), sortMethod, paginationWindow,
                        null);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (ArithmeticException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to compare core.getBuilder().getRows()[" + core.getBuilder().getRows() + "] ", e);
            throw new IllegalStateException("Failed to run filter", e);
        }
        return results;
    }

    @Override
    public <T> ChartData chart(Class<T> cls, ChartSpec spec) {
        return chart(null, cls, spec);
    }

    @Override
    public <T> ChartData chart(Sort sortMethod, Class<T> cls, ChartSpec spec) {
        FilterQueryBuilder executionBuilder = builderState;
        boolean hasWindowOrQualify = hasWindowOrQualify(executionBuilder);
        FluentExecutionMaterialization<T> execution;
        long chartStarted;
        if (hasWindowOrQualify) {
            execution = resolveExecutionMaterialization(executionBuilder, sortMethod, cls, true);
            chartStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
        } else {
            chartStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
            execution = resolveExecutionMaterialization(executionBuilder, sortMethod, cls, false);
        }
        ChartData chart = execution.toChartData(spec);
        int rowCount = execution.rowCount();
        emitStage(executionBuilder,
                QueryTelemetryStage.CHART,
                chartStarted,
                rowCount,
                rowCount,
                QueryTelemetrySupport.metadata(
                        "chartType", chart.getType(),
                        "labelCount", chart.getLabels() == null ? 0 : chart.getLabels().size(),
                        "datasetCount", chart.getDatasets() == null ? 0 : chart.getDatasets().size()
                ));
        return chart;
    }

    private FastStatsQuerySupport.FastStatsState resolveFastStatsState(FilterQueryBuilder executionBuilder,
                                                                       boolean allowBuild) {
        FastStatsQuerySupport.FastStatsState statsState = fastStatsState;
        if (statsState == null && allowBuild) {
            statsState = FastStatsQuerySupport.tryBuildState(executionBuilder, planCacheKey(executionBuilder));
            fastStatsState = statsState;
        }
        return statsState;
    }

    private <T> FluentExecutionMaterialization<T> resolveExecutionMaterialization(FilterQueryBuilder executionBuilder,
                                                                                  Sort sortMethod,
                                                                                  Class<T> projectionClass,
                                                                                  boolean hasWindowOrQualify) {
        if (executionBuilder.isFilterAlwaysFalse()) {
            return FluentExecutionMaterialization.queryRows(new ArrayList<>());
        }
        if (hasWindowOrQualify) {
            return FluentExecutionMaterialization.queryRows(filterRows(sortMethod));
        }
        FastArrayQuerySupport.FastArrayState fastState = fastArrayState;
        if (fastState != null) {
            return FluentExecutionMaterialization.typedRows(
                    FastArrayQuerySupport.filter(executionBuilder, fastState, sortMethod, projectionClass)
            );
        }
        FastStatsQuerySupport.FastStatsState statsState = resolveFastStatsState(executionBuilder, true);
        if (statsState != null) {
            return FluentExecutionMaterialization.arrayRows(
                    statsState.rows(),
                    statsState.schemaFields()
            );
        }
        return FluentExecutionMaterialization.queryRows(filterRows(sortMethod));
    }

    private FilterStageResult runFilterStage(FilterQueryBuilder executionBuilder,
                                             FilterCore core,
                                             boolean groupedQuery,
                                             boolean allowSchemaCleaning,
                                             boolean skipFieldFiltering,
                                             Map<String, Object> metadata) {
        FilterExecutionPlan plan = resolveExecutionPlan(core, executionBuilder, groupedQuery);
        if (allowSchemaCleaning && executionBuilder.requiresRuntimeSchemaCleaning()) {
            core.clean(core.getBuilder().getRows().get(0));
        }
        List<QueryRow> distinctRows = core.filterDistinctFields(plan);
        long filterStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
        List<QueryRow> filteredRows = skipFieldFiltering
                ? distinctRows
                : core.filterFields(distinctRows, plan);
        emitStage(executionBuilder,
                QueryTelemetryStage.FILTER,
                filterStarted,
                distinctRows.size(),
                filteredRows.size(),
                metadata);
        return new FilterStageResult(plan, filteredRows);
    }

    private FilterExecutionPlan resolveExecutionPlan(FilterCore core,
                                                     FilterQueryBuilder executionBuilder,
                                                     boolean groupedQuery) {
        if (!groupedQuery && !isStatsQuery(executionBuilder)) {
            return core.buildExecutionPlan();
        }
        FilterExecutionPlanCacheKey key = planCacheKey(executionBuilder);
        return executionBuilder.getExecutionPlanCache().getOrBuild(key, core::buildExecutionPlan);
    }

    private boolean isStatsQuery(FilterQueryBuilder builder) {
        return !builder.getMetrics().isEmpty()
                || !builder.getGroupFields().isEmpty()
                || !builder.getTimeBuckets().isEmpty();
    }

    private boolean hasHavingPredicates(FilterQueryBuilder builder) {
        return !builder.getHavingFields().isEmpty()
                || !builder.getHavingAllOfGroups().isEmpty()
                || !builder.getHavingAnyOfGroups().isEmpty();
    }

    private boolean hasWindows(FilterQueryBuilder builder) {
        return builder != null && !builder.getWindows().isEmpty();
    }

    private boolean hasWindowOrQualify(FilterQueryBuilder builder) {
        return builder != null && (hasWindows(builder) || FluentQualifySupport.hasPredicates(builder));
    }

    private void validateWindowShape(FilterQueryBuilder builder) {
        if (!hasWindows(builder)) {
            return;
        }
        if (builder.getMetrics().isEmpty()
                && (!builder.getGroupFields().isEmpty() || !builder.getTimeBuckets().isEmpty())) {
            throw new IllegalArgumentException("Window functions over grouped rows require at least one metric");
        }
    }

    private FilterExecutionPlanCacheKey planCacheKey(FilterQueryBuilder builder) {
        long shapeVersion = builder.getExecutionPlanShapeVersion();
        FilterExecutionPlanCacheKey cached = planCacheKey;
        if (cached != null && planCacheKeyVersion == shapeVersion) {
            return cached;
        }
        FilterExecutionPlanCacheKey computed = FilterExecutionPlanCacheKey.from(builder);
        planCacheKey = computed;
        planCacheKeyVersion = shapeVersion;
        return computed;
    }

    private void emitOrderStage(FilterQueryBuilder builder, long startedNanos, int beforeCount, int afterCount) {
        if (builder.getOrderFields().isEmpty()) {
            return;
        }
        emitStage(builder,
                QueryTelemetryStage.ORDER,
                startedNanos,
                beforeCount,
                afterCount,
                QueryTelemetrySupport.metadata("orderFieldCount", builder.getOrderFields().size()));
    }

    private void emitStage(FilterQueryBuilder builder,
                           QueryTelemetryStage stage,
                           long startedNanos,
                           Integer beforeCount,
                           Integer afterCount,
                           Map<String, Object> metadata) {
        QueryTelemetrySupport.emit(
                builder.getTelemetryListener(),
                stage,
                builder.getTelemetryQueryType(),
                builder.getTelemetrySource(),
                startedNanos,
                beforeCount,
                afterCount,
                metadata
        );
    }

    /**
     * Applies configured joins and stores joined rows as new executor state.
     */
    @Override
    public synchronized Filter join() {
        FilterQueryBuilder executionBuilder = builderState;
        this.fastStatsState = null;
        this.sourceIndexCache = null;
        ReusableFastJoinState cachedFastJoinState = reusableFastJoinState;
        if (cachedFastJoinState != null && cachedFastJoinState.matches(executionBuilder)) {
            executionBuilder.setExecutionSchema(cachedFastJoinState.state().schemaTypes());
            this.fastArrayState = cachedFastJoinState.state();
            return this;
        }
        FastArrayQuerySupport.FastArrayState fastState = FastArrayQuerySupport.tryBuildJoinedState(executionBuilder);
        if (fastState != null) {
            executionBuilder.setExecutionSchema(fastState.schemaTypes());
            this.fastArrayState = fastState;
            this.reusableFastJoinState = new ReusableFastJoinState(
                    executionBuilder.getExecutionPlanShapeVersion(),
                    executionBuilder.getSourceBeansForExecution(),
                    executionBuilder.getJoinSourceBeansForExecution(),
                    fastState
            );
            return this;
        }
        this.reusableFastJoinState = null;
        this.fastArrayState = null;
        FilterCore core = new FilterCore(executionBuilder);
        try {
            List<QueryRow> joinedRows = core.join(executionBuilder.getRows());
            ComputedFieldSupport.materializeRowsInPlace(joinedRows, executionBuilder.getComputedFieldRegistry());
            executionBuilder.setMaterializedRows(joinedRows, executionBuilder.deriveJoinedSourceFieldTypes());
        } catch (Exception e) {
            LOG.error("Failed to join filterList[" + executionBuilder.getRows() + "] ", e);
            throw new IllegalStateException("Failed to apply join pipeline", e);
        }
        return this;
    }

    private void materializeFastRowsIfPresent() {
        FastArrayQuerySupport.FastArrayState fastState = fastArrayState;
        if (fastState == null) {
            FastStatsQuerySupport.FastStatsState statsState = fastStatsState;
            if (statsState == null) {
                return;
            }
            FilterQueryBuilder executionBuilder = builderState;
            executionBuilder.setRows(FastStatsQuerySupport.toQueryRows(statsState));
            fastStatsState = null;
            sourceIndexCache = null;
            reusableFastJoinState = null;
            return;
        }
        FilterQueryBuilder executionBuilder = builderState;
        executionBuilder.setMaterializedRows(
                FastArrayQuerySupport.toQueryRows(fastState),
                fastState.schemaTypes()
        );
        fastArrayState = null;
        sourceIndexCache = null;
        reusableFastJoinState = null;
    }

    private List<?> lookupIndexedSourceRows(FilterQueryBuilder builder, String fieldName, Object value) {
        List<?> sourceBeans = builder.getSourceBeansForExecution();
        if (sourceBeans == null || sourceBeans.isEmpty()) {
            return List.of();
        }
        if (!builder.getSourceFieldTypesForExecution().containsKey(fieldName)) {
            return null;
        }

        SourceIndexCache cache = sourceIndexCache;
        if (cache == null || cache.sourceBeans() != sourceBeans) {
            cache = new SourceIndexCache(sourceBeans);
            sourceIndexCache = cache;
        }
        return cache.lookup(fieldName, value);
    }

    private static final class SourceIndexCache {
        private final List<?> sourceBeans;
        private final HashMap<String, Map<Object, List<?>>> byField = new HashMap<>();
        private final HashMap<String, Boolean> buildFailures = new HashMap<>();

        private SourceIndexCache(List<?> sourceBeans) {
            this.sourceBeans = sourceBeans;
        }

        private List<?> sourceBeans() {
            return sourceBeans;
        }

        private List<?> lookup(String fieldName, Object value) {
            Map<Object, List<?>> index = byField.get(fieldName);
            if (index == null && !Boolean.TRUE.equals(buildFailures.get(fieldName))) {
                index = buildFieldIndex(fieldName);
                if (index == null) {
                    buildFailures.put(fieldName, true);
                } else {
                    byField.put(fieldName, index);
                }
            }
            if (index == null || !FastPojoIndexSupport.isIndexSafeValue(value)) {
                return null;
            }
            if (value != null && !hasKeyType(index, FastPojoIndexSupport.indexKeyType(value))) {
                // e.g. an int field probed with 1L: equals() would miss rows the engine matches.
                return null;
            }
            List<?> matches = index.get(value);
            return matches == null ? List.of() : matches;
        }

        private static boolean hasKeyType(Map<Object, List<?>> index, Class<?> type) {
            for (Object key : index.keySet()) {
                if (key != null) {
                    return FastPojoIndexSupport.indexKeyType(key) == type;
                }
            }
            return true;
        }

        private Map<Object, List<?>> buildFieldIndex(String fieldName) {
            Object firstBean = CollectionUtil.firstNonNull(sourceBeans);
            if (firstBean == null) {
                return new LinkedHashMap<>();
            }
            ReflectionUtil.FlatRowReadPlan readPlan =
                    ReflectionUtil.compileFlatRowReadPlan(firstBean.getClass(), List.of(fieldName));
            if (readPlan.size() != 1) {
                return null;
            }

            LinkedHashMap<Object, List<?>> index = new LinkedHashMap<>();
            Object[] buffer = new Object[1];
            try {
                for (Object bean : sourceBeans) {
                    if (bean == null) {
                        continue;
                    }
                    ReflectionUtil.readFlatRowValues(bean, readPlan, buffer, 0);
                    Object key = buffer[0];
                    List<?> existing = index.get(key);
                    if (existing == null) {
                        ArrayList<Object> rows = new ArrayList<>();
                        rows.add(bean);
                        index.put(key, rows);
                    } else {
                        @SuppressWarnings("unchecked")
                        List<Object> typed = (List<Object>) existing;
                        typed.add(bean);
                    }
                }
            } catch (IllegalAccessException e) {
                return null;
            }
            return index;
        }
    }

    private record ReusableFastJoinState(long shapeVersion,
                                         List<?> sourceBeans,
                                         Map<Integer, List<?>> joinSourceBeans,
                                         FastArrayQuerySupport.FastArrayState state) {

        private boolean matches(FilterQueryBuilder builder) {
            return builder != null
                    && builder.getExecutionPlanShapeVersion() == shapeVersion
                    && builder.getSourceBeansForExecution() == sourceBeans
                    && builder.getJoinSourceBeansForExecution() == joinSourceBeans;
        }
    }

    private record FluentExecutionMaterialization<T>(List<T> typedRows,
                                                     List<Object[]> arrayRows,
                                                     List<String> schemaFields,
                                                     List<QueryRow> queryRows) {
        private static <T> FluentExecutionMaterialization<T> typedRows(List<T> typedRows) {
            return new FluentExecutionMaterialization<>(typedRows, null, null, null);
        }

        private static <T> FluentExecutionMaterialization<T> arrayRows(List<Object[]> arrayRows,
                                                                       List<String> schemaFields) {
            return new FluentExecutionMaterialization<>(null, arrayRows, schemaFields, null);
        }

        private static <T> FluentExecutionMaterialization<T> queryRows(List<QueryRow> queryRows) {
            return new FluentExecutionMaterialization<>(null, null, null, queryRows);
        }

        private List<T> toTypedRows(Class<T> projectionClass) {
            if (typedRows != null) {
                return typedRows;
            }
            if (arrayRows != null) {
                return ReflectionUtil.toClassList(projectionClass, arrayRows, schemaFields);
            }
            return ReflectionUtil.toClassList(projectionClass, queryRows);
        }

        private ChartData toChartData(ChartSpec spec) {
            if (typedRows != null) {
                return ChartMapper.toChartData(typedRows, spec);
            }
            if (arrayRows != null) {
                return ChartMapper.toChartData(arrayRows, schemaFields, spec);
            }
            return ChartMapper.toChartData(queryRows, spec);
        }

        private int rowCount() {
            if (typedRows != null) {
                return typedRows.size();
            }
            if (arrayRows != null) {
                return arrayRows.size();
            }
            return queryRows == null ? 0 : queryRows.size();
        }
    }

    private record FilterStageResult(FilterExecutionPlan plan,
                                     List<QueryRow> filteredRows) {
    }

}
