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
- `2026-04-25`: `mvn -B -ntp -pl pojo-lens test` passed: 1036 tests, 0 failures (post WP7).

## WP7 complete (2026-04-25)

- Replaced all 9 static `ConcurrentHashMap` caches in `ReflectionUtil` with bounded Caffeine caches (1 000 / 2 000 entry limits).
- Replaced `preparedExecutions` in `SqlLikeQuery` and `resolvedExecutions` in `NaturalQuery` with Caffeine caches (256 entries, 30-min expiry).
- Updated `SqlLikePreparedExecutionSupport` parameter type from `ConcurrentMap` to `Cache`.
- Fixed `ReflectionUtilTest` cache-introspection helpers to use `cache.asMap()`.

## Risks

- Java 25 build not yet validated locally (JDK 25 not confirmed on dev machine); CI will resolve via `actions/setup-java@v5`.
- `FilterExecutionPlanCacheStore.rebuildCache()` has a transient empty-cache window; concurrent queries see plan-cache miss storms on config change (WP10).
- Real MySQL verification still pending for `examples/spring-boot-starter-risk-console`.

## WP8 complete (2026-04-25)

- Pre-indexed `QueryRow` projection in `SqlLikeExecutionSupport.projectAliasedRows`: builds `Map<String,Integer>` from first row's schema, resolves source field indexes once per projection call, uses `row.getValueAt(idx)` per-row instead of O(n) scan.
- Computed field identifier resolution also uses pre-built index map via `resolveIndexedQueryRowFieldValue`.
- POJO source rows path unchanged (already uses `ReflectionUtil.getFieldValue` with caching).
- Confirmed: JoinEngine/AggregationEngine/GroupEngine/window already used pre-computed indexes; only `projectAliasedRows` had the per-row O(n) scan.
- 1036/1036 tests green.

## WP9 complete (2026-04-25)

- `AggregationEngine.aggregateMetrics` (ungrouped): replaced `QueryField`/`QueryRow` construction with `Object[]` + `RawQueryRow` — saves `metricCount` QueryField allocations + ArrayList + QueryRow per call.
- `AggregationEngine.aggregateGroupedMetrics`: changed `GroupAccumulator.groupProjection: List<QueryField>` to `Object[] groupValues`; output loop now builds `Object[]` + shared `outputSchema` + `RawQueryRow` per group — saves `columnCount + metricCount + 2` allocations per unique group.
- Confirmed: `FastPojoFilterSupport.toQueryRow` clone is already only called on matched rows (not per-row); `ObjectUtil.castValue` boxing is not in the filter hot path; `GroupEngine.toExternalKey()` is called once per unique group at output time only.
- 1036/1036 tests green.

## Next

- Release gate: WP6+WP7+WP8+WP9 complete — draft release notes; run final guardrails.
- WP10 (optional): FilterExecutionPlanCacheStore.rebuildCache() atomic swap.
- WP11 (optional): Java 25 modernization.
