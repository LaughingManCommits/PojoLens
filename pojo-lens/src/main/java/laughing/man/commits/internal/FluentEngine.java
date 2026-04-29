package laughing.man.commits.internal;

import laughing.man.commits.EngineDefaults;
import laughing.man.commits.filter.FilterExecutionPlanCacheStore;
import laughing.man.commits.internal.builder.FilterQueryBuilder;
import laughing.man.commits.internal.builder.FluentQueryDefinition;
import laughing.man.commits.internal.builder.QueryBuilder;

import java.util.List;
import java.util.function.Consumer;

/**
 * Internal factory for the fluent execution-planning DSL.
 */
public final class FluentEngine {

    public static final String SDF = EngineDefaults.SDF;
    public static final String EMPTY_GROUPING = EngineDefaults.EMPTY_GROUPING;

    private FluentEngine() {
    }

    public static QueryBuilder newQueryBuilder(List<?> pojos) {
        return new FilterQueryBuilder(pojos);
    }

    public static QueryBuilder newQueryBuilder(List<?> pojos, FilterExecutionPlanCacheStore cacheStore) {
        return new FilterQueryBuilder(pojos, cacheStore);
    }

    public static <T> FluentQueryDefinition<T> prepare(Class<T> projectionClass, Consumer<QueryBuilder> configurer) {
        return FluentQueryDefinition.of(projectionClass, configurer);
    }
}
