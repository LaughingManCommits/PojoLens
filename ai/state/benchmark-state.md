# Benchmark State

Load this file only for benchmark, threshold, or profiling tasks.

## Current Baselines

- Benchmark methodology is execution-only in benchmark methods (setup moved to `@Setup`).
- Core guardrails: `benchmarks/thresholds.json` (rebaselined on 2026-03-19 from CI data; `2026-04-10` added guarded `CsvLoadJmhBenchmark` budgets for typed and multiline CSV loads).
- Chart guardrails: `benchmarks/chart-thresholds.json`.
- Strict benchmark checker remains the gate for threshold validation.

## AI Memory Benchmark

- `scripts/benchmark-ai-memory.ps1 -Report ai/indexes/memory-benchmark.json` is the proof harness for AI memory refresh/check/query behavior.
- Latest measured run on `2026-03-27`: full refresh `1353.49 ms`, incremental refresh `331.509 ms`, check `124.475 ms`, average query `65.398 ms`.
- Latest no-change incremental reuse on `2026-03-27`: JSON indexes `rebuilt=0 reused=5`; SQLite `updated=0 reused=47 removed=0`.
- Latest fixed-query hit quality on `2026-03-27`: top-1 `1.0`, top-3 `1.0`.

## Current Position

- No active benchmark optimization work is open.
- WP19 is intentionally parked; do not reopen without a materially different structural hypothesis.
- Warmed profiler hotspots have repeatedly concentrated in `ReflectionUtil` and `FastArrayQuerySupport`.

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
