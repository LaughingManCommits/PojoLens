package laughing.man.commits.sqllike;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartMapper;
import laughing.man.commits.chart.ChartResultMapper;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.filter.FastStatsQuerySupport;
import laughing.man.commits.sqllike.SqlLikePreparedExecutionSupport.ExecutionContext;
import laughing.man.commits.sqllike.SqlLikePreparedExecutionSupport.ExecutionRun;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.internal.execution.SqlLikeExecutionSupport;
import laughing.man.commits.telemetry.QueryTelemetryEvent;
import laughing.man.commits.telemetry.QueryTelemetryListener;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import laughing.man.commits.telemetry.internal.QueryTelemetrySupport;
import laughing.man.commits.util.ReflectionUtil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class SqlLikeExecutionFlowSupport {

    private SqlLikeExecutionFlowSupport() {
    }

    static <T> List<T> executeFilter(ExecutionContext context, Class<T> projectionClass) {
        ExecutionRun run = context.newRun();
        OutputResolution output = resolveOutput(context, run);
        return switch (output.mode()) {
            case FAST_STATS_ALIASED -> SqlLikeExecutionSupport.projectAliasedRows(
                    output.statsState().rows(),
                    output.statsState().schemaFields(),
                    projectionClass,
                    output.select()
            );
            case RAW_ROWS_PROJECTED -> SqlLikeExecutionSupport.projectAliasedRows(
                    executeRawRows(run),
                    projectionClass,
                    output.select()
            );
            case RAW_ROWS_TYPED -> ReflectionUtil.toClassList(projectionClass, executeRawRows(run));
            case FAST_STATS_TYPED -> ReflectionUtil.toClassList(
                    projectionClass,
                    output.statsState().rows(),
                    output.statsState().schemaFields()
            );
            case DIRECT -> SqlLikeExecutionSupport.executeWithOptionalJoin(
                    run.builder(),
                    context.sort(),
                    context.applyJoin(),
                    projectionClass
            );
        };
    }

    static <T> Stream<T> executeStream(ExecutionContext context, Class<T> projectionClass) {
        ExecutionRun run = context.newRun();
        OutputResolution output = resolveOutput(context, run);
        return switch (output.mode()) {
            case FAST_STATS_ALIASED -> SqlLikeExecutionSupport.projectAliasedRows(
                    output.statsState().rows(),
                    output.statsState().schemaFields(),
                    projectionClass,
                    output.select()
            ).stream();
            case RAW_ROWS_PROJECTED -> SqlLikeExecutionSupport.projectAliasedRows(
                    executeRawRows(run),
                    projectionClass,
                    output.select()
            ).stream();
            case RAW_ROWS_TYPED -> ReflectionUtil.toClassList(
                    projectionClass,
                    executeRawRows(run)
            ).stream();
            case FAST_STATS_TYPED -> ReflectionUtil.toClassList(
                    projectionClass,
                    output.statsState().rows(),
                    output.statsState().schemaFields()
            ).stream();
            case DIRECT -> SqlLikeExecutionSupport.executeStreamWithOptionalJoin(
                    run.builder(),
                    context.sort(),
                    context.applyJoin(),
                    projectionClass
            );
        };
    }

    static <T> ChartData executeChart(ExecutionContext context,
                                      Class<T> projectionClass,
                                      ChartSpec spec,
                                      QueryTelemetryListener telemetryListener,
        String source) {
        if (canMapChartFromSourceRows(context, spec)) {
            List<?> sourceRows = context.sourceRows();
            long chartStarted = QueryTelemetrySupport.start(telemetryListener);
            ChartData chart = ChartMapper.toChartData(sourceRows, spec);
            emitChartTelemetry(chartStarted, countNonNullRows(sourceRows), chart, telemetryListener, source);
            return chart;
        }
        ExecutionRun run = context.newRun();
        OutputResolution output = resolveOutput(context, run);
        switch (output.mode()) {
            case FAST_STATS_ALIASED -> {
                long chartStarted = QueryTelemetrySupport.start(telemetryListener);
                ChartData chart = ChartMapper.toChartData(
                        output.statsState().rows(),
                        SqlLikeExecutionSupport.aliasedOutputSchema(
                                output.statsState().schemaFields(),
                                output.select()
                        ),
                        spec
                );
                emitChartTelemetry(
                        chartStarted,
                        output.statsState().rows().size(),
                        chart,
                        telemetryListener,
                        source
                );
                return chart;
            }
            case RAW_ROWS_PROJECTED -> {
                List<T> projectedRows = SqlLikeExecutionSupport.projectAliasedRows(
                        executeRawRows(run),
                        projectionClass,
                        output.select()
                );
                long chartStarted = QueryTelemetrySupport.start(telemetryListener);
                ChartData chart = ChartMapper.toChartData(projectedRows, spec);
                emitChartTelemetry(chartStarted, projectedRows.size(), chart, telemetryListener, source);
                return chart;
            }
            case FAST_STATS_TYPED -> {
                long chartStarted = QueryTelemetrySupport.start(telemetryListener);
                ChartData chart = ChartMapper.toChartData(
                        output.statsState().rows(),
                        output.statsState().schemaFields(),
                        spec
                );
                emitChartTelemetry(
                        chartStarted,
                        output.statsState().rows().size(),
                        chart,
                        telemetryListener,
                        source
                );
                return chart;
            }
            case RAW_ROWS_TYPED, DIRECT -> {
                List<QueryRow> rows = executeRawRows(run);
                long chartStarted = QueryTelemetrySupport.start(telemetryListener);
                ChartData chart = ChartResultMapper.toChartData(rows, spec);
                emitChartTelemetry(chartStarted, rows.size(), chart, telemetryListener, source);
                return chart;
            }
        }
        throw new IllegalStateException("Unhandled SQL-like output mode: " + output.mode());
    }

    static Map<String, Object> buildStageRowCounts(ExecutionContext context, QueryAst originalAst) {
        FilterQueryBuilder working = context.newExecutionBuilder();
        StageTelemetryCollector collector = new StageTelemetryCollector();
        working.telemetryContext("sql-like", "sql-like-explain", collector);
        materializeSourceRows(working);
        List<QueryRow> unpagedRows = SqlLikeExecutionSupport.executeWithOptionalJoin(
                working,
                context.sort(),
                context.applyJoin(),
                QueryRow.class
        );

        boolean whereApplied = hasWherePredicates(working);
        boolean groupApplied = !working.getMetrics().isEmpty();
        boolean havingApplied = hasHavingPredicates(working);
        boolean qualifyApplied = hasQualifyPredicates(working);
        boolean orderApplied = !working.getOrderFields().isEmpty();
        boolean limitApplied = originalAst.hasLimitClause() || originalAst.hasOffsetClause();

        int beforeWhere = collector.before(QueryTelemetryStage.FILTER);
        int afterWhere = collector.after(QueryTelemetryStage.FILTER);
        int beforeGroup = afterWhere;
        int afterGroup = groupApplied ? collector.after(QueryTelemetryStage.AGGREGATE) : afterWhere;
        int beforeHaving = afterGroup;
        int afterHaving = groupApplied ? unpagedRows.size() : afterGroup;
        int beforeQualify = afterHaving;
        int afterQualify = qualifyApplied ? unpagedRows.size() : afterHaving;
        int beforeOrder = afterQualify;
        int afterOrder = orderApplied ? unpagedRows.size() : afterQualify;
        int beforeLimit = afterOrder;
        int afterLimit = limitApplied
                ? applyOffsetAndLimitCount(afterOrder, originalAst.offset(), originalAst.limit())
                : afterOrder;

        Map<String, Object> stageCounts = new LinkedHashMap<>();
        stageCounts.put("where", stageEntry(whereApplied, beforeWhere, afterWhere));
        stageCounts.put("group", stageEntry(groupApplied, beforeGroup, afterGroup));
        stageCounts.put("having", stageEntry(havingApplied, beforeHaving, afterHaving));
        stageCounts.put("qualify", stageEntry(qualifyApplied, beforeQualify, afterQualify));
        stageCounts.put("order", stageEntry(orderApplied, beforeOrder, afterOrder));
        stageCounts.put("limit", stageEntry(limitApplied, beforeLimit, afterLimit));
        return Collections.unmodifiableMap(stageCounts);
    }

    private static List<QueryRow> executeRawRows(ExecutionRun run) {
        return SqlLikeExecutionSupport.executeWithOptionalJoin(
                run.builder(),
                run.sort(),
                run.applyJoin(),
                QueryRow.class
        );
    }

    private static boolean hasWherePredicates(FilterQueryBuilder builder) {
        return !builder.getFilterFields().isEmpty()
                || !builder.getAllOfGroups().isEmpty()
                || !builder.getAnyOfGroups().isEmpty();
    }

    private static boolean hasPlainFieldAliases(SelectAst select) {
        if (select == null) {
            return false;
        }
        for (SelectFieldAst field : select.fields()) {
            if (field.aliased()
                    && !field.metricField()
                    && !field.timeBucketField()
                    && !field.computedField()
                    && !field.windowField()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasHavingPredicates(FilterQueryBuilder builder) {
        return !builder.getHavingFields().isEmpty()
                || !builder.getHavingAllOfGroups().isEmpty()
                || !builder.getHavingAnyOfGroups().isEmpty();
    }

    private static boolean hasQualifyPredicates(FilterQueryBuilder builder) {
        return !builder.getQualifyFields().isEmpty()
                || !builder.getQualifyAllOfGroups().isEmpty()
                || !builder.getQualifyAnyOfGroups().isEmpty();
    }

    private static boolean canMapChartFromSourceRows(ExecutionContext context, ChartSpec spec) {
        QueryAst ast = context.ast();
        if (ast == null
                || context.applyJoin()
                || context.sort() != null
                || ast.hasJoins()
                || ast.hasAggregation()
                || ast.hasLimitClause()
                || ast.hasOffsetClause()
                || ast.hasQualifyClause()
                || !ast.filters().isEmpty()
                || ast.whereExpression() != null
                || !ast.groupByFields().isEmpty()
                || !ast.havingFilters().isEmpty()
                || ast.havingExpression() != null
                || !ast.orders().isEmpty()) {
            return false;
        }
        SelectAst select = context.select();
        if (select == null) {
            return false;
        }
        if (select.wildcard()) {
            return true;
        }
        if (select.hasComputedFields() || select.hasWindowFields() || hasPlainFieldAliases(select)) {
            return false;
        }
        Set<String> selectedFields = new LinkedHashSet<>(select.fields().size());
        for (SelectFieldAst field : select.fields()) {
            if (field.metricField() || field.timeBucketField() || field.aliased()) {
                return false;
            }
            selectedFields.add(field.field());
        }
        if (!selectedFields.contains(spec.xField()) || !selectedFields.contains(spec.yField())) {
            return false;
        }
        return !spec.multiSeries() || selectedFields.contains(spec.seriesField());
    }

    private static int countNonNullRows(List<?> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Object row : rows) {
            if (row != null) {
                count++;
            }
        }
        return count;
    }

    private static OutputResolution resolveOutput(ExecutionContext context, ExecutionRun run) {
        SelectAst select = context.select();
        FastStatsQuerySupport.FastStatsState statsState = run.fastStatsState();
        boolean hasQualify = context.ast() != null && context.ast().hasQualifyClause();
        if (requiresProjectedOutput(select, hasQualify)) {
            if (statsState != null && canProjectFromStats(select, hasQualify)) {
                return new OutputResolution(OutputMode.FAST_STATS_ALIASED, select, statsState);
            }
            return new OutputResolution(OutputMode.RAW_ROWS_PROJECTED, select, statsState);
        }
        if (hasQualify) {
            return new OutputResolution(OutputMode.RAW_ROWS_TYPED, select, statsState);
        }
        if (statsState != null) {
            return new OutputResolution(OutputMode.FAST_STATS_TYPED, select, statsState);
        }
        return new OutputResolution(OutputMode.DIRECT, select, null);
    }

    private static boolean requiresProjectedOutput(SelectAst select, boolean hasQualify) {
        return select != null
                && !select.wildcard()
                && (hasQualify || select.hasComputedFields() || select.hasWindowFields() || hasPlainFieldAliases(select));
    }

    private static boolean canProjectFromStats(SelectAst select, boolean hasQualify) {
        return select != null
                && !hasQualify
                && !select.hasComputedFields()
                && !select.hasWindowFields();
    }

    private static void materializeSourceRows(FilterQueryBuilder builder) {
        builder.setRows(builder.getRows(), builder.getSourceFieldTypesForExecution());
    }

    private static int applyOffsetAndLimitCount(int rowCount, Integer offset, Integer limit) {
        int normalizedOffset = offset == null ? 0 : Math.max(offset, 0);
        int remaining = Math.max(rowCount - normalizedOffset, 0);
        if (limit == null) {
            return remaining;
        }
        return Math.min(remaining, Math.max(limit, 0));
    }

    private static Map<String, Object> stageEntry(boolean applied, int before, int after) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("applied", applied);
        stage.put("before", before);
        stage.put("after", after);
        return Collections.unmodifiableMap(stage);
    }

    private static void emitChartTelemetry(long chartStarted,
                                           int rowCount,
                                           ChartData chart,
                                           QueryTelemetryListener telemetryListener,
                                           String source) {
        QueryTelemetrySupport.emit(
                telemetryListener,
                QueryTelemetryStage.CHART,
                "sql-like",
                source,
                chartStarted,
                rowCount,
                rowCount,
                QueryTelemetrySupport.metadata(
                        "chartType", chart.getType(),
                        "labelCount", chart.getLabels() == null ? 0 : chart.getLabels().size(),
                        "datasetCount", chart.getDatasets() == null ? 0 : chart.getDatasets().size()
                )
        );
    }

    private enum OutputMode {
        DIRECT,
        FAST_STATS_TYPED,
        FAST_STATS_ALIASED,
        RAW_ROWS_TYPED,
        RAW_ROWS_PROJECTED
    }

    private record OutputResolution(OutputMode mode,
                                    SelectAst select,
                                    FastStatsQuerySupport.FastStatsState statsState) {
    }

    private static final class StageTelemetryCollector implements QueryTelemetryListener {
        private final Map<QueryTelemetryStage, QueryTelemetryEvent> events = new LinkedHashMap<>();

        @Override
        public void onTelemetry(QueryTelemetryEvent event) {
            if (event == null) {
                return;
            }
            switch (event.stage()) {
                case FILTER, AGGREGATE, ORDER -> events.put(event.stage(), event);
                default -> {
                }
            }
        }

        private int before(QueryTelemetryStage stage) {
            QueryTelemetryEvent event = events.get(stage);
            return event == null || event.rowCountBefore() == null ? 0 : event.rowCountBefore();
        }

        private int after(QueryTelemetryStage stage) {
            QueryTelemetryEvent event = events.get(stage);
            return event == null || event.rowCountAfter() == null ? 0 : event.rowCountAfter();
        }
    }
}
