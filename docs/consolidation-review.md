# Consolidation And Deprecation Review

This document is the disposition register for the overlapping public surfaces
identified during the product-surface realignment work.

Current conclusion:
- the project is coherent enough that documentation realignment is sufficient
  for now
- if the library still has no meaningful external adoption, the active
  simplification candidate is the `PojoLens` facade overlap documented in the
  pre-adoption audit below
- no immediate `1.x` API removal is recommended
- the main remaining cleanup lever is continued de-emphasis of compatibility or
  advanced surface where newer defaults are already documented

## Guardrails

Any future consolidation must respect:

- [public-api-stability.md](public-api-stability.md)
- stable contract tests
- binary compatibility checks
- the migration guidance already captured in
  [entry-points.md](entry-points.md),
  [reusable-wrappers.md](reusable-wrappers.md), and
  [advanced-features.md](advanced-features.md)

## Disposition Register

| Surface | Tier | Disposition | Preferred path | Notes |
| --- | --- | --- | --- | --- |
| `PojoLensCore`, `PojoLensSql`, `PojoLensChart`, `PojoLensRuntime` | stable core/integration | `keep` | same | These are now the documented defaults for new code. |
| `PojoLens` facade query/chart entry methods | stable compatibility | `de-emphasize` | `PojoLensCore`, `PojoLensSql`, `PojoLensChart` | Keep for migration and mixed-style call sites; do not deprecate in `1.x`. |
| Static/global cache-control APIs on `PojoLens` / `PojoLensCore` / `PojoLensSql` | advanced | `candidate for deprecation later` | `PojoLensRuntime` instance-scoped policy | Published `1.x` call remains conservative here, but the pre-adoption audit below now tightens this to a runtime-first removal path before wider adoption. |
| `ReportDefinition<T>` | advanced workflow helper | `keep` | same | General reusable query contract; remains the top wrapper in the abstraction ladder. |
| `ChartQueryPreset<T>` | advanced workflow helper | `keep` + `de-emphasize as specialized` | `ReportDefinition<T>` when reuse becomes broader than chart-first flows | Still earns its place through preset factories and built-in chart spec. |
| `StatsViewPreset<T>` / `StatsTable<T>` | advanced workflow helper | `keep` + `de-emphasize as specialized` | `ReportDefinition<T>` for general row reuse; keep stats preset when totals/table payload matter | Still earns its place through totals and table payload semantics. |
| Regression, snapshot, metamodel, telemetry, caching, and benchmark tooling | mixed advanced/tooling | `keep` + `contain` | [advanced-features.md](advanced-features.md) | Valuable public follow-on surfaces, but not part of the first-read adoption story. |

## Migration Notes For Candidate Surfaces

### Static/Global Cache Policy

If this surface is reviewed for later deprecation, the migration path should be:

1. Prefer `PojoLens.newRuntime(...)` for new scoped policy decisions.
2. Move cache tuning and observability onto `PojoLensRuntime` where isolation or
   DI control is needed.
3. Keep global static cache APIs for cases where one process-wide policy is
   still intentionally desired until an equivalent migration story is fully
   documented.

This is a later review item, not an active deprecation.

## Pre-Adoption Facade Audit

Use this section only when no meaningful external adoption exists and small
breaking changes are still acceptable before broader release.

Scope notes:
- this audit covers public methods on `PojoLens`
- constants such as `SDF` and `EMPTY_GROUPING` are intentionally out of scope
- the goal is to keep helper-only facade methods while shrinking pure overlap

Current repo signal:
- user-facing docs for new code already prefer the explicit entry points in
  [entry-points.md](entry-points.md)
- migration docs and tests still contain many `PojoLens.*` references, which
  means the cleanup cost is mostly repo-internal right now

| `PojoLens` surface | Recommended disposition | Preferred path | Why |
| --- | --- | --- | --- |
| `newQueryBuilder(List<?>)` | `remove before wider adoption` | `PojoLensCore.newQueryBuilder(...)` | Pure fluent-entry alias; competes directly with the documented core entry point. |
| `parse(String)` | `remove before wider adoption` | `PojoLensSql.parse(...)` | Pure SQL-like parse alias; keeps new code on the facade for no added value. |
| `template(String, String...)` | `remove before wider adoption` | `PojoLensSql.template(...)` | Same overlap as `parse(...)`, but for reusable SQL-like templates. |
| `toChartData(List<T>, ChartSpec)` | `remove before wider adoption` | `PojoLensChart.toChartData(...)` | Pure chart-mapping alias; competes with the explicit chart helper. |
| `newRuntime()` and `newRuntime(PojoLensRuntimePreset)` | `keep helper-only` | same | Still useful as the lightweight runtime-construction helper for app code; does not create a second runtime model. |
| `newKeysetCursorBuilder()` and `parseKeysetCursor(String)` | `keep helper-only` | same | Small facade helpers around keyset cursor creation and token decoding; they do not compete with the main query-entry story. |
| `bundle(...)` overloads | `keep helper-only` | same | Cross-cutting workflow helper used by reports and joined SQL-like execution; keeping it on the facade is a reasonable namespace choice. |
| `compareSnapshots(...)` | `keep helper-only` | same | Snapshot comparison is an adjacent helper workflow, not a competing query entry path. |
| `report(...)` overloads | `keep helper-only` | same | Report creation sits above the entry-point layer and is still coherent as a facade helper. |
| SQL-like cache delegates: `setSqlLikeCacheEnabled`, `isSqlLikeCacheEnabled`, `setSqlLikeCacheMaxEntries`, `getSqlLikeCacheMaxEntries`, `setSqlLikeCacheMaxWeight`, `getSqlLikeCacheMaxWeight`, `setSqlLikeCacheExpireAfterWriteMillis`, `getSqlLikeCacheExpireAfterWriteMillis`, `setSqlLikeCacheStatsEnabled`, `isSqlLikeCacheStatsEnabled`, `clearSqlLikeCache`, `resetSqlLikeCacheStats`, `getSqlLikeCacheHits`, `getSqlLikeCacheMisses`, `getSqlLikeCacheSize`, `getSqlLikeCacheEvictions`, `getSqlLikeCacheSnapshot` | `remove before wider adoption` | `PojoLensRuntime.sqlLikeCache()` for scoped policy; `PojoLensSql.*` only if a global static path is intentionally retained | These methods are an extra compatibility layer on top of an already-advanced static surface and provide the least unique value on the facade. |
| Stats-plan cache delegates: `setStatsPlanCacheEnabled`, `isStatsPlanCacheEnabled`, `setStatsPlanCacheMaxEntries`, `getStatsPlanCacheMaxEntries`, `setStatsPlanCacheMaxWeight`, `getStatsPlanCacheMaxWeight`, `setStatsPlanCacheExpireAfterWriteMillis`, `getStatsPlanCacheExpireAfterWriteMillis`, `setStatsPlanCacheStatsEnabled`, `isStatsPlanCacheStatsEnabled`, `clearStatsPlanCache`, `resetStatsPlanCacheStats`, `getStatsPlanCacheHits`, `getStatsPlanCacheMisses`, `getStatsPlanCacheSize`, `getStatsPlanCacheEvictions`, `getStatsPlanCacheSnapshot` | `remove before wider adoption` | `PojoLensRuntime.statsPlanCache()` for scoped policy; `PojoLensCore.*` only if a global static path is intentionally retained | Same issue as the SQL-like cache delegates: duplicate compatibility-only indirection on top of advanced global policy controls. |

Next step after the audit:
1. Use the helper-only facade decision below as the input for `WP7.3`, which
   should decide whether any underlying static/global cache APIs survive on
   `PojoLensCore` or `PojoLensSql`.
2. Use both decisions as the implementation input for `WP7.4`.

## Pre-Adoption Facade Fate Decision

This is the concrete `WP7.2` decision, assuming the library still has no
meaningful external adoption and the unpublished `1.0.0` baseline is not being
treated as a public compatibility contract.

Decision:
- `PojoLens` should become a helper-only facade before wider adoption
- remove `PojoLens.newQueryBuilder(...)`
- remove `PojoLens.parse(...)`
- remove `PojoLens.template(...)`
- remove `PojoLens.toChartData(...)`
- keep `PojoLens.newRuntime(...)`, cursor helpers, `bundle(...)`,
  `compareSnapshots(...)`, and `report(...)`

Rationale:
- the four chosen methods are pure aliases to explicit entry points and add no
  unique behavior
- keeping them would preserve the main overlap that still makes the project
  surface feel wider than it is
- the remaining helper methods do not create competing query-entry stories

Release-note wording:
- `PojoLens` is now a helper-only facade.
- The duplicate facade entry methods `newQueryBuilder(...)`, `parse(...)`,
  `template(...)`, and `toChartData(...)` were removed in favor of the explicit
  entry points `PojoLensCore`, `PojoLensSql`, and `PojoLensChart`.
- Runtime construction, keyset cursor helpers, dataset bundling, snapshot
  comparison, and report helpers remain on `PojoLens`.

Migration wording:
- replace `PojoLens.newQueryBuilder(rows)` with
  `PojoLensCore.newQueryBuilder(rows)`
- replace `PojoLens.parse(queryText)` with `PojoLensSql.parse(queryText)`
- replace `PojoLens.template(queryText, params...)` with
  `PojoLensSql.template(queryText, params...)`
- replace `PojoLens.toChartData(rows, spec)` with
  `PojoLensChart.toChartData(rows, spec)`

Implementation note for `WP7.4`:
- update docs/examples/tests first or in the same change set
- then remove the four facade aliases
- finally re-run public-surface and binary-compat guardrails with the explicit
  understanding that this is a pre-adoption cleanup, not a published-API
  regression

## Pre-Adoption Cache Policy Audit

This is the concrete `WP7.3` decision, assuming the same no-users-yet
pre-adoption conditions as `WP7.2`.

Current repo signal:
- the runtime cache objects already expose the full policy and observability
  surface needed for tuning, clearing, and introspection
- user-facing docs already prefer `PojoLens.newRuntime(...)` when cache policy
  must vary by environment, tenant, or request scope
- current uses of the global cache APIs are concentrated in repo-local docs,
  tests, and explain plumbing rather than in an established external user base

Decision:
- no public global cache-policy owner needs to remain after pre-adoption cleanup
- remove the `PojoLens` facade cache delegates
- remove the underlying public static/global cache-control methods from
  `PojoLensSql`
- remove the underlying public static/global cache-control methods from
  `PojoLensCore`
- keep the process-default singleton caches internally so direct
  `PojoLensSql.parse(...)` and `PojoLensCore.newQueryBuilder(...)` flows still
  work with documented defaults
- make `PojoLensRuntime` the only public cache-policy tuning surface

Replacement map:

| Current global API owner | Current surface | Runtime-first replacement | Decision |
| --- | --- | --- | --- |
| `PojoLens` | SQL-like cache delegates (`set/is/get/clear/reset/snapshot`) | `runtime.sqlLikeCache().*` | `remove before wider adoption` |
| `PojoLens` | stats-plan cache delegates (`set/is/get/clear/reset/snapshot`) | `runtime.statsPlanCache().*` | `remove before wider adoption` |
| `PojoLensSql` | SQL-like cache controls and snapshot methods | `runtime.sqlLikeCache().setEnabled/isEnabled/setStatsEnabled/isStatsEnabled/setMaxEntries/getMaxEntries/setMaxWeight/getMaxWeight/setExpireAfterWriteMillis/getExpireAfterWriteMillis/clear/resetStats/getHits/getMisses/getSize/getEvictions/snapshot` | `remove before wider adoption` |
| `PojoLensCore` | stats-plan cache controls and snapshot methods | `runtime.statsPlanCache().setEnabled/isEnabled/setStatsEnabled/isStatsEnabled/setMaxEntries/maxEntries/setMaxWeight/maxWeight/setExpireAfterWriteMillis/expireAfterWriteMillis/clear/resetStats/hits/misses/size/evictions/snapshot` | `remove before wider adoption` |

Why removal is reasonable:
- the runtime-owned cache objects are already the more complete and more
  coherent policy model
- keeping public global mutators on `PojoLensSql` and `PojoLensCore` would
  preserve a second configuration story after the facade cleanup
- default singleton caches can remain an internal implementation detail for the
  explicit static entry points without remaining a public tuning surface

Implementation note for `WP7.4`:
- migrate docs/tests from global cache APIs to `PojoLensRuntime`
- keep explain payload cache snapshots, but source them from internal default
  cache plumbing instead of public facade methods
- if process-wide tuning becomes important later, add one dedicated bootstrap
  config mechanism instead of restoring a wide set of static mutators

Release-note wording:
- Cache policy tuning is now runtime-scoped.
- Public static/global cache policy methods were removed from `PojoLens`,
  `PojoLensSql`, and `PojoLensCore`.
- Use `PojoLens.newRuntime(...)` and the returned
  `runtime.sqlLikeCache()` / `runtime.statsPlanCache()` handles for cache
  tuning, clearing, statistics, and snapshots.

## Published `1.x` No-Removal Call

No current overlap justifies immediate code removal.
The documentation work completed in spikes 1-5 already resolves most of the
confusion without touching the published `1.x` stable surface.
The only exception is the explicit pre-adoption cleanup path above, which is
intended for the no-users-yet scenario before broader release.
