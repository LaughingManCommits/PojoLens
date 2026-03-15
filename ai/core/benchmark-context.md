# Benchmark Context

Load only when the task touches benchmark suites, threshold budgets, profiling, or performance regression analysis.

## Benchmark Surface

- Core guardrail suite: `scripts/benchmark-suite-main.args` with `benchmarks/thresholds.json`
- Chart guardrail suite: `scripts/benchmark-suite-chart.args` with `benchmarks/chart-thresholds.json`
- Baseline suite: `scripts/benchmark-suite-baseline.args`
- Cache suite: `scripts/benchmark-suite-cache.args`
- Hotspot suite: `scripts/benchmark-suite-hotspots.args`
- Standalone legacy cold sanity checks: `PojoLensJmhBenchmark.*`

## Interpretation Rules

- Treat `benchmarks/*.json` as guardrail budgets, not target ceilings.
- Keep cold guardrail runs and warmed tuning runs separate; they answer different questions.
- Do not use fluent-vs-SQL-like performance ratios as merge gates.
- Use hotspot `gc.alloc.rate.norm` for allocation work and core/chart suites for public-budget risk.
- Rebuild the `benchmark-runner` jar before quoting fresh benchmark numbers.

## Current Focus Cues

- Active performance work is split between absolute SQL-like/chart overhead in WP18 and broader reflection/conversion work in WP19.
- Recent warmed profiler work has repeatedly concentrated in `ReflectionUtil` and `FastArrayQuerySupport`.
- For the current SQL-like stats workload, chart mapping/export is no longer the default suspect; profile setup/query execution before reopening broad chart-path work.

## Detailed Sources

- `ai/state/benchmark-state.md`
- `BENCHMARKS.md`
- `target/benchmarks/*.json`
- warmed JFR artifacts under `target/*.jfr`
