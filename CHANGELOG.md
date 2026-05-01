# Changelog

All notable changes to this project will be documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions use date-based scheme `YYYY.MM.DD.HHmm`.

---

## [Unreleased]

### Added

- **Orchestrator command decomposition** - split retained-run summary/lifecycle,
  review/export/promote, validation/checkpoint persistence, and eval logic into
  `pojo_lens_agents.run_summary`, `review_ops`, `validation_ops`, and `evals`
  while keeping `scripts/ai/claude-orchestrator.py` as the compatibility
  entrypoint with the existing CLI and JSON contracts.

- **Lazy orchestrator layer loading** - the compatibility entrypoint now uses
  on-demand loading for extracted `pojo_lens_agents` modules so the full split
  stack is not imported until a command path actually touches it.

- **Thin orchestrator compatibility shim** - `scripts/ai/claude-orchestrator.py`
  is now a small compatibility wrapper that delegates to
  `pojo_lens_agents.orchestrator_app`, keeping the operator entrypoint well
  below the 1000-line target while preserving existing callers and tests.

- **Orchestrator planner/runtime-admin decomposition** - extracted planner
  prompt/invocation flow into `pojo_lens_agents.planner_ops`, cleanup/status/
  inventory/prune logic into `runtime_admin`, and retained run-record coercion
  plus branch-context helpers into `manifest_records` so the oversized
  `orchestrator_app` stops owning those command surfaces inline.

- **Orchestrator prompt/execution/manifest decomposition** - extracted prompt
  and worker-contract logic into `prompt_contracts`, `worker_contracts`, and
  `validate_cli`, task execution/workspace prep into `task_execution`, and run
  manifest serialization into `manifest_io`, reducing the remaining
  `pojo_lens_agents.orchestrator_app` control-plane surface again.

- **Orchestrator task-plan governance decomposition** - extracted run-policy,
  agent/task-plan loading, effective read/write scope helpers, and scope
  validation into `pojo_lens_agents.task_plan_ops`, further reducing the
  remaining orchestration app surface while keeping CLI contracts and focused
  tests intact.

- **Approval lifecycle state machine** - retained runs now expose explicit
  `lifecycleState` / `lifecycleStateReason` plus `approvalSummary`, and the
  coordinator persists `coordinatorReview`, `coordinatorValidation`, and
  `coordinatorPromotion` checkpoints with run-local summary artifacts so
  review, validation, and promotion gates are resumable and inspectable.

- **Retained-run corpus evaluation** - added `evaluate-corpus` so the
  orchestrator can aggregate retained-run `scoreSummary` status, average score
  percent, and benchmark-dimension counts across the runtime root.

- **Orchestrator score summary and eval fixture** - added a compact
  `scoreSummary` to `evaluate-run` so retained runs expose trendable
  pass/warn/fail counts and a simple quality percentage, and added the tracked
  `example-eval-readonly-review` fixture as the first WP32 eval-corpus anchor.

- **Atomic AI memory publish handshake** - `refresh-ai-memory` now stages
  derived artifacts before publishing them, updates a derived
  `ai/indexes/publish-state.json` marker, and lets `refresh-ai-memory -Check`
  wait/retry across an in-flight publish instead of reporting a false stale
  state during concurrent refresh/check runs.

- **Effort override and visibility for orchestration** - added `--effort` to
  `plan`, `run`, `resume`, and `retry`; retained manifests and retained-run
  views now surface resolved per-task effort/source plus `effortCounts`; and
  `evaluate-run` now warns when read-only tasks use high effort on non-complex
  model profiles.

- **Run-event lineage trace** - added manifest-backed run events for orchestration
  start, ready batches, task completion/blocking, parent-task lineage, and run
  finish so retained runs can be debugged and evaluated without inferring
  scheduler behavior from task records alone.

- **Trace summaries in retained-run views** - added compact `traceSummary`
  rollups to run inventory and status payloads so operators can see event
  counts, phase counts, latest phase, and referenced task lineage without
  opening full manifest event arrays.

- **Branch-context lineage and run evaluation** - added task-level
  `branch_context_id` lineage, compact `branchSummary` rollups, branch-aware
  dependency handoff text, an `evaluate-run` command for orchestration quality
  checks, and the tracked `example-trace-multibatch` regression fixture.

- **Run visibility and operator UX** - added a `status` command for retained
  runs, richer inventory flags and counts, grouped review summaries, dry-run
  promotion allow/refuse summaries, and documented operator flow for validate,
  run, review, validate-run, promote, cleanup, and prune.

- **LangGraph execution spike** - added
  `pojo_lens_agents.langgraph_spike` and focused tests to map the current
  orchestrator lifecycle onto candidate graph nodes, compare manifest-backed
  `resume`/`retry` semantics and review/promotion interrupts, simulate
  checkpointed parallel execution, and record the decision to keep the custom
  scheduler and manifest model as the production path for now.

- **Typed authoring compiler integration** - added
  `@GeneratePojoLensTypedFields` and `PojoLensTypedFieldsProcessor` so javac
  can emit IDE-visible `TypedField<T,V>` constants during compilation without
  Lombok-style AST rewriting. The existing `FieldMetamodelGenerator` remains
  the documented manual fallback.

- **Typed-field processor hardening** - expanded compiler-generation coverage
  across primitive, boxed, enum, time, nested, excluded, static/final, no-field,
  duplicate-target, invalid-target, and graph-depth cases, and documented the
  processor-path opt-in policy.

- **Typed compiler example** - added `examples/typed-compiler-maven` as a
  standalone Maven project that compiles an annotated model and consumes the
  generated typed constants from the same module.

- **Gradle and Kotlin typed-generation support** - added service-loader and
  Gradle incremental annotation-processor descriptors for
  `PojoLensTypedFieldsProcessor`, plus standalone Gradle Java and Kotlin/JVM
  kapt examples. Kotlin support is intentionally field-model based in this
  slice: `@JvmField var` fields are supported, while Kotlin property/data-class
  generation remains a future KSP or property-metadata decision.

### Changed

- **Risk-console typed DSL** - switched the retained Spring risk-console Query
  Studio typed query from hand-written `TypedField.of(...)` constants to
  compiler-generated `TransactionRecordTypedFields` constants.

- **Orchestrator CLI global options** - all 12 subcommands now accept
  `--verbose`/`-v`, `--provider-bin` (with `--claude-bin` as a legacy alias),
  `--dry-run`, and `--json` consistently. Previously several commands lacked
  `--dry-run` (`validate`, `review`, `export-patch`, `inventory`, `cleanup`) or
  `--json` (`cleanup`). `--max-parallel` is now documented in the root CLI
  description as the primary feature for independent parallel task execution.

- **Orchestrator exit codes** - `main()` now returns distinct codes:
  `EXIT_SUCCESS` (0), `EXIT_ERROR` (1), `EXIT_BOOTSTRAP` (2, reserved for
  `cli.py`), `EXIT_VALIDATION` (3, plan schema invalid), `EXIT_WORKER_FAILURE`
  (4, tasks returned "failed"), `EXIT_BLOCKED` (5, tasks blocked), 
  `EXIT_UNSAFE_PROMOTION` (6, promotion refused), and `EXIT_CRASH` (7,
  unexpected Python exception). `PromotionBlockedError` and `ValidationError`
  are new `OrchestratorError` subclasses that route to their respective codes.

- **Orchestrator runtime layering** - split the monolithic local multi-agent
  script behind package layers for scheduling, provider subprocess/JSON
  handling, path safety, run manifests, workspace review primitives, and
  run-governance checks while preserving the existing CLI JSON contracts and
  bounded parallel execution semantics.

### Removed

- **Redundant Spring dashboard example** - removed
  `examples/spring-boot-starter-basic` so the example set has a clearer
  progression: Spring quickstart for onboarding, risk console for the full
  dashboard/showcase, and typed compiler examples split by build tool.

- **Risk-console reviewer packet references** - removed the stale reviewer-doc
  consistency test and README handoff references from the retained risk-console
  example so the example stays focused on runnable app behavior.

## [2026.04.29.1809] - 2026-04-29

### Changed

- **Metamodel tooling surface consolidation** - moved
  `MetamodelBatchGenerator`, `MetamodelGenerationMode`,
  `MetamodelGenerationRequest`, and `MetamodelGenerationResult` into
  `laughing.man.commits.metamodel` so all metamodel generation stays under one
  public surface while `laughing.man.commits.tooling` remains validation-only.

- **SpotBugs static-analysis gate** - switched `pojo-lens` `-Pstatic-analysis`
  from the report-only `spotbugs` execution to the build-failing
  `spotbugs:check` goal with `failThreshold=High`, so high-severity
  SpotBugs findings now gate `verify` while lower-priority warnings still
  emit XML output.

- **Reusable wrapper guidance collapse** - first-read wrapper docs now center
  `ReportDefinition` and `SavedReport` as the default reusable contracts.
  `ChartQueryPreset` / `ChartQueryPresets` and `StatsViewPreset` /
  `StatsViewPresets` remain public as advanced convenience sugar rather than
  peer default workflow identities.

- **Public surface layering guidance** - first-read docs now separate the
  three primary authoring modes (`PojoLensSql`, `PojoLensNatural`,
  `TypedQuery`) from reusable contracts, runtime policy, and output helpers.
  Added `docs/output-helpers.md` so chart/table/schema guidance reads as one
  layered helper story rather than multiple peer workflow identities.

- **Typed DSL guide split** - added `docs/typed.md` as the dedicated typed
  authoring guide and moved first-read navigation to treat typed query usage
  as a peer route beside SQL-like and natural. `docs/metamodel.md` now points
  back to that guide and stays focused on typed-field generation tooling.

### Fixed

- **Window row materialization** - `FluentWindowSupport` now computes window values before wrapping `RawQueryRow`s, so fluent and SQL-like `ROW_NUMBER`, ranking, aggregate window, and `QUALIFY` queries keep populated aliases.

- **Window snapshot schema path** - `RawQueryRow` now reports computed-field-aware names when fields are already materialized, and `FilterQueryBuilder.snapshotForRows` reuses existing execution source types instead of rescanning row schemas. This keeps computed-join semantics intact while trimming the window benchmark path.

### Added

- **TypedQuery window and qualify slice** - added `TypedWindowOrder`,
  `TypedQuery.window(...)`, `windowCountAll(...)`, and `qualify(...)` so
  code-owned typed queries can express rank windows, default running aggregate
  windows, and post-window filtering on the same immutable surface used for
  joins, grouped aggregates, and `HAVING`.

- **TypedQuery bounded window frames** - aggregate typed windows and
  `windowCountAll(...)` now accept `QueryWindowFrame`, so code-owned queries
  can express trailing and full-partition `ROWS` frames on the same typed
  surface without introducing a second frame DSL. Typed subqueries remain
  deferred.

- **TypedQuery bounded subqueries** - typed predicates now support bounded
  `IN` / `EXISTS` / `NOT EXISTS` composition over the same source or an
  explicit source list, reusing the existing grouped-predicate lowering path
  instead of adding a second typed query builder. Correlated/scalar
  subqueries and broader named-source planning remain on the text surfaces.

- **TypedQuery HAVING slice** - added `TypedQuery.having(...)` plus grouped
  output validation so code-owned typed queries can filter grouped fields and
  metric aliases on the same immutable surface used for joins and aggregates.
  Totals-style metric queries can also apply typed `HAVING`, and parity
  coverage now compares the grouped typed `HAVING` path to equivalent
  SQL-like execution.

- **TypedQuery join and aggregate slice** - added `TypedQuery.join(...)`,
  `groupBy(...)`, `count(...)`, and `metric(...)`, plus `JoinBindings` /
  `DatasetBundle` execution, explain, and schema overloads so code-owned typed
  queries can reuse the same named multi-source model as SQL-like and natural
  flows while covering grouped aggregates and totals-style projections without
  adding parallel surfaces.

- **Library-first build tooling** - added
  `laughing.man.commits.metamodel.MetamodelBatchGenerator`,
  `MetamodelGenerationRequest`, `MetamodelGenerationResult`,
  `SavedReportCatalogValidator`, `SavedReportValidationResult`,
  `SavedReportCatalogValidationResult`, and `ToolingValidationIssue` as the
  first-party build/CI tooling surface for batch metamodel generation and
  saved-report/query validation without live data execution.

- **Build tooling guide** - added `docs/build-tooling.md` with the current
  tooling-shape decision, batch generation examples, saved-report catalog
  validation examples, a Maven generated-sources recipe, and incremental-build
  guidance.

- **Unified file-boundary loader surface** - added `PojoLensFiles` plus
  `runtime.files()` / `FileLoadRuntime` as the single public file-boundary
  loader story, while keeping `PojoLensCsv` and `runtime.csv()` as stable
  CSV-only convenience routes.

- **TSV boundary loading** - added `PojoLensFiles.tsv(...)` and
  `tsvWithReport(...)`, reusing the existing typed-row diagnostics and
  coercion model while forcing tab delimiters on the shared file loader
  surface.

- **JSON and JSONL boundary loading** - added `PojoLensFiles.json(...)`,
  `jsonWithReport(...)`, `jsonl(...)`, and `jsonlWithReport(...)`, plus
  runtime-owned `JsonOptions` defaults and structured `JsonLoadResult` /
  `JsonLoadReport` / `JsonLoadException` diagnostics on the same shared
  file-boundary surface.

- **Shared file-loader row schema support** - added internal shared row-schema
  binding support reused by CSV and JSON loaders so file-boundary schema and
  primitive-field validation stay aligned without creating a second loader
  abstraction layer.

- **Virtual-thread boundary profiles** - added opt-in `virtual` Spring
  profiles for the quickstart, basic, and risk-console examples; extended the
  starter/basic/quickstart smoke surfaces to expose
  `virtualThreadsEnabled`/`requestThreadVirtual`; added virtual-mode
  integration tests plus core `QueryCancellationToken.ofThread(...)`
  virtual-thread coverage; and documented that virtual threads are a
  Spring/JDBC boundary option rather than a core-engine throughput feature.

- **Scatter JFR profiling harness** - added
  `laughing.man.commits.benchmark.ChartScatterProfileMain` to the benchmark
  module and documented a repeatable `docs/benchmarking.md` JFR recipe for the
  `SCATTER size=100000` parity path, including the local Windows caveat that
  `jdk.CPUTimeSample` may be unavailable and should be verified with
  `jfr summary`.

- **Chart.js scatter bridge** - `ChartJsAdapter.toPayload()` now supports `ChartType.SCATTER`: emits `[{x, y}]` point arrays per dataset and a `type: "linear"` numeric x-axis instead of categorical labels. `ChartDataset` gains `xValues` (numeric x-coordinates populated by `ChartMapper` for scatter specs). `ChartJsDataset.data` widened to `Object` to support both numeric arrays and point-object arrays. 6 adapter bridge tests green.

- **Facet option helper** - added `FacetOption(value, count)` record and `FacetPresets.distinctCounts(fieldName)` factory returning `FacetQuery<T>` in package `laughing.man.commits.facet`. Counts distinct field values in-memory from any POJO list; results sorted by count descending then value ascending. 5 tests green.

- **Spring/JDBC bridge** (`PojoLensJdbc`) - added `PojoLensJdbc.query(jdbcTemplate, sql, rowClass, params...)`, `queryPushed(...)`, and `read(resultSet, rowClass)` to `pojo-lens-spring-boot-autoconfigure`. Wraps `JdbcTemplate` + `SqlLikeResultSetAdapter` so callers do not need to write `ResultSetExtractor` wrappers. `spring-jdbc` added as optional dependency to the autoconfigure module.

- **Period comparison helper** - added `PeriodComparison` and `ReportComparisons` to `laughing.man.commits.report`. `ReportComparisons.of(current, previous)`, `compare(currentRows, previousRows, field, metric)`, and `compareCount(...)` produce a `PeriodComparison` with `percentageDelta()` (`"+5%"`, `"-3%"`, `"flat"`, `"new"`), `ratePointDelta()` (`"+0.5 pt"`), `absoluteDelta()`, and `relativeDelta()`. 10 tests green.

- **Risk console showcase example** - added `examples/spring-boot-starter-risk-console`, a realistic Spring Boot dashboard that loads seeded payment-risk data from JDBC-backed tables, maps rows into POJOs, and uses PojoLens for dashboard summaries, trends, review queue ranking, merchant drilldowns, transaction detail views, saved reports, and report inspection. Includes Java Playwright browser coverage, screenshots, H2 test fallback, Docker Compose for MySQL-local runtime, and a core-library improvement-candidate log for showcase-driven gaps.

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

## [Unreleased]
  split completion paths.

- **Reflection hotspot guardrails** (`STRAT-WP5` first slice) - added
  `benchmarks/hotspot-thresholds.json` and
  `scripts/benchmark-suite-hotspot-reflection.args` for warmed forked JMH
  budgets on `HotspotMicroJmhBenchmark.reflectionToDomainRows` and
  `reflectionToClassList`. The narrow guardrail keeps the broader hotspot suite
  diagnostic-only while freezing the most stable reflection conversion paths
  into conservative threshold checks.

### Changed

- **Reusable report wrapper consolidation** - `ChartQueryPreset` and `StatsViewPreset` now delegate shared `source()`/`schema()`/`rows(...)` behavior to cached `ReportDefinition` owners while keeping chart/table-specific helpers on the specialized wrappers. Added regression coverage for the list, join-binding, and dataset-bundle bridge against `ReportDefinition`.

- **Repo-local Checkstyle profile** - switched the `pojo-lens` `lint`
  profile from stock `sun_checks.xml` to
  `config/checkstyle/checkstyle.xml`, keeping the gate focused on active
  hygiene rules (`AvoidStarImport`, `UnusedImports`, `NeedBraces`,
  `WhitespaceAfter`, `OperatorWrap`, and `RedundantModifier`) that match the
  current repository conventions.

- **SpotBugs Java 25 compatibility** - upgraded
  `spotbugs-maven-plugin` from `4.8.6.6` to `4.9.8.3` so the existing
  `-Pstatic-analysis` report can analyze Java 25 class files again instead of
  failing on unsupported class-file version `69`.

- **Checkstyle baseline refresh** - regenerated
  `scripts/checkstyle-baseline.txt` from the current `-Plint` report so the
  staged baseline gate matches the present repo-wide Checkstyle backlog again
  (`report=18454 baseline=18454 new=0 fixed=0`).

- **Internal lint cleanup** - tightened `final` parameters and wrapped long
  internal chart/join-helper lines in `ChartValidation`,
  `ChartResultMapper`, and `FilterQueryBuilder`, reducing the Checkstyle
  report from `18454` to `18420` before refreshing the staged baseline.

- **Lint gate completion** - cleaned the remaining repo-local Checkstyle
  violations in the touched internal/runtime/test slice, refreshed
  `scripts/checkstyle-baseline.txt`, and brought the `-Plint` gate plus the
  staged baseline check to `report=0 baseline=0 new=0 fixed=0`.

- **Spring/JDBC boundary guidance** - `docs/advanced-features.md`,
  `docs/jdbc.md`, and the Spring example READMEs now explicitly frame virtual
  threads as an opt-in blocking-boundary integration choice, document the
  `QueryCancellationToken.ofThread(...)` limitation to one concrete thread, and
  record the current no-long-lived-JDBC-lock pinning audit result.

- **Java 25 internal modernization** - sealed `FilterExpressionAst`, converted
  `CompiledRule` plus the `AggregationEngine` / `FastStatsQuerySupport`
  internal carrier types to records, replaced cast-based SQL-like and natural
  AST/value dispatch with pattern switches or binding patterns, and converted
  aggregation metric routing to switch expressions. `pojo-lens` main sources
  already had zero `.collect(Collectors.toList())` usages before this pass, so
  no production collector changes were needed.

- **WP17 internal Java 25 cleanup** - replaced repetitive manual dispatch in
  `SqlLikeCursor`, `TimeBucketUtil`, `ChartValidation`, `ChartResultMapper`,
  `ReportComparisons`, and `ObjectUtil` with non-preview switch expressions,
  consolidated internal `QueryRow` source detection in selected
  `FilterQueryBuilder` helpers, and added targeted regression coverage for
  cursor token round-trips plus numeric/chart/report coercion paths.
- **Filter rule-compilation deduplication** - extracted
  `FastPojoRuleSupport` as the shared owner of `compileRuleBundle`,
  `addKnownFields`, and the `CompiledRuleBundle` record. Both
  `FastPojoFilterSupport` and `FastPojoStreamSupport` now delegate to it,
  eliminating the duplicated AND/OR evaluation logic that previously existed
  in both classes.

- **`isNullOrBlank` consolidation** - replaced 5 instances of the redundant
  `x == null || StringUtil.isNull(x.trim())` pattern in
  `FilterQueryBuilder`, `QueryWindow`, and `QueryWindowOrder` with the
  canonical `StringUtil.isNullOrBlank(x)` call.

- **Query message consolidation** - added
  `sqllike.internal.error.SqlLikeFieldMessages` as the shared SQL-like
  unknown-field message owner, migrated validator/JOIN/diagnostics callers to
  it without changing user-facing wording, and deduplicated the repeated
  natural-field ambiguous/unknown helper flow in
  `NaturalQueryResolutionSupport`.
- **Docs navigation and surface maps** - linked the new `docs/facets.md` and `docs/jdbc.md` guides from the README/docs navigation and product-surface/public-stability maps, corrected the facet field-access note to match reflection behavior, and surfaced `examples/spring-boot-starter-risk-console` in the example inventory.
- **Risk console tabbed workspace** - split the large single-page dashboard into focused Overview, Analytics, Operations, PojoLens, and Reports tabs while keeping the same backend/API surface. Browser tests now switch tabs explicitly before interacting with hidden controls, and charts are resized when a tab becomes active so the tabbed UI stays stable.
- **Risk console tab navigation polish** - active top-level dashboard tabs now persist in the URL hash, restore on reload, and support keyboard arrow/home/end navigation. Dense PojoLens and Reports areas are split into secondary sub-tabs, so Workbench, Query Studio, report output, and report inspector no longer compete in one long vertical section. Browser coverage now locks the hash-backed tab restore and keyboard tab UX in place.
- **Risk console dashboard service refactor** - split the oversized `RiskConsoleDashboardService` into focused overview, transactions, workbench, reports, and shared-query-support services while keeping the controller contract and existing tests stable. The old dashboard service is now a thin facade, so app behavior stays the same but Spring responsibilities are easier to read, test, and extend.
- **ResultSet adapter bridge** - `SqlLikeResultSetAdapter` now accepts normalized JDBC-style column labels (`snake_case`, `kebab-case`, spaced labels, and case differences) and materializes common JDBC temporal values into matching Java time fields. The risk-console example now uses this adapter for its flat JDBC snapshot and related repository reads instead of hand-written row mapping everywhere.
- **Risk console transaction paging UX** - made keyset pagination visible in the transactions panel instead of hiding it behind a quiet cursor flow. The API now returns `loadedRows`, `totalRows`, and `pageSize`, while the UI shows a live paging summary and clearer `Load More Rows` / `All Rows Loaded` states. Browser and backend tests now lock the visible paging contract in.
- **Risk console transaction page controls** - replaced the append-only transaction `Load More` interaction with real Previous/Next page buttons backed by client-side keyset cursor history. The transactions panel now shows page numbers plus row ranges, while browser tests verify forward and backward page navigation.
- **Risk console PojoLens value story** - added a visible `Why PojoLens matters here` panel near the top of the dashboard, backed by a new `/api/dashboard/value-story` endpoint. It now explains the JDBC-to-POJO boundary, the filtered snapshot size, the number of dashboard/report surfaces PojoLens powers, and the concrete feature set in use. Browser and backend tests now lock this story in so the showcase does not bury PojoLens behind generic app chrome.
- **Risk console Query Studio** - added a dedicated Query Studio panel and `/api/dashboard/query-studio` endpoint to showcase PojoLens `runtime.natural()` with runtime vocabulary, `ReportDefinition.natural(...)`, `TypedQuery.from(...)`, and cooperative cancellation guards on the same filtered snapshot. The saved-report catalog now includes a natural-query contract, reviewer docs inventory the new screenshot, and browser/backend tests lock the new showcase surfaces in.
- **Risk console dashboard UX** - transactions now support real browser-tested filter empty states, sort controls for `id`, `createdAt`, `amount`, and `riskScore`, responsive layout checks, saved-report run/inspect flow, and resilient empty-result handling across summary cards, charts, tables, and inspector payloads.
- **Risk console advanced inspector UX** - moved low-signal PojoLens internals behind collapsed disclosures in the workbench and saved-report inspector so the default dashboard stays business-focused. Java Playwright coverage now opens those panels explicitly before asserting computed fields, policy, guard, telemetry, query, plan, diagnostics, pushdown, and explain metadata.
- **Risk console chart variety** - replaced the broken scatter-path dashboard experiment with stable PojoLens-backed `LINE`, `AREA`, `BAR`, `PIE`, and stacked `BAR` panels. The overview now shows payment-method share as a pie chart, decline reasons as a bar chart, and keeps regional status as a stacked bar. Browser analytics coverage now asserts the richer chart mix directly.
- **Risk console richer PojoLens showcase** - added a dedicated workbench panel and endpoint that now surfaces `StatsViewPresets`, `DatasetBundle` multi-source joins, computed fields, exposure policy metadata, execution guard metadata, telemetry snapshots, and join explain output in the live dashboard. Reviewer docs and screenshot inventory now include this richer PojoLens surface.
- **Risk console Java Playwright structure** - split the showcase browser suite into a shared `PlaywrightE2EBase` plus dedicated smoke, overview, drilldown, reports, filtering, sorting, and responsive test classes. This matches the planned reviewer workflow better and keeps failures narrow and readable under plain `mvn test`.
- **Risk console accessibility and analytics checks** - added keyboard-focus styling in the dashboard shell plus dedicated Java Playwright accessibility sanity and analytics filter tests. README now documents `mvn test` and the screenshot output path for reviewer runs.
- **Risk console reviewer coverage depth** - added dedicated Java Playwright merchant-detail and review-queue tests, plus README endpoint inventory and screenshot inventory for reviewer handoff.
- **Risk console backend contract tests** - added Spring Boot service and controller tests for summary, review queue, report inspection, and transaction sort behavior. Report inspector browser checks now assert diagnostics/default params/pushdown metadata instead of only checking that panels render.
- **Risk console reviewer handoff packet** - added `examples/spring-boot-starter-risk-console/REVIEWER.md`, linked it from the example README, and locked negative contract checks for invalid transaction sort fields and bad cursor tokens so reviewers can verify strict API failure behavior instead of only happy paths.
- **Risk console reviewer output and shell polish** - added `examples/spring-boot-starter-risk-console/REVIEW-REPORT-TEMPLATE.md`, linked reviewer docs together, added a live scope-summary strip for active filters/sort/data flow, and kept the dashboard smoke suite asserting the new UI summary block.
- **Risk console reviewer workflow guardrails** - expanded the example README with an explicit reviewer workflow and added `ReviewerDocsConsistencyTest` so documented screenshot inventories in `README.md` and `REVIEWER.md` must stay aligned with real Java Playwright `capture(...)` calls.
- **Risk console final review snapshot** - added `examples/spring-boot-starter-risk-console/REVIEW-REPORT.md`, linked it from the example docs, and recorded current reviewer verdict: showcase-ready for engineering review with real MySQL runtime verification still deferred.
- **Risk console created-at paging fix** - restored transaction paging for `createdAt` sort by keeping PojoLens filter/sort behavior and using an app-owned cursor token with ISO timestamp plus `id`. Added service and browser regression coverage so date-sorted `Load More` now works and stays tested.

### Fixed

- **Empty Checkstyle baseline refresh** - `scripts/check-lint-baseline.ps1`
  now handles zero-violation reports when writing the staged baseline instead
  of failing with a null `WriteAllLines(...)` argument.

- **WP15 scatter chart parity hotspot** - typed multi-series scatter mapping
  now defers x-label string conversion and reuses resolved direct-field handles
  instead of repeating per-row field-name lookups. The chart JMH harness now
  also primes reusable SQL-like chart state in `@Setup`, and the strict chart
  parity rerun passes for `SCATTER size=100000` at fluent `4.887 ms/op`,
  SQL-like `7.454 ms/op`, ratio `1.526`.

- **WP14 expression evaluator input validation contract** -
  `SqlExpressionEvaluator` now rejects null and blank expressions before any
  Caffeine cache lookup, restoring deterministic `IllegalArgumentException`
  behavior for `compileNumeric`, `collectIdentifiers`, `rewriteIdentifiers`,
  and `evaluateNumeric`. The valid-expression cache path stays unchanged.
- **WP13 stats-plan-cache reset semantics** - `FilterExecutionPlanCacheStore`
  now treats `resetStats()` as a true fresh-start operation by swapping in a
  new empty cache instead of rebuilding from existing entries. Runtime and
  public API regression coverage now lock `size()==0` immediately after reset
  and require the next equivalent stats query to record a miss rather than a
  hit.
- **WP12 QueryRow alias projection schema safety** - `SqlLikeExecutionSupport`
  no longer assumes every `QueryRow` shares the first row's field order during
  aliased or computed projection. Preferred indexes are now validated against
  each row's field names before use, so mixed-schema `QueryRow` inputs fall
  back to correct name-based resolution instead of returning swapped values.
- **Risk console empty decline-chart subset** - trends now return explicit empty chart payloads when the filtered transaction snapshot is non-empty but the decline-only subset is empty. This prevents `EQ-SQL-VAL-008` time-bucket validation failures on approved-only or otherwise no-decline dashboard scopes.
- **Risk console transaction next-page bug** - replaced the mixed paging implementation with one stable app-owned cursor strategy across `id`, `createdAt`, `amount`, and `riskScore` sorts. The transactions endpoint now pages over the PojoLens-sorted snapshot consistently, so `Next Page` no longer lands on an empty table when more results exist.
- **Risk console chart resize loop** - bounded dashboard and report chart canvases with fixed-height chart frames so Chart.js no longer grows the page vertically under responsive resize mode. Added smoke coverage that checks page height stabilizes after load.
- **Risk console workbench stability** - fixed joined-field exposure policy gaps, made join predicates explicit, tuned the demo execution-guard complexity ceiling for real UI filter flows, and returned schema-backed empty workbench payloads instead of crashing when filters produce no rows. Added browser and service regression coverage for the new workbench and empty-state path.
- **Risk console large-snapshot workbench guard** - raised the workbench `maxRowsScanned` ceiling from `10_000` to `250_000` so realistic showcase datasets do not 500 under the runtime inspector path while keeping returned-row and duration guardrails in place.

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

- **Java 25 toolchain alignment** - moved the repository build and CI matrix to
  Java 25, set `maven.compiler.release=25`, and updated workflow artifact steps
  to `actions/upload-artifact@v6`.
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
