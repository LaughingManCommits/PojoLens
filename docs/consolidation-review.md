# Consolidation And Deprecation Review

This document records the overlapping public surfaces reviewed during the
product-surface realignment work and the final pre-v2 disposition.

Current conclusion:
- the pre-v2 cleanup path was executed
- the `PojoLens` facade is removed
- public raw join-map execution overloads are removed
- public static/global cache policy methods are removed from `PojoLens`,
  `PojoLensSql`, and `PojoLensCore`
- the public `FilterExecutionPlanCache` compatibility facade is removed
- the remaining public story is the intended v2 surface documented in
  [entry-points.md](entry-points.md) and
  [public-api-stability.md](public-api-stability.md)

## Guardrails

Any future consolidation must respect:

- [public-api-stability.md](public-api-stability.md)
- stable contract tests
- binary compatibility checks from the first `v2` tag onward
- the migration guidance already captured in
  [entry-points.md](entry-points.md),
  [reusable-wrappers.md](reusable-wrappers.md), and
  [advanced-features.md](advanced-features.md)

## Disposition Register

| Surface | Tier | Disposition | Preferred path | Notes |
| --- | --- | --- | --- | --- |
| `PojoLensCore`, `PojoLensSql`, `PojoLensChart`, `PojoLensRuntime` | stable core/integration | `keep` | same | These are the documented defaults for new code and the intended v2 stable surface. |
| Compatibility-only facade/overlap surfaces | removed pre-v2 | `done` | owning types directly | The old `PojoLens` facade and raw join-map execution overloads are gone. |
| Cache policy tuning | advanced runtime integration | `keep public`, `runtime-first only` | `PojoLensRuntime.sqlLikeCache()` and `PojoLensRuntime.statsPlanCache()` | Public tuning now lives only on runtime-scoped handles. Direct entry points still use internal singleton defaults. |
| Default stats-plan cache facade | removed pre-v2 | `done` | internal default cache ownership | `FilterExecutionPlanCache` is gone; direct entry points no longer expose a public singleton-cache facade. |
| `ReportDefinition<T>` | advanced workflow helper | `keep` | same | General reusable query contract; remains the top wrapper in the abstraction ladder. |
| `ChartQueryPreset<T>` | advanced workflow helper | `keep` + `de-emphasize as specialized` | `ReportDefinition<T>` when reuse becomes broader than chart-first flows | Still earns its place through preset factories and built-in chart spec. |
| `StatsViewPreset<T>` / `StatsTable<T>` | advanced workflow helper | `keep` + `de-emphasize as specialized` | `ReportDefinition<T>` for general row reuse | Still earns its place through totals and table payload semantics. |
| Regression, snapshot, metamodel, telemetry, caching, and benchmark tooling | mixed advanced/tooling | `keep` + `contain` | [advanced-features.md](advanced-features.md) | Valuable public follow-on surfaces, but not part of the first-read adoption story. |

## Completed Pre-V2 Removals

| Removed surface | Replacement | Why it was removed |
| --- | --- | --- |
| `PojoLens` facade query/chart/runtime helpers | `PojoLensCore`, `PojoLensSql`, `PojoLensChart`, `PojoLensRuntime`, `ReportDefinition`, `DatasetBundle`, `SnapshotComparison`, `SqlLikeCursor` | Pure wrapper overlap on top of already public owners. |
| Public raw `Map<String, List<?>>` execution overloads | `JoinBindings` and `DatasetBundle` | One typed multi-source story is easier to document and test. |
| Public static/global cache policy methods on `PojoLens`, `PojoLensSql`, `PojoLensCore` | `runtime.sqlLikeCache().*` and `runtime.statsPlanCache().*` | Runtime-scoped policy is the only public cache-tuning model now. |
| Public `FilterExecutionPlanCache` compatibility facade | internal default cache ownership | A public singleton-cache facade would preserve a second cache-configuration story. |

## Remaining Advanced Surfaces

These remain public, but are intentionally not part of the first-read
adoption story:

- reusable workflow wrappers beyond `ReportDefinition<T>`
- runtime cache handles and diagnostics
- regression/snapshot/metamodel/tooling helpers
- benchmark and telemetry support
