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
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.SqlParams;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.internal.execution.SqlLikeExecutionSupport;
import laughing.man.commits.sqllike.internal.params.SqlLikeParameterSupport;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.telemetry.QueryTelemetryListener;
import laughing.man.commits.util.ReflectionUtil;

import java.util.Iterator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Controlled plain-English query contract lowered into the shared engine.
 */
public final class NaturalQuery {

    private final String source;
    private final String equivalentSqlLike;
    private final QueryState state;

    private NaturalQuery(String source, String equivalentSqlLike, QueryState state) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.equivalentSqlLike = Objects.requireNonNull(equivalentSqlLike, "equivalentSqlLike must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    public static NaturalQuery of(String source) {
        NaturalQueryParseResult parseResult = NaturalQueryParser.parseResult(source);
        String normalizedSource = source == null ? null : source.trim();
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

    public NaturalQuery executionPlanCache(FilterExecutionPlanCacheStore executionPlanCache) {
        Objects.requireNonNull(executionPlanCache, "executionPlanCache must not be null");
        return state.executionPlanCache() == executionPlanCache ? this : withState(state.withExecutionPlanCache(executionPlanCache));
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
        ResolvedExecution resolvedExecution = resolvedExecution(pojos, projectionClass);
        return new DefaultNaturalBoundQuery<>(
                resolvedExecution.delegate().bindTyped(pojos, projectionClass, joinBindings),
                resolvedExecution.resolved().ast(),
                state.chartType()
        );
    }

    public <T> List<T> filter(List<?> pojos, Class<T> projectionClass) {
        return resolvedDelegate(pojos, projectionClass).filter(pojos, projectionClass);
    }

    public <T> List<T> filter(DatasetBundle datasetBundle, Class<T> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return resolvedDelegate(datasetBundle.primaryRows(), projectionClass).filter(datasetBundle, projectionClass);
    }

    public <T> List<T> filter(List<?> pojos, JoinBindings joinBindings, Class<T> projectionClass) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return resolvedDelegate(pojos, projectionClass).filter(pojos, joinBindings, projectionClass);
    }

    public <T> Iterator<T> iterator(List<?> pojos, Class<T> projectionClass) {
        return resolvedDelegate(pojos, projectionClass).iterator(pojos, projectionClass);
    }

    public <T> Iterator<T> iterator(DatasetBundle datasetBundle, Class<T> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return resolvedDelegate(datasetBundle.primaryRows(), projectionClass).iterator(datasetBundle, projectionClass);
    }

    public <T> Iterator<T> iterator(List<?> pojos, JoinBindings joinBindings, Class<T> projectionClass) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return resolvedDelegate(pojos, projectionClass).iterator(pojos, joinBindings, projectionClass);
    }

    public <T> Stream<T> stream(List<?> pojos, Class<T> projectionClass) {
        return resolvedDelegate(pojos, projectionClass).stream(pojos, projectionClass);
    }

    public <T> Stream<T> stream(DatasetBundle datasetBundle, Class<T> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return resolvedDelegate(datasetBundle.primaryRows(), projectionClass).stream(datasetBundle, projectionClass);
    }

    public <T> Stream<T> stream(List<?> pojos, JoinBindings joinBindings, Class<T> projectionClass) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return resolvedDelegate(pojos, projectionClass).stream(pojos, joinBindings, projectionClass);
    }

    public <T> ChartData chart(List<?> pojos, Class<T> projectionClass, ChartSpec spec) {
        return resolvedDelegate(pojos, projectionClass).chart(pojos, projectionClass, spec);
    }

    public <T> ChartData chart(List<?> pojos, Class<T> projectionClass) {
        ResolvedExecution resolvedExecution = resolvedExecution(pojos, projectionClass);
        return resolvedExecution.delegate().chart(
                pojos,
                projectionClass,
                NaturalChartSupport.inferChartSpec(resolvedExecution.resolved().ast(), state.chartType())
        );
    }

    public <T> ChartData chart(DatasetBundle datasetBundle, Class<T> projectionClass, ChartSpec spec) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return resolvedDelegate(datasetBundle.primaryRows(), projectionClass).chart(datasetBundle, projectionClass, spec);
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
        return resolvedDelegate(pojos, projectionClass).chart(pojos, joinBindings, projectionClass, spec);
    }

    public <T> ChartData chart(List<?> pojos,
                               JoinBindings joinBindings,
                               Class<T> projectionClass) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        ResolvedExecution resolvedExecution = resolvedExecution(pojos, projectionClass);
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
        return createDelegate(state.ast()).schema(projectionClass);
    }

    public Map<String, Object> explain() {
        return addExplainMetadata(createDelegate(state.ast()).explain(), null);
    }

    public <T> Map<String, Object> explain(List<?> pojos, Class<T> projectionClass) {
        NaturalQueryResolutionSupport.ResolvedNaturalQuery resolved = resolve(pojos, projectionClass);
        return addExplainMetadata(
                createDelegate(resolved.ast()).explain(pojos, projectionClass),
                resolved
        );
    }

    public <T> Map<String, Object> explain(DatasetBundle datasetBundle, Class<T> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        NaturalQueryResolutionSupport.ResolvedNaturalQuery resolved = resolve(datasetBundle.primaryRows(), projectionClass);
        return addExplainMetadata(
                createDelegate(resolved.ast()).explain(datasetBundle, projectionClass),
                resolved
        );
    }

    public <T> Map<String, Object> explain(List<?> pojos,
                                           JoinBindings joinBindings,
                                           Class<T> projectionClass) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        NaturalQueryResolutionSupport.ResolvedNaturalQuery resolved = resolve(pojos, projectionClass);
        return addExplainMetadata(
                createDelegate(resolved.ast()).explain(pojos, joinBindings, projectionClass),
                resolved
        );
    }

    private NaturalQuery withState(QueryState updatedState) {
        return updatedState == state ? this : new NaturalQuery(source, equivalentSqlLike, updatedState);
    }

    private SqlLikeQuery resolvedDelegate(List<?> pojos, Class<?> projectionClass) {
        return resolvedExecution(pojos, projectionClass).delegate();
    }

    private ResolvedExecution resolvedExecution(List<?> pojos, Class<?> projectionClass) {
        NaturalQueryResolutionSupport.ResolvedNaturalQuery resolved = resolve(pojos, projectionClass);
        return new ResolvedExecution(resolved, createDelegate(resolved.ast()));
    }

    private NaturalQueryResolutionSupport.ResolvedNaturalQuery resolve(List<?> pojos, Class<?> projectionClass) {
        Class<?> sourceClass = SqlLikeExecutionSupport.inferSourceClass(pojos, projectionClass);
        Set<String> allowedFields = new LinkedHashSet<>(ReflectionUtil.collectQueryableFieldNames(sourceClass));
        allowedFields.addAll(state.computedFieldRegistry().names());
        return NaturalQueryResolutionSupport.resolve(
                new NaturalQueryParseResult(state.ast(), state.sourceFieldPhrases(), state.chartType()),
                allowedFields,
                state.vocabulary()
        );
    }

    private SqlLikeQuery createDelegate(QueryAst ast) {
        return SqlLikeQuery.fromAst(source, equivalentSqlLike, "natural", ast)
                .strictParameterTypes(state.strictParameterTypes())
                .lintMode(state.lintMode())
                .computedFields(state.computedFieldRegistry())
                .executionPlanCache(state.executionPlanCache())
                .telemetry(state.telemetryListener());
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
                              NaturalVocabulary vocabulary,
                              ChartType chartType) {

        private QueryState {
            Objects.requireNonNull(ast, "ast must not be null");
            sourceFieldPhrases = Collections.unmodifiableMap(new LinkedHashMap<>(
                    sourceFieldPhrases == null ? Map.of() : sourceFieldPhrases
            ));
            computedFieldRegistry = computedFieldRegistry == null ? ComputedFieldRegistry.empty() : computedFieldRegistry;
            executionPlanCache = Objects.requireNonNull(executionPlanCache, "executionPlanCache must not be null");
            vocabulary = vocabulary == null ? NaturalVocabulary.empty() : vocabulary;
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
                    NaturalVocabulary.empty(),
                    parseResult.chartType()
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
                    vocabulary,
                    chartType
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
                    vocabulary,
                    chartType
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
                    vocabulary,
                    chartType
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
                    vocabulary,
                    chartType
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
                    vocabulary,
                    chartType
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
                    vocabulary,
                    chartType
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
                    updatedVocabulary,
                    chartType
            );
        }
    }

    private record ResolvedExecution(NaturalQueryResolutionSupport.ResolvedNaturalQuery resolved,
                                     SqlLikeQuery delegate) {
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
