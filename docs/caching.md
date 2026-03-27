# Caching Policies

PojoLens currently has two internal caches behind its direct query paths:

This is an advanced policy-tuning surface.
Start with the default query path first, then come here when cache behavior
needs to be tuned or isolated.

- SQL-like parse cache
- stats-plan cache

Both caches use Caffeine-backed internals.
For public policy tuning, use `PojoLensRuntime`; it is the public cache-policy
configuration surface.

Current model:
- direct query entry points use internal default singleton caches
- public tuning, clearing, and observability live on `PojoLensRuntime`
- `PojoLens`, `PojoLensCore`, and `PojoLensSql` do not expose public
  static/global cache APIs

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

## Runtime-First Policy APIs

Preferred public tuning surface:

- `runtime.sqlLikeCache()`
- `runtime.statsPlanCache()`

Both returned cache objects expose the full policy and observability model:

- enable/disable
- stats enable/disable
- max entries
- max weight
- expire-after-write
- clear/reset stats
- hits/misses/size/evictions
- structured snapshot

Example:

```java
PojoLensRuntime runtime = PojoLens.newRuntime();
runtime.sqlLikeCache().setMaxEntries(1024);
runtime.statsPlanCache().setExpireAfterWriteMillis(30_000L);
```

## Removed Static APIs

Public static/global cache policy methods are removed from:

- `PojoLens`
- `PojoLensSql`
- `PojoLensCore`

Use `PojoLens.newRuntime()` and the returned cache handles instead.

## Instance-Scoped Runtime (DI / Multi-Tenant)

Runtime-scoped cache handles are the preferred public model.
Direct non-runtime entry points still use process-default singleton caches, but
those caches no longer have a public tuning API.

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

Use runtime-scoped policies whenever cache behavior should be explicit in app
code.

## Replacement Map

When moving off older static/global cache APIs:

- old SQL-like cache controls and snapshots -> `runtime.sqlLikeCache().*`
- old stats-plan cache controls and snapshots -> `runtime.statsPlanCache().*`

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

