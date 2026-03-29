package laughing.man.commits.filter.internal;

import laughing.man.commits.filter.FilterExecutionPlanCacheStore;

import java.util.Map;

/**
 * Internal owner for the default singleton stats-plan cache used by direct
 * non-runtime entry points.
 */
public final class DefaultFilterExecutionPlanCacheSupport {

    private static final FilterExecutionPlanCacheStore DEFAULT_STORE = new FilterExecutionPlanCacheStore();

    private DefaultFilterExecutionPlanCacheSupport() {
    }

    public static FilterExecutionPlanCacheStore defaultStore() {
        return DEFAULT_STORE;
    }

    public static Map<String, Object> snapshot() {
        return DEFAULT_STORE.snapshot();
    }
}
