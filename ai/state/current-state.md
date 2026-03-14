# Current State

## Repository Health

- The repository remains a single-module Java library with the expected `jar` packaging and CI/test structure.
- The AI memory system was compacted into hot and cold tiers on 2026-03-13.
- `mvn -q test` passed again on 2026-03-14 after the latest WP17 join-build optimization.
- Focused validation also passed again on 2026-03-14: `FilterImplFastPathTest`, `FilterQueryBuilderSelectiveMaterializationTest`, and `PojoLensJoinJmhBenchmarkParityTest`.
- The benchmark runner was rebuilt with `mvn -q -DskipTests -Pbenchmark-runner package` before rerunning JMH, so the current measurements come from the updated code.
- The earlier warmed end-to-end benchmark at `size=10000` measured about `1.091 ms/op` versus a current manual baseline rerun around `0.150 ms/op`; `-prof gc` measured about `1.441 ms/op` and `3,107,771 B/op`.
- A fresh reverted-code rerun on 2026-03-13 measured about `1.475 ms/op` for the same warmed end-to-end path, confirming session-to-session drift and forcing WP17 to re-establish its baseline locally before making more changes.
- After that controlled repro, three WP17 changes have now landed: a specialized single-rule fast matcher in `FastArrayQuerySupport`, a narrower read-path optimization in `ReflectionUtil`, and a 2026-03-14 join-build allocation cut that reuses one parent read buffer and stores single-child index entries without per-key `ArrayList` buckets.
- The current warmed end-to-end rerun at `size=10000` is about `0.654 ms/op` versus a current manual baseline rerun around `0.111 ms/op`; the current `-prof gc` rerun measures about `0.632 ms/op` and `2,307,857 B/op`.
- Warmed profiling is now also represented by `target/wp17-after-parent-buffer.jfr`, where the top current repository first-repo-frame CPU leaves are `ReflectionUtil$ResolvedFieldPath.read`, `FastArrayQuerySupport$ComputedFieldPlan.resolveValue`, `FastArrayQuerySupport.tryBuildJoinedState`, `FastArrayQuerySupport.buildChildIndex`, and `FastArrayQuerySupport.filterRows`.
- Warmed profiling is now represented by `target/pojolens-fastpath-current.jfr`, which shows the hot path has moved away from `ComputedFieldSupport.materializeRow`, `JoinEngine.mergeFields`, and `ReflectionUtil.collectQueryRowFieldTypes`.

## Active Work Areas

- `TODO.md` is still focused on profiler-driven follow-up work after WP14.
- WP14 is effectively accepted: the warmed 2026-03-13 JFR no longer shows `SqlExpressionEvaluator$Parser.*` among the dominant repository hotspots.
- WP15 and WP16 are effectively accepted on the selective single-join path after introducing `FastArrayQuerySupport`, array-row projection support in `ReflectionUtil`, and schema-based execution-plan dispatch in `FilterImpl`.
- Legacy `QueryRow` execution remains the fallback for broader shapes such as multi-join, collision-heavy, grouped, HAVING, distinct, and time-bucket flows; the fast path is intentionally narrow and benchmark-driven.
- WP17 is now the active implementation target, with `TODO.md` defining the next attack order around residual reflection reads, computed-field dependency lookup, join-state build/materialization, and projection writes on the selective single-join array path.
- An initial speculative WP17 implementation pass on 2026-03-13 regressed the warmed benchmark and was reverted; the follow-up controlled repro/profile loop then identified `matchesRuleGroups` and read-side reflection as the real current hotspots.
- WP17 is now actively moving again after landing the single-rule matcher fast path, the narrowed `ResolvedFieldPath.read` / `FlatRowReadPlan` read optimization, and the parent-buffer/single-child-bucket join-build optimization.
- The remaining gap on the selective single-join path is now concentrated in `ReflectionUtil$ResolvedFieldPath.read`, `FastArrayQuerySupport$ComputedFieldPlan.resolveValue`, residual `FastArrayQuerySupport.tryBuildJoinedState` / `buildChildIndex` work, `FastArrayQuerySupport.materializeJoinedRow`, and projection writes in `ReflectionUtil`.
- The new hot context is stable; deeper repository detail now lives in cold core files and indexes.

## Documentation Risks

- The previously known process-doc drift was fixed on 2026-03-13 in `CONTRIBUTING.md`, `MIGRATION.md`, and `RELEASE.md`.
- The doc-consistency scripts now check those benchmark-version and SQL-like capability invariants.
- The remaining documentation gap is that no artifact publication target is documented or declared in `pom.xml`.

## Next Validation Opportunities

- Reduce residual reflection cost in `ReflectionUtil$ResolvedFieldPath.read`; that is now the dominant first-repo-frame CPU leaf in the warmed profile.
- Reduce computed-field lookup overhead in `FastArrayQuerySupport$ComputedFieldPlan.resolveValue()` now that parent-buffer allocation is lower.
- Reduce the remaining child indexing / joined-row allocation cost in `FastArrayQuerySupport.buildChildIndex()` and `FastArrayQuerySupport.materializeJoinedRow()`.
- Reduce projection write overhead in `ReflectionUtil.applyProjectionWritePlan()` / `setResolvedFieldValue()` once the join-build tail is lower.
- Rerun the doc-consistency script when process docs or benchmark instructions change again.
- Regenerate AI indexes again after any source, test, or documentation structure change.
