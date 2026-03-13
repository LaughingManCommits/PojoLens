# Handoff

## Next Work Tasks

- Continue WP15/WP16 acceptance by removing the remaining warmed-path hits in `ComputedFieldSupport.materializeRow`, `JoinEngine.mergeFields`, and `ReflectionUtil.collectQueryRowFieldTypes`.
- Start WP17 on the remaining row-model churn in `ReflectionUtil` and `FilterCore` once the WP16 rescan leak is understood.
- Rerun warmed JFR plus warmed `-prof gc` benchmarks after the next implementation pass.
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
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/builder/QuerySpec.java`
- `src/main/java/laughing/man/commits/sqllike/internal/expression/SqlExpressionEvaluator.java`
- `src/test/java/laughing/man/commits/builder/FilterQueryBuilderSelectiveMaterializationTest.java`
- `src/test/java/laughing/man/commits/benchmark/PojoLensJoinJmhBenchmarkParityTest.java`
- `src/test/java/laughing/man/commits/sqllike/internal/expression/SqlExpressionEvaluatorTest.java`

## Unresolved Questions

- Should the doc-consistency tooling expand beyond process-doc drift into broader release and README alignment checks?
- What exact caller chain still executes `ReflectionUtil.collectQueryRowFieldTypes` on the warmed computed-field join path after the WP16 schema-derivation change?
- Can `ComputedFieldSupport.materializeRow` avoid the remaining `LinkedHashMap` and `HashMap` churn without breaking computed-field overwrite semantics?
- How much of the remaining gap is now in `extractQueryFields` / `toDomainRows` / `filterDisplayFields`, and should WP17 start there or in `buildFieldIndex` / `castToString` first?
