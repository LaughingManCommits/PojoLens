package laughing.man.commits.sqllike;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryRule;
import laughing.man.commits.builder.QueryWindow;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartMapper;
import laughing.man.commits.chart.ChartResultMapper;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.filter.FastStatsQuerySupport;
import laughing.man.commits.filter.FilterCore;
import laughing.man.commits.filter.FilterExecutionPlan;
import laughing.man.commits.sqllike.SqlLikePreparedExecutionSupport.ExecutionContext;
import laughing.man.commits.sqllike.SqlLikePreparedExecutionSupport.ExecutionRun;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.internal.execution.SqlLikeExecutionSupport;
import laughing.man.commits.telemetry.QueryTelemetryListener;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import laughing.man.commits.telemetry.internal.QueryTelemetrySupport;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.ReflectionUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    static Map<String, Object> buildStageRowCounts(ExecutionContext context) {
        FilterQueryBuilder working = context.newRun().builder();
        FilterCore core = new FilterCore(working);
        if (context.applyJoin()) {
            working.setRows(core.join(working.getRows()));
            core = new FilterCore(working);
        }

        boolean whereApplied = hasWherePredicates(working);
        boolean groupApplied = !working.getMetrics().isEmpty();
        boolean havingApplied = hasHavingPredicates(working);
        boolean qualifyApplied = hasQualifyPredicates(working);
        boolean orderApplied = !working.getOrderFields().isEmpty();
        boolean limitApplied = working.getLimit() != null || (working.getOffset() != null && working.getOffset() > 0);
        Integer paginationWindow = CollectionUtil.pagingWindow(working.getOffset(), working.getLimit());

        int beforeWhere = sizeOf(working.getRows());
        int afterWhere = beforeWhere;
        int beforeGroup = afterWhere;
        int afterGroup = beforeGroup;
        int beforeHaving = afterGroup;
        int afterHaving = beforeHaving;
        int beforeQualify = afterHaving;
        int afterQualify = beforeQualify;
        int beforeOrder = afterQualify;
        int afterOrder = beforeOrder;
        int beforeLimit = afterOrder;
        int afterLimit = beforeLimit;

        if (working.getRows() != null && !working.getRows().isEmpty()) {
            if (working.requiresRuntimeSchemaCleaning()) {
                core.clean(working.getRows().get(0));
            }
            FilterExecutionPlan plan = core.buildExecutionPlan();
            List<QueryRow> distinctRows = core.filterDistinctFields(plan);
            beforeWhere = sizeOf(distinctRows);
            List<QueryRow> whereRows = core.filterFields(distinctRows, plan);
            afterWhere = sizeOf(whereRows);

            beforeGroup = afterWhere;
            List<QueryRow> groupedRows = whereRows;
            if (groupApplied) {
                groupedRows = core.aggregateMetrics(whereRows, plan);
            }
            afterGroup = sizeOf(groupedRows);

            beforeHaving = afterGroup;
            List<QueryRow> havingRows = groupedRows;
            FilterCore groupedCore = null;
            FilterExecutionPlan groupedPlan = null;
            if (havingApplied) {
                FilterQueryBuilder havingBuilder = working.snapshotForRows(groupedRows);
                groupedCore = new FilterCore(havingBuilder);
                groupedPlan = groupedCore.buildExecutionPlan();
                havingRows = groupedCore.filterHavingFields(groupedRows, groupedPlan);
            }
            afterHaving = sizeOf(havingRows);
            beforeQualify = afterHaving;
            List<QueryRow> qualifyRows = havingRows;
            if (qualifyApplied) {
                qualifyRows = applyQualifyStageViaFluent(working, havingRows);
            }
            afterQualify = sizeOf(qualifyRows);

            beforeOrder = afterQualify;
            List<QueryRow> orderedRows = qualifyRows;
            if (orderApplied) {
                if (groupedPlan != null && groupedCore != null) {
                    FilterQueryBuilder orderBuilder = groupedCore.getBuilder().snapshotForRows(qualifyRows);
                    FilterCore orderCore = new FilterCore(orderBuilder);
                    FilterExecutionPlan orderPlan = orderCore.buildExecutionPlan();
                    orderedRows = orderCore.orderByFields(qualifyRows, context.sort(), orderPlan, paginationWindow);
                } else {
                    FilterQueryBuilder orderBuilder = working.snapshotForRows(qualifyRows);
                    FilterCore orderCore = new FilterCore(orderBuilder);
                    FilterExecutionPlan orderPlan = orderCore.buildExecutionPlan();
                    orderedRows = orderCore.orderByFields(qualifyRows, context.sort(), orderPlan, paginationWindow);
                }
            }
            afterOrder = sizeOf(orderedRows);

            beforeLimit = afterOrder;
            List<QueryRow> limitedRows = CollectionUtil.applyOffsetAndLimit(
                    orderedRows,
                    working.getOffset(),
                    working.getLimit()
            );
            afterLimit = sizeOf(limitedRows);
        }

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

    private static List<QueryRow> applyQualifyStageViaFluent(FilterQueryBuilder sourceBuilder, List<QueryRow> inputRows) {
        if (inputRows == null || inputRows.isEmpty()) {
            return inputRows;
        }
        FilterQueryBuilder qualifyBuilder = new FilterQueryBuilder(inputRows).copyOnBuild(false);
        for (QueryWindow window : sourceBuilder.getWindows()) {
            qualifyBuilder.addWindow(
                    window.alias(),
                    window.function(),
                    window.valueField(),
                    window.countAll(),
                    window.partitionFields(),
                    window.orderFields()
            );
        }
        for (Map.Entry<String, String> fieldEntry : sourceBuilder.getQualifyFields().entrySet()) {
            String ruleId = fieldEntry.getKey();
            qualifyBuilder.addQualify(
                    fieldEntry.getValue(),
                    sourceBuilder.getQualifyValues().get(ruleId),
                    sourceBuilder.getQualifyClause().get(ruleId),
                    sourceBuilder.getQualifySeparator().getOrDefault(ruleId, Separator.AND),
                    sourceBuilder.getQualifyDateFormats().get(ruleId)
            );
        }
        for (List<QueryRule> group : sourceBuilder.getQualifyAllOfGroups()) {
            qualifyBuilder.addQualifyAllOf(group.toArray(new QueryRule[0]));
        }
        for (List<QueryRule> group : sourceBuilder.getQualifyAnyOfGroups()) {
            qualifyBuilder.addQualifyAnyOf(group.toArray(new QueryRule[0]));
        }
        return qualifyBuilder.initFilter().filter(QueryRow.class);
    }

    private static int sizeOf(List<?> rows) {
        return rows == null ? 0 : rows.size();
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
}
