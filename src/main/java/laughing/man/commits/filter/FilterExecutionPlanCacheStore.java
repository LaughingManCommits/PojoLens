package laughing.man.commits.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Instance-scoped execution-plan cache for repeated stats workloads.
 */
public final class FilterExecutionPlanCacheStore {

    private static final int DEFAULT_MAX_ENTRIES = 512;
    private static final long DEFAULT_MAX_WEIGHT = 0L;
    private static final long DEFAULT_EXPIRE_AFTER_WRITE_MILLIS = 0L;

    private final Object mutationLock = new Object();

    private volatile boolean enabled = true;
    private volatile boolean statsEnabled = true;
    private volatile int maxEntries = DEFAULT_MAX_ENTRIES;
    private volatile long maxWeight = DEFAULT_MAX_WEIGHT;
    private volatile long expireAfterWriteMillis = DEFAULT_EXPIRE_AFTER_WRITE_MILLIS;
    private volatile Cache<String, FilterExecutionPlan> cache = newCache();

    public FilterExecutionPlan getOrBuild(String key, Supplier<FilterExecutionPlan> factory) {
        if (!enabled || key == null) {
            return factory.get();
        }
        return cache.get(key, ignored -> factory.get());
    }

    public void clear() {
        synchronized (mutationLock) {
            cache.invalidateAll();
            cache.cleanUp();
        }
    }

    public int size() {
        cache.cleanUp();
        long size = cache.estimatedSize();
        return (int) Math.min(Integer.MAX_VALUE, size);
    }

    public long hits() {
        if (!statsEnabled) {
            return 0L;
        }
        cache.cleanUp();
        return cache.stats().hitCount();
    }

    public long misses() {
        if (!statsEnabled) {
            return 0L;
        }
        cache.cleanUp();
        return cache.stats().missCount();
    }

    public long evictions() {
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
        snapshot.put("size", size());
        snapshot.put("hits", hits());
        snapshot.put("misses", misses());
        snapshot.put("evictions", evictions());
        return Collections.unmodifiableMap(snapshot);
    }

    public void resetStats() {
        rebuildCache();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public boolean isStatsEnabled() {
        return statsEnabled;
    }

    public void setStatsEnabled(boolean value) {
        if (statsEnabled == value) {
            return;
        }
        statsEnabled = value;
        rebuildCache();
    }

    public int maxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("maxEntries must be > 0");
        }
        maxEntries = value;
        rebuildCache();
    }

    public long maxWeight() {
        return maxWeight;
    }

    public void setMaxWeight(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("maxWeight must be >= 0");
        }
        maxWeight = value;
        rebuildCache();
    }

    public long expireAfterWriteMillis() {
        return expireAfterWriteMillis;
    }

    public void setExpireAfterWriteMillis(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("expireAfterWriteMillis must be >= 0");
        }
        expireAfterWriteMillis = value;
        rebuildCache();
    }

    private Cache<String, FilterExecutionPlan> newCache() {
        Caffeine<String, FilterExecutionPlan> builder = typedBuilder();

        if (statsEnabled) {
            builder.recordStats();
        }

        if (maxWeight > 0L) {
            builder = builder.maximumWeight(maxWeight)
                    .weigher((String key, FilterExecutionPlan value) -> Math.max(1, key.length()));
        } else {
            builder = builder.maximumSize(maxEntries);
        }

        if (expireAfterWriteMillis > 0L) {
            builder = builder.expireAfterWrite(Duration.ofMillis(expireAfterWriteMillis));
        }

        return builder.build();
    }

    private void rebuildCache() {
        synchronized (mutationLock) {
            Map<String, FilterExecutionPlan> entries = new LinkedHashMap<>(cache.asMap());
            cache = newCache();
            cache.putAll(entries);
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Caffeine<K, V> typedBuilder() {
        return (Caffeine<K, V>) Caffeine.newBuilder();
    }
}

