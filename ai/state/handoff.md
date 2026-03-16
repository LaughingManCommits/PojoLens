# Handoff

## Resume Order

- Load hot context, then `TODO.md` if the task touches active performance work.
- Load `ai/state/benchmark-state.md` and `ai/core/benchmark-context.md` only for benchmark, profiler, or threshold tasks.
- Rebuild the benchmark runner with `mvn -B -ntp -Pbenchmark-runner -DskipTests package` before quoting fresh JMH numbers.

## Current Priority

- WP19 is now parked.
- If performance work continues, WP20 is the next recommended package.
- A narrow `ReflectionUtil` follow-up now skips no-op projection casts when the raw value already matches the resolved leaf type, and `ReflectionUtilTest` now covers nested projection materialization from `Object[]` rows.
- The rebuilt `2026-03-16` hotspot suite (`target/benchmarks/hotspots-gc-2026-03-16.json`) now measures `reflectionToClassList|size=10000` at about `852.025 us/op` / `1,400,236 B/op` and `reflectionToDomainRows|size=10000` at about `418.191 us/op` / `2,840,026 B/op`.
- Those reflection latencies are materially lower than the recorded `2026-03-14` hotspot snapshot (`1115.501` and `557.219 us/op` respectively), but the allocation footprint is effectively unchanged, which is context for a future reopen rather than a reason to keep WP19 active now.
- `computedFieldJoinSelectiveMaterialization|size=10000` now measures about `303.247 us/op` / `3,532,314 B/op`, so it still owns the biggest absolute hotspot allocation even though its latency also moved down.
- A refreshed warmed JFR now exists at `target/wp19-current-2026-03-16.jfr`; it measured the join benchmark at about `0.666 ms/op` with JFR overhead and kept the same dominant class cluster: `ReflectionUtil$ResolvedFieldPath.read` (`837`), `FastArrayQuerySupport.applyComputedValues` (`399`), `ReflectionUtil.applyProjectionWritePlan` (`270`), `FastArrayQuerySupport.tryBuildJoinedState` (`241`), and `FastArrayQuerySupport.buildChildIndex` (`211`).
- First-repo-frame allocation in that warmed JFR is still led by `ReflectionUtil$ResolvedFieldPath.read` (`4220`), `FastArrayQuerySupport.materializeJoinedRow` (`3684`), and `FastArrayQuerySupport.buildChildIndex` (`3117`).
- Two smaller 2026-03-16 follow-up optimizations were benchmark-flat and not kept: a specialized `ReflectionUtil` nested-write path and a `Double` fast path in `FastArrayQuerySupport.applyComputedValues`.
- A larger 2026-03-16 structural spike that tried to prefilter fast-array joined rows during `tryBuildJoinedState()` regressed the real warmed target from about `0.584 ms/op` to about `0.700 ms/op`, so it was reverted. A clean warmed rerun on the reverted code recovered to about `0.595 ms/op`.
- WP18 is parked as good enough for now; only reopen it if a fresh profile points back to scatter mapping or another chart-heavy path.

## Guardrails

- Do not reopen the broader `FilterImpl` raw-row delegation without a new hypothesis; it regressed the short `size=10000` reruns and was not kept.
- Do not reopen WP17 micro-tuning unless a fresh clean-tree profile shows a high-confidence win on the selective single-join path.
- Judge SQL-like work by absolute `ms/op`, allocation, and product value; fluent-vs-SQL-like ratios are diagnostic only.
- Follow the `TODO.md` consolidation guidance: share plans and metadata first, and do not merge specialized bean, `QueryRow`, or `Object[]` execution loops just to remove duplication.
- Do not spend more time on prepared-view vs copy micro-tuning for this stats shape unless a fresh profile reopens it; the dominant 2026-03-15 setup cost was mostly removed by the 2026-03-16 single-group fast-stats and bucket-format pass.
- Do not reopen WP19 without a structurally different hypothesis. The obvious prefiltered-fast-state redesign already regressed the warmed target and was not kept.

## Useful Files

- `TODO.md`
- `BENCHMARKS.md`
- `ai/state/benchmark-state.md`
- `ai/core/benchmark-context.md`
- `src/main/java/laughing/man/commits/benchmark/HotspotMicroJmhBenchmark.java`
- `src/main/java/laughing/man/commits/util/ReflectionUtil.java`
- `src/main/java/laughing/man/commits/filter/FastArrayQuerySupport.java`
- `src/main/java/laughing/man/commits/util/ObjectUtil.java`
- `target/benchmarks/hotspots-gc-2026-03-16.json`
- `target/wp19-current-2026-03-16.jfr`

## Next Validation

- If continuing performance work, shift to WP20 rather than reopening WP19 immediately.
- Only reopen WP19 with a materially different structural idea than the reverted prefiltered-fast-state spike.
- After code changes, rerun focused regressions plus `mvn -q test`.
- Use exact targeted `StatsQueryJmhBenchmark` reruns only as follow-up evidence, not as the sole attribution source when the session is already drift-heavy.
