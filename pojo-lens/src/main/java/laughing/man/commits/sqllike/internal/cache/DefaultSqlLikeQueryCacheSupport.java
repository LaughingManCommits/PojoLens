package laughing.man.commits.sqllike.internal.cache;

import java.util.Map;
import laughing.man.commits.sqllike.SqlLikeQuery;

/**
 * Internal owner for the default singleton SQL-like cache used by direct
 * non-runtime entry points.
 */
public final class DefaultSqlLikeQueryCacheSupport {

    private static final SqlLikeQueryCache DEFAULT_CACHE = new SqlLikeQueryCache();

    private DefaultSqlLikeQueryCacheSupport() {
    }

    public static SqlLikeQuery parse(String sqlLikeQuery) {
        return DEFAULT_CACHE.parse(sqlLikeQuery);
    }

    public static Map<String, Object> snapshot() {
        return DEFAULT_CACHE.snapshot();
    }
}
