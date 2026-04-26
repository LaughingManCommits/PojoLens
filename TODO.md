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
| WP11| Java 25 Modernization                       | Pending  | Records, sealed AST hierarchy, pattern matching, Stream.toList()                     |
| WP12| QueryRow Alias Projection Schema Safety     | Done     | Preferred-index hints now verify per-row field names; heterogeneous QueryRow tests  |
| WP13| Stats Plan Cache Reset Semantics            | Done     | `resetStats()` now swaps to an empty cache; runtime/public reset regressions green  |
| WP14| Expression Evaluator Input Validation Contract | Done    | Front-door null/blank validation restored before Caffeine cache access              |
| Release Gate | Release Gate                         | Pending  | Release notes; final guardrails; benchmark/WP11 release-cut decisions               |

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
- Review snapshot was `1037/1037`; after WP12-WP14 the suite is `1043/1043`,
  and the remaining release work is release notes, final guardrails, and the
  pending benchmark/WP11 cut decisions.

---

## WP6: Expression Cache Contention Fix

**Priority:** Critical Performance
**Goal:** Replace the global `Collections.synchronizedMap()` LRU in
`SqlExpressionEvaluator` with a Caffeine cache to eliminate global lock
contention on the expression evaluation hot path.

**Context:**
- `SqlExpressionEvaluator` holds two static caches — `TOKEN_CACHE` and
  `COMPILED_CACHE` — backed by `Collections.synchronizedMap(new LinkedHashMap(...))`
  with a custom `removeEldestEntry` override (lines 20–33).
- `Collections.synchronizedMap` wraps the entire map with a single monitor lock.
  Every get, put, and eviction check serializes. Under concurrent query execution
  (e.g. parallel report generation, virtual-thread workloads) all threads contend
  on the same lock.
- Expression evaluation is called per-row for computed columns, making this a
  genuine hot-path bottleneck, not a cold startup cost.
- Caffeine is already a compile dependency (`caffeine 3.x` in `pojo-lens/pom.xml`).

**Tasks:**
- [ ] Replace `TOKEN_CACHE` and `COMPILED_CACHE` in `SqlExpressionEvaluator` with
      `Caffeine.newBuilder().maximumSize(512).build()`.
- [ ] Remove the `Collections.synchronizedMap` + `LinkedHashMap` + `removeEldestEntry`
      pattern entirely.
- [ ] Keep cache size limits (512 entries each) and confirm Caffeine handles
      size-bounded eviction correctly without LRU override.
- [ ] Add a micro-benchmark (JMH, 4 threads) confirming throughput improvement
      under contention; add threshold to `benchmarks/thresholds.json`.
- [ ] Verify all existing `SqlExpressionEvaluator`-touching tests still pass.

**Validate:**
- `mvn -B -ntp -pl pojo-lens "-Dtest=*Expression*Test,*Sql*Test" test`
- `mvn -B -ntp test`

---

## WP7: Reflection Cache Bounds & Safety

**Priority:** High Performance / Memory Safety
**Goal:** Add size bounds to all 9 unbounded global `ConcurrentHashMap` caches in
`ReflectionUtil` to prevent unbounded growth in long-running servers.

**Context:**
- `ReflectionUtil` declares 9 static `ConcurrentHashMap` instances (lines 37–45):
  `MUTABLE_FIELD_CACHE`, `MUTABLE_FIELD_BY_NAME_CACHE`, `READABLE_FIELD_BY_NAME_CACHE`,
  `FIELD_GRAPH_CACHE`, `FIELD_PATH_CACHE`, `FLAT_ROW_READ_PLAN_CACHE`,
  `DIRECT_FIELD_READ_PLAN_CACHE`, `PROJECTION_WRITE_PLAN_CACHE`, `NO_ARG_CTOR_CACHE`.
- None has a size limit. In a server loading many POJO classes over its lifetime
  (hot reload, dynamic report schemas, multi-tenant class loading) these grow
  unboundedly and can contribute to metaspace/heap pressure.
- Additionally, `SqlLikeQuery.preparedExecutions` and
  `NaturalQuery.resolvedExecutions` are per-instance `ConcurrentHashMap` caches
  with no eviction. Queries that receive many distinct parameter sets accumulate
  stale plans indefinitely.

**Tasks:**
- [ ] Replace each of the 9 `ConcurrentHashMap` caches in `ReflectionUtil` with
      Caffeine caches. Suggested max sizes per cache:
      - `MUTABLE_FIELD_CACHE`, `MUTABLE_FIELD_BY_NAME_CACHE`,
        `READABLE_FIELD_BY_NAME_CACHE`, `FIELD_GRAPH_CACHE`,
        `NO_ARG_CTOR_CACHE`: 1 000 entries each (class-keyed, rare eviction).
      - `FIELD_PATH_CACHE`, `FLAT_ROW_READ_PLAN_CACHE`,
        `DIRECT_FIELD_READ_PLAN_CACHE`, `PROJECTION_WRITE_PLAN_CACHE`:
        2 000 entries each (path/plan keyed, higher variety).
- [ ] Replace `preparedExecutions` in `SqlLikeQuery` and `resolvedExecutions` in
      `NaturalQuery` with Caffeine caches bounded at 256 entries each with
      `expireAfterAccess(30, MINUTES)`.
- [ ] Confirm `computeIfAbsent` call sites work with Caffeine's `get(key, loader)`
      equivalent; update all 34 call sites in `ReflectionUtil`.
- [ ] Add a test that loads 1 100 distinct classes and confirms the cache stays
      within its bound (eviction occurs, no OOM).
- [ ] Document the size choices in a comment in `ReflectionUtil`.

**Validate:**
- `mvn -B -ntp test`
- Memory profile: confirm no unbounded growth after 1 000 class loads.

---

## WP8: Filter Hot-Path Field Index Pre-computation

**Priority:** High Performance
**Goal:** Eliminate per-row O(n) `QueryFieldLookupUtil.findFieldIndex()` calls
from the join, group, aggregation, and window hot paths by pre-computing field
index maps at plan-compilation time.

**Context:**
- `QueryFieldLookupUtil.findFieldIndex()` performs a linear scan over a
  `List<QueryField>` by name on every call (lines 15–26).
- It is invoked from `JoinEngine`, `AggregationEngine`, `GroupEngine`, and
  window-stage paths — on every row for every field being accessed.
- With 10 fields and 100 000 rows this is 1 000 000 scans that could be
  1 000 000 array index lookups if field positions were resolved once at
  plan time.
- A `preferredIndex` fast-path already exists in `findFieldValue()` (line 36)
  but is only used where callers happen to know the index. The structural fix is
  to compute and cache a `Map<String, Integer>` field index at plan compilation
  and pass it to the engine.

**Tasks:**
- [ ] Add a `FieldIndexMap` helper (or extend `FilterExecutionPlan`) that builds
      a `String → int` index from a `List<QueryField>` once at plan time.
- [ ] Update `JoinEngine` to resolve join-key field positions from `FieldIndexMap`
      at plan init, not per-row.
- [ ] Update `AggregationEngine` to resolve metric and group field positions at
      plan init.
- [ ] Update `GroupEngine` to use pre-indexed positions.
- [ ] Update window-stage field resolution similarly.
- [ ] Ensure `preferredIndex` fast-path in `findFieldValue()` is used consistently
      throughout engine calls; remove direct `findFieldIndex()` calls from loops.
- [ ] Add a JMH benchmark confirming join + group throughput improvement at
      10 000 rows with 10 fields; add threshold entry.
- [ ] Confirm all existing join, group, and aggregation tests still pass.

**Validate:**
- `mvn -B -ntp -pl pojo-lens "-Dtest=*Join*Test,*Group*Test,*Agg*Test,*Window*Test" test`
- `mvn -B -ntp test`
- JMH join + group benchmark within threshold.

---

## WP9: Allocation Reduction in Hot Paths

**Priority:** Moderate Performance
**Goal:** Reduce unnecessary object allocation and boxing on the filter, projection,
and group-key hot paths to lower GC pressure on large repeated workloads.

**Context — three confirmed allocation hotspots:**

1. **Array cloning in FastPojoFilterSupport/FastPojoStreamSupport** (lines ~224
   and ~216 respectively): `values.clone()` is called per-row in the tight filter
   loop. The clone allocates a new `Object[]` for every row that passes the filter.
   Reusing a pre-allocated output buffer (written then immediately converted to
   `QueryRow`) would eliminate these allocations.

2. **Boxing wrappers in ObjectUtil** (lines ~143–172): `Integer.valueOf(n.intValue())`,
   `Long.valueOf(n.longValue())`, and `Double.valueOf(n.doubleValue())` are called
   for every numeric type cast in projection/compute. For numeric workloads with
   many computed fields this creates significant boxing churn. Java's integer cache
   covers [-128,127] but typical report values exceed this range.

3. **GroupEngine `toExternalKey()` string allocation**: `QueryKey.toExternalKey()`
   builds a new `String` for every group key during GROUP BY output serialization.
   The `QueryKey` already implements `equals`/`hashCode` correctly and can be used
   as a map key directly — the string form is only needed at serialization time.

**Tasks:**
- [ ] In `FastPojoFilterSupport.tryFilterRows()` and `FastPojoStreamSupport`,
      pre-allocate a reusable `Object[]` scratch buffer per invocation (sized to
      max field count) and write row values into it; create `QueryRow` from the
      buffer view without cloning on each row.
- [ ] In `ObjectUtil` numeric cast paths, verify whether the returned `Object` is
      immediately stored or compared. Where the result feeds back into a numeric
      comparison (not stored long-term), replace `Integer.valueOf` + `cls.cast`
      with a direct comparison on the unboxed value to avoid wrapper allocation.
- [ ] Audit `GroupEngine` result serialization — confirm `toExternalKey()` is only
      called at output time, not during GROUP BY key building. If called during
      building, switch to using `QueryKey` directly as the map key.
- [ ] Add a JMH benchmark measuring allocation rate (via `-prof gc`) on a 50 000
      row filter + group pipeline before and after; confirm improvement.

**Validate:**
- `mvn -B -ntp test`
- JMH gc-profiled benchmark shows reduced `gc.alloc.rate` on filter+group path.

---

## WP10: Cache Coherence Hardening

**Priority:** Moderate Correctness
**Goal:** Fix two known cache coherence gaps: the `FilterExecutionPlanCacheStore`
rebuild race window, and the lack of bounds on per-query execution caches.

**Context:**

1. **rebuildCache() race window** (`FilterExecutionPlanCacheStore.java` lines 169–175):
   ```java
   private void rebuildCache() {
       synchronized (mutationLock) {
           Map<...> entries = new LinkedHashMap<>(cache.asMap());
           cache = newCache();        // ← new cache is empty here
           cache.putAll(entries);     // ← entries restored after a gap
       }
   }
   ```
   Between `cache = newCache()` and `cache.putAll(entries)`, concurrent `getOrBuild`
   calls see an empty cache and will all miss, triggering redundant plan rebuilds.
   More critically, `getOrBuild` reads `cache` via the volatile field without
   holding `mutationLock`, so it can observe the empty-cache state during rebuild.
   `resetStats()` calls `rebuildCache()` which means a stats reset can cause a
   transient plan-cache miss storm.

2. **Unbounded preparedExecutions/resolvedExecutions** (`SqlLikeQuery` and
   `NaturalQuery`): each instance caches prepared execution plans in an unbounded
   `ConcurrentHashMap`. A query object reused across many different parameter sets
   (e.g. in a long-lived Spring bean or test) accumulates stale entries. This is
   partially addressed in WP7 but the per-instance concern is separate from the
   global `ReflectionUtil` concern.

**Tasks:**
- [ ] Redesign `rebuildCache()` using an atomic-swap pattern: build the new cache
      fully (including `putAll`) before swapping the `volatile` reference, so
      readers never see an empty intermediate state.
      ```java
      private void rebuildCache() {
          synchronized (mutationLock) {
              Cache<...> next = newCache();
              next.putAll(cache.asMap());   // populate before swap
              cache = next;                 // atomic volatile write
          }
      }
      ```
- [ ] Verify that `getOrBuild` reading `cache` outside `mutationLock` is safe
      after the atomic-swap fix (volatile read gives a fully-populated cache).
- [ ] Separate `resetStats()` from `rebuildCache()`: stats reset should create a
      new cache without copying existing entries (the point of a stats reset is to
      start fresh, not to re-populate).
- [ ] Bound `preparedExecutions` in `SqlLikeQuery` and `resolvedExecutions` in
      `NaturalQuery` (coordinate with WP7 if that work package also covers these).
- [ ] Add a concurrent test: multiple threads hit `getOrBuild` while a config
      change triggers `rebuildCache()`; assert no thread ever gets a null plan.

**Validate:**
- `mvn -B -ntp test`
- Stress test: concurrent plan cache read + `rebuildCache()` with 20 threads,
  1 000 iterations; assert 0 null-plan results.

---

## WP11: Java 25 Modernization

**Priority:** Quality / Maintainability
**Goal:** Apply Java 25 language features to reduce boilerplate, improve
readability, enable compiler-exhaustiveness checks, and modernize the style of
the core engine internals.

**Context — confirmed candidates:**

1. **Records for internal value types** — several small final classes with all-final
   fields and no business logic are record candidates:
   - `CompiledRule` (4 fields: `compareValue`, `clause`, `separator`, `dateFormat`)
   - `AggregationEngine.NumericStats` (accumulator fields, all primitives/Numbers)
   - `AggregationEngine.GroupAccumulator` (2 fields)
   - `OrderEngine.OrderColumn` / `GroupColumn` in `FilterExecutionPlan`

2. **Sealed AST hierarchy** — `FilterAst`, `SelectFieldAst`, `OrderAst`, and
   similar abstract base classes in `sqllike/ast/` have a fixed set of permitted
   subtypes. Sealing them enables exhaustive `switch` expressions and removes the
   `default` fallback defensive branches.

3. **Pattern matching** — `instanceof` checks followed by explicit casts throughout
   `FastPojoFilterSupport`, `ObjectUtil`, `SqlExpressionEvaluator`, and engine
   internals can be replaced with binding patterns.

4. **Stream.toList()** — `.collect(Collectors.toList())` calls throughout parser,
   binder, and plan-builder paths should become `.toList()`.

5. **Switch expressions** — multi-branch `if/else if` chains over enum/string
   constants in `ObjectUtil`, `Clauses` dispatch, and filter-operator routing
   should become switch expressions.

**Tasks:**
- [ ] Convert `CompiledRule`, `NumericStats`, `GroupAccumulator`, `OrderColumn`,
      `GroupColumn` to records. Validate compact constructor validation where null
      checks are currently in the constructor body.
- [ ] Seal `FilterAst` and its concrete subtypes in `sqllike/ast/`; update all
      `instanceof` dispatch sites to use exhaustive `switch` expressions.
- [ ] Seal `SelectFieldAst` and `OrderAst` similarly if their subtype sets are
      fully known.
- [ ] Replace `instanceof X x2` pattern with binding patterns throughout engine
      internals (search for `instanceof` + subsequent cast on the same variable).
- [ ] Replace `.collect(Collectors.toList())` with `.toList()` throughout
      `pojo-lens` main sources.
- [ ] Replace multi-branch `if/else if` enum dispatches with switch expressions
      where all branches are covered; remove unreachable `default` fallbacks.
- [ ] Run full test suite to confirm no behavioural change.
- [ ] Update `ai/core/agent-invariants.md` if any sealed hierarchy changes public
      API shape (sealed interfaces on public types need careful compat review).

**Validate:**
- `mvn -B -ntp test`
- `mvn -B -ntp -Plint verify -DskipTests`
- `scripts/check-doc-consistency.ps1`

---

## WP12: QueryRow Alias Projection Schema Safety

**Priority:** High Correctness
**Goal:** Restore correct aliased projection for `QueryRow` sources even when
row-local field order differs between rows.

**Context:**
- `SqlLikeExecutionSupport.projectAliasedQueryRows()` now builds one
  `Map<String,Integer>` from the first `QueryRow` and reuses those indexes for
  every later row.
- `resolveIndexedQueryRowValues()` then reads later rows with `row.getValueAt(idx)`
  based on the first row's field order rather than the current row's field name.
- Review repro: first row `[a=1,b=2]`, second row `[b=20,a=10]`, `select a,b`
  returns `20,10` for the second row instead of `10,20`.
- Previous behavior used per-row name lookup and stayed correct for
  heterogeneous `QueryRow` schemas.

**Tasks:**
- [x] Redesign `projectAliasedQueryRows()` so correctness does not depend on the
      first row's field order.
- [x] Keep a fast path only when schema uniformity is proven per row or
      normalized up front.
- [x] Add a regression test covering same-field/different-order `QueryRow`
      inputs for aliased projection.
- [x] Add a regression test covering computed-field identifier resolution over
      heterogeneous `QueryRow` field order.
- [x] Re-run row-projection and SQL-like alias coverage after the fix.

**Validate:**
- `mvn -B -ntp -pl pojo-lens "-Dtest=SqlLikeAliasTest,SqlLikeMappingParityTest,SqlLikeQueryContractTest" test`
- `mvn -B -ntp test`

---

## WP13: Stats Plan Cache Reset Semantics

**Priority:** Moderate Correctness
**Goal:** Make `FilterExecutionPlanCacheStore.resetStats()` a true fresh-start
operation that clears counters and cached entries.

**Context:**
- `FilterExecutionPlanCacheStore.resetStats()` still delegates to
  `rebuildCache()`.
- `rebuildCache()` now uses the safe atomic-swap pattern, but it still copies
  `cache.asMap()` into the new cache before the swap.
- Review repro: cache size remains `1` immediately after `resetStats()`, and
  the next identical stats query records a hit instead of a miss.
- TODO and AI state already claimed fresh-start semantics, so the backlog and
  memory now overstate the implementation.

**Tasks:**
- [x] Split `resetStats()` from `rebuildCache()` with a direct `cache = newCache()`
      under `mutationLock`, without copying existing entries.
- [x] Add a regression test asserting `size()==0` immediately after
      `resetStats()`.
- [x] Add a regression test asserting the next identical stats query records a
      miss, not a hit, after `resetStats()`.
- [x] Cover the runtime/public cache controls that expose stats-plan-cache reset
      semantics.
- [x] Reconcile TODO and AI notes once the implementation matches the contract.

**Validate:**
- `mvn -B -ntp -pl pojo-lens "-Dtest=CachePolicyConfigTest,CacheConcurrencyTest,PublicApiCacheCoverageTest" test`
- `mvn -B -ntp test`

---

## WP14: Expression Evaluator Input Validation Contract

**Priority:** Moderate Correctness / API Consistency
**Goal:** Restore deterministic null/blank expression validation in
`SqlExpressionEvaluator` before any Caffeine cache lookup.

**Context:**
- `compileNumeric()` and `tokensFor()` now call `Cache.get(...)` directly.
- Caffeine rejects null keys with `NullPointerException`.
- Review repro: `compileNumeric(null)` now throws `NullPointerException`
  instead of the previous validation-oriented `IllegalArgumentException`.
- The same regression affects `collectIdentifiers(...)`,
  `rewriteIdentifiers(...)`, and `evaluateNumeric(...)`.

**Tasks:**
- [x] Validate null/blank expressions before any cache access in all public
      `SqlExpressionEvaluator` entry points.
- [x] Preserve one consistent exception type/message for null and blank
      expression inputs.
- [x] Add regression tests for `compileNumeric`, `collectIdentifiers`,
      `rewriteIdentifiers`, and `evaluateNumeric`.
- [x] Confirm the Caffeine caches remain in the hot path after the front-door
      validation.

**Validate:**
- `mvn -B -ntp -pl pojo-lens "-Dtest=SqlExpressionEvaluatorTest" test`
- `mvn -B -ntp test`

---

## Release Gate

**Priority:** High
**Goal:** Cut the next release after the WP6-WP10 review follow-ups land and the
performance work is backed by the final guardrails.

**Tasks:**
- [x] Complete WP12 (QueryRow alias projection schema safety).
- [x] Complete WP13 (stats plan cache reset semantics).
- [x] Complete WP14 (expression evaluator input validation contract).
- [ ] Decide whether to backfill the missing WP6/WP8/WP9 JMH + threshold work
      before the release cut.
- [ ] Decide whether WP11 lands before or after the release cut.
- [ ] Update release notes focusing on the Java 25 upgrade, the performance
      work, and the post-review correctness fixes.
- [ ] Run final release guardrails from `RELEASE.md`.
- [ ] Update `ai/state/current-state.md` and `ai/state/handoff.md` after release.

**Validate:**
- `mvn -B -ntp test`
- `mvn -B -ntp -Plint verify -DskipTests`
- `scripts/check-doc-consistency.ps1`
- Release benchmark guardrails from `docs/benchmarking.md`.
