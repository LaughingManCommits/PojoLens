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

## Problem Areas

- SQL-like chart mapping parity is the clearest current hotspot.
  - Every chart parity row failed despite generous absolute budgets.
  - The main gap is SQL-like `BAR`, `LINE`, `PIE`, and `AREA` mapping relative to fluent mapping; `SCATTER` also fails but by smaller multiples.
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

- Investigate SQL-like chart mapping parity failures before treating chart performance as healthy.
- Profile allocation sources in window rank/running-total execution, computed-field join materialization, and reflection/projection conversion.
- Keep recommending lazy stream consumption for first-page or bounded-window callers.
- Keep index hints scoped to repeated hot snapshots; avoid selling them as a cold one-shot optimization.
- Revisit benchmark threshold tightness once the parity and allocation hotspots above are understood.
