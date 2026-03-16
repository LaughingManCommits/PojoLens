# Current State

## Repository Health

- The repository remains a single-module Maven Java library that builds a `jar` on Java `17`.
- The latest recorded full suite is `mvn -q test` on `2026-03-16`, which passed with `433` tests.
- AI memory was compacted on `2026-03-15`; hot context now carries only startup-critical facts, while detailed benchmark history stays in `ai/state/benchmark-state.md` and `BENCHMARKS.md`.

## Active Work

- `TODO.md` remains the source-of-truth backlog.
- WP18 is the active package: reduce absolute SQL-like stats/query and chart overhead by product value and `ms/op`, not by fluent-vs-SQL-like ratios.
- `TODO.md` now makes consolidation guidance explicit: share plans/metadata first, and keep bean, `QueryRow`, and `Object[]` hot loops specialized unless a merged path is benchmark-positive.
- WP17 selective single-join fast-path work is parked as good enough for now; reopen it only if a fresh profile shows a clear benchmark-backed win.

## Landed WP18 Shape

- Non-subquery SQL-like executions now reuse prepared validated/bound shapes and rebind per-call builders.
- Chart execution maps directly from internal rows, and fast stats charts can stay on indexed `Object[]` rows.
- `ChartPayloadJsonExporter` now writes fixed-scale numeric payloads directly instead of using per-point `String.format(...)`.
- Prepared non-join stats shapes precompute reusable execution-plan cache keys, snapshot construction avoids a duplicate `QuerySpec` copy, `preparedExecutionView(...)` avoids full builder cloning on the bean-backed prepared path, `ReflectionUtil` caches flat read plans, and SQL-like execution reuses one per-call run state.
- Aliased fast-stats filter/chart execution now keeps projection names correct on both direct and grouped fallback paths.
- `FastStatsQuerySupport` now has a dedicated single-group aggregation path, and `TimeBucketUtil` now renders fixed-shape bucket strings directly instead of using `String.format(...)` on every scanned row.

## Current Evidence

- Focused regressions (`TimeBucketAggregationTest`, `SqlLikeChartIntegrationTest`, `SqlLikeQueryContractTest`, `TimeBucketUtilTest`) plus full `mvn -q test` passed on `2026-03-16`.
- A rebuilt `2026-03-16` prepared fast-stats microbenchmark at `size=10000`, `-f 1 -wi 2 -i 5 -r 100ms -prof gc` now measures `sqlLikePreparedStatsFastPathSetupCopy` at about `509.906 us/op` / `2,145,675 B/op` and `sqlLikePreparedStatsFastPathSetupView` at about `512.749 us/op` / `2,145,195 B/op`, down materially from the prior `7.306/7.517 ms/op` and `16.8 MB/op` snapshot.
- Exact targeted reruns on `2026-03-16` at `size=10000`, `-f 1 -wi 3 -i 5 -r 250ms` now measure `fluentTimeBucketMetrics` at about `0.529 ms/op`, `fluentTimeBucketMetricsToChart` at about `0.531 ms/op`, `sqlLikeParseAndTimeBucketMetrics` at about `0.519 ms/op`, and `sqlLikeParseAndTimeBucketMetricsToChart` at about `0.526 ms/op`.
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
- This bean-backed single-group time-bucket stats shape now looks mostly solved; do not keep tuning it unless a fresh profile shows a new hotspot.
- Do not assume the same win automatically carries over to other grouped, aliased, or multi-series SQL-like/chart workloads without targeted validation.
- Prepared-view vs copy is no longer the dominant question on the full fast-stats setup path; further rebinding micro-tuning should stay parked unless a new profile reopens it.
