package laughing.man.commits;

import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.SqlLikeTemplate;
import laughing.man.commits.sqllike.internal.cache.SqlLikeQueryCache;

import java.util.Map;

/**
 * SQL-like parser/cache entry points.
 */
public final class PojoLensSql {

    private static final SqlLikeQueryCache SQL_LIKE_CACHE = new SqlLikeQueryCache();

    private PojoLensSql() {
    }

    public static SqlLikeQuery parse(String sqlLikeQuery) {
        return SQL_LIKE_CACHE.parse(sqlLikeQuery);
    }

    public static SqlLikeTemplate template(String sqlLikeQuery, String... expectedParams) {
        return SqlLikeTemplate.of(parse(sqlLikeQuery), expectedParams);
    }

    public static void setSqlLikeCacheEnabled(boolean enabled) {
        SQL_LIKE_CACHE.setEnabled(enabled);
    }

    public static boolean isSqlLikeCacheEnabled() {
        return SQL_LIKE_CACHE.isEnabled();
    }

    public static void setSqlLikeCacheMaxEntries(int maxEntries) {
        SQL_LIKE_CACHE.setMaxEntries(maxEntries);
    }

    public static int getSqlLikeCacheMaxEntries() {
        return SQL_LIKE_CACHE.getMaxEntries();
    }

    public static void setSqlLikeCacheMaxWeight(long maxWeight) {
        SQL_LIKE_CACHE.setMaxWeight(maxWeight);
    }

    public static long getSqlLikeCacheMaxWeight() {
        return SQL_LIKE_CACHE.getMaxWeight();
    }

    public static void setSqlLikeCacheExpireAfterWriteMillis(long expireAfterWriteMillis) {
        SQL_LIKE_CACHE.setExpireAfterWriteMillis(expireAfterWriteMillis);
    }

    public static long getSqlLikeCacheExpireAfterWriteMillis() {
        return SQL_LIKE_CACHE.getExpireAfterWriteMillis();
    }

    public static void setSqlLikeCacheStatsEnabled(boolean statsEnabled) {
        SQL_LIKE_CACHE.setStatsEnabled(statsEnabled);
    }

    public static boolean isSqlLikeCacheStatsEnabled() {
        return SQL_LIKE_CACHE.isStatsEnabled();
    }

    public static void clearSqlLikeCache() {
        SQL_LIKE_CACHE.clear();
    }

    public static void resetSqlLikeCacheStats() {
        SQL_LIKE_CACHE.resetStats();
    }

    public static long getSqlLikeCacheHits() {
        return SQL_LIKE_CACHE.getHits();
    }

    public static long getSqlLikeCacheMisses() {
        return SQL_LIKE_CACHE.getMisses();
    }

    public static int getSqlLikeCacheSize() {
        return SQL_LIKE_CACHE.getSize();
    }

    public static long getSqlLikeCacheEvictions() {
        return SQL_LIKE_CACHE.getEvictions();
    }

    public static Map<String, Object> getSqlLikeCacheSnapshot() {
        return SQL_LIKE_CACHE.snapshot();
    }
}

