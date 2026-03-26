package laughing.man.commits;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.filter.FilterExecutionPlanCacheStore;

import java.util.List;

/**
 * Core fluent-query entry points without SQL-like parser concerns.
 */
public final class PojoLensCore {

    public static final String SDF = EngineDefaults.SDF;
    public static final String EMPTY_GROUPING = EngineDefaults.EMPTY_GROUPING;

    private PojoLensCore() {
    }

    public static QueryBuilder newQueryBuilder(List<?> pojos) {
        return new FilterQueryBuilder(pojos);
    }

    public static QueryBuilder newQueryBuilder(List<?> pojos, FilterExecutionPlanCacheStore cacheStore) {
        return new FilterQueryBuilder(pojos, cacheStore);
    }
}

