# Handoff

## Next Work Tasks

- Rerun a warmed JFR for `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` on the new `~2.605 ms/op` baseline to verify whether the latest schema-aware `ComputedFieldSupport` path and in-place join materialization actually removed the old hot frames.
- If `ReflectionUtil.collectQueryRowFieldTypes` still shows up, trace the remaining caller path outside `FilterImpl.join()`; `snapshotForRows(...)` and other `QueryRow`-based builder paths are still suspect.
- If the new JFR confirms computed materialization is no longer dominant, start WP17 on the remaining row-model churn in `ReflectionUtil` and `FilterCore`, with `extractQueryFields`, `toDomainRows`, `filterFields`, `filterDisplayFields`, and join-key `castToString` as the highest-yield candidates.
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
- `src/main/java/laughing/man/commits/filter/FilterCore.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/builder/QuerySpec.java`
- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/sqllike/internal/expression/SqlExpressionEvaluator.java`
- `src/test/java/laughing/man/commits/builder/FilterQueryBuilderSelectiveMaterializationTest.java`
- `src/test/java/laughing/man/commits/benchmark/PojoLensJoinJmhBenchmarkParityTest.java`
- `src/test/java/laughing/man/commits/sqllike/internal/expression/SqlExpressionEvaluatorTest.java`

## Unresolved Questions

- Should the doc-consistency tooling expand beyond process-doc drift into broader release and README alignment checks?
- What exact caller chain still executes `ReflectionUtil.collectQueryRowFieldTypes` on the warmed computed-field join path after the WP16 schema-derivation change and the latest in-place join materialization pass?
- Did the latest `ComputedFieldSupport` rewrite remove `LinkedHashMap` / `HashMap` churn enough for the hot path to move into `ReflectionUtil` and `FilterCore`, or is `JoinEngine.mergeFields` still too expensive?
- After the `~2.605 ms/op` result, is the next best gain in `extractQueryFields` / `toDomainRows` / `filterDisplayFields`, or in join-key coercion and `buildFieldIndex` / `castToString`?
