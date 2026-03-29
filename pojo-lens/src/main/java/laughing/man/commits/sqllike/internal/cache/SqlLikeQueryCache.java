package laughing.man.commits.sqllike.internal.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import laughing.man.commits.filter.FilterExecutionPlanCacheStore;
import laughing.man.commits.filter.internal.DefaultFilterExecutionPlanCacheSupport;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Internal bounded cache for parsed SQL-like queries.
 */
public final class SqlLikeQueryCache {

    private static final int DEFAULT_MAX_ENTRIES = 256;
    private static final long DEFAULT_MAX_WEIGHT = 0L;
    private static final long DEFAULT_EXPIRE_AFTER_WRITE_MILLIS = 0L;

    private final Object mutationLock = new Object();
    private final AtomicLong bypassMisses = new AtomicLong();

    private volatile FilterExecutionPlanCacheStore executionPlanCache = DefaultFilterExecutionPlanCacheSupport.defaultStore();
    private volatile boolean enabled = true;
    private volatile boolean statsEnabled = true;
    private volatile int maxEntries = DEFAULT_MAX_ENTRIES;
    private volatile long maxWeight = DEFAULT_MAX_WEIGHT;
    private volatile long expireAfterWriteMillis = DEFAULT_EXPIRE_AFTER_WRITE_MILLIS;
    private volatile Cache<String, SqlLikeQuery> cache = newCache();

    public SqlLikeQueryCache() {
    }

    public SqlLikeQueryCache(FilterExecutionPlanCacheStore executionPlanCache) {
        setExecutionPlanCacheStore(executionPlanCache);
    }

    public SqlLikeQuery parse(String source) {
        String normalized = normalize(source);
        if (!enabled) {
            if (statsEnabled) {
                bypassMisses.incrementAndGet();
            }
            return newQuery(normalized);
        }
        return cache.get(normalized, this::newQuery);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setMaxEntries(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1");
        }
        this.maxEntries = maxEntries;
        rebuildCache();
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxWeight(long maxWeight) {
        if (maxWeight < 0L) {
            throw new IllegalArgumentException("maxWeight must be >= 0");
        }
        this.maxWeight = maxWeight;
        rebuildCache();
    }

    public long getMaxWeight() {
        return maxWeight;
    }

    public void setExpireAfterWriteMillis(long expireAfterWriteMillis) {
        if (expireAfterWriteMillis < 0L) {
            throw new IllegalArgumentException("expireAfterWriteMillis must be >= 0");
        }
        this.expireAfterWriteMillis = expireAfterWriteMillis;
        rebuildCache();
    }

    public long getExpireAfterWriteMillis() {
        return expireAfterWriteMillis;
    }

    public void setStatsEnabled(boolean statsEnabled) {
        if (this.statsEnabled == statsEnabled) {
            return;
        }
        this.statsEnabled = statsEnabled;
        rebuildCache();
    }

    public boolean isStatsEnabled() {
        return statsEnabled;
    }

    public void setExecutionPlanCacheStore(FilterExecutionPlanCacheStore executionPlanCache) {
        if (executionPlanCache == null) {
            throw new IllegalArgumentException("executionPlanCache must not be null");
        }
        synchronized (mutationLock) {
            this.executionPlanCache = executionPlanCache;
            cache.invalidateAll();
            cache.cleanUp();
        }
    }

    public void clear() {
        synchronized (mutationLock) {
            cache.invalidateAll();
            cache.cleanUp();
        }
    }

    public long getHits() {
        if (!statsEnabled) {
            return 0L;
        }
        cache.cleanUp();
        return cache.stats().hitCount();
    }

    public long getMisses() {
        if (!statsEnabled) {
            return 0L;
        }
        cache.cleanUp();
        return cache.stats().missCount() + bypassMisses.get();
    }

    public int getSize() {
        cache.cleanUp();
        long size = cache.estimatedSize();
        return (int) Math.min(Integer.MAX_VALUE, size);
    }

    public long getEvictions() {
        if (!statsEnabled) {
            return 0L;
        }
        cache.cleanUp();
        return cache.stats().evictionCount();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("enabled", enabled);
        snapshot.put("statsEnabled", statsEnabled);
        snapshot.put("maxEntries", maxEntries);
        snapshot.put("maxWeight", maxWeight);
        snapshot.put("expireAfterWriteMillis", expireAfterWriteMillis);
        snapshot.put("size", getSize());
        snapshot.put("hits", getHits());
        snapshot.put("misses", getMisses());
        snapshot.put("evictions", getEvictions());
        return Collections.unmodifiableMap(snapshot);
    }

    public void resetStats() {
        bypassMisses.set(0L);
        rebuildCache();
    }

    private Cache<String, SqlLikeQuery> newCache() {
        Caffeine<String, SqlLikeQuery> builder = typedBuilder();

        if (statsEnabled) {
            builder.recordStats();
        }

        if (maxWeight > 0L) {
            builder = builder.maximumWeight(maxWeight)
                    .weigher((String key, SqlLikeQuery value) -> Math.max(1, key.length()));
        } else {
            builder = builder.maximumSize(maxEntries);
        }

        if (expireAfterWriteMillis > 0L) {
            builder = builder.expireAfterWrite(Duration.ofMillis(expireAfterWriteMillis));
        }

        return builder.build();
    }

    private SqlLikeQuery newQuery(String normalized) {
        return SqlLikeQuery.of(normalized).executionPlanCache(executionPlanCache);
    }

    private void rebuildCache() {
        synchronized (mutationLock) {
            Map<String, SqlLikeQuery> existing = new LinkedHashMap<>(cache.asMap());
            cache = newCache();
            cache.putAll(existing);
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Caffeine<K, V> typedBuilder() {
        return (Caffeine<K, V>) Caffeine.newBuilder();
    }

    private static String normalize(String source) {
        if (source == null) {
            throw SqlLikeErrors.argument(SqlLikeErrorCodes.API_QUERY_NULL,
                    "SQL-like query must not be null");
        }
        String normalized = source.trim();
        if (normalized.isEmpty()) {
            throw SqlLikeErrors.argument(SqlLikeErrorCodes.API_QUERY_BLANK,
                    "SQL-like query must not be blank");
        }
        return normalized;
    }
}

