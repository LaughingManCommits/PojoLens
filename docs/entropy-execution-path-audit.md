# Entropy Execution Path Audit

This document is the `WP8.4` audit artifact for the `Entropy Reduction`
roadmap.
It maps the duplicate internal execution, planning, binding, and
materialization paths that are still present after the `WP8.2` and `WP8.3`
surface decisions.

## Summary

- The largest remaining duplicate-path cost is not public overload count; it is
  repeated internal stage walking across fluent execution, SQL-like execution,
  and SQL-like explain.
- Three `WP8.5` targets stand out as code-deleting and plausibly
  performance-neutral or performance-positive:
  - unify fluent stage execution across flat, grouped, and chart flows
  - replace SQL-like explain stage replay with the same stage primitives used
    by normal execution
  - unify SQL-like select or alias materialization decisions across
    `filter`, `stream`, and `chart`
- Two smaller duplications should stay out of the first `WP8.5` cut:
  - `preparedExecutionView(...)` vs `preparedExecutionCopy(...)`
  - broader chart/tabular row-shape helper convergence

## Scope Guardrails

- `WP8.4` targets internal execution-path duplication only.
- Public overload ladders on `SqlLikeQuery`, `ReportDefinition`,
  `ChartQueryPreset`, and `StatsViewPreset` are already handled as surface
  policy in `WP8.3`; they are not the main deletion target here.
- Any `WP8.5` cleanup should preserve the current benchmark guardrails for
  grouped stats, chart mapping, streaming, and prepared SQL-like execution.

## Duplicate Path Map

| Stage | Current paths | Duplicate pattern | Why it matters | `WP8.5` disposition |
| --- | --- | --- | --- | --- |
| Fluent execution stages | `FilterImpl.filterGroups(...)`, `FilterImpl.filterRows(...)`, and `FilterImpl.chart(...)` | Each path rebuilds overlapping distinct/filter/group/order/materialization logic with only the terminal output shape changing | Keeps the hottest fluent path hard to simplify and creates behavior-drift risk when grouped, chart, and flat execution evolve separately | `Target` |
| SQL-like execution vs explain | `SqlLikeExecutionFlowSupport.executeFilter(...)`, `executeStream(...)`, `executeChart(...)`, and `buildStageRowCounts(...)` | Normal execution and explain both walk where/group/having/qualify/order/limit stages, but explain replays them with a second bespoke pipeline | The explain path can drift from live execution, and qualify/window handling is rebuilt through an extra fluent round-trip | `Target` |
| SQL-like select or alias materialization | `SqlLikeExecutionFlowSupport.executeFilter(...)`, `executeStream(...)`, and `executeChart(...)` | The same `select != null && !wildcard && ...` branching is reimplemented three times, with separate decisions for aliased projection, raw rows, and chart mapping | Makes SQL-like output behavior harder to reason about and spreads select/alias fixes across three methods | `Target` |
| Optional join dispatch | `SqlLikeExecutionSupport.executeWithOptionalJoin(...)`, `executeStreamWithOptionalJoin(...)`, and unused `executeIteratorWithOptionalJoin(...)` | Three parallel join-or-no-join dispatch helpers differ only by terminal operation | Small but real code duplication; one helper is currently unused | `Small target` |
| Prepared SQL-like rebinding | `FilterQueryBuilder.preparedExecutionView(...)` and `preparedExecutionCopy(...)` | Both rebuild prepared execution state, but the view path exists to avoid copy cost and the copy path is still the fallback for `QueryRow` inputs | There is duplication, but it is intentional and benchmarked; deleting blindly would trade simplicity for compatibility/performance risk | `Defer` |
| Row-shape adapters | `ChartMapper`, `ChartResultMapper`, `TabularRows`, and `SqlLikeExecutionSupport.projectAliasedRows(...)` | Multiple helpers adapt `QueryRow`, `Object[]`, and POJO rows into chart or tabular payloads | There is conceptual overlap, but most code is type-shape specific and some of it is public advanced surface | `Do not lead with this` |

## `WP8.5` Shortlist

### Target 1: Shared Fluent Stage Runner

Scope:
- extract the common fluent execution stages now spread across
  `FilterImpl.filterGroups(...)`, `filterRows(...)`, and `chart(...)`
- keep fast-path decisions (`FastArrayQuerySupport`, `FastStatsQuerySupport`,
  `FastPojoStreamSupport`) at the edges, but collapse the post-decision stage
  walking onto one internal runner

Expected deletion:
- one shared stage runner can replace the repeated distinct/filter/order/chart
  and grouped-vs-flat branching logic
- chart production should consume the same execution result object rather than
  re-deciding row acquisition in `chart(...)`

Validation hooks:
- tests:
  `PojoLensOrderGroupBehaviorTest`,
  `PojoLensConcurrencyBehaviorTest`,
  `SqlLikeMappingParityTest`
- benchmarks:
  `StatsQueryJmhBenchmark`,
  `StreamingExecutionJmhBenchmark`,
  `StreamsBaselineJmhBenchmark`

Risk:
- high hot-path sensitivity; any abstraction must not disable the existing fast
  array, fast stats, or lazy stream paths

### Target 2: Shared SQL-like Stage Accounting

Scope:
- replace `SqlLikeExecutionFlowSupport.buildStageRowCounts(...)` with stage
  accounting that reuses the same primitives as normal SQL-like execution
- remove `applyQualifyStageViaFluent(...)` and reuse the existing
  `FluentWindowSupport` and `FluentQualifySupport` stage helpers directly

Expected deletion:
- deletes the bespoke explain-only stage replay
- removes one extra builder reconstruction path for SQL-like `QUALIFY`

Validation hooks:
- tests:
  `ExplainToolingTest`,
  `SqlLikeMappingParityTest`
- benchmarks:
  `SqlLikePipelineJmhBenchmark`,
  `StatsQueryJmhBenchmark`

Risk:
- explain output is contract-sensitive; stage counts and `joinSourceBindings`
  metadata must remain deterministic

### Target 3: Unified SQL-like Output Materializer

Scope:
- factor the repeated select/alias/computed/window decision tree from
  `executeFilter(...)`, `executeStream(...)`, and `executeChart(...)` into one
  internal mode resolver plus terminal adapters
- keep streaming-specific behavior, but derive it from the same execution mode
  rather than rechecking the same flags in multiple methods

Expected deletion:
- removes repeated alias/wildcard/qualify branching
- shrinks the number of places that decide between raw rows,
  `ReflectionUtil.toClassList(...)`, `SqlLikeExecutionSupport.projectAliasedRows(...)`,
  and chart mapping

Validation hooks:
- tests:
  `SqlLikeMappingParityTest`,
  `ChartMapperArrayRowsTest`,
  `ChartResultMapperMappingTest`
- benchmarks:
  `StreamingExecutionJmhBenchmark`,
  `ChartVisualizationJmhBenchmark`,
  `SqlLikePipelineJmhBenchmark`

Risk:
- medium; list-vs-stream behavior and alias projection semantics must remain
  unchanged

### Target 4: Optional-Join Helper Cleanup

Scope:
- remove or fold the unused
  `SqlLikeExecutionSupport.executeIteratorWithOptionalJoin(...)`
- after Target 3, consider collapsing the remaining optional-join helpers into
  one terminal adapter pattern

Expected deletion:
- guaranteed deletion of the currently unused iterator helper
- possible small follow-on simplification of filter/stream dispatch

Validation hooks:
- tests:
  `SqlLikeJoinBindingsContractTest`,
  `PublicApiEcosystemCoverageTest`
- benchmarks:
  `StreamingExecutionJmhBenchmark`

Risk:
- low; this is small cleanup, not a primary entropy lever

## Deferred Or Low-Value Targets

### Keep Dual Prepared Rebind Modes For Now

Why deferred:
- `preparedExecutionView(...)` is already the preferred fast path
- `preparedExecutionCopy(...)` remains the fallback for `QueryRow`-backed
  sources and is covered by
  `FilterQueryBuilderSelectiveMaterializationTest`
- `HotspotMicroJmhBenchmark` already proves the copy-vs-view cost delta, so
  deleting one path without redesign would be risky rather than simplifying

### Do Not Lead `WP8.5` With Chart/Tabular Helper Convergence

Why deferred:
- `ChartMapper`, `ChartResultMapper`, `TabularRows`, and aliased projection
  helpers all touch row-shape adaptation, but they sit at different public or
  semi-public seams
- convergence here is more likely to reshuffle code than delete it
- this is a complexity-only target unless a later implementation pass finds a
  concrete shared adapter that reduces both code and runtime branches

## Result

- `WP8.4` identifies three primary `WP8.5` execution-path unification targets:
  shared fluent stage running, shared SQL-like stage accounting, and a unified
  SQL-like output materializer.
- `WP8.4` also identifies one low-risk cleanup target:
  optional-join helper consolidation beginning with the unused iterator helper.
- `WP8.5` should not start with prepared-rebind mode removal or broad
  chart/tabular helper convergence, because those are currently lower-value or
  higher-risk than the stage-runner work above.

