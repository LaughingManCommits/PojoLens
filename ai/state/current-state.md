# Current State

## Repo

- Java 17 multi-module library build with runtime, Spring Boot, and JMH modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-24`: chart/scatter fix — `ChartDataset` now carries `xValues` for scatter; `ChartMapper` emits numeric x/y pairs into `xValues`; `ChartJsAdapter` emits `[{x,y}]` points and numeric x-axis for SCATTER type; `ChartJsDataset.data` widened to `Object`.
- `2026-04-24`: `FacetPresets.distinctCounts(fieldName)` and `FacetQuery<T>` added to `laughing.man.commits.facet` — in-memory distinct-value counts sorted by count desc.
- `2026-04-24`: `PojoLensJdbc` added to `pojo-lens-spring-boot-autoconfigure` — thin `JdbcTemplate` + `SqlLikeResultSetAdapter` bridge; `RiskConsoleJdbcRepository.queryRows()` updated to use it.
- `2026-04-24`: `PeriodComparison` and `ReportComparisons` added to `laughing.man.commits.report` — numeric and row-based period delta helpers with `percentageDelta()`, `ratePointDelta()`, `absoluteDelta()`.
- `2026-04-24`: risk-console dashboard is now split into Overview, Analytics, Operations, PojoLens, and Reports tabs; charts resize on tab activation and browser tests navigate tabs explicitly.
- `2026-04-24`: risk-console top-level tabs now persist in the URL hash, restore on reload, and support keyboard arrow/home/end navigation; PojoLens and Reports now have secondary sub-tabs so those sections are not one long vertical block.
- `2026-04-24`: risk-console trends now guard empty decline-only subsets and return explicit empty chart payloads instead of letting ChartQueryPreset lose source type on empty lists.
- `2026-04-24`: risk-console `RiskConsoleDashboardService` is now a thin facade over focused overview, transactions, workbench, reports, and shared-query-support services.
- `2026-04-24`: `SqlLikeResultSetAdapter` now accepts normalized JDBC labels and coerces common JDBC temporal values into Java time fields
- `2026-04-24`: risk-console now uses that adapter for flat JDBC snapshot and related repository reads.
- `2026-04-24`: risk-console transactions now have visible Previous/Next paging controls with stronger styling, plus stable app-owned cursor paging across `id`, `createdAt`, `amount`, and `riskScore`.
- `2026-04-24`: risk-console keeps visible value-story and collapsed advanced inspector sections.
- `2026-04-24`: risk-console now has a Query Studio panel and `/api/dashboard/query-studio` endpoint showing `runtime.natural()`, `ReportDefinition.natural(...)`, `TypedQuery.from(...)`, and cooperative cancellation on the same filtered snapshot.
- `2026-04-23`: `STRAT-WP2` to `STRAT-WP5` are complete; `STRAT-WP1` saved reports is already shipped.

## Verified

- `2026-04-24`: `mvn -B -ntp -pl pojo-lens test` passed: 1036 tests, 0 failures, 0 errors — after scatter, facet, and comparison additions.
- `2026-04-24`: `mvn -B -ntp -f examples\spring-boot-starter-risk-console\pom.xml "-Dtest=!ReviewerDocsConsistencyTest" test` passed: 35 tests (ReviewerDocsConsistencyTest excluded — pre-existing REVIEWER.md deletion).
- `2026-04-24`: `mvn -B -ntp -pl pojo-lens "-Dtest=SqlLikePushdownAdapterTest,StablePublicApiContractTest" test` passed: 19 tests, 0 failures, 0 errors.
- `2026-04-24`: `mvn -B -ntp -f examples\spring-boot-starter-risk-console\pom.xml "-Dtest=RiskConsoleDashboardServiceTest,RiskConsoleControllerTest" test` passed after adding Query Studio and natural-report paths: 21 tests, 0 failures, 0 errors.
- `2026-04-24`: `mvn -B -ntp -f examples\spring-boot-starter-risk-console\pom.xml test` passed after adding URL-backed tab restore, keyboard tab navigation, and PojoLens/Reports secondary sub-tabs: 36 tests, 0 failures, 0 errors.
- `2026-04-24`: `scripts/check-doc-consistency.ps1` passed.

## Release

- Latest cut is `2026.04.17.1834`.

## Risks

- Pushdown stays host-owned; PojoLens still does not own SQL rendering, DB execution, or authorization.
- Risk-console MySQL runtime verification is still deferred; automated reviewer coverage is H2-backed until a Docker/local MySQL pass is run.
- `ChartJsAdapter` is still not showcase-safe for PojoLens `SCATTER`.

## Next

- Run one real MySQL reviewer pass for `spring-boot-starter-risk-console` when Docker or local MySQL is available.
- Resume release preparation.
- Update CHANGELOG.md for the 4 core-library improvements from `docs/core-library-improvement-candidates.md`.
