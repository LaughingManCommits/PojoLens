# Current State

## Repository Health

- The repository remains a single-module Java library with the expected `jar` packaging and CI/test structure.
- The AI memory system was compacted into hot and cold tiers on 2026-03-13.
- `mvn -q test` passed again on 2026-03-14 after the latest WP17 bound-expression optimization.
- Focused validation also passed again on 2026-03-14: `SqlExpressionEvaluatorTest`, `FilterImplFastPathTest`, `FilterQueryBuilderSelectiveMaterializationTest`, and `PojoLensJoinJmhBenchmarkParityTest`.
- The benchmark runner was rebuilt with `mvn -q -DskipTests -Pbenchmark-runner package` before rerunning JMH, so the current measurements come from the updated code.
- A broader benchmark sweep was captured on 2026-03-14 across the core, chart, baseline, cache, hotspot, and standalone legacy suites; `BENCHMARKS.md` and `ai/state/benchmark-state.md` summarize it, and chart thresholds currently pass with headroom while the old fluent-vs-SQL-like chart parity report is now treated as diagnostic-only.
- `BENCHMARKS.md`, `ai/state/benchmark-state.md`, and `ai/core/benchmark-context.md` now also capture the recurring warmed JFR hotspot clusters; the common class-level stress is concentrated in `ReflectionUtil` and `FastArrayQuerySupport`.
- The earlier warmed end-to-end benchmark at `size=10000` measured about `1.091 ms/op` versus a current manual baseline rerun around `0.150 ms/op`; `-prof gc` measured about `1.441 ms/op` and `3,107,771 B/op`.
- A fresh reverted-code rerun on 2026-03-13 measured about `1.475 ms/op` for the same warmed end-to-end path, confirming session-to-session drift and forcing WP17 to re-establish its baseline locally before making more changes.
- After that controlled repro, four WP17 changes have now landed: a specialized single-rule fast matcher in `FastArrayQuerySupport`, a narrower read-path optimization in `ReflectionUtil`, a 2026-03-14 join-build allocation cut that reuses one parent read buffer and stores single-child index entries without per-key `ArrayList` buckets, and direct array-index binding for compiled numeric expressions in `SqlExpressionEvaluator` / `FastArrayQuerySupport`.
- The last landed WP17 improvement still measures about `0.617 ms/op` and `2,307,905 B/op` on the current short `-prof gc` rerun, but current clean-tree long warmed reruns on unchanged code have drifted back up to about `0.892 ms/op` versus a current manual baseline rerun around `0.111 ms/op`.
- Two additional WP17 follow-up experiments on 2026-03-14 targeted compiled `ReflectionUtil` accessors and early join-time filtering/materialization, but both regressed the warmed benchmark and were reverted; the git tree is clean again.
- Warmed profiling is now also represented by `target/wp17-after-bound-expression.jfr`, where the top current repository first-repo-frame CPU leaves are `ReflectionUtil$ResolvedFieldPath.read`, `FastArrayQuerySupport.applyComputedValues`, `FastArrayQuerySupport.tryBuildJoinedState`, `FastArrayQuerySupport.buildChildIndex`, and `ReflectionUtil.applyProjectionWritePlan`.
- Warmed profiling is now represented by `target/pojolens-fastpath-current.jfr`, which shows the hot path has moved away from `ComputedFieldSupport.materializeRow`, `JoinEngine.mergeFields`, and `ReflectionUtil.collectQueryRowFieldTypes`.

## Active Work Areas

- `TODO.md` now parks WP17 as good enough for now, treats larger redesigns as allowed when they buy real wins, and focuses next on absolute chart/SQL-like overhead plus the broader benchmark backlog exposed by the full 2026-03-14 sweep.
- WP14 is effectively accepted: the warmed 2026-03-13 JFR no longer shows `SqlExpressionEvaluator$Parser.*` among the dominant repository hotspots.
- WP15 and WP16 are effectively accepted on the selective single-join path after introducing `FastArrayQuerySupport`, array-row projection support in `ReflectionUtil`, and schema-based execution-plan dispatch in `FilterImpl`.
- Legacy `QueryRow` execution remains the fallback for broader shapes such as multi-join, collision-heavy, grouped, HAVING, distinct, and time-bucket flows; the fast path is intentionally narrow and benchmark-driven.
- WP17 is now considered good enough for the moment; its remaining reflection/join/projection tail stays documented, but it is no longer the default active package.
- WP18 is now the active package: reduce absolute chart and SQL-like chart cost by product value and `ms/op`, not by fluent-vs-SQL-like ratio, and allow larger redesigns when they have clearer benchmark upside.
- An initial speculative WP17 implementation pass on 2026-03-13 regressed the warmed benchmark and was reverted; the follow-up controlled repro/profile loop then identified `matchesRuleGroups` and read-side reflection as the real current hotspots.
- The last round of WP17 work landed the single-rule matcher fast path, the narrowed `ResolvedFieldPath.read` / `FlatRowReadPlan` read optimization, the parent-buffer/single-child-bucket join-build optimization, and bound array-expression evaluation before the package was parked as good enough.
- Two immediate follow-up WP17 directions on 2026-03-14 were explicitly ruled out for now: compiled `ReflectionUtil` accessors and early join-time filtering/materialization both regressed the long warmed benchmark and were reverted on the same clean tree.
- The remaining gap on the selective single-join path is now concentrated in `ReflectionUtil$ResolvedFieldPath.read`, residual `FastArrayQuerySupport.tryBuildJoinedState` / `buildChildIndex` work, `FastArrayQuerySupport.applyComputedValues` / `castNumericValue`, `FastArrayQuerySupport.materializeJoinedRow`, and projection writes in `ReflectionUtil`.
- Benchmark stress outside WP17 is now most visible in absolute chart/SQL-like overhead, cold filter-vs-baseline gaps, and hotspot allocation in computed join / reflection conversion microbenchmarks rather than in hard threshold failures.
- The broader performance backlog after WP17 is now explicit: absolute chart/SQL-like overhead work, recurring reflection/conversion hotspot work, and only then budget rebaselining.
- The new hot context is stable; deeper repository detail now lives in cold core files and indexes.

## Documentation Risks

- The previously known process-doc drift was fixed on 2026-03-13 in `CONTRIBUTING.md`, `MIGRATION.md`, and `RELEASE.md`.
- The doc-consistency scripts now check those benchmark-version and SQL-like capability invariants.
- The remaining documentation gap is that no artifact publication target is documented or declared in `pom.xml`.

## Next Validation Opportunities

- Reduce residual reflection cost in `ReflectionUtil$ResolvedFieldPath.read`; that is now the dominant first-repo-frame CPU leaf in the warmed profile.
- Re-establish a stable long warmed baseline or fresh clean-tree profile before landing another WP17 code change; current 300 ms warmed reruns are drifting away from the short allocation-focused reruns on unchanged code.
- Reduce residual computed evaluation and numeric-cast overhead in `FastArrayQuerySupport.applyComputedValues()` / `castNumericValue()` now that dependency lookup is bound away.
- Reduce the remaining child indexing / joined-row allocation cost in `FastArrayQuerySupport.buildChildIndex()` and `FastArrayQuerySupport.materializeJoinedRow()`.
- Reduce projection write overhead in `ReflectionUtil.applyProjectionWritePlan()` / `setResolvedFieldValue()` once the join-build tail is lower.
- Rerun the doc-consistency script when process docs or benchmark instructions change again.
- Regenerate AI indexes again after any source, test, or documentation structure change.
