package laughing.man.commits.sqllike;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartMapper;
import laughing.man.commits.chart.ChartResultMapper;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.filter.FastStatsQuerySupport;
import laughing.man.commits.filter.FilterCore;
import laughing.man.commits.filter.FilterExecutionPlan;
import laughing.man.commits.sqllike.SqlLikePreparedExecutionSupport.ExecutionContext;
import laughing.man.commits.sqllike.SqlLikePreparedExecutionSupport.ExecutionRun;
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
        FastStatsQuerySupport.FastStatsState statsState = run.fastStatsState();
        SelectAst select = context.select();
        if (select != null
                && !select.wildcard()
                && (select.hasComputedFields() || hasPlainFieldAliases(select))) {
            if (statsState != null && !select.hasComputedFields()) {
                return SqlLikeExecutionSupport.projectAliasedRows(
                        statsState.rows(),
                        statsState.schemaFields(),
                        projectionClass,
                        select
                );
            }
            List<QueryRow> sourceRows = executeRawRows(run);
            return SqlLikeExecutionSupport.projectAliasedRows(sourceRows, projectionClass, select);
        }
        if (statsState != null) {
            return ReflectionUtil.toClassList(
                    projectionClass,
                    statsState.rows(),
                    statsState.schemaFields()
            );
        }
        return SqlLikeExecutionSupport.executeWithOptionalJoin(
                run.builder(),
                context.sort(),
                context.applyJoin(),
                projectionClass
        );
    }

    static <T> Stream<T> executeStream(ExecutionContext context, Class<T> projectionClass) {
        ExecutionRun run = context.newRun();
        FastStatsQuerySupport.FastStatsState statsState = run.fastStatsState();
        SelectAst select = context.select();
        if (select != null
                && !select.wildcard()
                && (select.hasComputedFields() || hasPlainFieldAliases(select))) {
            return executeFilter(context, projectionClass).stream();
        }
        if (statsState != null) {
            return ReflectionUtil.toClassList(
                    projectionClass,
                    statsState.rows(),
                    statsState.schemaFields()
            ).stream();
        }
        return SqlLikeExecutionSupport.executeStreamWithOptionalJoin(
                run.builder(),
                context.sort(),
                context.applyJoin(),
                projectionClass
        );
    }

    static <T> ChartData executeChart(ExecutionContext context,
                                      Class<T> projectionClass,
                                      ChartSpec spec,
                                      QueryTelemetryListener telemetryListener,
                                      String source) {
        ExecutionRun run = context.newRun();
        FastStatsQuerySupport.FastStatsState statsState = run.fastStatsState();
        SelectAst select = context.select();
        if (statsState != null) {
            if (select != null && !select.wildcard() && select.hasComputedFields()) {
                List<T> projectedRows = SqlLikeExecutionSupport.projectAliasedRows(
                        executeRawRows(run),
                        projectionClass,
                        select
                );
                long chartStarted = QueryTelemetrySupport.start(telemetryListener);
                ChartData chart = ChartMapper.toChartData(projectedRows, spec);
                emitChartTelemetry(chartStarted, projectedRows.size(), chart, telemetryListener, source);
                return chart;
            }
            long chartStarted = QueryTelemetrySupport.start(telemetryListener);
            List<String> chartSchema =
                    select != null && !select.wildcard() && hasPlainFieldAliases(select)
                            ? SqlLikeExecutionSupport.aliasedOutputSchema(statsState.schemaFields(), select)
                            : statsState.schemaFields();
            ChartData chart = ChartMapper.toChartData(statsState.rows(), chartSchema, spec);
            emitChartTelemetry(chartStarted, statsState.rows().size(), chart, telemetryListener, source);
            return chart;
        }
        List<QueryRow> rows = executeRawRows(run);
        if (select != null
                && !select.wildcard()
                && (select.hasComputedFields() || hasPlainFieldAliases(select))) {
            List<T> projectedRows = SqlLikeExecutionSupport.projectAliasedRows(rows, projectionClass, select);
            long chartStarted = QueryTelemetrySupport.start(telemetryListener);
            ChartData chart = ChartMapper.toChartData(projectedRows, spec);
            emitChartTelemetry(chartStarted, projectedRows.size(), chart, telemetryListener, source);
            return chart;
        }
        long chartStarted = QueryTelemetrySupport.start(telemetryListener);
        ChartData chart = ChartResultMapper.toChartData(rows, spec);
        emitChartTelemetry(chartStarted, rows.size(), chart, telemetryListener, source);
        return chart;
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
        boolean orderApplied = !working.getOrderFields().isEmpty();
        boolean limitApplied = working.getLimit() != null || (working.getOffset() != null && working.getOffset() > 0);
        Integer paginationWindow = CollectionUtil.pagingWindow(working.getOffset(), working.getLimit());

        int beforeWhere = sizeOf(working.getRows());
        int afterWhere = beforeWhere;
        int beforeGroup = afterWhere;
        int afterGroup = beforeGroup;
        int beforeHaving = afterGroup;
        int afterHaving = beforeHaving;
        int beforeOrder = afterHaving;
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

            beforeOrder = afterHaving;
            List<QueryRow> orderedRows = havingRows;
            if (orderApplied) {
                if (groupedPlan != null && groupedCore != null) {
                    orderedRows = groupedCore.orderByFields(havingRows, context.sort(), groupedPlan, paginationWindow);
                } else {
                    FilterQueryBuilder orderBuilder = working.snapshotForRows(havingRows);
                    FilterCore orderCore = new FilterCore(orderBuilder);
                    FilterExecutionPlan orderPlan = orderCore.buildExecutionPlan();
                    orderedRows = orderCore.orderByFields(havingRows, context.sort(), orderPlan, paginationWindow);
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
        stageCounts.put("order", stageEntry(orderApplied, beforeOrder, afterOrder));
        stageCounts.put("limit", stageEntry(limitApplied, beforeLimit, afterLimit));
        return Collections.unmodifiableMap(stageCounts);
    }

    private static List<QueryRow> executeRawRows(ExecutionRun run) {
        FilterQueryBuilder working = run.builder();
        FastStatsQuerySupport.FastStatsState statsState = run.fastStatsState();
        if (statsState != null) {
            return FastStatsQuerySupport.toQueryRows(statsState);
        }
        FilterCore core = new FilterCore(working);
        if (run.applyJoin()) {
            working.setRows(core.join(working.getRows()));
            core = new FilterCore(working);
        }
        if (working.getRows() == null || working.getRows().isEmpty()) {
            return Collections.emptyList();
        }

        if (working.requiresRuntimeSchemaCleaning()) {
            core.clean(working.getRows().get(0));
        }
        FilterExecutionPlan plan = run.resolveRawExecutionPlan(core);
        List<QueryRow> distinctRows = core.filterDistinctFields(plan);
        long filterStarted = QueryTelemetrySupport.start(working.getTelemetryListener());
        List<QueryRow> whereRows = core.filterFields(distinctRows, plan);
        emitStage(
                working,
                QueryTelemetryStage.FILTER,
                filterStarted,
                distinctRows.size(),
                whereRows.size(),
                null
        );

        if (!working.getMetrics().isEmpty()) {
            long aggregateStarted = QueryTelemetrySupport.start(working.getTelemetryListener());
            List<QueryRow> groupedRows = core.aggregateMetrics(whereRows, plan);
            emitStage(
                    working,
                    QueryTelemetryStage.AGGREGATE,
                    aggregateStarted,
                    whereRows.size(),
                    groupedRows.size(),
                    QueryTelemetrySupport.metadata("havingApplied", hasHavingPredicates(working))
            );
            List<QueryRow> havingRows = groupedRows;
            FilterCore groupedCore = null;
            FilterExecutionPlan groupedPlan = null;
            if (hasHavingPredicates(working)) {
                FilterQueryBuilder havingBuilder = working.snapshotForRows(groupedRows);
                groupedCore = new FilterCore(havingBuilder);
                groupedPlan = groupedCore.buildExecutionPlan();
                havingRows = groupedCore.filterHavingFields(groupedRows, groupedPlan);
            }
            List<QueryRow> orderedRows = havingRows;
            if (!working.getOrderFields().isEmpty()) {
                long orderStarted = QueryTelemetrySupport.start(working.getTelemetryListener());
                Integer paginationWindow = CollectionUtil.pagingWindow(working.getOffset(), working.getLimit());
                if (groupedPlan != null && groupedCore != null) {
                    orderedRows = groupedCore.orderByFields(havingRows, run.sort(), groupedPlan, paginationWindow);
                } else {
                    FilterQueryBuilder orderBuilder = working.snapshotForRows(havingRows);
                    FilterCore orderCore = new FilterCore(orderBuilder);
                    FilterExecutionPlan orderPlan = orderCore.buildExecutionPlan();
                    orderedRows = orderCore.orderByFields(havingRows, run.sort(), orderPlan, paginationWindow);
                }
                emitStage(
                        working,
                        QueryTelemetryStage.ORDER,
                        orderStarted,
                        havingRows.size(),
                        orderedRows.size(),
                        QueryTelemetrySupport.metadata("orderFieldCount", working.getOrderFields().size())
                );
            }
            return CollectionUtil.applyOffsetAndLimit(orderedRows, working.getOffset(), working.getLimit());
        }

        if (hasHavingPredicates(working)) {
            throw new IllegalStateException("HAVING requires grouped/aggregate query context");
        }

        long orderStarted = QueryTelemetrySupport.start(working.getTelemetryListener());
        Integer paginationWindow = CollectionUtil.pagingWindow(working.getOffset(), working.getLimit());
        List<QueryRow> orderedRows = core.orderByFields(whereRows, run.sort(), plan, paginationWindow);
        if (!working.getOrderFields().isEmpty()) {
            emitStage(
                    working,
                    QueryTelemetryStage.ORDER,
                    orderStarted,
                    whereRows.size(),
                    orderedRows.size(),
                    QueryTelemetrySupport.metadata("orderFieldCount", working.getOrderFields().size())
            );
        }
        List<QueryRow> limitedRows = CollectionUtil.applyOffsetAndLimit(
                orderedRows,
                working.getOffset(),
                working.getLimit()
        );
        return core.filterDisplayFields(limitedRows, plan);
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
            if (field.aliased() && !field.metricField() && !field.timeBucketField() && !field.computedField()) {
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

    private static int sizeOf(List<?> rows) {
        return rows == null ? 0 : rows.size();
    }

    private static void emitStage(FilterQueryBuilder builder,
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
}
