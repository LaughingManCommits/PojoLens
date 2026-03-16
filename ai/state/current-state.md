# Current State

## Repository Health

- The repository remains a single-module Maven Java library that builds a `jar` on Java `17`.
- The latest recorded full suite is `mvn -q test` on `2026-03-16`, which passed with `448` tests.
- AI memory was compacted on `2026-03-15`; hot context now carries only startup-critical facts, while detailed benchmark history stays in `ai/state/benchmark-state.md` and `BENCHMARKS.md`.

## Active Work

- `TODO.md` was cleared on `2026-03-16` and is now intentionally empty; use the AI state files and benchmark artifacts as the active follow-up record until the backlog is repopulated.
- WP20 is complete; the computed-field join core budgets now reflect the post-WP17/WP19 implementation, and the hotspot remains diagnostic-only.
- WP19 is parked after two failed structural spikes; the newest deferred-materialization/projected-output attempt cut allocation but regressed the real warmed join target, so reopen it only with a genuinely new larger hypothesis.
- WP18 is parked again after a 2026-03-16 scatter-specific follow-up; reopen it only if a fresh profile shows another chart-specific root cause beyond the remaining broader row/query overhead.
- A new low-risk consolidation pass now shares repeated cold-path collection helpers through `CollectionUtil` instead of keeping separate `firstNonNull(...)` and limit-copy helpers in builder, filter, chart, and SQL-like code.
- Consolidation guidance remains unchanged: share plans and metadata first, and keep bean, `QueryRow`, and `Object[]` hot loops specialized unless a merged path is benchmark-positive.
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

## Landed Consolidation Increment

- `CollectionUtil` now centralizes repeated `firstNonNull(...)` and generic limit-copy behavior used by `FilterQueryBuilder`, `FastArrayQuerySupport`, `FastStatsQuerySupport`, `FilterImpl`, `ChartMapper`, `SqlLikeQuery`, and `SqlLikeExecutionSupport`.
- `CollectionUtilTest` now pins that helper behavior so later cleanup can keep consolidating cold-path plumbing without reintroducing local copies.
- `CollectionUtil.expectedMapCapacity(...)` now also centralizes the repeated map-capacity sizing rule previously duplicated in `AggregationEngine`, `FilterCore`, `GroupEngine`, `JoinEngine`, `FastArrayQuerySupport`, and `FastStatsQuerySupport`.
- `QueryRowAdapterSupport` now centralizes the duplicated cold-path `Object[] -> QueryRow` materializer previously repeated in `FastArrayQuerySupport` and `FastStatsQuerySupport`.
- `QueryRowAdapterSupportTest` now pins that adapter behavior so later consolidation can keep sharing row-shape adapters without touching the fast executors.
- `SchemaIndexUtil` now centralizes exact-name field-index planning for both raw field-name schemas and `QueryRow` field lists, and `ChartMapper` plus `SqlLikeExecutionSupport` now reuse it instead of keeping local exact-name indexing loops.
- `SchemaIndexUtilTest` now pins that exact-name index behavior so later consolidation can keep sharing schema-planning logic without touching the specialized executors.
- `QueryFieldLookupUtil` now centralizes exact-name `QueryField` index and value lookup, and `ChartMapper`, `SqlLikeExecutionSupport`, `JoinEngine`, and `RuleCleaner` now reuse it instead of keeping local exact-name scans.
- `QueryFieldLookupUtilTest` now pins that lookup behavior so later consolidation can keep sharing `QueryRow`/`QueryField` lookup logic without touching the specialized array and bean executors.

## Current Evidence

- Focused regressions (`TimeBucketAggregationTest`, `SqlLikeChartIntegrationTest`, `SqlLikeQueryContractTest`, `TimeBucketUtilTest`) plus full `mvn -q test` passed on `2026-03-16`.
- A rebuilt `2026-03-16` prepared fast-stats microbenchmark at `size=10000`, `-f 1 -wi 2 -i 5 -r 100ms -prof gc` now measures `sqlLikePreparedStatsFastPathSetupCopy` at about `509.906 us/op` / `2,145,675 B/op` and `sqlLikePreparedStatsFastPathSetupView` at about `512.749 us/op` / `2,145,195 B/op`, down materially from the prior `7.306/7.517 ms/op` and `16.8 MB/op` snapshot.
- Exact targeted reruns on `2026-03-16` at `size=10000`, `-f 1 -wi 3 -i 5 -r 250ms` now measure `fluentTimeBucketMetrics` at about `0.529 ms/op`, `fluentTimeBucketMetricsToChart` at about `0.531 ms/op`, `sqlLikeParseAndTimeBucketMetrics` at about `0.519 ms/op`, and `sqlLikeParseAndTimeBucketMetricsToChart` at about `0.526 ms/op`.
- Matching exact targeted reruns on `2026-03-16` now also measure grouped stats query/chart at about `0.273`, `0.273`, `0.277`, and `0.264 ms/op` for fluent grouped query, fluent grouped chart, SQL-like grouped query, and SQL-like grouped chart at `size=10000`.
- Exact targeted reruns on `2026-03-16` further measure multi-series line/area chart mapping at about `0.566`, `0.575`, `0.577`, and `0.572 ms/op` for fluent line, fluent area, SQL-like line, and SQL-like area at `size=10000`.
- Chart guardrails still pass; the latest rebuilt chart-suite rerun on `2026-03-15` stayed `45/45`.
- A rebuilt full chart guardrail rerun on `2026-03-16` still passed `45/45`; the largest remaining cold chart-mapping scores were scatter mapping at about `2.924 ms/op` fluent and `4.613 ms/op` SQL-like for `size=10000`, and about `19.743` / `24.991 ms/op` at `size=100000`.
- A direct WP18 scatter follow-up on `2026-03-16` reopened that remaining candidate: fresh warmed reruns of `ChartVisualizationJmhBenchmark.(fluentScatterMapping|sqlLikeScatterMapping)` measured about `1.476` / `13.848 ms/op` for fluent and `1.536` / `14.639 ms/op` for SQL-like at `size=10000/100000`.
- `ChartMapper` now accumulates multi-series charts through dense label/series indexes and caches validated x-axis labels once per distinct x instead of building nested per-series maps keyed by repeated formatted x strings.
- Focused chart regressions (`ChartMapperArrayRowsTest`, `ChartResultMapperMappingTest`, `SqlLikeChartIntegrationTest`) plus full `mvn -q test` passed after that scatter pass, and the full suite now totals `448` tests after the latest consolidation helper, adapter, schema-index, and query-field lookup coverage.
- A follow-up consolidation pass on `2026-03-16` added `SchemaIndexUtil` for exact-name schema/index planning, switched `ChartMapper` and `SqlLikeExecutionSupport` to it, and passed focused regressions (`SchemaIndexUtilTest`, `ChartMapperArrayRowsTest`, `ChartResultMapperMappingTest`, `SqlLikeAliasTest`, `SqlLikeChartIntegrationTest`) plus `mvn -q test` and `scripts/check-doc-consistency.ps1`.
- Another follow-up consolidation pass on `2026-03-16` added `QueryFieldLookupUtil` for exact-name `QueryField` access, switched `ChartMapper`, `SqlLikeExecutionSupport`, `JoinEngine`, and `RuleCleaner` to it, and passed focused regressions (`QueryFieldLookupUtilTest`, `SchemaIndexUtilTest`, `FilterCoreTest`, `ChartMapperArrayRowsTest`, `ChartResultMapperMappingTest`, `SqlLikeAliasTest`, `SqlLikeChartIntegrationTest`) plus `mvn -q test` and `scripts/check-doc-consistency.ps1`.
- Matching warmed reruns after the scatter pass now measure about `1.321` / `9.939 ms/op` for fluent and `1.300` / `9.760 ms/op` for SQL-like at `size=10000/100000`; matching `size=100000`, `-prof gc` reruns fell from about `41,297,324` / `38,497,149 B/op` to about `36,595,578` / `33,795,681 B/op` for fluent / SQL-like scatter.
- A rebuilt full chart guardrail rerun after the scatter pass still passed `45/45`, but its cold scatter scores drifted to about `5.403` / `8.460 ms/op` at `size=10000` and `22.605` / `36.384 ms/op` at `size=100000`; use that suite as a guardrail, not as the attribution source for this pass.
- A rebuilt full hotspot-suite rerun on `2026-03-16` with `@scripts/benchmark-suite-hotspots.args -f 1 -wi 1 -i 3 -r 100ms -prof gc` now measures `reflectionToClassList|size=10000` at about `852.025 us/op` / `1,400,236 B/op` and `reflectionToDomainRows|size=10000` at about `418.191 us/op` / `2,840,026 B/op`, versus the recorded `2026-03-14` snapshot of `1115.501 us/op` / `1,400,238 B/op` and `557.219 us/op` / `2,840,027 B/op`.
- The same `2026-03-16` hotspot-suite rerun measures `computedFieldJoinSelectiveMaterialization|size=10000` at about `303.247 us/op` / `3,532,314 B/op` and `groupedMultiMetricAggregation|size=10000` at about `353.796 us/op` / `1,043,042 B/op`.
- WP20 reran the computed-field benchmarks on `2026-03-16`: repeated strict-style cold reruns of `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at `-f 1 -wi 0 -i 1 -r 100ms` measured about `2.597`, `3.766`, and `6.020 ms/op` at `size=1000` and about `114.241`, `113.818`, and `60.348 ms/op` at `size=10000`.
- Matching warmed `-prof gc` reruns on `2026-03-16` measured `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at about `0.063 ms/op` / `247,520 B/op` for `size=1000` and `0.603 ms/op` / `2,302,715 B/op` for `size=10000`, versus `manualHashJoinLeftComputedField` at about `0.009 ms/op` / `84,512 B/op` and `0.098 ms/op` / `927,128 B/op`.
- A fresh WP20 hotspot rerun on `2026-03-16` measured `computedFieldJoinSelectiveMaterialization` at about `34.472 us/op` / `364,232 B/op` for `size=1000` and `319.695 us/op` / `3,532,314 B/op` for `size=10000`; the latency is improved, but the allocation profile is still too large for a stable strict gate.
- `benchmarks/thresholds.json` now tightens `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` to `25 ms/op` at `size=1000` and `200 ms/op` at `size=10000`, and a rebuilt core strict-suite rerun on `2026-03-16` still passed `42/42` with that workload measuring about `5.263` and `118.793 ms/op`.
- A refreshed warmed JFR on `2026-03-16` produced `target/wp19-current-2026-03-16.jfr` and measured `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField|size=10000` at about `0.666 ms/op` with JFR overhead, which is effectively flat versus the last recorded warm-profile cycle.
- That refreshed warmed JFR still concentrates CPU in `ReflectionUtil$ResolvedFieldPath.read` (`837`), `FastArrayQuerySupport.applyComputedValues` (`399`), `ReflectionUtil.applyProjectionWritePlan` (`270`), `FastArrayQuerySupport.tryBuildJoinedState` (`241`), and `FastArrayQuerySupport.buildChildIndex` (`211`); allocation still centers in `ResolvedFieldPath.read` (`4220`), `materializeJoinedRow` (`3684`), and `buildChildIndex` (`3117`).
- Two post-profile micro-optimizations on `2026-03-16` were benchmark-flat and were not kept: a specialized nested-write path in `ReflectionUtil` and a `Double` fast path in `FastArrayQuerySupport.applyComputedValues`.
- A larger structural spike that tried to prefilter fast-array joined rows during join-state creation regressed the real warmed target from about `0.584 ms/op` to about `0.700 ms/op`, so it was reverted. A clean warmed rerun on the reverted code recovered to about `0.595 ms/op`.
- A second structural spike on `2026-03-16` then tried to defer fast-array joined-row materialization and filter directly to compact projected rows. Comparable short `-prof gc` reruns measured about `0.073 ms/op` / `220,040 B/op` at `size=1000` and `0.712 ms/op` / `2,022,740 B/op` at `size=10000`, versus the current warmed checkpoint of about `0.063 ms/op` / `247,520 B/op` and `0.603 ms/op` / `2,302,715 B/op`; allocation improved, but latency regressed, so it was reverted as well.
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
- After the scatter pass, the remaining `size=100000` scatter cost looks less chart-specific and more like broader row-materialization/query overhead; do not keep tuning `ChartMapper` without a fresh profile that isolates another chart-specific bottleneck.
- The strict computed-field join budget is intentionally still conservative because `-wi 0 -i 1` cold runs drift materially; do not tighten below `25/200 ms/op` without another repeated strict rerun cycle.
- WP19 is not done just because the hotspot-suite latencies fell; reflection/conversion allocations are still essentially flat and the refreshed warmed JFR kept the same dominant class cluster.
- Do not keep spending time on WP19 without a materially different structural idea. Both the prefiltered-fast-state redesign and the deferred-materialization/projected-output redesign regressed the warmed target and were not kept.
