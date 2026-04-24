# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. `PojoLensJdbc.query()` now replaces the manual `ResultSetExtractor` in `RiskConsoleJdbcRepository`; use `PojoLensJdbc` for any new JDBC-backed read paths.
4. Scatter charts now safe: `ChartJsAdapter` emits `{x,y}` points; use `ChartType.SCATTER` with numeric xField normally.
5. Run one real MySQL reviewer pass for `examples/spring-boot-starter-risk-console` when Docker or local MySQL is available.
6. Keep `RiskConsoleDashboardService` thin; keep feature logic in focused services.
7. Update CHANGELOG.md for the 4 core-library improvements, then continue release preparation.

## Focus

- `2026-04-24`: 4 core-library improvements delivered — scatter bridge, facet option helper, JDBC bridge, period comparison.
- `2026-04-24`: risk-console dashboard now uses Overview, Analytics, Operations, PojoLens, and Reports tabs; browser tests switch tabs before hidden-panel interactions.
- `2026-04-24`: risk-console top-level tabs now persist in the URL hash, restore on reload, and support keyboard arrow/home/end navigation; PojoLens and Reports now use secondary sub-tabs.
- `2026-04-24`: risk-console trends now guard empty decline-only subsets and return explicit empty chart payloads instead of routing empty lists into ChartQueryPreset time buckets.
- `2026-04-24`: risk-console `RiskConsoleDashboardService` is now a thin facade over focused overview, transactions, workbench, reports, and shared-query-support services.

## Facts

- `2026-04-24`: scatter xValues stored in `ChartDataset.xValues`; `ChartMapper` scatter path reads xField as numeric; `ChartJsAdapter` zips into `[{x,y}]`; `ChartJsDataset.data` is now `Object`.
- `2026-04-24`: `FacetPresets.distinctCounts("field").options(rows)` returns `List<FacetOption>` sorted by count desc; package `laughing.man.commits.facet`.
- `2026-04-24`: `PojoLensJdbc` in `pojo-lens-spring-boot-autoconfigure` wraps JdbcTemplate + SqlLikeResultSetAdapter; optional spring-jdbc dep added to autoconfigure pom.
- `2026-04-24`: `ReportComparisons.compare(currentRows, previousRows, field, metric)` and `PeriodComparison.percentageDelta()` / `ratePointDelta()` in `laughing.man.commits.report`.
- `2026-04-24`: tab activation hides non-active sections and resizes Chart.js instances after reveal so hidden-panel charts render correctly.
- `2026-04-24`: dashboard navigation state is hash-backed (`tab`, `pojoLens`, `reports`), so reload/back-forward preserve the active top-level tab and the active PojoLens or Reports sub-section.
- `2026-04-24`: approved-only or other no-decline dashboard scopes no longer throw `EQ-SQL-VAL-008`; `RiskConsoleOverviewService.trends(...)` now emits empty decline charts when the decline subset is empty.
- `2026-04-24`: risk-console feature methods moved into `RiskConsoleOverviewService`, `RiskConsoleTransactionsService`, `RiskConsoleWorkbenchService`, `RiskConsoleReportsService`, and `RiskConsoleQuerySupport`; controller and tests still use the same facade bean.
- `2026-04-24`: `/api/transactions` returns `loadedRows`, `totalRows`, and `pageSize`; the UI shows page/range status from app-owned cursor history over the PojoLens-sorted snapshot.

## Validate

- After code changes: `mvn -B -ntp test`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-24` full core suite: 1036 green; risk-console (excluding pre-existing ReviewerDocsConsistencyTest): 35 green; doc check: OK.

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
