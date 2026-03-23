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
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.internal.execution.SqlLikeExecutionSupport;
import laughing.man.commits.sqllike.internal.qualify.SqlLikeQualifySupport;
import laughing.man.commits.sqllike.internal.window.SqlLikeWindowSupport;
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
        QueryAst ast = context.ast();
        boolean hasQualify = ast != null && ast.hasQualifyClause();
        if (select != null
                && !select.wildcard()
                && (hasQualify || select.hasComputedFields() || select.hasWindowFields() || hasPlainFieldAliases(select))) {
            if (statsState != null && !hasQualify && !select.hasComputedFields() && !select.hasWindowFields()) {
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
        if (hasQualify) {
            return ReflectionUtil.toClassList(projectionClass, executeRawRows(run));
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
        QueryAst ast = context.ast();
        boolean hasQualify = ast != null && ast.hasQualifyClause();
        if (select != null
                && !select.wildcard()
                && (hasQualify || select.hasComputedFields() || select.hasWindowFields() || hasPlainFieldAliases(select))) {
            return executeFilter(context, projectionClass).stream();
        }
        if (hasQualify) {
            return ReflectionUtil.toClassList(
                    projectionClass,
                    executeRawRows(run)
            ).stream();
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
        QueryAst ast = context.ast();
        boolean hasQualify = ast != null && ast.hasQualifyClause();
        if (statsState != null) {
            if (select != null && !select.wildcard() && (hasQualify || select.hasComputedFields() || select.hasWindowFields())) {
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
                && (hasQualify || select.hasComputedFields() || select.hasWindowFields() || hasPlainFieldAliases(select))) {
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
        QueryAst ast = context.ast();
        FilterCore core = new FilterCore(working);
        if (context.applyJoin()) {
            working.setRows(core.join(working.getRows()));
            core = new FilterCore(working);
        }

        boolean whereApplied = hasWherePredicates(working);
        boolean groupApplied = !working.getMetrics().isEmpty();
        boolean havingApplied = hasHavingPredicates(working);
        boolean qualifyApplied = ast != null && ast.hasQualifyClause();
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
                List<QueryRow> qualifySource =
                        ast.select() != null && ast.select().hasWindowFields()
                                ? SqlLikeWindowSupport.applyWindowFunctions(havingRows, ast.select())
                                : havingRows;
                beforeQualify = sizeOf(qualifySource);
                qualifyRows = SqlLikeQualifySupport.apply(
                        qualifySource,
                        ast.qualifyExpression(),
                        ast.qualifyFilters()
                );
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
