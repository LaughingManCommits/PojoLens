# Current State

## Repository Health

- The repository remains a single-module Java library with the expected `jar` packaging and CI/test structure.
- The AI memory system was compacted into hot and cold tiers on 2026-03-13.
- `mvn -q test` passed again on 2026-03-14 after the latest WP17 bound-expression optimization.
- Focused validation also passed again on 2026-03-14: `SqlExpressionEvaluatorTest`, `FilterImplFastPathTest`, `FilterQueryBuilderSelectiveMaterializationTest`, and `PojoLensJoinJmhBenchmarkParityTest`.
- The benchmark runner was rebuilt with `mvn -q -DskipTests -Pbenchmark-runner package` before rerunning JMH, so the current measurements come from the updated code.
- `mvn -q test` passed again on 2026-03-14 after the first WP18 runtime redesign.
- `mvn -q test` passed again on 2026-03-14 after the second WP18 chart-path redesign.
- `mvn -q test` passed again on 2026-03-15 after the third WP18 chart-export pass.
- A broader benchmark sweep was captured on 2026-03-14 across the core, chart, baseline, cache, hotspot, and standalone legacy suites; `BENCHMARKS.md` and `ai/state/benchmark-state.md` summarize it, and chart thresholds currently pass with headroom while the old fluent-vs-SQL-like chart parity report is now treated as diagnostic-only.
- A rebuilt 2026-03-15 chart-suite rerun plus strict threshold check still passed `45/45` after the chart-export pass; comparable cold `scatterPayloadJsonExport` timings fell to about `0.066`, `0.634`, and `1.146 ms/op` for sizes `1000`, `10000`, and `100000`, and a targeted `size=10000` `-prof gc` rerun measured about `0.367 ms/op` and `580,857 B/op`.
- Exact targeted WP18 JMH reruns on 2026-03-14 at `size=10000`, `-f 1 -wi 3 -i 5 -r 250ms` now measure `StatsQueryJmhBenchmark.fluentTimeBucketMetricsToChart` at about `5.045 ms/op` and `sqlLikeParseAndTimeBucketMetricsToChart` at about `4.891 ms/op`, down materially from the earlier same-session snapshot around `7.200 ms/op` and `7.088 ms/op`.
- Matching exact query-only reruns now measure `fluentTimeBucketMetrics` at about `4.990 ms/op` and `sqlLikeParseAndTimeBucketMetrics` at about `4.728 ms/op`, so chart-mapping overhead on that stats workload is now close to noise and the old projection round-trip is no longer the main absolute chart cost there.
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
- The first WP18 runtime redesign landed on 2026-03-14: non-subquery SQL-like executions now cache a prepared validated/bound shape and rebind a request-scoped `FilterQueryBuilder` per call instead of rebuilding the fluent pipeline every execution.
- The same WP18 change also disables that prepared-shape reuse for subquery-bearing SQL-like queries, because subquery result sets depend on the current execution rows and join bindings.
- The second WP18 redesign landed on 2026-03-14: fluent and SQL-like chart execution now map directly from internal `QueryRow` results, and `ChartMapper` uses indexed `QueryRow` field reads instead of round-tripping through caller projection objects.
- The third WP18 chart-export pass landed on 2026-03-15: `ChartPayloadJsonExporter` now pre-sizes its payload buffer and appends fixed-scale numeric values directly instead of using per-point `String.format(...)`.
- A narrower WP18 consolidation pass on 2026-03-15 now precomputes reusable raw execution-plan cache keys for prepared non-join SQL-like stats shapes, so repeated aliased stats filters and chart executions reuse the existing stats plan cache without rebuilding that key from the rebound builder each call.
- An initial speculative WP17 implementation pass on 2026-03-13 regressed the warmed benchmark and was reverted; the follow-up controlled repro/profile loop then identified `matchesRuleGroups` and read-side reflection as the real current hotspots.
- The last round of WP17 work landed the single-rule matcher fast path, the narrowed `ResolvedFieldPath.read` / `FlatRowReadPlan` read optimization, the parent-buffer/single-child-bucket join-build optimization, and bound array-expression evaluation before the package was parked as good enough.
- Two immediate follow-up WP17 directions on 2026-03-14 were explicitly ruled out for now: compiled `ReflectionUtil` accessors and early join-time filtering/materialization both regressed the long warmed benchmark and were reverted on the same clean tree.
- The remaining gap on the selective single-join path is now concentrated in `ReflectionUtil$ResolvedFieldPath.read`, residual `FastArrayQuerySupport.tryBuildJoinedState` / `buildChildIndex` work, `FastArrayQuerySupport.applyComputedValues` / `castNumericValue`, `FastArrayQuerySupport.materializeJoinedRow`, and projection writes in `ReflectionUtil`.
- Benchmark stress outside WP17 is now most visible in absolute chart/SQL-like overhead, cold filter-vs-baseline gaps, and hotspot allocation in computed join / reflection conversion microbenchmarks rather than in hard threshold failures.
- The latest exact targeted WP18 JMH reruns at `size=10000`, `-f 1 -wi 3 -i 5 -r 250ms` now measure `StatsQueryJmhBenchmark.sqlLikeParseAndTimeBucketMetricsToChart` at about `4.891 ms/op` and `fluentTimeBucketMetricsToChart` at about `5.045 ms/op`, with the corresponding query-only runs at about `4.728 ms/op` and `4.990 ms/op`.
- The newer short 2026-03-15 WP18 consolidation reruns at `size=10000`, `-f 1 -wi 2 -i 3 -r 200ms` stayed near baseline rather than producing a clear win: `sqlLikeParseAndTimeBucketMetrics` measured about `4.889 ms/op` and `sqlLikeParseAndTimeBucketMetricsToChart` about `4.731 ms/op`; a broader `FilterImpl` raw-row delegation was tried first and not kept after regressing those short reruns to about `5.404 ms/op` and `5.034 ms/op`.
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
- On WP18, the time-bucket chart mapping path and the benchmark JSON export path now look mostly solved; keep the newer prepared-key consolidation, avoid retrying the broader `FilterImpl` raw-row delegation without fresh evidence, and next isolate the remaining absolute cost in SQL-like setup/query execution or any remaining real chart assembly path.
- Rerun the doc-consistency script when process docs or benchmark instructions change again.
- Regenerate AI indexes again after any source, test, or documentation structure change.
