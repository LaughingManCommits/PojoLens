# Changelog

All notable changes to this project will be documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions use date-based scheme `YYYY.MM.DD.HHmm`.

---

## [Unreleased]

### Added

- **SQL-like pushdown-readiness preview** (`STRAT-WP4` first slice) - added
  `SqlLikePushdownPreview`, `SqlLikePushdownMode`, and
  `SqlLikeQuery.pushdownPreview()` to classify query shapes as `FULL`, `SPLIT`,
  or `IN_MEMORY_ONLY` for host-owned adapters. The first-phase subset covers
  simple selected fields, comparison predicates with literals or parameters,
  ordering, limit, and offset. Joins, grouping, aggregation, windows,
  subqueries, `HAVING`, `QUALIFY`, computed selects, time buckets, and
  unsupported filter operators stay in-memory with stable fallback reason
  codes. `explain()` now includes `pushdownPreview`, and SQL-like BIND
  telemetry includes pushdown mode, pushable stages, in-memory stages, and
  fallback reasons. This is advisory planning metadata only; PojoLens still
  does not own database execution or SQL rendering.

- **SQL-like pushdown bridge** (`STRAT-WP4`) - added
  `SqlLikePushdownAdapter`, `SqlLikePushdownRequest`,
  `SqlLikePushdownResult`, `SqlLikeResultSetAdapter`, and
  `SqlLikeQuery.filterWithPushdown(...)` so host-owned adapters can materialize
  pushed first-phase rows and let PojoLens finish unsupported stages in memory.
  Added `PUSHDOWN` telemetry and JMH coverage for pure in-memory, pushed, and
  split completion paths.

- **Reflection hotspot guardrails** (`STRAT-WP5` first slice) - added
  `benchmarks/hotspot-thresholds.json` and
  `scripts/benchmark-suite-hotspot-reflection.args` for warmed forked JMH
  budgets on `HotspotMicroJmhBenchmark.reflectionToDomainRows` and
  `reflectionToClassList`. The narrow guardrail keeps the broader hotspot suite
  diagnostic-only while freezing the most stable reflection conversion paths
  into conservative threshold checks.

### Changed

- **Reflection materialization hot path** (`STRAT-WP5` first slice) -
  `ReflectionUtil` now reuses compiled direct-field read plans across
  equivalent selections and uses cached nested field-path writes during
  projection materialization. This trims repeated reflection setup work on
  warmed conversion paths while preserving existing query semantics.

- **Repeated join and window execution hot paths** (`STRAT-WP5`) - repeated
  computed-field join execution now reuses prepared fast join state for stable
  filter snapshots instead of rebuilding the fast join structure on every
  `.join()` call. Window execution now writes directly into the final output
  row buffers and uses cheaper partition-key shapes for common partition
  layouts, reducing warmed SQL-like window allocation overhead.

- **Batch/columnar evaluation outcome** (`STRAT-WP5`) - evaluated a broader
  batch/columnar execution mode for heavy report workloads and kept the engine
  row-oriented. Existing array-backed fast paths remain the chosen bounded
  acceleration strategy until a future workload demonstrates that a second
  execution model is justified.

- **Stable public typed DSL foundation** (`STRAT-WP3`) - added
  `TypedField<T,V>`, `TypedPredicate<T>`, and `TypedQuery<T>` in the `dsl`
  package for code-owned projection, filtering, ordering, offset, and limit
  composition. `TypedQuery` is immutable, lowers into the shared engine, supports
  explain/schema interop, and applies execution guards for row-scan,
  row-return, and duration limits. `FieldMetamodelGenerator.generateTyped(...)`
  emits generated `TypedField<T,V>` constants, including boxed primitive field
  types. Contract coverage locks public API shape, nested `AND`/`OR` predicate
  semantics, and generated typed-source compilation. Typed grouping,
  aggregation, joins, windows, and subqueries are deferred.

- **Cooperative query cancellation** (`STRAT-WP2` completion) — added
  `QueryCancellationToken` (@FunctionalInterface) to the `sqllike` package with
  `ofAtomic(AtomicBoolean)` and `ofThread(Thread)` static factories. Attach via
  `QueryExecutionGuard.Builder#cancellationToken(token)`. The library polls the
  token at execution start (eager paths) and between every row in lazy
  (stream/iterator) paths. When the token fires, a `QueryExecutionGuardException`
  is thrown with block code `GUARD_CANCELLED`. `QueryGuardOutcome#cancelled()`
  factory carries `rowsReturnedBeforeAbort` — the exact number of rows the caller
  already received before the abort, providing deterministic aborted-query
  metadata. `auditMetadata()` includes `rowsReturnedBeforeAbort` for telemetry
  and structured logging. `QueryExecutionGuard#hasPreExecutionLimits()` added to
  skip unnecessary plan-preview builds for cancel-only guards. Senior-review
  hardening closes bound eager `filter`/`chart` cancellation, TypedQuery empty
  input cancellation, stable public API contract coverage, and public docs
  alignment.

- **Production query governance and audit** (`STRAT-WP2`) — added
  `QueryExecutionGuard`, `QueryGuardOutcome`, `QueryComplexitySummary`, and
  `QueryExecutionGuardException` to the `sqllike` package. `QueryExecutionGuard`
  enforces bounded execution via pre-execution checks (max rows scanned, max
  complexity score) and post-execution checks (max rows returned, max duration).
  `QueryComplexitySummary` derives an additive score from the parsed query shape
  (1/filter, 3/join, +2 grouping, +2 aggregation, +4 windows, +3 subqueries).
  `QueryGuardOutcome` carries machine-readable block codes, human-readable reasons,
  and structured `auditMetadata()` for telemetry and logging. Known block codes:
  `GUARD_ROWS_SCANNED_EXCEEDED`, `GUARD_COMPLEXITY_EXCEEDED`,
  `GUARD_ROWS_RETURNED_EXCEEDED`, `GUARD_DURATION_EXCEEDED`, `GUARD_CANCELLED`.
  Guard wired into `SqlLikeQuery.executionGuard(guard)` and
  `NaturalQuery.executionGuard(guard)`. `QueryTelemetryStage.GUARD_REJECTED`
  emitted via `QueryTelemetryListener` on block. Security boundary documented:
  exposure control and execution governance are in-scope; auth/RBAC/tenant policy
  remain host-application responsibilities. Contract coverage added to
  `StablePublicApiContractTest`.

- **Stable embedded reporting contract** (`STRAT-WP1`) — added `SavedReport`
  and `SavedReportKind` to the `report` package. `SavedReport` is a versioned,
  serialization-friendly contract carrying query text, default parameters,
  optional chart spec, and optional schema. Supports SQL-like and natural
  query kinds. Provides `planPreview()` and `diagnostics()` for data-free
  review, and `toQuery()` / `toNaturalQuery()` / `toDefinition(Class<T>)` for
  replay. Added `TabularColumn.typeName()` returning the column's Java simple
  type name for JSON-friendly UI-builder metadata. Contract coverage added to
  `StablePublicApiContractTest`.

- **Better error suggestions** (`QOL-WP5`) - extracted `NameSuggestions` helper
  (Levenshtein ≤ 2 + prefix match, up to 3 candidates, case-normalised) into
  `laughing.man.commits.internal`. Wired deterministic "Did you mean" suggestions
  into SQL-like unknown-field errors (WHERE/SELECT/ORDER BY/QUALIFY/HAVING
  aggregate/JOIN child/JOIN source/JOIN flexible resolve/subquery source),
  SQL-like and template unknown parameter errors, and natural-query unknown
  field term errors. Diagnostics filter suggestion candidates through the
  active exposure policy, so blocked fields and sources are not leaked in
  suggestion text or allowed-field/source lists.

- **Page result helper** (`QOL-WP4`) - added `PageResult<T>` in the `sqllike`
  package with `rows()`, `hasMore()`, and `nextCursor()`. New
  `SqlLikeQuery.filterPage(List, Class)`, `filterPage(DatasetBundle, Class)`,
  and `filterPage(List, JoinBindings, Class)` entry points execute with
  `limit + 1` lookahead, trim the extra row, and build a keyset cursor from the
  last visible row's `ORDER BY` field values. Requires a static `LIMIT` clause
  and at least one `ORDER BY` field. Cursor is built via
  `ReflectionUtil.DirectFieldReadPlan` so it works with any field visibility.
  Five new error codes: `EQ-SQL-PAG-001` (missing `ORDER BY`),
  `EQ-SQL-PAG-002` (missing static `LIMIT`), `EQ-SQL-PAG-003` (null or
  unreadable `ORDER BY` field value), `EQ-SQL-PAG-004` (non-positive page
  size), and `EQ-SQL-PAG-005` (`OFFSET` with cursor-backed page result).
- **SQL-like plan preview** (`QOL-WP3`) - added `SqlLikePlanPreview` and six
  companion types (`PlanPreviewField`, `PlanPreviewFilter`, `PlanPreviewJoin`,
  `PlanPreviewOrder`, `PlanPreviewPaging`, `PlanPreviewPredicate`) in the
  `sqllike` package. New
  `SqlLikeQuery.planPreview()` entry point returns a deterministic structural
  description of a query's execution shape — selected fields with aliases,
  metrics, time buckets, and window function details; WHERE/HAVING/QUALIFY
  predicates with operator and value-kind; JOIN clauses; ORDER BY fields; paging
  config; and required parameters — all without executing against rows or
  requiring a source class. Does not include cost estimates or row counts.
- **Grouped plan preview predicates** (`QOL-WP3` hardening) - added
  `PlanPreviewPredicate` plus `filterExpression()`, `havingExpression()`, and
  `qualifyExpression()` so preview tooling can retain `AND`/`OR` grouping.
  `PlanPreviewFilter.subqueryPreview()` now exposes nested subquery shape, and
  repeated literal predicates are preserved in preview filter lists.
- **Query diagnostics API** (`QOL-WP1`) - added `QueryDiagnostics` and
  `QueryDiagnosticsError` public types in the `sqllike` package. New
  `SqlLikeQuery.diagnostics()` (AST-level), `diagnostics(Class, Class)`, and
  `diagnostics(Class, Class, JoinBindings)` entry points let tooling inspect
  required params, referenced fields, output fields, join sources, subquery
  usage, lint warnings, and validation findings before execution.
  `NaturalQuery.diagnostics()` and `diagnostics(Class, Class)` delegate
  through the equivalent SQL-like representation.
- **Query diagnostics hardening** - completed WP1 review fixes so diagnostics
  report nested subquery fields/sources, join child fields, multiple unknown
  `WHERE` field findings, runtime natural-vocabulary resolution, and stable
  public API/docs coverage.
- **Query exposure policy** (`QOL-WP2`) - added public `QueryExposurePolicy`
  allowlists for query fields and named sources. SQL-like and natural queries
  can attach a policy directly or inherit one from `PojoLensRuntime`, and
  blocked field/source references now appear in diagnostics and fail before
  execution.

### Changed

- **CI runtime** - added Java 25 to the CI test matrix, moved artifact uploads
  to `actions/upload-artifact@v6`, and kept published bytecode targeting Java
  17.
- **Benchmark docs** - replaced versioned benchmark runner examples with
  dynamic `BENCHMARK_JAR` resolution and updated the documentation consistency
  gate to reject hardcoded benchmark jar versions.
- **Documentation backlog** - cleared the DOC-WP1 through DOC-WP10 follow-up
  list covering post-release stability wording, entry-point summaries,
  product-family names, cache docs, cross-links, and the docs landing page.
- **CI chart artifacts** - corrected the chart artifact job to run the
  `ChartLibraryInteropTest` selector and upload the module-local PNG outputs.
- **Backlog** - replaced the completed documentation TODO list with a
  SQL-like-first public-surface reset that keeps natural as guided text and
  demotes fluent to internal engine infrastructure.
- **Public query surface** - started `SURFACE-WP1` by making SQL-like the
  README and path-selection default while moving fluent wording out of the
  first-read public query story.
- **Natural query positioning** - completed `SURFACE-WP2` by documenting
  natural queries as controlled guided text after the SQL-like default, with
  explicit vocabulary, parameter, diagnostics, and authorization boundaries.
- **Fluent surface reset** - completed `SURFACE-WP3` by moving fluent builder
  guidance into maintainer-only docs, removing fluent report positioning from
  public wrapper docs, and marking fluent contracts as internal reset
  candidates instead of stable public API.
- **Public API compatibility reset** - completed `SURFACE-WP4` by narrowing
  binary compatibility checks to the SQL-like, natural, runtime, reports, CSV,
  tree, chart, cursor, schema, and join-binding surfaces while keeping fluent
  coverage as internal engine tests.
- **Fluent engine boundary** - completed `SURFACE-WP5` by moving mutable
  fluent planning types under `laughing.man.commits.internal.builder`,
  replacing public builder factories with the internal `FluentEngine` factory,
  and keeping benchmark coverage on the internal engine path.
- **Internal fluent docs** - expanded maintainer-only fluent engine guidance
  with method groups, execution lifecycle notes, and examples for filters,
  joins, aggregates, windows, subqueries, and prepared internal definitions.
- **Backlog** - replaced the completed SQL-like-first surface reset TODOs with
  scoped developer-experience work packages for diagnostics, exposure policy,
  dry-run previews, pagination helpers, and error suggestions.

---

## [2026.04.17.1834] - 2026-04-17

### Added

- **Natural language query surface** — `PojoLensNatural`, `NaturalQuery`, `NaturalBoundQuery`, and `PojoLensRuntime.natural()` provide a controlled plain-English query path (`show`, `where`, `sort by`, `group by`, `having`, `limit`, `bucket by`, `as chart`) that lowers deterministically into the shared engine. Includes runtime-scoped `NaturalVocabulary` for field aliases, reusable `NaturalTemplate` parameter schemas, and parity with fluent/SQL-like for aggregates, joins, window analytics, time buckets, and chart output.
- **Natural subquery and existence predicates** — natural grammar accepts bounded `is in query … end query`, `exists query … end query`, and `not exists query … end query` predicates with `and`/`or` connectors; lowers onto fluent/core subquery predicates.
- **CSV boundary adapter** — `PojoLensCsv` loads UTF-8 CSV into typed rows at the file boundary with strict header-based coercion, multiline quoted-record support, CRLF/BOM handling, `CsvCoercionPolicy` for blank/null/locale/date/enum rules, `CsvLoadReport`/`CsvLoadResult` diagnostics, and `runtime.csv().read(...)` / `readWithReport(...)` integration. Dynamic schema remains deferred (`CSV-WP6`).
- **Bounded window frames** — public `QueryWindowFrame` adds explicit `ROWS BETWEEN` frame control (`UNBOUNDED PRECEDING / CURRENT ROW / <n> PRECEDING / UNBOUNDED FOLLOWING`) for aggregate window functions alongside the existing running-window default.
- **Immutable fluent prepared wrapper** — `PojoLensCore.prepare(...)` returns an immutable `FluentQueryDefinition<T>` that rebuilds a fresh `QueryBuilder` per execution; exposes `rows(...)`, `schema()`, `explain()`, and promotes to `ReportDefinition<T>`.
- **Bounded subquery and existence predicates** — fluent `QueryBuilder` exposes `addInSubquery(...)`, `addExists(...)`, and `addNotExists(...)` with self-source and explicit-source execution-snapshot resolution. `QueryRule.inSubquery(...)`, `QueryRule.exists(...)`, and `QueryRule.notExists(...)` participate in `allOf(...)` / `anyOf(...)` groups. SQL-like `WHERE … IN (select …)` and `WHERE [NOT] EXISTS (select …)` bind onto fluent/core predicates; bounded OR/DNF subquery shapes lower onto grouped fluent predicates.
- **Aggregate ORDER BY diagnostics** — SQL-like queries now surface useful error messages distinguishing known-raw-field ORDER BY references from unknown-field typos and correctly scope HAVING wording.
- **Natural joined-schema vocabulary** — runtime `schema(...)` resolves registered vocabulary aliases against projection/source type at explain time; new overloads accept `DatasetBundle` or `JoinBindings` for join-source schema resolution.
- **Natural QUALIFY** — natural `qualify` accepts controlled inline window phrases, multiple partitions, and supported aggregate ROWS frames; `NaturalQuery` caches resolved delegates by execution shape.
- **Tree row shaping** — `PojoLensTree` selects deterministic subtrees from flat parent-ID POJO lists before normal fluent or SQL-like execution, with optional depth metadata through `TreeEntry`.

### Changed

- **CI workflow lint** - grouped repeated GitHub output and step-summary
  redirects in the CI workflow to satisfy ShellCheck `SC2129`.
- **CI runtime** - updated first-party `actions/checkout` and
  `actions/setup-java` workflow pins to their Node 24 major versions.
- **Release guardrails** - binary compatibility checks now include the stable
  `PojoLensTree`, `TreeTraversalBuilder`, and `TreeEntry` contracts.
- **Lint baseline** - refreshed `scripts/checkstyle-baseline.txt` from the
  current Checkstyle report so the baseline gate is synchronized again.
- **Benchmark thresholds** - recalibrated CSV load guardrails against CI
  timings for cold temp-file I/O sensitivity.
- **Positioning guidance** - README and benchmarking docs now state when to use
  PojoLens, when not to use it, and how to keep external performance
  comparisons reproducible and honest.
- **Input-safety guidance** - SQL-like and natural docs now call out parameter
  binding, allowed-field exposure, lint mode, strict typing, and authorization
  boundaries for user-authored query text.
- **`ReflectionUtil` cleanup** — renamed `isPlatformType` → `isUserDefinedType`; removed dead `extractQueryFields` and `buildSchema` methods; `DirectFieldReadPlan` now includes `final` fields via a dedicated `READABLE_FIELD_BY_NAME_CACHE`; `collectFieldGraph` uses an array-backed path stack instead of per-node list allocation; `buildMutableFieldByNameMap` uses `LinkedHashMap` for consistent field ordering.
- **`FastArrayQuerySupport` cleanup** — replaced `stream().findFirst()` with direct iterator in `canUseFastJoinPath`; `visitingComputedNames` allocated once per `compileJoinPlan` call instead of per field; dead 3-arg `orderRows` overload deleted; `andMatched`/`andFailed` renamed to `andAnyPassed`/`andAnyFailed` with clarifying comment.

---

## [2026.03.28.1919] — 2026-03-28

Initial public release.

### Core engine

- In-memory query execution over existing Java POJOs (`List<T>`) — no ORM rewrite, no database required.
- Filtering with AND/OR rule groups, field path traversal, computed fields, and optional equality index hints.
- Ordering, grouping, aggregates (`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`), HAVING, and DISTINCT.
- JOIN execution across multiple sources via `JoinBindings` with fast-array join path for single-key equality joins.
- Time-bucket aggregation.
- Streaming/lazy execution with a true lazy POJO fast path for simple non-joined/non-aggregate/non-ordered shapes.
- Pagination: `LIMIT`/`OFFSET`, named parameters (`:limit`, `:offset`), and first-class keyset cursor (`SqlLikeCursor`) with stable ORDER BY contract.
- Explain and schema metadata on every execution path.
- Telemetry hooks via `PojoLensRuntime` listener bridge.

### Window analytics

- `ROW_NUMBER()`, `RANK()`, and `DENSE_RANK()` with `OVER (PARTITION BY … ORDER BY …)`.
- Aggregate window functions (`SUM`, `AVG`, `MIN`, `MAX`, `COUNT`) with running-window frame.
- `QUALIFY` clause for post-window row filtering.
- Fluent parity: `addWindow(...)`, `addQualify(...)`, qualify rule groups.
- SQL-like window/qualify unified onto fluent execution path via `SqlLikeBinder`.

### Query surfaces

- **Fluent API** (`PojoLensCore`, `QueryBuilder`) — type-safe Java composition; canonical capability layer.
- **SQL-like API** (`PojoLensSql`, `SqlLikeQuery`) — dynamic/config-driven query strings; SQL-like parsing, validation, and binding onto the fluent/core path.
- `SqlLikeBoundQuery` — reusable bound execution with materialized source rows for repeated runs.

### Output helpers

- **Chart mapping** — `PojoLensChart` and `ChartQueryPreset` for chart payload generation; built-in Chart.js dataset mapping (`ChartJsDataset`, `ChartSpec`) including `withType(...)` for `BAR`/`PIE`/`LINE`/`AREA` switching.
- **Stats presets** — `StatsViewPresets` (`summary`/`by`/`topNBy`), `StatsViewPreset`, `StatsTable`, and `StatsTablePayload`/`TabularRows`/`tablePayload(...)` for grouped table output.
- **Report definitions** — `ReportDefinition<T>` as the canonical reusable-query contract with chart and stats promotion.
- **Dataset bundles** — `DatasetBundle` as the reusable snapshot form for multi-source execution.
- **Snapshot comparison** — regression fixture and snapshot diff support.

### Runtime and integration

- `PojoLensRuntime` — instance-scoped policy, cache tuning, DI support, and optional multi-tenant query behavior. Only public cache-tuning surface.
- **Spring Boot autoconfigure and starter** — `pojo-lens-spring-boot-autoconfigure` and `pojo-lens-spring-boot-starter` auto-configure `PojoLensRuntime` via `pojo-lens.*` properties; optional Micrometer telemetry listener bridge; published alongside the runtime artifact.
- **Spring Boot examples** — `examples/spring-boot-starter-quickstart` (minimal onboarding) and `examples/spring-boot-starter-basic` (advanced dashboard with Chart.js, Bootstrap, REST endpoints, and Java Playwright E2E tests).

### Build and quality

- Multi-module Maven build: `pojo-lens` (runtime jar), `pojo-lens-spring-boot-autoconfigure`, `pojo-lens-spring-boot-starter`, `pojo-lens-benchmarks` (deploy-skipped JMH tooling).
- Date-based versioning scheme `YYYY.MM.DD.HHmm`; Git tags use `release-<version>`.
- Maven Central release profile (`release-central`) with sources, Javadoc, GPG signing, and Central publishing plugin.
- Binary compatibility gate via `japicmp` CI job against the latest `release-*` tag.
- Public API stability policy (`docs/public-api-stability.md`) with 1.x tiering and compatibility contract.
- JMH benchmark suite isolated in `pojo-lens-benchmarks`; threshold checker in `benchmarks/thresholds.json` with CI guardrails.
- Checkstyle baseline gate (`scripts/checkstyle-baseline.txt`).
- Doc consistency checker (`scripts/check-doc-consistency.ps1`, `.py`).
