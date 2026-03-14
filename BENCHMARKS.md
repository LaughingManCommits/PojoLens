# Benchmark Report

Generated from a full local benchmark sweep on 2026-03-14.

## Scope

- Rebuilt benchmark runner: `mvn -B -ntp -Pbenchmark-runner -DskipTests package`
- Core guardrail suite: `@scripts/benchmark-suite-main.args -f 1 -wi 0 -i 1 -r 100ms`
- Chart guardrail suite: `@scripts/benchmark-suite-chart.args -f 1 -wi 0 -i 1 -r 100ms`
- Streams baseline suite: `@scripts/benchmark-suite-baseline.args -f 1 -wi 0 -i 1 -r 100ms`
- Cache concurrency suite: `@scripts/benchmark-suite-cache.args -t 8 -f 1 -wi 0 -i 1 -r 100ms`
- Hotspot suite with allocation profiler: `@scripts/benchmark-suite-hotspots.args -f 1 -wi 1 -i 3 -r 100ms -prof gc`
- Standalone legacy benchmark: `laughing.man.commits.benchmark.PojoLensJmhBenchmark.* -f 1 -wi 0 -i 1 -r 100ms`

## Caveats

- The core/chart/baseline/cache/legacy runs are cold guardrail-style snapshots with `0` warmup iterations and a single `100ms` measurement. They are useful for regression control, not for comparing directly to warmed WP17 tuning runs.
- Hotspot numbers use `-prof gc`, `1` warmup iteration, and `3` measurement iterations. Use those for allocation and stress analysis, not for direct comparison with the cold guardrail suite.
- Cache results are `thrpt` (`ops/s`) and should not be compared numerically to average-time (`ms/op` or `us/op`) workloads.
- Streams/manual comparisons are semantically aligned but still reflect cold JMH setup overhead. Treat ratios as directional, not as marketing numbers.

## Executive Summary

- Core threshold status: 42/42 entries passed `benchmarks/thresholds.json`.
- Chart threshold status: 45/45 entries passed `benchmarks/chart-thresholds.json`.
- Historical chart parity note: 5 failures out of 15 fluent vs SQL-like comparisons were observed in this 2026-03-14 snapshot, but that ratio is now treated as diagnostic-only rather than as a gate.
- Highest cold core budget usage: laughing.man.commits.benchmark.PojoLensPipelineJmhBenchmark.fullGroupPipeline|size=1000 at 36.8%.
- Highest hotspot allocation: computedFieldJoinSelectiveMaterialization|size=10000 at 3532314.399 B/op.
- Highest hotspot latency: reflectionToClassList|size=10000 at 1115.501 us/op.

## Core Guardrail Suite

- All core threshold checks passed.
- The worst relative budget consumers are still comfortably below budget, which suggests the current core thresholds remain conservative.

| Workload | Score | Budget | Budget Use | Headroom |
| --- | --- | --- | --- | --- |
| laughing.man.commits.benchmark.PojoLensPipelineJmhBenchmark.fullGroupPipeline|size=1000 | 110.463 ms/op | 300 ms/op | 36.8% | 189.537 ms/op |
| laughing.man.commits.benchmark.PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField|size=10000 | 136.208 ms/op | 500 ms/op | 27.2% | 363.792 ms/op |
| laughing.man.commits.benchmark.PojoLensPipelineJmhBenchmark.fullFilterPipeline|size=10000 | 129.008 ms/op | 750 ms/op | 17.2% | 620.992 ms/op |
| laughing.man.commits.benchmark.PojoLensPipelineJmhBenchmark.fullGroupPipeline|size=10000 | 139.857 ms/op | 900 ms/op | 15.5% | 760.143 ms/op |
| laughing.man.commits.benchmark.SqlLikePipelineJmhBenchmark.parseAndFilterHaving|size=10000 | 131.85 ms/op | 1300 ms/op | 10.1% | 1168.15 ms/op |
| laughing.man.commits.benchmark.StatsQueryJmhBenchmark.fluentTimeBucketMetricsToChart|size=10000 | 133.176 ms/op | 1600 ms/op | 8.3% | 1466.824 ms/op |
| laughing.man.commits.benchmark.SqlLikePipelineJmhBenchmark.parseAndFilterHaving|size=1000 | 56.468 ms/op | 700 ms/op | 8.1% | 643.532 ms/op |
| laughing.man.commits.benchmark.StatsQueryJmhBenchmark.fluentTimeBucketMetrics|size=10000 | 111.317 ms/op | 1400 ms/op | 8.0% | 1288.683 ms/op |

Slowest cold core workloads:

| Workload | Score | Budget Use |
| --- | --- | --- |
| laughing.man.commits.benchmark.PojoLensPipelineJmhBenchmark.fullGroupPipeline|size=10000 | 139.857 ms/op | 15.5% |
| laughing.man.commits.benchmark.PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField|size=10000 | 136.208 ms/op | 27.2% |
| laughing.man.commits.benchmark.StatsQueryJmhBenchmark.sqlLikeParseAndTimeBucketMetricsToChart|size=10000 | 135.007 ms/op | 7.5% |
| laughing.man.commits.benchmark.StatsQueryJmhBenchmark.fluentTimeBucketMetricsToChart|size=10000 | 133.176 ms/op | 8.3% |
| laughing.man.commits.benchmark.SqlLikePipelineJmhBenchmark.parseAndFilterHaving|size=10000 | 131.85 ms/op | 10.1% |
| laughing.man.commits.benchmark.PojoLensPipelineJmhBenchmark.fullFilterPipeline|size=10000 | 129.008 ms/op | 17.2% |
| laughing.man.commits.benchmark.StatsQueryJmhBenchmark.sqlLikeParseAndTimeBucketMetrics|size=10000 | 118.594 ms/op | 7.9% |
| laughing.man.commits.benchmark.StatsQueryJmhBenchmark.fluentTimeBucketMetrics|size=10000 | 111.317 ms/op | 8.0% |

## Chart Suite

- All chart thresholds passed.
- Historical chart parity data from this run showed the SQL-like path exceeding the old fluent ratio guardrail in 5 cases, but that ratio is no longer treated as a benchmark gate because SQL-like intentionally includes query translation work.

Worst chart threshold consumers:

| Workload | Score | Budget | Budget Use |
| --- | --- | --- | --- |
| laughing.man.commits.benchmark.ChartVisualizationJmhBenchmark.scatterPayloadJsonExport|size=1000 | 3.91 ms/op | 5.386 ms/op | 72.6% |
| laughing.man.commits.benchmark.ChartVisualizationJmhBenchmark.scatterPayloadJsonExport|size=10000 | 43.157 ms/op | 62.583 ms/op | 69.0% |
| laughing.man.commits.benchmark.ChartVisualizationJmhBenchmark.scatterPayloadJsonExport|size=100000 | 82.048 ms/op | 176.555 ms/op | 46.5% |
| laughing.man.commits.benchmark.ChartVisualizationJmhBenchmark.linePayloadJsonExport|size=1000 | 0.303 ms/op | 0.794 ms/op | 38.1% |
| laughing.man.commits.benchmark.ChartVisualizationJmhBenchmark.areaPayloadJsonExport|size=100000 | 0.292 ms/op | 0.797 ms/op | 36.6% |
| laughing.man.commits.benchmark.ChartVisualizationJmhBenchmark.areaPayloadJsonExport|size=10000 | 0.35 ms/op | 0.969 ms/op | 36.1% |
| laughing.man.commits.benchmark.ChartVisualizationJmhBenchmark.linePayloadJsonExport|size=100000 | 0.243 ms/op | 0.766 ms/op | 31.7% |
| laughing.man.commits.benchmark.ChartVisualizationJmhBenchmark.areaPayloadJsonExport|size=1000 | 0.299 ms/op | 0.953 ms/op | 31.4% |

Historical chart parity snapshot:

| Chart | Size | Fluent | SQL-like | Ratio | Limit |
| --- | --- | --- | --- | --- | --- |
| SCATTER | 1000 | 0.952225 ms/op | 1.757569 ms/op | 1.845750x | 1.750000x |
| BAR | 10000 | 1.009345 ms/op | 1.802326 ms/op | 1.785639x | 1.750000x |
| LINE | 10000 | 1.409154 ms/op | 2.498889 ms/op | 1.773326x | 1.750000x |
| PIE | 10000 | 0.996702 ms/op | 1.786875 ms/op | 1.792788x | 1.750000x |
| SCATTER | 10000 | 5.418491 ms/op | 9.485809 ms/op | 1.750637x | 1.750000x |

## Streams Baseline Suite

| Workload | Size | PojoLens | Streams | Ratio |
| --- | --- | --- | --- | --- |
| FilterProjection | 1000 | 18.317 ms/op | 0.012 ms/op | 1497.6x |
| FilterProjection | 10000 | 126.115 ms/op | 0.13 ms/op | 973.6x |
| GroupedMetrics | 1000 | 0.94 ms/op | 0.008 ms/op | 118.0x |
| GroupedMetrics | 10000 | 11.435 ms/op | 0.072 ms/op | 159.9x |
| TimeBucketMetrics | 1000 | 17.301 ms/op | 0.098 ms/op | 176.7x |
| TimeBucketMetrics | 10000 | 147.092 ms/op | 1.398 ms/op | 105.2x |

## Standalone Legacy Filter Benchmark

- `PojoLensJmhBenchmark` is not currently part of the published suite manifests, but it is still useful as a cold filter/distinct/order sanity check.

| Size | Manual | PojoLens | Ratio |
| --- | --- | --- | --- |
| 1000 | 0.002 ms/op | 5.683 ms/op | 3701.1x |
| 10000 | 0.016 ms/op | 126.65 ms/op | 7874.4x |

## Cache Concurrency Suite

| Benchmark | Mode | Threads | Score |
| --- | --- | --- | --- |
| sqlLikeParseHotSetConcurrent | thrpt | 8 | 312607925.225 ops/s |
| statsPlanBuildHotSetConcurrent | thrpt | 8 | 114.799 ops/s |

## Hotspot Suite (`-prof gc`)

Highest hotspot latencies:

| Benchmark | Latency | Alloc |
| --- | --- | --- |
| reflectionToClassList|size=10000 | 1115.501 us/op | 1400237.743 B/op |
| reflectionToDomainRows|size=10000 | 557.219 us/op | 2840026.819 B/op |
| computedFieldJoinSelectiveMaterialization|size=10000 | 469.259 us/op | 3532314.399 B/op |
| groupedMultiMetricAggregation|size=10000 | 457.742 us/op | 1043042.326 B/op |
| reflectionToClassList|size=1000 | 117.549 us/op | 140232.608 B/op |
| reflectionToDomainRows|size=1000 | 56.647 us/op | 284040.289 B/op |
| computedFieldJoinSelectiveMaterialization|size=1000 | 50.712 us/op | 364312.281 B/op |
| groupedMultiMetricAggregation|size=1000 | 47.289 us/op | 121120.242 B/op |

Highest hotspot allocations:

| Benchmark | Alloc | Latency |
| --- | --- | --- |
| computedFieldJoinSelectiveMaterialization|size=10000 | 3532314.399 B/op | 469.259 us/op |
| reflectionToDomainRows|size=10000 | 2840026.819 B/op | 557.219 us/op |
| reflectionToClassList|size=10000 | 1400237.743 B/op | 1115.501 us/op |
| groupedMultiMetricAggregation|size=10000 | 1043042.326 B/op | 457.742 us/op |
| computedFieldJoinSelectiveMaterialization|size=1000 | 364312.281 B/op | 50.712 us/op |
| reflectionToDomainRows|size=1000 | 284040.289 B/op | 56.647 us/op |
| reflectionToClassList|size=1000 | 140232.608 B/op | 117.549 us/op |
| groupedMultiMetricAggregation|size=1000 | 121120.242 B/op | 47.289 us/op |

## Recurring Warm JFR Hotspots

Compared warmed artifacts:

- `target/pojolens-fastpath-current.jfr`
- `target/wp17-after-readpath.jfr`
- `target/wp17-after-parent-buffer.jfr`

Recurring first-repo-frame CPU clusters:

- `ReflectionUtil` read-side access remained dominant across all three profiles: `readResolvedFieldValue` / `ResolvedFieldPath.read` moved from about `589` to `875` to `925` samples as earlier bottlenecks were removed.
- `FastArrayQuerySupport` stayed as the other dominant warmed cluster: `ComputedFieldPlan.resolveValue` moved from about `200` to `253` to `331`, while `tryBuildJoinedState` and `buildChildIndex` remained present in every profile.
- `FastArrayQuerySupport.filterRows` was very large in the earliest profile (`961`) and much smaller in the later two (`126`, `122`), which confirms the matcher work helped but did not remove the class from the hot set.
- `ReflectionUtil.applyProjectionWritePlan` / `setResolvedFieldValue` stayed visible in the later profiles, so projection writes remain part of the tail after join-build and read-path cleanup.

Recurring first-repo-frame allocation clusters:

- `FastArrayQuerySupport.buildChildIndex` remained the largest recurring allocation site and grew from about `1271` to `3676` to `5353` samples as parent-side allocation was reduced.
- `ReflectionUtil` extraction work remained heavy through `readFlatRowValues`, `readResolvedFieldValue`, and `ResolvedFieldPath.read`.
- `FastArrayQuerySupport.materializeJoinedRow` remained a top allocation site in all three warmed profiles.
- `FastArrayQuerySupport.castNumericValue` emerged in the later two warmed profiles once earlier costs dropped.
- `ReflectionUtil.instantiateNoArg` fell sharply in the newest profile but still belongs to the recurring projection/conversion cost family.

Interpretation:

- The main recurring warmed overhead is now concentrated in `ReflectionUtil` and `FastArrayQuerySupport`, not in the older `ComputedFieldSupport`, `JoinEngine`, or `collectQueryRowFieldTypes` path.
- That class-level concentration matches the hotspot suite, which is why `TODO.md` now treats reflection/conversion work and class-level JFR clusters as first-class backlog items instead of only extending WP17 method-by-method.

## Stress Points

- Absolute chart and SQL-like chart latency still matters, but fluent-vs-SQL-like ratio is no longer treated as a release gate because the entry styles do different work by design.
- Cold end-to-end performance remains most exposed on older filter/baseline comparisons rather than on hard threshold failures. `StreamsBaselineJmhBenchmark.fluentFilterProjection|size=10000` came in at `126.115 ms/op` versus `0.130 ms/op` for the Streams baseline, and the legacy `PojoLensJmhBenchmark.pojoLensFilter|size=10000` came in at `126.650 ms/op` versus `0.016 ms/op` for the manual baseline.
- Allocation stress is still concentrated in conversion/materialization paths. The largest `B/op` values are `computedFieldJoinSelectiveMaterialization|size=10000` (`3,532,314 B/op`), `reflectionToDomainRows|size=10000` (`2,840,027 B/op`), and `reflectionToClassList|size=10000` (`1,400,238 B/op`).
- Cross-JFR warm profiling shows the recurring class-level hotspot concentration is now mostly `ReflectionUtil` plus `FastArrayQuerySupport`, which is the main common stress point behind the current WP17 and hotspot-microbenchmark backlog.
- Cache throughput shows a sharp split between pure parse-cache reuse (`sqlLikeParseHotSetConcurrent` at `312,607,925 ops/s`) and concurrent stats plan building (`114.799 ops/s`). That is expected semantically, but it remains the cache-side stress concentration.
- The core and chart budget files both have large remaining headroom. If the intent is tighter regression detection rather than conservative guardrails, the current data supports future rebaselining work.

## Raw Artifacts

- `target/benchmarks/core.json`
- `target/benchmarks/core-report.csv`
- `target/benchmarks/chart.json`
- `target/benchmarks/chart-report.csv`
- `target/benchmarks/baseline.json`
- `target/benchmarks/cache.json`
- `target/benchmarks/hotspots-gc.json`
- `target/benchmarks/pojolens-legacy.json`
