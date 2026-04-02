package laughing.man.commits.natural;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.filter.FilterExecutionPlanCacheStore;
import laughing.man.commits.natural.parser.NaturalQueryParser;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.SqlParams;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.telemetry.QueryTelemetryListener;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.stream.Stream;

/**
 * Controlled plain-English query contract lowered into the shared engine.
 */
public final class NaturalQuery {

    private final SqlLikeQuery delegate;
    private final String equivalentSqlLike;

    private NaturalQuery(SqlLikeQuery delegate, String equivalentSqlLike) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.equivalentSqlLike = Objects.requireNonNull(equivalentSqlLike, "equivalentSqlLike must not be null");
    }

    public static NaturalQuery of(String source) {
        var ast = NaturalQueryParser.parse(source);
        String equivalentSqlLike = NaturalQueryRenderer.toSqlLike(ast);
        return new NaturalQuery(
                SqlLikeQuery.fromAst(source, equivalentSqlLike, "natural", ast),
                equivalentSqlLike
        );
    }

    public String source() {
        return delegate.source();
    }

    public String equivalentSqlLike() {
        return equivalentSqlLike;
    }

    public NaturalQuery params(Map<String, ?> parameters) {
        return new NaturalQuery(delegate.params(parameters), equivalentSqlLike);
    }

    public NaturalQuery params(SqlParams parameters) {
        return new NaturalQuery(delegate.params(parameters), equivalentSqlLike);
    }

    public NaturalQuery strictParameterTypes() {
        return strictParameterTypes(true);
    }

    public NaturalQuery strictParameterTypes(boolean enabled) {
        SqlLikeQuery updated = delegate.strictParameterTypes(enabled);
        return updated == delegate ? this : new NaturalQuery(updated, equivalentSqlLike);
    }

    public boolean isStrictParameterTypesEnabled() {
        return delegate.isStrictParameterTypesEnabled();
    }

    public NaturalQuery lintMode() {
        return lintMode(true);
    }

    public NaturalQuery lintMode(boolean enabled) {
        SqlLikeQuery updated = delegate.lintMode(enabled);
        return updated == delegate ? this : new NaturalQuery(updated, equivalentSqlLike);
    }

    public boolean isLintModeEnabled() {
        return delegate.isLintModeEnabled();
    }

    public NaturalQuery telemetry(QueryTelemetryListener listener) {
        SqlLikeQuery updated = delegate.telemetry(listener);
        return updated == delegate ? this : new NaturalQuery(updated, equivalentSqlLike);
    }

    public QueryTelemetryListener telemetryListener() {
        return delegate.telemetryListener();
    }

    public NaturalQuery computedFields(ComputedFieldRegistry registry) {
        SqlLikeQuery updated = delegate.computedFields(registry);
        return updated == delegate ? this : new NaturalQuery(updated, equivalentSqlLike);
    }

    public ComputedFieldRegistry computedFieldRegistry() {
        return delegate.computedFieldRegistry();
    }

    public NaturalQuery executionPlanCache(FilterExecutionPlanCacheStore executionPlanCache) {
        SqlLikeQuery updated = delegate.executionPlanCache(executionPlanCache);
        return updated == delegate ? this : new NaturalQuery(updated, equivalentSqlLike);
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
        return new DefaultNaturalBoundQuery<>(delegate.bindTyped(pojos, projectionClass, joinBindings));
    }

    public <T> List<T> filter(List<?> pojos, Class<T> projectionClass) {
        return delegate.filter(pojos, projectionClass);
    }

    public <T> List<T> filter(DatasetBundle datasetBundle, Class<T> projectionClass) {
        return delegate.filter(datasetBundle, projectionClass);
    }

    public <T> List<T> filter(List<?> pojos, JoinBindings joinBindings, Class<T> projectionClass) {
        return delegate.filter(pojos, joinBindings, projectionClass);
    }

    public <T> Iterator<T> iterator(List<?> pojos, Class<T> projectionClass) {
        return delegate.iterator(pojos, projectionClass);
    }

    public <T> Iterator<T> iterator(DatasetBundle datasetBundle, Class<T> projectionClass) {
        return delegate.iterator(datasetBundle, projectionClass);
    }

    public <T> Iterator<T> iterator(List<?> pojos, JoinBindings joinBindings, Class<T> projectionClass) {
        return delegate.iterator(pojos, joinBindings, projectionClass);
    }

    public <T> Stream<T> stream(List<?> pojos, Class<T> projectionClass) {
        return delegate.stream(pojos, projectionClass);
    }

    public <T> Stream<T> stream(DatasetBundle datasetBundle, Class<T> projectionClass) {
        return delegate.stream(datasetBundle, projectionClass);
    }

    public <T> Stream<T> stream(List<?> pojos, JoinBindings joinBindings, Class<T> projectionClass) {
        return delegate.stream(pojos, joinBindings, projectionClass);
    }

    public <T> ChartData chart(List<?> pojos, Class<T> projectionClass, ChartSpec spec) {
        return delegate.chart(pojos, projectionClass, spec);
    }

    public <T> ChartData chart(DatasetBundle datasetBundle, Class<T> projectionClass, ChartSpec spec) {
        return delegate.chart(datasetBundle, projectionClass, spec);
    }

    public <T> ChartData chart(List<?> pojos,
                               JoinBindings joinBindings,
                               Class<T> projectionClass,
                               ChartSpec spec) {
        return delegate.chart(pojos, joinBindings, projectionClass, spec);
    }

    public Sort sort() {
        return delegate.sort();
    }

    public TabularSchema schema(Class<?> projectionClass) {
        return delegate.schema(projectionClass);
    }

    public Map<String, Object> explain() {
        return addEquivalentSqlLike(delegate.explain());
    }

    public <T> Map<String, Object> explain(List<?> pojos, Class<T> projectionClass) {
        return addEquivalentSqlLike(delegate.explain(pojos, projectionClass));
    }

    public <T> Map<String, Object> explain(DatasetBundle datasetBundle, Class<T> projectionClass) {
        return addEquivalentSqlLike(delegate.explain(datasetBundle, projectionClass));
    }

    public <T> Map<String, Object> explain(List<?> pojos,
                                           JoinBindings joinBindings,
                                           Class<T> projectionClass) {
        return addEquivalentSqlLike(delegate.explain(pojos, joinBindings, projectionClass));
    }

    private Map<String, Object> addEquivalentSqlLike(Map<String, Object> explain) {
        LinkedHashMap<String, Object> updated = new LinkedHashMap<>(explain);
        updated.put("equivalentSqlLike", equivalentSqlLike);
        return Collections.unmodifiableMap(updated);
    }

    private static final class DefaultNaturalBoundQuery<T> implements NaturalBoundQuery<T> {
        private final laughing.man.commits.sqllike.SqlLikeBoundQuery<T> delegate;

        private DefaultNaturalBoundQuery(laughing.man.commits.sqllike.SqlLikeBoundQuery<T> delegate) {
            this.delegate = delegate;
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
        public ChartData chart(ChartSpec spec) {
            return delegate.chart(spec);
        }
    }
}
