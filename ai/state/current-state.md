# Current State

## Repository Health

- The repository remains a single-module Maven Java library that builds a `jar` on Java `17`.
- The latest recorded full suite is `mvn -q test` on `2026-03-16`, which passed with `433` tests.
- AI memory was compacted on `2026-03-15`; hot context now carries only startup-critical facts, while detailed benchmark history stays in `ai/state/benchmark-state.md` and `BENCHMARKS.md`.

## Active Work

- `TODO.md` remains the source-of-truth backlog.
- WP19 is now the active package: reduce recurring reflection/conversion hotspot clusters in `ReflectionUtil` and `FastArrayQuerySupport`, and judge progress by both hotspot-suite numbers and warmed JFR movement.
- WP18 is parked as good enough for now after the 2026-03-16 stats/chart validation pass; reopen it only if a fresh profile shows real leverage in scatter mapping or another chart-heavy path.
- `TODO.md` now makes consolidation guidance explicit: share plans/metadata first, and keep bean, `QueryRow`, and `Object[]` hot loops specialized unless a merged path is benchmark-positive.
- WP17 selective single-join fast-path work is parked as good enough for now; reopen it only if a fresh profile shows a clear benchmark-backed win.

## Landed WP18 Shape

- Non-subquery SQL-like executions now reuse prepared validated/bound shapes and rebind per-call builders.
- Chart execution maps directly from internal rows, and fast stats charts can stay on indexed `Object[]` rows.
- `ChartPayloadJsonExporter` now writes fixed-scale numeric payloads directly instead of using per-point `String.format(...)`.
- Prepared non-join stats shapes precompute reusable execution-plan cache keys, snapshot construction avoids a duplicate `QuerySpec` copy, `preparedExecutionView(...)` avoids full builder cloning on the bean-backed prepared path, `ReflectionUtil` caches flat read plans, and SQL-like execution reuses one per-call run state.
- Aliased fast-stats filter/chart execution now keeps projection names correct on both direct and grouped fallback paths.
- `FastStatsQuerySupport` now has a dedicated single-group aggregation path, and `TimeBucketUtil` now renders fixed-shape bucket strings directly instead of using `String.format(...)` on every scanned row.

## Landed WP19 Increment

- `ReflectionUtil.applyProjectionWritePlan(...)` now skips no-op `ObjectUtil.castValue(...)` calls when the projected raw value already matches the resolved leaf type.
- `ReflectionUtilTest` now covers nested projection materialization from `Object[]` rows so future WP19 refactors keep the array-row projection path correct.

## Current Evidence

- Focused regressions (`TimeBucketAggregationTest`, `SqlLikeChartIntegrationTest`, `SqlLikeQueryContractTest`, `TimeBucketUtilTest`) plus full `mvn -q test` passed on `2026-03-16`.
- A rebuilt `2026-03-16` prepared fast-stats microbenchmark at `size=10000`, `-f 1 -wi 2 -i 5 -r 100ms -prof gc` now measures `sqlLikePreparedStatsFastPathSetupCopy` at about `509.906 us/op` / `2,145,675 B/op` and `sqlLikePreparedStatsFastPathSetupView` at about `512.749 us/op` / `2,145,195 B/op`, down materially from the prior `7.306/7.517 ms/op` and `16.8 MB/op` snapshot.
- Exact targeted reruns on `2026-03-16` at `size=10000`, `-f 1 -wi 3 -i 5 -r 250ms` now measure `fluentTimeBucketMetrics` at about `0.529 ms/op`, `fluentTimeBucketMetricsToChart` at about `0.531 ms/op`, `sqlLikeParseAndTimeBucketMetrics` at about `0.519 ms/op`, and `sqlLikeParseAndTimeBucketMetricsToChart` at about `0.526 ms/op`.
- Matching exact targeted reruns on `2026-03-16` now also measure grouped stats query/chart at about `0.273`, `0.273`, `0.277`, and `0.264 ms/op` for fluent grouped query, fluent grouped chart, SQL-like grouped query, and SQL-like grouped chart at `size=10000`.
- Exact targeted reruns on `2026-03-16` further measure multi-series line/area chart mapping at about `0.566`, `0.575`, `0.577`, and `0.572 ms/op` for fluent line, fluent area, SQL-like line, and SQL-like area at `size=10000`.
- Chart guardrails still pass; the latest rebuilt chart-suite rerun on `2026-03-15` stayed `45/45`.
- A rebuilt full chart guardrail rerun on `2026-03-16` still passed `45/45`; the largest remaining cold chart-mapping scores were scatter mapping at about `2.924 ms/op` fluent and `4.613 ms/op` SQL-like for `size=10000`, and about `19.743` / `24.991 ms/op` at `size=100000`.
- A rebuilt full hotspot-suite rerun on `2026-03-16` with `@scripts/benchmark-suite-hotspots.args -f 1 -wi 1 -i 3 -r 100ms -prof gc` now measures `reflectionToClassList|size=10000` at about `852.025 us/op` / `1,400,236 B/op` and `reflectionToDomainRows|size=10000` at about `418.191 us/op` / `2,840,026 B/op`, versus the recorded `2026-03-14` snapshot of `1115.501 us/op` / `1,400,238 B/op` and `557.219 us/op` / `2,840,027 B/op`.
- The same `2026-03-16` hotspot-suite rerun measures `computedFieldJoinSelectiveMaterialization|size=10000` at about `303.247 us/op` / `3,532,314 B/op` and `groupedMultiMetricAggregation|size=10000` at about `353.796 us/op` / `1,043,042 B/op`.
- A refreshed warmed JFR on `2026-03-16` produced `target/wp19-current-2026-03-16.jfr` and measured `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField|size=10000` at about `0.666 ms/op` with JFR overhead, which is effectively flat versus the last recorded warm-profile cycle.
- That refreshed warmed JFR still concentrates CPU in `ReflectionUtil$ResolvedFieldPath.read` (`837`), `FastArrayQuerySupport.applyComputedValues` (`399`), `ReflectionUtil.applyProjectionWritePlan` (`270`), `FastArrayQuerySupport.tryBuildJoinedState` (`241`), and `FastArrayQuerySupport.buildChildIndex` (`211`); allocation still centers in `ResolvedFieldPath.read` (`4220`), `materializeJoinedRow` (`3684`), and `buildChildIndex` (`3117`).
- Two post-profile micro-optimizations on `2026-03-16` were benchmark-flat and were not kept: a specialized nested-write path in `ReflectionUtil` and a `Double` fast path in `FastArrayQuerySupport.applyComputedValues`.
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
- If WP18 stays open, scatter mapping is now the clearest remaining chart workload to profile directly because grouped stats and grouped line/area chart shapes are already well below their guardrails.
- WP19 is not done just because the hotspot-suite latencies fell; reflection/conversion allocations are still essentially flat and the refreshed warmed JFR kept the same dominant class cluster.
- The refreshed warmed JFR now exists, and it shows the same class cluster still dominating. The next WP19 attempt should go after `FastArrayQuerySupport.materializeJoinedRow` / `buildChildIndex` or a more structural `ReflectionUtil` read-path reuse idea, not more branch-level micro-tweaks.
