# Benchmarking

`PojoLens` benchmarking is primarily a regression-control tool, not a competitor-marketing exercise.

The benchmark contract for this project is:
- publish explicit latency budgets for the hot paths we own
- keep those budgets under CI guardrails
- add conservative external baselines only where semantics are genuinely comparable
- require parity checks so benchmark numbers do not hide behavior differences

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
java -jar target/pojo-lens-1.3.0-benchmarks.jar laughing.man.commits.benchmark.PojoLensPipelineJmhBenchmark.fullFilterPipeline -f 1 -wi 1 -i 3
```

## Budgeted CI Suites

Core guardrail suite:

```bash
java -jar target/pojo-lens-1.3.0-benchmarks.jar @scripts/benchmark-suite-main.args -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks.json
java -cp target/pojo-lens-1.3.0-benchmarks.jar laughing.man.commits.benchmark.BenchmarkThresholdChecker target/benchmarks.json benchmarks/thresholds.json target/benchmark-report.csv --strict
```

Chart guardrail suite:

```bash
java -jar target/pojo-lens-1.3.0-benchmarks.jar @scripts/benchmark-suite-chart.args -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks/charts/chart-benchmarks.json
java -cp target/pojo-lens-1.3.0-benchmarks.jar laughing.man.commits.benchmark.BenchmarkThresholdChecker target/benchmarks/charts/chart-benchmarks.json benchmarks/chart-thresholds.json target/benchmarks/charts/chart-benchmark-report.csv --strict
java -cp target/pojo-lens-1.3.0-benchmarks.jar laughing.man.commits.benchmark.ChartParityChecker target/benchmarks/charts/chart-benchmarks.json target/benchmarks/charts/chart-parity-report.csv
```

Cache concurrency scenario:

```bash
java -jar target/pojo-lens-1.3.0-benchmarks.jar @scripts/benchmark-suite-cache.args -t 8 -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks-cache.json
```

## Representative Budgets

The budget files are the source of truth:
- `benchmarks/thresholds.json`
- `benchmarks/chart-thresholds.json`

Representative core budgets from `benchmarks/thresholds.json`:

| Workload | Category | Size 1k | Size 10k |
|---|---|---:|---:|
| `PojoLensPipelineJmhBenchmark.fullFilterPipeline` | `FILTER` | `350 ms/op` | `750 ms/op` |
| `PojoLensPipelineJmhBenchmark.fullGroupPipeline` | `GROUP` | `300 ms/op` | `900 ms/op` |
| `PojoLensJoinJmhBenchmark.pojoLensJoinLeft` | `JOIN` | `500 ms/op` | `1500 ms/op` |
| `SqlLikePipelineJmhBenchmark.parseOnly` | `PARSE` | `5 ms/op` | `5 ms/op` |
| `SqlLikePipelineJmhBenchmark.parseAndFilter` | `FILTER` | `500 ms/op` | `900 ms/op` |
| `SqlLikePipelineJmhBenchmark.sqlLikeCacheSnapshotRead` | `CACHE` | `1 ms/op` | `1 ms/op` |
| `StatsQueryJmhBenchmark.fluentGroupedMetrics` | `GROUP` | `600 ms/op` | `1100 ms/op` |
| `StatsQueryJmhBenchmark.fluentTimeBucketMetrics` | `TIME_BUCKET` | `700 ms/op` | `1400 ms/op` |
| `StatsQueryJmhBenchmark.fluentGroupedMetricsExplain` | `EXPLAIN` | `250 ms/op` | `650 ms/op` |
| `StatsQueryJmhBenchmark.sqlLikeParseAndGroupedMetricsToChart` | `CHART` | `900 ms/op` | `1600 ms/op` |

The chart threshold file carries the full chart-type matrix for `BAR`, `LINE`, `PIE`, `AREA`, and `SCATTER` mapping/export paths across `1k`, `10k`, and `100k` datasets.

## Conservative Streams Baseline

For apples-to-apples comparisons, `PojoLens` now ships a dedicated JMH baseline suite against plain Java Streams for workloads where semantics are directly comparable.

Baseline suite command:

```bash
java -jar target/pojo-lens-1.3.0-benchmarks.jar @scripts/benchmark-suite-baseline.args -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/benchmarks/baselines.json
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

## Semantic Parity Guardrails

Comparable baseline numbers are only useful if the outputs actually match.

Current parity guardrails:
- `StreamsBenchmarkParityTest` locks the Streams baseline against equivalent fluent query outputs
- `ChartParityChecker` checks chart benchmark outputs for fluent vs SQL-like parity
- `BenchmarkMetricQueriesParityTest` keeps the normalized benchmark-query helpers aligned across fluent and SQL-like access

## Plot Generation

```bash
java -cp target/pojo-lens-1.3.0-benchmarks.jar laughing.man.commits.benchmark.BenchmarkMetricsPlotGenerator target/benchmarks.json benchmarks/thresholds.json target/benchmarks/charts/chart-benchmarks.json benchmarks/chart-thresholds.json target/benchmarks/charts/images
```

Artifacts:
- `target/benchmarks.json`
- `target/benchmark-report.csv`
- `target/benchmarks/charts/chart-benchmarks.json`
- `target/benchmarks/charts/chart-benchmark-report.csv`
- `target/benchmarks/charts/chart-parity-report.csv`
- `target/benchmarks/charts/metrics-normalized.csv`
- `target/benchmarks/charts/images/INDEX.txt`
- `target/benchmarks/charts/images/*.png`

## CI Gates

GitHub Actions currently enforces:
- strict threshold checks for the core benchmark suite
- strict threshold checks for the chart benchmark suite
- chart parity checks
- benchmark plot generation/upload
- cache concurrency stress loops

The Streams baseline suite is intentionally reproducible but not currently a merge gate. It is there to answer "what is the cost relative to plain Java code for equivalent work?" without turning CI into hardware-noise theater.

## Interpretation Rules

- Treat the threshold files as budgets, not as bragging rights.
- Prefer latency and allocation context over raw throughput-only claims.
- Only compare against Streams where output semantics are intentionally aligned.
- Do not compare `PojoLens` to databases or unrelated libraries as if the workloads were equivalent.
- When a comparison needs caveats, write the caveats next to the number.

