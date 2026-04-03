# Public API Stability (Date-Based Releases)

PojoLens now uses date-based releases.

Release format:
- Maven version: `YYYY.MM.DD.HHmm`
- Git tag: `release-<version>`

The repo is still in an explicit pre-first-release surface-reduction phase.
The intended stable surface is already documented and tested, but
compatibility-only wrappers and adapter overloads are still allowed to shrink
before the first public `release-*` tag.

## Tier Definitions

- `Stable`:
  - intended long-lived product surface
  - covered by contract tests now
  - covered by binary/source compatibility checks starting from the first
    public `release-*` tag
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

- `Stable` means "part of the intended dated-release stable surface"
- `Advanced` means "public, but not part of the narrow core promise"
- compatibility adapters are allowed, but they should not become a second
  product story

The default first-read story stays centered on the core engine:
`PojoLensCore`, `PojoLensNatural`, `PojoLensSql`, `PojoLensRuntime`,
`PojoLensChart`, and `ReportDefinition<T>`.

## Stable Surface

### Entry Points

- `PojoLensCore.newQueryBuilder(List<?>)`
- `PojoLensNatural.parse(String)`
- `PojoLensNatural.template(String, String...)`
- `PojoLensSql.parse(String)`
- `PojoLensSql.template(String, String...)`
- `PojoLensChart.toChartData(List<T>, ChartSpec)`
- `PojoLensRuntime`
  - constructor
  - `ofPreset(PojoLensRuntimePreset)`
  - `natural()`
  - `newQueryBuilder(List<?>)`
  - `parse(String)`
  - `template(String, String...)`
  - `applyPreset(PojoLensRuntimePreset)`
  - strict/lint toggles
  - `setNaturalVocabulary(NaturalVocabulary)`
  - `getNaturalVocabulary()`
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

### Plain-English Contracts

- `NaturalRuntime`:
  - `parse`, `template`
- `NaturalQuery`:
  - `of`, `source`, `equivalentSqlLike`, `params`
  - `bindTyped`, `filter`, `iterator`, `stream`, `chart`, `schema`, `explain`
  - chart execution supports either explicit `ChartSpec` or parsed natural chart phrases
  - named multi-source execution only through `JoinBindings` or `DatasetBundle`
- `NaturalTemplate`:
  - `of`, `bind`, `source`, `expectedParams`
- `NaturalBoundQuery`:
  - `filter`, `iterator`, `stream`, `chart`

### Shared Stable Types

- `NaturalVocabulary`
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

Cross-module surface guidance is documented in
[product-surface.md](product-surface.md) and
[reusable-wrappers.md](reusable-wrappers.md).

## Pre-First-Release Cleanup Policy

Before the first public `release-*` tag:

- compatibility-only wrappers, execution overloads, and public default-cache
  facades may be removed directly
- migration notes must be explicit
- contract tests should be updated in the same change

This rule is why the following were removed before the first public release:

- the `PojoLens` facade
- public raw `Map<String, List<?>>` execution overloads on SQL-like and wrapper
  APIs
- the public `FilterExecutionPlanCache` compatibility facade around the
  default stats-plan cache

If you need to adapt existing map-shaped join inputs, convert once at the
boundary with `JoinBindings.from(map)` and continue on the typed surface.

## Post-First-Release Compatibility Policy

After the first public `release-*` tag:

- do not remove stable methods/classes in later dated releases
- do not change stable method signatures incompatibly in later dated releases
- keep behavioral contracts consistent except for bug fixes
- additive API changes are allowed

For `Advanced` APIs:

- changes are allowed in later releases when needed for
  maintainability/performance
- release notes must call out notable advanced-surface changes

## Deprecation Policy

Before the first public release:

- compatibility-only surfaces can be removed without a deprecation window
- migration guidance must land in `MIGRATION.md`

After the first public release:

- stable APIs deprecate first, remove only after an explicit compatibility reset
- every deprecation includes migration guidance

## Enforcement

- `StablePublicApiContractTest` validates the intended stable surface and
  baseline behavior.
- CI binary compatibility checks start from the first `release-*` tag rather
  than from coarse major-version markers.
