# Performance Backlog

## Summary

WP1-WP14 and WP5 are complete and intentionally removed from this file.

This file tracks the active performance backlog plus the accepted package boundaries that still matter for follow-up decisions.

As of 2026-03-14, the main warmed tuning gap is still the computed-field single-join path:

- `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=10000` with `-wi 5 -i 10 -r 300ms`: about `0.614 ms/op`
- `PojoLensJoinJmhBenchmark.manualHashJoinLeftComputedField` at `size=10000` with the same settings: about `0.108 ms/op`
- current gap: about `5.7x`

WP17 is considered good enough for now. Active follow-up work should favor broader, higher-leverage redesigns over more narrow micro-tuning unless fresh profiling shows an obvious reopen candidate.

The broader benchmark sweep now also shows portfolio-level issues that should stay visible in the backlog:

- chart thresholds passed with headroom, and the old fluent-vs-SQL-like chart parity report is now diagnostic-only because SQL-like intentionally pays query-translation overhead that fluent does not
- cold filter/baseline comparisons remain far slower than Streams/manual baselines, so broader end-to-end overhead is still real outside the warmed WP17 target
- recurring warmed JFR hotspot classes are now concentrated in `ReflectionUtil` and `FastArrayQuerySupport`, which is a stronger prioritization signal than any single method name

Focused warm profiling still drives WP17, but the backlog below now also tracks the broader suite-level and class-level stress points.

## Focused Profiler Findings

Primary reproduction command:

```powershell
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField -p size=10000 -f 1 -wi 5 -i 10 -r 300ms -jvmArgsAppend "-XX:StartFlightRecording=filename=target/wp17-after-bound-expression.jfr,settings=profile,dumponexit=true"
```

Profiler artifact:

- `target/wp17-after-bound-expression.jfr`

Observed during the current warm profile on 2026-03-14 after the latest landed WP17 changes:

- benchmark average with JFR overhead: about `0.668 ms/op`
- current manual baseline average from the same controlled rerun cycle: about `0.108 ms/op`
- JFR duration: about `53 s`
- young GCs during the recording: `311`

Top sampled CPU leaf methods in repository code:

- `ReflectionUtil$ResolvedFieldPath.read` (`832` first-repo-frame samples)
- `FastArrayQuerySupport.applyComputedValues` (`313`)
- `FastArrayQuerySupport.tryBuildJoinedState` (`246`)
- `FastArrayQuerySupport.buildChildIndex` (`213`)
- `ReflectionUtil.applyProjectionWritePlan` (`151`)
- `ReflectionUtil.setResolvedFieldValue` (`91`)
- `ReflectionUtil.noArgConstructor` (`83`)
- `ObjectUtil.castValue` (`65`)

Top sampled allocation sites in repository code:

- `FastArrayQuerySupport.buildChildIndex` (`6824` first-repo-frame allocation samples)
- `FastArrayQuerySupport.materializeJoinedRow` (`4731`)
- `ReflectionUtil$ResolvedFieldPath.read` (`2467`)
- `ReflectionUtil.readFlatRowValues` (`715`)
- `FastArrayQuerySupport.castNumericValue` (`671`)
- `ReflectionUtil.instantiateNoArg` (`22`)
- `FilterQueryBuilder.copySourceBeans` (`13`)

Focused warm profiling remains the main driver for the backlog below.

## Cross-JFR Hotspot Clusters

Compared warmed profiler artifacts:

- `target/pojolens-fastpath-current.jfr`
- `target/wp17-after-readpath.jfr`
- `target/wp17-after-parent-buffer.jfr`
- `target/wp17-after-bound-expression.jfr`

Recurring first-repo-frame CPU clusters across those profiles:

- `ReflectionUtil` read path: `readResolvedFieldValue` / `ResolvedFieldPath.read` stayed dominant at about `589`, `875`, `925`, and now `832` first-repo-frame CPU samples as earlier bottlenecks dropped away
- `FastArrayQuerySupport` computed/join path: `ComputedFieldPlan.resolveValue` grew from about `200` to `253` to `331` and then dropped out after direct array-index binding; the newest profile now shows computed work through `applyComputedValues` (`313`) while `tryBuildJoinedState` and `buildChildIndex` stay present
- `FastArrayQuerySupport.filterRows` was very large in the earliest fast-path profile (`961`), then much lower (`126`, `122`), and is now effectively out of the dominant current cluster (`14` first-repo-frame CPU samples)
- `ReflectionUtil` projection writes stayed visible in the later profiles through `applyProjectionWritePlan` / `setResolvedFieldValue`

Recurring first-repo-frame allocation clusters across those profiles:

- `FastArrayQuerySupport.buildChildIndex` remained the largest recurring allocation site and rose from about `1271` to `3676` to `5353` to `6824` samples as parent-side buffer work was reduced
- `ReflectionUtil` read-side extraction stayed heavy through `readFlatRowValues`, `readResolvedFieldValue`, and `ResolvedFieldPath.read`, even though the newest profile shifts more allocation weight back toward join indexing/materialization
- `FastArrayQuerySupport.materializeJoinedRow` remained a top allocation source in every warmed profile and rose to about `4731` samples in the newest recording
- `FastArrayQuerySupport.castNumericValue` emerged after the read-path cleanup and still stays visible in the top allocation cluster
- `ReflectionUtil.instantiateNoArg` is lower in the newest profile but still belongs to the recurring conversion/projection cost family

Common interpretation:

1. the warmed hotspot picture is now primarily class-level in `ReflectionUtil` and `FastArrayQuerySupport`
2. old `ComputedFieldSupport`, `JoinEngine`, and `collectQueryRowFieldTypes` work is no longer the main thing holding back this path
3. broader backlog work should target those recurring classes plus absolute chart/SQL-like overhead, not just one isolated micro-optimization at a time

## Target

Near-term target for this path:

- reduce warmed overhead to under **5x** manual
- practical latency target at the current manual baseline: `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField < 0.75 ms/op`
- practical allocation target on the same path: **< 2,000,000 B/op**
- practical combined goal: lower both latency and allocation together, not one at the expense of the other

Stretch target:

- reduce warmed overhead to under **3x** manual
- stretch latency target at the current manual baseline: **< 0.45 ms/op**
- stretch allocation target on the same path: **< 1,000,000 B/op**

Minimum acceptance direction for each meaningful WP17 improvement:

- report **ms/op delta**
- report **B/op delta**
- report whether young-GC pressure improved, stayed flat, or regressed

Guardrail:

- do not accept CPU-only wins as sufficient if allocation and GC pressure remain effectively flat on the warmed target path

Do not trade away correctness for speed.

## Current Interpretation

The major bottleneck is no longer the old row-wrapper/materialization path.

Accepted conclusions from the latest warm profiling and follow-up validation:

- old `SqlExpressionEvaluator$Parser.*` hotspots are no longer dominant
- old `ComputedFieldSupport.materializeRow` / `JoinEngine.mergeFields` hotspots are no longer dominant on the selective single-join fast path
- old joined-row field-type rescanning is no longer the dominant issue on the selective single-join fast path
- generic single-rule filter overhead dropped materially after the specialized fast matcher landed on the array path
- read-side reflection dropped materially after narrowing `ResolvedFieldPath.read` / `FlatRowReadPlan` traversal on the common path
- parent-side join setup allocation dropped materially after reusing a single parent read buffer and storing single-child index entries without per-key `ArrayList` buckets
- direct computed dependency lookup dropped out after binding compiled numeric expressions to array indexes on the fast path, but computed work still shows up through `applyComputedValues`
- the remaining cost is now concentrated in:
  - `ReflectionUtil$ResolvedFieldPath.read`
  - residual join-state work in `FastArrayQuerySupport.tryBuildJoinedState` / `buildChildIndex`
  - `FastArrayQuerySupport.applyComputedValues`
  - `FastArrayQuerySupport.materializeJoinedRow`
  - projection writes and numeric casting in `ReflectionUtil` / `FastArrayQuerySupport`
  - residual allocation churn during child indexing and joined-row materialization on the array-based fast path

Working hypothesis:

1. the remaining gap is now mostly **residual reflection reads + child indexing/materialization allocation + projection/numeric-cast overhead**
2. the generic single-rule filter path is no longer the first thing to attack on this benchmark shape
3. the next optimization work should stay focused on the warmed selective single-join array path unless fresh profiling proves otherwise

WP Interpretation:

- WP14 is accepted: the old `SqlExpressionEvaluator$Parser.*` hot spots remain out of the dominant warmed samples.
- WP15 is effectively accepted on the selective single-join path: the old `ComputedFieldSupport.materializeRow` / `JoinEngine.mergeFields` hot path dropped out after replacing the `QueryRow` materialization path with the array-based execution path.
- WP16 is effectively accepted on this path: `ReflectionUtil.collectQueryRowFieldTypes` no longer shows up as a dominant warmed leaf because the fast path bypasses joined-row rescans entirely.
- WP17 is now parked as good enough because the remaining cost is concentrated in residual reflection reads, child indexing, joined-row materialization, computed boxing/casting, and projection writes on the selective single-join path, while broader redesign work now has better leverage.
- the broader 2026-03-14 benchmark sweep also justifies tracking absolute chart/SQL-like overhead and reflection/conversion hotspot work as separate packages instead of treating WP17 as the full performance picture

Latest post-profile validation on 2026-03-14:

- pre-change warmed local rerun of `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=10000`, `-wi 5 -i 10 -r 300ms`: about `0.713 ms/op`
- pre-change allocation-focused rerun at `size=10000`, `-wi 3 -i 5 -r 250ms -prof gc`: about `0.729 ms/op` and `3,107,785.502 B/op`
- prior warmed baseline after the single-child bucket optimization in `FastArrayQuerySupport.buildChildIndex()` and the reusable parent buffer in `tryBuildJoinedState()`: about `0.654 ms/op`
- prior allocation-focused baseline after that pass at `size=10000`, `-wi 3 -i 5 -r 250ms -prof gc`: about `0.632 ms/op` and `2,307,857.286 B/op`
- current warmed `PojoLensJoinJmhBenchmark.manualHashJoinLeftComputedField` rerun at `size=10000`, `-wi 5 -i 10 -r 300ms`: about `0.108 ms/op`
- after binding compiled numeric expressions to direct array indexes in `SqlExpressionEvaluator` and `FastArrayQuerySupport`: warmed `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField`: about `0.614 ms/op`
- allocation-focused post-change rerun at `size=10000`, `-wi 3 -i 5 -r 250ms -prof gc`: about `0.617 ms/op` and `2,307,945.304 B/op`
- allocation and GC did not improve with this pass: `B/op` stayed effectively flat at about `+88 B/op`, `gc.count` stayed at `8`, and `gc.time` moved from `5 ms` to `6 ms`
- the latest warmed JFR now points first to `ReflectionUtil$ResolvedFieldPath.read`, `FastArrayQuerySupport.applyComputedValues`, residual `tryBuildJoinedState` / `buildChildIndex` work, `materializeJoinedRow`, and projection writes

## Active Work Packages

### WP15: Remove per-row row/field cloning in computed and join materialization

Status: effectively accepted on the selective single-join path; residual work moved to WP17

Goal: stop rebuilding large `QueryRow` and `QueryField` graphs for every joined row.

Scope:

- `src/main/java/laughing/man/commits/computed/internal/ComputedFieldSupport.java`
- `src/main/java/laughing/man/commits/filter/JoinEngine.java`
- row/domain classes under `src/main/java/laughing/man/commits/domain`

Tasks:

- remove the full-field-copy loop in `ComputedFieldSupport.materializeRow()`
- replace `JoinEngine.mergeFields()` row-by-row cloning with schema-aware value assembly
- avoid per-row `LinkedHashMap`, `HashSet`, and duplicate-name bookkeeping when the joined schema is already known

Progress on 2026-03-13:

- `ComputedFieldSupport.materializeRow()` now reuses existing base `QueryField` objects and only allocates replacement `QueryField` instances for computed outputs that are added or updated.
- `JoinEngine` now precomputes a merge plan per join step, reuses unchanged parent/child `QueryField` instances for the common no-collision path, and limits new `QueryField` allocation to renamed collision fields or unmatched-child null placeholders.
- `ComputedFieldSupport` now precomputes a stable row-schema materialization plan, resolves source/computed dependencies once per schema, and eliminates per-row `LinkedHashMap` construction and field-name upsert scans on the common path.
- `FilterImpl.join()` now materializes computed fields in place on internally produced joined rows, and `FilterQueryBuilder.setMaterializedRows(...)` stores those rows without wrapping them in a second `QueryRow` copy pass.
- `FastArrayQuerySupport` now provides a separate array-based execution path for the selective single-join fluent flow, so the hot benchmark path no longer needs `QueryRow` / `QueryField` objects for join, filter, or projection.
- `ReflectionUtil` now exposes flattened read plans plus array-row projection support, which lets the fast path extract selected bean fields directly into `Object[]` rows and project from arrays back into the caller projection class.
- Added regression coverage in `FilterQueryBuilderSelectiveMaterializationTest` to confirm distinct computed outputs are preserved across multiple child matches on the same joined parent row.
- Added `FilterImplFastPathTest` to assert that the benchmark-like selective computed join shape actually activates the new array execution state.
- Short validation on 2026-03-13:
  - `mvn -q test` passed.
  - `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=1000`, `-wi 1 -i 2 -r 100ms`: about `0.257 ms/op` versus the prior short smoke around `0.464 ms/op`.
  - `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` at `size=1000`, `-wi 1 -i 2 -r 100ms -prof gc`: about `40.405 us/op` and `363,856 B/op` versus the prior short smoke around `60.147 us/op` and `363,856 B/op`.
- Warmed validation on 2026-03-13:
  - earlier warmed `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` runs improved to about `3.029 ms/op` and then about `2.605 ms/op`.
  - the current warmed `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` rerun measures about `1.091 ms/op` with the current manual baseline rerun at about `0.150 ms/op`.
  - warmed `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` with `-prof gc` now measures about `1.441 ms/op` and `3,107,771.004 B/op`, down materially from the earlier `13,061,013.568 B/op`.
  - the warmed fast-path JFR confirms the old `ComputedFieldSupport` / `JoinEngine` / `collectQueryRowFieldTypes` bottlenecks are no longer dominant on this path.

Remaining acceptance work:

- no WP15-specific acceptance work remains on the selective single-join path; broader coverage should be treated as separate fast-path scope rather than reopening the legacy `QueryRow` design

Acceptance criteria:

- warmed join profiling materially reduces sampled allocations for `QueryField`, `QueryRow`, `LinkedHashMap`, and `HashMap$Node`
- `ComputedFieldSupport.materializeRow` and `JoinEngine.mergeFields` are no longer the dominant hot methods on the selective single-join path
- join behavior and field-collision behavior remain unchanged

### WP16: Derive joined-row field types without rescanning materialized rows

Status: effectively accepted on the selective single-join path; residual work moved to WP17

Goal: stop walking every joined row just to reconstruct field-type metadata.

Scope:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Tasks:

- stop using `ReflectionUtil.collectQueryRowFieldTypes(rows)` after join execution
- derive joined field types from parent/child source metadata and computed-field definitions
- keep builder validation and typed projection behavior correct

Progress on 2026-03-13:

- `FilterImpl.join()` now updates builder row schema from `FilterQueryBuilder.deriveJoinedSourceFieldTypes()` instead of rescanning all joined rows to rediscover field types.
- `FilterQueryBuilder` now retains per-join source field-type metadata and derives merged join schemas with the same collision-renaming rules used by `JoinEngine`, including `RIGHT_JOIN` field ordering.
- Added regression coverage in `FilterQueryBuilderSelectiveMaterializationTest` to ensure unmatched left joins retain child field types even when every joined child value is `null`.
- Validation on 2026-03-13:
  - `mvn -q test` passed after the WP16 schema-derivation changes.
  - `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=1000`, `-wi 1 -i 2 -r 100ms`: about `0.249 ms/op`, slightly better than the post-WP15 short smoke around `0.257 ms/op`.
  - an earlier warmed JFR for `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` still sampled `ReflectionUtil.collectQueryRowFieldTypes` heavily (`284` first-app-frame CPU samples) before the later fast-path dispatch changes, which is why WP16 remained open at that point.

Remaining acceptance work:

- no WP16-specific acceptance work remains on the selective single-join path; the remaining decision is whether broader multi-join or collision-heavy queries should get their own fast path or continue to fall back to legacy `QueryRow` execution

Acceptance criteria:

- warmed join profiling no longer shows `ReflectionUtil.collectQueryRowFieldTypes` as a major CPU leaf on the selective single-join path
- no full joined-row rescan is required anywhere on the fast single-join execution path after `FilterImpl.join()`

### WP17: Reduce residual overhead on the selective single-join array path

Status: good enough for now; parked

Priority: reopen only when fresh profiling or a broader redesign exposes a clear high-confidence win

Goal: reduce the remaining reflection, filtering, projection, and allocation cost on the warmed array-based join path.

Scope:

- `src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`

Primary optimization targets (combined CPU/allocation priority):

- `FastArrayQuerySupport.buildChildIndex`
- `FastArrayQuerySupport.tryBuildJoinedState`
- `FastArrayQuerySupport.materializeJoinedRow`
- `FastArrayQuerySupport.applyComputedValues`
- `ReflectionUtil.readFlatRowValues`
- `ReflectionUtil$ResolvedFieldPath.read`
- `ReflectionUtil.instantiateNoArg`
- `ReflectionUtil.applyProjectionWritePlan`
- `ReflectionUtil.setResolvedFieldValue`
- `FilterQueryBuilder.copySourceBeans`
- `FastArrayQuerySupport.castNumericValue`
- `FastArrayQuerySupport.filterRows`

Progress on 2026-03-14:

- Rebuilt the benchmark runner and re-established the local warmed / allocation baselines under current-session conditions before and after this pass.
- `FastArrayQuerySupport.buildChildIndex()` now stores the first child match directly in the hash index and only promotes a join key to `List<Object[]>` when that key actually fans out, removing the common-case per-key `ArrayList` allocation.
- `FastArrayQuerySupport.tryBuildJoinedState()` now reuses a single parent read buffer across the parent scan instead of allocating a fresh parent `Object[]` per parent row.
- `ReflectionUtil.readFlatRowValues(Object, FlatRowReadPlan, Object[], int)` now fills preallocated arrays, enabling the reused parent buffer without rebuilding field-path metadata.
- `SqlExpressionEvaluator.CompiledExpression` now supports binding numeric expressions to direct array indexes once per plan, and `FastArrayQuerySupport` now uses that bound form in `applyComputedValues()` instead of per-row lambda/string-based dependency resolution.
- Focused validation passed after the latest landed WP17 changes: `SqlExpressionEvaluatorTest`, `FilterImplFastPathTest`, `FilterQueryBuilderSelectiveMaterializationTest`, and `PojoLensJoinJmhBenchmarkParityTest`.
- `mvn -q test` also passed after the bound-expression change.
- Warmed end-to-end reruns improved from the prior local baseline around `0.654 ms/op` to about `0.614 ms/op`.
- Allocation-focused reruns stayed effectively flat at about `2,307,945.304 B/op`; `gc.count` stayed at `8` and `gc.time` regressed slightly from `5 ms` to `6 ms` across the five measurement iterations.
- The latest warmed JFR moved computed work out of `ComputedFieldPlan.resolveValue()` and into `FastArrayQuerySupport.applyComputedValues()`, while `ReflectionUtil$ResolvedFieldPath.read` remains the dominant first-repo-frame CPU leaf.

Tasks:

- reduce child-side extraction and indexing cost in `FastArrayQuerySupport.buildChildIndex()`
- reduce join-state construction overhead in `FastArrayQuerySupport.tryBuildJoinedState()`
- reduce allocation churn in `materializeJoinedRow()`
- reduce residual computed evaluation and numeric-cast overhead in `FastArrayQuerySupport.applyComputedValues()` / `castNumericValue()`
- continue reducing residual read-side cost in `readFlatRowValues()` / `ResolvedFieldPath.read`
- reduce projection overhead in `setResolvedFieldValue()` / `applyProjectionWritePlan()`
- decide whether defensive source-list copying in `FilterQueryBuilder.copySourceBeans()` still belongs on the hot path now that compatibility constraints are relaxed
- reduce generic numeric-casting overhead if it is still on the hot path after higher-value changes
- move repeated per-plan work out of per-row execution wherever possible

Allowed strategies:

- precompute access plans once per execution plan
- replace repeated generic reflective access with cached or specialized access paths where benchmark-proven
- fuse extraction, filtering, and computed evaluation when it removes duplicate work
- avoid rebuilding arrays or row state when values can be reused or written directly
- avoid eager projection materialization on internal fast-path stages when the caller-visible result does not require it yet
- reduce boxing, branch-heavy type switching, and repeated metadata lookups on the hot path

Do not do:

- do not reopen old `QueryRow` / `QueryField` work unless profiling clearly shows it has become dominant again
- do not broaden scope to multi-join or collision-heavy redesign unless profiling on the target benchmark requires it
- do not do speculative cleanup refactors with no benchmark evidence
- do not optimize cold-start behavior ahead of the warmed target path

Acceptance criteria:

- warmed profiling shows a clear reduction in:
  - parent-side `FastArrayQuerySupport.tryBuildJoinedState` staging work
  - child-side bucket allocation inside `FastArrayQuerySupport.buildChildIndex`
  - `ReflectionUtil.readFlatRowValues`
  - residual join-state allocation before projection
- `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` improves materially from the current post-change baseline around `0.614 ms/op`
- `B/op` drops materially from the current post-change baseline around `2,307,945.304 B/op`
- young-GC pressure drops materially from the current post-change fast-path baseline
- correctness and projection behavior remain unchanged

Minimum success criteria:

- warmed benchmark remains below `0.75 ms/op`
- allocation falls below `2,000,000 B/op`
- young-GC pressure does not regress

Strong success criteria:

- warmed benchmark falls below `0.45 ms/op`
- allocation falls below `1,000,000 B/op`
- warmed profiling materially shifts away from the current `ReflectionUtil` / `FastArrayQuerySupport` hotspot concentration

### WP18: Reduce absolute overhead on chart and SQL-like chart flows

Status: active

Priority: highest

Goal: reduce absolute chart and SQL-like chart latency where it materially improves the product, without using fluent-vs-SQL-like ratio checks as a gate.

Scope:

- `src/main/java/laughing/man/commits/benchmark/ChartVisualizationJmhBenchmark.java`
- `src/main/java/laughing/man/commits/benchmark/StatsQueryJmhBenchmark.java`
- `src/main/java/laughing/man/commits/sqllike`
- `src/main/java/laughing/man/commits/chart`
- `target/benchmarks/chart.json`

Current evidence from 2026-03-14 to 2026-03-15:

- chart thresholds passed `45/45`
- the prior chart parity report failed in `5/15` comparisons, but that ratio is now diagnostic-only because SQL-like includes parse/translation work that fluent does not
- absolute chart cost is still worth attacking: `StatsQueryJmhBenchmark.sqlLikeParseAndTimeBucketMetricsToChart|size=10000` measured about `135.007 ms/op`, `StatsQueryJmhBenchmark.fluentTimeBucketMetricsToChart|size=10000` measured about `133.176 ms/op`, and `ChartVisualizationJmhBenchmark.scatterPayloadJsonExport|size=10000` used about `69.0%` of its current budget
- chart and SQL-like chart flows are still valid optimization targets, but they should now be judged on absolute `ms/op`, allocation, and product value rather than on fluent-vs-SQL-like ratio alone
- the first WP18 structural redesign is now landed: reusable non-subquery SQL-like execution shapes cache validation/binding work and rebind request-scoped builders per execution instead of rebuilding the fluent pipeline every time
- the second WP18 redesign is now landed: fluent and SQL-like chart execution map directly from internal `QueryRow` results, and `ChartMapper` now uses indexed `QueryRow` field reads instead of projecting to caller classes and then reflecting back over those objects
- exact targeted JMH reruns on 2026-03-14 at `size=10000`, `-f 1 -wi 3 -i 5 -r 250ms` now measure `StatsQueryJmhBenchmark.fluentTimeBucketMetricsToChart` at about `5.045 ms/op` and `sqlLikeParseAndTimeBucketMetricsToChart` at about `4.891 ms/op`, down materially from the earlier same-session targeted snapshot around `7.200 ms/op` and `7.088 ms/op`
- matching exact query-only reruns now measure `fluentTimeBucketMetrics` at about `4.990 ms/op` and `sqlLikeParseAndTimeBucketMetrics` at about `4.728 ms/op`, which means chart-mapping overhead on that stats workload is now close to noise and the remaining absolute cost is mostly outside the old projection round-trip
- the third WP18 chart-export pass landed on 2026-03-15: `ChartPayloadJsonExporter` now pre-sizes its payload buffer and appends fixed-scale numeric values directly instead of calling `String.format(...)` for every point
- `mvn -q test` passed on 2026-03-15 after that exporter pass, and a rebuilt cold chart-suite rerun plus `BenchmarkThresholdChecker` still passed `45/45`
- the comparable cold chart-suite rerun on 2026-03-15 now measures `scatterPayloadJsonExport` at about `0.066 ms/op`, `0.634 ms/op`, and `1.146 ms/op` for sizes `1000`, `10000`, and `100000`, down materially from the 2026-03-14 snapshot around `3.910`, `43.157`, and `82.048 ms/op`
- targeted warm reruns on 2026-03-15 at `size=10000`, `-f 1 -wi 2 -i 3 -r 200ms` now measure `scatterPayloadJsonExport` at about `0.560 ms/op`; the matching `-prof gc` rerun measures about `0.367 ms/op` and `580,857 B/op`
- with benchmark JSON export now cheap again, the remaining WP18 product-value question is more likely SQL-like setup/query execution or other chart assembly work than `ChartPayloadJsonExporter`
- a narrower 2026-03-15 consolidation pass now precomputes reusable raw execution-plan cache keys for prepared non-join SQL-like stats shapes, so repeated aliased stats filters and chart executions reuse the existing stats plan cache without rebuilding that key from the rebound builder every call
- focused cache regressions were added for repeated SQL-like aliased stats filter/chart execution, and `mvn -q test` passed after the consolidation pass
- a broader same-session attempt to delegate SQL-like raw-row execution fully through `FilterImpl` was benchmarked and not kept after short `size=10000` reruns regressed to about `5.404 ms/op` for `sqlLikeParseAndTimeBucketMetrics` and `5.034 ms/op` for `sqlLikeParseAndTimeBucketMetricsToChart`
- the kept narrower pass is functionally useful but benchmark-neutral so far: the same short reruns now measure about `4.889 ms/op` and `4.731 ms/op` versus the earlier same-session baselines around `4.881 ms/op` and `4.594 ms/op`
- a follow-up 2026-03-15 consolidation pass now removes a duplicate `QuerySpec` copy in `FilterQueryBuilder` snapshot construction by letting snapshot builders adopt the already-copied spec; snapshot-isolation regression coverage was added and `mvn -q test` passed again
- the same-session short 2026-03-15 reruns after that copy-removal pass were dominated by broad drift rather than a clean patch signal: `sqlLikeParseAndTimeBucketMetrics` measured about `6.418 ms/op`, `sqlLikeParseAndTimeBucketMetricsToChart` about `6.632 ms/op`, and fluent controls also drifted up to about `7.146 ms/op` and `7.029 ms/op`
- a fourth 2026-03-15 setup pass now lets bean-backed prepared SQL-like executions bind to a lightweight `preparedExecutionView(...)` that shares the validated query shape and skips `RuleCleaner` on that validated path, while `QueryRow`-backed executions still fall back to isolated copies
- focused validation passed again after that pass: `SqlLikeQueryContractTest`, `SqlLikeChartIntegrationTest`, `CachePolicyConfigTest`, `FilterQueryBuilderSelectiveMaterializationTest`, and then `mvn -q test`
- a direct same-session rebinding probe on a 10k-row bean-backed time-bucket stats template now measures `preparedExecutionView(...)` at about `4.228 us/op` versus `preparedExecutionCopy(...)` at about `5.822 us/op` (`~1.38x` faster), but the matching short end-to-end rerun still drifted to about `7.510 ms/op` for `sqlLikeParseAndTimeBucketMetrics` and `6.783 ms/op` for `sqlLikeParseAndTimeBucketMetricsToChart` with fluent controls near `7.677` and `7.359 ms/op`
- a fifth 2026-03-15 direct-stats pass now lets simple bean-backed non-join stats queries aggregate directly from `ReflectionUtil.FlatRowReadPlan` reads through `FastStatsQuerySupport`, keeping existing stats-plan cache reuse while bypassing source `QueryRow` materialization
- the next short targeted rerun after that direct-stats pass improved the same 10k workloads to about `6.042 ms/op` for `sqlLikeParseAndTimeBucketMetrics`, `6.698 ms/op` for `sqlLikeParseAndTimeBucketMetricsToChart`, `6.663 ms/op` for `fluentTimeBucketMetrics`, and `6.746 ms/op` for `fluentTimeBucketMetricsToChart`; query-heavy paths moved down most, while chart deltas stayed smaller
- a sixth 2026-03-15 direct-chart pass now routes `FastStatsQuerySupport.FastStatsState` chart executions through indexed `Object[]` mapping in `ChartMapper` instead of rebuilding `QueryRow` / `QueryField` graphs first; `ChartMapperArrayRowsTest` was added to pin the new mapper path
- focused chart/sql-like coverage plus full `mvn -q test` passed after the direct-chart pass
- the latest short targeted rerun after the direct-chart pass stayed mixed rather than proving a clean SQL-like chart win: fluent query/chart measured about `6.934` and `6.336 ms/op`, while SQL-like query/chart measured about `6.318` and `7.173 ms/op`; treat that as a sign to profile the remaining SQL-like chart cost directly rather than assume `QueryRow` materialization was the whole gap
- a seventh 2026-03-15 setup/refactor pass now caches `ReflectionUtil.compileFlatRowReadPlan(...)` by root type plus selected schema and introduces a per-call `SqlLikeQuery` execution run so filter/chart/raw-row paths reuse one rebound builder and one fast-stats resolution per invocation instead of rebuilding that state ad hoc
- focused regression coverage was added in `ReflectionUtilTest` for flat read-plan cache reuse and in `SqlLikeTypedBindContractTest` for repeated bound stats charts; focused tests plus full `mvn -q test` passed after the refactor
- the latest short targeted rerun after that setup/refactor pass stayed noisy overall, but SQL-like chart moved below SQL-like query in the same run: fluent query/chart measured about `6.919` and `7.182 ms/op`, while SQL-like query/chart measured about `7.033` and `6.381 ms/op`; treat that as weak evidence for setup-side improvement, not a clean attribution result
- an eighth 2026-03-15 alias-projection/chart pass now keeps plain aliased fast-stats filters on direct array-row projection, adds aliased output-schema remapping for fast-stats charts, and carries the caller projection class through SQL-like chart execution so plain aliases and computed select outputs still chart correctly when grouped queries fall back through `QueryRow` results
- focused regressions now cover repeated aliased fast-stats filter rebinding, aliased stats charts with and without `ORDER BY`, and aliased stats chart cache reuse; focused suites plus full `mvn -q test` passed again after that pass
- the latest short targeted rerun after the alias/chart pass measured fluent query/chart about `6.452` and `6.737 ms/op`, while SQL-like query/chart measured about `6.735` and `4.764 ms/op`; treat that as evidence the chart path stayed low while the remaining SQL-like query/setup cost is still unresolved, not as a clean overall speed win
- a dedicated prepared SQL-like stats setup microbenchmark now lives in `HotspotMicroJmhBenchmark`; the first `size=10000`, `-f 1 -wi 2 -i 5 -r 100ms -prof gc` run measured `sqlLikePreparedStatsRebindView` at about `0.324 us/op` and `3,120 B/op` versus `0.668 us/op` and `3,528 B/op` for `sqlLikePreparedStatsRebindCopy`, while the combined rebind-plus-fast-stats setup stayed around `7.517 ms/op` / `16,785,798 B/op` for view versus `7.306 ms/op` / `16,786,181 B/op` for copy, which is evidence that builder-copy savings remain real but are now buried under the row-scan/aggregation work in full fast-stats state creation

Tasks:

- profile the most expensive absolute chart and SQL-like chart paths by `ms/op`, not by ratio
- separate SQL-like translation, stats plan construction, remaining chart payload assembly, and serialization cost on the chart workloads that matter most now that the typed-projection round-trip is removed
- allow larger chart or SQL-like pipeline redesigns when they clearly reduce product-visible overhead while preserving substantially similar features/behavior
- land fixes in priority order by absolute product cost and benchmark leverage, not by fluent-vs-SQL-like ratio
- keep chart threshold pass status intact while improving absolute latency headroom
- if work stays on prepared-builder setup cost, use a direct profile or dedicated microbenchmark to isolate what still remains after the lighter prepared view; short whole-query JMH alone is still too drift-heavy for patch attribution
- now that aliased stats charts preserve projection names on both the fast path and the grouped fallback path and the latest short rerun again keeps SQL-like chart below query, profile the remaining SQL-like query/setup cost specifically and only revisit chart assembly if a direct profile points there
- use the dedicated prepared SQL-like fast-stats microbenchmark or a fresh profile to separate rebind cost from full fast-stats state creation; short whole-query JMH is still conflating setup and row-scan variance

Boundary with WP19:

- if a chart or SQL-like chart fix depends on shared reflection/conversion work already tracked in WP19, land the shared code under WP19 and use WP18 to track chart-specific reproduction and acceptance

Acceptance criteria:

- selected chart or SQL-like chart workloads show material absolute `ms/op` improvements versus the current `BENCHMARKS.md` snapshot
- chart threshold checks still pass after any WP18 fix
- the root cause of the prior absolute chart overhead is documented in `BENCHMARKS.md`

### WP19: Reduce recurring reflection and conversion hotspot clusters

Status: pending

Priority: high

Goal: cut the class-level overhead that recurs across warmed JFRs and the hotspot microbenchmark suite, especially in `ReflectionUtil` and `FastArrayQuerySupport`.

This package covers cross-cutting overhead that affects both the warmed selective join path and the broader conversion/projection workloads. It is not limited to the WP17 benchmark shape.

Scope:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java`
- `src/main/java/laughing/man/commits/util/ObjectUtil.java`
- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/benchmark/HotspotMicroJmhBenchmark.java`
- `target/benchmarks/hotspots-gc.json`
- warmed JFR artifacts under `target/*.jfr`

Current evidence from 2026-03-14:

- hottest microbenchmark latency is `reflectionToClassList|size=10000` at `1115.501 us/op`
- hottest microbenchmark allocation is `computedFieldJoinSelectiveMaterialization|size=10000` at `3,532,314 B/op`
- recurring warmed JFR hotspot classes are `ReflectionUtil` and `FastArrayQuerySupport`

Tasks:

- reduce `reflectionToClassList` and `reflectionToDomainRows` latency/allocation with the same class-level changes that help the warmed join path
- reduce recurring projection/conversion cost in `instantiateNoArg`, `applyProjectionWritePlan`, `setResolvedFieldValue`, and numeric casts
- decide whether compiled accessors or other specialized conversion plans should become the default for the common benchmark shapes
- rerun the hotspot suite with `-prof gc` and compare against the current `BENCHMARKS.md` snapshot

Acceptance criteria:

- hotspot `-prof gc` runs show material latency and allocation drops in the reflection/conversion workloads
- warmed JFR hotspot concentration shifts materially away from the current `ReflectionUtil` and `FastArrayQuerySupport` class cluster
- improvements are reported with both `us/op` or `ms/op` and `B/op`

### WP20: Rebaseline computed-field benchmark budgets after implementation changes

Status: pending

Goal: tighten budgets only after the implementation work above stabilizes.

Scope:

- `src/main/java/laughing/man/commits/benchmark/HotspotMicroJmhBenchmark.java`
- `src/main/java/laughing/man/commits/benchmark/PojoLensJoinJmhBenchmark.java`
- `scripts/benchmark-suite-hotspots.args`
- `scripts/benchmark-suite-main.args`
- `benchmarks/thresholds.json`
- `docs/benchmarking.md`

Tasks:

- rerun `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` after WP14-WP19
- rerun the warmed and cold end-to-end computed-field join benchmarks after WP14-WP19
- decide whether the hotspot stays diagnostic-only or gets a stable threshold
- tighten the current end-to-end strict-suite budgets only after repeated stable runs

Boundary:

- WP20 covers computed-field and hotspot rebaselining only
- chart thresholds remain under WP18 unless chart-path implementation changes justify separate chart rebaselining

Acceptance criteria:

- hotspot threshold policy is explicit and stable
- end-to-end computed-field join budgets reflect the new implementation rather than the current high-allocation baseline

### Decision Rules For The Agent

When making changes:

- use benchmark and profiler evidence, not intuition
- prefer the simplest change that clearly improves the benchmarked path, whether it is small and isolated or a broader redesign
- allow design changes when they materially improve performance, reduce complexity, or remove architectural bottlenecks
- prefer a larger redesign over a smaller tweak when the larger change has the clearer path to a real benchmark win
- rerun the target benchmark after each meaningful optimization
- only reprofile after a measurable change
- report both speed delta and allocation delta
- keep correctness intact

If a change improves one hotspot but regresses another, document that explicitly.

For this library stage, design changes are allowed if they are evidence-backed and move the implementation toward a better long-term shape. Do not preserve a weaker design only because it is already in place.

## Validation Plan

Core correctness:

- `mvn -q test`
- `src/test/java/laughing/man/commits/builder/FilterQueryBuilderSelectiveMaterializationTest.java`
- `src/test/java/laughing/man/commits/benchmark/PojoLensJoinJmhBenchmarkParityTest.java`

Warm benchmark reproduction:

```powershell
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField -p size=10000 -f 1 -wi 5 -i 10 -r 300ms
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.PojoLensJoinJmhBenchmark.manualHashJoinLeftComputedField -p size=10000 -f 1 -wi 5 -i 10 -r 300ms
```

Allocation-focused diagnostics:

```powershell
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization -p size=10000 -f 1 -wi 3 -i 5 -r 250ms -prof gc
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField -p size=10000 -f 1 -wi 3 -i 5 -r 250ms -prof gc
```

Strict-suite verification after implementation changes:

```powershell
java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-main.args -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks.json
java -cp target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.BenchmarkThresholdChecker target/benchmarks.json benchmarks/thresholds.json target/benchmark-report.csv --strict
```

## Recommended Order

1. WP18
2. WP19
3. WP20
4. WP17 only if broader work exposes a clear reopen case

Do not reopen WP5 unless someone intentionally wants a new feature scope beyond the accepted selective/single-join boundary.
