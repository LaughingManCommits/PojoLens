package laughing.man.commits.filter;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Static compatibility facade around the default execution-plan cache store.
 */
public final class FilterExecutionPlanCache {

    private static volatile FilterExecutionPlanCacheStore defaultStore = new FilterExecutionPlanCacheStore();

    private FilterExecutionPlanCache() {
    }

    public static FilterExecutionPlan getOrBuild(String key, Supplier<FilterExecutionPlan> factory) {
        return defaultStore.getOrBuild(key, factory);
    }

    public static void clear() {
        defaultStore.clear();
    }

    public static int size() {
        return defaultStore.size();
    }

    public static long hits() {
        return defaultStore.hits();
    }

    public static long misses() {
        return defaultStore.misses();
    }

    public static long evictions() {
        return defaultStore.evictions();
    }

    public static Map<String, Object> snapshot() {
        return defaultStore.snapshot();
    }

    public static void resetStats() {
        defaultStore.resetStats();
    }

    public static boolean isEnabled() {
        return defaultStore.isEnabled();
    }

    public static void setEnabled(boolean value) {
        defaultStore.setEnabled(value);
    }

    public static boolean isStatsEnabled() {
        return defaultStore.isStatsEnabled();
    }

    public static void setStatsEnabled(boolean value) {
        defaultStore.setStatsEnabled(value);
    }

    public static int maxEntries() {
        return defaultStore.maxEntries();
    }

    public static void setMaxEntries(int value) {
        defaultStore.setMaxEntries(value);
    }

    public static long maxWeight() {
        return defaultStore.maxWeight();
    }

    public static void setMaxWeight(long value) {
        defaultStore.setMaxWeight(value);
    }

    public static long expireAfterWriteMillis() {
        return defaultStore.expireAfterWriteMillis();
    }

    public static void setExpireAfterWriteMillis(long value) {
        defaultStore.setExpireAfterWriteMillis(value);
    }

    public static FilterExecutionPlanCacheStore defaultStore() {
        return defaultStore;
    }

    public static void setDefaultStore(FilterExecutionPlanCacheStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        defaultStore = store;
    }
}

