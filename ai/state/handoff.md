# Handoff

## Resume Order

- Load hot context, then `TODO.md` if the task touches active performance work.
- Load `ai/state/benchmark-state.md` and `ai/core/benchmark-context.md` only for benchmark, profiler, or threshold tasks.
- Rebuild the benchmark runner with `mvn -B -ntp -Pbenchmark-runner -DskipTests package` before quoting fresh JMH numbers.

## Current Priority

- WP18 is the active package.
- The bean-backed single-group time-bucket stats workload now looks mostly solved after the `2026-03-16` fast-stats pass.
- The latest prepared fast-stats microbenchmark dropped full setup to about `509.906 us/op` / `2,145,675 B/op` for copy and `512.749 us/op` / `2,145,195 B/op` for view at `size=10000`.
- The matching exact targeted whole-query reruns now measure fluent query/chart about `0.529` / `0.531 ms/op` and SQL-like query/chart about `0.519` / `0.526 ms/op`.
- The next likely WP18 follow-up is to validate other grouped, aliased, or multi-series SQL-like/chart shapes before doing more work on the already-fixed single-group time-bucket path.

## Guardrails

- Do not reopen the broader `FilterImpl` raw-row delegation without a new hypothesis; it regressed the short `size=10000` reruns and was not kept.
- Do not reopen WP17 micro-tuning unless a fresh clean-tree profile shows a high-confidence win on the selective single-join path.
- Judge SQL-like work by absolute `ms/op`, allocation, and product value; fluent-vs-SQL-like ratios are diagnostic only.
- Follow the `TODO.md` consolidation guidance: share plans and metadata first, and do not merge specialized bean, `QueryRow`, or `Object[]` execution loops just to remove duplication.
- Do not spend more time on prepared-view vs copy micro-tuning for this stats shape unless a fresh profile reopens it; the dominant 2026-03-15 setup cost was mostly removed by the 2026-03-16 single-group fast-stats and bucket-format pass.

## Useful Files

- `TODO.md`
- `BENCHMARKS.md`
- `ai/state/benchmark-state.md`
- `ai/core/benchmark-context.md`
- `src/main/java/laughing/man/commits/benchmark/HotspotMicroJmhBenchmark.java`
- `src/main/java/laughing/man/commits/sqllike/SqlLikeQuery.java`
- `src/main/java/laughing/man/commits/sqllike/internal/execution/SqlLikeExecutionSupport.java`
- `src/main/java/laughing/man/commits/filter/FastStatsQuerySupport.java`
- `src/main/java/laughing/man/commits/chart/ChartMapper.java`

## Next Validation

- Check whether other WP18 product-visible workloads still need work, especially grouped/aliased or multi-series SQL-like chart flows that do not match the fixed single-group time-bucket benchmark shape.
- After code changes, rerun focused regressions plus `mvn -q test`.
- Use exact targeted `StatsQueryJmhBenchmark` reruns only as follow-up evidence, not as the sole attribution source when the session is already drift-heavy.
