# Current State

## Repo

- Java 25 multi-module library with core runtime, Spring Boot integration, and JMH benchmark modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-25`: Upgraded build to Java 25 (`maven.compiler.release=25`); CI matrix narrowed to `[25]`; all `java-version:"17"` in workflows updated to `"25"`; Java skill, agent-invariants, and repo-purpose updated.
- `2026-04-25`: Full pojo-lens module scan complete (208 files). Six new work packages written (WP6–WP11) covering expression cache contention, reflection cache bounds, field index pre-computation, allocation reduction, cache coherence, and Java 25 modernization.
- `2026-04-24`: README/docs continuity patch landed; core helpers shipped (scatter bridge, FacetPresets, PojoLensJdbc, ReportComparisons); risk-console dashboard refactored to tabbed nav + focused services.

## Verified

- `2026-04-24`: `mvn -B -ntp -pl pojo-lens test` passed: 1036 tests, 0 failures.
- `2026-04-24`: `mvn -B -ntp -f examples\spring-boot-starter-risk-console\pom.xml test` passed: 36 tests, 0 failures.

## Release

- Latest cut is `2026.04.17.1834`.
- Next release gate: WP6 + WP7 + WP8 complete (expression cache, reflection bounds, field index).

## Verified

- `2026-04-25`: `mvn -B -ntp -pl pojo-lens test` passed: 1036 tests, 0 failures (post WP6).

## Risks

- Java 25 build not yet validated locally (JDK 25 not confirmed on dev machine); CI will resolve via `actions/setup-java@v5`.
- 9 unbounded `ConcurrentHashMap` caches in `ReflectionUtil` are a memory risk in long-running servers.
- `FilterExecutionPlanCacheStore.rebuildCache()` has a transient empty-cache window; concurrent queries see plan-cache miss storms on config change.
- Real MySQL verification still pending for `examples/spring-boot-starter-risk-console`.

## Next

- WP7: Bound all 9 `ReflectionUtil` caches + per-query execution caches.
- WP8: Pre-compute field index maps at plan time; remove per-row O(n) lookups.
