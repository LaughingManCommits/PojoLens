# Handoff

## Resume Order

- Load hot context, then `TODO.md` if the task touches active performance work.
- Load `ai/state/benchmark-state.md` and `ai/core/benchmark-context.md` only for benchmark, profiler, or threshold tasks.
- Rebuild the benchmark runner with `mvn -B -ntp -Pbenchmark-runner -DskipTests package` before quoting fresh JMH numbers.

## Current Priority

- WP19 is now the active package.
- A narrow `ReflectionUtil` follow-up now skips no-op projection casts when the raw value already matches the resolved leaf type, and `ReflectionUtilTest` now covers nested projection materialization from `Object[]` rows.
- The rebuilt `2026-03-16` hotspot suite (`target/benchmarks/hotspots-gc-2026-03-16.json`) now measures `reflectionToClassList|size=10000` at about `852.025 us/op` / `1,400,236 B/op` and `reflectionToDomainRows|size=10000` at about `418.191 us/op` / `2,840,026 B/op`.
- Those reflection latencies are materially lower than the recorded `2026-03-14` hotspot snapshot (`1115.501` and `557.219 us/op` respectively), but the allocation footprint is effectively unchanged, so WP19 remains open.
- `computedFieldJoinSelectiveMaterialization|size=10000` now measures about `303.247 us/op` / `3,532,314 B/op`, so it still owns the biggest absolute hotspot allocation even though its latency also moved down.
- A refreshed warmed JFR now exists at `target/wp19-current-2026-03-16.jfr`; it measured the join benchmark at about `0.666 ms/op` with JFR overhead and kept the same dominant class cluster: `ReflectionUtil$ResolvedFieldPath.read` (`837`), `FastArrayQuerySupport.applyComputedValues` (`399`), `ReflectionUtil.applyProjectionWritePlan` (`270`), `FastArrayQuerySupport.tryBuildJoinedState` (`241`), and `FastArrayQuerySupport.buildChildIndex` (`211`).
- First-repo-frame allocation in that warmed JFR is still led by `ReflectionUtil$ResolvedFieldPath.read` (`4220`), `FastArrayQuerySupport.materializeJoinedRow` (`3684`), and `FastArrayQuerySupport.buildChildIndex` (`3117`).
- Two smaller 2026-03-16 follow-up optimizations were benchmark-flat and not kept: a specialized `ReflectionUtil` nested-write path and a `Double` fast path in `FastArrayQuerySupport.applyComputedValues`.
- WP18 is parked as good enough for now; only reopen it if a fresh profile points back to scatter mapping or another chart-heavy path.

## Guardrails

- Do not reopen the broader `FilterImpl` raw-row delegation without a new hypothesis; it regressed the short `size=10000` reruns and was not kept.
- Do not reopen WP17 micro-tuning unless a fresh clean-tree profile shows a high-confidence win on the selective single-join path.
- Judge SQL-like work by absolute `ms/op`, allocation, and product value; fluent-vs-SQL-like ratios are diagnostic only.
- Follow the `TODO.md` consolidation guidance: share plans and metadata first, and do not merge specialized bean, `QueryRow`, or `Object[]` execution loops just to remove duplication.
- Do not spend more time on prepared-view vs copy micro-tuning for this stats shape unless a fresh profile reopens it; the dominant 2026-03-15 setup cost was mostly removed by the 2026-03-16 single-group fast-stats and bucket-format pass.
- Do not mark WP19 done from the hotspot suite alone; rerun warmed JFR on the current tree before claiming the `ReflectionUtil` / `FastArrayQuerySupport` class cluster has materially shifted.
- The warmed JFR has now been refreshed and it still points at the same classes, so the next WP19 change should favor `materializeJoinedRow` / `buildChildIndex` or a larger read-path reuse idea over another small branch-level tweak.

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

- If continuing WP19, start from the refreshed JFR instead of rerunning it immediately again.
- The next likely leverage point is `FastArrayQuerySupport.materializeJoinedRow` / `buildChildIndex`; both smaller 2026-03-16 follow-up experiments after the refreshed JFR were flat and were not kept.
- After code changes, rerun focused regressions plus `mvn -q test`.
- Use exact targeted `StatsQueryJmhBenchmark` reruns only as follow-up evidence, not as the sole attribution source when the session is already drift-heavy.
