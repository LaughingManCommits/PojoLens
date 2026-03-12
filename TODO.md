# Performance Review And Fix Plan

## Summary

Most of the originally planned performance work is now reflected in the codebase. After reconciliation on 2026-03-11, the primary clearly-open engine item is still eager full flattening/selective materialization.

Current focus:

1. `HotspotMicroJmhBenchmark` has already confirmed the current flattening, projection, cache-hit, and grouped-aggregation budgets on 2026-03-11.
2. WP5 selective/lazy materialization is now in progress and should stay the main implementation item until the remaining conservative fallbacks are either optimized or intentionally kept.

Review update (2026-03-10):

- WP1, WP2, WP3, and WP6 removed real redundant work and should stay.
- They do not yet put the engine at a maximum-performance ceiling.
- The remaining dominant costs are still eager row materialization, partial execution-plan compilation, repeated grouped-metric scans, and avoidable row identity/materialization churn.
- A quick local Streams-baseline spot check still showed a large gap on comparable workloads, so the completed packages should be treated as incremental wins rather than end-state performance.

Reconciliation update (2026-03-11):

- WP4, WP7, WP8, WP9, WP10, WP11, and WP12 are already implemented in code and should be treated as completed.
- WP13 is now implemented via `HotspotMicroJmhBenchmark`, `scripts/benchmark-suite-hotspots.args`, and updated benchmarking docs.
- WP5 remains the primary open performance redesign.
- The historical findings below are kept for traceability, but current status and sequence decisions should use this reconciliation and the work-package statuses below.

Execution update (2026-03-11, later):

- Forked hotspot JMH with `-prof gc` confirmed that flattening/materialization is still the dominant allocation cost:
  - `HotspotMicroJmhBenchmark.reflectionToDomainRows` at `size=10000`: about `563 us/op`, about `2.84 MB/op`
  - `HotspotMicroJmhBenchmark.reflectionToClassList` at `size=10000`: about `966 us/op`, about `1.40 MB/op`
  - `HotspotMicroJmhBenchmark.groupedMultiMetricAggregation` at `size=10000`: about `355 us/op`, about `654 KB/op`
  - `HotspotMicroJmhBenchmark.statsPlanCacheHit` at `size=10000`: about `0.004 us/op`
- This was enough to justify starting WP5 instead of spending more time on cache-hit or grouped-aggregation tuning first.
- WP5 work completed so far:
  - source POJO rows now materialize lazily instead of being flattened eagerly in the builder constructor
  - ordinary fluent queries selectively materialize only the source fields needed by filters, distinct, order, group, return fields, metrics, time buckets, and join keys
  - pending join child sources materialize lazily, with selective join materialization enabled only for the safe case of a single non-colliding join shape
  - non-join computed-field queries now expand only the base-field dependency graph required by referenced computed fields instead of forcing full source flattening
- Conservative full-materialization fallback still remains for:
  - computed-field queries that also use joins
  - explicit rule-group queries (`allOf`, `anyOf`, HAVING group variants)
  - open-ended full-row outputs
  - multi-join or schema-collision join shapes
- Latest verification on 2026-03-11:
  - targeted tests passed for `FilterQueryBuilderSelectiveMaterializationTest`, `ComputedFieldRegistryTest`, `PojoLensJoinBehaviorTest`, `FilterCoreTest`, `PublicApiCoverageTest`, `SqlLikeJoinTest`, `SqlLikeQueryContractTest`, `SqlLikeMappingParityTest`, and `PojoLensConcurrencyBehaviorTest`
  - `mvn -q -DskipTests test-compile` passed

## Historical Findings

### 1. Repeated deep-copy of query rows

Impact: high allocation pressure and avoidable CPU time.

Observed in:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/builder/QuerySpec.java`

Details:

- `initFilter()` snapshots the builder.
- `FilterImpl.filter()` snapshots again before execution.
- `FilterImpl.filterGroups()` also snapshots again.
- Stats paths snapshot again for aggregate/HAVING branches.
- `QuerySpec.deepCopy()` deep-copies every `QueryRow` and `QueryField`.

This means a single query can clone the full row payload two or more times before doing useful work.

### 2. Reflection path resolution repeated during result mapping

Impact: high for typed result workloads.

Observed in:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Details:

- `toClassList()` calls `getFieldType()` for each output field.
- `setFieldValue()` resolves the same field path again.
- `resolveFieldPath()` splits the path and rebuilds the field list every time.

So each projected field can pay the reflection-path lookup cost twice in the same operation.

### 3. Eager full flattening of source objects

Impact: high for large datasets, especially when queries touch few fields.

Observed in:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/filter/JoinEngine.java`

Details:

- The builder constructor immediately calls `ReflectionUtil.toDomainRows(pojos)`.
- `toDomainRows()` scans every bean and allocates:
  - a `HashMap` for collected values,
  - an `IdentityHashMap`-backed set for cycle detection,
  - one `QueryRow`,
  - one `QueryField` per queryable field.
- Join sources are flattened the same way.

This is usually a single scan, but it is a heavy eager scan with a lot of object churn.

### 4. HAVING flow rebuilds execution plan on aggregated rows

Impact: medium.

Observed in:

- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/filter/FilterCore.java`

Details:

- `FilterImpl` builds a plan for aggregated rows.
- `filterHavingFields()` builds another plan again internally.

This is avoidable repeated work inside a single stats operation.

### 5. UUID generation is expensive on hot paths

Impact: medium.

Observed in:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`

Details:

- `newUUID()` uses substring, regex, parse, and replace operations per character.
- It is called for rows and rule ids.

This is more allocation-heavy than necessary.

### 6. Builder-time validation rescans materialized rows

Impact: medium.

Observed in:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`

Details:

- `ensureNumericField()` scans rows to find a non-null sample.
- `ensureDateField()` does the same.
- Repeated metric or bucket configuration can trigger repeated linear scans.

### 7. Execution-plan reuse is still only partial

Impact: high for grouped, stats, order, and distinct-heavy workloads.

Observed in:

- `src/main/java/laughing/man/commits/filter/FilterExecutionPlan.java`
- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/filter/FilterCore.java`
- `src/main/java/laughing/man/commits/filter/OrderEngine.java`
- `src/main/java/laughing/man/commits/filter/GroupEngine.java`
- `src/main/java/laughing/man/commits/filter/AggregationEngine.java`

Details:

- `FilterExecutionPlan` currently compiles field indexes and WHERE rules only.
- `filterDistinctFields()` still sorts configured keys and resolves field indexes per execution.
- `orderByFields()`, `groupByFields()`, and `aggregateMetrics()` rebuild ordered column descriptors every run.
- Stats-plan caching also pays for a large serialized key in `planCacheKey()` on every invocation.

### 8. Grouped aggregation still rescans rows once per metric

Impact: high when grouped queries carry multiple metrics or large time buckets.

Observed in:

- `src/main/java/laughing/man/commits/filter/AggregationEngine.java`

Details:

- Grouped aggregation first stores all matching rows per group.
- It then calls `calculateMetricValue()` once per configured metric.
- `SUM`, `AVG`, `MIN`, and `MAX` each trigger another full pass over the grouped rows.

This prevents grouped/time-bucket queries from approaching a single-pass ceiling.

### 9. Row ids are still allocated on hot paths where they are not semantically required

Impact: medium-high allocation pressure.

Observed in:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/filter/AggregationEngine.java`
- `src/main/java/laughing/man/commits/filter/JoinEngine.java`
- `src/main/java/laughing/man/commits/filter/FilterCore.java`
- `src/main/java/laughing/man/commits/filter/GroupEngine.java`

Details:

- Every source row gets a string id during `toDomainRows()`.
- Aggregate rows and joined rows allocate fresh ids again.
- The main current consumer is grouped display-field correlation, which can likely use positional correlation or a lighter identity representation.

WP6 made generation cheaper, but it did not remove the underlying allocation volume.

### 10. Typed result materialization still has uncached constructor/setup work

Impact: medium for projection-heavy workloads.

Observed in:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Details:

- `toClassList()` still calls `cls.getDeclaredConstructor().newInstance()` per row.
- The per-call `resolvedFieldsByName` map is rebuilt for each projection batch.
- There is no dedicated projection/materialization benchmark proving this stage is now cheap enough.

### 11. Benchmark coverage still misses the hottest conversion costs

Impact: medium, because regressions can hide behind end-to-end budgets.

Observed in:

- `src/main/java/laughing/man/commits/benchmark`
- `docs/benchmarking.md`

Details:

- The current JMH suites cover end-to-end pipelines, charts, explain, and cache concurrency.
- There is no dedicated JMH coverage for `toDomainRows()`, `toClassList()`, stats-plan cache-hit overhead, or allocation rate via `-prof gc`.
- That makes it too easy to mark packages complete without proving ceiling performance on the hottest remaining conversion paths.

## Review Of Completed Work Packages

- WP1 switched execution snapshots to row-reference copies for normal execution paths and retained deep copies only for explicit deep-copy flows.
- WP2 now includes field-path caching, cached projection write plans, and cached no-arg constructors in the materialization path.
- WP3 reuses prepared plans within the stats/HAVING flow where the schema is unchanged.
- WP4 landed via `FieldGraphDescriptor` and direct ordered extraction in `ReflectionUtil.toDomainRows()`.
- WP6 replaced the old UUID formatter with a direct alpha-hex writer.
- WP7 now validates numeric/date fields from cached schema metadata instead of rescanning rows.
- WP8 compiles distinct/order/group/metric metadata in `FilterExecutionPlan`, and downstream engines consume that metadata directly.
- WP9 uses per-group metric accumulators instead of per-metric rescans.
- WP10 makes row ids optional on the hot filter/group/projection/aggregation/join paths, with tests covering that behavior.
- WP11 caches projection write plans and no-arg constructors for typed materialization.
- WP12 uses structural `FilterExecutionPlanCacheKey` objects plus shape-version reuse in `FilterImpl`.
- WP13 adds direct hotspot microbenchmarks and local `-prof gc` guidance.

## Recommended Fix Order

1. Finish or explicitly freeze the remaining WP5 scope:
   - decide whether computed-field plus join queries should keep the conservative full-materialization fallback, or
   - extend selective materialization to that path if benchmark data says it matters.
2. Rerun the new computed-field hotspot together with end-to-end suites to capture the allocation delta from the current WP5 slice and determine whether a stable budget is emerging.
3. Promote stable hotspot allocation budgets into threshold/config files once they survive repeated local runs.

## Work Packages

### WP1: Remove repeated execution snapshots

Status: completed

Goal: a single logical query should not deep-copy all rows multiple times.

Scope:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/builder/QuerySpec.java`

Tasks:

- Decide the real mutability boundary for execution.
- Keep config snapshots if needed, but stop cloning row payloads unless a stage truly mutates rows.
- Remove the redundant snapshot between `initFilter()` and `filter()/filterGroups()`.
- Rework stats branches so aggregate/HAVING paths reuse lightweight builders or immutable execution state instead of cloning full row trees.

Acceptance criteria:

- Non-join fluent queries do not deep-copy all `QueryRow` objects more than once per execution.
- Repeated calls to `filter()` on the same builder still behave correctly.
- Existing concurrency behavior tests still pass.

### WP2: Cache field path resolution for output mapping

Status: completed

Goal: resolve each reflective field path once and reuse it within the operation and across operations.

Scope:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Tasks:

- Add a cache keyed by `(rootClass, fieldPath)` that stores:
  - resolved `FieldPath`,
  - target leaf type,
  - optionally a setter plan.
- Update `toClassList()` to resolve the path once per field and reuse it for both type lookup and assignment.
- Reuse the same cached path for `getFieldValue()` and `setFieldValue()` where possible.

Acceptance criteria:

- `toClassList()` no longer calls path resolution twice per field.
- Nested field materialization still works.
- Existing nested path tests still pass.

### WP3: Reuse execution plans within a single stats operation

Status: completed

Goal: avoid rebuilding `FilterExecutionPlan` for the same schema and query shape during one run.

Scope:

- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/filter/FilterCore.java`

Tasks:

- Add overloads so `filterHavingFields()` accepts a prepared plan.
- Reuse the already-built aggregated-row plan in HAVING and ORDER BY stages.
- Check whether non-stats grouped flows can also pass the same plan through consistently.

Acceptance criteria:

- HAVING filtering does not rebuild the plan when one is already available.
- Stats query behavior is unchanged.

### WP4: Introduce schema-level row extraction plan

Status: completed

Goal: stop rediscovering how to flatten beans for every row.

Scope:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Tasks:

- Build a cached schema descriptor that contains:
  - queryable field names,
  - resolved extraction paths,
  - leaf types,
  - exclusion decisions.
- Replace `collectFieldValueMap()` plus per-row `HashMap` assembly with direct extraction into ordered field slots.
- Avoid building a temporary map when the final output is already an ordered field list.

Acceptance criteria:

- Flattening a bean does not allocate an intermediate `Map<String, Object>` per row.
- Queryable field order remains stable.
- Nested object traversal and cycle detection remain correct.

### WP5: Evaluate lazy or selective materialization for fluent queries

Status: in progress

Goal: avoid flattening data that the query will never touch.

Scope:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- query execution pipeline classes under `src/main/java/laughing/man/commits/filter`

Tasks:

- Decide whether to:
  - keep `QueryRow` but materialize only referenced fields, or
  - keep source beans and use a compiled accessor plan for filtering/grouping/order stages.
- Prototype a minimal selective materialization path for:
  - filter fields,
  - distinct fields,
  - order fields,
  - group fields,
  - return fields,
  - metric fields,
  - join keys.
- Compare complexity against the current eager flattening design.

Progress update (2026-03-11):

- Implemented:
  - lazy source-bean retention in `FilterQueryBuilder`
  - selective source-field materialization for ordinary fluent queries
  - safe single-join lazy/selective join-source materialization
  - computed-field dependency expansion for non-join selective materialization
- Verified:
  - targeted fluent, join, computed-field, SQL-like parity, and concurrency suites pass
  - compile-only Maven verification passes
- Remaining:
  - decide whether computed-field plus join flows should stay on the current safe full fallback or receive a selective path
  - rerun end-to-end benchmarks and capture before/after allocation deltas for the implemented WP5 slice

Progress update (2026-03-12):

- Implemented:
  - safe single-join computed-field queries now retain selective parent and child materialization when schemas remain collision-free
  - `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` was added and wired into `scripts/benchmark-suite-hotspots.args` plus `docs/benchmarking.md`
  - `PojoLensJoinJmhBenchmark` now includes `pojoLensJoinLeftComputedField` plus `manualHashJoinLeftComputedField` for end-to-end comparison of the WP5 computed-field single-join path
  - `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` is now wired into `scripts/benchmark-suite-main.args` with conservative cold-run thresholds in `benchmarks/thresholds.json`
- Verified:
  - full `mvn -q test` passed after the latest WP5 builder and benchmark changes
  - a forked `-prof gc` hotspot run captured the new computed-field join path at about `37.9 us/op` and about `363,824 B/op` for `size=1000`
  - the same run captured about `361.5 us/op` and about `3,531,826 B/op` for `size=10000`
  - a forked `-prof gc` end-to-end run captured `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at about `0.335 ms/op` and about `2,138,298 B/op` for `size=1000`
  - the same end-to-end run captured about `3.750 ms/op` and about `20,981,581 B/op` for `size=10000`
  - the same end-to-end run captured `manualHashJoinLeftComputedField` at about `0.009 ms/op` / `84,512 B/op` for `size=1000` and about `0.097 ms/op` / `927,128 B/op` for `size=10000`
  - a second forked `-prof gc` end-to-end run stayed at about `0.335 ms/op` / `2,138,338 B/op` for `size=1000` and about `3.589 ms/op` / `20,981,552 B/op` for `size=10000`
  - repeated isolated cold guardrail-style runs for `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` stayed around `57.6-57.9 ms/op` for `size=1000` and `166.9-169.8 ms/op` for `size=10000`
  - the full strict core benchmark suite passed with `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at about `109.706 ms/op` for `size=1000` and about `160.863 ms/op` for `size=10000`, and `BenchmarkThresholdChecker --strict` passed with conservative budgets of `250 ms/op` / `500 ms/op`
- Remaining:
  - rerun the hotspot enough times to decide whether its allocation numbers are stable enough for a threshold or should remain documented as a local diagnostic
  - decide later whether the new end-to-end guardrail budgets should be tightened once more machines and runs are available

Acceptance criteria:

- Large-query benchmarks show lower allocation rate when only a subset of fields is used.
- API behavior and result ordering remain unchanged.

Current evidence:

- Targeted behavior coverage currently supports the second acceptance criterion for the implemented slices.
- The first acceptance criterion is now partially supported by repeated hotspot diagnostics and repeated end-to-end computed-field join runs, including new conservative strict-suite thresholds for the end-to-end path. The hotspot path still remains diagnostic-only.

### WP6: Replace expensive UUID generation

Status: completed

Goal: reduce avoidable string churn on row/rule id creation.

Scope:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Tasks:

- Replace regex/substrings with a direct character loop, or
- replace the custom format entirely with a cheaper monotonic/request-scoped id strategy if uniqueness requirements allow it.

Acceptance criteria:

- No regex or substring calls inside the hot id generator path.
- Existing assumptions about identifier safety still hold.

### WP7: Cache field type metadata for builder validation

Status: completed

Goal: stop scanning rows repeatedly during metric/date validation.

Scope:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Tasks:

- Expose cached field type metadata from reflection/schema descriptors.
- Use field metadata for `ensureFieldExists()`, `ensureNumericField()`, and `ensureDateField()`.
- Fall back only when runtime-computed fields require runtime inspection.

Acceptance criteria:

- Adding multiple metrics and time buckets does not rescan all rows for each check.
- Validation errors remain correct and deterministic.

### WP8: Compile full execution-plan metadata

Status: completed

Goal: stop rebuilding distinct/order/group/time-bucket/metric accessors on every execution.

Scope:

- `src/main/java/laughing/man/commits/filter/FilterExecutionPlan.java`
- `src/main/java/laughing/man/commits/filter/FilterCore.java`
- `src/main/java/laughing/man/commits/filter/OrderEngine.java`
- `src/main/java/laughing/man/commits/filter/GroupEngine.java`
- `src/main/java/laughing/man/commits/filter/AggregationEngine.java`
- `src/main/java/laughing/man/commits/filter/FilterImpl.java`

Tasks:

- Extend `FilterExecutionPlan` to precompute:
  - distinct field indexes in stable order,
  - order columns with effective date formats,
  - group/time-bucket columns,
  - metric field indexes and aliases,
  - optionally HAVING rule bindings and explicit-group field lookups.
- Make `filterDistinctFields()`, `orderByFields()`, `groupByFields()`, and `aggregateMetrics()` consume plan metadata instead of rebuilding it.
- Reuse normalized query-shape data for cache lookup instead of recomputing from raw builder maps each run.

Acceptance criteria:

- No `TreeSet` or repeated column-resolution work remains in the distinct/order/group/aggregate hot paths.
- Grouped/stats queries build these descriptors once per query shape.
- Existing grouping/order/HAVING tests still pass.

### WP9: Fuse grouped metric aggregation into a single pass

Status: completed

Goal: compute all metrics while grouping instead of rescanning each group for each metric.

Scope:

- `src/main/java/laughing/man/commits/filter/AggregationEngine.java`

Tasks:

- Replace `GroupAccumulator.rows` plus per-metric rescans with per-group metric accumulators.
- Update `COUNT`, `SUM`, `AVG`, `MIN`, and `MAX` in one row pass.
- Preserve current null handling and numeric semantics.
- Only materialize per-group source row lists when a downstream feature truly needs them.

Acceptance criteria:

- Grouped/time-bucket queries scan each input row at most once for aggregation work.
- Adding extra metrics does not add another full pass over grouped rows.
- Existing grouped-metric and time-bucket parity tests still pass.

### WP10: Eliminate hot-path row id churn

Status: completed

Goal: stop allocating string row ids for pipelines that do not need them.

Scope:

- `src/main/java/laughing/man/commits/domain/QueryRow.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/filter/FilterCore.java`
- `src/main/java/laughing/man/commits/filter/GroupEngine.java`
- `src/main/java/laughing/man/commits/filter/AggregationEngine.java`
- `src/main/java/laughing/man/commits/filter/JoinEngine.java`

Tasks:

- Decide whether row identity should be optional, numeric, or replaced by positional correlation in grouping/projection flows.
- Remove eager id creation from `toDomainRows()` for simple filter/order/projection paths.
- Avoid generating fresh ids for aggregate rows unless an API contract truly requires them.

Acceptance criteria:

- Non-join, non-grouped typed queries do not allocate per-row string ids.
- Grouping correctness no longer depends on comparing generated UUID-like strings.
- Existing behavior tests still pass.

### WP11: Optimize typed projection/materialization path

Status: completed

Goal: lower remaining reflection overhead in `toClassList()`.

Scope:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Tasks:

- Use cached no-arg constructors for root projection types.
- Precompile and cache projection write plans keyed by `(projectionClass, sourceFieldSchema)`.
- Reuse resolved field plans across batches instead of rebuilding a per-call `Map<String, ResolvedFieldPath>`.

Acceptance criteria:

- Result materialization does not do repeated constructor lookup per row.
- Nested projection still works.
- Dedicated projection/materialization benchmarks show measurable improvement.

### WP12: Replace serialized stats-plan cache keys with structural query-shape keys

Status: completed

Goal: make stats-plan caching cheaper than the plan build it is avoiding.

Scope:

- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/filter/FilterExecutionPlanCacheStore.java`
- a new immutable key type under `src/main/java/laughing/man/commits/filter`

Tasks:

- Replace large string assembly in `planCacheKey()` with an immutable key object that stores normalized ordered config.
- Compute or cache the key once per execution snapshot or once per config mutation.
- Measure cache-hit overhead against the current string-serialization approach.

Acceptance criteria:

- The cache-hit path does not sort and stringify all config maps on every execution.
- Repeated grouped/stats queries are cheaper with the cache enabled than with it disabled.
- Cache snapshot/telemetry behavior remains intact.

### WP13: Add hotspot microbenchmarks and allocation guardrails

Status: completed

Goal: measure the paths that the current end-to-end benchmark suite hides.

Scope:

- benchmark classes under `src/main/java/laughing/man/commits/benchmark`
- `docs/benchmarking.md`
- benchmark threshold/config files where stable

Tasks:

- Add dedicated JMH benchmarks for:
  - `ReflectionUtil.toDomainRows()`,
  - `ReflectionUtil.toClassList()`,
  - stats-plan cache-hit paths,
  - grouped metrics with multiple aggregates.
- Add local guidance for running these with `-prof gc` and capture allocation budgets where stable.
- Document how the microbenchmarks complement the existing end-to-end suites.

Acceptance criteria:

- The benchmark suite can isolate flattening, projection, cache-hit, and multi-metric aggregation costs.
- Future completed performance packages have a direct measurement path.

## Benchmark Plan

Use the existing JMH benchmarks to validate the changes:

- `PojoLensPipelineJmhBenchmark`
- `PojoLensJmhBenchmark`
- `PojoLensJoinJmhBenchmark`
- `StatsQueryJmhBenchmark`
- `CacheConcurrencyJmhBenchmark`
- `HotspotMicroJmhBenchmark` for row flattening, typed projection, stats-plan cache hits, and multi-metric grouped aggregation
- use `-prof gc` during hotspot tuning where the environment is stable enough to compare allocations

Track:

- average time,
- allocation rate if available,
- effect of cache warmup,
- differences between flat, grouped, join, and stats workloads.

## Test Plan

At minimum, run:

- existing unit and integration tests,
- nested path query tests,
- join behavior tests,
- concurrency behavior tests,
- SQL-like parity tests for any shared reflection changes,
- `HotspotMicroJmhBenchmark` locally with `-prof gc` when touching reflection/materialization/cache hot paths,
- benchmarks before and after each major work package.

## Suggested Remaining Implementation Sequence

1. Continue WP5 from the current partial state:
   - either keep the conservative computed-field plus join fallback as the stopping point, or
   - extend it only if new measurements justify the added complexity
2. Rerun the new computed-field hotspot plus end-to-end benchmarks to capture the current WP5 delta and decide whether the hotspot is threshold-ready
3. tighten hotspot threshold/config files only after the new microbenchmark numbers stabilize

Hotspot microbenchmarking is now a standing prerequisite rather than a future work package.

## Next Session Handoff

- First decision tomorrow: do repeated hotspot/end-to-end runs justify treating the new computed-field join path as stable enough for a budget, or should it remain measurement-only for now.
- If continuing implementation, revisit `FilterQueryBuilder` only if the remaining full fallback shapes show up as meaningful benchmark costs.
- If measuring first, rerun `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` with forked `-prof gc` and compare it against the end-to-end suites before changing thresholds.
