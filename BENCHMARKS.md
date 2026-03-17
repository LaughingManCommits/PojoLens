# Benchmark Report

Generated from a full local benchmark sweep on 2026-03-17.

## Scope

- Rebuilt benchmark runner: `mvn -B -ntp -Pbenchmark-runner -DskipTests package`
- Core guardrail suite: `@scripts/benchmark-suite-main.args -f 1 -wi 0 -i 1 -r 100ms`
- Chart guardrail suite: `@scripts/benchmark-suite-chart.args -f 1 -wi 0 -i 1 -r 100ms`
- Streams baseline suite: `@scripts/benchmark-suite-baseline.args -f 1 -wi 0 -i 1 -r 100ms`
- Cache concurrency suite: `@scripts/benchmark-suite-cache.args -t 8 -f 1 -wi 0 -i 1 -r 100ms`
- Hotspot suite with allocation profiler: `@scripts/benchmark-suite-hotspots.args -f 1 -wi 1 -i 3 -r 100ms -prof gc`
- Standalone legacy benchmark: `laughing.man.commits.benchmark.PojoLensJmhBenchmark.* -f 1 -wi 0 -i 1 -r 100ms`

## Caveats

- The core/chart/baseline/cache/legacy runs are cold guardrail-style snapshots with `0` warmup iterations and a single `100ms` measurement. They are useful for regression control, not for comparing directly to warmed tuning runs.
- Hotspot numbers use `-prof gc`, `1` warmup iteration, and `3` measurement iterations. Use those for allocation and stress analysis, not for direct comparison with the cold guardrail suite.
- Cache results are `thrpt` (`ops/s`) and should not be compared numerically to average-time (`ms/op` or `us/op`) workloads.
- Streams/manual comparisons are semantically aligned but still reflect cold JMH setup overhead. Treat ratios as directional, not absolute.
- Cold single-iteration runs drift significantly; do not use them as patch attribution sources.

## Executive Summary

- Core threshold status: **42/42** entries passed `benchmarks/thresholds.json`.
- Chart threshold status: **45/45** entries passed `benchmarks/chart-thresholds.json`.
- Highest core budget usage: `pojoLensJoinLeftComputedField|size=10000` at 31.5% (62.924 ms/op vs 200 ms budget).
- Highest hotspot allocation: `computedFieldJoinSelectiveMaterialization|size=10000` at 3,532,314 B/op.
- Highest hotspot latency: `reflectionToClassList|size=10000` at 826.706 us/op.
- All stats, chart, and SQL-like guardrail workloads are well within budget after the WP18/WP19/WP20 and consolidation passes.

## Core Guardrail Suite

All 42 core threshold checks passed.

Highest budget consumers:

| Workload | Score | Budget | Budget Use |
|---|---|---|---|
| `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` size=10000 | 62.924 ms/op | 200 ms | 31.5% |
| `PojoLensPipelineJmhBenchmark.fullGroupPipeline` size=1000 | 51.121 ms/op | 300 ms | 17.0% |
| `PojoLensPipelineJmhBenchmark.fullFilterPipeline` size=10000 | 113.933 ms/op | 750 ms | 15.2% |
| `PojoLensPipelineJmhBenchmark.fullGroupPipeline` size=10000 | 125.657 ms/op | 900 ms | 14.0% |
| `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` size=1000 | 3.100 ms/op | 25 ms | 12.4% |
| `SqlLikePipelineJmhBenchmark.parseAndFilterHaving` size=10000 | 127.418 ms/op | 1300 ms | 9.8% |
| `SqlLikePipelineJmhBenchmark.parseAndFilterHaving` size=1000 | 52.948 ms/op | 700 ms | 7.6% |

Full cold core scores:

| Workload | size=1000 | size=10000 |
|---|---|---|
| `PojoLensPipelineJmhBenchmark.fullFilterPipeline` | 3.523 ms/op | 113.933 ms/op |
| `PojoLensPipelineJmhBenchmark.fullGroupPipeline` | 51.121 ms/op | 125.657 ms/op |
| `PojoLensJoinJmhBenchmark.pojoLensJoinLeft` | 2.270 ms/op | 28.790 ms/op |
| `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` | 3.100 ms/op | 62.924 ms/op |
| `SqlLikePipelineJmhBenchmark.parseOnly` | 0.005 ms/op | 0.006 ms/op |
| `SqlLikePipelineJmhBenchmark.parseAndFilter` | 0.648 ms/op | 6.018 ms/op |
| `SqlLikePipelineJmhBenchmark.parseAndFilterHaving` | 52.948 ms/op | 127.418 ms/op |
| `SqlLikePipelineJmhBenchmark.parseAndExplain` | 0.003 ms/op | 0.006 ms/op |
| `SqlLikePipelineJmhBenchmark.parseAndFilterBooleanDepth` | 1.954 ms/op | 36.186 ms/op |
| `SqlLikePipelineJmhBenchmark.parseAndFilterHavingComputed` | 1.320 ms/op | 20.433 ms/op |
| `StatsQueryJmhBenchmark.fluentGroupedMetrics` | 0.101 ms/op | 0.778 ms/op |
| `StatsQueryJmhBenchmark.fluentGroupedMetricsToChart` | 0.116 ms/op | 0.782 ms/op |
| `StatsQueryJmhBenchmark.fluentTimeBucketMetrics` | 0.511 ms/op | 4.080 ms/op |
| `StatsQueryJmhBenchmark.fluentTimeBucketMetricsToChart` | 0.438 ms/op | 3.714 ms/op |
| `StatsQueryJmhBenchmark.sqlLikeParseAndGroupedMetrics` | 0.088 ms/op | 0.729 ms/op |
| `StatsQueryJmhBenchmark.sqlLikeParseAndGroupedMetricsToChart` | 0.092 ms/op | 0.750 ms/op |
| `StatsQueryJmhBenchmark.sqlLikeParseAndTimeBucketMetrics` | 0.428 ms/op | 3.903 ms/op |
| `StatsQueryJmhBenchmark.sqlLikeParseAndTimeBucketMetricsToChart` | 0.436 ms/op | 3.478 ms/op |
| `StatsQueryJmhBenchmark.fluentGroupedMetricsExplain` | 0.004 ms/op | 0.009 ms/op |
| `StatsQueryJmhBenchmark.sqlLikeGroupedMetricsExplain` | 0.002 ms/op | 0.002 ms/op |

## Chart Guardrail Suite

All 45 chart threshold checks passed.

Chart mapping cold scores:

| Workload | size=1000 | size=10000 | size=100000 |
|---|---|---|---|
| `fluentBarMapping` | 0.059 ms/op | 0.402 ms/op | 1.920 ms/op |
| `fluentPieMapping` | 0.056 ms/op | 0.363 ms/op | 1.904 ms/op |
| `fluentLineMapping` | 0.134 ms/op | 0.786 ms/op | 5.413 ms/op |
| `fluentAreaMapping` | 0.170 ms/op | 0.814 ms/op | 5.624 ms/op |
| `fluentScatterMapping` | 0.523 ms/op | 4.853 ms/op | 24.599 ms/op |
| `sqlLikeBarMapping` | 0.052 ms/op | 0.555 ms/op | 2.482 ms/op |
| `sqlLikePieMapping` | 0.055 ms/op | 0.568 ms/op | 2.280 ms/op |
| `sqlLikeLineMapping` | 0.123 ms/op | 0.918 ms/op | 7.407 ms/op |
| `sqlLikeAreaMapping` | 0.170 ms/op | 0.961 ms/op | 6.833 ms/op |
| `sqlLikeScatterMapping` | 0.786 ms/op | 6.552 ms/op | 53.196 ms/op |
| `scatterPayloadJsonExport` | 0.090 ms/op | 0.587 ms/op | 1.027 ms/op |
| `linePayloadJsonExport` | 0.003 ms/op | 0.004 ms/op | 0.004 ms/op |
| `areaPayloadJsonExport` | 0.003 ms/op | 0.003 ms/op | 0.003 ms/op |
| `barPayloadJsonExport` | 0.001 ms/op | 0.001 ms/op | 0.001 ms/op |
| `piePayloadJsonExport` | 0.001 ms/op | 0.001 ms/op | 0.001 ms/op |

Highest chart budget consumers:

| Workload | Score | Budget | Budget Use |
|---|---|---|---|
| `sqlLikeScatterMapping` size=10000 | 6.552 ms/op | 112.833 ms | 5.8% |
| `fluentScatterMapping` size=10000 | 4.853 ms/op | 91.318 ms | 5.3% |
| `sqlLikeScatterMapping` size=100000 | 53.196 ms/op | 1205.989 ms | 4.4% |
| `scatterPayloadJsonExport` size=1000 | 0.090 ms/op | 5.386 ms | 1.7% |
| `scatterPayloadJsonExport` size=10000 | 0.587 ms/op | 62.583 ms | 0.9% |

## Streams Baseline Suite

| Workload | PojoLens | Streams | Ratio |
|---|---|---|---|
| `fluentFilterProjection` size=1000 | 4.945 ms/op | 0.012 ms/op | 412x |
| `fluentFilterProjection` size=10000 | 120.522 ms/op | 0.124 ms/op | 972x |
| `fluentGroupedMetrics` size=1000 | 0.143 ms/op | 0.008 ms/op | 18x |
| `fluentGroupedMetrics` size=10000 | 1.238 ms/op | 0.069 ms/op | 18x |
| `fluentTimeBucketMetrics` size=1000 | 0.591 ms/op | 0.087 ms/op | 7x |
| `fluentTimeBucketMetrics` size=10000 | 8.229 ms/op | 0.984 ms/op | 8x |

## Standalone Legacy Filter Benchmark

| Size | Manual | PojoLens | Ratio |
|---|---|---|---|
| 1000 | 0.002 ms/op | 5.655 ms/op | 2828x |
| 10000 | 0.015 ms/op | 118.385 ms/op | 7892x |

## Cache Concurrency Suite

| Benchmark | Mode | Threads | Score |
|---|---|---|---|
| `sqlLikeParseHotSetConcurrent` | thrpt | 8 | 439,893,240 ops/s |
| `statsPlanBuildHotSetConcurrent` | thrpt | 8 | 173.254 ops/s |

## Hotspot Suite (`-prof gc`)

Run at `-f 1 -wi 1 -i 3 -r 100ms -prof gc`. Results written to `target/benchmarks/hotspots-gc-2026-03-17.json`.

| Benchmark | size | Latency | Alloc |
|---|---|---|---|
| `reflectionToClassList` | 1000 | 77.475 us/op | 140,232 B/op |
| `reflectionToClassList` | 10000 | 826.706 us/op | 1,400,236 B/op |
| `reflectionToDomainRows` | 1000 | 40.501 us/op | 284,040 B/op |
| `reflectionToDomainRows` | 10000 | 412.950 us/op | 2,840,026 B/op |
| `computedFieldJoinSelectiveMaterialization` | 1000 | 31.818 us/op | 364,312 B/op |
| `computedFieldJoinSelectiveMaterialization` | 10000 | 330.570 us/op | 3,532,314 B/op |
| `groupedMultiMetricAggregation` | 1000 | 35.669 us/op | 121,120 B/op |
| `groupedMultiMetricAggregation` | 10000 | 351.328 us/op | 1,043,042 B/op |
| `sqlLikePreparedStatsFastPathSetupCopy` | 1000 | 50.074 us/op | 220,304 B/op |
| `sqlLikePreparedStatsFastPathSetupCopy` | 10000 | 494.452 us/op | 2,145,643 B/op |
| `sqlLikePreparedStatsFastPathSetupView` | 1000 | 49.216 us/op | 219,880 B/op |
| `sqlLikePreparedStatsFastPathSetupView` | 10000 | 496.686 us/op | 2,145,218 B/op |
| `sqlLikePreparedStatsRebindCopy` | 1000 | 0.503 us/op | 3,528 B/op |
| `sqlLikePreparedStatsRebindCopy` | 10000 | 0.464 us/op | 3,528 B/op |
| `sqlLikePreparedStatsRebindView` | 1000 | 0.260 us/op | 3,120 B/op |
| `sqlLikePreparedStatsRebindView` | 10000 | 0.257 us/op | 3,120 B/op |
| `statsPlanCacheHit` | 1000 | 0.004 us/op | ~0 B/op |
| `statsPlanCacheHit` | 10000 | 0.004 us/op | ~0 B/op |

Interpretation:
- `computedFieldJoinSelectiveMaterialization|size=10000` still owns the largest absolute allocation (3,532,314 B/op); kept diagnostic-only per WP20 policy.
- Reflection/conversion latency (`reflectionToClassList`, `reflectionToDomainRows`) has fallen materially from the 2026-03-14 baseline (1115/557 us/op) but allocation footprint is essentially unchanged.
- Prepared fast-stats setup cost has collapsed from the pre-WP18 baseline (~7.3 ms/op) to ~494 us/op; rebinding overhead is negligible at ~0.5 us/op.
- `groupedMultiMetricAggregation` allocation is flat and consistent with previous snapshots.

## Stress Points

- `computedFieldJoinSelectiveMaterialization` allocation remains large; `warmed JFR` should be refreshed before any new WP19 structural hypothesis.
- Cold filter-vs-streams gap (`fluentFilterProjection|size=10000` at 972x overhead) is the most visible absolute ratio, but it reflects cold JMH setup overhead, not a regression.
- Cache plan-build throughput (`statsPlanBuildHotSetConcurrent` at 173 ops/s vs 440M ops/s for parse-cache) is the expected hot/cold split.
- Core and chart budgets both have large remaining headroom; current thresholds remain conservative guardrails.

## Raw Artifacts

- `target/benchmarks/core.json`
- `target/benchmarks/chart.json`
- `target/benchmarks/baseline.json`
- `target/benchmarks/cache.json`
- `target/benchmarks/hotspots-gc-2026-03-17.json`
- `target/benchmarks/pojolens-legacy.json`
