# TODO

## Benchmark Snapshot (2026-03-31)

- Full benchmark runner rebuilt with `mvn -B -ntp -Pbenchmark-runner -DskipTests package`.
- Artifacts captured under `target/benchmarks/2026-03-31-full/`:
  - `core.json`, `core-threshold-report.csv`
  - `charts/chart.json`, `charts/chart-threshold-report.csv`, `charts/chart-parity-report.csv`
  - `baseline-gc.json`, `cache.json`, `hotspots-gc.json`, `streaming-gc.json`, `window-gc.json`
  - `indexes-warm-gc.json`, `indexes-cold-gc.json`
  - `charts/images/*` plot exports

## Results

- Core thresholds: pass.
  - Closest core budget: `SqlLikePipelineJmhBenchmark.parseAndExplainExecution|size=10000` at `106.350 ms/op` vs `180.000 ms/op` budget (`59.1%` of limit).
  - Next closest core budgets: `parseAndFilterWindowRunningTotal|size=10000` at `126.816 ms/op` vs `260.000 ms/op` (`48.8%`), `parseAndFilterWindowRank|size=10000` at `128.237 ms/op` vs `270.000 ms/op` (`47.5%`).
- Chart thresholds: pass.
  - Closest chart budget: `ChartVisualizationJmhBenchmark.sqlLikeScatterMapping|size=10000` at `8.311 ms/op` vs `112.833 ms/op` (`7.4%` of limit).
  - Absolute chart budgets currently have very large headroom; they did not catch the parity regressions below.
- Chart parity: fail (`15/15` rows in `charts/chart-parity-report.csv`).
  - Worst ratios: `PIE|100000` `2363.090x`, `BAR|100000` `2299.236x`, `LINE|100000` `1382.223x`, `AREA|100000` `1170.540x`.
  - Scatter is materially better but still fails the parity gate: `2.022x` (`1k`), `2.168x` (`10k`), `2.599x` (`100k`).
- Cache throughput (`8` threads, warmed):
  - `sqlLikeParseHotSetConcurrent`: `120,218,830 ops/s`
  - `statsPlanBuildHotSetConcurrent`: `24,007.941 ops/s`
- Streams baseline:
  - `FilterProjection`: fluent is roughly parity on latency but allocates more than Streams (`1.139x` slower / `2.429x` more allocation at `1k`; `1.012x` slower / `2.694x` more allocation at `10k`).
  - `GroupedMetrics`: fluent is much faster and leaner than Streams (`0.044x` latency / `0.521x` allocation at `1k`; `0.005x` latency / `0.510x` allocation at `10k`).
  - `TimeBucketMetrics`: fluent is much faster and leaner than Streams (`0.018x` latency / `0.010x` allocation at both `1k` and `10k`).

## Follow-Up Result (2026-03-31 cache-reset rerun)

- Added a benchmark-harness fix so fluent JMH runs no longer reuse `FilterImpl` execution caches across invocations:
  - new helper `BenchmarkFilterCacheReset`
  - `@Setup(Level.Invocation)` resets in `ChartVisualizationJmhBenchmark` and `StatsQueryJmhBenchmark`
- Corrected artifacts captured under `target/benchmarks/2026-03-31-followup-cache-reset/`:
  - `core.json`, `core-threshold-report.csv`
  - `charts/chart.json`, `charts/chart-threshold-report.csv`, `charts/chart-parity-report.csv`
- Corrected core thresholds: pass.
- Corrected chart thresholds: pass.
- Corrected chart parity: fail only `3/15` rows, all `SCATTER`.
  - `SCATTER|1000`: `2.264x`
  - `SCATTER|10000`: `1.815x`
  - `SCATTER|100000`: `2.057x`
- The prior broad chart-parity blow-up was mostly benchmark-harness bias from warmed fluent caches.
  - `BAR|100000`: `2299.236x -> 1.643x`
  - `PIE|100000`: `2363.090x -> 1.491x`
  - `AREA|100000`: `1170.540x -> 1.273x`
  - `LINE|100000`: `1382.223x -> 0.233x` (`sqlLike` is faster than fluent in the corrected single-iteration run)
- The same cache-reset correction collapsed the stats-query gap back to near parity.
  - `GroupedMetrics`: `1.346x` at `1k`, `1.076x` at `10k`
  - `GroupedMetricsToChart`: `1.345x` at `1k`, `1.130x` at `10k`
  - `TimeBucketMetrics`: `1.291x` at `1k`, `1.194x` at `10k`
  - `TimeBucketMetricsToChart`: `1.325x` at `1k`, `1.285x` at `10k`

## Stability Result (2026-03-31 warmup+measurement rerun)

- Reran the corrected chart suite with steadier settings under `target/benchmarks/2026-03-31-followup-stability/charts-full/`:
  - `-wi 3 -i 5 -w 200ms -r 200ms`
  - reports: `chart.json`, `chart-threshold-report.csv`, `chart-parity-report.csv`
- Stable chart thresholds: pass.
  - Closest budget stays `sqlLikeScatterMapping|size=10000`: `1.268 ms/op` vs `112.833 ms/op` (`1.1%` of limit).
- Stable chart parity: pass (`15/15` rows).
  - Worst stable ratio is `SCATTER|100000` at `1.610x`.
  - Other largest stable ratios: `SCATTER|1000` `1.360x`, `SCATTER|10000` `1.281x`, `BAR|100000` `1.079x`.
  - `LINE|100000` settled at `0.884x`, so the earlier single-iteration inversion/noise is not a current concern.
- Stable scatter GC spot-checks live in `target/benchmarks/2026-03-31-followup-stability/chart-scatter-line-gc.json`.
  - Latency still passes parity: `1.350x` (`1k`), `1.246x` (`10k`), `1.592x` (`100k`).
  - Allocation remains meaningfully higher on SQL-like scatter: `1.70x` (`1k`), `1.89x` (`10k`), `4.77x` (`100k`).
  - `SCATTER|100000` alloc/op: fluent `4.99 MB/op` vs SQL-like `23.80 MB/op`.

## Bound Scatter Follow-Up (2026-03-31 reusable bind path)

- Added a non-join bound SQL-like reuse path so repeated `bindTyped(...).chart(...)` executions keep the prepared/materialized source rows instead of rebuilding them every call.
  - runtime change: `SqlLikePreparedExecutionSupport.ExecutionContext.reusableBoundContext()`
  - coverage: repeated bound scatter charts in `SqlLikeTypedBindContractTest`
- Added a targeted benchmark method `ChartVisualizationJmhBenchmark.sqlLikeBoundScatterMapping`.
- Cold threshold spot-check artifacts live in `target/benchmarks/2026-03-31-bound-scatter-threshold.json`.
  - `sqlLikeBoundScatterMapping|size=1000`: `0.542 ms/op`
  - `sqlLikeBoundScatterMapping|size=10000`: `5.542 ms/op`
  - `sqlLikeBoundScatterMapping|size=100000`: `23.055 ms/op`
- Warmed GC comparison artifacts live in `target/benchmarks/2026-03-31-followup-stability/chart-scatter-bound-gc.json`.
  - Bound scatter stays slightly faster than direct repeated SQL-like scatter: `1.136x` (`1k`), `1.178x` (`10k`), `1.061x` (`100k`) in favor of the bound path.
  - Bound scatter allocation is materially lower than direct repeated SQL-like scatter: `1.324x` (`1k`), `1.390x` (`10k`), `1.890x` (`100k`) less allocation on the bound path.
  - `SCATTER|100000` direct-vs-bound alloc/op: direct `23.80 MB/op` vs bound `12.59 MB/op`.
  - This isolates a meaningful part of the remaining SQL-like scatter overhead to repeated source rebind/materialization rather than the chart mapper itself.

## Problem Areas

- Stable chart latency parity is healthy after the cache-reset fix and a warmed rerun.
  - The remaining chart-specific concern is scatter allocation, not scatter latency parity.
  - In the targeted GC rerun, SQL-like scatter allocates `1.70x` more at `1k`, `1.89x` more at `10k`, and `4.77x` more at `100k`.
  - The new bound scatter rerun shows part of that overhead is avoidable on reusable bind-first workloads, but direct repeated SQL-like scatter is still the heavier default path.
- SQL-like window stages are allocation-heavy.
  - `parseAndFilterWindowRank|size=10000`: `1.630 ms/op`, `4,397,611.991 B/op`
  - `parseAndFilterWindowRunningTotal|size=10000`: `1.655 ms/op`, `4,594,287.429 B/op`
  - Versus `parseAndFilterWindowBaseline|size=10000`, window stages are about `3.2x` slower and `5.9x` to `6.2x` more allocative.
- Computed-field selective join materialization remains expensive.
  - `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization|size=10000`: `232.237 us/op`, `2,013,241.180 B/op`
- Reflection and projection conversion still dominate warm microbenchmark allocation.
  - `reflectionToClassList|size=10000`: `834.662 us/op`, `1,400,236.239 B/op`
  - `reflectionToDomainRows|size=10000`: `325.532 us/op`, `1,000,129.644 B/op`
  - `sqlLikePreparedStatsFastPathSetupCopy|size=10000`: `388.013 us/op`, `979,633.955 B/op`
  - `sqlLikePreparedStatsFastPathSetupView|size=10000`: `392.861 us/op`, `978,953.987 B/op`
- List materialization remains far costlier than lazy streaming for first-page-style consumers.
  - Fluent at `10k`: list vs lazy stream is `75.69x` slower and `60.20x` higher allocation.
  - SQL-like at `10k`: list vs lazy stream is `95.60x` slower and `69.19x` higher allocation.
- Index hints are still only justified for repeated hot workloads.
  - Warm `10k`: indexed is `0.27x` the scan latency (`190.489 us/op` vs `705.640 us/op`) but `5.60x` the allocation (`1,429,336.961 B/op` vs `255,323.554 B/op`).
  - Cold `10k`: indexed regresses to `2.04x` slower and `2.20x` more allocation than scan (`113,539.100 us/op`, `8,020,528.000 B/op`).

## Follow-Up

- Treat the broad chart-parity failure in `2026-03-31-full` as a benchmark artifact first, not a product regression.
- Treat the one-iteration cache-reset chart follow-up as noisy; use warmed multi-iteration reruns before opening parity bugs.
- If chart follow-up continues, profile SQL-like scatter allocation rather than chart latency parity.
- Prefer `bindTyped(...)` when the same SQL-like chart runs repeatedly against the same in-memory snapshot; the new bound scatter follow-up shows that reuse materially trims allocation.
- Profile allocation sources in window rank/running-total execution, computed-field join materialization, and reflection/projection conversion.
- Keep recommending lazy stream consumption for first-page or bounded-window callers.
- Keep index hints scoped to repeated hot snapshots; avoid selling them as a cold one-shot optimization.
- Revisit benchmark threshold tightness once the parity and allocation hotspots above are understood.
