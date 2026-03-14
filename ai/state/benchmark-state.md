# Benchmark State

Load only when the task is about benchmarks, thresholds, parity, profiling, or performance regressions. Skip this file for feature work, bug fixes, or ordinary documentation edits.

- Snapshot date: `2026-03-14`.
- Suites run: core, chart, Streams baseline, cache concurrency, hotspot `-prof gc`, and standalone `PojoLensJmhBenchmark`.
- Threshold status: core `42/42` pass; chart `45/45` pass.
- Historical chart parity note: the 2026-03-14 snapshot recorded `5/15` fluent-vs-SQL-like ratio overages (`SCATTER 1k`; `BAR`, `LINE`, `PIE`, `SCATTER` at `10k`), but that ratio is now diagnostic-only rather than a benchmark gate.
- Cold core stress: `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField|size=10000` measured `136.208 ms/op`; `PojoLensPipelineJmhBenchmark.fullGroupPipeline|size=1000` used the largest share of its current budget (`36.8%`).
- Hotspot stress: highest latency `reflectionToClassList|size=10000` at `1115.501 us/op`; highest allocation `computedFieldJoinSelectiveMaterialization|size=10000` at `3,532,314 B/op`.
- Recurring warm JFR stress: the common hotspot classes across the current warmed join profiles are `ReflectionUtil` and `FastArrayQuerySupport`; load `ai/core/benchmark-context.md` only when the task needs the class/method breakdown.
- Baseline stress: `fluentFilterProjection|size=10000` measured `126.115 ms/op` versus `0.130 ms/op` for Streams; legacy `pojoLensFilter|size=10000` measured `126.650 ms/op` versus `0.016 ms/op` for manual.
- Cache stress: `statsPlanBuildHotSetConcurrent` measured `114.799 ops/s` versus `312,607,925 ops/s` for `sqlLikeParseHotSetConcurrent`.
- Detailed report: `BENCHMARKS.md`. Raw artifacts live under `target/benchmarks/`.
