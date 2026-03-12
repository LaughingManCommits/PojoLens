# Handoff

Primary context files:

- ai/core/agent-invariants.md
- ai/core/repo-purpose.md
- ai/core/module-index.md
- ai/core/readme-alignment.md
- ai/core/runbook.md
- ai/state/current-state.md

Load order is defined in the root `AGENTS.md`.

---

# Current Focus

**WP5 selective / lazy materialization is complete** at the accepted selective/single-join boundary. The remaining backlog is post-WP5 benchmark hardening, mainly hotspot threshold follow-up and any future tightening of the computed-field join guardrail.

Relevant reference:

- TODO.md

---

# Key Code Areas

Fluent execution pipeline:

- src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java
- src/main/java/laughing/man/commits/filter/FilterImpl.java
- src/main/java/laughing/man/commits/filter/FilterExecutionPlan.java
- src/main/java/laughing/man/commits/util/ReflectionUtil.java
- src/test/java/laughing/man/commits/builder/FilterQueryBuilderSelectiveMaterializationTest.java
- src/main/java/laughing/man/commits/benchmark/HotspotMicroJmhBenchmark.java
- src/main/java/laughing/man/commits/benchmark/PojoLensJoinJmhBenchmark.java
- src/test/java/laughing/man/commits/benchmark/PojoLensJoinJmhBenchmarkParityTest.java
- scripts/benchmark-suite-hotspots.args
- scripts/benchmark-suite-main.args
- benchmarks/thresholds.json
- docs/benchmarking.md

SQL-like execution:

- src/main/java/laughing/man/commits/sqllike/SqlLikeQuery.java
- src/main/java/laughing/man/commits/sqllike/parser/SqlLikeParser.java
- src/main/java/laughing/man/commits/sqllike/internal/validation/SqlLikeValidator.java

Charts and reporting:

- src/main/java/laughing/man/commits/chart/*
- src/main/java/laughing/man/commits/report/ReportDefinition.java

Snapshot/testing utilities:

- src/main/java/laughing/man/commits/snapshot/*
- src/main/java/laughing/man/commits/testing/*

---

# Open Questions

- Should `CONTRIBUTING.md` and `RELEASE.md` benchmark examples be corrected to `1.0.0`?
- Should doc-consistency tooling detect version drift?
- Do the initial forked hotspot numbers for `computedFieldJoinSelectiveMaterialization` (`~37.9 us/op` / `~363,824 B/op` at `1k`, `~361.5 us/op` / `~3,531,826 B/op` at `10k`) hold up across repeated runs?
- Should WP14 keep `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` as a diagnostic-only benchmark, or tighten it into a stable budget after more reruns?
- The end-to-end computed-field join path now has conservative strict-suite budgets of `250 ms/op` at `1k` and `500 ms/op` at `10k`; should those be tightened once more cold-run data is collected?
- The repeated end-to-end profiled runs held at about `0.335 ms/op` / `2,138,3xx B/op` for `1k` and `3.589-3.750 ms/op` / `20,981,5xx B/op` for `10k`; how much gap to the manual baseline is acceptable as a longer-term regression budget?
- Only revisit multi-join or explicit-rule-group selective materialization if someone intentionally opens a new package beyond closed WP5 scope.
