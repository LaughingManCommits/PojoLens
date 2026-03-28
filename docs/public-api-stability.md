# Public API Stability (Pre-V2)

PojoLens is in an explicit pre-v2 surface-reduction phase.
The intended v2 surface is already documented and tested, but compatibility-only
wrappers and adapter overloads are still allowed to shrink before the first
`v2` release.

## Tier Definitions

- `Stable`:
  - intended v2 product surface
  - covered by contract tests now
  - covered by binary/source compatibility checks starting from the first `v2`
    release tag
- `Advanced`:
  - public and supported, but expected to evolve faster
  - best-effort compatibility only
- `Internal`:
  - no compatibility guarantee
  - includes `*.internal.*` packages and implementation helpers

## Reading The Tiers

Product-surface families are defined in [product-surface.md](product-surface.md):

- core query engine
- workflow helpers
- integration
- tooling
- compatibility adapters

The families and tiers are related, but not the same thing:

- `Stable` means "part of the intended v2 surface"
- `Advanced` means "public, but not part of the narrow core promise"
- compatibility adapters are allowed, but they should not become a second
  product story

The default first-read story stays centered on the core engine:
`PojoLensCore`, `PojoLensSql`, `PojoLensRuntime`, `PojoLensChart`, and
`ReportDefinition<T>`.

## Stable Surface

### Entry Points

- `PojoLensCore.newQueryBuilder(List<?>)`
- `PojoLensSql.parse(String)`
- `PojoLensSql.template(String, String...)`
- `PojoLensChart.toChartData(List<T>, ChartSpec)`
- `PojoLensRuntime`
  - constructor
  - `ofPreset(PojoLensRuntimePreset)`
  - `newQueryBuilder(List<?>)`
  - `parse(String)`
  - `template(String, String...)`
  - `applyPreset(PojoLensRuntimePreset)`
  - strict/lint toggles
- `DatasetBundle`
  - `of(List<?>)`
  - `of(List<?>, JoinBindings)`
  - `builder(List<?>)`

### Fluent Query Contracts

- `QueryBuilder`:
  - `addRule`, `addOrder`, `addGroup`, `addField`, `addMetric`, `addCount`
  - `addHaving`, `addQualify`, `addJoinBeans`
  - `limit`, `offset`
  - `initFilter`, `explain`, `schema`
- `Filter`:
  - `filter`, `iterator`, `stream`, `chart`, `join`

### SQL-like Contracts

- `SqlLikeQuery`:
  - `of`, `source`, `params`
  - `keysetAfter`, `keysetBefore`
  - `bindTyped`, `filter`, `iterator`, `stream`, `chart`, `schema`, `explain`
  - named multi-source execution only through `JoinBindings` or `DatasetBundle`
- `SqlLikeBoundQuery`:
  - `filter`, `iterator`, `stream`, `chart`
- `SqlLikeTemplate`:
  - `of`, `bind`, `source`, `expectedParams`
- `SqlParams`:
  - `builder`, `empty`, `asMap`
- `SqlLikeCursor`:
  - `builder`, `fromToken`, `toToken`
- `JoinBindings`:
  - `empty`, `of`, `from`, `builder`, `asMap`

### Shared Stable Types

- `PojoLensRuntimePreset`
- query enums:
  - `Clauses`, `Join`, `Metric`, `Separator`, `Sort`, `TimeBucket`
- chart contracts:
  - `ChartSpec`, `ChartData`, `ChartDataset`, `ChartType`

## Advanced Surface (Examples)

The following remain public, but are treated as advanced:

- fine-grained runtime cache tuning and observability on `PojoLensRuntime`
- reusable workflow wrappers such as `ReportDefinition`, `ChartQueryPreset`,
  `StatsViewPreset`, and related helper types
- `SnapshotComparison`, regression fixtures, parity helpers, and other testing
  support
- metamodel generation
- benchmark tooling and threshold helpers

Current overlap dispositions live in
[consolidation-review.md](consolidation-review.md).

## Pre-V2 Cleanup Policy

Before the first `v2` release:

- compatibility-only wrappers, execution overloads, and public default-cache
  facades may be removed directly
- migration notes must be explicit
- contract tests should be updated in the same change

This rule is why the following were removed before `v2`:

- the `PojoLens` facade
- public raw `Map<String, List<?>>` execution overloads on SQL-like and wrapper
  APIs
- the public `FilterExecutionPlanCache` compatibility facade around the
  default stats-plan cache

If you need to adapt existing map-shaped join inputs, convert once at the
boundary with `JoinBindings.from(map)` and continue on the typed surface.

## Post-V2 Compatibility Policy

After the first `v2` release tag:

- do not remove stable methods/classes in `2.x` minors or patches
- do not change stable method signatures incompatibly in `2.x`
- keep behavioral contracts consistent except for bug fixes
- additive API changes are allowed

For `Advanced` APIs:

- changes are allowed in minors when needed for maintainability/performance
- release notes must call out notable advanced-surface changes

## Deprecation Policy

Before `v2`:

- compatibility-only surfaces can be removed without a deprecation window
- migration guidance must land in `MIGRATION.md`

After `v2`:

- stable APIs deprecate first, remove in the next major
- every deprecation includes migration guidance

## Enforcement

- `StablePublicApiContractTest` validates the intended v2 stable surface and
  baseline behavior.
- CI binary compatibility checks start from `v2` release tags, not from the
  pre-v2 facade/adapter surface.
