package laughing.man.commits.sqllike;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartMapper;
import laughing.man.commits.chart.ChartResultMapper;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.filter.FilterCore;
import laughing.man.commits.filter.FilterExecutionPlan;
import laughing.man.commits.filter.FilterExecutionPlanCacheKey;
import laughing.man.commits.filter.FastStatsQuerySupport;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.sqllike.internal.binding.SqlLikeBinder;
import laughing.man.commits.sqllike.internal.cursor.SqlLikeKeysetSupport;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.sqllike.internal.explain.SqlLikeExplainSupport;
import laughing.man.commits.sqllike.internal.execution.SqlLikeExecutionSupport;
import laughing.man.commits.sqllike.internal.lint.SqlLikeLintSupport;
import laughing.man.commits.sqllike.internal.params.SqlLikeParameterSupport;
import laughing.man.commits.sqllike.internal.validation.SqlLikeValidator;
import laughing.man.commits.sqllike.parser.SqlLikeParser;
import laughing.man.commits.telemetry.QueryTelemetryListener;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import laughing.man.commits.telemetry.internal.QueryTelemetrySupport;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.table.internal.TabularSchemaSupport;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * SQL-like query API contract.
 * <p>
 * This type captures user-provided SQL-like text and maps it to the existing
 * fluent PojoLens pipeline.
 */
public final class SqlLikeQuery {

    private final String source;
    private final QueryAst ast;
    private final boolean strictParameterTypes;
    private final boolean lintMode;
    private final Set<String> suppressedLintCodes;
    private final QueryTelemetryListener telemetryListener;
    private final ComputedFieldRegistry computedFieldRegistry;
    private final ConcurrentMap<ExecutionShapeKey, PreparedExecution> preparedExecutions;

    private SqlLikeQuery(String source, QueryAst ast) {
        this(source, ast, false, false, Collections.emptySet(), null, ComputedFieldRegistry.empty());
    }

    private SqlLikeQuery(String source, QueryAst ast, boolean strictParameterTypes) {
        this(source, ast, strictParameterTypes, false, Collections.emptySet(), null, ComputedFieldRegistry.empty());
    }

    private SqlLikeQuery(String source,
                         QueryAst ast,
                         boolean strictParameterTypes,
                         boolean lintMode,
                         Set<String> suppressedLintCodes,
                         QueryTelemetryListener telemetryListener,
                         ComputedFieldRegistry computedFieldRegistry) {
        this.source = source;
        this.ast = ast;
        this.strictParameterTypes = strictParameterTypes;
        this.lintMode = lintMode;
        this.suppressedLintCodes = Collections.unmodifiableSet(new LinkedHashSet<>(suppressedLintCodes));
        this.telemetryListener = telemetryListener;
        this.computedFieldRegistry = computedFieldRegistry == null ? ComputedFieldRegistry.empty() : computedFieldRegistry;
        this.preparedExecutions = new ConcurrentHashMap<>();
    }

    public static SqlLikeQuery of(String source) {
        if (source == null) {
            throw SqlLikeErrors.argument(SqlLikeErrorCodes.API_QUERY_NULL,
                    "SQL-like query must not be null");
        }
        String normalized = source.trim();
        if (normalized.isEmpty()) {
            throw SqlLikeErrors.argument(SqlLikeErrorCodes.API_QUERY_BLANK,
                    "SQL-like query must not be blank");
        }
        QueryAst ast = SqlLikeParser.parse(normalized);
        return new SqlLikeQuery(normalized, ast);
    }

    /**
     * Returns the normalized query string supplied by the caller.
     *
     * @return query text
     */
    public String source() {
        return source;
    }

    /**
     * Returns parsed AST for the SQL-like query.
     *
     * @return query AST
     */
    QueryAst ast() {
        return ast;
    }


    /**
     * Returns a query with named SQL-like parameters bound.
     * <p>
     * Example: {@code where department = :dept and salary >= :min}
     *
     * @param parameters values keyed by parameter name (without ':')
     * @return query with parameters resolved
     */
    public SqlLikeQuery params(Map<String, ?> parameters) {
        return new SqlLikeQuery(source,
                SqlLikeParameterSupport.bind(ast, parameters),
                strictParameterTypes,
                lintMode,
                suppressedLintCodes,
                telemetryListener,
                computedFieldRegistry);
    }

    /**
     * Returns a query with named SQL-like parameters bound.
     *
     * @param parameters typed SQL-like parameter container
     * @return query with parameters resolved
     */
    public SqlLikeQuery params(SqlParams parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        return params(parameters.asMap());
    }

    /**
     * Applies keyset "next page" cursor predicates aligned with this query's
     * {@code ORDER BY} fields.
     *
     * @param cursor keyset cursor values keyed by ORDER BY field name
     * @return query with cursor predicates merged into WHERE
     */
    public SqlLikeQuery keysetAfter(SqlLikeCursor cursor) {
        Objects.requireNonNull(cursor, "cursor must not be null");
        return new SqlLikeQuery(
                source,
                SqlLikeKeysetSupport.applyAfter(ast, cursor),
                strictParameterTypes,
                lintMode,
                suppressedLintCodes,
                telemetryListener,
                computedFieldRegistry
        );
    }

    /**
     * Applies keyset "previous page" cursor predicates aligned with this
     * query's {@code ORDER BY} fields.
     *
     * @param cursor keyset cursor values keyed by ORDER BY field name
     * @return query with cursor predicates merged into WHERE
     */
    public SqlLikeQuery keysetBefore(SqlLikeCursor cursor) {
        Objects.requireNonNull(cursor, "cursor must not be null");
        return new SqlLikeQuery(
                source,
                SqlLikeKeysetSupport.applyBefore(ast, cursor),
                strictParameterTypes,
                lintMode,
                suppressedLintCodes,
                telemetryListener,
                computedFieldRegistry
        );
    }

    /**
     * Enables strict parameter type validation for this query.
     *
     * @return query with strict parameter typing enabled
     */
    public SqlLikeQuery strictParameterTypes() {
        return strictParameterTypes(true);
    }

    /**
     * Configures strict parameter type validation for this query.
     *
     * @param enabled strict parameter typing toggle
     * @return query with updated strict parameter typing mode
     */
    public SqlLikeQuery strictParameterTypes(boolean enabled) {
        if (strictParameterTypes == enabled) {
            return this;
        }
        return new SqlLikeQuery(source, ast, enabled, lintMode, suppressedLintCodes, telemetryListener, computedFieldRegistry);
    }

    /**
     * Returns whether strict parameter typing is enabled.
     *
     * @return true when strict parameter typing is enabled
     */
    public boolean isStrictParameterTypesEnabled() {
        return strictParameterTypes;
    }

    /**
     * Enables SQL-like lint mode for explain diagnostics.
     *
     * @return query with lint mode enabled
     */
    public SqlLikeQuery lintMode() {
        return lintMode(true);
    }

    /**
     * Configures SQL-like lint mode for explain diagnostics.
     *
     * @param enabled lint mode toggle
     * @return query with updated lint mode
     */
    public SqlLikeQuery lintMode(boolean enabled) {
        if (lintMode == enabled) {
            return this;
        }
        return new SqlLikeQuery(source, ast, strictParameterTypes, enabled, suppressedLintCodes, telemetryListener, computedFieldRegistry);
    }

    /**
     * Returns whether lint mode is enabled for explain diagnostics.
     *
     * @return true when lint mode is enabled
     */
    public boolean isLintModeEnabled() {
        return lintMode;
    }

    public SqlLikeQuery telemetry(QueryTelemetryListener listener) {
        if (telemetryListener == listener) {
            return this;
        }
        return new SqlLikeQuery(source, ast, strictParameterTypes, lintMode, suppressedLintCodes, listener, computedFieldRegistry);
    }

    public QueryTelemetryListener telemetryListener() {
        return telemetryListener;
    }

    public SqlLikeQuery computedFields(ComputedFieldRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        if (computedFieldRegistry == registry) {
            return this;
        }
        return new SqlLikeQuery(source, ast, strictParameterTypes, lintMode, suppressedLintCodes, telemetryListener, registry);
    }

    public ComputedFieldRegistry computedFieldRegistry() {
        return computedFieldRegistry;
    }

    /**
     * Returns deterministic non-blocking lint warnings for this SQL-like query.
     *
     * @return lint warnings after suppression
     */
    public List<SqlLikeLintWarning> lintWarnings() {
        return SqlLikeLintSupport.warnings(ast, suppressedLintCodes);
    }

    /**
     * Suppresses the provided lint warning codes for this query.
     *
     * @param codes lint warning codes to suppress
     * @return query with updated lint suppression
     */
    public SqlLikeQuery suppressLintWarnings(String... codes) {
        Objects.requireNonNull(codes, "codes must not be null");
        TreeSet<String> normalized = new TreeSet<>(suppressedLintCodes);
        for (String code : codes) {
            if (StringUtil.isNullOrBlank(code)) {
                throw new IllegalArgumentException("lint warning code must not be null/blank");
            }
            normalized.add(code.trim());
        }
        if (normalized.equals(suppressedLintCodes)) {
            return this;
        }
        return new SqlLikeQuery(source, ast, strictParameterTypes, lintMode, normalized, telemetryListener, computedFieldRegistry);
    }

    /**
     * Binds query to data and captures projection type for typed execution.
     *
     * @param pojos input data
     * @param projectionClass projection/validation class
     * @param <T> projection type
     * @return typed SQL-like bound query
     */
    public <T> SqlLikeBoundQuery<T> bindTyped(List<?> pojos, Class<T> projectionClass) {
        return bindTyped(pojos, projectionClass, Collections.emptyMap());
    }

    /**
     * Binds query to an immutable dataset bundle and captures projection type
     * for typed execution.
     *
     * @param datasetBundle execution dataset bundle
     * @param projectionClass projection/validation class
     * @param <T> projection type
     * @return typed SQL-like bound query
     */
    public <T> SqlLikeBoundQuery<T> bindTyped(DatasetBundle datasetBundle, Class<T> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return bindTyped(datasetBundle.primaryRows(), projectionClass, datasetBundle.joinSources());
    }

    /**
     * Binds query with join sources and captures projection type for typed execution.
     *
     * @param pojos parent/source rows
     * @param projectionClass projection/validation class
     * @param joinSources join source rows keyed by JOIN source name
     * @param <T> projection type
     * @return typed SQL-like bound query
     */
    public <T> SqlLikeBoundQuery<T> bindTyped(List<?> pojos,
                                               Class<T> projectionClass,
                                               Map<String, List<?>> joinSources) {
        ExecutionContext context = prepareExecution(pojos, joinSources, projectionClass);
        return new DefaultSqlLikeBoundQuery<>(context, projectionClass);
    }

    /**
     * Binds query with typed join bindings and captures projection for typed execution.
     *
     * @param pojos parent/source rows
     * @param projectionClass projection/validation class
     * @param joinBindings typed join source bindings
     * @param <T> projection type
     * @return typed SQL-like bound query
     */
    public <T> SqlLikeBoundQuery<T> bindTyped(List<?> pojos,
                                               Class<T> projectionClass,
                                               JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return bindTyped(pojos, projectionClass, joinBindings.asMap());
    }

    /**
     * Executes this SQL-like query against the provided dataset.
     *
     * @param pojos input data
     * @param cls projection class
     * @param <T> projection type
     * @return filtered rows
     */
    public <T> List<T> filter(List<?> pojos, Class<T> cls) {
        return filter(pojos, Collections.emptyMap(), cls);
    }

    /**
     * Executes this SQL-like query against the provided dataset bundle.
     *
     * @param datasetBundle execution dataset bundle
     * @param cls projection class
     * @param <T> projection type
     * @return filtered rows
     */
    public <T> List<T> filter(DatasetBundle datasetBundle, Class<T> cls) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return filter(datasetBundle.primaryRows(), datasetBundle.joinSources(), cls);
    }

    /**
     * Executes query against provided rows with optional SQL-like JOIN sources.
     *
     * @param pojos parent/source rows
     * @param joinSources join source rows keyed by JOIN source name
     * @param cls projection class
     * @param <T> projection type
     * @return filtered rows
     */
    public <T> List<T> filter(List<?> pojos, Map<String, List<?>> joinSources, Class<T> cls) {
        ExecutionContext context = prepareExecution(pojos, joinSources, cls);
        return executeFilter(context, cls);
    }

    /**
     * Executes query with typed SQL-like JOIN source bindings.
     *
     * @param pojos parent/source rows
     * @param joinBindings typed join source bindings
     * @param cls projection class
     * @param <T> projection type
     * @return filtered rows
     */
    public <T> List<T> filter(List<?> pojos, JoinBindings joinBindings, Class<T> cls) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return filter(pojos, joinBindings.asMap(), cls);
    }

    /**
     * Executes this SQL-like query and exposes rows through an iterator.
     *
     * @param pojos input data
     * @param cls projection class
     * @param <T> projection type
     * @return result iterator
     */
    public <T> Iterator<T> iterator(List<?> pojos, Class<T> cls) {
        return stream(pojos, cls).iterator();
    }

    /**
     * Executes this SQL-like query against the provided dataset bundle and
     * exposes rows through an iterator.
     *
     * @param datasetBundle execution dataset bundle
     * @param cls projection class
     * @param <T> projection type
     * @return result iterator
     */
    public <T> Iterator<T> iterator(DatasetBundle datasetBundle, Class<T> cls) {
        return stream(datasetBundle, cls).iterator();
    }

    /**
     * Executes query against provided rows with optional SQL-like JOIN sources
     * and exposes rows through an iterator.
     *
     * @param pojos parent/source rows
     * @param joinSources join source rows keyed by JOIN source name
     * @param cls projection class
     * @param <T> projection type
     * @return result iterator
     */
    public <T> Iterator<T> iterator(List<?> pojos, Map<String, List<?>> joinSources, Class<T> cls) {
        return stream(pojos, joinSources, cls).iterator();
    }

    /**
     * Executes query with typed SQL-like JOIN source bindings and exposes rows
     * through an iterator.
     *
     * @param pojos parent/source rows
     * @param joinBindings typed join source bindings
     * @param cls projection class
     * @param <T> projection type
     * @return result iterator
     */
    public <T> Iterator<T> iterator(List<?> pojos, JoinBindings joinBindings, Class<T> cls) {
        return stream(pojos, joinBindings, cls).iterator();
    }

    /**
     * Executes this SQL-like query and exposes rows through a stream.
     *
     * @param pojos input data
     * @param cls projection class
     * @param <T> projection type
     * @return result stream
     */
    public <T> Stream<T> stream(List<?> pojos, Class<T> cls) {
        return stream(pojos, Collections.emptyMap(), cls);
    }

    /**
     * Executes this SQL-like query against the provided dataset bundle and
     * exposes rows through a stream.
     *
     * @param datasetBundle execution dataset bundle
     * @param cls projection class
     * @param <T> projection type
     * @return result stream
     */
    public <T> Stream<T> stream(DatasetBundle datasetBundle, Class<T> cls) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return stream(datasetBundle.primaryRows(), datasetBundle.joinSources(), cls);
    }

    /**
     * Executes query against provided rows with optional SQL-like JOIN sources
     * and exposes rows through a stream.
     *
     * @param pojos parent/source rows
     * @param joinSources join source rows keyed by JOIN source name
     * @param cls projection class
     * @param <T> projection type
     * @return result stream
     */
    public <T> Stream<T> stream(List<?> pojos, Map<String, List<?>> joinSources, Class<T> cls) {
        ExecutionContext context = prepareExecution(pojos, joinSources, cls);
        return executeStream(context, cls);
    }

    /**
     * Executes query with typed SQL-like JOIN source bindings and exposes rows
     * through a stream.
     *
     * @param pojos parent/source rows
     * @param joinBindings typed join source bindings
     * @param cls projection class
     * @param <T> projection type
     * @return result stream
     */
    public <T> Stream<T> stream(List<?> pojos, JoinBindings joinBindings, Class<T> cls) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return stream(pojos, joinBindings.asMap(), cls);
    }

    private <T> List<T> executeFilter(ExecutionContext context, Class<T> projectionClass) {
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

    private <T> Stream<T> executeStream(ExecutionContext context, Class<T> projectionClass) {
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

    /**
     * Executes SQL-like query and maps output rows to chart payload.
     *
     * @param pojos source rows
     * @param projectionClass projection class used by SQL-like execution
     * @param spec chart mapping spec
     * @param <T> projection type
     * @return chart payload
     */
    public <T> ChartData chart(List<?> pojos, Class<T> projectionClass, ChartSpec spec) {
        return chart(pojos, Collections.emptyMap(), projectionClass, spec);
    }

    /**
     * Executes SQL-like query against a dataset bundle and maps rows to chart
     * payload.
     *
     * @param datasetBundle execution dataset bundle
     * @param projectionClass projection class used by SQL-like execution
     * @param spec chart mapping spec
     * @param <T> projection type
     * @return chart payload
     */
    public <T> ChartData chart(DatasetBundle datasetBundle, Class<T> projectionClass, ChartSpec spec) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return chart(datasetBundle.primaryRows(), datasetBundle.joinSources(), projectionClass, spec);
    }

    /**
     * Executes SQL-like query with JOIN sources and maps output rows to chart payload.
     *
     * @param pojos source rows
     * @param joinSources join source rows keyed by source name
     * @param projectionClass projection class used by SQL-like execution
     * @param spec chart mapping spec
     * @param <T> projection type
     * @return chart payload
     */
    public <T> ChartData chart(List<?> pojos,
                               Map<String, List<?>> joinSources,
                               Class<T> projectionClass,
                               ChartSpec spec) {
        ExecutionContext context = prepareExecution(pojos, joinSources, projectionClass);
        return executeChart(context, projectionClass, spec);
    }

    /**
     * Executes SQL-like query with typed join bindings and maps rows to chart payload.
     *
     * @param pojos source rows
     * @param joinBindings typed join source bindings
     * @param projectionClass projection class used by SQL-like execution
     * @param spec chart mapping spec
     * @param <T> projection type
     * @return chart payload
     */
    public <T> ChartData chart(List<?> pojos,
                               JoinBindings joinBindings,
                               Class<T> projectionClass,
                               ChartSpec spec) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return chart(pojos, joinBindings.asMap(), projectionClass, spec);
    }

    /**
     * Resolves query sort direction from ORDER BY.
     *
     * @return sort direction, or null when ORDER BY is absent
     */
    public Sort sort() {
        return SqlLikeBinder.resolveSort(ast);
    }

    public TabularSchema schema(Class<?> projectionClass) {
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        return TabularSchemaSupport.fromSqlLikeQuery(ast, projectionClass);
    }

    /**
     * Returns deterministic debug metadata for this SQL-like query.
     *
     * @return explain payload
     */
    public Map<String, Object> explain() {
        return buildExplainPayload(Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Returns deterministic debug metadata for this SQL-like query including
     * stage row counts for the provided execution rows.
     *
     * @param pojos source rows
     * @param projectionClass projection class
     * @param <T> projection type
     * @return explain payload with stage row counts
     */
    public <T> Map<String, Object> explain(List<?> pojos, Class<T> projectionClass) {
        return explain(pojos, Collections.emptyMap(), projectionClass);
    }

    /**
     * Returns deterministic debug metadata for this SQL-like query including
     * stage row counts for a dataset bundle execution context.
     *
     * @param datasetBundle execution dataset bundle
     * @param projectionClass projection class
     * @param <T> projection type
     * @return explain payload with stage row counts
     */
    public <T> Map<String, Object> explain(DatasetBundle datasetBundle, Class<T> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return explain(datasetBundle.primaryRows(), datasetBundle.joinSources(), projectionClass);
    }

    /**
     * Returns deterministic debug metadata for this SQL-like query including
     * stage row counts for the provided execution rows and joins.
     *
     * @param pojos source rows
     * @param joinSources join source rows keyed by JOIN source name
     * @param projectionClass projection class
     * @param <T> projection type
     * @return explain payload with stage row counts
     */
    public <T> Map<String, Object> explain(List<?> pojos,
                                           Map<String, List<?>> joinSources,
                                           Class<T> projectionClass) {
        ExecutionContext context = prepareExecution(pojos, joinSources, projectionClass);
        return buildExplainPayload(joinSources, buildStageRowCounts(context));
    }

    /**
     * Returns deterministic debug metadata for this SQL-like query including
     * stage row counts for typed join bindings.
     *
     * @param pojos source rows
     * @param joinBindings typed join source bindings
     * @param projectionClass projection class
     * @param <T> projection type
     * @return explain payload with stage row counts
     */
    public <T> Map<String, Object> explain(List<?> pojos,
                                           JoinBindings joinBindings,
                                           Class<T> projectionClass) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return explain(pojos, joinBindings.asMap(), projectionClass);
    }

    private Map<String, Object> buildExplainPayload(Map<String, List<?>> joinSources,
                                                    Map<String, Object> stageRowCounts) {
        return SqlLikeExplainSupport.payload(source, ast, joinSources, stageRowCounts, lintMode, lintWarnings(), computedFieldRegistry);
    }

    private Map<String, Object> buildStageRowCounts(ExecutionContext context) {
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

    private static Map<String, Object> stageEntry(boolean applied, int before, int after) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("applied", applied);
        stage.put("before", before);
        stage.put("after", after);
        return Collections.unmodifiableMap(stage);
    }

    private <T> ChartData executeChart(ExecutionContext context, Class<T> projectionClass, ChartSpec spec) {
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
                emitChartTelemetry(chartStarted, projectedRows.size(), chart);
                return chart;
            }
            long chartStarted = QueryTelemetrySupport.start(telemetryListener);
            List<String> chartSchema =
                    select != null && !select.wildcard() && hasPlainFieldAliases(select)
                            ? SqlLikeExecutionSupport.aliasedOutputSchema(statsState.schemaFields(), select)
                            : statsState.schemaFields();
            ChartData chart = ChartMapper.toChartData(statsState.rows(), chartSchema, spec);
            emitChartTelemetry(chartStarted, statsState.rows().size(), chart);
            return chart;
        }
        List<QueryRow> rows = executeRawRows(run);
        if (select != null
                && !select.wildcard()
                && (select.hasComputedFields() || hasPlainFieldAliases(select))) {
            List<T> projectedRows = SqlLikeExecutionSupport.projectAliasedRows(rows, projectionClass, select);
            long chartStarted = QueryTelemetrySupport.start(telemetryListener);
            ChartData chart = ChartMapper.toChartData(projectedRows, spec);
            emitChartTelemetry(chartStarted, projectedRows.size(), chart);
            return chart;
        }
        long chartStarted = QueryTelemetrySupport.start(telemetryListener);
        ChartData chart = ChartResultMapper.toChartData(rows, spec);
        emitChartTelemetry(chartStarted, rows.size(), chart);
        return chart;
    }

    private List<QueryRow> executeRawRows(ExecutionRun run) {
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
        emitStage(working,
                QueryTelemetryStage.FILTER,
                filterStarted,
                distinctRows.size(),
                whereRows.size(),
                null);

        if (!working.getMetrics().isEmpty()) {
            long aggregateStarted = QueryTelemetrySupport.start(working.getTelemetryListener());
            List<QueryRow> groupedRows = core.aggregateMetrics(whereRows, plan);
            emitStage(working,
                    QueryTelemetryStage.AGGREGATE,
                    aggregateStarted,
                    whereRows.size(),
                    groupedRows.size(),
                    QueryTelemetrySupport.metadata("havingApplied", hasHavingPredicates(working)));
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
                emitStage(working,
                        QueryTelemetryStage.ORDER,
                        orderStarted,
                        havingRows.size(),
                        orderedRows.size(),
                        QueryTelemetrySupport.metadata("orderFieldCount", working.getOrderFields().size()));
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
            emitStage(working,
                    QueryTelemetryStage.ORDER,
                    orderStarted,
                    whereRows.size(),
                    orderedRows.size(),
                    QueryTelemetrySupport.metadata("orderFieldCount", working.getOrderFields().size()));
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

    private <T> ExecutionContext prepareExecution(List<?> pojos,
                                                  Map<String, List<?>> joinSources,
                                                  Class<T> projectionClass) {
        Objects.requireNonNull(pojos, "pojos must not be null");
        Objects.requireNonNull(joinSources, "joinSources must not be null");
        SqlLikeParameterSupport.assertFullyBound(ast);
        long bindStarted = QueryTelemetrySupport.start(telemetryListener);
        Class<?> sourceClass = SqlLikeExecutionSupport.inferSourceClass(pojos, projectionClass);
        PreparedExecution prepared;
        if (containsSubqueries(ast)) {
            prepared = buildPreparedExecution(sourceClass, projectionClass, pojos, joinSources);
        } else {
            ExecutionShapeKey shapeKey = ExecutionShapeKey.of(ast, sourceClass, projectionClass, joinSources);
            prepared = preparedExecutions.computeIfAbsent(
                    shapeKey,
                    ignored -> buildPreparedExecution(sourceClass, projectionClass, pojos, joinSources)
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
        return new ExecutionContext(prepared, pojos, joinSources);
    }

    private <T> PreparedExecution buildPreparedExecution(Class<?> sourceClass,
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

    private void emitChartTelemetry(long chartStarted, int rowCount, ChartData chart) {
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

    private final class ExecutionContext {
        private final PreparedExecution prepared;
        private final List<?> pojos;
        private final Map<String, List<?>> joinSources;

        private ExecutionContext(PreparedExecution prepared,
                                 List<?> pojos,
                                 Map<String, List<?>> joinSources) {
            this.prepared = prepared;
            this.pojos = pojos;
            this.joinSources = joinSources;
        }

        private FilterQueryBuilder newExecutionBuilder() {
            return prepared.newExecutionBuilder(pojos, joinSources, telemetryListener, source);
        }

        private Sort sort() {
            return prepared.sort();
        }

        private boolean applyJoin() {
            return prepared.applyJoin();
        }

        private SelectAst select() {
            return prepared.select();
        }

        private FilterExecutionPlan resolveRawExecutionPlan(FilterCore core, FilterQueryBuilder builder) {
            FilterExecutionPlanCacheKey cachedKey = prepared.rawExecutionPlanCacheKey();
            if (cachedKey == null) {
                return core.buildExecutionPlan();
            }
            return builder.getExecutionPlanCache().getOrBuild(cachedKey, core::buildExecutionPlan);
        }

        private FilterExecutionPlanCacheKey rawExecutionPlanCacheKey() {
            return prepared.rawExecutionPlanCacheKey();
        }

        private ExecutionRun newRun() {
            return new ExecutionRun(this);
        }
    }

    private final class ExecutionRun {
        private final ExecutionContext context;
        private FilterQueryBuilder builder;
        private FastStatsQuerySupport.FastStatsState fastStatsState;
        private boolean fastStatsResolved;

        private ExecutionRun(ExecutionContext context) {
            this.context = context;
        }

        private FilterQueryBuilder builder() {
            if (builder == null) {
                builder = context.newExecutionBuilder();
            }
            return builder;
        }

        private FastStatsQuerySupport.FastStatsState fastStatsState() {
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

        private boolean applyJoin() {
            return context.applyJoin();
        }

        private Sort sort() {
            return context.sort();
        }

        private FilterExecutionPlan resolveRawExecutionPlan(FilterCore core) {
            return context.resolveRawExecutionPlan(core, builder());
        }
    }

    private static final class PreparedExecution {
        private final FilterQueryBuilder templateBuilder;
        private final List<String> joinSourceNames;
        private final Sort sort;
        private final boolean applyJoin;
        private final SelectAst select;
        private final FilterExecutionPlanCacheKey rawExecutionPlanCacheKey;

        private PreparedExecution(FilterQueryBuilder templateBuilder,
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

        private Sort sort() {
            return sort;
        }

        private boolean applyJoin() {
            return applyJoin;
        }

        private SelectAst select() {
            return select;
        }

        private FilterExecutionPlanCacheKey rawExecutionPlanCacheKey() {
            return rawExecutionPlanCacheKey;
        }
    }

    private record ExecutionShapeKey(Class<?> sourceClass,
                                     Class<?> projectionClass,
                                     List<JoinSourceShape> joinSources) {
        private static ExecutionShapeKey of(QueryAst ast,
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

    private final class DefaultSqlLikeBoundQuery<T> implements SqlLikeBoundQuery<T> {
        private final ExecutionContext context;
        private final Class<T> projectionClass;

        private DefaultSqlLikeBoundQuery(ExecutionContext context, Class<T> projectionClass) {
            this.context = context;
            this.projectionClass = projectionClass;
        }

        @Override
        public List<T> filter() {
            return executeFilter(context, projectionClass);
        }

        @Override
        public Iterator<T> iterator() {
            return stream().iterator();
        }

        @Override
        public Stream<T> stream() {
            return executeStream(context, projectionClass);
        }

        @Override
        public ChartData chart(ChartSpec spec) {
            return executeChart(context, projectionClass, spec);
        }
    }

}
