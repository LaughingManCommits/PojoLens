package laughing.man.commits.sqllike;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.filter.FastStatsQuerySupport;
import laughing.man.commits.filter.FilterCore;
import laughing.man.commits.filter.FilterExecutionPlan;
import laughing.man.commits.filter.FilterExecutionPlanCacheKey;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.sqllike.internal.binding.SqlLikeBinder;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.sqllike.internal.execution.SqlLikeExecutionSupport;
import laughing.man.commits.sqllike.internal.params.SqlLikeParameterSupport;
import laughing.man.commits.sqllike.internal.validation.SqlLikeValidator;
import laughing.man.commits.telemetry.QueryTelemetryListener;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import laughing.man.commits.telemetry.internal.QueryTelemetrySupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

final class SqlLikePreparedExecutionSupport {

    private SqlLikePreparedExecutionSupport() {
    }

    static <T> ExecutionContext prepareExecution(String source,
                                                 QueryAst ast,
                                                 boolean strictParameterTypes,
                                                 ComputedFieldRegistry computedFieldRegistry,
                                                 QueryTelemetryListener telemetryListener,
                                                 ConcurrentMap<ExecutionShapeKey, PreparedExecution> preparedExecutions,
                                                 List<?> pojos,
                                                 Map<String, List<?>> joinSources,
                                                 Class<T> projectionClass) {
        Objects.requireNonNull(pojos, "pojos must not be null");
        Objects.requireNonNull(joinSources, "joinSources must not be null");
        SqlLikeParameterSupport.assertFullyBound(ast);
        long bindStarted = QueryTelemetrySupport.start(telemetryListener);
        Class<?> sourceClass = SqlLikeExecutionSupport.inferSourceClass(pojos, projectionClass);
        PreparedExecution prepared;
        if (containsSubqueries(ast)) {
            prepared = buildPreparedExecution(
                    ast,
                    strictParameterTypes,
                    computedFieldRegistry,
                    sourceClass,
                    projectionClass,
                    pojos,
                    joinSources
            );
        } else {
            ExecutionShapeKey shapeKey = ExecutionShapeKey.of(ast, sourceClass, projectionClass, joinSources);
            prepared = preparedExecutions.computeIfAbsent(
                    shapeKey,
                    ignored -> buildPreparedExecution(
                            ast,
                            strictParameterTypes,
                            computedFieldRegistry,
                            sourceClass,
                            projectionClass,
                            pojos,
                            joinSources
                    )
            );
        }
        QueryTelemetrySupport.emit(
                telemetryListener,
                QueryTelemetryStage.BIND,
                "sql-like",
                source,
                bindStarted,
                pojos.size(),
                pojos.size(),
                QueryTelemetrySupport.metadata(
                        "projectionClass", projectionClass.getSimpleName(),
                        "joinSourceCount", joinSources.size(),
                        "applyJoin", prepared.applyJoin()
                )
        );
        return new ExecutionContext(prepared, pojos, joinSources, telemetryListener, source);
    }

    private static <T> PreparedExecution buildPreparedExecution(QueryAst ast,
                                                                boolean strictParameterTypes,
                                                                ComputedFieldRegistry computedFieldRegistry,
                                                                Class<?> sourceClass,
                                                                Class<T> projectionClass,
                                                                List<?> pojos,
                                                                Map<String, List<?>> joinSources) {
        QueryAst normalizedAst = SqlLikeValidator.validateForFilter(
                ast,
                sourceClass,
                projectionClass,
                joinSources,
                strictParameterTypes,
                computedFieldRegistry
        );
        FilterQueryBuilder boundBuilder = (FilterQueryBuilder) SqlLikeBinder.bind(
                normalizedAst,
                pojos,
                joinSources,
                sourceClass,
                computedFieldRegistry
        );
        FilterExecutionPlanCacheKey rawExecutionPlanCacheKey =
                shouldCacheRawExecutionPlan(normalizedAst, boundBuilder)
                        ? FilterExecutionPlanCacheKey.from(boundBuilder)
                        : null;
        return new PreparedExecution(
                boundBuilder.snapshotForPreparedExecution(),
                extractJoinSourceNames(normalizedAst),
                SqlLikeBinder.resolveSort(normalizedAst),
                normalizedAst.hasJoins(),
                normalizedAst.select(),
                rawExecutionPlanCacheKey
        );
    }

    private static boolean shouldCacheRawExecutionPlan(QueryAst ast, FilterQueryBuilder builder) {
        return !ast.hasJoins()
                && (!builder.getMetrics().isEmpty()
                || !builder.getGroupFields().isEmpty()
                || !builder.getTimeBuckets().isEmpty());
    }

    private static List<String> extractJoinSourceNames(QueryAst ast) {
        if (!ast.hasJoins()) {
            return List.of();
        }
        ArrayList<String> joinSourceNames = new ArrayList<>(ast.joins().size());
        ast.joins().forEach(join -> joinSourceNames.add(join.childSource()));
        return List.copyOf(joinSourceNames);
    }

    private static Class<?> inferJoinSourceClass(List<?> rows) {
        if (rows == null || rows.isEmpty()) {
            throw SqlLikeErrors.argument(SqlLikeErrorCodes.VALIDATION_INVALID_JOIN_ROWS,
                    "JOIN source rows must not be null/empty");
        }
        for (Object row : rows) {
            if (row != null) {
                return row.getClass();
            }
        }
        throw SqlLikeErrors.argument(SqlLikeErrorCodes.VALIDATION_INVALID_JOIN_ROWS,
                "JOIN source rows must contain at least one non-null element");
    }

    private static boolean containsSubqueries(QueryAst ast) {
        for (FilterAst filter : ast.filters()) {
            if (filter.value() instanceof SubqueryValueAst) {
                return true;
            }
        }
        for (FilterAst filter : ast.havingFilters()) {
            if (filter.value() instanceof SubqueryValueAst) {
                return true;
            }
        }
        return containsSubquery(ast.whereExpression()) || containsSubquery(ast.havingExpression());
    }

    private static boolean containsSubquery(FilterExpressionAst expression) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            return predicateAst.filter().value() instanceof SubqueryValueAst;
        }
        FilterBinaryAst binaryAst = (FilterBinaryAst) expression;
        return containsSubquery(binaryAst.left()) || containsSubquery(binaryAst.right());
    }

    static final class ExecutionContext {
        private final PreparedExecution prepared;
        private final List<?> pojos;
        private final Map<String, List<?>> joinSources;
        private final QueryTelemetryListener telemetryListener;
        private final String source;

        ExecutionContext(PreparedExecution prepared,
                         List<?> pojos,
                         Map<String, List<?>> joinSources,
                         QueryTelemetryListener telemetryListener,
                         String source) {
            this.prepared = prepared;
            this.pojos = pojos;
            this.joinSources = joinSources;
            this.telemetryListener = telemetryListener;
            this.source = source;
        }

        FilterQueryBuilder newExecutionBuilder() {
            return prepared.newExecutionBuilder(pojos, joinSources, telemetryListener, source);
        }

        Sort sort() {
            return prepared.sort();
        }

        boolean applyJoin() {
            return prepared.applyJoin();
        }

        SelectAst select() {
            return prepared.select();
        }

        FilterExecutionPlan resolveRawExecutionPlan(FilterCore core, FilterQueryBuilder builder) {
            FilterExecutionPlanCacheKey cachedKey = prepared.rawExecutionPlanCacheKey();
            if (cachedKey == null) {
                return core.buildExecutionPlan();
            }
            return builder.getExecutionPlanCache().getOrBuild(cachedKey, core::buildExecutionPlan);
        }

        FilterExecutionPlanCacheKey rawExecutionPlanCacheKey() {
            return prepared.rawExecutionPlanCacheKey();
        }

        ExecutionRun newRun() {
            return new ExecutionRun(this);
        }
    }

    static final class ExecutionRun {
        private final ExecutionContext context;
        private FilterQueryBuilder builder;
        private FastStatsQuerySupport.FastStatsState fastStatsState;
        private boolean fastStatsResolved;

        ExecutionRun(ExecutionContext context) {
            this.context = context;
        }

        FilterQueryBuilder builder() {
            if (builder == null) {
                builder = context.newExecutionBuilder();
            }
            return builder;
        }

        FastStatsQuerySupport.FastStatsState fastStatsState() {
            if (!fastStatsResolved) {
                if (!context.applyJoin()) {
                    fastStatsState = FastStatsQuerySupport.tryBuildState(
                            builder(),
                            context.rawExecutionPlanCacheKey()
                    );
                }
                fastStatsResolved = true;
            }
            return fastStatsState;
        }

        boolean applyJoin() {
            return context.applyJoin();
        }

        Sort sort() {
            return context.sort();
        }

        FilterExecutionPlan resolveRawExecutionPlan(FilterCore core) {
            return context.resolveRawExecutionPlan(core, builder());
        }
    }

    static final class PreparedExecution {
        private final FilterQueryBuilder templateBuilder;
        private final List<String> joinSourceNames;
        private final Sort sort;
        private final boolean applyJoin;
        private final SelectAst select;
        private final FilterExecutionPlanCacheKey rawExecutionPlanCacheKey;

        PreparedExecution(FilterQueryBuilder templateBuilder,
                          List<String> joinSourceNames,
                          Sort sort,
                          boolean applyJoin,
                          SelectAst select,
                          FilterExecutionPlanCacheKey rawExecutionPlanCacheKey) {
            this.templateBuilder = templateBuilder;
            this.joinSourceNames = joinSourceNames;
            this.sort = sort;
            this.applyJoin = applyJoin;
            this.select = select;
            this.rawExecutionPlanCacheKey = rawExecutionPlanCacheKey;
        }

        private FilterQueryBuilder newExecutionBuilder(List<?> pojos,
                                                       Map<String, List<?>> joinSources,
                                                       QueryTelemetryListener telemetryListener,
                                                       String source) {
            FilterQueryBuilder builder = templateBuilder.preparedExecutionView(
                    pojos,
                    joinSourcesByIndex(joinSources)
            );
            builder.telemetry(telemetryListener);
            builder.telemetryContext("sql-like", source, telemetryListener);
            return builder;
        }

        private Map<Integer, List<?>> joinSourcesByIndex(Map<String, List<?>> joinSources) {
            if (joinSourceNames.isEmpty()) {
                return Collections.emptyMap();
            }
            LinkedHashMap<Integer, List<?>> byIndex = new LinkedHashMap<>(joinSourceNames.size());
            for (int i = 0; i < joinSourceNames.size(); i++) {
                String joinSourceName = joinSourceNames.get(i);
                List<?> rows = joinSources.get(joinSourceName);
                if (rows == null) {
                    throw SqlLikeErrors.argument(SqlLikeErrorCodes.VALIDATION_MISSING_JOIN_SOURCE,
                            "Missing JOIN source binding for '" + joinSourceName + "'");
                }
                byIndex.put(i + 1, rows);
            }
            return byIndex;
        }

        Sort sort() {
            return sort;
        }

        boolean applyJoin() {
            return applyJoin;
        }

        SelectAst select() {
            return select;
        }

        FilterExecutionPlanCacheKey rawExecutionPlanCacheKey() {
            return rawExecutionPlanCacheKey;
        }
    }

    static record ExecutionShapeKey(Class<?> sourceClass,
                                    Class<?> projectionClass,
                                    List<JoinSourceShape> joinSources) {
        static ExecutionShapeKey of(QueryAst ast,
                                    Class<?> sourceClass,
                                    Class<?> projectionClass,
                                    Map<String, List<?>> joinSources) {
            ArrayList<JoinSourceShape> joinShapes = new ArrayList<>(ast.joins().size());
            ast.joins().forEach(join -> {
                List<?> rows = joinSources.get(join.childSource());
                if (rows == null) {
                    throw SqlLikeErrors.argument(SqlLikeErrorCodes.VALIDATION_MISSING_JOIN_SOURCE,
                            "Missing JOIN source binding for '" + join.childSource() + "'");
                }
                joinShapes.add(new JoinSourceShape(join.childSource(), inferJoinSourceClass(rows)));
            });
            return new ExecutionShapeKey(sourceClass, projectionClass, List.copyOf(joinShapes));
        }
    }

    private record JoinSourceShape(String sourceName, Class<?> rowClass) {
    }
}
