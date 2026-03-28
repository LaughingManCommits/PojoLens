# Entropy Internalization Decision

This document is the `WP8.2` decision artifact for the `Entropy Reduction`
roadmap.
It turns the `WP8.1` audit into release-scoped calls about what can narrow
before the first public release, what must stay public once the first
`release-*` tag exists, and what should wait for a later compatibility reset
only if code deletion is not practical now.

## Decision Summary

- Pre-first-release cleanup may remove compatibility-only overlap and implementation
  leaks when the result is a narrower, better-documented surface.
- Prefer internalization or public-surface subtraction over package reshuffles
  that add aliases and more code.
- Public runtime cache handles stay public pre-first-release, but surrounding
  execution-plan internals should keep moving off the public path.

## Compatibility Inputs

- `pojo-lens/pom.xml` and CI now treat the intended dated-release stable
  surface as the compatibility baseline, with binary checks starting from the
  first public `release-*` tag.
- `StablePublicApiContractTest` enforces the stable entry points and core query
  contracts rather than the full public type count.
- [public-api-stability.md](public-api-stability.md) allows `Advanced` APIs to
  evolve faster before the first public release when maintainability or
  performance requires it.
- [caching.md](caching.md) documents `runtime.sqlLikeCache()` and
  `runtime.statsPlanCache()` as the intentional public policy-tuning handles.
- `PublicSurfaceContractTest` still references `FilterQueryBuilder`, but that
  is a repo-local guardrail, not a user contract. It should be refocused on
  `QueryBuilder` and `Filter` behavior when implementation types narrow.

## Package Policy

- Keep public pre-first-release packages centered on the documented product story:
  entry points, core query contracts, chart contracts, runtime integration,
  diagnostics, and deliberately public advanced wrappers/tooling.
- Move implementation-heavy helpers behind package-private scope or
  `*.internal.*` packages when they are not intentional user contracts.
- Do not add new intentionally public contracts under `*.internal.*`.
- Public cache handles may remain public, but their public members should be
  policy/observability oriented rather than parser or execution-plan plumbing.

## Disposition Table

| Area | Types | Decision | Target | Compatibility notes |
| --- | --- | --- | --- | --- |
| Stable entry/core surface | `PojoLensCore`, `PojoLensSql`, `PojoLensChart`, `PojoLensRuntime`, `PojoLensRuntimePreset`, `DatasetBundle`, `QueryBuilder`, `Filter`, `SqlLikeQuery`, `SqlLikeBoundQuery`, `SqlLikeTemplate`, `SqlParams`, `SqlLikeCursor`, `JoinBindings`, shared enums, chart value contracts | `Keep public stable` | first public `release-*` tag onward | This is the intended dated-release stable set enforced by docs, contract tests, and `japicmp` once the first public `release-*` tag exists. |
| Builder/filter implementation helpers | `EngineDefaults`, `FieldSelectors`, `FilterQueryBuilder`, `FilterCore`, `FilterImpl`, `FastStatsQuerySupport` | `Internalize` | pre-first-release | These are engine mechanics, not intended product contracts. Update tests to stop naming implementation types directly. |
| Filter execution-plan internals | `FilterExecutionPlan`, `FilterExecutionPlanCache`, `FilterExecutionPlanCacheKey` | `Internalize` | pre-first-release | `FilterExecutionPlanCache` is already removed. `FilterExecutionPlan` and `FilterExecutionPlanCacheKey` should keep moving out of public signatures. |
| Stats-plan cache handle | `FilterExecutionPlanCacheStore` | `Keep public advanced`, `narrow members` | pre-first-release for member narrowing | Keep tuning and observability methods public. The public default-store facade is removed; remaining execution-plan member leaks should keep shrinking. |
| SQL-like diagnostics surface | `SqlLikeLintCodes`, `SqlLikeLintWarning`, `SqlLikeParseException`, `SqlLikeErrorCodes` | `Keep public advanced` | later compatibility reset only if it deletes code | Error codes and diagnostics are user-facing and already documented. `SqlLikeErrorCodes` is a packaging anomaly, not an internalization target now. |
| SQL-like cache handle | `SqlLikeQueryCache` | `Keep public advanced` | later compatibility reset only if it deletes code | `PojoLensRuntime.sqlLikeCache()` and current docs/tests depend on this type. Adding a public alias now would increase code instead of reducing it. |
| SQL-like parser, AST, and execution helpers | `SqlLikeParser`, `SqlLikeErrors`, `FilterExpressionAst`, `FilterAst`, `FilterBinaryAst`, `FilterPredicateAst`, `JoinAst`, `OrderAst`, `ParameterValueAst`, `QueryAst`, `SelectAst`, `SelectFieldAst`, `SubqueryValueAst`, `AggregateExpressionSupport`, `SqlLikeBinder`, `DefaultSqlLikeQueryCacheSupport`, `SqlLikeKeysetSupport`, `SqlLikeExecutionSupport`, `SqlLikeExplainSupport`, `BooleanExpressionNormalizer`, `SqlExpressionEvaluator`, `SqlLikeLintSupport`, `BoundParameterValue`, `SqlLikeParameterSupport`, `SqlLikeJoinResolution`, `SqlLikeValidator` | `Internalize` | pre-first-release | This is the largest concentration of public internals and is outside the intended dated-release contract gate. |
| Chart mapping and validation helpers | `ChartMapper`, `ChartResultMapper`, `ChartValidation` | `Internalize` | pre-first-release | Public chart contracts stay; mapper/validator mechanics do not need to. |
| Domain/intermediate row models | `QueryRow`, `QueryField`, `RawQueryRow` | `Internalize` | pre-first-release | These are query-pipeline row containers rather than public result contracts. Benchmark notes may still mention them as internals. |
| Support helpers and utilities | `ComputedFieldSupport`, `TabularSchemaSupport`, `QueryTelemetrySupport`, `CollectionUtil`, `GroupKeyUtil`, `ObjectUtil`, `QueryFieldLookupUtil`, `ReflectionUtil`, `SchemaIndexUtil`, `StringUtil`, `TimeBucketUtil` | `Internalize` | pre-first-release | These are support glue/utilities with no documented product identity. |
| Advanced public wrappers/tooling outside this package decision | `ReportDefinition`, `ChartQueryPreset`, `ChartQueryPresets`, `StatsViewPreset`, `StatsViewPresets`, `StatsTable`, `StatsTablePayload`, computed-field contracts, telemetry contracts, snapshot helpers, metamodel helpers, regression fixtures, `TimeBucketPreset` | `Keep public advanced for now` | handled in `WP8.3` or later | These are wrapper/tooling decisions rather than public implementation leaks. |

## Pre-First-Release Implementation Rules

- Start by shrinking `FilterExecutionPlanCacheStore` to policy and observability
  methods only, so execution-plan types can be internalized cleanly.
- Internalize implementation classes before moving packages around. Package
  relocation without deletion is later compatibility-reset work unless it also removes code.
- Preserve the documented runtime cache controls pre-first-release:
  enable/disable, stats enable/disable, max entries, max weight,
  expire-after-write, clear/reset stats, hits/misses/size/evictions, and
  snapshot.
- When advanced members are narrowed, record the change in `MIGRATION.md` and
  release notes.

## Result

- Pre-first-release internalization is approved for builder/filter internals,
  execution-plan types, SQL-like parser/AST helpers, chart mapping helpers,
  intermediate row models, and support/util packages.
- The public `FilterExecutionPlanCache` facade is removed and default direct
  entry points now rely on internal cache ownership.
- Public runtime cache handles and user-facing diagnostics remain, but package
  cleanup for `SqlLikeQueryCache` and `SqlLikeErrorCodes` is deferred unless a
  code-deleting alternative appears.

