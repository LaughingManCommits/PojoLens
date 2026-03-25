# Product Surface Map

This document is the canonical feature-family classification for PojoLens.

Use it to keep README, examples, API guidance, and roadmap decisions aligned on
what is core, what is convenience, and what is advanced/tooling surface.

## Canonical Product Story

- PojoLens is an in-memory POJO query engine.
- Its first-class surface is fluent and SQL-like querying over existing Java
  objects.
- Chart/table/report helpers, runtime wiring, and tooling all layer on top of
  that same execution engine.

## Family Definitions

- `Core query engine`:
  query definition and execution APIs that make up the main product value.
- `Workflow helper`:
  reusable wrappers or output helpers built on top of the core engine.
- `Integration`:
  framework or runtime wiring around the engine.
- `Tooling`:
  diagnostics, regression, generation, or benchmark support.
- `Compatibility`:
  public facade APIs preserved for migration or mixed-style convenience.
- `Advanced`:
  public APIs that are useful, but are not the default first path for new
  adoption.

## Feature Family Matrix

| Surface | Family | Positioning | Primary contracts | 1.x classification | Primary docs |
| --- | --- | --- | --- | --- | --- |
| Fluent querying | `Core query engine` | First-class query authoring path | `PojoLensCore`, `QueryBuilder`, `Filter` | `Stable` core | `README.md`, `docs/usecases.md` |
| SQL-like querying | `Core query engine` | First-class dynamic query authoring path | `PojoLensSql`, `SqlLikeQuery`, `SqlLikeTemplate`, `SqlParams`, `SqlLikeCursor`, `JoinBindings` | `Stable` core | `README.md`, `docs/sql-like.md` |
| Compatibility facade | `Compatibility` | Migration-friendly facade over the same engine | `PojoLens` | `Stable` compatibility surface | `README.md`, `docs/modules.md` |
| Dataset composition | `Workflow helper` | Reusable multi-source execution wiring | `DatasetBundle` | `Stable` support contract | `docs/usecases.md`, `docs/reports.md` |
| Chart output mapping | `Workflow helper` | Chart-ready output contracts built on query results | `PojoLensChart`, `ChartSpec`, `ChartData`, `ChartDataset`, `ChartType` | `Stable` helper contracts | `docs/charts.md` |
| Reusable workflow wrappers | `Workflow helper` | Convenience wrappers for reusable row/chart/table flows | `ReportDefinition`, `ChartQueryPreset`, `ChartQueryPresets`, `StatsViewPreset`, `StatsViewPresets`, `StatsTable` | `Advanced` convenience surface | `docs/reports.md`, `docs/charts.md`, `docs/stats-presets.md` |
| Runtime-scoped execution and policy | `Integration` | Scoped runtime configuration and DI-friendly execution | `PojoLensRuntime`, `PojoLensRuntimePreset` | `Stable` runtime surface; policy tuning is partly `Advanced` | `README.md`, `docs/caching.md`, `docs/telemetry.md` |
| Spring Boot support | `Integration` | Optional framework wiring for Boot applications | `pojo-lens-spring-boot-autoconfigure`, `pojo-lens-spring-boot-starter` | Optional integration surface | `README.md`, `docs/modules.md` |
| Query diagnostics and policy controls | `Tooling` | Operational visibility and tuning around the core engine | `explain`, telemetry hooks, lint mode, cache stats and controls | Mixed: `explain` is core-adjacent, policy controls are largely `Advanced` | `docs/sql-like.md`, `docs/telemetry.md`, `docs/caching.md` |
| Schema metadata | `Workflow helper` | Deterministic table/chart column metadata for renderers | `schema()`, `TabularSchema`, `TabularColumn` | `Stable` support contract | `docs/tabular-schema.md`, `docs/reports.md`, `docs/stats-presets.md` |
| Regression and snapshot support | `Tooling` | Regression safety and parity tooling | `QueryRegressionFixture`, `QuerySnapshotFixture`, `FluentSqlLikeParity`, snapshot comparison helpers | `Advanced` tooling surface | `docs/regression-fixtures.md`, `docs/snapshot-comparison.md` |
| Field metamodel generation | `Tooling` | Typed field constants for query authoring support | `FieldMetamodel`, `FieldMetamodelGenerator` | `Advanced` tooling surface | `docs/metamodel.md` |
| Benchmarking and thresholds | `Tooling` | Performance validation, not runtime product surface | `pojo-lens-benchmarks`, threshold/parity checkers | Tooling only | `docs/benchmarking.md`, `CONTRIBUTING.md` |

## Current Classification Calls

- `PojoLens` is not a separate engine. It is the compatibility facade over the
  same fluent/SQL-like runtime.
- `PojoLensRuntime` is not a third query style. It is the scoped runtime and
  configuration model around the same engine.
- `PojoLensChart` and chart/table/report wrappers are workflow helpers layered
  on top of query execution, not separate product pillars.
- `ReportDefinition` is the general reusable execution wrapper.
  `ChartQueryPreset` and `StatsViewPreset` are specialized convenience wrappers
  built for chart-first and table-first flows.
- Telemetry, cache policy controls, lint mode, regression fixtures, metamodel
  generation, and benchmarking are useful public features, but they belong to
  advanced/tooling surface rather than the first-read product story.

## Follow-On Work

This classification is the input to the next roadmap steps:

- entry-point realignment
- reusable wrapper decision rules
- advanced-feature containment in README/docs
- later consolidation or deprecation review where overlap remains low-value
