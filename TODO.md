# Performance Backlog

## Summary

WP1-WP14 and WP5 are complete and intentionally removed from this file.

This file now tracks only unfinished performance work.

As of 2026-03-13, the main remaining gap is still the warmed computed-field single-join path, not broad pipeline correctness:

- `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=10000` with `-wi 5 -i 10 -r 300ms`: about `1.043 ms/op`
- `PojoLensJoinJmhBenchmark.manualHashJoinLeftComputedField` at `size=10000` with the same settings: about `0.161 ms/op`
- current gap: about `6.5x`

Focused warm profiling is still the driver for the backlog below.

## Focused Profiler Findings

Primary reproduction command:

```powershell
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField -p size=10000 -f 1 -wi 5 -i 10 -r 300ms -jvmArgsAppend "-XX:StartFlightRecording=filename=target/wp17-after-readpath.jfr,settings=profile,dumponexit=true"
```

Profiler artifact:

- `target/wp17-after-readpath.jfr`

Observed during the current warm profile on 2026-03-13 after the first landed WP17 changes:

- benchmark average with JFR overhead: about `1.097 ms/op`
- current manual baseline average from the same controlled rerun cycle: about `0.161 ms/op`
- JFR duration: about `53 s`
- young GCs during the recording: about `180`

Top sampled CPU leaf methods in repository code:

- `FastArrayQuerySupport.buildChildIndex` (`753` first-app-frame samples)
- `FastArrayQuerySupport.tryBuildJoinedState` (`563`)
- `FastArrayQuerySupport$ComputedFieldPlan.resolveValue` (`271`)
- `ReflectionUtil$ResolvedFieldPath.read` (`209`)
- `FastArrayQuerySupport.filterRows` (`114`)
- `ReflectionUtil.applyProjectionWritePlan` (`112`)
- `ReflectionUtil.noArgConstructor` (`97`)
- `ReflectionUtil.setResolvedFieldValue` (`90`)

Top sampled allocation sites in repository code:

- `FastArrayQuerySupport.buildChildIndex` (`4162` first-app-frame allocation samples)
- `ReflectionUtil.readFlatRowValues` (`3159`)
- `ReflectionUtil$ResolvedFieldPath.read` (`2916`)
- `FastArrayQuerySupport.materializeJoinedRow` (`2243`)
- `ReflectionUtil.instantiateNoArg` (`983`)
- `FastArrayQuerySupport.castNumericValue` (`553`)
- `FilterQueryBuilder.copySourceBeans` (`348`)
- `FastArrayQuerySupport.filterRows` (`223`)

Focused warm profiling remains the main driver for the backlog below.

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
- the remaining cost is now concentrated in:
  - `FastArrayQuerySupport.buildChildIndex`
  - `FastArrayQuerySupport.tryBuildJoinedState`
  - `FastArrayQuerySupport$ComputedFieldPlan.resolveValue`
  - `ReflectionUtil.readFlatRowValues` / `ReflectionUtil$ResolvedFieldPath.read`
  - `FastArrayQuerySupport.materializeJoinedRow`
  - projection writes and no-arg instantiation in `ReflectionUtil`
  - residual allocation churn during join-state construction on the array-based fast path

Working hypothesis:

1. the remaining gap is now mostly **join-state build + child indexing + residual reflection/allocation overhead**
2. the generic single-rule filter path is no longer the first thing to attack on this benchmark shape
3. the next optimization work should stay focused on the warmed selective single-join array path unless fresh profiling proves otherwise

WP Interpretation:

- WP14 is accepted: the old `SqlExpressionEvaluator$Parser.*` hot spots remain out of the dominant warmed samples.
- WP15 is effectively accepted on the selective single-join path: the old `ComputedFieldSupport.materializeRow` / `JoinEngine.mergeFields` hot path dropped out after replacing the `QueryRow` materialization path with the array-based execution path.
- WP16 is effectively accepted on this path: `ReflectionUtil.collectQueryRowFieldTypes` no longer shows up as a dominant warmed leaf because the fast path bypasses joined-row rescans entirely.
- WP17 remains the active implementation target because the remaining cost is now concentrated in child indexing, join-state construction, computed dependency lookup, residual read-side reflection, and joined-row allocation on the selective single-join path.

Latest post-profile validation on 2026-03-13:

- controlled repro before the first landed WP17 changes: warmed `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=10000`, `-wi 5 -i 10 -r 300ms`: about `1.436 ms/op`
- warmed `PojoLensJoinJmhBenchmark.manualHashJoinLeftComputedField` at `size=10000`, `-wi 5 -i 10 -r 300ms`: about `0.161 ms/op`
- after landing the specialized single-rule matcher in `FastArrayQuerySupport.filterRows()`: warmed `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField`: about `1.094 ms/op`
- after landing the narrower read-path optimization in `ReflectionUtil`: warmed `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField`: about `1.043 ms/op`
- allocation-focused post-change `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=10000`, `-wi 3 -i 5 -r 250ms -prof gc`: about `1.031 ms/op` and `3,107,786.135 B/op`
- the latest warmed JFR now confirms that `FastArrayQuerySupport.filterRows` is no longer the dominant CPU leaf; the next hotspot cluster is `buildChildIndex`, `tryBuildJoinedState`, `ComputedFieldPlan.resolveValue`, and `ResolvedFieldPath.read`

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

Status: active

Priority: highest

Goal: reduce the remaining reflection, filtering, projection, and allocation cost on the warmed array-based join path.

Scope:

- `src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`

Primary optimization targets (combined CPU/allocation priority):

- `FastArrayQuerySupport.buildChildIndex`
- `FastArrayQuerySupport.tryBuildJoinedState`
- `FastArrayQuerySupport.materializeJoinedRow`
- `FastArrayQuerySupport$ComputedFieldPlan.resolveValue`
- `ReflectionUtil.readFlatRowValues`
- `ReflectionUtil$ResolvedFieldPath.read`
- `ReflectionUtil.instantiateNoArg`
- `ReflectionUtil.applyProjectionWritePlan`
- `ReflectionUtil.setResolvedFieldValue`
- `FilterQueryBuilder.copySourceBeans`
- `FastArrayQuerySupport.castNumericValue`
- `FastArrayQuerySupport.filterRows`

Progress on 2026-03-13:

- Re-established the local warmed benchmark baseline under controlled reruns after an earlier speculative WP17 pass regressed and was reverted.
- Added a specialized single-rule fast matcher in `FastArrayQuerySupport`, which materially reduced generic rule-evaluation overhead on the benchmark-shaped single-filter path.
- Added a narrower read-side field-path optimization in `ReflectionUtil`, using precompiled `ResolvedFieldPath.read` traversal through `FlatRowReadPlan`.
- Focused validation passed after both landed WP17 changes: `FilterImplFastPathTest`, `FilterQueryBuilderSelectiveMaterializationTest`, and `PojoLensJoinJmhBenchmarkParityTest`.
- Warmed end-to-end reruns improved from the controlled local baseline around `1.436 ms/op` to about `1.094 ms/op`, then to about `1.043 ms/op`.
- Allocation-focused reruns remain effectively flat at about `3,107,786 B/op`, so the current WP17 gains are primarily CPU-side.
- The latest warmed JFR moved the next hotspot cluster into child indexing, join-state construction, computed-field dependency lookup, residual read-side reflection, and joined-row allocation.

Tasks:

- reduce child-side extraction and indexing cost in `FastArrayQuerySupport.buildChildIndex()`
- reduce join-state construction overhead in `FastArrayQuerySupport.tryBuildJoinedState()`
- reduce allocation churn in `materializeJoinedRow()`
- reduce computed dependency lookup overhead in `FastArrayQuerySupport$ComputedFieldPlan.resolveValue()`
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
  - `FastArrayQuerySupport.buildChildIndex`
  - `FastArrayQuerySupport.tryBuildJoinedState`
  - `FastArrayQuerySupport$ComputedFieldPlan.resolveValue`
  - `ReflectionUtil.readFlatRowValues` / `ReflectionUtil$ResolvedFieldPath.read`
- `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` improves materially from the current post-change baseline around `1.043 ms/op`
- `B/op` drops materially from the current post-change baseline around `3,107,786.135 B/op`
- young-GC pressure drops materially from the current post-change fast-path baseline
- correctness and projection behavior remain unchanged

Minimum success criteria:

- warmed benchmark falls below `0.75 ms/op`

Strong success criteria:

- warmed benchmark falls below `0.45 ms/op`

### WP18: Rebaseline computed-field benchmark budgets after implementation changes

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

- rerun `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` after WP14-WP17
- rerun the warmed and cold end-to-end computed-field join benchmarks after WP14-WP17
- decide whether the hotspot stays diagnostic-only or gets a stable threshold
- tighten the current end-to-end strict-suite budgets only after repeated stable runs

Acceptance criteria:

- hotspot threshold policy is explicit and stable
- end-to-end computed-field join budgets reflect the new implementation rather than the current high-allocation baseline

### Decision Rules For The Agent

When making changes:
- use benchmark and profiler evidence, not intuition
- prefer small, isolated, measurable changes
- rerun the target benchmark after each meaningful optimization
- only reprofile after a measurable improvement
- report both speed delta and allocation delta
- keep correctness intact

If a change improves one hotspot but regresses another, document that explicitly.
If a broader redesign seems necessary, prove it with profiler evidence from the target benchmark first.

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

1. WP15
2. WP16
3. WP17
4. WP18

Do not reopen WP5 unless someone intentionally wants a new feature scope beyond the accepted selective/single-join boundary.
