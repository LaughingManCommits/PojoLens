package laughing.man.commits.natural;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.filter.FilterExecutionPlanCacheStore;
import laughing.man.commits.filter.internal.DefaultFilterExecutionPlanCacheSupport;
import laughing.man.commits.natural.parser.NaturalQueryParser;
import laughing.man.commits.natural.parser.NaturalQueryParseResult;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.QueryExecutionGuard;
import laughing.man.commits.sqllike.QueryExposurePolicy;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.SqlParams;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.internal.execution.SqlLikeExecutionSupport;
import laughing.man.commits.sqllike.internal.params.SqlLikeParameterSupport;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.telemetry.QueryTelemetryListener;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.stream.Stream;

/**
 * Controlled plain-English query contract lowered into the shared engine.
 */
public final class NaturalQuery {

    private final String source;
    private final String equivalentSqlLike;
    private final QueryState state;
    private final Cache<ResolutionShapeKey, ResolvedExecution> resolvedExecutions;

    private NaturalQuery(String source, String equivalentSqlLike, QueryState state) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.equivalentSqlLike = Objects.requireNonNull(equivalentSqlLike, "equivalentSqlLike must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.resolvedExecutions = Caffeine.newBuilder()
                .maximumSize(256)
                .expireAfterAccess(Duration.ofMinutes(30))
                .build();
    }

    public static NaturalQuery of(String source) {
        String nonNullSource = Objects.requireNonNull(source, "source must not be null");
        NaturalQueryParseResult parseResult = NaturalQueryParser.parseResult(nonNullSource);
        String normalizedSource = nonNullSource.trim();
        String equivalentSqlLike = NaturalQueryRenderer.toSqlLike(parseResult.ast());
        return new NaturalQuery(
                normalizedSource,
                equivalentSqlLike,
                QueryState.of(parseResult)
        );
    }

    public String source() {
        return source;
    }

    public String equivalentSqlLike() {
        return equivalentSqlLike;
    }

    QueryAst ast() {
        return state.ast();
    }

    public NaturalQuery params(Map<String, ?> parameters) {
        return withState(state.withAst(SqlLikeParameterSupport.bind(state.ast(), parameters)));
    }

    public NaturalQuery params(SqlParams parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        return params(parameters.asMap());
    }

    public NaturalQuery strictParameterTypes() {
        return strictParameterTypes(true);
    }

    public NaturalQuery strictParameterTypes(boolean enabled) {
        return state.strictParameterTypes() == enabled ? this : withState(state.withStrictParameterTypes(enabled));
    }

    public boolean isStrictParameterTypesEnabled() {
        return state.strictParameterTypes();
    }

    public NaturalQuery lintMode() {
        return lintMode(true);
    }

    public NaturalQuery lintMode(boolean enabled) {
        return state.lintMode() == enabled ? this : withState(state.withLintMode(enabled));
    }

    public boolean isLintModeEnabled() {
        return state.lintMode();
    }

    /**
     * Returns pre-execution diagnostics for this natural query by inspecting the
     * equivalent SQL-like representation. No source class is required; field
     * existence is not validated.
     *
     * @return diagnostics with structural metadata and lint warnings
     */
    public QueryDiagnostics diagnostics() {
        return createDelegate(state.ast()).diagnostics();
    }

    /**
     * Returns pre-execution diagnostics for this natural query including field
     * and source validation against the provided classes.
     *
     * @param sourceClass     class whose fields are queryable
     * @param projectionClass class that receives query output
     * @return diagnostics with validation findings, structural metadata, and lint warnings
     */
    public QueryDiagnostics diagnostics(Class<?> sourceClass, Class<?> projectionClass) {
        Objects.requireNonNull(sourceClass, "sourceClass must not be null");
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        NaturalQueryResolutionSupport.ResolvedNaturalQuery resolved = resolveForDiagnostics(sourceClass);
        return createDelegate(resolved.ast())
                .diagnostics(sourceClass, projectionClass);
    }

    public NaturalQuery telemetry(QueryTelemetryListener listener) {
        return state.telemetryListener() == listener ? this : withState(state.withTelemetryListener(listener));
    }

    public QueryTelemetryListener telemetryListener() {
        return state.telemetryListener();
    }

    public NaturalQuery computedFields(ComputedFieldRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        return state.computedFieldRegistry() == registry ? this : withState(state.withComputedFieldRegistry(registry));
    }

    public ComputedFieldRegistry computedFieldRegistry() {
        return state.computedFieldRegistry();
    }

    public NaturalQuery exposurePolicy(QueryExposurePolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        return state.exposurePolicy() == policy ? this : withState(state.withExposurePolicy(policy));
    }

    public QueryExposurePolicy exposurePolicy() {
        return state.exposurePolicy();
    }

    public NaturalQuery executionPlanCache(FilterExecutionPlanCacheStore executionPlanCache) {
        Objects.requireNonNull(executionPlanCache, "executionPlanCache must not be null");
        return state.executionPlanCache() == executionPlanCache ? this : withState(state.withExecutionPlanCache(executionPlanCache));
    }

    /**
     * Returns a query with the given execution guard applied.
     *
     * @param guard execution guard; must not be null
     * @return query with guard attached
     */
    public NaturalQuery executionGuard(QueryExecutionGuard guard) {
        Objects.requireNonNull(guard, "guard must not be null");
        return state.executionGuard() == guard ? this : withState(state.withExecutionGuard(guard));
    }

    /**
     * Returns the execution guard attached to this query.
     *
     * @return execution guard
     */
    public QueryExecutionGuard executionGuard() {
        return state.executionGuard();
    }

    NaturalQuery vocabulary(NaturalVocabulary vocabulary) {
        Objects.requireNonNull(vocabulary, "vocabulary must not be null");
        return state.vocabulary() == vocabulary ? this : withState(state.withVocabulary(vocabulary));
    }

    public <T> NaturalBoundQuery<T> bindTyped(List<?> pojos, Class<T> projectionClass) {
        return bindTyped(pojos, projectionClass, JoinBindings.empty());
    }

    public <T> NaturalBoundQuery<T> bindTyped(DatasetBundle datasetBundle, Class<T> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return bindTyped(datasetBundle.primaryRows(), projectionClass, datasetBundle.joinBindings());
    }

    public <T> NaturalBoundQuery<T> bindTyped(List<?> pojos,
                                              Class<T> projectionClass,
                                              JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        ResolvedExecution resolvedExecution = resolvedExecution(pojos, joinBindings.asMap(), projectionClass);
        return new DefaultNaturalBoundQuery<>(
                resolvedExecution.delegate().bindTyped(pojos, projectionClass, joinBindings),
                resolvedExecution.resolved().ast(),
                state.chartType()
        );
    }

    public <T> List<T> filter(List<?> pojos, Class<T> projectionClass) {
        return resolvedDelegate(pojos, Map.of(), projectionClass).filter(pojos, projectionClass);
    }

    public <T> List<T> filter(DatasetBundle datasetBundle, Class<T> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return resolvedDelegate(datasetBundle.primaryRows(), datasetBundle.joinBindings().asMap(), projectionClass)
                .filter(datasetBundle, projectionClass);
    }

    public <T> List<T> filter(List<?> pojos, JoinBindings joinBindings, Class<T> projectionClass) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return resolvedDelegate(pojos, joinBindings.asMap(), projectionClass).filter(pojos, joinBindings, projectionClass);
    }

    public <T> Iterator<T> iterator(List<?> pojos, Class<T> projectionClass) {
        return resolvedDelegate(pojos, Map.of(), projectionClass).iterator(pojos, projectionClass);
    }

    public <T> Iterator<T> iterator(DatasetBundle datasetBundle, Class<T> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return resolvedDelegate(datasetBundle.primaryRows(), datasetBundle.joinBindings().asMap(), projectionClass)
                .iterator(datasetBundle, projectionClass);
    }

    public <T> Iterator<T> iterator(List<?> pojos, JoinBindings joinBindings, Class<T> projectionClass) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return resolvedDelegate(pojos, joinBindings.asMap(), projectionClass).iterator(pojos, joinBindings, projectionClass);
    }

    public <T> Stream<T> stream(List<?> pojos, Class<T> projectionClass) {
        return resolvedDelegate(pojos, Map.of(), projectionClass).stream(pojos, projectionClass);
    }

    public <T> Stream<T> stream(DatasetBundle datasetBundle, Class<T> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return resolvedDelegate(datasetBundle.primaryRows(), datasetBundle.joinBindings().asMap(), projectionClass)
                .stream(datasetBundle, projectionClass);
    }

    public <T> Stream<T> stream(List<?> pojos, JoinBindings joinBindings, Class<T> projectionClass) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return resolvedDelegate(pojos, joinBindings.asMap(), projectionClass).stream(pojos, joinBindings, projectionClass);
    }

    public <T> ChartData chart(List<?> pojos, Class<T> projectionClass, ChartSpec spec) {
        return resolvedDelegate(pojos, Map.of(), projectionClass).chart(pojos, projectionClass, spec);
    }

    public <T> ChartData chart(List<?> pojos, Class<T> projectionClass) {
        ResolvedExecution resolvedExecution = resolvedExecution(pojos, Map.of(), projectionClass);
        return resolvedExecution.delegate().chart(
                pojos,
                projectionClass,
                NaturalChartSupport.inferChartSpec(resolvedExecution.resolved().ast(), state.chartType())
        );
    }

    public <T> ChartData chart(DatasetBundle datasetBundle, Class<T> projectionClass, ChartSpec spec) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return resolvedDelegate(datasetBundle.primaryRows(), datasetBundle.joinBindings().asMap(), projectionClass)
                .chart(datasetBundle, projectionClass, spec);
    }

    public <T> ChartData chart(DatasetBundle datasetBundle, Class<T> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return chart(datasetBundle.primaryRows(), datasetBundle.joinBindings(), projectionClass);
    }

    public <T> ChartData chart(List<?> pojos,
                               JoinBindings joinBindings,
                               Class<T> projectionClass,
                               ChartSpec spec) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return resolvedDelegate(pojos, joinBindings.asMap(), projectionClass).chart(pojos, joinBindings, projectionClass, spec);
    }

    public <T> ChartData chart(List<?> pojos,
                               JoinBindings joinBindings,
                               Class<T> projectionClass) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        ResolvedExecution resolvedExecution = resolvedExecution(pojos, joinBindings.asMap(), projectionClass);
        return resolvedExecution.delegate().chart(
                pojos,
                joinBindings,
                projectionClass,
                NaturalChartSupport.inferChartSpec(resolvedExecution.resolved().ast(), state.chartType())
        );
    }

    public Sort sort() {
        return createDelegate(state.ast()).sort();
    }

    public TabularSchema schema(Class<?> projectionClass) {
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        NaturalQueryResolutionSupport.ResolvedNaturalQuery resolved = resolveForSchema(projectionClass);
        return createDelegate(resolved.ast()).schema(projectionClass);
    }

    public TabularSchema schema(List<?> pojos, Class<?> projectionClass) {
        return schema(pojos, JoinBindings.empty(), projectionClass);
    }

    public TabularSchema schema(DatasetBundle datasetBundle, Class<?> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return schema(datasetBundle.primaryRows(), datasetBundle.joinBindings(), projectionClass);
    }

    public TabularSchema schema(List<?> pojos, JoinBindings joinBindings, Class<?> projectionClass) {
        Objects.requireNonNull(pojos, "pojos must not be null");
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        return resolvedExecution(pojos, joinBindings.asMap(), projectionClass).delegate().schema(projectionClass);
    }

    public Map<String, Object> explain() {
        return addExplainMetadata(createDelegate(state.ast()).explain(), null);
    }

    public <T> Map<String, Object> explain(List<?> pojos, Class<T> projectionClass) {
        ResolvedExecution resolvedExecution = resolvedExecution(pojos, Map.of(), projectionClass);
        return addExplainMetadata(
                resolvedExecution.delegate().explain(pojos, projectionClass),
                resolvedExecution.resolved()
        );
    }

    public <T> Map<String, Object> explain(DatasetBundle datasetBundle, Class<T> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        ResolvedExecution resolvedExecution =
                resolvedExecution(datasetBundle.primaryRows(), datasetBundle.joinBindings().asMap(), projectionClass);
        return addExplainMetadata(
                resolvedExecution.delegate().explain(datasetBundle, projectionClass),
                resolvedExecution.resolved()
        );
    }

    public <T> Map<String, Object> explain(List<?> pojos,
                                           JoinBindings joinBindings,
                                           Class<T> projectionClass) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        ResolvedExecution resolvedExecution = resolvedExecution(pojos, joinBindings.asMap(), projectionClass);
        return addExplainMetadata(
                resolvedExecution.delegate().explain(pojos, joinBindings, projectionClass),
                resolvedExecution.resolved()
        );
    }

    private NaturalQuery withState(QueryState updatedState) {
        return updatedState == state ? this : new NaturalQuery(source, equivalentSqlLike, updatedState);
    }

    private SqlLikeQuery resolvedDelegate(List<?> pojos,
                                          Map<String, List<?>> joinSources,
                                          Class<?> projectionClass) {
        return resolvedExecution(pojos, joinSources, projectionClass).delegate();
    }

    private ResolvedExecution resolvedExecution(List<?> pojos,
                                                Map<String, List<?>> joinSources,
                                                Class<?> projectionClass) {
        Map<String, List<?>> effectiveJoinSources = joinSources == null ? Map.of() : joinSources;
        ResolutionShapeKey shapeKey = ResolutionShapeKey.of(state.ast(), pojos, effectiveJoinSources, projectionClass);
        return resolvedExecutions.get(shapeKey, ignored -> {
            NaturalQueryResolutionSupport.ResolvedNaturalQuery resolved =
                    resolve(pojos, effectiveJoinSources, projectionClass);
            return new ResolvedExecution(resolved, createDelegate(resolved.ast()));
        });
    }

    private NaturalQueryResolutionSupport.ResolvedNaturalQuery resolve(List<?> pojos,
                                                                      Map<String, List<?>> joinSources,
                                                                      Class<?> projectionClass) {
        Map<String, List<?>> effectiveJoinSources = joinSources == null ? Map.of() : joinSources;
        if (state.ast().hasJoins() && hasUnboundJoinSources(effectiveJoinSources)) {
            return NaturalQueryResolutionSupport.passthrough(state.ast(), equivalentSqlLike);
        }
        Class<?> sourceClass = SqlLikeExecutionSupport.inferSourceClass(pojos, projectionClass);
        Set<String> allowedFields = new LinkedHashSet<>(ReflectionUtil.collectQueryableFieldNames(sourceClass));
        allowedFields.addAll(state.computedFieldRegistry().names());
        String rootSourceName = state.ast().select() == null ? null : state.ast().select().sourceName();
        if (rootSourceName != null) {
            addQualifiedFields(allowedFields, rootSourceName, sourceClass);
        }
        for (Map.Entry<String, List<?>> entry : effectiveJoinSources.entrySet()) {
            Class<?> joinSourceClass = SqlLikeExecutionSupport.inferSourceClass(entry.getValue(), projectionClass);
            addQualifiedFields(allowedFields, entry.getKey(), joinSourceClass);
        }
        return NaturalQueryResolutionSupport.resolve(
                new NaturalQueryParseResult(state.ast(), state.sourceFieldPhrases(), state.chartType()),
                allowedFields,
                state.vocabulary()
        );
    }

    private NaturalQueryResolutionSupport.ResolvedNaturalQuery resolveForSchema(Class<?> projectionClass) {
        if (state.ast().hasJoins()) {
            return NaturalQueryResolutionSupport.passthrough(state.ast(), equivalentSqlLike);
        }
        Set<String> allowedFields = new LinkedHashSet<>(ReflectionUtil.collectQueryableFieldNames(projectionClass));
        allowedFields.addAll(state.computedFieldRegistry().names());
        addVocabularyTargets(allowedFields, state.vocabulary());
        addUnaliasedSourcePhraseFields(allowedFields, state.sourceFieldPhrases(), state.vocabulary());
        String rootSourceName = state.ast().select() == null ? null : state.ast().select().sourceName();
        if (rootSourceName != null) {
            addQualifiedVocabularyTargets(allowedFields, rootSourceName, state.vocabulary());
        }
        return NaturalQueryResolutionSupport.resolve(
                new NaturalQueryParseResult(state.ast(), state.sourceFieldPhrases(), state.chartType()),
                allowedFields,
                state.vocabulary()
        );
    }

    private NaturalQueryResolutionSupport.ResolvedNaturalQuery resolveForDiagnostics(Class<?> sourceClass) {
        if (state.ast().hasJoins()) {
            return NaturalQueryResolutionSupport.passthrough(state.ast(), equivalentSqlLike);
        }
        Set<String> allowedFields = new LinkedHashSet<>(ReflectionUtil.collectQueryableFieldNames(sourceClass));
        allowedFields.addAll(state.computedFieldRegistry().names());
        addVocabularyTargets(allowedFields, state.vocabulary());
        addUnaliasedSourcePhraseFields(allowedFields, state.sourceFieldPhrases(), state.vocabulary());
        String rootSourceName = state.ast().select() == null ? null : state.ast().select().sourceName();
        if (rootSourceName != null) {
            addQualifiedVocabularyTargets(allowedFields, rootSourceName, state.vocabulary());
            addQualifiedFields(allowedFields, rootSourceName, sourceClass);
        }
        return NaturalQueryResolutionSupport.resolve(
                new NaturalQueryParseResult(state.ast(), state.sourceFieldPhrases(), state.chartType()),
                allowedFields,
                state.vocabulary()
        );
    }

    private static void addVocabularyTargets(Set<String> allowedFields, NaturalVocabulary vocabulary) {
        for (List<String> targets : vocabulary.aliases().values()) {
            allowedFields.addAll(targets);
        }
    }

    private static void addUnaliasedSourcePhraseFields(Set<String> allowedFields,
                                                       Map<String, String> sourceFieldPhrases,
                                                       NaturalVocabulary vocabulary) {
        for (String naturalField : sourceFieldPhrases.values()) {
            if (vocabulary.resolveAliasTargets(naturalField).isEmpty()) {
                allowedFields.add(naturalField);
            }
        }
    }

    private static void addQualifiedVocabularyTargets(Set<String> allowedFields,
                                                     String sourceName,
                                                     NaturalVocabulary vocabulary) {
        for (List<String> targets : vocabulary.aliases().values()) {
            for (String target : targets) {
                allowedFields.add(sourceName + "." + target);
            }
        }
    }

    private boolean hasUnboundJoinSources(Map<String, List<?>> joinSources) {
        for (var join : state.ast().joins()) {
            if (!joinSources.containsKey(join.childSource())) {
                return true;
            }
        }
        return false;
    }

    private static void addQualifiedFields(Set<String> allowedFields,
                                           String sourceName,
                                           Class<?> sourceClass) {
        for (String fieldName : ReflectionUtil.collectQueryableFieldNames(sourceClass)) {
            allowedFields.add(fieldName);
            allowedFields.add(sourceName + "." + fieldName);
        }
    }

    private SqlLikeQuery createDelegate(QueryAst ast) {
        return SqlLikeQuery.fromAst(source, equivalentSqlLike, "natural", ast)
                .strictParameterTypes(state.strictParameterTypes())
                .lintMode(state.lintMode())
                .computedFields(state.computedFieldRegistry())
                .exposurePolicy(state.exposurePolicy())
                .executionPlanCache(state.executionPlanCache())
                .telemetry(state.telemetryListener())
                .executionGuard(state.executionGuard());
    }

    private Map<String, Object> addExplainMetadata(Map<String, Object> explain,
                                                   NaturalQueryResolutionSupport.ResolvedNaturalQuery resolved) {
        LinkedHashMap<String, Object> updated = new LinkedHashMap<>(explain);
        updated.put("equivalentSqlLike", equivalentSqlLike);
        if (state.chartType() != null) {
            updated.put("naturalChartType", state.chartType().name());
            try {
                updated.put(
                        resolved == null ? "naturalChartSpec" : "resolvedNaturalChartSpec",
                        NaturalChartSupport.describeInferredChart(
                                resolved == null ? state.ast() : resolved.ast(),
                                state.chartType()
                        )
                );
            } catch (IllegalArgumentException ex) {
                updated.put("naturalChartInferenceError", ex.getMessage());
            }
        }
        if (resolved != null) {
            updated.put("resolvedNaturalFields", explainableResolutionMappings(resolved.resolvedByOriginalPhrase()));
            updated.put("resolvedEquivalentSqlLike", resolved.equivalentSqlLike());
        }
        return Collections.unmodifiableMap(updated);
    }

    private static Map<String, String> explainableResolutionMappings(Map<String, String> resolvedByOriginalPhrase) {
        if (resolvedByOriginalPhrase.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : resolvedByOriginalPhrase.entrySet()) {
            String normalizedOriginal = NaturalVocabularySupport.normalizeNaturalFieldToken(entry.getKey());
            if (!normalizedOriginal.equals(entry.getValue())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(filtered);
    }

    private record QueryState(QueryAst ast,
                              Map<String, String> sourceFieldPhrases,
                              boolean strictParameterTypes,
                              boolean lintMode,
                              QueryTelemetryListener telemetryListener,
                              ComputedFieldRegistry computedFieldRegistry,
                              FilterExecutionPlanCacheStore executionPlanCache,
                              QueryExposurePolicy exposurePolicy,
                              NaturalVocabulary vocabulary,
                              ChartType chartType,
                              QueryExecutionGuard executionGuard) {

        private QueryState {
            Objects.requireNonNull(ast, "ast must not be null");
            sourceFieldPhrases = Collections.unmodifiableMap(new LinkedHashMap<>(
                    sourceFieldPhrases == null ? Map.of() : sourceFieldPhrases
            ));
            computedFieldRegistry = computedFieldRegistry == null ? ComputedFieldRegistry.empty() : computedFieldRegistry;
            executionPlanCache = Objects.requireNonNull(executionPlanCache, "executionPlanCache must not be null");
            exposurePolicy = exposurePolicy == null ? QueryExposurePolicy.unrestricted() : exposurePolicy;
            vocabulary = vocabulary == null ? NaturalVocabulary.empty() : vocabulary;
            executionGuard = executionGuard == null ? QueryExecutionGuard.unrestricted() : executionGuard;
        }

        private static QueryState of(NaturalQueryParseResult parseResult) {
            return new QueryState(
                    parseResult.ast(),
                    parseResult.sourceFieldPhrases(),
                    false,
                    false,
                    null,
                    ComputedFieldRegistry.empty(),
                    DefaultFilterExecutionPlanCacheSupport.defaultStore(),
                    QueryExposurePolicy.unrestricted(),
                    NaturalVocabulary.empty(),
                    parseResult.chartType(),
                    QueryExecutionGuard.unrestricted()
            );
        }

        private QueryState withAst(QueryAst updatedAst) {
            return new QueryState(
                    updatedAst,
                    sourceFieldPhrases,
                    strictParameterTypes,
                    lintMode,
                    telemetryListener,
                    computedFieldRegistry,
                    executionPlanCache,
                    exposurePolicy,
                    vocabulary,
                    chartType,
                    executionGuard
            );
        }

        private QueryState withStrictParameterTypes(boolean enabled) {
            return new QueryState(
                    ast,
                    sourceFieldPhrases,
                    enabled,
                    lintMode,
                    telemetryListener,
                    computedFieldRegistry,
                    executionPlanCache,
                    exposurePolicy,
                    vocabulary,
                    chartType,
                    executionGuard
            );
        }

        private QueryState withLintMode(boolean enabled) {
            return new QueryState(
                    ast,
                    sourceFieldPhrases,
                    strictParameterTypes,
                    enabled,
                    telemetryListener,
                    computedFieldRegistry,
                    executionPlanCache,
                    exposurePolicy,
                    vocabulary,
                    chartType,
                    executionGuard
            );
        }

        private QueryState withTelemetryListener(QueryTelemetryListener listener) {
            return new QueryState(
                    ast,
                    sourceFieldPhrases,
                    strictParameterTypes,
                    lintMode,
                    listener,
                    computedFieldRegistry,
                    executionPlanCache,
                    exposurePolicy,
                    vocabulary,
                    chartType,
                    executionGuard
            );
        }

        private QueryState withComputedFieldRegistry(ComputedFieldRegistry registry) {
            return new QueryState(
                    ast,
                    sourceFieldPhrases,
                    strictParameterTypes,
                    lintMode,
                    telemetryListener,
                    registry,
                    executionPlanCache,
                    exposurePolicy,
                    vocabulary,
                    chartType,
                    executionGuard
            );
        }

        private QueryState withExecutionPlanCache(FilterExecutionPlanCacheStore cache) {
            return new QueryState(
                    ast,
                    sourceFieldPhrases,
                    strictParameterTypes,
                    lintMode,
                    telemetryListener,
                    computedFieldRegistry,
                    cache,
                    exposurePolicy,
                    vocabulary,
                    chartType,
                    executionGuard
            );
        }

        private QueryState withExposurePolicy(QueryExposurePolicy policy) {
            return new QueryState(
                    ast,
                    sourceFieldPhrases,
                    strictParameterTypes,
                    lintMode,
                    telemetryListener,
                    computedFieldRegistry,
                    executionPlanCache,
                    policy,
                    vocabulary,
                    chartType,
                    executionGuard
            );
        }

        private QueryState withVocabulary(NaturalVocabulary updatedVocabulary) {
            return new QueryState(
                    ast,
                    sourceFieldPhrases,
                    strictParameterTypes,
                    lintMode,
                    telemetryListener,
                    computedFieldRegistry,
                    executionPlanCache,
                    exposurePolicy,
                    updatedVocabulary,
                    chartType,
                    executionGuard
            );
        }

        private QueryState withExecutionGuard(QueryExecutionGuard guard) {
            return new QueryState(
                    ast,
                    sourceFieldPhrases,
                    strictParameterTypes,
                    lintMode,
                    telemetryListener,
                    computedFieldRegistry,
                    executionPlanCache,
                    exposurePolicy,
                    vocabulary,
                    chartType,
                    guard
            );
        }
    }

    private record ResolvedExecution(NaturalQueryResolutionSupport.ResolvedNaturalQuery resolved,
                                     SqlLikeQuery delegate) {
    }

    private record ResolutionShapeKey(Class<?> sourceClass,
                                      Class<?> projectionClass,
                                      List<JoinSourceShape> joinSources) {

        private static ResolutionShapeKey of(QueryAst ast,
                                             List<?> pojos,
                                             Map<String, List<?>> joinSources,
                                             Class<?> projectionClass) {
            Class<?> sourceClass = SqlLikeExecutionSupport.inferSourceClass(pojos, projectionClass);
            ArrayList<JoinSourceShape> joinShapes = new ArrayList<>();
            for (var entry : joinSources.entrySet()) {
                Class<?> joinSourceClass = SqlLikeExecutionSupport.inferSourceClass(entry.getValue(), projectionClass);
                joinShapes.add(new JoinSourceShape(entry.getKey(), joinSourceClass));
            }
            if (ast.hasJoins()) {
                for (var join : ast.joins()) {
                    if (!joinSources.containsKey(join.childSource())) {
                        joinShapes.add(new JoinSourceShape(join.childSource(), null));
                    }
                }
            }
            return new ResolutionShapeKey(sourceClass, projectionClass, List.copyOf(joinShapes));
        }
    }

    private record JoinSourceShape(String sourceName, Class<?> rowClass) {
    }

    private static final class DefaultNaturalBoundQuery<T> implements NaturalBoundQuery<T> {
        private final laughing.man.commits.sqllike.SqlLikeBoundQuery<T> delegate;
        private final QueryAst resolvedAst;
        private final ChartType chartType;

        private DefaultNaturalBoundQuery(laughing.man.commits.sqllike.SqlLikeBoundQuery<T> delegate,
                                         QueryAst resolvedAst,
                                         ChartType chartType) {
            this.delegate = delegate;
            this.resolvedAst = resolvedAst;
            this.chartType = chartType;
        }

        @Override
        public List<T> filter() {
            return delegate.filter();
        }

        @Override
        public Iterator<T> iterator() {
            return delegate.iterator();
        }

        @Override
        public Stream<T> stream() {
            return delegate.stream();
        }

        @Override
        public ChartData chart() {
            return delegate.chart(NaturalChartSupport.inferChartSpec(resolvedAst, chartType));
        }

        @Override
        public ChartData chart(ChartSpec spec) {
            return delegate.chart(spec);
        }
    }
}
