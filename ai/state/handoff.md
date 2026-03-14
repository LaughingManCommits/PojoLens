# Handoff

## Next Work Tasks

- The newest clean-tree long warmed rerun on unchanged code measured about `0.892 ms/op` at `size=10000` versus a current manual baseline rerun around `0.111 ms/op`.
- The current short allocation-focused rerun is still about `0.618 ms/op` / `2,307,905 B/op`, with `gc.count` at `8` and `gc.time` at `5 ms`; the long warmed and short allocation reruns are diverging again on unchanged code.
- A broader 2026-03-14 benchmark sweep is summarized in `BENCHMARKS.md` and `ai/state/benchmark-state.md`: all core/chart thresholds passed, the old chart parity report is now diagnostic-only rather than gated, and the hotspot suite still points to computed join / reflection conversion allocation pressure.
- WP18 is the active package now: reduce absolute chart and SQL-like chart overhead by product value and `ms/op`, not by fluent-vs-SQL-like ratio, and allow larger redesigns when they have the clearer path to a real win.
- The first WP18 runtime redesign is now in: non-subquery SQL-like executions cache a prepared validated/bound shape and rebind a request-scoped `FilterQueryBuilder` per execution, which also avoids the extra `copyOnBuild` snapshot on the hot path.
- Subquery-bearing SQL-like queries intentionally bypass that prepared-shape cache for correctness, because their bound value sets depend on the current execution rows and join bindings.
- The second WP18 redesign is now in: fluent and SQL-like chart execution map directly from internal `QueryRow` results, and `ChartMapper` now uses indexed `QueryRow` field reads instead of projecting to caller classes and then reflecting back over those objects.
- Exact targeted 2026-03-14 JMH reruns at `size=10000`, `-f 1 -wi 3 -i 5 -r 250ms` now measure `StatsQueryJmhBenchmark.sqlLikeParseAndTimeBucketMetricsToChart` at about `4.891 ms/op` and `fluentTimeBucketMetricsToChart` at about `5.045 ms/op`.
- Matching exact query-only reruns now measure `StatsQueryJmhBenchmark.sqlLikeParseAndTimeBucketMetrics` at about `4.728 ms/op` and `fluentTimeBucketMetrics` at about `4.990 ms/op`, so chart-mapping overhead on that stats workload is now close to noise.
- The benchmark memory now also records the recurring warmed JFR hotspot clusters in `BENCHMARKS.md` and `ai/core/benchmark-context.md`; the common class-level stress is concentrated in `ReflectionUtil` and `FastArrayQuerySupport`.
- Two speculative WP17 follow-up directions were tried on 2026-03-14 and reverted on the same clean tree after benchmark regressions: compiled `ReflectionUtil` accessors and early join-time filtering/materialization inside `FastArrayQuerySupport`.
- The newest 2026-03-14 change bound compiled numeric expressions to direct array indexes once per plan in `SqlExpressionEvaluator`, and `FastArrayQuerySupport.applyComputedValues()` now uses that bound form instead of per-row lambda/string dependency resolution.
- The prior 2026-03-14 change reused one parent read buffer across `tryBuildJoinedState()` and changed `buildChildIndex()` to store single-child matches directly in the hash index, only promoting to `List<Object[]>` on actual fan-out.
- The current warmed JFR artifact is `target/wp17-after-bound-expression.jfr`.
- Follow the refreshed `TODO.md` order: treat WP17 as good enough for now, attack absolute chart/SQL-like overhead next, then the broader reflection/conversion hotspot suite, and rebaseline budgets last.
- Prefer a deeper internal redesign over compatibility-preserving tweaks: compiled accessor chains or method handles, narrower predicate plans, and cheaper projection materialization are all on the table.
- The next WP18 opportunities after the prepared execution and direct `QueryRow` chart-mapping changes are likely remaining SQL-like setup/query cost on stats charts or chart serialization/export paths such as `ChartVisualizationJmhBenchmark.scatterPayloadJsonExport`.
- Small local tweaks are no longer preferred by default; larger redesigns are explicitly allowed when they have the clearer path to a real benchmark win while keeping feature behavior substantially similar.
- The first speculative WP17 code experiments in this session regressed the warmed benchmark and were reverted before the current benchmark-backed pass landed; keep driving changes from fresh profiles and rebuilt benchmark jars.
- The matcher/read-path/parent-buffer/bound-expression changes that did land are in `src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java`, `src/main/java/laughing/man/commits/util/ReflectionUtil.java`, and `src/main/java/laughing/man/commits/sqllike/internal/expression/SqlExpressionEvaluator.java`.
- The newest warmed profile is `target/wp17-after-bound-expression.jfr`. Its current first-repo-frame CPU hotspots are `ReflectionUtil$ResolvedFieldPath.read` (`832`), `FastArrayQuerySupport.applyComputedValues` (`313`), `FastArrayQuerySupport.tryBuildJoinedState` (`236`), `FastArrayQuerySupport.buildChildIndex` (`213`), and `ReflectionUtil.applyProjectionWritePlan` (`151`).
- The newest warmed profile's first-repo-frame allocation hotspots are `FastArrayQuerySupport.buildChildIndex` (`6824`), `FastArrayQuerySupport.materializeJoinedRow` (`4731`), `ReflectionUtil$ResolvedFieldPath.read` (`2467`), `ReflectionUtil.readFlatRowValues` (`715`), and `FastArrayQuerySupport.castNumericValue` (`671`).
- If WP17 is reopened later, start with a fresh clean-tree controlled repro/profile loop before another code change; after that, target residual reflection reads or joined-row/index allocation, and require an allocation win as well as a CPU win.
- If the task is about broader profiling rather than immediate code changes, load `ai/core/benchmark-context.md` for the cross-JFR hotspot clusters before choosing the next package.
- Keep process docs aligned if benchmark commands, release steps, or SQL-like capability boundaries change again.

## Relevant Files

- `TODO.md`
- `docs/benchmarking.md`
- `.github/workflows/ci.yml`
- `BENCHMARKS.md`
- `ai/state/benchmark-state.md`
- `ai/core/benchmark-context.md`
- `benchmarks/thresholds.json`
- `scripts/benchmark-suite-main.args`
- `scripts/benchmark-suite-hotspots.args`
- `src/main/java/laughing/man/commits/computed/internal/ComputedFieldSupport.java`
- `src/main/java/laughing/man/commits/filter/JoinEngine.java`
- `src/main/java/laughing/man/commits/filter/FilterImpl.java`
- `src/main/java/laughing/man/commits/chart/ChartMapper.java`
- `src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java`
- `src/main/java/laughing/man/commits/sqllike/internal/expression/SqlExpressionEvaluator.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java`
- `src/main/java/laughing/man/commits/filter/FilterExecutionPlan.java`
- `src/main/java/laughing/man/commits/sqllike/SqlLikeQuery.java`
- `target/pojolens-fastpath-current.jfr`
- `target/wp17-after-readpath.jfr`
- `target/wp17-after-parent-buffer.jfr`
- `target/wp17-after-bound-expression.jfr`
- `src/test/java/laughing/man/commits/filter/FilterImplFastPathTest.java`
- `src/test/java/laughing/man/commits/builder/FilterQueryBuilderSelectiveMaterializationTest.java`
- `src/test/java/laughing/man/commits/benchmark/PojoLensJoinJmhBenchmarkParityTest.java`
- `src/test/java/laughing/man/commits/sqllike/internal/expression/SqlExpressionEvaluatorTest.java`
- `src/test/java/laughing/man/commits/SqlLikeChartIntegrationTest.java`
- `src/test/java/laughing/man/commits/ChartResultMapperMappingTest.java`
- `src/test/java/laughing/man/commits/ChartResultMapperValidationTest.java`
- `src/test/java/laughing/man/commits/SqlLikeJoinTest.java`
- `src/test/java/laughing/man/commits/SqlLikeTypedBindContractTest.java`

## Unresolved Questions

- Should broader multi-join, collision-heavy, or grouped flows get their own array-based path, or should the legacy `QueryRow` engine remain the long-tail fallback?
- Is the next best gain in compiled field access for `ReflectionUtil$ResolvedFieldPath.read`, or in reducing joined-row/index allocation plus projection/cast overhead now that computed dependency lookup is already bound away?
- Why are the current 300 ms warmed reruns drifting back toward `~0.892 ms/op` on unchanged code while the short `-prof gc` rerun still sits near `0.618 ms/op`? The next session should answer that before trusting another optimization result.
- Should projection stay on no-arg construction plus setter writes, or move to a cheaper constructor or record-style path for common benchmark projections?
- Can `FilterQueryBuilder.copySourceBeans()` be bypassed safely on the fast path now that internal compatibility constraints are relaxed?
- Is the next WP18 gain now in SQL-like setup/query execution or chart JSON export, now that the old projection-to-chart round-trip is removed and chart-mapping overhead on the time-bucket stats workload is close to noise?
