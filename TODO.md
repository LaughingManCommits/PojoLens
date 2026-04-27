# TODO

## Product Direction

**Conclusion:** PojoLens is the embedded reporting and governed query layer for
Java apps working over already-materialized object snapshots.

**Winning niche:** Safe configurable reporting over data the application already
owns in memory. Strong diagnostics, plan preview, explain, keyset pagination,
telemetry hooks, reusable report/chart/schema helpers, and optional Spring Boot
wiring.

**Non-goals:**
- Not a replacement for jOOQ, Querydsl, or Spring Data for DB-backed queries.
- No free-form AI/chatbot natural queries.
- No auth, RBAC, or tenant-security framework in core.

---

## Status Overview

| WP  | Title                                       | Status   | Key deliverables                                                                     |
|-----|---------------------------------------------|----------|--------------------------------------------------------------------------------------|
| WP1 | Stable Embedded Reporting Contract          | Done     | SavedReport, SavedReportKind, TabularColumn.typeName(), 25 tests                     |
| WP2 | Production Query Governance And Audit       | Done     | QueryExecutionGuard, QueryGuardOutcome, QueryComplexitySummary, 23 tests             |
| WP3 | Stable Public Typed DSL                     | Done     | TypedField, TypedPredicate, TypedQuery, metamodel, 61 tests                          |
| WP4 | Hybrid Adapters And Pushdown                | Done     | Pushdown preview, host adapter bridge, ResultSet ingestion, split execution          |
| WP5 | Repeated-Workload Performance Upgrade       | Done     | Reflection caching, join reuse, window allocation, hotspot guardrails                |
| WP6 | Expression Cache Contention Fix             | Done     | Replace synchronized LRU map; Caffeine in SqlExpressionEvaluator                    |
| WP7 | Reflection Cache Bounds & Safety            | Done     | Size-bound all unbounded ConcurrentHashMaps in ReflectionUtil                        |
| WP8 | Filter Hot-Path Field Index Pre-computation | Done     | Pre-index field positions at plan time; remove per-row O(n) lookups                 |
| WP9 | Allocation Reduction in Hot Paths           | Done     | RawQueryRow output in AggregationEngine; confirmed FastPojoFilter clone is minimal   |
| WP10| Cache Coherence Hardening                   | Done     | rebuildCache() atomic swap; concurrent test added; reset semantics follow-up in WP13 |
| WP11| Java 25 Modernization                       | Done     | Internal records, sealed filter-expression AST, pattern switches, full tests         |
| WP12| QueryRow Alias Projection Schema Safety     | Done     | Preferred-index hints now verify per-row field names; heterogeneous QueryRow tests  |
| WP13| Stats Plan Cache Reset Semantics            | Done     | `resetStats()` now swaps to an empty cache; runtime/public reset regressions green  |
| WP14| Expression Evaluator Input Validation Contract | Done    | Front-door null/blank validation restored before Caffeine cache access              |
| Release Gate | Release Gate                         | Pending  | Scope decisions made; lint/chart parity cleared; final release guardrails pending    |
| WP15| JDK 25 JFR Chart-Parity Profiling          | Done     | Scatter hotspot fix, JFR harness/recipe, chart parity rerun                          |
| WP16| Virtual-Thread Boundary Evaluation         | Done     | Opt-in Spring `virtual` profiles, smoke coverage, cancellation/pinning audit         |
| WP17| Internal Java 25 Cleanup Pass              | Done     | Internal utility/cursor switch cleanup; targeted regressions; full reactor green     |
| WP18| JDK 25 Runtime Knob Evaluation             | Pending  | Compact headers, generational Shenandoah, AOT cache startup/runtime matrix           |
| WP19| Reusable Report Wrapper Consolidation      | Done     | `ReportDefinition` as shared row-query owner; preset wrappers delegate cleanly       |
| WP20| Query Message Consolidation                | Done     | `SqlLikeFieldMessages`, natural helper dedupe, targeted message-contract tests green  |

---

## Review Findings (2026-04-26)

- WP6-WP10 review found three correctness regressions; all three are now
  resolved in WP12-WP14.
- `SqlLikeExecutionSupport.projectAliasedRows(...)` previously reused the first
  `QueryRow` schema across all rows; WP12 now validates preferred indexes
  against each row and falls back to name lookup when the row-local schema
  differs.
- `FilterExecutionPlanCacheStore.resetStats()` previously preserved entries;
  WP13 now swaps in a fresh empty cache so the next equivalent stats query
  records a miss instead of a hit.
- `SqlExpressionEvaluator` public entry points previously let null and blank
  expressions reach Caffeine; WP14 now restores deterministic
  `IllegalArgumentException("Expression must not be blank")` behavior before
  cache access.
- Review snapshot was `1037/1037`; after WP11-WP15 and the later follow-up
  additions the full reactor is now `1067/1067`, and the remaining release
  work is the final release guardrails.

---

## Release Gate

**Priority:** High
**Goal:** Cut the next release after the WP6-WP10 review follow-ups land and the
performance work is backed by the final guardrails.

**Tasks:**
- [x] Complete WP12 (QueryRow alias projection schema safety).
- [x] Complete WP13 (stats plan cache reset semantics).
- [x] Complete WP14 (expression evaluator input validation contract).
- [x] Decide whether to backfill the missing WP6/WP8/WP9 JMH + threshold work
      before the release cut.
- [x] Decide whether WP11 lands before or after the release cut.
- [x] Update release notes focusing on the Java 25 upgrade, the performance
      work, and the post-review correctness fixes.
- [ ] Run final release guardrails from `RELEASE.md`.
- [ ] Update `ai/state/current-state.md` and `ai/state/handoff.md` after release.

**Current release-cut decisions (2026-04-26):**
- Defer the unimplemented WP6/WP8/WP9 benchmark-backfill tasks until after the
  next release cut. Existing strict core/chart guardrails already cover the
  shipped performance surface, and adding new benchmark suites or threshold
  entries would expand scope while the release gate is blocked elsewhere.
- WP11 landed before the release cut on `2026-04-26`.

**Validate:**
- `mvn -B -ntp test`
- `mvn -B -ntp -Plint verify -DskipTests`
- `scripts/check-doc-consistency.ps1`
- Release benchmark guardrails from `docs/benchmarking.md`.

---

## WP18: JDK 25 Runtime Knob Evaluation

**Priority:** Experimental Runtime Performance
**Goal:** Measure JDK 25 runtime features as deployment guidance rather than as
mandatory code changes.

**Context:**
- JDK 25 ships productized runtime features such as compact object headers and
  generational Shenandoah, plus simpler AOT cache creation and method-profile
  reuse.
- These knobs can improve startup, footprint, or GC behavior without changing
  PojoLens source code, but they need repo-local data before they become
  guidance.
- The benchmark module and Spring examples provide a reasonable place to gather
  comparative startup and throughput numbers.

**Tasks:**
- [ ] Define a small runtime matrix covering default JVM settings, compact
      object headers, generational Shenandoah, and AOT cache startup for the
      benchmark runner and one Spring example app.
- [ ] Execute the matrix with `$env:JAVA_HOME\\bin\\java.exe` and capture
      startup time, heap footprint, and relevant throughput/parity outputs.
- [ ] Decide which knobs are worth documenting in `docs/benchmarking.md` or
      release guidance, and which should remain experimental notes only.
- [ ] Keep all runtime-feature guidance explicitly optional until the data is
      stable across multiple runs and environments.
- [ ] Document platform or tooling assumptions so reruns do not depend on
      unstated local setup.

**Validate:**
- `mvn -B -ntp -Pbenchmark-runner -DskipTests package`
- `scripts/check-doc-consistency.ps1`
