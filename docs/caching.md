# Caching Policies

`PojoLens` currently exposes two runtime caches:

- SQL-like parse cache
- stats-plan cache

Both caches use Caffeine-backed internals and are configurable through `PojoLens`.
For DI or multi-tenant isolation, both caches can also be scoped per `PojoLensRuntime`.

## Defaults

- SQL-like cache:
  - `enabled=true`
  - `statsEnabled=true`
  - `maxEntries=256`
  - `maxWeight=0` (disabled)
  - `expireAfterWriteMillis=0` (disabled)
- stats-plan cache:
  - `enabled=true`
  - `statsEnabled=true`
  - `maxEntries=512`
  - `maxWeight=0` (disabled)
  - `expireAfterWriteMillis=0` (disabled)

`maxWeight=0` means size-based eviction by entry count (`maxEntries`).  
Set `maxWeight > 0` to switch to weighted eviction.

## Public Policy APIs

SQL-like cache:

- `setSqlLikeCacheEnabled(boolean)`
- `setSqlLikeCacheStatsEnabled(boolean)`
- `setSqlLikeCacheMaxEntries(int)`
- `setSqlLikeCacheMaxWeight(long)`
- `setSqlLikeCacheExpireAfterWriteMillis(long)`
- `clearSqlLikeCache()`
- `resetSqlLikeCacheStats()`
- `getSqlLikeCacheSnapshot()`

stats-plan cache:

- `setStatsPlanCacheEnabled(boolean)`
- `setStatsPlanCacheStatsEnabled(boolean)`
- `setStatsPlanCacheMaxEntries(int)`
- `setStatsPlanCacheMaxWeight(long)`
- `setStatsPlanCacheExpireAfterWriteMillis(long)`
- `clearStatsPlanCache()`
- `resetStatsPlanCacheStats()`
- `getStatsPlanCacheSnapshot()`

## Instance-Scoped Runtime (DI / Multi-Tenant)

Static `PojoLens.*` cache APIs operate on the process-default singleton caches.

For isolated cache policy per tenant/request scope, use `PojoLens.newRuntime()`:

```java
PojoLensRuntime runtime = PojoLens.newRuntime();
runtime.sqlLikeCache().setMaxEntries(1024);
runtime.statsPlanCache().setExpireAfterWriteMillis(30_000L);

List<Employee> rows = runtime.newQueryBuilder(source)
    .addGroup("department")
    .addCount("total")
    .initFilter()
    .filter(Employee.class);
```

Use static policies when one global cache policy is sufficient.
Use runtime-scoped policies when separate cache isolation is required.

## Tradeoffs

- Higher `maxEntries`/`maxWeight`:
  - reduces rebuild/parse churn
  - increases memory footprint
- Lower `maxEntries`/`maxWeight`:
  - lowers memory usage
  - can increase CPU due to frequent misses
- Enabling expiry:
  - bounds stale/idle entries
  - can reduce hit ratio when TTL is too aggressive
- Disabling stats:
  - reduces counter overhead
  - removes hit/miss/eviction observability in snapshots

## Tuning Notes

- Start with defaults and benchmark first.
- For hot repeated SQL-like queries, increase SQL cache size before enabling TTL.
- For high-cardinality grouped metrics, increase stats-plan cache limits.
- Use cache snapshots to monitor hit/miss trends after every change.

