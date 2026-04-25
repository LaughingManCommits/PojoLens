# Current State

## Repo

- Java 17 multi-module library with core runtime, Spring Boot integration, and JMH benchmark modules.
- Current release is `2026.04.17.1834`.

## Focus

- `2026-04-24`: README/docs continuity patch landed - public navigation now surfaces `docs/facets.md` and `docs/jdbc.md`, the facet reflection note is corrected, and example inventories include `spring-boot-starter-risk-console`.
- `2026-04-24`: core helper work shipped - scatter `xValues` plus Chart.js scatter bridge, `FacetPresets.distinctCounts(...)`, `PojoLensJdbc`, and `ReportComparisons` / `PeriodComparison`.
- `2026-04-24`: risk-console dashboard now uses top-level tabs plus PojoLens/Reports sub-tabs with URL-hash restore and keyboard navigation.
- `2026-04-24`: risk-console trends now return explicit empty decline-chart payloads for no-decline scopes.
- `2026-04-24`: risk-console dashboard logic now lives in focused overview, transactions, workbench, reports, and shared-query-support services behind the existing facade.

## Verified

- `2026-04-24`: `mvn -B -ntp -pl pojo-lens test` passed: 1036 tests, 0 failures.
- `2026-04-24`: `mvn -B -ntp -f examples\spring-boot-starter-risk-console\pom.xml test` passed: 36 tests, 0 failures.
- `2026-04-24`: `scripts/check-doc-consistency.ps1` passed after the docs continuity patch.
- `2026-04-24`: `scripts/refresh-ai-memory.ps1` and `scripts/refresh-ai-memory.ps1 -Check` passed after compacting hot state and refreshing the docs memory index.

## Release

- Latest cut is `2026.04.17.1834`.

## Risks

- Pushdown remains host-owned; PojoLens still does not own SQL rendering, DB execution, or authorization.
- Real MySQL verification is still pending for `examples/spring-boot-starter-risk-console`; reviewer automation remains H2-backed.
- `ChartJsAdapter` scatter support is fixed at the library level but still not used in the risk-console showcase.

## Next

- Run one real MySQL reviewer pass for `examples/spring-boot-starter-risk-console` when Docker or local MySQL is available.
- Resume release preparation.
