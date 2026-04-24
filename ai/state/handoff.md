# Handoff

## Resume

1. Load hot context files.
2. Check `git status --short`.
3. Keep using `SqlLikeResultSetAdapter` for flat JDBC rows; keep manual mapping only for record-shaped or non-flat payloads.
4. Keep the stable chart mix; do not reintroduce scatter until `ChartJsAdapter` supports it safely.
5. Run one real MySQL reviewer pass for `examples/spring-boot-starter-risk-console` when Docker or local MySQL is available.
6. Keep `RiskConsoleDashboardService` thin; keep feature logic in focused services.
7. Otherwise continue release preparation.

## Focus

- `2026-04-24`: risk-console dashboard now uses Overview, Analytics, Operations, PojoLens, and Reports tabs; browser tests switch tabs before hidden-panel interactions.
- `2026-04-24`: risk-console top-level tabs now persist in the URL hash, restore on reload, and support keyboard arrow/home/end navigation; PojoLens and Reports now use secondary sub-tabs.
- `2026-04-24`: risk-console trends now guard empty decline-only subsets and return explicit empty chart payloads instead of routing empty lists into ChartQueryPreset time buckets.
- `2026-04-24`: risk-console `RiskConsoleDashboardService` is now a thin facade over focused overview, transactions, workbench, reports, and shared-query-support services.

## Facts

- `2026-04-24`: tab activation hides non-active sections and resizes Chart.js instances after reveal so hidden-panel charts render correctly.
- `2026-04-24`: dashboard navigation state is hash-backed (`tab`, `pojoLens`, `reports`), so reload/back-forward preserve the active top-level tab and the active PojoLens or Reports sub-section.
- `2026-04-24`: approved-only or other no-decline dashboard scopes no longer throw `EQ-SQL-VAL-008`; `RiskConsoleOverviewService.trends(...)` now emits empty decline charts when the decline subset is empty.
- `2026-04-24`: risk-console feature methods moved into `RiskConsoleOverviewService`, `RiskConsoleTransactionsService`, `RiskConsoleWorkbenchService`, `RiskConsoleReportsService`, and `RiskConsoleQuerySupport`; controller and tests still use the same facade bean.
- `2026-04-24`: `/api/transactions` returns `loadedRows`, `totalRows`, and `pageSize`; the UI shows page/range status from app-owned cursor history over the PojoLens-sorted snapshot.

## Validate

- After code changes: `mvn -B -ntp test`.
- After docs/process changes: `scripts/check-doc-consistency.ps1`.
- After AI memory changes: `scripts/refresh-ai-memory.ps1`, then `-Check`.
- Last validation: `2026-04-24` core adapter checks passed (19 green), risk-console service/controller checks passed (21 green), and the full risk-console example passed `mvn -B -ntp -f examples\spring-boot-starter-risk-console\pom.xml test` after the hash-backed tab/sub-tab navigation update (36 green).

## Cold Pointers

- process: `AGENTS.md`, `ai/AGENTS.md`, `CHANGELOG.md`
- public API/docs: `README.md`, `docs/**`
- release/benchmarks: `RELEASE.md`, `.github/workflows/*`, `docs/benchmarking.md`
