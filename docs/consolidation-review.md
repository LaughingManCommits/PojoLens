# Consolidation And Deprecation Review

This document is the disposition register for the overlapping public surfaces
identified during the product-surface realignment work.

Current conclusion:
- the project is coherent enough that documentation realignment is sufficient
  for now
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
| Static/global cache-control APIs on `PojoLens` / `PojoLensCore` / `PojoLensSql` | advanced | `candidate for deprecation later` | `PojoLensRuntime` instance-scoped policy | Only future deprecation-worthy overlap found so far; not ready for action until runtime-scoped migration guidance is sufficient for common global-policy use cases. |
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

## No-Removal Call

No current overlap justifies immediate code removal.
The documentation work completed in spikes 1-5 already resolves most of the
confusion without touching the `1.x` stable surface.
