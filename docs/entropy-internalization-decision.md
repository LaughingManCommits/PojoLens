# Entropy Internalization Decision

This document is the `WP8.2` decision artifact for the `Entropy Reduction`
roadmap.
It turns the `WP8.1` audit into release-scoped calls about what can narrow in
`1.x`, what must stay public in `1.x`, and what should wait for `2.x`.

## Decision Summary

- Stable-surface removals wait for `2.x`.
- Advanced/helper implementation leaks may be narrowed in `1.x` when they are
  outside the stable compatibility gate and outside the documented default
  product story.
- In `1.x`, prefer internalization or public-surface subtraction over package
  reshuffles that add aliases and more code.
- Public runtime cache handles stay public in `1.x`, but the surrounding
  execution-plan internals should stop leaking through them.

## Compatibility Inputs

- `pojo-lens/pom.xml` locks the `1.x` stable surface through the
  `binary-compat` profile and explicit `japicmp` includes.
- `StablePublicApiContractTest` enforces the stable entry points and core query
  contracts rather than the full public type count.
- [public-api-stability.md](public-api-stability.md) allows `Advanced` APIs to
  evolve faster within `1.x` when maintainability or performance requires it.
- [caching.md](caching.md) documents `runtime.sqlLikeCache()` and
  `runtime.statsPlanCache()` as intentional public policy-tuning handles.
- `PublicSurfaceContractTest` still references `FilterQueryBuilder`, but that
  is a repo-local guardrail, not a user contract. It should be refocused on
  `QueryBuilder` and `Filter` behavior when implementation types narrow.

## Package Policy

- Keep public `1.x` packages centered on the documented product story:
  entry points, core query contracts, chart contracts, runtime integration,
  diagnostics, and deliberately public advanced wrappers/tooling.
- Move implementation-heavy helpers behind package-private scope or
  `*.internal.*` packages when they are not intentional user contracts.
- Do not add new intentionally public contracts under `*.internal.*`.
- Public cache handles may remain public, but their public members should be
  policy/observability oriented rather than parser or execution-plan plumbing.

## Disposition Table

| Area                                                           | Types                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | Decision                                 | Target                                                       | Compatibility notes                                                                                                                                        |
|----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Stable entry/core surface                                      | `PojoLens`, `PojoLensCore`, `PojoLensSql`, `PojoLensChart`, `PojoLensRuntime`, `PojoLensRuntimePreset`, `DatasetBundle`, `QueryBuilder`, `Filter`, `SqlLikeQuery`, `SqlLikeBoundQuery`, `SqlLikeTemplate`, `SqlParams`, `SqlLikeCursor`, `JoinBindings`, shared enums, chart value contracts                                                                                                                                                                                                                                                                         | `Keep public stable`                     | `2.x` for removals only                                      | This is the `1.x` freeze set enforced by docs, contract tests, and `japicmp`.                                                                              |
| Builder/filter implementation helpers                          | `EngineDefaults`, `FieldSelectors`, `FilterQueryBuilder`, `FilterCore`, `FilterImpl`, `FastStatsQuerySupport`                                                                                                                                                                                                                                                                                                                                                                                                                                                        | `Internalize`                            | `1.x`                                                        | These are engine mechanics, not intended product contracts. Update tests to stop naming implementation types directly.                                     |
| Filter execution-plan internals                                | `FilterExecutionPlan`, `FilterExecutionPlanCache`, `FilterExecutionPlanCacheKey`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | `Internalize`                            | `1.x`                                                        | These types exist to support execution planning and cache keys. They should disappear from public signatures before deeper cleanup.                        |
| Stats-plan cache handle                                        | `FilterExecutionPlanCacheStore`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | `Keep public advanced`, `narrow members` | `1.x` for member narrowing, `2.x` for type/interface cleanup | Keep tuning and observability methods public. Stop treating `getOrBuild(FilterExecutionPlanCacheKey, Supplier<FilterExecutionPlan>)` as a public contract. |
| SQL-like diagnostics surface                                   | `SqlLikeLintCodes`, `SqlLikeLintWarning`, `SqlLikeParseException`, `SqlLikeErrorCodes`                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | `Keep public advanced`                   | `1.x`; package cleanup in `2.x` if still needed              | Error codes and diagnostics are user-facing and already documented. `SqlLikeErrorCodes` is a packaging anomaly, not an internalization target for `1.x`.   |
| SQL-like cache handle                                          | `SqlLikeQueryCache`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | `Keep public advanced`                   | `1.x`; package cleanup in `2.x` if still needed              | `PojoLensRuntime.sqlLikeCache()` and current docs/tests depend on this type. Adding a public alias now would increase code instead of reducing it.         |
| SQL-like parser, AST, and execution helpers                    | `SqlLikeParser`, `SqlLikeErrors`, `FilterExpressionAst`, `FilterAst`, `FilterBinaryAst`, `FilterPredicateAst`, `JoinAst`, `OrderAst`, `ParameterValueAst`, `QueryAst`, `SelectAst`, `SelectFieldAst`, `SubqueryValueAst`, `AggregateExpressionSupport`, `SqlLikeBinder`, `DefaultSqlLikeQueryCacheSupport`, `SqlLikeKeysetSupport`, `SqlLikeExecutionSupport`, `SqlLikeExplainSupport`, `BooleanExpressionNormalizer`, `SqlExpressionEvaluator`, `SqlLikeLintSupport`, `BoundParameterValue`, `SqlLikeParameterSupport`, `SqlLikeJoinResolution`, `SqlLikeValidator` | `Internalize`                            | `1.x`                                                        | This is the largest concentration of public internals and is outside the stable `1.x` contract gate.                                                       |
| Chart mapping and validation helpers                           | `ChartMapper`, `ChartResultMapper`, `ChartValidation`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | `Internalize`                            | `1.x`                                                        | Public chart contracts stay; mapper/validator mechanics do not need to.                                                                                    |
| Domain/intermediate row models                                 | `QueryRow`, `QueryField`, `RawQueryRow`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | `Internalize`                            | `1.x`                                                        | These are query-pipeline row containers rather than public result contracts. Benchmark notes may still mention them as internals.                          |
| Support helpers and utilities                                  | `ComputedFieldSupport`, `TabularSchemaSupport`, `QueryTelemetrySupport`, `CollectionUtil`, `GroupKeyUtil`, `ObjectUtil`, `QueryFieldLookupUtil`, `ReflectionUtil`, `SchemaIndexUtil`, `StringUtil`, `TimeBucketUtil`                                                                                                                                                                                                                                                                                                                                                 | `Internalize`                            | `1.x`                                                        | These are support glue/utilities with no documented product identity.                                                                                      |
| Advanced public wrappers/tooling outside this package decision | `ReportDefinition`, `ChartQueryPreset`, `ChartQueryPresets`, `StatsViewPreset`, `StatsViewPresets`, `StatsTable`, `StatsTablePayload`, computed-field contracts, telemetry contracts, snapshot helpers, metamodel helpers, regression fixtures, `TimeBucketPreset`                                                                                                                                                                                                                                                                                                   | `Keep public advanced for now`           | handled in `WP8.3` or later                                  | These may still be simplified, but they are wrapper/tooling decisions rather than public implementation leaks.                                             |

## `1.x` Implementation Rules

- Start by shrinking `FilterExecutionPlanCacheStore` to policy and observability
  methods only, so execution-plan types can be internalized cleanly.
- Internalize implementation classes before moving packages around. Package
  relocation without deletion is `2.x` work unless it also removes code.
- Preserve the documented runtime cache controls in `1.x`:
  enable/disable, stats enable/disable, max entries, max weight,
  expire-after-write, clear/reset stats, hits/misses/size/evictions, and
  snapshot.
- When `1.x` advanced members are narrowed, record the change in
  `MIGRATION.md` and release notes.

## Result

- `1.x` internalization is approved for builder/filter internals, execution-plan
  types, SQL-like parser/AST helpers, chart mapping helpers, intermediate row
  models, and support/util packages.
- `1.x` keeps the public runtime cache handles and user-facing diagnostics, but
  treats package cleanup for `SqlLikeQueryCache` and `SqlLikeErrorCodes` as
  `2.x` work unless a code-deleting alternative appears.
