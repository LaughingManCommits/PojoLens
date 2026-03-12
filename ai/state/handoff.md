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

The main unfinished engineering work still relates to **WP5 selective / lazy materialization**, but the remaining work is now dominated by repeated measurement and threshold decisions rather than core builder plumbing.

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
- Do the initial end-to-end computed-field join numbers (`~0.335 ms/op` / `~2,138,298 B/op` at `1k`, `~3.750 ms/op` / `~20,981,581 B/op` at `10k`) hold up across repeated runs, and how much gap to the manual baseline is acceptable as a regression budget?
- Are the new computed-field-plus-single-join selective paths worth tightening into benchmark thresholds and adding to the strict main suite after reruns, or should they remain guidance-only?
- Should the remaining WP5 fallbacks for explicit rule groups or multi-join shapes be left as the stable stopping point?
