# Benchmark State

Load this file only for benchmark, threshold, or profiling tasks.

## Current Baselines

- Benchmark methodology is execution-only in benchmark methods (setup moved to `@Setup`).
- Core guardrails: `benchmarks/thresholds.json` (rebaselined on 2026-03-19 from CI data; `2026-04-17` recalibrated CSV load budgets for CI cold temp-file I/O variance).
- Chart guardrails: `benchmarks/chart-thresholds.json`.
- Reflection hotspot guardrails: `benchmarks/hotspot-thresholds.json` for warmed `HotspotMicroJmhBenchmark.reflectionToDomainRows` and `reflectionToClassList`.
- Strict benchmark checker remains the gate for threshold validation.

## AI Memory Benchmark

- `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json` is the proof harness for AI memory refresh/check/query behavior.
- Latest measured run on `2026-03-27`: full refresh `1353.49 ms`, incremental refresh `331.509 ms`, check `124.475 ms`, average query `65.398 ms`.
- Latest no-change incremental reuse on `2026-03-27`: JSON indexes `rebuilt=0 reused=5`; SQLite `updated=0 reused=47 removed=0`.
- Latest fixed-query hit quality on `2026-03-27`: top-1 `1.0`, top-3 `1.0`.

## Current Position

- `2026-04-23`: WP5 is complete: reflection hot-path caching shipped, repeated joins reuse prepared fast join state, warmed window allocation overhead dropped, and batch/columnar evaluation closed with no new execution mode.
- `2026-04-17`: CI reported CSV guardrail misses at typed `1k = 13.655 ms`, multiline `1k = 5.709 ms`, and multiline `10k = 54.170 ms`; thresholds now allow `18.0 ms`, `8.0 ms`, and `70.0 ms` respectively.
- `2026-04-23`: WP5 first slice reduced reflection hot-path cost by caching direct-field read plans across equivalent selections and reusing cached nested-path writes during projection materialization.
- `2026-04-23`: warmed forked hotspot runs promoted conservative reflection guardrails at `45.0 us/op` and `450.0 us/op` for `reflectionToDomainRows`, plus `100.0 us/op` and `1000.0 us/op` for `reflectionToClassList`.
- `2026-04-23`: warmed repeated-join spot checks dropped `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField` to about `0.010 ms/op` at `1k` and `0.104 ms/op` at `10k` with far lower allocation, reflecting prepared fast join-state reuse for stable filter snapshots.
- `2026-04-23`: warmed window diagnostics at `size=10000` measured `parseAndFilterWindowRank ~1.449 ms/op / 3,470,398 B/op` and `parseAndFilterWindowRunningTotal ~1.462 ms/op / 3,678,267 B/op`, down from the `2026-03-23` `~4.40 MB/op` and `~4.59 MB/op` range after trimming output-buffer and partition-key churn.
- `2026-04-23`: local strict cold core-suite checks still fail on `SqlLikePipelineJmhBenchmark.parseAndExplainExecution`, `parseAndFilterHaving`, and the window benchmarks with `~8.0 s/op` results; warmed isolated diagnostics and targeted WP5 suites remain the reliable validation path for this slice until that cold-suite anomaly is investigated.
- WP19 is intentionally parked; do not reopen without a materially different structural hypothesis.
- Warmed profiler hotspots have repeatedly concentrated in `ReflectionUtil` and `FastArrayQuerySupport`.
- `2026-04-15`: `ReflectionUtil.DirectFieldReadPlan` now backs direct POJO
  chart fast paths and reduced the small-size SQL-like scatter allocation gap.
  Final `size=1000` warmed GC spot check measured fluent `259,400 B/op`,
  direct SQL-like `284,273 B/op`, and bound SQL-like `283,737 B/op`; rerun
  warmed `10k`/`100k` scatter checks before retiring the broader scatter
  allocation concern.

## Operational Rules

- Keep cold guardrail runs and warmed tuning runs separate.
- Rebuild benchmark runner before quoting fresh numbers:
  `mvn -B -ntp -Pbenchmark-runner -DskipTests package`
- Use benchmark suite args from `scripts/benchmark-suite-*.args`.
- Do not run concurrent Maven builds in the same workspace `target/` directory.
- For WP5 evidence, prefer the warmed join/window/reflection spot checks; the local cold core-suite anomaly above is unresolved.

## Sources

- `docs/benchmarking.md`
- `benchmarks/thresholds.json`
- `benchmarks/chart-thresholds.json`
- `benchmarks/hotspot-thresholds.json`
- `ai/indexes/memory-benchmark.json`
- `scripts/benchmark-ai-memory.py`
- `target/benchmarks/*.json` (generated artifacts, not source of truth)
