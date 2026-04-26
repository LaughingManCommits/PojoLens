# Current State

## Repo

- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmark modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-26`: Reviewed WP6-WP10 implementation; TODO now tracks WP12-WP14 for mixed-schema QueryRow alias projection, stats-plan-cache reset semantics, and SqlExpressionEvaluator null/blank validation.
- `2026-04-25`: Java 25 upgrade complete; `maven.compiler.release=25`, CI matrix `[25]`, and workflow Java versions are aligned.
- `2026-04-25`: Full pojo-lens module scan (208 files) produced the WP6-WP11 performance/quality backlog.
- `2026-04-24`: README/docs continuity patch, core helper additions, and risk-console tab-navigation work all shipped.

## Verified

- `2026-04-26`: `mvn -B -ntp -pl pojo-lens test` passed: 1037 tests, 0 failures.
- `2026-04-24`: `mvn -B -ntp -f examples\spring-boot-starter-risk-console\pom.xml test` passed: 36 tests, 0 failures.

## Release

- Latest cut is `2026.04.17.1834`.
- Next release gate: fix WP12 + WP13 + WP14, then run release notes and final guardrails.
- Decide whether to backfill the promised WP6/WP8/WP9 JMH + threshold work before the release cut.

## Risks

- `SqlLikeExecutionSupport.projectAliasedRows(...)` is incorrect for heterogeneous `QueryRow` field order; mixed-schema alias projection can return wrong values (WP12).
- `FilterExecutionPlanCacheStore.resetStats()` still preserves entries; the next identical stats query hits instead of missing (WP13).
- `SqlExpressionEvaluator.compileNumeric(null)` now throws `NullPointerException` via Caffeine instead of the previous validation-oriented contract (WP14).
- Real MySQL verification is still pending for `examples/spring-boot-starter-risk-console`.

## Next

- Implement WP12, WP13, and WP14.
- Re-run `scripts/check-doc-consistency.ps1`, `mvn -B -ntp test`, and release guardrails after the fixes land.
- WP11 remains optional: Java 25 modernization (records, sealed AST, pattern matching, `Stream.toList()`).
