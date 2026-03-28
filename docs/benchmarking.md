# Benchmarking

`PojoLens` benchmarking is primarily a regression-control tool, not a competitor-marketing exercise.
It is advanced tooling, not part of the default adoption path.

The benchmark contract for this project is:
- publish explicit latency budgets for the hot paths we own
- keep those budgets under CI guardrails
- add conservative external baselines only where semantics are genuinely comparable
- keep correctness parity in tests, but judge performance work by absolute latency/allocation rather than fluent-vs-SQL-like ratio checks

## Benchmark Categories

The current benchmark/reporting layer classifies workloads into these categories:
- `FILTER`
- `GROUP`
- `TIME_BUCKET`
- `JOIN`
- `CHART`
- `PARSE`
- `EXPLAIN`
- `CACHE`

These categories are derived into normalized benchmark rows by `BenchmarkMetricLoader`, so CSV exports and plots can be grouped by workload class instead of raw method names alone.

## Quick Local Run (JMH)

```bash
mvn -Pbenchmark -DskipTests test-compile exec:java -Djmh.args="laughing.man.commits.benchmark.PojoLensPipelineJmhBenchmark.fullFilterPipeline -f 0 -wi 0 -i 1 -r 100ms"
```

PowerShell:

```powershell
mvn -Pbenchmark -DskipTests test-compile exec:java "-Djmh.args=laughing.man.commits.benchmark.PojoLensPipelineJmhBenchmark.fullFilterPipeline -f 0 -wi 0 -i 1 -r 100ms"
```

## Build Forked Benchmark Runner

```bash
mvn -Pbenchmark-runner -DskipTests package
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.PojoLensPipelineJmhBenchmark.fullFilterPipeline -f 1 -wi 1 -i 3
```

## Budgeted CI Suites

Core guardrail suite:

```bash
java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-main.args -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks.json
java -cp target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.BenchmarkThresholdChecker target/benchmarks.json benchmarks/thresholds.json target/benchmark-report.csv --strict
```

Chart guardrail suite:

```bash
java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-chart.args -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks/charts/chart-benchmarks.json
java -cp target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.BenchmarkThresholdChecker target/benchmarks/charts/chart-benchmarks.json benchmarks/chart-thresholds.json target/benchmarks/charts/chart-benchmark-report.csv --strict
```

Cache concurrency scenario:

```bash
java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-cache.args -t 8 -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks-cache.json
```

Hotspot microbenchmark suite:

```bash
java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-hotspots.args -f 1 -wi 1 -i 3 -r 100ms
```

## Representative Budgets

The budget files are the source of truth:
- `benchmarks/thresholds.json`
- `benchmarks/chart-thresholds.json`

**Benchmark methodology note (as of 2026-03-20):** All benchmarks now measure execution only. Query plan compilation (`newQueryBuilder(...).add*().initFilter()`) and SQL-like parse (`PojoLensSql.parse()`) are performed once in `@Setup` and reused across iterations. The `@Benchmark` method measures only `filter()`, `filterGroups()`, `chart()`, `join().filter()`, etc. Thresholds in the JSON files reflect this separation.

Representative core budgets from `benchmarks/thresholds.json`:

| Workload | Category | Size 1k | Size 10k |
|---|---|---:|---:|
| `PojoLensPipelineJmhBenchmark.fullFilterPipeline` | `FILTER` | `169.3 ms/op` | `185.4 ms/op` |
| `PojoLensPipelineJmhBenchmark.fullGroupPipeline` | `GROUP` | `219.8 ms/op` | `290.0 ms/op` |
| `PojoLensJoinJmhBenchmark.pojoLensJoinLeft` | `JOIN` | `182.4 ms/op` | `243.5 ms/op` |
| `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` | `JOIN` | `82.2 ms/op` | `205.7 ms/op` |
| `SqlLikePipelineJmhBenchmark.parseOnly` | `PARSE` | `0.2 ms/op` | `0.2 ms/op` |
| `SqlLikePipelineJmhBenchmark.parseAndFilter` | `FILTER` | `198.3 ms/op` | `235.3 ms/op` |
| `SqlLikePipelineJmhBenchmark.sqlLikeCacheSnapshotRead` | `CACHE` | `0.1 ms/op` | `0.1 ms/op` |
| `StatsQueryJmhBenchmark.fluentGroupedMetrics` | `GROUP` | `6.7 ms/op` | `52.9 ms/op` |
| `StatsQueryJmhBenchmark.fluentTimeBucketMetrics` | `TIME_BUCKET` | `38.1 ms/op` | `162.1 ms/op` |
| `StatsQueryJmhBenchmark.fluentGroupedMetricsExplain` | `EXPLAIN` | `0.3 ms/op` | `0.3 ms/op` |
| `StatsQueryJmhBenchmark.sqlLikeParseAndGroupedMetricsToChart` | `CHART` | `12.7 ms/op` | `81.0 ms/op` |

The chart threshold file carries the full chart-type matrix for `BAR`, `LINE`, `PIE`, `AREA`, and `SCATTER` mapping/export paths across `1k`, `10k`, and `100k` datasets.

## Conservative Streams Baseline

For apples-to-apples comparisons, `PojoLens` now ships a dedicated JMH baseline suite against plain Java Streams for workloads where semantics are directly comparable.

Baseline suite command:

```bash
java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-baseline.args -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks/baselines.json
```

Baseline workloads:
- `StreamsBaselineJmhBenchmark.fluentFilterProjection` vs `StreamsBaselineJmhBenchmark.streamsFilterProjection`
- `StreamsBaselineJmhBenchmark.fluentGroupedMetrics` vs `StreamsBaselineJmhBenchmark.streamsGroupedMetrics`
- `StreamsBaselineJmhBenchmark.fluentTimeBucketMetrics` vs `StreamsBaselineJmhBenchmark.streamsTimeBucketMetrics`

Why the baseline is narrow:
- plain Streams do not provide a direct equivalent for SQL parsing, explain output, or cache inspection
- join comparisons are closer to manual in-memory hash-join code than to a Streams-native primitive
- chart payload mapping is partly renderer-contract work, not just query execution

This keeps the comparison honest instead of forcing fake apples-to-apples claims.

## Streaming Short-Circuit Tradeoffs

Streaming has two different behaviors depending on consumer usage:
- full-drain consumers still process all matching rows
- short-circuit consumers (`limit(n)`, first-page extraction, early break) can avoid materializing tail rows

Dedicated benchmark suite:

```bash
java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-streaming.args -p size=10000 -f 1 -wi 1 -i 3 -r 100ms -prof gc -rf json -rff target/benchmarks/streaming-execution-forked.json
```

Benchmark shape (`StreamingExecutionJmhBenchmark`):
- Query matches most rows (`where integerField >= 100`) over `size=10000`.
- List path computes first-page checksum after calling `filter(...)`, so full result materialization still occurs.
- Stream path computes the same checksum from `stream(...).limit(50)`, so iteration stops early.

Representative `2026-03-21` results (`size=10000`, forked warmed run):

| Workload | us/op | B/op (`gc.alloc.rate.norm`) |
|---|---:|---:|
| `fluentFilterListMaterialized` | `493.186` | `1,118,258.562` |
| `fluentFilterStreamLazy` | `6.267` | `18,608.032` |
| `sqlLikeFilterListMaterialized` | `644.118` | `1,594,643.260` |
| `sqlLikeFilterStreamLazy` | `6.776` | `22,392.035` |

Interpretation:
- For first-page style consumers, streaming cuts allocation by roughly `60x` (fluent) to `71x` (SQL-like) and reduces latency by roughly `79x` to `95x` in this workload.
- These gains come from avoiding full result list materialization when callers only need an initial window.

## SQL-like Window Overhead

Window queries are now benchmarked against an equivalent non-window SQL-like baseline to keep window-stage overhead visible.

Dedicated suite:

```bash
java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-window.args -p size=10000 -f 1 -wi 1 -i 3 -r 100ms -prof gc -rf json -rff target/benchmarks/window-overhead-forked.json
```

Benchmarks (`SqlLikePipelineJmhBenchmark`):
- `parseAndFilterWindowBaseline`
- `parseAndFilterWindowRank`
- `parseAndFilterWindowRunningTotal`

Representative `2026-03-23` forked results (`size=10000`):

| Workload | ms/op | B/op (`gc.alloc.rate.norm`) |
|---|---:|---:|
| `parseAndFilterWindowBaseline` | `0.688` | `744,068` |
| `parseAndFilterWindowRank` | `2.659` | `4,397,898` |
| `parseAndFilterWindowRunningTotal` | `2.601` | `4,594,581` |

Interpretation:
- Window stages add meaningful overhead versus non-window SQL-like filtering for this workload (`~3.8x` slower, `~5.9x` to `6.2x` more allocation).
- Rank and running-total windows are in the same performance band here; running totals allocate slightly more.
- Keep this suite as a follow-up diagnostic until thresholds are formalized.

## `WP8.5` Execution-Path Spot Checks

The `WP8.5` entropy-reduction work changed three hot internal areas:

- fluent grouped stage running
- SQL-like execution explain stage accounting
- SQL-like output materialization for list-vs-stream execution

Forked local spot-check commands used for `WP8.6`:

```bash
java -jar target/pojo-lens-1.0.0-benchmarks.jar 'laughing.man.commits.benchmark.StatsQueryJmhBenchmark.(fluentGroupedRows|fluentGroupedMetrics)$' -p size=1000,10000 -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/wp8.6-group-benchmarks-forked.json
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.SqlLikePipelineJmhBenchmark.parseAndExplainExecution -p size=1000,10000 -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/wp8.6-sqllike-execution-explain-benchmarks-forked.json
java -jar target/pojo-lens-1.0.0-benchmarks.jar 'laughing.man.commits.benchmark.StreamingExecutionJmhBenchmark.(fluentFilterListMaterialized|fluentFilterStreamLazy|sqlLikeFilterListMaterialized|sqlLikeFilterStreamLazy)$' -p size=1000,10000 -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/wp8.6-streaming-benchmarks-forked.json
```

Representative `2026-03-28` forked spot-check results:

| Benchmark | Size | Score | Units |
|---|---:|---:|---|
| `StatsQueryJmhBenchmark.fluentGroupedRows` | `1000` | `0.108` | `ms/op` |
| `StatsQueryJmhBenchmark.fluentGroupedRows` | `10000` | `1.010` | `ms/op` |
| `SqlLikePipelineJmhBenchmark.parseAndExplainExecution` | `1000` | `3.662` | `ms/op` |
| `SqlLikePipelineJmhBenchmark.parseAndExplainExecution` | `10000` | `57.199` | `ms/op` |
| `StreamingExecutionJmhBenchmark.fluentFilterListMaterialized` | `10000` | `2555.610` | `us/op` |
| `StreamingExecutionJmhBenchmark.fluentFilterStreamLazy` | `10000` | `45.733` | `us/op` |
| `StreamingExecutionJmhBenchmark.sqlLikeFilterListMaterialized` | `10000` | `3485.327` | `us/op` |
| `StreamingExecutionJmhBenchmark.sqlLikeFilterStreamLazy` | `10000` | `60.815` | `us/op` |

Interpretation:

- `filterGroups(...)` remains in the low-millisecond band in this local cold spot check.
- Execution-backed SQL-like `explain(rows, projection)` remains materially slower than metadata-only explain, as expected, because it now walks the live bound execution path.
- The lazy stream advantage remains intact after the SQL-like materialization cleanup: at `size=10000`, the stream path is still roughly `56x` faster (fluent) and `57x` faster (SQL-like) than the list-materializing path for this first-page-style workload.

## Optional Index Hint Tradeoffs

Optional fluent index hints are now benchmarked with a selective equality workload (`IndexHintJmhBenchmark`):
- query shape: `stringField = :exactKey and integerField >= 0`
- baseline: normal scan path
- indexed: `.addIndex("stringField")`

Warm (repeated) run command:

```bash
java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-indexes.args -f 1 -wi 1 -i 3 -r 100ms -prof gc -rf json -rff target/benchmarks/index-hint-forked.json
```

Cold run command:

```bash
java -jar target/pojo-lens-1.0.0-benchmarks.jar @scripts/benchmark-suite-indexes.args -f 1 -wi 0 -i 1 -r 100ms -prof gc -rf json -rff target/benchmarks/index-hint-cold.json
```

Representative `2026-03-21` results (`size=10000`):

| Scenario | Scan us/op | Indexed us/op | Scan B/op | Indexed B/op |
|---|---:|---:|---:|---:|
| Warm (`-wi 1 -i 3`) | `842.561` | `205.164` | `254,012.391` | `1,428,193.059` |
| Cold (`-wi 0 -i 1`) | `115,657.800` | `123,970.100` | `6,578,744.000` | `7,768,168.000` |

Interpretation:
- Warm repeated workloads see a strong latency win (`~4.1x` faster in this run) from candidate narrowing.
- Allocation cost is higher (`~5.6x` in the warm run) because index construction/allocation overhead is paid in this prototype path.
- Cold one-shot runs can regress (here, indexed was slower and allocated more), so index hints should be used for repeated hot snapshots rather than one-off scans.

## Hotspot Microbenchmarks

The hotspot suite isolates the conversion and cache paths that the end-to-end suites intentionally blur together:
- `HotspotMicroJmhBenchmark.reflectionToDomainRows`
- `HotspotMicroJmhBenchmark.reflectionToClassList`
- `HotspotMicroJmhBenchmark.statsPlanCacheHit`
- `HotspotMicroJmhBenchmark.sqlLikePreparedStatsRebindCopy`
- `HotspotMicroJmhBenchmark.sqlLikePreparedStatsRebindView`
- `HotspotMicroJmhBenchmark.sqlLikePreparedStatsFastPathSetupCopy`
- `HotspotMicroJmhBenchmark.sqlLikePreparedStatsFastPathSetupView`
- `HotspotMicroJmhBenchmark.groupedMultiMetricAggregation`
- `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization`

Use the hotspot suite when tuning reflection flattening, typed projection, execution-plan cache hits, prepared SQL-like stats setup, grouped multi-metric aggregation, or the computed-field join materialization path.

Allocation-focused local run examples:

```bash
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.HotspotMicroJmhBenchmark.reflectionToDomainRows -p size=10000 -f 1 -wi 1 -i 3 -r 100ms -prof gc
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.HotspotMicroJmhBenchmark.reflectionToClassList -p size=10000 -f 1 -wi 1 -i 3 -r 100ms -prof gc
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.HotspotMicroJmhBenchmark.statsPlanCacheHit -p size=10000 -f 1 -wi 1 -i 5 -r 100ms -prof gc
java -jar target/pojo-lens-1.0.0-benchmarks.jar 'laughing.man.commits.benchmark.HotspotMicroJmhBenchmark.sqlLikePreparedStats(Rebind|FastPathSetup)(Copy|View)' -p size=10000 -f 1 -wi 1 -i 5 -r 100ms -prof gc
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.HotspotMicroJmhBenchmark.groupedMultiMetricAggregation -p size=10000 -f 1 -wi 1 -i 3 -r 100ms -prof gc
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization -p size=10000 -f 1 -wi 1 -i 3 -r 100ms -prof gc
```

For hotspot tuning, capture both the JMH score and the `gc.alloc.rate.norm` output from `-prof gc`. These runs are local diagnostics rather than merge-gated thresholds until the allocation budgets are stable enough to survive machine noise.

Representative warmed `-prof gc` numbers as of `2026-03-17` (after `RawQueryRow` allocation reduction):

| Benchmark | size | us/op | B/op |
|---|---|---:|---:|
| `reflectionToDomainRows` | 1k | ~39 us/op | 100,136 B/op |
| `reflectionToDomainRows` | 10k | ~427 us/op | 1,000,122 B/op |
| `reflectionToClassList` | 1k | ~87 us/op | 140,232 B/op |
| `reflectionToClassList` | 10k | ~996 us/op | 1,400,236 B/op |
| `groupedMultiMetricAggregation` | 1k | ~22 us/op | 73,120 B/op |
| `groupedMultiMetricAggregation` | 10k | ~303 us/op | 1,043,042 B/op |

As of the 2026-03-17 rebaseline, `computedFieldJoinSelectiveMaterialization` remains diagnostic-only. Repeated `-prof gc` reruns measured about `28.0 us/op` / `212,656 B/op` at `size=1000` and `256.0 us/op` / `2,012,761 B/op` at `size=10000` (down from `364,312 B/op` and `3,532,314 B/op` before the `RawQueryRow` allocation reduction), so the path is still too allocation-heavy to freeze into a strict merge gate.

## Semantic Guardrails

Comparable baseline numbers are only useful if the outputs actually match.

Current correctness guardrails:
- `StreamsBenchmarkParityTest` locks the Streams baseline against equivalent fluent query outputs
- `PojoLensJoinJmhBenchmarkParityTest` locks the computed-field join benchmark against its manual hash-join baseline
- `BenchmarkMetricQueriesParityTest` keeps the normalized benchmark-query helpers aligned across fluent and SQL-like access

## End-To-End Computed Join Diagnostics

When WP5 selective single-join changes need end-to-end validation, use the dedicated computed-field join benchmarks in `PojoLensJoinJmhBenchmark`:
- `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField`
- `PojoLensJoinJmhBenchmark.manualHashJoinLeftComputedField`

Example local comparison run:

```bash
java -jar target/pojo-lens-1.0.0-benchmarks.jar 'laughing.man.commits.benchmark.PojoLensJoinJmhBenchmark.(pojoLensJoinLeftComputedField|manualHashJoinLeftComputedField)' -p size=1000,10000 -f 1 -wi 1 -i 3 -r 100ms -prof gc -rf json -rff target/benchmarks-computed-field-join-e2e.json
```

The PojoLens path is part of the core guardrail suite through `scripts/benchmark-suite-main.args`.

As of 2026-03-20 (execution-only methodology), a strict-style cold run (`-f 1 -wi 0 -i 1 -r 100ms`) measured `2.232 ms/op` at `size=1000` and `32.074 ms/op` at `size=10000`. The core guardrail is at `82.2 ms/op` at `size=1000` and `205.7 ms/op` at `size=10000` in `benchmarks/thresholds.json`, preserving headroom for machine noise on the cold strict suite. The previous cold score at `size=10000` was `121.266 ms/op` under the old setup-bundled methodology; the reduction reflects query plan compilation being excluded from the measured iteration.

Warmed `-prof gc` reruns on `2026-03-17` (under the old setup-bundled methodology) measured `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at about `0.062 ms/op` / `247,432 B/op` for `size=1000` and `0.589 ms/op` / `2,302,619 B/op` for `size=10000`, while the manual comparison baseline measured about `0.009 ms/op` / `84,512 B/op` and `0.094 ms/op` / `927,128 B/op`. (The `RawQueryRow` improvement primarily benefits the `reflectionToDomainRows` path: `2,840,026 B/op` → `1,000,122 B/op` at `size=10000`, a 64.8% allocation reduction.) These warmed numbers need a refresh under the current execution-only methodology before being used as profiling baselines.

Keep `manualHashJoinLeftComputedField` as a local comparison baseline rather than a merge gate. Use the profiled local command above when you need allocation context or want to compare the current PojoLens path against the manual baseline directly. These budgets are intentionally based on the colder no-warmup strict-suite configuration, not the warmer `-wi 1 -i 3 -prof gc` profiling runs.

## Plot Generation

```bash
java -cp target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.BenchmarkMetricsPlotGenerator target/benchmarks.json benchmarks/thresholds.json target/benchmarks/charts/chart-benchmarks.json benchmarks/chart-thresholds.json target/benchmarks/charts/images
```

Artifacts:
- `target/benchmarks.json`
- `target/benchmark-report.csv`
- `target/benchmarks/charts/chart-benchmarks.json`
- `target/benchmarks/charts/chart-benchmark-report.csv`
- `target/benchmarks/charts/metrics-normalized.csv`
- `target/benchmarks/charts/images/INDEX.txt`
- `target/benchmarks/charts/images/*.png`

## CI Gates

GitHub Actions currently enforces:
- strict threshold checks for the core benchmark suite
- strict threshold checks for the chart benchmark suite
- benchmark plot generation/upload
- cache concurrency stress loops

The Streams baseline suite is intentionally reproducible but not currently a merge gate. It is there to answer "what is the cost relative to plain Java code for equivalent work?" without turning CI into hardware-noise theater.

## Interpretation Rules

- Treat the threshold files as budgets, not as bragging rights.
- Prefer latency and allocation context over raw throughput-only claims.
- Only compare against Streams where output semantics are intentionally aligned.
- Do not use fluent-vs-SQL-like performance ratios as a merge gate; SQL-like intentionally pays query-translation cost that fluent does not.
- Do not compare `PojoLens` to databases or unrelated libraries as if the workloads were equivalent.
- When a comparison needs caveats, write the caveats next to the number.
