package laughing.man.commits.filter;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartMapper;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.computed.internal.ComputedFieldSupport;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.builder.FilterQueryBuilder;
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
import java.util.Set;
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
        try {
            if (core.getBuilder().getRows() != null
                    && !core.getBuilder().getRows().isEmpty()) {
                FilterExecutionPlan plan = resolveExecutionPlan(core, executionBuilder, true);
                if (executionBuilder.requiresRuntimeSchemaCleaning()) {
                    core.clean(core.getBuilder().getRows().get(0));
                }
                // Remove duplicate rows when distinct columns are configured.
                List<QueryRow> distinctClasses = core.filterDistinctFields(plan);
                // Apply rule filtering (AND/OR and grouped operators).
                long filterStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
                List<QueryRow> filterClasses = core.filterFields(distinctClasses, plan);
                emitStage(executionBuilder,
                        QueryTelemetryStage.FILTER,
                        filterStarted,
                        distinctClasses.size(),
                        filterClasses.size(),
                        QueryTelemetrySupport.metadata("grouped", true));
                // Project configured display fields.
                List<QueryRow> displayFields = core.filterDisplayFields(filterClasses, plan);
                // Group rows using configured grouping fields.
                Map<String, List<QueryRow>> groupedClasses = core.groupByFields(filterClasses, displayFields, plan);
                // Convert grouped rows back to caller type.
                Set<String> keys = groupedClasses.keySet();
                Map<String, List<T>> map = new HashMap<>();
                for (String key : keys) {
                    List<T> list = ReflectionUtil.toClassList(cls, groupedClasses.get(key));
                    map.put(key, list);
                }

                return map;
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to compare core.getBuilder().getRows()[" + core.getBuilder().getRows() + "] ", e);
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
        if (hasWindowOrQualify(builderState)) {
            return ReflectionUtil.toClassList(cls, filterRows(sortMethod));
        }
        FastArrayQuerySupport.FastArrayState fastState = fastArrayState;
        if (fastState != null) {
            return FastArrayQuerySupport.filter(builderState, fastState, sortMethod, cls);
        }
        FastStatsQuerySupport.FastStatsState statsState = fastStatsState;
        if (statsState == null) {
            statsState = FastStatsQuerySupport.tryBuildState(builderState, planCacheKey(builderState));
            fastStatsState = statsState;
        }
        if (statsState != null) {
            return ReflectionUtil.toClassList(cls, statsState.rows(), statsState.schemaFields());
        }
        return ReflectionUtil.toClassList(cls, filterRows(sortMethod));
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

    private List<QueryRow> filterRows(Sort sortMethod) {
        if (hasWindowOrQualify(builderState)) {
            fastStatsState = null;
        }
        FastStatsQuerySupport.FastStatsState statsState = fastStatsState;
        if (statsState == null) {
            if (!hasWindowOrQualify(builderState)) {
                statsState = FastStatsQuerySupport.tryBuildState(builderState, planCacheKey(builderState));
            }
            fastStatsState = statsState;
        }
        if (statsState != null) {
            return FastStatsQuerySupport.toQueryRows(statsState);
        }

        List<QueryRow> results = new ArrayList<>();
        FilterQueryBuilder executionBuilder = builderState;

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
            Integer paginationWindow = CollectionUtil.pagingWindow(executionBuilder.getOffset(), executionBuilder.getLimit());
            if (core.getBuilder().getRows() != null && !core.getBuilder().getRows().isEmpty()) {
                FilterExecutionPlan plan = resolveExecutionPlan(core, executionBuilder, false);
                if (!fastPojoFilterApplied && executionBuilder.requiresRuntimeSchemaCleaning()) {
                    core.clean(core.getBuilder().getRows().get(0));
                }
                // Remove duplicate rows when distinct columns are configured.
                List<QueryRow> distinctClasses = core.filterDistinctFields(plan);
                // Apply rule filtering (AND/OR and grouped operators).
                long filterStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
                List<QueryRow> filterClasses = fastPojoFilterApplied
                        ? distinctClasses
                        : core.filterFields(distinctClasses, plan);
                emitStage(executionBuilder,
                        QueryTelemetryStage.FILTER,
                        filterStarted,
                        distinctClasses.size(),
                        filterClasses.size(),
                        null);
                if (!executionBuilder.getMetrics().isEmpty()) {
                    // Compute aggregate metrics (global or grouped based on GROUP BY config).
                    long aggregateStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
                    List<QueryRow> aggregated = core.aggregateMetrics(filterClasses, plan);
                    emitStage(executionBuilder,
                            QueryTelemetryStage.AGGREGATE,
                            aggregateStarted,
                            filterClasses.size(),
                            aggregated.size(),
                            QueryTelemetrySupport.metadata("havingApplied", hasHavingPredicates(executionBuilder)));
                    if (hasHavingPredicates(executionBuilder)) {
                        FilterQueryBuilder havingBuilder = executionBuilder.snapshotForRows(aggregated);
                        FilterCore havingCore = new FilterCore(havingBuilder);
                        FilterExecutionPlan havingPlan = havingCore.buildExecutionPlan();
                        List<QueryRow> havingFiltered = havingCore.filterHavingFields(aggregated, havingPlan);
                        // ORDER BY for stats queries is evaluated on post-aggregation rows.
                        long orderStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
                        List<QueryRow> orderedHaving = havingCore.orderByFields(
                                havingFiltered, sortMethod, havingPlan, paginationWindow);
                        emitOrderStage(executionBuilder, orderStarted, havingFiltered.size(), orderedHaving.size());
                        results = CollectionUtil.applyOffsetAndLimit(
                                orderedHaving,
                                executionBuilder.getOffset(),
                                executionBuilder.getLimit()
                        );
                    } else {
                        FilterQueryBuilder aggregateBuilder = executionBuilder.snapshotForRows(aggregated);
                        FilterCore aggregateCore = new FilterCore(aggregateBuilder);
                        FilterExecutionPlan aggregatePlan = aggregateCore.buildExecutionPlan();
                        long orderStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
                        List<QueryRow> orderedAggregated = aggregateCore.orderByFields(
                                aggregated, sortMethod, aggregatePlan, paginationWindow);
                        emitOrderStage(executionBuilder, orderStarted, aggregated.size(), orderedAggregated.size());
                        results = CollectionUtil.applyOffsetAndLimit(
                                orderedAggregated,
                                executionBuilder.getOffset(),
                                executionBuilder.getLimit()
                        );
                    }
                } else {
                    if (hasHavingPredicates(executionBuilder)) {
                        throw new IllegalStateException("HAVING requires grouped/aggregate query context");
                    }
                    boolean hasWindows = hasWindows(executionBuilder);
                    boolean hasQualify = FluentQualifySupport.hasPredicates(executionBuilder);
                    List<QueryRow> stagedRows = hasWindows
                            ? FluentWindowSupport.apply(filterClasses, executionBuilder.getWindows())
                            : filterClasses;
                    List<QueryRow> qualifiedRows = hasQualify
                            ? FluentQualifySupport.apply(executionBuilder, stagedRows)
                            : stagedRows;
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
                    // Apply offset/limit before display projection to avoid projecting rows that will be discarded.
                    List<QueryRow> limited = CollectionUtil.applyOffsetAndLimit(
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
                }
            }
        } catch (IllegalArgumentException e) {
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
        if (hasWindowOrQualify(builderState)) {
            List<QueryRow> rows = filterRows(sortMethod);
            long chartStarted = QueryTelemetrySupport.start(builderState.getTelemetryListener());
            ChartData chart = ChartMapper.toChartData(rows, spec);
            emitStage(builderState,
                    QueryTelemetryStage.CHART,
                    chartStarted,
                    rows.size(),
                    rows.size(),
                    QueryTelemetrySupport.metadata(
                            "chartType", chart.getType(),
                            "labelCount", chart.getLabels() == null ? 0 : chart.getLabels().size(),
                            "datasetCount", chart.getDatasets() == null ? 0 : chart.getDatasets().size()
                    ));
            return chart;
        }
        FastArrayQuerySupport.FastArrayState fastState = fastArrayState;
        int rowCount;
        long chartStarted = QueryTelemetrySupport.start(builderState.getTelemetryListener());
        ChartData chart;
        if (fastState != null) {
            List<T> rows = FastArrayQuerySupport.filter(builderState, fastState, sortMethod, cls);
            rowCount = rows.size();
            chart = ChartMapper.toChartData(rows, spec);
        } else {
            FastStatsQuerySupport.FastStatsState statsState = fastStatsState;
            if (statsState == null) {
                statsState = FastStatsQuerySupport.tryBuildState(builderState, planCacheKey(builderState));
                fastStatsState = statsState;
            }
            if (statsState != null) {
                rowCount = statsState.rows().size();
                chart = ChartMapper.toChartData(statsState.rows(), statsState.schemaFields(), spec);
            } else {
                List<QueryRow> rows = filterRows(sortMethod);
                rowCount = rows.size();
                chart = ChartMapper.toChartData(rows, spec);
            }
        }
        emitStage(builderState,
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
        if (!builder.getMetrics().isEmpty()
                || !builder.getGroupFields().isEmpty()
                || !builder.getTimeBuckets().isEmpty()) {
            throw new IllegalArgumentException("Window functions are only supported for non-aggregate fluent queries");
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
        FastArrayQuerySupport.FastArrayState fastState = FastArrayQuerySupport.tryBuildJoinedState(executionBuilder);
        if (fastState != null) {
            executionBuilder.setExecutionSchema(fastState.schemaTypes());
            this.fastArrayState = fastState;
            return this;
        }
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
            return;
        }
        FilterQueryBuilder executionBuilder = builderState;
        executionBuilder.setMaterializedRows(
                FastArrayQuerySupport.toQueryRows(fastState),
                fastState.schemaTypes()
        );
        fastArrayState = null;
        sourceIndexCache = null;
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
            if (index == null) {
                return null;
            }
            List<?> matches = index.get(value);
            return matches == null ? List.of() : matches;
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

}
