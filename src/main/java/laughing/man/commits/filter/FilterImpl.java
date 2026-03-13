package laughing.man.commits.filter;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartMapper;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.computed.internal.ComputedFieldSupport;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryMetric;
import laughing.man.commits.builder.QueryRule;
import laughing.man.commits.builder.QueryTimeBucket;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import laughing.man.commits.telemetry.internal.QueryTelemetrySupport;
import laughing.man.commits.util.ReflectionUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public FilterImpl(FilterQueryBuilder query) {
        this.builderState = query;
    }

    /**
     * Executes the configured query and returns grouped results.
     */
    @Override
    public <T> Map<String, List<T>> filterGroups(Class<T> cls) {
        FilterQueryBuilder executionBuilder = builderState;
        FilterCore core = new FilterCore(executionBuilder);
        try {
            if (core.getBuilder().getRows() != null
                    && !core.getBuilder().getRows().isEmpty()) {
                FilterExecutionPlan plan = resolveExecutionPlan(core, executionBuilder, true);
                core.clean(core.getBuilder().getRows().get(0));
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
        List<QueryRow> results = new ArrayList<>();
        FilterQueryBuilder executionBuilder = builderState;
        FilterCore core = new FilterCore(executionBuilder);
        try {
            if (core.getBuilder().getRows() != null && !core.getBuilder().getRows().isEmpty()) {
                FilterExecutionPlan plan = resolveExecutionPlan(core, executionBuilder, false);
                core.clean(core.getBuilder().getRows().get(0));
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
                        List<QueryRow> orderedHaving = havingCore.orderByFields(havingFiltered, sortMethod, havingPlan);
                        emitOrderStage(executionBuilder, orderStarted, havingFiltered.size(), orderedHaving.size());
                        results = applyLimit(orderedHaving, executionBuilder.getLimit());
                    } else {
                        FilterQueryBuilder aggregateBuilder = executionBuilder.snapshotForRows(aggregated);
                        FilterCore aggregateCore = new FilterCore(aggregateBuilder);
                        FilterExecutionPlan aggregatePlan = aggregateCore.buildExecutionPlan();
                        long orderStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
                        List<QueryRow> orderedAggregated = aggregateCore.orderByFields(aggregated, sortMethod, aggregatePlan);
                        emitOrderStage(executionBuilder, orderStarted, aggregated.size(), orderedAggregated.size());
                        results = applyLimit(orderedAggregated, executionBuilder.getLimit());
                    }
                } else {
                    if (hasHavingPredicates(executionBuilder)) {
                        throw new IllegalStateException("HAVING requires grouped/aggregate query context");
                    }
                    // Keep ordering semantics consistent for non-aggregation queries.
                    long orderStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
                    List<QueryRow> sortedList = core.orderByFields(filterClasses, sortMethod, plan);
                    emitOrderStage(executionBuilder, orderStarted, filterClasses.size(), sortedList.size());
                    // Project configured display fields.
                    List<QueryRow> projected = core.filterDisplayFields(sortedList, plan);
                    results = applyLimit(projected, executionBuilder.getLimit());
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to compare core.getBuilder().getRows()[" + core.getBuilder().getRows() + "] ", e);
            throw new IllegalStateException("Failed to run filter", e);
        }
        return ReflectionUtil.toClassList(cls, results);
    }

    @Override
    public <T> ChartData chart(Class<T> cls, ChartSpec spec) {
        List<T> rows = filter(cls);
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

    @Override
    public <T> ChartData chart(Sort sortMethod, Class<T> cls, ChartSpec spec) {
        List<T> rows = filter(sortMethod, cls);
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

    private List<QueryRow> applyLimit(List<QueryRow> rows, Integer limit) {
        if (rows == null || limit == null || limit >= rows.size()) {
            return rows;
        }
        if (limit <= 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(rows.subList(0, limit));
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

}

