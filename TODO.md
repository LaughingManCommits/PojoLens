# TODO

## Product Direction

**Conclusion:** PojoLens should be the embedded reporting and governed query
layer for Java apps working over already-materialized object snapshots.
It should stop reading like a general-purpose Java query stack, because that
puts it into direct competition with stronger database-first tools.

**Why this direction fits the repo:**
- The current strengths are text-first in-memory querying, diagnostics,
  explain/preview tooling, chart/report shaping, and Spring-friendly runtime
  wiring.
- The current weaknesses are broad production adoption, type-safe code-owned
  query composition, database pushdown, and large-workload execution limits.
- The winning niche is safe configurable reporting over data the application
  already owns in memory.

**Major wins to preserve while moving forward:**
- SQL-like and controlled natural query text over the same engine.
- Strong diagnostics, plan preview, explain payloads, keyset pagination, and
  telemetry hooks.
- Reusable report/chart/schema helpers and optional Spring Boot wiring.
- Strong executable docs and public-surface contract coverage.

**Non-goals for this roadmap:**
- Do not position PojoLens as a replacement for jOOQ, Querydsl, or Spring Data
  for normal database-backed application queries.
- Do not expand natural queries into a free-form AI/chatbot feature.
- Do not build a full authentication, RBAC, or tenant-security framework into
  the core engine.

---

## Status Overview

| WP  | Title                                  | Status            | Key deliverables                                                           |
|-----|----------------------------------------|-------------------|----------------------------------------------------------------------------|
| WP1 | Stable Embedded Reporting Contract     | Done              | SavedReport, SavedReportKind, TabularColumn.typeName(), 25 tests           |
| WP2 | Production Query Governance And Audit  | Done              | QueryExecutionGuard, QueryGuardOutcome, QueryComplexitySummary, 23 tests   |
| WP3 | Stable Public Typed DSL               | Done              | TypedField, TypedPredicate, TypedQuery foundation, typed metamodel, 61 tests |
| WP4 | Hybrid Adapters And Pushdown           | Done              | Pushdown preview, host adapter bridge, ResultSet ingestion, split execution, benchmarks |
| WP5 | Repeated-Workload Performance Upgrade  | Not started       | Ã¢â‚¬â€                                                                          |
| Ã¢â‚¬â€   | Release Gate                           | Pending decision  | WP1+WP2 shipped; release cut not yet triggered                             |

---

## STRAT-WP1: Stable Embedded Reporting Contract

**Priority:** High
**Goal:** Turn the repo's best current niche into a stable product surface for
saved reports, admin screens, and chart/table workflows.

Context:
- The repo already has `ReportDefinition`, chart presets, stats presets,
  schema output, and explain/preview metadata.
- Those helpers are useful, but several of them still live on the advanced
  surface instead of the clear stable product path.
- If PojoLens is going to win anywhere, it should win here first.

Scope:
- Decide which reporting helpers become stable public API versus which should
  be redesigned before stabilization.
- Define a versioned report/query contract that can be saved, reviewed, and
  replayed safely.
- Expose UI-friendly schema and plan metadata for report builders and admin
  tooling.
- Keep the core centered on reporting/query workflows, not a general workflow
  engine.

Tasks:
- [x] Audit advanced reporting helpers and decide which ones move to the stable
      surface. Finding: all current helpers (ReportDefinition, ChartQueryPreset,
      StatsViewPreset, TabularSchema, ChartSpec, SqlLikePlanPreview, etc.) are
      already on the public surface and stable; no redesign required.
- [x] Define a versioned saved-report contract covering query text, parameters,
      schema, and chart/table configuration.
      Delivered: SavedReport + SavedReportKind in report/ package. FORMAT_VERSION="1".
      Supports sqlLike() and natural() factories, immutable builders, planPreview(),
      diagnostics(), toQuery(), toNaturalQuery(), toDefinition(Class<T>).
- [x] Expose serializable field/column metadata for UI builders and saved
      report review.
      Delivered: TabularColumn.typeName() returns type().getSimpleName() for
      JSON-friendly column metadata.
- [x] Add examples for saved reports, runtime-owned presets, and migration-safe
      replay.
      Delivered: SavedReportTest covers full createÃ¢â€ â€™configureÃ¢â€ â€™reviewÃ¢â€ â€™replay workflow
      and both SQL-like and natural replay paths.
- [x] Add public API and binary-compat coverage for every promoted type.
      Delivered: StablePublicApiContractTest.stableSavedReportContractsShouldRemainAvailable()
      and stableTabularColumnTypeNameContractShouldRemainAvailable().

Validate:
- `mvn -B -ntp -pl pojo-lens "-Dtest=*Report*Test,*Preset*Test,*Schema*Test,*PublicApi*Test" test`
- `scripts/check-doc-consistency.ps1`
- `git diff --check`

---

## STRAT-WP2: Production Query Governance And Audit

**Priority:** High
**Goal:** Make user-authored SQL-like and natural queries safe enough for real
admin/config/reporting usage.

Context:
- Current exposure policy only allowlists fields and named sources.
- That is useful, but it is not enough for production query governance.
- Real-world adoption needs bounded execution, rejection reasons, and audit
  visibility for user-authored query text.

Scope:
- Add bounded query governance for complexity, row budgets, deadlines, and
  cooperative cancellation.
- Surface deterministic audit metadata for accepted, rejected, and aborted
  queries.
- Keep auth/RBAC outside the library, but give host applications a credible
  control point for safe execution.

Tasks:
- [x] Design a public execution-guard contract for max complexity, max rows
      scanned, max rows returned, deadline, and cancellation.
      Delivered: QueryExecutionGuard (builder API), QueryGuardOutcome (allowed/blocked
      with audit metadata), QueryComplexitySummary (from SqlLikePlanPreview), and
      QueryExecutionGuardException (carries full outcome). Block codes:
      GUARD_ROWS_SCANNED_EXCEEDED, GUARD_COMPLEXITY_EXCEEDED, GUARD_ROWS_RETURNED_EXCEEDED,
      GUARD_DURATION_EXCEEDED.
- [x] Add pre-execution complexity summaries from the parsed query shape.
      Delivered: QueryComplexitySummary.from(SqlLikePlanPreview) computes additive score
      (1/filter, 3/join, +2 group, +2 agg, +4 windows, +3 subqueries).
- [x] Apply guard checks to SQL-like and natural execution paths.
      Delivered: SqlLikeQuery.executionGuard(guard) + NaturalQuery.executionGuard(guard).
      Pre-execution check in prepareExecution (rows scanned + complexity), post-execution
      check in filter/chart methods (rows returned + duration). NaturalQuery propagates
      guard through createDelegate(). QueryTelemetryStage.GUARD_REJECTED emitted on block.
- [x] Emit audit-friendly telemetry/explain metadata for blocked or aborted
      queries.
      Delivered: QueryGuardOutcome.auditMetadata() returns structured map; GUARD_REJECTED
      telemetry events fired via QueryTelemetryListener before throwing.
- [x] Document the security boundary clearly: exposure control and execution
      governance are in scope; auth and tenant policy remain host-owned.
      Documented in QueryExecutionGuard Javadoc and docs/advanced-features.md.

Review follow-up (`2026-04-23`):
- [x] Harden guard enforcement across `stream(...)`, `iterator()`, and bound
      query execution so `maxRowsReturned` and `maxDurationMillis` cannot be
      bypassed by switching execution entry points.
- [x] Count bound JOIN source rows in the pre-execution row-scan budget instead
      of only the primary root rows.
- [x] Add explicit tests for lazy execution, bound execution, telemetry
      rejection, and join-backed row-scan budgets.
- [x] Re-scope or implement the still-missing WP2 contract pieces:
      cooperative cancellation plus deterministic aborted-query metadata.
      Delivered (`2026-04-23`): `QueryCancellationToken` (@FunctionalInterface,
      `ofAtomic`, `ofThread` factories); `QueryExecutionGuard.Builder#cancellationToken`;
      `QueryExecutionGuard#checkCancellation`; `QueryGuardOutcome#cancelled` factory
      with `rowsReturnedBeforeAbort`; polled in `GuardedIterator#hasNext` (lazy paths)
      and `prepareExecution` (eager paths) for SqlLikeQuery and TypedQuery;
      block code `GUARD_CANCELLED`; 30 tests in `QueryCancellationTest` after
      senior-review hardening.

Senior review follow-up (`2026-04-23`):
- [x] Fix bound eager cancellation so `SqlLikeBoundQuery.filter()` and
      `SqlLikeBoundQuery.chart(...)` re-check cancellation when execution starts,
      not only when the query is first bound.
- [x] Add stable public API contract coverage for `QueryCancellationToken`,
      cancellation-aware `QueryExecutionGuard` methods, cancellation outcomes,
      and external-package public API usage.
- [x] Make `TypedQuery` honor pre-execution cancellation even for empty input.
- [x] Document `GUARD_CANCELLED`, `QueryCancellationToken`, and
      `rowsReturnedBeforeAbort` in public execution-governance/stability docs.
- [x] Add regression tests for bound eager cancellation, empty typed input
      cancellation, and invalid cancellation row counts.

Validate:
- `mvn -B -ntp -pl pojo-lens "-Dtest=*Policy*Test,*Exposure*Test,*Telemetry*Test,*Natural*Test,*SqlLike*Test" test`
- `scripts/check-doc-consistency.ps1`
- `git diff --check`

---

## STRAT-WP3: Stable Public Typed DSL

**Priority:** High
**Goal:** Remove the biggest adoption blocker for code-owned queries without
backing away from the SQL-like-first public story.

Context:
- The current public path is strong for text-authored queries.
- The current public path is weak for teams that want compile-time-safe query
  composition in normal Java code.
- The old/internal fluent builder proves there is engine support here, but it
  is not the right public answer in its current form.

Scope:
- Design an initial stable typed DSL foundation that lowers into the shared
  engine without exposing internal builder machinery.
- Reuse metamodel generation so typed queries do not depend on caller-authored
  string field names.
- Keep SQL-like and natural as first-class text surfaces; the typed DSL is for
  code-owned filter/projection/order/page composition.
- Keep typed grouping, aggregation, joins, windows, and subqueries deferred
  until their API shape can be stabilized without leaking internal builder
  concepts.

Tasks:
- [x] Design a stable typed builder API for projection, filters, ordering, and
      paging.
      Delivered: `TypedQuery<T>` with immutable `select`, `where`, `orderBy`,
      `orderByDesc`, `limit`, `offset`, and `filter` methods.
- [x] Reuse or extend metamodel generation so typed queries do not depend on
      string field names.
      Delivered: `FieldMetamodelGenerator.generateTyped(...)`, including boxed
      primitive field types and compiler-backed generated-source tests.
- [x] Add typed predicate composition.
      Delivered: `TypedField<T,V>` and `TypedPredicate<T>` with leaf operators,
      `AND`/`OR`/`NOT` descriptors, and execution lowering that preserves nested
      mixed `AND`/`OR` semantics. `NOT` remains an explicit execution-time
      unsupported shape.
- [x] Ensure typed queries interoperate with explain and schema.
      Delivered: `TypedQuery.explain(...)` and `schema(...)`. Guard interop is
      limited to row-scan, row-return, and duration checks; typed queries do not
      have plan-preview complexity scoring.
- [x] Add migration guidance explaining when to use typed DSL versus SQL-like
      versus natural.
      Delivered: `docs/entry-points.md`, `docs/usecases.md`,
      `docs/metamodel.md`, and `docs/public-api-stability.md`.
- [x] Add contract tests that lock the typed DSL to stable public behavior
      rather than internal builder details.
      Delivered: `TypedFieldContractTest`, `TypedPredicateContractTest`,
      `TypedQueryContractTest`, and `StablePublicApiContractTest` coverage.

Deferred:
- [ ] Design stable typed grouping, aggregation, joins, windows, and subqueries
      as a later DSL expansion.

Validate:
- `mvn -B -ntp -pl pojo-lens "-Dtest=*Metamodel*Test,*PublicApi*Test,*QueryContractTest,*Fluent*Parity*Test" test`
- `scripts/check-doc-consistency.ps1`
- `git diff --check`

---

## STRAT-WP4: Hybrid Adapters And Pushdown

**Priority:** High
**Goal:** Make PojoLens viable when the source data is not already sitting in a
small-to-moderate in-memory list.

Context:
- Pure in-memory execution is fine for snapshots and bounded internal tooling.
- It is a dead end for broader adoption if every serious workload must fully
  materialize first.
- The repo needs a credible bridge story for database-backed or streaming
  source data.

Scope:
- Start with a bounded pushdown story, not a giant adapter matrix.
- Classify query shapes into "pushable", "split execution", and
  "in-memory only".
- Make fallback behavior explicit in explain/telemetry output.

Tasks:
- [x] Define the supported query subset for first-phase pushdown.
      Delivered (`2026-04-23`): `SqlLikePushdownPreview`,
      `SqlLikePushdownMode`, and `SqlLikeQuery.pushdownPreview()` classify
      SQL-like query shapes as `FULL`, `SPLIT`, or `IN_MEMORY_ONLY`.
      First-phase pushable stages are simple selected fields, comparison
      `WHERE` predicates with literals or named parameters, `ORDER BY`,
      `LIMIT`, and `OFFSET`. Joins, grouping, aggregation, windows, subqueries,
      `HAVING`, `QUALIFY`, computed selects, time buckets, and unsupported
      filter operators remain in-memory with stable fallback reason codes.
- [x] Add a first bridge path for JDBC/`ResultSet` ingestion or a jOOQ/Spring
      Data integration point for simple filter/order/page workloads.
      Delivered (`2026-04-23`): `SqlLikePushdownAdapter`,
      `SqlLikePushdownRequest`, `SqlLikePushdownResult`, and
      `SqlLikeResultSetAdapter` provide a host-owned adapter contract and JDBC
      `ResultSet` materialization helper without SQL rendering or database
      execution inside PojoLens.
- [x] Support split execution where simple stages push down and unsupported
      stages finish in memory.
      Delivered (`2026-04-23`): `SqlLikeQuery.filterWithPushdown(...)` fetches
      first-phase materialized rows through the adapter and then completes the
      SQL-like query in memory.
- [x] Surface pushdown/fallback decisions in explain and telemetry.
      Delivered (`2026-04-23`): `explain()` includes `pushdownPreview`, and
      SQL-like BIND telemetry includes pushdown mode, pushable stages,
      in-memory stages, and fallback reasons. This slice remains advisory
      planning metadata only because current repository boundaries still keep
      database execution, SQL rendering, and adapter authorization host-owned.
- [x] Benchmark pushed, split, and pure in-memory paths on representative
      workloads.
      Delivered (`2026-04-23`): `SqlLikePipelineJmhBenchmark` includes
      `pureInMemoryPushdownCandidate`, `pushedFirstPhaseCandidate`,
      `pureInMemorySplitCandidate`, and `splitPushdownCandidate`; the dedicated
      suite is `scripts/benchmark-suite-pushdown.args`.

Validate:
- `mvn -B -ntp test`
- targeted adapter integration tests
- benchmark guardrails from `docs/benchmarking.md`
- `git diff --check`

---

## STRAT-WP5: Repeated-Workload Performance Upgrade

**Priority:** Medium
**Goal:** Make repeated reporting workloads materially cheaper on latency and
allocation, especially around joins, windows, and typed projection.

Context:
- The repo already tracks performance seriously and documents real overheads.
- That is good engineering discipline, but it also exposes where the engine is
  still too allocation-heavy for a stronger product story.
- Better performance is a multiplier once the product direction is clear.

Scope:
- Improve hot paths without changing the public mental model.
- Focus on repeated workloads, not microbench bragging.
- Keep benchmark claims tied to explicit workloads and budgets.

Tasks:
- [x] Reduce reflection hot-path cost with cached or generated accessors where
      safe.
- [ ] Improve repeated join execution with reusable indexes/hash structures.
- [ ] Reduce window-stage allocation overhead.
- [ ] Evaluate a batch/columnar execution path for heavy report workloads.
- [x] Promote a small set of stable JMH budgets for the hottest supported
      workloads.

Validate:
- `mvn -B -ntp -pl pojo-lens-benchmarks test`
- benchmark guardrails from `docs/benchmarking.md`
- `mvn -B -ntp test`
- `git diff --check`

---

## Release Gate

**Priority:** High
**Goal:** Do not cut another release until at least one strategic package above
ships in a way that strengthens the product story, not just the feature count.

Tasks:
- [x] Decide the first strategic packages to ship Ã¢â‚¬â€ WP1 (reporting contract)
      and WP2 (governance) are complete and strengthen the product story.
- [ ] Decide whether WP3, WP4, or WP5 ships next before cutting the release,
      or cut based on WP1+WP2 alone.
- [ ] Update release notes around the product direction, not just the API
      delta.
- [ ] Run final release guardrails from `RELEASE.md`.

Validate:
- `mvn -B -ntp test`
- `mvn -B -ntp -Plint verify -DskipTests`
- `scripts/check-doc-consistency.ps1`
- release benchmark guardrails from `docs/benchmarking.md`
