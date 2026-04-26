# Current State

## Repo

- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmark modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-26`: Completed WP14. `SqlExpressionEvaluator` now performs front-door null/blank validation before Caffeine access, preserving the cached fast path for valid expressions while restoring deterministic validation errors.
- `2026-04-26`: Completed WP13. `FilterExecutionPlanCacheStore.resetStats()` now swaps to a fresh empty cache, and runtime/public regressions lock empty-after-reset plus next-query-miss semantics.
- `2026-04-26`: Completed WP12. `SqlLikeExecutionSupport` now treats preferred field indexes as per-row hints, and regression coverage locks aliased/computed `QueryRow` projection against mixed field order.
- `2026-04-25`: Java 25 upgrade complete; `maven.compiler.release=25`, CI matrix `[25]`, and workflow Java versions are aligned.
- `2026-04-25`: Full pojo-lens module scan (208 files) produced the WP6-WP11 performance/quality backlog.
- `2026-04-24`: README/docs continuity patch, core helper additions, and risk-console tab-navigation work all shipped.

## Verified

- `2026-04-26`: `mvn -B -ntp -pl pojo-lens "-Dtest=SqlExpressionEvaluatorTest" test` passed: 6 tests, 0 failures.
- `2026-04-26`: `mvn -B -ntp test` passed: 1066 tests, 0 failures across all modules.
- `2026-04-26`: `scripts/check-doc-consistency.ps1` passed.
- `2026-04-26`: `mvn -B -ntp -pl pojo-lens "-Dtest=CachePolicyConfigTest,CacheConcurrencyTest,PublicApiCacheCoverageTest" test` passed: 15 tests, 0 failures.
- `2026-04-26`: `mvn -B -ntp -pl pojo-lens "-Dtest=SqlLikeAliasTest,SqlLikeMappingParityTest,SqlLikeQueryContractTest,SqlLikeExecutionSupportTest" test` passed: 52 tests, 0 failures.
- `2026-04-24`: `mvn -B -ntp -f examples\spring-boot-starter-risk-console\pom.xml test` passed: 36 tests, 0 failures.

## Release

- Latest cut is `2026.04.17.1834`.
- Next release gate: decide benchmark backfill and WP11 timing, then run release notes and final guardrails.
- Decide whether to backfill the promised WP6/WP8/WP9 JMH + threshold work before the release cut.

## Risks

- The promised WP6/WP8/WP9 JMH + threshold follow-ups are still not implemented; release scope needs an explicit decision.
- Real MySQL verification is still pending for `examples/spring-boot-starter-risk-console`.

## Next

- Decide whether to backfill WP6/WP8/WP9 benchmark work before release.
- Decide whether WP11 lands before or after the release cut.
- Run release notes and final release guardrails.
- WP11 remains optional: Java 25 modernization (records, sealed AST, pattern matching, `Stream.toList()`).
