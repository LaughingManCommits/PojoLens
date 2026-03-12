# Performance Backlog

## Summary

WP1-WP13 and WP5 are complete and intentionally removed from this file.

This file now tracks only unfinished performance work.

As of 2026-03-12, the main remaining gap is the warmed computed-field single-join path, not broad pipeline correctness:

- `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `size=10000` with `-wi 5 -i 10 -r 300ms`: about `5.505 ms/op`
- `PojoLensJoinJmhBenchmark.manualHashJoinLeftComputedField` at `size=10000` with the same settings: about `0.148 ms/op`
- current gap: about `37x`

Focused warm profiling is now the driver for the backlog below.

## Focused Profiler Findings

Primary reproduction command:

```powershell
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField -p size=10000 -f 1 -wi 5 -i 10 -r 300ms -jvmArgsAppend "-XX:StartFlightRecording=filename=target/pojolens-join-warm-fork.jfr,settings=profile,dumponexit=true"
```

Profiler artifact:

- `target/pojolens-join-warm-fork.jfr`

Observed during the warm profile:

- benchmark average: about `5.505 ms/op`
- JFR duration: about `53 s`
- young GCs during the recording: `261`

Top sampled CPU leaf methods in repository code:

- `ComputedFieldSupport.materializeRow`
- `JoinEngine.mergeFields`
- `ReflectionUtil.collectQueryRowFieldTypes`
- `FilterCore.filterFields`
- `ReflectionUtil.readResolvedFieldValue`
- `SqlExpressionEvaluator$Parser.parseFactor`
- `FilterCore.filterDisplayFields`

Top sampled allocation sites in repository code:

- `ComputedFieldSupport.materializeRow`
- `JoinEngine.mergeFields`
- `ReflectionUtil.extractQueryFields`
- `SqlExpressionEvaluator$Parser.parseFactor`
- `JoinEngine.buildFieldIndex`
- `SqlExpressionEvaluator$Parser.<init>`
- `ReflectionUtil.readResolvedFieldValue`
- `ReflectionUtil.toDomainRows`

Top sampled allocated classes:

- `QueryField`
- `Object[]`
- `LinkedHashMap$Entry`
- `HashMap$Node[]`
- `HashMap$Node`
- `LinkedHashMap`
- `QueryRow`
- `ArrayList`

Interpretation:

- computed fields are still reparsed and reevaluated row-by-row
- computed-field materialization still clones full row state per row
- join output assembly still clones parent/child field objects per row
- post-join builder state still rescans all joined rows to rediscover field types
- the selective join path still pays large `QueryField` and container-allocation costs even after WP5

## Active Work Packages

### WP14: Compile computed-field expressions for row execution

Status: in progress

Goal: stop reparsing computed expressions for every row.

Scope:

- `src/main/java/laughing/man/commits/computed/internal/ComputedFieldSupport.java`
- `src/main/java/laughing/man/commits/sqllike/internal/expression/SqlExpressionEvaluator.java`
- computed-field registry integration points

Tasks:

- compile numeric computed expressions once instead of creating a new parser per row
- cache the compiled representation by computed-field definition or expression string
- keep identifier collection and validation behavior intact

Progress on 2026-03-12:

- `SqlExpressionEvaluator` now compiles numeric expressions into reusable cached ASTs keyed by expression string.
- `ComputedFieldSupport.materializeRows()` now compiles the applicable computed-field definitions once per materialization call and reuses the compiled expressions for each row.
- Added targeted regression coverage in `SqlExpressionEvaluatorTest` and `ComputedFieldRegistryTest`.
- Remaining acceptance work is a comparable warmed profile/JFR pass to confirm the old `SqlExpressionEvaluator$Parser.*` hot spots disappeared from the computed-field join path.

Acceptance criteria:

- warmed join profiling no longer shows `SqlExpressionEvaluator$Parser.*` among the dominant CPU/allocation sites for `pojoLensJoinLeftComputedField`
- computed-field behavior and type coercion remain unchanged

### WP15: Remove per-row row/field cloning in computed and join materialization

Status: pending

Goal: stop rebuilding large `QueryRow` and `QueryField` graphs for every joined row.

Scope:

- `src/main/java/laughing/man/commits/computed/internal/ComputedFieldSupport.java`
- `src/main/java/laughing/man/commits/filter/JoinEngine.java`
- row/domain classes under `src/main/java/laughing/man/commits/domain`

Tasks:

- remove the full-field-copy loop in `ComputedFieldSupport.materializeRow()`
- replace `JoinEngine.mergeFields()` row-by-row cloning with schema-aware value assembly
- avoid per-row `LinkedHashMap`, `HashSet`, and duplicate-name bookkeeping when the joined schema is already known

Acceptance criteria:

- warmed join profiling materially reduces sampled allocations for `QueryField`, `QueryRow`, `LinkedHashMap`, and `HashMap$Node`
- `ComputedFieldSupport.materializeRow` and `JoinEngine.mergeFields` are no longer the dominant hot methods
- join behavior and field-collision behavior remain unchanged

### WP16: Derive joined-row field types without rescanning materialized rows

Status: pending

Goal: stop walking every joined row just to reconstruct field-type metadata.

Scope:

- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`

Tasks:

- stop using `ReflectionUtil.collectQueryRowFieldTypes(rows)` after join execution
- derive joined field types from parent/child source metadata and computed-field definitions
- keep builder validation and typed projection behavior correct

Acceptance criteria:

- warmed join profiling no longer shows `ReflectionUtil.collectQueryRowFieldTypes` as a major CPU leaf
- no full joined-row rescan is required after `FilterImpl.join()`

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

1. WP14
2. WP15
3. WP16
4. WP17
5. WP18

Do not reopen WP5 unless someone intentionally wants a new feature scope beyond the accepted selective/single-join boundary.
