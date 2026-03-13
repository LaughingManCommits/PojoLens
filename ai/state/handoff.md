# Handoff

## Next Work Tasks

- Continue WP15 acceptance work, then move to WP16 through WP18.
- Rerun warmed benchmark and JFR validation before judging WP14/WP15 complete.
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
- `src/main/java/laughing/man/commits/sqllike/internal/expression/SqlExpressionEvaluator.java`
- `src/test/java/laughing/man/commits/builder/FilterQueryBuilderSelectiveMaterializationTest.java`
- `src/test/java/laughing/man/commits/benchmark/PojoLensJoinJmhBenchmarkParityTest.java`
- `src/test/java/laughing/man/commits/sqllike/internal/expression/SqlExpressionEvaluatorTest.java`

## Unresolved Questions

- Should the doc-consistency tooling expand beyond process-doc drift into broader release and README alignment checks?
- Do warmed benchmarks confirm that WP14 removed the old parser hot spot and that WP15 moved `ComputedFieldSupport.materializeRow` / `JoinEngine.mergeFields` out of the dominant hot path?
- Why did the short hotspot time improve while `gc.alloc.rate.norm` stayed essentially flat, and does that point the remaining allocation cost at WP16/WP17?
