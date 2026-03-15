# Handoff

## Resume Order

- Load hot context, then `TODO.md` if the task touches active performance work.
- Load `ai/state/benchmark-state.md` and `ai/core/benchmark-context.md` only for benchmark, profiler, or threshold tasks.
- Rebuild the benchmark runner with `mvn -B -ntp -Pbenchmark-runner -DskipTests package` before quoting fresh JMH numbers.

## Current Priority

- WP18 is the active package.
- The chart-path mapping and JSON-export work now look mostly solved for the current bean-backed stats workloads.
- The remaining likely bottleneck is SQL-like setup/query execution after prepared-shape reuse, direct bean stats aggregation, direct array-row chart mapping, flat-read-plan caching, and alias-safe chart projection.

## Guardrails

- Do not reopen the broader `FilterImpl` raw-row delegation without a new hypothesis; it regressed the short `size=10000` reruns and was not kept.
- Do not reopen WP17 micro-tuning unless a fresh clean-tree profile shows a high-confidence win on the selective single-join path.
- Judge SQL-like work by absolute `ms/op`, allocation, and product value; fluent-vs-SQL-like ratios are diagnostic only.

## Useful Files

- `TODO.md`
- `BENCHMARKS.md`
- `ai/state/benchmark-state.md`
- `ai/core/benchmark-context.md`
- `src/main/java/laughing/man/commits/sqllike/SqlLikeQuery.java`
- `src/main/java/laughing/man/commits/sqllike/internal/execution/SqlLikeExecutionSupport.java`
- `src/main/java/laughing/man/commits/filter/FastStatsQuerySupport.java`
- `src/main/java/laughing/man/commits/chart/ChartMapper.java`

## Next Validation

- Add a setup-focused microbenchmark or capture a fresh profile for the remaining prepared SQL-like stats/query cost.
- After code changes, rerun focused regressions plus `mvn -q test`.
- Use exact targeted `StatsQueryJmhBenchmark` reruns only as follow-up evidence, not as the sole attribution source when the session is already drift-heavy.
