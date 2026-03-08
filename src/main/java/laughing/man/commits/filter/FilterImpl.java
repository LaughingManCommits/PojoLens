package laughing.man.commits.filter;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartMapper;
import laughing.man.commits.chart.ChartSpec;
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
import java.util.Comparator;
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

    public FilterImpl(FilterQueryBuilder query) {
        this.builderState = query;
    }

    /**
     * Executes the configured query and returns grouped results.
     */
    @Override
    public <T> Map<String, List<T>> filterGroups(Class<T> cls) {
        FilterQueryBuilder executionBuilder = builderState.snapshotForExecution();
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
        FilterQueryBuilder executionBuilder = builderState.snapshotForExecution();
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
                        FilterQueryBuilder havingBuilder = executionBuilder.snapshotForExecution();
                        havingBuilder.setRows(aggregated);
                        FilterCore havingCore = new FilterCore(havingBuilder);
                        FilterExecutionPlan havingPlan = havingCore.buildExecutionPlan();
                        List<QueryRow> havingFiltered = havingCore.filterHavingFields(aggregated);
                        // ORDER BY for stats queries is evaluated on post-aggregation rows.
                        long orderStarted = QueryTelemetrySupport.start(executionBuilder.getTelemetryListener());
                        List<QueryRow> orderedHaving = havingCore.orderByFields(havingFiltered, sortMethod, havingPlan);
                        emitOrderStage(executionBuilder, orderStarted, havingFiltered.size(), orderedHaving.size());
                        results = applyLimit(orderedHaving, executionBuilder.getLimit());
                    } else {
                        FilterQueryBuilder aggregateBuilder = executionBuilder.snapshotForExecution();
                        aggregateBuilder.setRows(aggregated);
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
        String key = planCacheKey(executionBuilder);
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

    private String planCacheKey(FilterQueryBuilder builder) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("schema=").append(schemaKey(builder)).append('|');
        appendSortedMap(sb, "filters", builder.getFilterFields());
        appendSortedMap(sb, "clauses", builder.getFilterClause());
        appendSortedMap(sb, "separators", builder.getFilterSeparator());
        appendSortedMap(sb, "dates", builder.getFilterDateFormats());
        appendSortedMap(sb, "havingFields", builder.getHavingFields());
        appendSortedMap(sb, "havingClauses", builder.getHavingClause());
        appendSortedMap(sb, "havingSeparators", builder.getHavingSeparator());
        appendSortedMap(sb, "havingDates", builder.getHavingDateFormats());
        appendSortedMap(sb, "groups", builder.getGroupFields());
        appendSortedMap(sb, "orders", builder.getOrderFields());
        appendSortedMap(sb, "distinct", builder.getDistinctFields());
        appendIndexedList(sb, "returns", builder.getReturnFields());
        appendTimeBuckets(sb, "timeBuckets", builder.getTimeBuckets());
        appendMetrics(sb, "metrics", builder.getMetrics());
        appendRuleGroups(sb, "allOf", builder.getAllOfGroups());
        appendRuleGroups(sb, "anyOf", builder.getAnyOfGroups());
        appendRuleGroups(sb, "havingAllOf", builder.getHavingAllOfGroups());
        appendRuleGroups(sb, "havingAnyOf", builder.getHavingAnyOfGroups());
        return sb.toString();
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

    private String schemaKey(FilterQueryBuilder builder) {
        if (builder.getRows() == null || builder.getRows().isEmpty()) {
            return "empty";
        }
        QueryRow row = builder.getRows().get(0);
        if (row == null || row.getFields() == null) {
            return "empty";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.getFields().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(row.getFields().get(i).getFieldName());
        }
        return sb.toString();
    }

    private <K, V> void appendSortedMap(StringBuilder sb, String label, Map<K, V> map) {
        sb.append(label).append('=');
        List<Map.Entry<K, V>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<K, V> entry = entries.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.valueOf(entry.getKey()))
                    .append(':')
                    .append(entry.getValue());
        }
        sb.append('|');
    }

    private void appendIndexedList(StringBuilder sb, String label, List<String> values) {
        sb.append(label).append('=');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(i).append(':').append(values.get(i));
        }
        sb.append('|');
    }

    private void appendTimeBuckets(StringBuilder sb, String label, Map<String, QueryTimeBucket> buckets) {
        sb.append(label).append('=');
        List<Map.Entry<String, QueryTimeBucket>> entries = new ArrayList<>(buckets.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Map.Entry<String, QueryTimeBucket> entry = entries.get(i);
            QueryTimeBucket bucket = entry.getValue();
            sb.append(entry.getKey())
                    .append(':')
                    .append(bucket.getDateField())
                    .append(':')
                    .append(bucket.getPreset().explainToken());
        }
        sb.append('|');
    }

    private void appendMetrics(StringBuilder sb, String label, List<QueryMetric> metrics) {
        sb.append(label).append('=');
        for (int i = 0; i < metrics.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            QueryMetric metric = metrics.get(i);
            sb.append(metric.getMetric())
                    .append(':')
                    .append(metric.getField())
                    .append(':')
                    .append(metric.getAlias());
        }
        sb.append('|');
    }

    private void appendRuleGroups(StringBuilder sb, String label, List<List<QueryRule>> groups) {
        sb.append(label).append('=');
        for (int g = 0; g < groups.size(); g++) {
            if (g > 0) {
                sb.append(';');
            }
            List<QueryRule> group = groups.get(g);
            for (int i = 0; i < group.size(); i++) {
                QueryRule rule = group.get(i);
                if (i > 0) {
                    sb.append('&');
                }
                sb.append(rule.getColumn())
                        .append(':')
                        .append(rule.getClause())
                        .append(':')
                        .append(rule.getValue())
                        .append(':')
                        .append(rule.getDateFormat());
            }
        }
        sb.append('|');
    }

    /**
     * Applies configured joins and stores joined rows as new executor state.
     */
    @Override
    public synchronized Filter join() {
        FilterQueryBuilder executionBuilder = builderState.snapshotForExecution();
        FilterCore core = new FilterCore(executionBuilder);
        try {
            executionBuilder.setRows(core.join(executionBuilder.getRows()));
            builderState = executionBuilder;
        } catch (Exception e) {
            LOG.error("Failed to join filterList[" + executionBuilder.getRows() + "] ", e);
            throw new IllegalStateException("Failed to apply join pipeline", e);
        }
        return this;
    }

}

