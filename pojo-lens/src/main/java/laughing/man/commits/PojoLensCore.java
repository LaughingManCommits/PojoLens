package laughing.man.commits;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.filter.FilterExecutionPlanCache;
import laughing.man.commits.filter.FilterExecutionPlanCacheStore;

import java.util.List;
import java.util.Map;

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

    public static void setStatsPlanCacheEnabled(boolean enabled) {
        FilterExecutionPlanCache.setEnabled(enabled);
    }

    public static boolean isStatsPlanCacheEnabled() {
        return FilterExecutionPlanCache.isEnabled();
    }

    public static void setStatsPlanCacheMaxEntries(int maxEntries) {
        FilterExecutionPlanCache.setMaxEntries(maxEntries);
    }

    public static int getStatsPlanCacheMaxEntries() {
        return FilterExecutionPlanCache.maxEntries();
    }

    public static void setStatsPlanCacheMaxWeight(long maxWeight) {
        FilterExecutionPlanCache.setMaxWeight(maxWeight);
    }

    public static long getStatsPlanCacheMaxWeight() {
        return FilterExecutionPlanCache.maxWeight();
    }

    public static void setStatsPlanCacheExpireAfterWriteMillis(long expireAfterWriteMillis) {
        FilterExecutionPlanCache.setExpireAfterWriteMillis(expireAfterWriteMillis);
    }

    public static long getStatsPlanCacheExpireAfterWriteMillis() {
        return FilterExecutionPlanCache.expireAfterWriteMillis();
    }

    public static void setStatsPlanCacheStatsEnabled(boolean statsEnabled) {
        FilterExecutionPlanCache.setStatsEnabled(statsEnabled);
    }

    public static boolean isStatsPlanCacheStatsEnabled() {
        return FilterExecutionPlanCache.isStatsEnabled();
    }

    public static void clearStatsPlanCache() {
        FilterExecutionPlanCache.clear();
    }

    public static void resetStatsPlanCacheStats() {
        FilterExecutionPlanCache.resetStats();
    }

    public static long getStatsPlanCacheHits() {
        return FilterExecutionPlanCache.hits();
    }

    public static long getStatsPlanCacheMisses() {
        return FilterExecutionPlanCache.misses();
    }

    public static int getStatsPlanCacheSize() {
        return FilterExecutionPlanCache.size();
    }

    public static long getStatsPlanCacheEvictions() {
        return FilterExecutionPlanCache.evictions();
    }

    public static Map<String, Object> getStatsPlanCacheSnapshot() {
        return FilterExecutionPlanCache.snapshot();
    }
}

