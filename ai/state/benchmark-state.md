# Benchmark State

Load this file only for benchmark, threshold, or profiling tasks.

## Current Baselines

- Benchmark methodology is execution-only in benchmark methods (setup moved to `@Setup`).
- Core guardrails: `benchmarks/thresholds.json` (rebaselined on 2026-03-19 from CI data).
- Chart guardrails: `benchmarks/chart-thresholds.json`.
- Strict benchmark checker remains the gate for threshold validation.

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
- `target/benchmarks/*.json` (generated artifacts, not source of truth)
