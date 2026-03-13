# Handoff

## Next Work Tasks

- Start WP17 from the new fast-path baseline: warmed `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` is about `1.091 ms/op` at `size=10000` versus a current manual baseline rerun around `0.150 ms/op`, and `-prof gc` measures about `1.441 ms/op` / `3,107,771 B/op`.
- The current warmed JFR artifact is `target/pojolens-fastpath-current.jfr`; it already confirms the hot path has moved away from `ComputedFieldSupport.materializeRow`, `JoinEngine.mergeFields`, and `ReflectionUtil.collectQueryRowFieldTypes`.
- Attack the remaining hot frames in this order: `FastArrayQuerySupport.filterRows`, `ReflectionUtil.readFlatRowValues`, `ReflectionUtil.readResolvedFieldValue`, `ReflectionUtil.setResolvedFieldValue`, then `FilterQueryBuilder.copySourceBeans`.
- Prefer a deeper internal redesign over compatibility-preserving tweaks: compiled accessor chains or method handles, narrower predicate plans, and cheaper projection materialization are all on the table.
- Keep process docs aligned if benchmark commands, release steps, or SQL-like capability boundaries change again.

## Relevant Files

- `TODO.md`
- `docs/benchmarking.md`
- `benchmarks/thresholds.json`
- `scripts/benchmark-suite-main.args`
- `scripts/benchmark-suite-hotspots.args`
- `src/main/java/laughing/man/commits/computed/internal/ComputedFieldSupport.java`
- `src/main/java/laughing/man/commits/filter/JoinEngine.java`
- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/filter/FilterExecutionPlan.java`
- `target/pojolens-fastpath-current.jfr`
- `src/test/java/laughing/man/commits/filter/FilterImplFastPathTest.java`
- `src/test/java/laughing/man/commits/builder/FilterQueryBuilderSelectiveMaterializationTest.java`
- `src/test/java/laughing/man/commits/benchmark/PojoLensJoinJmhBenchmarkParityTest.java`

## Unresolved Questions

- Should broader multi-join, collision-heavy, or grouped flows get their own array-based path, or should the legacy `QueryRow` engine remain the long-tail fallback?
- Is the next best gain in compiled field access for `readFlatRowValues()` / `readResolvedFieldValue()`, or in reducing generic rule evaluation inside `FastArrayQuerySupport.filterRows()`?
- Should projection stay on no-arg construction plus setter writes, or move to a cheaper constructor or record-style path for common benchmark projections?
- Can `FilterQueryBuilder.copySourceBeans()` be bypassed safely on the fast path now that internal compatibility constraints are relaxed?
