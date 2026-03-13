# Current State

## Repository Health

- The repository remains a single-module Java library with the expected `jar` packaging and CI/test structure.
- The AI memory system was compacted into hot and cold tiers on 2026-03-13.
- `mvn -q test` passed on 2026-03-13 after landing the selective single-join array fast path.
- Focused validation also passed on 2026-03-13: `FilterImplFastPathTest`, `FilterQueryBuilderSelectiveMaterializationTest`, and `PojoLensJoinJmhBenchmarkParityTest`.
- The benchmark runner was rebuilt with `mvn -q -DskipTests -Pbenchmark-runner package` before rerunning JMH, so the current measurements come from the updated code.
- The earlier warmed end-to-end benchmark at `size=10000` measured about `1.091 ms/op` versus a current manual baseline rerun around `0.150 ms/op`; `-prof gc` measured about `1.441 ms/op` and `3,107,771 B/op`.
- A fresh reverted-code rerun on 2026-03-13 measured about `1.475 ms/op` for the same warmed end-to-end path, so the benchmark is currently showing environment or runtime drift relative to the earlier `1.091 ms/op` run and should be re-established under tighter control before more WP17 code changes land.
- Warmed profiling is now represented by `target/pojolens-fastpath-current.jfr`, which shows the hot path has moved away from `ComputedFieldSupport.materializeRow`, `JoinEngine.mergeFields`, and `ReflectionUtil.collectQueryRowFieldTypes`.

## Active Work Areas

- `TODO.md` is still focused on profiler-driven follow-up work after WP14.
- WP14 is effectively accepted: the warmed 2026-03-13 JFR no longer shows `SqlExpressionEvaluator$Parser.*` among the dominant repository hotspots.
- WP15 and WP16 are effectively accepted on the selective single-join path after introducing `FastArrayQuerySupport`, array-row projection support in `ReflectionUtil`, and schema-based execution-plan dispatch in `FilterImpl`.
- The remaining gap on that path is now concentrated in `FastArrayQuerySupport.filterRows`, `ReflectionUtil.readFlatRowValues`, `ReflectionUtil.readResolvedFieldValue`, `ReflectionUtil.setResolvedFieldValue`, and `FilterQueryBuilder.copySourceBeans`.
- Legacy `QueryRow` execution remains the fallback for broader shapes such as multi-join, collision-heavy, grouped, HAVING, distinct, and time-bucket flows; the fast path is intentionally narrow and benchmark-driven.
- WP17 is now the active implementation target, with `TODO.md` defining an explicit highest-priority attack order across reflection reads, joined-row materialization, rule evaluation, projection writes, and residual source-copy/casting overhead on the selective single-join array path.
- An initial WP17 implementation pass on 2026-03-13 tried lower-overhead reflection access and more aggressive fast-path filtering/materialization changes, but the warmed benchmark regressed and the code changes were reverted; the next step should be controlled repro/profiling rather than another speculative optimization.
- The new hot context is stable; deeper repository detail now lives in cold core files and indexes.

## Documentation Risks

- The previously known process-doc drift was fixed on 2026-03-13 in `CONTRIBUTING.md`, `MIGRATION.md`, and `RELEASE.md`.
- The doc-consistency scripts now check those benchmark-version and SQL-like capability invariants.
- The remaining documentation gap is that no artifact publication target is documented or declared in `pom.xml`.

## Next Validation Opportunities

- Prototype compiled field-access chains for `ReflectionUtil.readFlatRowValues()` / `readResolvedFieldValue()` so the fast path stops paying generic reflective traversal on every row.
- Reduce generic rule-group overhead inside `FastArrayQuerySupport.filterRows()` by precompiling narrower predicate plans for the benchmark-shaped single-join flow.
- Reduce projection cost in `ReflectionUtil.setResolvedFieldValue()` / `applyProjectionWritePlan()` and reassess whether no-arg instantiation plus setter writes are still the right default for the fast path.
- Decide whether `FilterQueryBuilder.copySourceBeans()` should remain on the hot path now that internal compatibility constraints are relaxed.
- Rerun the doc-consistency script when process docs or benchmark instructions change again.
- Regenerate AI indexes again after any source, test, or documentation structure change.
