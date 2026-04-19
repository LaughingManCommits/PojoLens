# Changelog

All notable changes to this project will be documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions use date-based scheme `YYYY.MM.DD.HHmm`.

---

## [Unreleased]

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
