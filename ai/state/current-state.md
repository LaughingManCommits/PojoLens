# Current State

## Repository Health

- The repository remains a single-module Java library with the expected `jar` packaging and CI/test structure.
- The AI memory system was compacted into hot and cold tiers on 2026-03-13.
- `mvn -q test` passed on 2026-03-13 after landing the selective single-join array fast path.
- Focused validation also passed on 2026-03-13: `FilterImplFastPathTest`, `FilterQueryBuilderSelectiveMaterializationTest`, and `PojoLensJoinJmhBenchmarkParityTest`.
- The benchmark runner was rebuilt with `mvn -q -DskipTests -Pbenchmark-runner package` before rerunning JMH, so the current measurements come from the updated code.
- The earlier warmed end-to-end benchmark at `size=10000` measured about `1.091 ms/op` versus a current manual baseline rerun around `0.150 ms/op`; `-prof gc` measured about `1.441 ms/op` and `3,107,771 B/op`.
- A fresh reverted-code rerun on 2026-03-13 measured about `1.475 ms/op` for the same warmed end-to-end path, confirming session-to-session drift and forcing WP17 to re-establish its baseline locally before making more changes.
- After that controlled repro, two WP17 changes landed on 2026-03-13: a specialized single-rule fast matcher in `FastArrayQuerySupport` and a narrower read-path optimization in `ReflectionUtil`. The current warmed end-to-end rerun at `size=10000` is now about `1.043 ms/op` versus a current manual baseline rerun around `0.161 ms/op`; the current `-prof gc` rerun measures about `1.031 ms/op` and `3,107,786 B/op`.
- Warmed profiling is now also represented by `target/wp17-after-readpath.jfr`, where the top current repository CPU leaves have shifted to `FastArrayQuerySupport.buildChildIndex`, `FastArrayQuerySupport.tryBuildJoinedState`, `FastArrayQuerySupport$ComputedFieldPlan.resolveValue`, and `ReflectionUtil$ResolvedFieldPath.read`.
- Warmed profiling is now represented by `target/pojolens-fastpath-current.jfr`, which shows the hot path has moved away from `ComputedFieldSupport.materializeRow`, `JoinEngine.mergeFields`, and `ReflectionUtil.collectQueryRowFieldTypes`.

## Active Work Areas

- `TODO.md` is still focused on profiler-driven follow-up work after WP14.
- WP14 is effectively accepted: the warmed 2026-03-13 JFR no longer shows `SqlExpressionEvaluator$Parser.*` among the dominant repository hotspots.
- WP15 and WP16 are effectively accepted on the selective single-join path after introducing `FastArrayQuerySupport`, array-row projection support in `ReflectionUtil`, and schema-based execution-plan dispatch in `FilterImpl`.
- The remaining gap on that path is now concentrated in `FastArrayQuerySupport.filterRows`, `ReflectionUtil.readFlatRowValues`, `ReflectionUtil.readResolvedFieldValue`, `ReflectionUtil.setResolvedFieldValue`, and `FilterQueryBuilder.copySourceBeans`.
- Legacy `QueryRow` execution remains the fallback for broader shapes such as multi-join, collision-heavy, grouped, HAVING, distinct, and time-bucket flows; the fast path is intentionally narrow and benchmark-driven.
- WP17 is now the active implementation target, with `TODO.md` defining an explicit highest-priority attack order across reflection reads, joined-row materialization, rule evaluation, projection writes, and residual source-copy/casting overhead on the selective single-join array path.
- An initial speculative WP17 implementation pass on 2026-03-13 regressed the warmed benchmark and was reverted; the follow-up controlled repro/profile loop then identified `matchesRuleGroups` and read-side reflection as the real current hotspots.
- WP17 is now actively moving again after landing the single-rule matcher fast path and the narrowed `ResolvedFieldPath.read` / `FlatRowReadPlan` read optimization.
- The remaining gap on the selective single-join path is now concentrated in `FastArrayQuerySupport.buildChildIndex`, `FastArrayQuerySupport.tryBuildJoinedState`, `FastArrayQuerySupport$ComputedFieldPlan.resolveValue`, `ReflectionUtil.readFlatRowValues`, `ReflectionUtil$ResolvedFieldPath.read`, and joined-row allocation in `FastArrayQuerySupport.materializeJoinedRow`.
- The new hot context is stable; deeper repository detail now lives in cold core files and indexes.

## Documentation Risks

- The previously known process-doc drift was fixed on 2026-03-13 in `CONTRIBUTING.md`, `MIGRATION.md`, and `RELEASE.md`.
- The doc-consistency scripts now check those benchmark-version and SQL-like capability invariants.
- The remaining documentation gap is that no artifact publication target is documented or declared in `pom.xml`.

## Next Validation Opportunities

- Reduce child-side extraction and indexing cost inside `FastArrayQuerySupport.buildChildIndex()`; that is now the top current repository CPU and allocation leaf in the warmed profile.
- Reduce `FastArrayQuerySupport.tryBuildJoinedState()` / `materializeJoinedRow()` overhead so the join setup phase stops allocating full transient arrays so aggressively on the benchmark path.
- Reduce computed-field lookup overhead in `FastArrayQuerySupport$ComputedFieldPlan.resolveValue()` now that the generic single-rule filter cost is lower.
- Reassess whether `FilterQueryBuilder.copySourceBeans()` still belongs on the hot path once the higher-yield join/build costs are lower.
- Rerun the doc-consistency script when process docs or benchmark instructions change again.
- Regenerate AI indexes again after any source, test, or documentation structure change.
