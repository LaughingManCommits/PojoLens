# Public API Stability (1.x)

PojoLens now uses explicit API tiers so compatibility expectations are clear.

## Tier Definitions

- `Stable`:
  - Covered by compatibility guarantees for `1.x`.
  - Breaking source/binary changes require deprecation first (or a major version).
- `Advanced`:
  - Public and supported, but may evolve faster within `1.x`.
  - Best-effort compatibility only.
- `Internal`:
  - No compatibility guarantee.
  - Includes any `*.internal.*` package.

## Reading The Tiers

Product-surface families are defined in [product-surface.md](product-surface.md):

- core query engine
- workflow helpers
- integration
- compatibility
- tooling

The tiers and families are related, but they are not the same thing:

- `Stable` means compatibility-guaranteed for `1.x`.
- `Advanced` means public but faster-evolving.
- a surface can be `Compatibility` and still be `Stable`
  (for example `PojoLens`)
- a surface can be a `Workflow helper` and still be `Advanced`
  (for example report/preset convenience wrappers)
- the default first-read product story should still center on the core query
  engine even when other public surfaces are stable

The non-default public surfaces are grouped in
[advanced-features.md](advanced-features.md) so they stay discoverable without
competing with the core onboarding path.

## Stable Surface (1.x)

Stable surface is intentionally wider than the default onboarding story. Some
compatibility or support contracts remain stable even though the preferred
narrative for new users should start with the core query engine.
`PojoLens` is stable as a helper-only compatibility facade; the overlapping
query/chart entry aliases were removed before the current surface was locked.

### Entry Points

- `PojoLens`
  - `newRuntime()`
  - `newRuntime(PojoLensRuntimePreset)`
  - `newKeysetCursorBuilder()`
  - `parseKeysetCursor(String)`
  - `bundle(List<?>)`
  - `bundle(List<?>, Map<String, List<?>>)`
  - `bundle(List<?>, JoinBindings)`
- `PojoLensCore.newQueryBuilder(List<?>)`
- `PojoLensSql.parse(String)`
- `PojoLensSql.template(String, String...)`
- `PojoLensChart.toChartData(List<T>, ChartSpec)`
- `PojoLensRuntime`
  - `newQueryBuilder(List<?>)`
  - `parse(String)`
  - `template(String, String...)`
  - `applyPreset(PojoLensRuntimePreset)`
  - strict/lint toggles

### Fluent Query Contracts

- `QueryBuilder`:
  - `addRule`, `addOrder`, `addGroup`, `addField`, `addMetric`, `addCount`, `addHaving`
  - `limit`, `offset`
  - `initFilter`, `explain`, `schema`
  - `addJoinBeans`
- `Filter`:
  - `filter`, `iterator`, `stream`, `chart`, `join`

### SQL-like Contracts

- `SqlLikeQuery`:
  - `of`, `source`, `params`
  - `keysetAfter`, `keysetBefore`
  - `bindTyped`, `filter`, `iterator`, `stream`, `chart`, `schema`, `explain`
- `SqlLikeBoundQuery`:
  - `filter`, `iterator`, `stream`, `chart`
- `SqlLikeTemplate`:
  - `of`, `bind`, `source`, `expectedParams`
- `SqlParams`:
  - `builder`, `empty`, `asMap`
- `SqlLikeCursor`:
  - `builder`, `fromToken`, `toToken`
- `JoinBindings`:
  - `of`, `builder`, `asMap`

### Shared Stable Types

- `DatasetBundle`
- `PojoLensRuntimePreset`
- Query enums:
  - `Clauses`, `Join`, `Metric`, `Separator`, `Sort`, `TimeBucket`
- Chart contracts used by stable entry points:
  - `ChartSpec`, `ChartData`, `ChartDataset`, `ChartType`

## Advanced Surface (Examples)

The following remain public, but are treated as advanced in `1.x`:

- fine-grained runtime policy tuning and cache observability APIs on
  `PojoLensRuntime`
- reusable workflow wrappers such as `PojoLens.report(...)` and chart/stats
  preset helpers
- `PojoLens.compareSnapshots(...)` and regression/testing helper APIs
- metamodel generation helpers
- benchmark and threshold tooling

See [advanced-features.md](advanced-features.md) for the grouped landing page
used in the docs.
Current overlap dispositions live in
[consolidation-review.md](consolidation-review.md).

## Compatibility Policy

For `Stable` APIs in `1.x`:

- Do not remove stable methods/classes in minor/patch releases.
- Do not change method signatures or return types incompatibly.
- Keep behavioral contracts consistent except for bug fixes.
- Additive API changes are allowed.

For `Advanced` APIs:

- Changes are allowed in minors when needed for maintainability/performance.
- Release notes must call out notable advanced-surface changes.

## Deprecation Policy

- Deprecate first, remove later.
- Keep deprecated stable APIs for at least one minor release before any removal.
- Every deprecation must include migration guidance in `MIGRATION.md`.
- Removal or incompatible change to stable APIs requires a major version.

## Enforcement

- Contract tests in `StablePublicApiContractTest` validate stable entry-point availability and baseline behavior.
- CI runs `japicmp` through the `binary-compat` Maven profile against the latest `v*` tag baseline and fails on binary/source-incompatible stable-surface changes.
