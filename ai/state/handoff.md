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

**WP14 is now in progress.** WP5 remains closed at the accepted selective/single-join boundary, and the current backlog is profiler-driven follow-up on the computed-field join path.

Relevant reference:

- TODO.md

---

# Key Code Areas

Fluent execution pipeline:

- src/main/java/laughing/man/commits/builder/FilterQueryBuilder.java
- src/main/java/laughing/man/commits/computed/internal/ComputedFieldSupport.java
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
- src/main/java/laughing/man/commits/sqllike/internal/expression/SqlExpressionEvaluator.java
- src/main/java/laughing/man/commits/sqllike/parser/SqlLikeParser.java
- src/main/java/laughing/man/commits/sqllike/internal/validation/SqlLikeValidator.java
- src/test/java/laughing/man/commits/sqllike/internal/expression/SqlExpressionEvaluatorTest.java
- src/test/java/laughing/man/commits/ComputedFieldRegistryTest.java

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
- Does a comparable warmed JFR now confirm that the old `SqlExpressionEvaluator$Parser.*` hot spots disappeared after the new compiled-expression path?
- Can `ComputedFieldSupport.materializeRow` and `JoinEngine.mergeFields` stop cloning `QueryRow`/`QueryField` state per row?
- Can `FilterImpl.join()` avoid the post-join `collectQueryRowFieldTypes(rows)` rescan by deriving schema from parent/child metadata?
- After implementation changes, should `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization` stay diagnostic-only or get a stable threshold?
- After implementation changes, should the end-to-end computed-field join strict-suite budgets (`250 ms/op` at `1k`, `500 ms/op` at `10k`) tighten?
- Only revisit multi-join or explicit-rule-group selective materialization if someone intentionally opens a new package beyond closed WP5 scope.
