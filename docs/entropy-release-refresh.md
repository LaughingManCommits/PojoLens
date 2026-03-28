# Entropy Release Refresh

This document is the `WP8.6` refresh artifact for the `Entropy Reduction`
roadmap.
It captures the final user-facing guidance, migration notes, and benchmark
evidence after the `WP8.5` implementation work landed.

## Default Path Per Job

For new code, the default path per job is now:

- `PojoLensCore` for service-owned fluent queries
- `PojoLensSql` for dynamic or text-authored SQL-like queries
- `PojoLensRuntime` when policy, caches, linting, telemetry, or computed fields
  must be instance-scoped
- `PojoLensChart` when rows already exist and only chart mapping remains
- `ReportDefinition<T>` for reusable row-first query contracts

For multi-source execution:

- start with `JoinBindings` for one-off named secondary sources
- promote to `DatasetBundle` when the same primary plus join snapshot is reused

## `WP8.5` User-Facing Notes

The main user-visible behavior/shape updates from `WP8.5` are:

- SQL-like `explain(rows, projectionClass)` stage counts now come from the
  same bound execution path used by live SQL-like execution, instead of an
  explain-only replay path
- `HAVING` and `QUALIFY` stage counts remain deterministic even when
  `ORDER BY` is absent
- `laughing.man.commits.chart.validation.ChartValidation` is no longer a
  supported public helper; chart validation is internal to the chart entry
  points and mappers

Migration direction:

- use `PojoLensChart.toChartData(...)`, `Filter.chart(...)`,
  `SqlLikeQuery.chart(...)`, `ChartMapper`, or `ChartResultMapper`
  instead of importing `ChartValidation`
- refresh any explain snapshots that depended on the old SQL-like
  `HAVING`/`QUALIFY` stage-count behavior

## Benchmark Evidence

Methodology:

- forked JMH runner jar built from `pojo-lens-benchmarks`
- `1` fork, `0` warmup iterations, `1` measurement iteration, `100 ms`
  measurement window
- spot checks are local evidence for the touched internals; threshold files in
  `benchmarks/*.json` remain the source of truth for guarded suites

Commands used:

```powershell
java -jar target/pojo-lens-1.0.0-benchmarks.jar "laughing.man.commits.benchmark.StatsQueryJmhBenchmark.(fluentGroupedRows|fluentGroupedMetrics)$" -p size=1000,10000 -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/wp8.6-group-benchmarks-forked.json
java -jar target/pojo-lens-1.0.0-benchmarks.jar laughing.man.commits.benchmark.SqlLikePipelineJmhBenchmark.parseAndExplainExecution -p size=1000,10000 -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/wp8.6-sqllike-execution-explain-benchmarks-forked.json
java -jar target/pojo-lens-1.0.0-benchmarks.jar "laughing.man.commits.benchmark.StreamingExecutionJmhBenchmark.(fluentFilterListMaterialized|fluentFilterStreamLazy|sqlLikeFilterListMaterialized|sqlLikeFilterStreamLazy)$" -p size=1000,10000 -f 1 -wi 0 -i 1 -r 100ms -rf json -rff target/wp8.6-streaming-benchmarks-forked.json
```

Representative `2026-03-28` results:

| Benchmark | Size | Score | Units |
| --- | ---: | ---: | --- |
| `StatsQueryJmhBenchmark.fluentGroupedRows` | `1000` | `0.108` | `ms/op` |
| `StatsQueryJmhBenchmark.fluentGroupedRows` | `10000` | `1.010` | `ms/op` |
| `SqlLikePipelineJmhBenchmark.parseAndExplainExecution` | `1000` | `3.662` | `ms/op` |
| `SqlLikePipelineJmhBenchmark.parseAndExplainExecution` | `10000` | `57.199` | `ms/op` |
| `StreamingExecutionJmhBenchmark.fluentFilterListMaterialized` | `1000` | `203.306` | `us/op` |
| `StreamingExecutionJmhBenchmark.fluentFilterStreamLazy` | `1000` | `40.714` | `us/op` |
| `StreamingExecutionJmhBenchmark.sqlLikeFilterListMaterialized` | `1000` | `384.214` | `us/op` |
| `StreamingExecutionJmhBenchmark.sqlLikeFilterStreamLazy` | `1000` | `63.719` | `us/op` |
| `StreamingExecutionJmhBenchmark.fluentFilterListMaterialized` | `10000` | `2555.610` | `us/op` |
| `StreamingExecutionJmhBenchmark.fluentFilterStreamLazy` | `10000` | `45.733` | `us/op` |
| `StreamingExecutionJmhBenchmark.sqlLikeFilterListMaterialized` | `10000` | `3485.327` | `us/op` |
| `StreamingExecutionJmhBenchmark.sqlLikeFilterStreamLazy` | `10000` | `60.815` | `us/op` |

Interpretation:

- the shared fluent grouped stage runner still keeps `filterGroups(...)` in the
  sub-millisecond to low-millisecond band for these local spot checks
- SQL-like execution explain remains in a reasonable cold range after the
  stage-accounting cleanup and now measures the real execution-backed explain
  path
- the lazy-stream advantage for first-page consumers remains intact after the
  SQL-like output-materialization cleanup; list-backed paths are still much
  slower than the early-stop stream paths at `size=10000`

## Release Note Wording

Suggested release-note wording for the `WP8.5`/`WP8.6` refresh:

> PojoLens now documents one default path per job: `PojoLensCore`,
> `PojoLensSql`, `PojoLensRuntime`, `PojoLensChart`, and `ReportDefinition<T>`.
> `JoinBindings` is the default one-off multi-source binding input, with
> `DatasetBundle` as the reusable snapshot form. Cache policy tuning is now
> public only through `PojoLensRuntime`; the old
> `FilterExecutionPlanCache` compatibility facade is gone. SQL-like execution
> explain now reports stage counts from the live bound execution path,
> including stable `HAVING`/`QUALIFY` counts without `ORDER BY`. The legacy
> `chart.validation.ChartValidation` helper is no longer a supported public
> contract.

## Result

- `README.md` and the selection docs now reinforce the reduced default path
  set directly
- migration and release wording now call out the narrowed advanced/helper
  surface and the explain-stage-count alignment
- benchmark evidence now exists for the exact grouped, execution-explain, and
  streaming paths touched during `WP8.5`

