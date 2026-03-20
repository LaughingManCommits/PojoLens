# Benchmark Context

Load only when work touches benchmark suites, threshold budgets, or performance profiling.

## Benchmark Surface

- Core guardrail suite: `scripts/benchmark-suite-main.args` + `benchmarks/thresholds.json`
- Chart guardrail suite: `scripts/benchmark-suite-chart.args` + `benchmarks/chart-thresholds.json`
- Baseline suite: `scripts/benchmark-suite-baseline.args`
- Cache suite: `scripts/benchmark-suite-cache.args`
- Hotspot suite: `scripts/benchmark-suite-hotspots.args`

## Interpretation Rules

- Treat threshold JSON files as guardrails, not performance targets.
- Keep cold guardrail runs and warmed tuning runs separate.
- Do not use fluent-vs-SQL-like ratio as a merge gate.
- Use hotspot allocation metrics for allocation work; use core/chart suites for public budget risk.
- Rebuild the benchmark runner jar before quoting fresh numbers.

## Detailed Sources

- `ai/state/benchmark-state.md`
- `docs/benchmarking.md`
- `target/benchmarks/*.json`
- `target/*.jfr`
