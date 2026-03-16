# Handoff

## Resume Order

- Load hot context, then `TODO.md` if the task touches active performance work.
- Load `ai/state/benchmark-state.md` and `ai/core/benchmark-context.md` only for benchmark, profiler, or threshold tasks.
- Rebuild the benchmark runner with `mvn -B -ntp -Pbenchmark-runner -DskipTests package` before quoting fresh JMH numbers.

## Current Priority

- WP19 is now parked after two reverted structural spikes.
- WP20 is now complete; the computed-field join core budgets were rebased and the hotspot policy is explicit.
- WP18 also landed one more 2026-03-16 scatter-specific increment and is parked again unless a fresh profile exposes another chart-specific root cause.
- If performance work continues, reopen WP19 only with a materially different structural idea, or reopen WP18 only if a fresh scatter/chart profile points beyond the remaining broader row/query overhead.
- A narrow `ReflectionUtil` follow-up now skips no-op projection casts when the raw value already matches the resolved leaf type, and `ReflectionUtilTest` now covers nested projection materialization from `Object[]` rows.
- The rebuilt `2026-03-16` hotspot suite (`target/benchmarks/hotspots-gc-2026-03-16.json`) now measures `reflectionToClassList|size=10000` at about `852.025 us/op` / `1,400,236 B/op` and `reflectionToDomainRows|size=10000` at about `418.191 us/op` / `2,840,026 B/op`.
- Those reflection latencies are materially lower than the recorded `2026-03-14` hotspot snapshot (`1115.501` and `557.219 us/op` respectively), but the allocation footprint is effectively unchanged, which is context for a future reopen rather than a reason to keep WP19 active now.
- `computedFieldJoinSelectiveMaterialization|size=10000` now measures about `303.247 us/op` / `3,532,314 B/op`, so it still owns the biggest absolute hotspot allocation even though its latency also moved down.
- WP20 reran the computed-field join benchmarks on `2026-03-16`: repeated strict-style cold reruns of `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` measured about `2.597`, `3.766`, and `6.020 ms/op` at `size=1000` and about `114.241`, `113.818`, and `60.348 ms/op` at `size=10000`.
- Matching warmed `-prof gc` reruns measured `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` at about `0.063 ms/op` / `247,520 B/op` for `size=1000` and `0.603 ms/op` / `2,302,715 B/op` for `size=10000`, versus `manualHashJoinLeftComputedField` at about `0.009 ms/op` / `84,512 B/op` and `0.098 ms/op` / `927,128 B/op`.
- A fresh hotspot rerun on `2026-03-16` measured `computedFieldJoinSelectiveMaterialization` at about `34.472 us/op` / `364,232 B/op` for `size=1000` and `319.695 us/op` / `3,532,314 B/op` for `size=10000`; keep it diagnostic-only for now.
- `benchmarks/thresholds.json` now tightens `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` to `25 ms/op` at `size=1000` and `200 ms/op` at `size=10000`. A rebuilt strict core suite wrote `target/wp20-core-benchmarks.json`, and `BenchmarkThresholdChecker` still passed in `--strict` mode with the computed-field join scores at about `5.263` and `118.793 ms/op`.
- A direct scatter-specific follow-up on `2026-03-16` measured warmed `ChartVisualizationJmhBenchmark.(fluentScatterMapping|sqlLikeScatterMapping)` at about `1.476` / `13.848 ms/op` for fluent and `1.536` / `14.639 ms/op` for SQL-like at `size=10000/100000`, which was enough to reopen WP18 for one focused pass.
- That scatter pass changed `ChartMapper` to accumulate multi-series charts through dense label/series indexes and cache validated x-axis labels once per distinct x instead of building nested per-series maps keyed by repeated formatted x strings.
- Matching warmed reruns after the pass now measure about `1.321` / `9.939 ms/op` for fluent and `1.300` / `9.760 ms/op` for SQL-like at `size=10000/100000`; matching `size=100000`, `-prof gc` reruns fell from about `41,297,324` / `38,497,149 B/op` to about `36,595,578` / `33,795,681 B/op`.
- Focused chart regressions plus full `mvn -q test` passed after the scatter pass, and the suite now covers `436` tests.
- A rebuilt full chart guardrail rerun still passed `45/45`, but the cold scatter entries drifted to about `5.403` / `8.460 ms/op` at `size=10000` and `22.605` / `36.384 ms/op` at `size=100000`; treat that suite as guardrail validation, not patch attribution.
- A refreshed warmed JFR now exists at `target/wp19-current-2026-03-16.jfr`; it measured the join benchmark at about `0.666 ms/op` with JFR overhead and kept the same dominant class cluster: `ReflectionUtil$ResolvedFieldPath.read` (`837`), `FastArrayQuerySupport.applyComputedValues` (`399`), `ReflectionUtil.applyProjectionWritePlan` (`270`), `FastArrayQuerySupport.tryBuildJoinedState` (`241`), and `FastArrayQuerySupport.buildChildIndex` (`211`).
- First-repo-frame allocation in that warmed JFR is still led by `ReflectionUtil$ResolvedFieldPath.read` (`4220`), `FastArrayQuerySupport.materializeJoinedRow` (`3684`), and `FastArrayQuerySupport.buildChildIndex` (`3117`).
- Two smaller 2026-03-16 follow-up optimizations were benchmark-flat and not kept: a specialized `ReflectionUtil` nested-write path and a `Double` fast path in `FastArrayQuerySupport.applyComputedValues`.
- A larger 2026-03-16 structural spike that tried to prefilter fast-array joined rows during `tryBuildJoinedState()` regressed the real warmed target from about `0.584 ms/op` to about `0.700 ms/op`, so it was reverted. A clean warmed rerun on the reverted code recovered to about `0.595 ms/op`.
- A second 2026-03-16 structural spike then tried to defer fast-array joined-row materialization and filter directly to compact projected rows. Comparable short `-prof gc` reruns measured about `0.073 ms/op` / `220,040 B/op` at `size=1000` and `0.712 ms/op` / `2,022,740 B/op` at `size=10000`, versus the current warmed checkpoint of about `0.063 ms/op` / `247,520 B/op` and `0.603 ms/op` / `2,302,715 B/op`; allocation improved, but the warmed target regressed, so it was reverted too.
- WP18 is parked as good enough for now; only reopen it if a fresh profile points back to scatter mapping or another chart-heavy path.

## Guardrails

- Do not reopen the broader `FilterImpl` raw-row delegation without a new hypothesis; it regressed the short `size=10000` reruns and was not kept.
- Do not reopen WP17 micro-tuning unless a fresh clean-tree profile shows a high-confidence win on the selective single-join path.
- Judge SQL-like work by absolute `ms/op`, allocation, and product value; fluent-vs-SQL-like ratios are diagnostic only.
- Follow the `TODO.md` consolidation guidance: share plans and metadata first, and do not merge specialized bean, `QueryRow`, or `Object[]` execution loops just to remove duplication.
- Do not spend more time on prepared-view vs copy micro-tuning for this stats shape unless a fresh profile reopens it; the dominant 2026-03-15 setup cost was mostly removed by the 2026-03-16 single-group fast-stats and bucket-format pass.
- Do not reopen WP19 without a structurally different hypothesis. Both the prefiltered-fast-state redesign and the deferred-materialization/projected-output redesign already regressed the warmed target and were not kept.

## Useful Files

- `TODO.md`
- `BENCHMARKS.md`
- `ai/state/benchmark-state.md`
- `benchmarks/thresholds.json`
- `ai/core/benchmark-context.md`
- `src/main/java/laughing/man/commits/chart/ChartMapper.java`
- `src/main/java/laughing/man/commits/benchmark/HotspotMicroJmhBenchmark.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java`
- `src/main/java/laughing/man/commits/util/ObjectUtil.java`
- `target/benchmarks/hotspots-gc-2026-03-16.json`
- `target/wp18-scatter-warm.json`
- `target/wp18-scatter-warm-after.json`
- `target/wp18-scatter-gc.json`
- `target/wp18-scatter-gc-after.json`
- `target/wp18-chart-benchmarks-after.json`
- `target/wp20-core-benchmarks.json`
- `target/wp20-computed-join-warm-gc.json`
- `target/wp20-computed-join-hotspot-gc.json`
- `target/wp19-current-2026-03-16.jfr`

## Next Validation

- Do not reopen WP20 unless the computed-field join implementation changes again enough to invalidate the new `25/200 ms/op` cold budgets.
- Do not reopen WP18 just because the cold chart suite drifts; use targeted warmed reruns or a direct profile to isolate chart-specific cost first.
- If continuing performance work, prefer WP19 only with a materially different structural idea, or reopen WP18 only with fresh scatter/chart evidence that points past the new `ChartMapper` accumulator pass.
- Only reopen WP19 with a materially different structural idea than the reverted prefiltered-fast-state spike.
- After code changes, rerun focused regressions plus `mvn -q test`.
- Use exact targeted `StatsQueryJmhBenchmark` reruns only as follow-up evidence, not as the sole attribution source when the session is already drift-heavy.
