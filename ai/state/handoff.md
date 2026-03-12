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
- scripts/benchmark-suite-hotspots.args
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
- Are the new computed-field-plus-single-join selective paths worth tightening into benchmark thresholds after hotspot reruns, or should they remain guidance-only?
- Should the remaining WP5 fallbacks for explicit rule groups or multi-join shapes be left as the stable stopping point?
