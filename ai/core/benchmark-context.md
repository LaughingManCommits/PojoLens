# Benchmark Context

Load only when the task touches benchmark suites, threshold budgets, parity checks, profiling, or performance regression analysis. Do not load this file for normal coding, bug fixing, or prose-only work.

## Benchmark Surface

- Core guardrail suite: `scripts/benchmark-suite-main.args` backed by `benchmarks/thresholds.json`.
- Chart guardrail suite: `scripts/benchmark-suite-chart.args` backed by `benchmarks/chart-thresholds.json` plus `ChartParityChecker`.
- Baseline suite: `scripts/benchmark-suite-baseline.args` for fluent vs plain Streams comparisons where semantics align.
- Cache suite: `scripts/benchmark-suite-cache.args` for concurrent SQL-like parse cache and stats-plan cache stress.
- Hotspot suite: `scripts/benchmark-suite-hotspots.args` for reflection/materialization/cache microbenchmarks; use `-prof gc` when allocation matters.
- Standalone legacy benchmark: `PojoLensJmhBenchmark.*` is still useful as a cold sanity check but is not part of the current published suite manifests.

## Interpretation Rules

- Treat `benchmarks/*.json` as guardrail budgets, not as target performance ceilings.
- Separate cold guardrail numbers (`-wi 0 -i 1`) from warmed tuning numbers. They answer different questions and should not be compared directly.
- Treat chart parity failures separately from chart threshold failures. A suite can stay within budget while still violating fluent vs SQL-like ratio expectations.
- Treat cache throughput (`ops/s`) separately from average-time workloads (`ms/op` or `us/op`).
- Use hotspot `gc.alloc.rate.norm` when deciding where allocation work belongs; use core/chart suites when deciding whether guardrails or public budgets are at risk.

## Current Snapshot Pointers

- Current simplified snapshot: `ai/state/benchmark-state.md`.
- Current detailed report: `BENCHMARKS.md`.
- Current raw artifacts: `target/benchmarks/core.json`, `target/benchmarks/chart.json`, `target/benchmarks/baseline.json`, `target/benchmarks/cache.json`, `target/benchmarks/hotspots-gc.json`, and `target/benchmarks/pojolens-legacy.json`.

## Recurring Warm JFR Hotspot Clusters

Use this section only when the task is profiler-led and needs class-level hotspot prioritization. Skip it for ordinary benchmark threshold or reporting work.

- Compared warmed artifacts: `target/pojolens-fastpath-current.jfr`, `target/wp17-after-readpath.jfr`, `target/wp17-after-parent-buffer.jfr`, and `target/wp17-after-bound-expression.jfr`.
- Recurring CPU cluster 1: `ReflectionUtil` read-side access. `readResolvedFieldValue` / `ResolvedFieldPath.read` stayed dominant at about `589`, `875`, `925`, and `832` first-repo-frame samples across the warmed profiles.
- Recurring CPU cluster 2: `FastArrayQuerySupport` join/computed work. `ComputedFieldPlan.resolveValue` rose from about `200` to `253` to `331`, then dropped out after direct array-index binding; the latest profile now shows computed work through `applyComputedValues` (`313`) while `tryBuildJoinedState` and `buildChildIndex` still stay present.
- Recurring CPU cluster 3: projection writes remained visible through `ReflectionUtil.applyProjectionWritePlan` and `ReflectionUtil.setResolvedFieldValue` after earlier bottlenecks were reduced.
- Recurring allocation cluster 1: `FastArrayQuerySupport.buildChildIndex` stayed largest and rose from about `1271` to `3676` to `5353` to `6824` first-repo-frame allocation samples.
- Recurring allocation cluster 2: `ReflectionUtil` extraction stayed heavy through `readFlatRowValues`, `readResolvedFieldValue`, and `ResolvedFieldPath.read`, even after dependency lookup was bound away.
- Recurring allocation cluster 3: `FastArrayQuerySupport.materializeJoinedRow` stayed large in all warmed profiles and rose to about `4731`; `castNumericValue` remains visible even after the computed lookup change.
- Interpretation: the dominant warmed stress has converged into `ReflectionUtil` plus `FastArrayQuerySupport`, and the newest profiler evidence points more toward reflection reads plus join/index materialization allocation than toward any remaining string-based computed dependency lookup.
