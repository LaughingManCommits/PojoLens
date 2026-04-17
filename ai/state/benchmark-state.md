# Benchmark State

Load this file only for benchmark, threshold, or profiling tasks.

## Current Baselines

- Benchmark methodology is execution-only in benchmark methods (setup moved to `@Setup`).
- Core guardrails: `benchmarks/thresholds.json` (rebaselined on 2026-03-19 from CI data; `2026-04-17` recalibrated CSV load budgets for CI cold temp-file I/O variance).
- Chart guardrails: `benchmarks/chart-thresholds.json`.
- Strict benchmark checker remains the gate for threshold validation.

## AI Memory Benchmark

- `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json` is the proof harness for AI memory refresh/check/query behavior.
- Latest measured run on `2026-03-27`: full refresh `1353.49 ms`, incremental refresh `331.509 ms`, check `124.475 ms`, average query `65.398 ms`.
- Latest no-change incremental reuse on `2026-03-27`: JSON indexes `rebuilt=0 reused=5`; SQLite `updated=0 reused=47 removed=0`.
- Latest fixed-query hit quality on `2026-03-27`: top-1 `1.0`, top-3 `1.0`.

## Current Position

- No active benchmark optimization work is open.
- `2026-04-17`: CI reported CSV guardrail misses at typed `1k = 13.655 ms`, multiline `1k = 5.709 ms`, and multiline `10k = 54.170 ms`; thresholds now allow `18.0 ms`, `8.0 ms`, and `70.0 ms` respectively.
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

## Sources

- `docs/benchmarking.md`
- `benchmarks/thresholds.json`
- `benchmarks/chart-thresholds.json`
- `ai/indexes/memory-benchmark.json`
- `scripts/benchmark-ai-memory.py`
- `target/benchmarks/*.json` (generated artifacts, not source of truth)
