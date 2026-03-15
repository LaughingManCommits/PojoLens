# Current State

## Repository Health

- The repository remains a single-module Maven Java library that builds a `jar` on Java `17`.
- The latest recorded full suite is `mvn -q test` on `2026-03-15`, which passed with `431` tests.
- AI memory was compacted on `2026-03-15`; hot context now carries only startup-critical facts, while detailed benchmark history stays in `ai/state/benchmark-state.md` and `BENCHMARKS.md`.

## Active Work

- `TODO.md` remains the source-of-truth backlog.
- WP18 is the active package: reduce absolute SQL-like stats/query and chart overhead by product value and `ms/op`, not by fluent-vs-SQL-like ratios.
- WP17 selective single-join fast-path work is parked as good enough for now; reopen it only if a fresh profile shows a clear benchmark-backed win.

## Landed WP18 Shape

- Non-subquery SQL-like executions now reuse prepared validated/bound shapes and rebind per-call builders.
- Chart execution maps directly from internal rows, and fast stats charts can stay on indexed `Object[]` rows.
- `ChartPayloadJsonExporter` now writes fixed-scale numeric payloads directly instead of using per-point `String.format(...)`.
- Prepared non-join stats shapes precompute reusable execution-plan cache keys, snapshot construction avoids a duplicate `QuerySpec` copy, `preparedExecutionView(...)` avoids full builder cloning on the bean-backed prepared path, `ReflectionUtil` caches flat read plans, and SQL-like execution reuses one per-call run state.
- Aliased fast-stats filter/chart execution now keeps projection names correct on both direct and grouped fallback paths.

## Current Evidence

- Chart guardrails still pass; the latest rebuilt chart-suite rerun on `2026-03-15` stayed `45/45`.
- Exact targeted reruns from `2026-03-14` at `size=10000`, `-f 1 -wi 3 -i 5 -r 250ms` measured:
  - `sqlLikeParseAndTimeBucketMetrics`: about `4.728 ms/op`
  - `sqlLikeParseAndTimeBucketMetricsToChart`: about `4.891 ms/op`
  - `fluentTimeBucketMetrics`: about `4.990 ms/op`
  - `fluentTimeBucketMetricsToChart`: about `5.045 ms/op`
- Later short `2026-03-15` reruns were drift-heavy, but after the alias/chart pass SQL-like chart still ran below SQL-like query (`4.764 ms/op` vs `6.735 ms/op`), which points more toward remaining setup/query cost than chart assembly.
- A new prepared SQL-like stats hotspot microbenchmark now isolates rebinding from full fast-stats setup. Its first `size=10000`, `-f 1 -wi 2 -i 5 -r 100ms -prof gc` run measured rebind view at about `0.324 us/op` / `3,120 B/op` versus copy at about `0.668 us/op` / `3,528 B/op`, while full rebind-plus-fast-stats setup stayed around `7.517 ms/op` / `16,785,798 B/op` for view versus `7.306 ms/op` / `16,786,181 B/op` for copy.

## Current Risks

- Short whole-query JMH remains too noisy for patch attribution on its own.
- Builder rebinding is no longer the default suspect on its own; the new microbenchmark shows the remaining full fast-stats setup cost is dominated by row-scan/aggregation work.
- The next likely WP18 gain is to profile or microbenchmark the remaining SQL-like query/setup path beyond rebinding before another broad refactor.
