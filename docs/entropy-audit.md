# Entropy Audit

This document is the `WP8.1` baseline for the `Entropy Reduction` roadmap.
It records the public runtime surface as it existed at the start of the
roadmap, the main duplicate concept families, and the strongest code-reduction
candidates that fed later pre-v2 cleanup work.

## Baseline

- At baseline time, the runtime module exposed `122` public top-level types across
  `36` packages.
- `19` public top-level types already live under `14` `*.internal.*`
  packages.
- The largest public packages by type count are:
  - `laughing.man.commits.chart`: `11`
  - `laughing.man.commits.sqllike.ast`: `11`
  - `laughing.man.commits.builder`: `9`
  - `laughing.man.commits.filter`: `8`
  - `laughing.man.commits.sqllike`: `8`
  - `laughing.man.commits.util`: `8`
  - `laughing.man.commits`: `8`
- The documented first-read story is much smaller than the actual public
  footprint. Most entropy is in helper, parser, intermediate-row, and
  implementation-heavy types rather than the explicit entry points.

## Classification Legend

- `Keep default`: part of the intended default story for new code.
- `Keep advanced`: public and useful, but not part of the first-read path.
- `Keep compatibility/support`: public support contract kept for compatibility
  or shared value objects.
- `Internalize candidate`: public today, but the type reads like
  implementation detail, glue, or intermediate state.
- `Packaging anomaly`: public on purpose, but currently living under an
  `*.internal.*` package that increases surface confusion.

## Inventory By Area

| Area                                        | Public types                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Count | Classification                                         | Notes                                                                                                             |
|---------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------:|--------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| Root entry and support surface              | `PojoLens`, `PojoLensCore`, `PojoLensSql`, `PojoLensChart`, `PojoLensRuntime`, `PojoLensRuntimePreset`, `DatasetBundle`                                                                                                                                                                                                                                                                                                                                                                                                                                              |     7 | `Keep default` / `Keep compatibility/support`          | This is the documented top-level product story.                                                                   |
| Root support leak                           | `EngineDefaults`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |     1 | `Internalize candidate`                                | Public constants helper with no user-facing identity.                                                             |
| Annotation and enums                        | `Exclude`, `Clauses`, `Join`, `Metric`, `Separator`, `Sort`, `TimeBucket`, `WindowFunction`                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |     8 | `Keep compatibility/support`                           | Stable shared contracts used across fluent and SQL-like APIs.                                                     |
| Fluent builder contracts and value objects  | `QueryBuilder`, `FieldSelector`, `QueryRule`, `QueryMetric`, `QueryTimeBucket`, `QueryWindow`, `QueryWindowOrder`                                                                                                                                                                                                                                                                                                                                                                                                                                                    |     7 | `Keep default` / `Keep advanced`                       | Core query contract plus builder-support value objects.                                                           |
| Fluent builder implementation helpers       | `FieldSelectors`, `FilterQueryBuilder`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |     2 | `Internalize candidate`                                | `FilterQueryBuilder` is the mutable implementation behind `QueryBuilder`; `FieldSelectors` is support glue.       |
| Filter execution public contracts           | `Filter`, `FilterExecutionPlanCacheStore`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |     2 | `Keep default` / `Keep advanced`                       | `Filter` is core fluent execution. `FilterExecutionPlanCacheStore` is the current public stats-plan cache handle. |
| Filter execution implementation leakage     | `FilterCore`, `FilterImpl`, `FilterExecutionPlan`, `FilterExecutionPlanCache`, `FilterExecutionPlanCacheKey`, `FastStatsQuerySupport`                                                                                                                                                                                                                                                                                                                                                                                                                                |     6 | `Internalize candidate`                                | These were pipeline and cache internals that were still public at baseline time.                                  |
| SQL-like core contracts                     | `SqlLikeQuery`, `SqlLikeTemplate`, `SqlLikeBoundQuery`, `SqlLikeCursor`, `SqlParams`, `JoinBindings`                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |     6 | `Keep default` / `Keep compatibility/support`          | This is the explicit SQL-like contract surface.                                                                   |
| SQL-like diagnostics and cache handles      | `SqlLikeLintCodes`, `SqlLikeLintWarning`, `SqlLikeParseException`, `SqlLikeErrorCodes`, `SqlLikeQueryCache`                                                                                                                                                                                                                                                                                                                                                                                                                                                          |     5 | `Keep advanced` / `Packaging anomaly`                  | Diagnostics are public, but `SqlLikeErrorCodes` and `SqlLikeQueryCache` live under `*.internal.*` packages.       |
| SQL-like parser, AST, and execution helpers | `SqlLikeParser`, `SqlLikeErrors`, `FilterExpressionAst`, `FilterAst`, `FilterBinaryAst`, `FilterPredicateAst`, `JoinAst`, `OrderAst`, `ParameterValueAst`, `QueryAst`, `SelectAst`, `SelectFieldAst`, `SubqueryValueAst`, `AggregateExpressionSupport`, `SqlLikeBinder`, `DefaultSqlLikeQueryCacheSupport`, `SqlLikeKeysetSupport`, `SqlLikeExecutionSupport`, `SqlLikeExplainSupport`, `BooleanExpressionNormalizer`, `SqlExpressionEvaluator`, `SqlLikeLintSupport`, `BoundParameterValue`, `SqlLikeParameterSupport`, `SqlLikeJoinResolution`, `SqlLikeValidator` |    26 | `Internalize candidate`                                | This is the single largest concentration of public parser and execution internals.                                |
| Chart core contracts                        | `ChartData`, `ChartDataset`, `ChartSpec`, `ChartType`, `NullPointPolicy`, `SeriesPoint`, `MultiSeriesPoint`                                                                                                                                                                                                                                                                                                                                                                                                                                                          |     7 | `Keep compatibility/support`                           | Stable output/value contracts around chart mapping.                                                               |
| Chart specialized wrappers                  | `ChartQueryPreset`, `ChartQueryPresets`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |     2 | `Keep advanced`                                        | Specialized chart-first wrapper story already de-emphasized in docs.                                              |
| Chart.js specialized helpers                | `ChartJsAdapter`, `ChartJsPayload`, `ChartJsData`, `ChartJsDataset`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |     4 | `Keep advanced`                                        | Useful, but clearly specialized frontend payload helpers.                                                         |
| Chart mapping and validation helpers        | `ChartMapper`, `ChartResultMapper`, `ChartValidation`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |     3 | `Internalize candidate`                                | These are mapping/validation mechanics behind `PojoLensChart` and wrapper APIs.                                   |
| General reusable wrapper                    | `ReportDefinition`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |     1 | `Keep advanced`                                        | This is the general reusable-query contract.                                                                      |
| Stats/table specialized wrappers            | `StatsViewPreset`, `StatsViewPresets`, `StatsTable`, `StatsTablePayload`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |     4 | `Keep advanced`                                        | Useful, but overlapping specialized workflow layer.                                                               |
| Tabular schema support                      | `TabularSchema`, `TabularColumn`, `TabularRows`, `TabularSchemaSupport`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |     4 | `Keep compatibility/support` + `Internalize candidate` | Keep the schema contracts; `TabularSchemaSupport` is support glue.                                                |
| Domain/intermediate row models              | `QueryRow`, `QueryField`, `RawQueryRow`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |     3 | `Internalize candidate`                                | `QueryRow` already describes itself as an internal row container.                                                 |
| Computed-field public surface               | `ComputedFieldDefinition`, `ComputedFieldRegistry`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |     2 | `Keep advanced`                                        | Public authoring/runtime hook.                                                                                    |
| Computed-field support helper               | `ComputedFieldSupport`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |     1 | `Internalize candidate`                                | Public only because the implementation currently reaches across packages.                                         |
| Snapshot comparison surface                 | `SnapshotComparison`, `SnapshotComparisonSummary`, `SnapshotDeltaRow`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |     3 | `Keep advanced`                                        | Public adjacent workflow helper.                                                                                  |
| Telemetry public surface                    | `QueryTelemetryEvent`, `QueryTelemetryListener`, `QueryTelemetryStage`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |     3 | `Keep advanced`                                        | Public operational diagnostics surface.                                                                           |
| Telemetry support helper                    | `QueryTelemetrySupport`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |     1 | `Internalize candidate`                                | Support glue behind telemetry emission.                                                                           |
| Metamodel surface                           | `FieldMetamodel`, `FieldMetamodelGenerator`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |     2 | `Keep advanced`                                        | Optional typed-authoring support.                                                                                 |
| Testing/regression surface                  | `FluentSqlLikeParity`, `QueryRegressionFixture`, `QuerySnapshotFixture`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |     3 | `Keep advanced`                                        | Useful follow-on testing helpers, but not part of first-read adoption.                                            |
| Time preset helper                          | `TimeBucketPreset`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |     1 | `Keep advanced`                                        | Optional authoring convenience for time buckets.                                                                  |
| Utility package                             | `CollectionUtil`, `GroupKeyUtil`, `ObjectUtil`, `QueryFieldLookupUtil`, `ReflectionUtil`, `SchemaIndexUtil`, `StringUtil`, `TimeBucketUtil`                                                                                                                                                                                                                                                                                                                                                                                                                          |     8 | `Internalize candidate`                                | Pure support/utilities with no documented product identity.                                                       |

## Key Findings

### 1. The public surface is much wider than the documented product story

At baseline time, the explicit default story was roughly:

- `PojoLensCore`
- `PojoLensSql`
- `PojoLensRuntime`
- `PojoLensChart`
- helper-only `PojoLens`
- `ReportDefinition` as the general reusable wrapper

That is a small fraction of the `122` public types currently exposed.

### 2. Internalization is the highest-leverage code-reduction path

There are `52` clear internalization candidates before even debating wrapper
or workflow-helper reductions:

- `EngineDefaults`
- builder implementation helpers
- filter execution internals
- parser/AST/execution helpers
- chart mapping/validation helpers
- row/intermediate domain models
- support helpers under `computed`, `telemetry`, and `table`
- the entire public `util` package

This is the fastest path to lower compatibility burden and later code deletion.

### 3. `.internal.*` packages are not consistently internal

The current surface mixes three different realities:

- genuinely internal-looking helpers already under `*.internal.*`
- intentionally public types that still live under `*.internal.*`
  (`SqlLikeQueryCache`, `SqlLikeErrorCodes`)
- public support glue outside `*.internal.*`

That package naming mismatch is itself entropy. `WP8.2` should decide whether
to internalize those types or move the intentionally public ones to
non-internal packages.

### 4. Cache tuning currently leaks implementation types

`PojoLensRuntime.sqlLikeCache()` and `PojoLensRuntime.statsPlanCache()` expose
implementation-heavy cache types directly:

- `SqlLikeQueryCache`
- `FilterExecutionPlanCacheStore`

The stats-plan cache type also exposes `FilterExecutionPlanCacheKey` and
`FilterExecutionPlan` in its public API. That keeps execution-plan internals
public even when callers only need policy and observability.

### 5. Wrapper and binding overlap is real, but narrower than implementation leakage

The reusable wrapper ladder is coherent, but still exposes three peer-level
identities:

- `ReportDefinition`
- `ChartQueryPreset`
- `StatsViewPreset`

The multi-source story also has three shapes:

- raw `Map<String, List<?>>`
- `JoinBindings`
- `DatasetBundle`

These should be simplified after implementation leaks are classified, not
before.

## Duplicate Concept Families

| Concept family             | Current public shapes                                                                       | Entropy risk                                                  | Input work package |
|----------------------------|---------------------------------------------------------------------------------------------|---------------------------------------------------------------|--------------------|
| Reusable query contract    | `ReportDefinition`, `ChartQueryPreset`, `StatsViewPreset`                                   | Three peer-level wrapper identities for one engine            | `WP8.3`            |
| Multi-source binding       | raw `Map<String, List<?>>`, `JoinBindings`, `DatasetBundle`                                 | Three ways to express join sources and snapshot reuse         | `WP8.3`            |
| Runtime construction       | `new PojoLensRuntime()`, `PojoLensRuntime.ofPreset(...)`                                         | Two equally-valid creation idioms for the same scoped runtime | `WP8.3`            |
| Cache tuning surface       | `SqlLikeQueryCache`, `FilterExecutionPlanCacheStore`                                        | Public tuning depends on implementation-heavy cache classes   | `WP8.2`            |
| Chart mapping access paths | `PojoLensChart`, `Filter.chart(...)`, `SqlLikeQuery.chart(...)`, wrappers, `ChartJsAdapter` | Many access points around one mapping pipeline                | `WP8.3` / `WP8.4`  |

## Shortlist of Code-Reduction Candidates

### Strong internalization candidates

- `EngineDefaults`
- `FilterQueryBuilder`
- `FilterCore`
- `FilterImpl`
- `FilterExecutionPlan`
- `FilterExecutionPlanCache`
- `FilterExecutionPlanCacheKey`
- `FastStatsQuerySupport`
- `SqlLikeParser`
- all public `sqllike.ast.*` types
- `SqlLikeErrors`
- `ChartMapper`
- `ChartResultMapper`
- `ChartValidation`
- `QueryRow`, `QueryField`, `RawQueryRow`
- `ComputedFieldSupport`
- `TabularSchemaSupport`
- `QueryTelemetrySupport`
- the public `util` package

### Packaging anomalies to resolve

- `SqlLikeQueryCache`: intentionally public, but under
  `laughing.man.commits.sqllike.internal.cache`
- `SqlLikeErrorCodes`: intentionally public, but under
  `laughing.man.commits.sqllike.internal.error`

### Keep-public but de-emphasize

- `ChartQueryPreset` / `ChartQueryPresets`
- `StatsViewPreset` / `StatsViewPresets`
- raw `Map<String, List<?>>` join-source overloads where `JoinBindings` or
  `DatasetBundle` already exist

## Resulting Inputs

### Input to `WP8.2`

- start with the `52` internalization candidates above
- decide which can move in `1.x` versus which must wait for `2.x`
- decide whether public cache handles should be wrapped behind smaller public
  interfaces instead of exposing implementation classes

### Input to `WP8.3`

- make `ReportDefinition` the canonical reusable-query contract unless a
  specialized wrapper adds durable value
- choose one default multi-source binding story for new code
- decide whether both runtime construction idioms should remain equally
  promoted

### Input to `WP8.4`

- focus on duplicate execution mechanics behind `FilterQueryBuilder`,
  `FilterImpl`, `FilterCore`, parser/AST flow, and chart mapping helpers
- tie any internal-path unification to benchmark and regression coverage before
  changing hot code

