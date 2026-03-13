# Performance Backlog

## Summary

WP1-WP14 and WP5 are complete and intentionally removed from this file.

This file now tracks only unfinished performance work.

As of 2026-03-13, the main remaining gap is still the warmed computed-field single-join path, not broad pipeline correctness:

- `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=10000` with `-wi 5 -i 10 -r 300ms`: about `2.605 ms/op`
- `PojoLensJoinJmhBenchmark.manualHashJoinLeftComputedField` at `size=10000` with the same settings: about `0.108 ms/op`
- current gap: about `24x`

Focused warm profiling is still the driver for the backlog below.

## Focused Profiler Findings

Primary reproduction command:

```powershell
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField -p size=10000 -f 1 -wi 5 -i 10 -r 300ms -jvmArgsAppend "-XX:StartFlightRecording=filename=target/pojolens-join-warm-fork.jfr,settings=profile,dumponexit=true"
```

Profiler artifact:

- `target/pojolens-join-warm-fork.jfr`

Observed during the warm profile on 2026-03-13 before the latest WP15 pass:

- benchmark average: about `3.029 ms/op`
- manual baseline average: about `0.108 ms/op`
- JFR duration: about `53 s`
- young GCs during the recording: `381`

Top sampled CPU leaf methods in repository code:

- `ComputedFieldSupport.materializeRow` (`600` first-app-frame samples)
- `FilterCore.filterFields` (`411`)
- `ReflectionUtil.readResolvedFieldValue` (`292`)
- `ReflectionUtil.collectQueryRowFieldTypes` (`284`)
- `ObjectUtil.castToString` (`185`)
- `FilterCore.filterDisplayFields` (`155`)
- `JoinEngine.join` (`123`)
- `JoinEngine.mergeFields` (`119`)

Top sampled allocation sites in repository code:

- `ComputedFieldSupport.materializeRow` (`5397` first-app-frame allocation samples)
- `ReflectionUtil.extractQueryFields` (`2587`)
- `JoinEngine.buildFieldIndex` (`1248`)
- `ObjectUtil.castToString` (`1222`)
- `ReflectionUtil.toDomainRows` (`808`)
- `ReflectionUtil.readResolvedFieldValue` (`779`)
- `JoinEngine.mergeFields` (`639`)
- `ComputedFieldSupport.newQueryField` (`579`)

Interpretation:

- WP14 is accepted: the old `SqlExpressionEvaluator$Parser.*` hot spots dropped out of the dominant warmed CPU and allocation samples.
- WP15 is still open: the earlier warm JFR still showed `ComputedFieldSupport.materializeRow` as the hottest repository frame and `JoinEngine.mergeFields` in the warmed path.
- WP16 is still open: `ReflectionUtil.collectQueryRowFieldTypes` remains a major warmed CPU leaf, so the derived-schema fast path is not yet eliminating that rescan cost from the real join execution path.
- WP17 is now the clearest next implementation target because row flattening and projection churn (`extractQueryFields`, `toDomainRows`, `filterFields`, `filterDisplayFields`) still dominate the remaining allocation profile.

Latest post-profile validation on 2026-03-13:

- warmed `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=10000`, `-wi 5 -i 10 -r 300ms`: about `2.605 ms/op`
- warmed `PojoLensJoinJmhBenchmark.manualHashJoinLeftComputedField` at `size=10000`, `-wi 5 -i 10 -r 300ms`: about `0.108 ms/op`
- allocation-focused `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=10000`, `-wi 3 -i 5 -r 250ms -prof gc`: about `2.702 ms/op` and `13,061,013.568 B/op`
- a follow-up warmed JFR has not yet been captured after this latest implementation pass, so hotspot attribution still reflects the earlier `3.029 ms/op` recording above

## Active Work Packages

### WP15: Remove per-row row/field cloning in computed and join materialization

Status: in progress

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
- Added regression coverage in `FilterQueryBuilderSelectiveMaterializationTest` to confirm distinct computed outputs are preserved across multiple child matches on the same joined parent row.
- Short validation on 2026-03-13:
  - `mvn -q test` passed.
  - `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=1000`, `-wi 1 -i 2 -r 100ms`: about `0.257 ms/op` versus the prior short smoke around `0.464 ms/op`.
  - `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` at `size=1000`, `-wi 1 -i 2 -r 100ms -prof gc`: about `40.405 us/op` and `363,856 B/op` versus the prior short smoke around `60.147 us/op` and `363,856 B/op`.
- Warmed validation on 2026-03-13:
  - earlier warmed `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` run improved to about `3.029 ms/op` versus the prior warmed baseline around `5.505 ms/op`.
  - the latest warmed `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` run improved again to about `2.605 ms/op` with the manual baseline unchanged at about `0.108 ms/op`.
  - warmed `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` with `-prof gc` now measures about `2.702 ms/op` and `13,061,013.568 B/op`.
  - a new warmed JFR is still needed to confirm whether `ComputedFieldSupport.materializeRow` and `JoinEngine.mergeFields` have fallen out of the dominant hot path after the latest pass.

Remaining acceptance work:

- reduce `ComputedFieldSupport.materializeRow` further so it stops dominating the warmed path
- reduce or eliminate the remaining `JoinEngine.mergeFields` / `ComputedFieldSupport.newQueryField` allocation churn
- confirm with a follow-up warmed JFR that both methods fall out of the dominant repository hotspots

Acceptance criteria:

- warmed join profiling materially reduces sampled allocations for `QueryField`, `QueryRow`, `LinkedHashMap`, and `HashMap$Node`
- `ComputedFieldSupport.materializeRow` and `JoinEngine.mergeFields` are no longer the dominant hot methods
- join behavior and field-collision behavior remain unchanged

### WP16: Derive joined-row field types without rescanning materialized rows

Status: in progress

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
  - warmed JFR for `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` still sampled `ReflectionUtil.collectQueryRowFieldTypes` heavily (`284` first-app-frame CPU samples), so the intended fast path is not yet removing that work from the real warmed execution profile.

Remaining acceptance work:

- identify and remove the remaining call path that still executes `ReflectionUtil.collectQueryRowFieldTypes` during the warmed computed-field join path
- confirm the derived-schema path remains correct for broader multi-join and collision-heavy cases beyond the current regression set

Acceptance criteria:

- warmed join profiling no longer shows `ReflectionUtil.collectQueryRowFieldTypes` as a major CPU leaf
- no full joined-row rescan is required anywhere on the warmed join execution path after `FilterImpl.join()`

### WP17: Reduce residual row-model churn on the selective join path

Status: pending

Goal: lower the remaining object churn from row flattening, filtering, and projection on the warmed join path.

Scope:

- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/filter/FilterCore.java`
- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`

Tasks:

- audit whether `extractQueryFields()` and `toDomainRows()` still over-materialize fields on the selective join path
- reduce avoidable `ArrayList` and `Object[]` churn in projection/filter phases
- revisit whether `filterDisplayFields()` and related row-projection stages can avoid rebuilding field lists

Acceptance criteria:

- warmed join profiling shows a clear reduction in `ReflectionUtil.toDomainRows`, `extractQueryFields`, and `FilterCore.filterDisplayFields`
- young-GC count drops materially from the current warm-profile baseline

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
