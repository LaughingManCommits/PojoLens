# Benchmark State

Load only when the task is about benchmarks, thresholds, parity, profiling, or performance regressions.

- Full benchmark-surface snapshot date: `2026-03-14`.
- Latest targeted follow-up date: `2026-03-15`.
- Guardrail status from the recorded runs: core `42/42` pass, chart `45/45` pass.
- The `2026-03-15` chart follow-up kept `45/45` passing after the exporter rewrite.
- `ChartPayloadJsonExporter` is no longer a primary cost center: comparable cold reruns measured about `0.066`, `0.634`, and `1.146 ms/op` for `scatterPayloadJsonExport` at sizes `1000`, `10000`, and `100000`, and a targeted `size=10000` `-prof gc` rerun measured about `0.367 ms/op` and `580,857 B/op`.
- For the targeted bean-backed stats workload, old chart mapping/export overhead no longer looks like the main issue; the remaining WP18 gap is more likely SQL-like setup/query execution.
- Persistent non-WP18 stress still exists in cold filter-vs-baseline gaps, cache plan-build throughput, and recurring reflection/conversion hotspots centered in `ReflectionUtil` and `FastArrayQuerySupport`.
- Use `BENCHMARKS.md` for detailed measurements and `ai/core/benchmark-context.md` for suite and profiler interpretation rules.
