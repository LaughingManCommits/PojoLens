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
| SQL-like querying | `Core query engine` | Primary public query authoring path | `PojoLensSql`, `SqlLikeQuery`, `SqlLikeTemplate`, `SqlParams`, `SqlLikeCursor`, `PageResult`, `JoinBindings` | `Stable` core | `README.md`, `docs/sql-like.md` |
| Plain-English querying | `Core query engine` | Guided text authoring path for non-SQL users | `PojoLensNatural`, `NaturalQuery`, `NaturalTemplate`, `NaturalBoundQuery` | `Stable` core | `README.md`, `docs/entry-points.md`, `docs/natural.md` |
| Typed DSL querying | `Core query engine` | Java-owned authoring path for refactor-friendly query composition in code | `TypedQuery`, `TypedField`, `TypedPredicate`, `TypedWindowOrder` | `Stable` core | `README.md`, `docs/entry-points.md`, `docs/typed.md`, `docs/metamodel.md` |
| Fluent engine DSL | `Core query engine` | Internal execution-planning and parity infrastructure, not the public product story | `laughing.man.commits.internal.FluentEngine`, `laughing.man.commits.internal.builder.*` | Internal | `docs/internal-fluent-engine.md` |
| Dataset composition | `Workflow helper` | Reusable multi-source execution wiring | `DatasetBundle` | `Stable` support contract | `docs/usecases.md`, `docs/reports.md` |
| Chart output mapping | `Workflow helper` | Chart-ready output contracts built on query results | `PojoLensChart`, `ChartSpec`, `ChartData`, `ChartDataset`, `ChartType` | `Stable` helper contracts | `docs/charts.md` |
| Tree row shaping | `Workflow helper` | Deterministic subtree selection from flat parent-ID POJO lists before normal query execution | `PojoLensTree`, `TreeTraversalBuilder`, `TreeEntry` | `Stable` helper contracts | `docs/tree.md`, `docs/entry-points.md` |
| Facet option helpers | `Workflow helper` | Distinct-value snapshot summaries for filter bars and navigation facets | `FacetPresets`, `FacetQuery`, `FacetOption` | `Advanced` helper surface | `docs/facets.md` |
| Reusable workflow wrappers | `Workflow helper` | Convenience wrappers for reusable row/chart/table flows | `ReportDefinition`, `ChartQueryPreset`, `ChartQueryPresets`, `StatsViewPreset`, `StatsViewPresets`, `StatsTable` | `Advanced` convenience surface | `docs/reusable-wrappers.md`, `docs/reports.md`, `docs/charts.md`, `docs/stats-presets.md` |
| Runtime-scoped execution and policy | `Integration` | Scoped runtime configuration, natural-query vocabulary, and DI-friendly execution | `PojoLensRuntime`, `PojoLensRuntimePreset`, `NaturalVocabulary` | `Stable` runtime surface; policy tuning is partly `Advanced` | `README.md`, `docs/caching.md`, `docs/telemetry.md` |
| Spring Boot support | `Integration` | Optional framework wiring for Boot applications | `pojo-lens-spring-boot-autoconfigure`, `pojo-lens-spring-boot-starter` | Optional integration surface | `README.md`, `docs/modules.md` |
| Spring/JDBC row bridge | `Integration` | Optional Spring helper for materializing query-ready POJO rows from `JdbcTemplate` or `ResultSet` boundaries | `PojoLensJdbc` | `Advanced` integration helper | `docs/jdbc.md`, `docs/modules.md` |
| Query diagnostics and policy controls | `Tooling` | Operational visibility and tuning around the core engine | `explain`, telemetry hooks, lint mode, cache stats and controls | Mixed: `explain` is core-adjacent, policy controls are largely `Advanced` | `docs/sql-like.md`, `docs/telemetry.md`, `docs/caching.md` |
| Pushdown readiness and bridge metadata | `Tooling` | Advisory host-adapter classification for simple SQL-like stages plus materialized-row completion; no database execution or SQL rendering owned by PojoLens | `SqlLikeQuery.pushdownPreview()`, `pushdownRequest()`, `filterWithPushdown(...)`, `SqlLikePushdownPreview`, `SqlLikePushdownMode`, `SqlLikePushdownAdapter`, `SqlLikePushdownResult`, `SqlLikeResultSetAdapter` | `Stable` SQL-like planning and bridge surface | `docs/sql-like.md`, `docs/telemetry.md` |
| Schema metadata | `Workflow helper` | Deterministic table/chart column metadata for renderers | `schema()`, `TabularSchema`, `TabularColumn` | `Stable` support contract | `docs/tabular-schema.md`, `docs/reports.md`, `docs/stats-presets.md` |
| Regression and snapshot support | `Tooling` | Regression safety and parity tooling | `QueryRegressionFixture`, `QuerySnapshotFixture`, `FluentSqlLikeParity`, `SnapshotComparison` | `Advanced` tooling surface | `docs/regression-fixtures.md`, `docs/snapshot-comparison.md` |
| Build-time generation and catalog validation | `Tooling` | Compiler-time typed field generation, deterministic fallback codegen, and CI validation for typed authoring plus saved-report/query catalogs | `GeneratePojoLensTypedFields`, `PojoLensTypedFieldsProcessor`, `FieldMetamodel`, `FieldMetamodelGenerator`, `MetamodelBatchGenerator`, `MetamodelGenerationRequest`, `SavedReportCatalogValidator`, `SavedReportValidationResult`, `SavedReportCatalogValidationResult`, `ToolingValidationIssue` | `Advanced` tooling surface | `docs/build-tooling.md`, `docs/metamodel.md` |
| Benchmarking and thresholds | `Tooling` | Performance validation, not runtime product surface | `pojo-lens-benchmarks`, threshold/parity checkers | Tooling only | `docs/benchmarking.md`, `CONTRIBUTING.md` |
| File-boundary loading | `Compatibility adapter` | Boundary-only loading from CSV/TSV/JSON/JSONL files into typed rows, with optional runtime-owned defaults, explicit row-mapping policy, and load-scoped diagnostics | `PojoLensFiles`, `PojoLensCsv`, `CsvOptions`, `CsvCoercionPolicy`, `CsvLoadResult`, `CsvLoadReport`, `CsvLoadException`, `JsonOptions`, `JsonLoadResult`, `JsonLoadReport`, `JsonLoadException`, `CsvRuntime`, `FileLoadRuntime`, `PojoLensRuntime` | `Advanced` adapter surface | `README.md`, `docs/entry-points.md`, `docs/files.md`, `docs/csv.md` |
| Boundary adapters | `Compatibility adapter` | Explicit conversion for boundary inputs only | `JoinBindings.from(Map)` | `Advanced` adapter surface | `docs/sql-like.md`, `MIGRATION.md` |

## Current Classification Calls

- `PojoLens` is no longer part of the public surface.
- `PojoLensRuntime` is not a third query style. It is the scoped runtime and
  configuration model around the same engine, including runtime-owned natural
  vocabulary for guided text queries.
- `TypedQuery` is the Java-owned authoring mode. `docs/typed.md` teaches query
  composition, while `docs/metamodel.md` stays focused on generating the typed
  field constants used by that surface.
- `PojoLensFiles` is the single file-boundary loader story. It loads typed rows
  into memory before the existing engine runs; it is not a second query engine
  or a generic table platform. `PojoLensCsv` remains the stable CSV-only
  convenience entry point over the same support, and `runtime.files()` keeps
  the same adapter story while moving defaults onto `PojoLensRuntime`.
  `CsvCoercionPolicy` keeps delimited-text variation handling explicit instead
  of inferred, while `JsonOptions` keeps JSON/JSONL row-shape rules equally
  explicit. Load reports and exceptions stay at the file boundary instead of
  expanding query `explain(...)` into file-ingestion semantics. Excel remains
  a deliberate non-goal because workbook/document semantics are outside this
  bounded row-loader surface.
- `PojoLensChart` and chart/table/report wrappers are workflow helpers layered
  on top of query execution, not separate product pillars.
- Chart output, table payloads, and schema metadata form one output-helper
  story layered over query execution; they should be documented as helper
  routes, not as separate query-authoring modes.
- `PojoLensTree` is a workflow helper for flat ID/parent-ID row shaping before
  query execution. It returns `List<T>` or `TreeEntry<T>` metadata and does not
  add parser syntax, graph algorithms, persistence behavior, or a second query
  engine.
- `FacetPresets` is a lightweight workflow helper for UI/filter-bar option
  generation over an already materialized snapshot. It does not add a second
  query language, aggregation engine, or persistence abstraction.
- `ReportDefinition` is the general reusable execution wrapper.
  SQL-like and natural report definitions are the public path.
  `ChartQueryPreset` and `StatsViewPreset` are specialized convenience wrappers
  built for chart-first and table-first flows.
- Raw map-shaped join execution is no longer public surface. Convert once with
  `JoinBindings.from(map)` if a boundary already provides that shape.
- Pushdown readiness is planning metadata for host-owned adapters. It may help
  an application run simple filters/order/page stages before materializing rows,
  but PojoLens still does not own database execution, ORM integration, SQL
  rendering, or adapter authorization.
- `PojoLensJdbc` is a thin Spring integration bridge over `JdbcTemplate` and
  `SqlLikeResultSetAdapter`. It helps materialize rows at a host-owned SQL
  boundary; it does not make PojoLens a database access layer or SQL renderer.
- Telemetry, cache policy controls, lint mode, regression fixtures, build-time
  metamodel/query tooling, and benchmarking are useful public features, but
  they belong to advanced/tooling surface rather than the first-read product
  story.


