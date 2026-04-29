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
`PojoLensFiles`, `TypedQuery<T>`, `PojoLensTree`, and `ReportDefinition<T>`.

## Stable Surface

### Entry Points

- `PojoLensNatural.parse(String)`
- `PojoLensNatural.template(String, String...)`
- `PojoLensSql.parse(String)`
- `PojoLensSql.template(String, String...)`
- `PojoLensChart.toChartData(List<T>, ChartSpec)`
- `PojoLensFiles`
  - `csv(Path, Class<T>)`
  - `csv(Path, Class<T>, CsvOptions)`
  - `csvWithReport(Path, Class<T>)`
  - `csvWithReport(Path, Class<T>, CsvOptions)`
  - `tsv(Path, Class<T>)`
  - `tsv(Path, Class<T>, CsvOptions)`
  - `tsvWithReport(Path, Class<T>)`
  - `tsvWithReport(Path, Class<T>, CsvOptions)`
  - `json(Path, Class<T>)`
  - `json(Path, Class<T>, JsonOptions)`
  - `jsonWithReport(Path, Class<T>)`
  - `jsonWithReport(Path, Class<T>, JsonOptions)`
  - `jsonl(Path, Class<T>)`
  - `jsonl(Path, Class<T>, JsonOptions)`
  - `jsonlWithReport(Path, Class<T>)`
  - `jsonlWithReport(Path, Class<T>, JsonOptions)`
- `PojoLensCsv`
  - `read(Path, Class<T>)`
  - `read(Path, Class<T>, CsvOptions)`
  - `readWithReport(Path, Class<T>)`
  - `readWithReport(Path, Class<T>, CsvOptions)`
- `PojoLensTree.fromFlat(List<T>, Function<T,K>, Function<T,K>)`
- `PojoLensTree.subtreeOf(List<T>, Function<T,K>, Function<T,K>, K)`
- `PojoLensRuntime`
  - constructor
  - `ofPreset(PojoLensRuntimePreset)`
  - `natural()`
  - `files()`
  - `parse(String)`
  - `template(String, String...)`
  - `applyPreset(PojoLensRuntimePreset)`
  - strict/lint toggles
  - `setNaturalVocabulary(NaturalVocabulary)`
  - `getNaturalVocabulary()`
  - `setQueryExposurePolicy(QueryExposurePolicy)`
  - `getQueryExposurePolicy()`
  - `setJsonDefaults(JsonOptions)`
  - `getJsonDefaults()`
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
  - `bindTyped`, `filter`, `filterWithPushdown`, `filterPage`, `iterator`, `stream`, `chart`, `schema`, `exposurePolicy`, `diagnostics`, `explain`, `planPreview`, `pushdownPreview`, `pushdownRequest`
  - `SqlLikePushdownAdapter`, `SqlLikePushdownRequest`, `SqlLikePushdownResult`, `SqlLikePushdownException`, and `SqlLikeResultSetAdapter` are stable bridge contracts for host-owned pushdown adapters.
  - named multi-source execution only through `JoinBindings` or `DatasetBundle`
- `QueryExposurePolicy`:
  - `unrestricted`, `builder`, `toBuilder`
  - `allowedFields`, `allowedSources`
  - `restrictsFields`, `restrictsSources`
  - `allowsField`, `allowsSource`
- `QueryCancellationToken`:
  - `ofAtomic`, `ofThread`, `isCancelled`
- `QueryExecutionGuard`:
  - `unrestricted`, `builder`, `isUnrestricted`, `hasPreExecutionLimits`
  - `maxRowsScanned`, `maxRowsReturned`, `maxComplexityScore`,
    `maxDurationMillis`, `cancellationToken`
  - `checkPreExecution`, `checkPostExecution`, `checkCancellation`
- `QueryExecutionGuard.Builder`:
  - `maxRowsScanned`, `maxRowsReturned`, `maxComplexityScore`,
    `maxDurationMillis`, `cancellationToken`, `build`
- `QueryGuardOutcome`:
  - `allowed`, `blocked`, `cancelled`
  - `allowed`, `blocked`, `blockCode`, `blockReason`, `complexitySummary`,
    `rowsReturnedBeforeAbort`, `auditMetadata`
- `QueryExecutionGuardException`:
  - `of`, `outcome`
- `QueryDiagnostics`:
  - `valid`, `errors`, `lintWarnings`, `requiredParams`, `referencedFields`,
    `outputFields`, `joinSources`, `hasSubqueries`
- `QueryDiagnosticsError`:
  - `code`, `message`
- `SqlLikePlanPreview`:
  - `source`, `isWildcard`, `selectFields`, `filters`, `filterExpression`, `groupByFields`,
    `havingFilters`, `havingExpression`, `qualifyFilters`, `qualifyExpression`, `orderFields`,
    `joins`, `paging`, `requiredParams`, `hasSubqueries`
  - `hasGrouping`, `hasJoins`, `hasWindows`, `hasPaging`, `hasAggregation`
- `PlanPreviewField`:
  - `field`, `outputName`, `alias`, `metric`, `timeBucket`, `windowFunction`,
    `windowPartitionFields`, `windowOrderFields`, `windowFrame`
  - `isComputed`, `isCountAll`, `isWindow`, `isMetric`, `isTimeBucket`
- `PlanPreviewFilter`:
  - `field`, `operator`, `valueKind`, `parameterName`, `subqueryPreview`
- `PlanPreviewPredicate`:
  - `isLeaf`, `filter`, `operator`, `children`
- `PlanPreviewJoin`:
  - `type`, `source`, `parentField`, `childField`
- `PlanPreviewOrder`:
  - `field`, `direction`
- `PlanPreviewPaging`:
  - `limit`, `limitParameter`, `offset`, `offsetParameter`, `hasLimit`, `hasOffset`
- `SqlLikePushdownPreview`:
  - `source`, `mode`, `pushableStages`, `inMemoryStages`, `fallbackReasons`
  - `isFullyPushable`, `requiresSplitExecution`, `isInMemoryOnly`
- `SqlLikePushdownMode`:
  - `FULL`, `SPLIT`, `IN_MEMORY_ONLY`
- `SqlLikeBoundQuery`:
  - `filter`, `iterator`, `stream`, `chart`
- `SqlLikeTemplate`:
  - `of`, `bind`, `source`, `expectedParams`
- `SqlParams`:
  - `builder`, `empty`, `asMap`
- `SqlLikeCursor`:
  - `builder`, `fromToken`, `toToken`
- `PageResult<T>`:
  - `rows`, `hasMore`, `nextCursor`
- `JoinBindings`:
  - `empty`, `of`, `from`, `builder`, `asMap`

### Typed DSL Contracts

- `TypedField<T,V>`:
  - `of`, `fieldName`, `valueType`
  - predicate factories: `eq`, `ne`, `gt`, `gte`, `lt`, `lte`, `in`, `isNull`, `isNotNull`
- `TypedPredicate<T>`:
  - `operator`, `field`, `value`, `values`, `children`, `isLeaf`
  - combinators: `and`, `or`, `not`, `allOf`, `anyOf`
- `TypedQuery<T>`:
  - `from`, `select`, `where`, `orderBy`, `orderByDesc`, `limit`, `offset`
  - `executionGuard`, `filter`, `explain`, `schema`
  - current stable foundation covers projection, filters, ordering, offset, limit,
    explain/schema, and row-scan/row-return/duration/cancellation guard checks
  - typed grouping, aggregation, joins, windows, and subqueries are deferred
- `FieldMetamodelGenerator.generateTyped(...)`

### Plain-English Contracts

- `NaturalRuntime`:
  - `parse`, `template`
- `NaturalQuery`:
  - `of`, `source`, `equivalentSqlLike`, `params`
  - `bindTyped`, `filter`, `iterator`, `stream`, `chart`, `schema`, `exposurePolicy`, `diagnostics`, `explain`
  - chart execution supports either explicit `ChartSpec` or parsed natural chart phrases
  - named multi-source execution only through `JoinBindings` or `DatasetBundle`
- `NaturalTemplate`:
  - `of`, `bind`, `source`, `expectedParams`
- `NaturalBoundQuery`:
  - `filter`, `iterator`, `stream`, `chart`

### Shared Stable Types

- `NaturalVocabulary`
- `PojoLensRuntimePreset`
- file-boundary loader contracts:
  - `CsvOptions`, `CsvCoercionPolicy`, `CsvLoadResult`, `CsvLoadReport`,
    `CsvLoadException`, `JsonOptions`, `JsonLoadResult`, `JsonLoadReport`,
    `JsonLoadException`, `CsvRuntime`, `FileLoadRuntime`
- query enums:
  - `Clauses`, `Join`, `Metric`, `Separator`, `Sort`, `TimeBucket`
- chart contracts:
  - `ChartSpec`, `ChartData`, `ChartDataset`, `ChartType`

## Advanced Surface (Examples)

The following remain public, but are treated as advanced:

- fine-grained runtime cache tuning and observability on `PojoLensRuntime`
- reusable workflow wrappers such as `ReportDefinition`, `ChartQueryPreset`,
  `StatsViewPreset`, and related helper types
- facet helper contracts such as `FacetPresets`, `FacetQuery`, and
  `FacetOption`
- the Spring/JDBC bridge helper `PojoLensJdbc`
- `SnapshotComparison`, regression fixtures, parity helpers, and other testing
  support
- metamodel generation beyond the stable typed-field generator entry point
- benchmark tooling and threshold helpers

## Internal Engine DSL

The fluent builder surface has moved out of the stable public API.
It remains useful as implementation infrastructure, but it is not documented
as a public product surface.

Internal implementation entry points:

- `laughing.man.commits.internal.FluentEngine`
- `laughing.man.commits.internal.builder.QueryBuilder`
- `laughing.man.commits.internal.builder.FilterQueryBuilder`
- `Filter`
- `laughing.man.commits.internal.builder.QueryRule`
- `laughing.man.commits.internal.builder.FluentQueryDefinition<T>`

Removed public fluent bridge methods:

- `PojoLensCore`
- `PojoLensRuntime.newQueryBuilder(...)`
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
- stable public API tests and binary compatibility include lists track the
  SQL-like-first surface.
