# Product Surface Map

This document is the canonical feature-family classification for PojoLens.

Use it to keep README, examples, API guidance, and surface decisions aligned on
what is core, what is convenience, and what is advanced/tooling surface.

## Canonical Product Story

- PojoLens is an in-memory POJO query engine.
- Its first-class public query surface is SQL-like text over existing Java
  objects, with controlled plain-English as the guided non-SQL alternative.
- New query capability should land in the shared execution engine, then be
  exposed through SQL-like first and natural where the controlled grammar can
  express it clearly.
- Boundary adapters may load external representations into typed rows, but
  they do not change the POJO-first engine story.
- Chart/table/report helpers, runtime wiring, and tooling layer on top of that
  same engine.
- Compatibility-only facade overlap is not part of the public surface.

## Family Definitions

- `Core query engine`:
  query definition and execution APIs that make up the main product value.
- `Workflow helper`:
  reusable wrappers or output helpers built on top of the core engine.
- `Integration`:
  framework or runtime wiring around the engine.
- `Tooling`:
  diagnostics, regression, generation, or benchmark support.
- `Compatibility adapter`:
  explicit bridge helpers used only at boundaries, not a second product story.
- `Advanced`:
  public APIs that are useful, but are not the default first path for new
  adoption.

## Feature Family Matrix

| Surface | Family | Positioning | Primary contracts | Current classification | Primary docs |
| --- | --- | --- | --- | --- | --- |
| SQL-like querying | `Core query engine` | Primary public query authoring path | `PojoLensSql`, `SqlLikeQuery`, `SqlLikeTemplate`, `SqlParams`, `SqlLikeCursor`, `JoinBindings` | `Stable` core | `README.md`, `docs/sql-like.md` |
| Plain-English querying | `Core query engine` | Guided text authoring path for non-SQL users | `PojoLensNatural`, `NaturalQuery`, `NaturalTemplate`, `NaturalBoundQuery` | `Stable` core | `README.md`, `docs/entry-points.md`, `docs/natural.md` |
| Fluent engine DSL | `Core query engine` | Internal execution-planning and parity infrastructure, not the primary public story | `PojoLensCore`, `QueryBuilder`, `QueryRule`, `FluentQueryDefinition`, `Filter` | Pending internal reset | `docs/internal-fluent-engine.md` |
| Dataset composition | `Workflow helper` | Reusable multi-source execution wiring | `DatasetBundle` | `Stable` support contract | `docs/usecases.md`, `docs/reports.md` |
| Chart output mapping | `Workflow helper` | Chart-ready output contracts built on query results | `PojoLensChart`, `ChartSpec`, `ChartData`, `ChartDataset`, `ChartType` | `Stable` helper contracts | `docs/charts.md` |
| Tree row shaping | `Workflow helper` | Deterministic subtree selection from flat parent-ID POJO lists before normal query execution | `PojoLensTree`, `TreeTraversalBuilder`, `TreeEntry` | `Stable` helper contracts | `docs/tree.md`, `docs/entry-points.md` |
| Reusable workflow wrappers | `Workflow helper` | Convenience wrappers for reusable row/chart/table flows | `ReportDefinition`, `ChartQueryPreset`, `ChartQueryPresets`, `StatsViewPreset`, `StatsViewPresets`, `StatsTable` | `Advanced` convenience surface | `docs/reusable-wrappers.md`, `docs/reports.md`, `docs/charts.md`, `docs/stats-presets.md` |
| Runtime-scoped execution and policy | `Integration` | Scoped runtime configuration, natural-query vocabulary, and DI-friendly execution | `PojoLensRuntime`, `PojoLensRuntimePreset`, `NaturalVocabulary` | `Stable` runtime surface; policy tuning is partly `Advanced` | `README.md`, `docs/caching.md`, `docs/telemetry.md` |
| Spring Boot support | `Integration` | Optional framework wiring for Boot applications | `pojo-lens-spring-boot-autoconfigure`, `pojo-lens-spring-boot-starter` | Optional integration surface | `README.md`, `docs/modules.md` |
| Query diagnostics and policy controls | `Tooling` | Operational visibility and tuning around the core engine | `explain`, telemetry hooks, lint mode, cache stats and controls | Mixed: `explain` is core-adjacent, policy controls are largely `Advanced` | `docs/sql-like.md`, `docs/telemetry.md`, `docs/caching.md` |
| Schema metadata | `Workflow helper` | Deterministic table/chart column metadata for renderers | `schema()`, `TabularSchema`, `TabularColumn` | `Stable` support contract | `docs/tabular-schema.md`, `docs/reports.md`, `docs/stats-presets.md` |
| Regression and snapshot support | `Tooling` | Regression safety and parity tooling | `QueryRegressionFixture`, `QuerySnapshotFixture`, `FluentSqlLikeParity`, `SnapshotComparison` | `Advanced` tooling surface | `docs/regression-fixtures.md`, `docs/snapshot-comparison.md` |
| Field metamodel generation | `Tooling` | Typed field constants for query authoring support | `FieldMetamodel`, `FieldMetamodelGenerator` | `Advanced` tooling surface | `docs/metamodel.md` |
| Benchmarking and thresholds | `Tooling` | Performance validation, not runtime product surface | `pojo-lens-benchmarks`, threshold/parity checkers | Tooling only | `docs/benchmarking.md`, `CONTRIBUTING.md` |
| CSV onboarding | `Compatibility adapter` | Boundary-only loading from UTF-8 CSV into typed rows, with optional runtime-owned defaults, explicit coercion policy, and load-scoped diagnostics | `PojoLensCsv`, `CsvOptions`, `CsvCoercionPolicy`, `CsvLoadResult`, `CsvLoadReport`, `CsvLoadException`, `CsvRuntime`, `PojoLensRuntime` | `Advanced` adapter surface | `README.md`, `docs/entry-points.md`, `docs/csv.md` |
| Boundary adapters | `Compatibility adapter` | Explicit conversion for boundary inputs only | `JoinBindings.from(Map)` | `Advanced` adapter surface | `docs/sql-like.md`, `MIGRATION.md` |

## Current Classification Calls

- `PojoLens` is no longer part of the public surface.
- `PojoLensRuntime` is not a third query style. It is the scoped runtime and
  configuration model around the same engine, including runtime-owned natural
  vocabulary for guided text queries.
- `PojoLensCsv` is a boundary-only onboarding helper. It loads typed rows into
  memory before the existing engine runs; it is not a second query engine or
  a generic table platform. `runtime.csv()` keeps the same adapter story while
  moving defaults onto `PojoLensRuntime`, and `CsvCoercionPolicy` keeps CSV
  variation handling explicit instead of inferred. `CsvLoadReport` and
  `CsvLoadException` keep troubleshooting at the load boundary instead of
  expanding query `explain(...)` into file-ingestion semantics.
- `PojoLensChart` and chart/table/report wrappers are workflow helpers layered
  on top of query execution, not separate product pillars.
- `PojoLensTree` is a workflow helper for flat ID/parent-ID row shaping before
  query execution. It returns `List<T>` or `TreeEntry<T>` metadata and does not
  add parser syntax, graph algorithms, persistence behavior, or a second query
  engine.
- `ReportDefinition` is the general reusable execution wrapper.
  SQL-like and natural report definitions are the public first-read path;
  fluent-backed definitions are pending internalization with the builder DSL.
  `ChartQueryPreset` and `StatsViewPreset` are specialized convenience wrappers
  built for chart-first and table-first flows.
- Raw map-shaped join execution is no longer public surface. Convert once with
  `JoinBindings.from(map)` if a boundary already provides that shape.
- Telemetry, cache policy controls, lint mode, regression fixtures, metamodel
  generation, and benchmarking are useful public features, but they belong to
  advanced/tooling surface rather than the first-read product story.


