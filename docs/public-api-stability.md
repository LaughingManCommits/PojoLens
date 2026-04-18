# Public API Stability (Date-Based Releases)

PojoLens now uses date-based releases.

Release format:
- Maven version: `YYYY.MM.DD.HHmm`
- Git tag: `release-<version>`

The first public `release-*` baseline has shipped. Stable APIs are documented,
covered by contract tests, and checked against public release baselines for
binary/source compatibility.

## Tier Definitions

- `Stable`:
  - intended long-lived product surface
  - covered by contract tests now
  - covered by binary/source compatibility checks against public `release-*`
    baselines
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

- `Stable` means "part of the dated-release stable surface"
- `Advanced` means "public, but not part of the narrow core promise"
- compatibility adapters are allowed, but they should not become a second
  product story

The default first-read story is SQL-like first:
`PojoLensSql`, `PojoLensNatural`, `PojoLensRuntime`, `PojoLensChart`,
`PojoLensTree`, and `ReportDefinition<T>`.

## Stable Surface

### Entry Points

- `PojoLensNatural.parse(String)`
- `PojoLensNatural.template(String, String...)`
- `PojoLensSql.parse(String)`
- `PojoLensSql.template(String, String...)`
- `PojoLensChart.toChartData(List<T>, ChartSpec)`
- `PojoLensTree.fromFlat(List<T>, Function<T,K>, Function<T,K>)`
- `PojoLensTree.subtreeOf(List<T>, Function<T,K>, Function<T,K>, K)`
- `PojoLensRuntime`
  - constructor
  - `ofPreset(PojoLensRuntimePreset)`
  - `natural()`
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

### Tree Row-Shaping Contracts

- `TreeTraversalBuilder<T,K>`:
  - `subtree`, `maxDepth`, `prune`, `leavesOnly`
  - `toList`, `toEntries`
- `TreeEntry<T>`:
  - `node`, `depth`, `parent`

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

## Internal Engine DSL

The fluent builder surface is being moved out of the stable public API.
It remains useful as implementation infrastructure while the reset is in
progress, but it is no longer documented as a stable product surface.

Internal / compatibility-reset candidates:

- `PojoLensCore`
- `PojoLensRuntime.newQueryBuilder(...)`
- `QueryBuilder`
- `FilterQueryBuilder`
- `Filter`
- `QueryRule`
- `FluentQueryDefinition<T>`
- `ReportDefinition.fluent(...)`

Maintainer guidance lives in [internal-fluent-engine.md](internal-fluent-engine.md).

Cross-module surface guidance is documented in
[product-surface.md](product-surface.md) and
[reusable-wrappers.md](reusable-wrappers.md).

## Compatibility Policy

For `Stable` APIs:

- do not remove stable methods/classes in later dated releases
- do not change stable method signatures incompatibly in later dated releases
- keep behavioral contracts consistent except for bug fixes
- additive API changes are allowed

For `Advanced` APIs:

- changes are allowed in later releases when needed for
  maintainability/performance
- release notes must call out notable advanced-surface changes

Compatibility-only surfaces removed before the first public baseline include
the old `PojoLens` facade, public raw `Map<String, List<?>>` execution
overloads on SQL-like and wrapper APIs, and the public
`FilterExecutionPlanCache` compatibility facade around the default stats-plan
cache. Adapt existing map-shaped join inputs at the boundary with
`JoinBindings.from(map)` and continue on the typed surface.

## Deprecation Policy

- stable APIs deprecate first, remove only after an explicit compatibility reset
- every deprecation includes migration guidance

## Enforcement

- `StablePublicApiContractTest` validates the intended stable surface and
  baseline behavior.
- CI binary compatibility checks start from the first `release-*` tag rather
  than from coarse major-version markers.
- `SURFACE-WP4` will align the stable public API tests and binary compatibility
  include list with the SQL-like-first surface.
