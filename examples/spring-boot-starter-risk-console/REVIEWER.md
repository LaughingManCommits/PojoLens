# Risk Console Reviewer Packet

Use this file when reviewing showcase app.

## Run

Build and test:

```bash
mvn -B -ntp -f examples/spring-boot-starter-risk-console/pom.xml test
```

App run:

```bash
docker compose -f examples/spring-boot-starter-risk-console/docker-compose.yml up -d
mvn -B -ntp -f examples/spring-boot-starter-risk-console/pom.xml spring-boot:run
```

Primary runtime note:
- Local automated tests use H2 fallback profile.
- Main showcase runtime target is MySQL.

## Reviewer Rules

- Run app if possible.
- Run tests. Do not trust static read only.
- Check screenshots in `target/playwright-screenshots/`.
- Write final review with `examples/spring-boot-starter-risk-console/REVIEW-REPORT-TEMPLATE.md`.
- Latest filled review snapshot lives in `examples/spring-boot-starter-risk-console/REVIEW-REPORT.md`.
- Confirm backend data drives UI.
- Confirm PojoLens shapes loaded POJOs. No fake frontend numbers.
- Mark MySQL runtime as pending if Docker/local MySQL not available.

## Feature Matrix

| Feature | Endpoint | Main Test | Extra Test | Screenshot |
|---|---|---|---|---|
| Dashboard smoke | `/api/dashboard/summary`, `/api/dashboard/trends` | `DashboardSmokeTest` | `AccessibilitySanityTest` | `dashboard-overview.png` |
| Filtered KPI cards | `/api/dashboard/summary` | `DashboardOverviewTest` | `RiskConsoleDashboardServiceTest.summaryIncludesExpectedCards` | `merchant-drilldown.png` |
| Trend charts react to filters | `/api/dashboard/trends` | `AnalyticsTest` |  | `analytics-filtered.png` |
| Top merchants drilldown | `/api/dashboard/top-merchants`, `/api/merchants/{merchantId}/overview` | `DashboardOverviewTest` | `MerchantDetailTest` | `merchant-drilldown.png`, `merchant-detail-panels.png` |
| Review queue | `/api/reviews/queue` | `ReviewQueueTest` | `RiskConsoleControllerTest.reviewQueueEndpointRespectsRiskBandFilter` | `review-queue-high-risk.png` |
| Transaction drawer | `/api/transactions/{transactionId}` | `DrilldownTest` |  | `transaction-drawer.png` |
| Transaction filtering empty state | `/api/transactions` | `TableFilteringTest` |  | `transaction-empty-state.png` |
| Transaction sorting | `/api/transactions` | `TableSortingTest` | `RiskConsoleDashboardServiceTest.transactionsSortByAmountAscending` | `transaction-sorting.png` |
| Bad sort field rejection | `/api/transactions` | `RiskConsoleControllerTest.transactionsEndpointRejectsBadSortField` | `RiskConsoleDashboardServiceTest.transactionsRejectBadSortField` | none |
| Bad cursor rejection | `/api/transactions` | `RiskConsoleControllerTest.transactionsEndpointRejectsBadCursor` |  | none |
| Saved reports run | `/api/reports/{reportId}/run` | `ReportsInspectorTest` |  | `report-inspector.png` |
| Inspector metadata | `/api/reports/{reportId}/inspect` | `ReportsInspectorTest` | `RiskConsoleControllerTest.reportInspectEndpointReturnsReviewMetadata`, `RiskConsoleDashboardServiceTest.reportInspectExposesDiagnosticsAndPushdownPreview` | `report-inspector.png` |
| PojoLens workbench | `/api/dashboard/workbench` | `WorkbenchFeatureTest` | `RiskConsoleControllerTest.workbenchEndpointReturnsRichPojoLensMetadata`, `RiskConsoleDashboardServiceTest.workbenchExposesStatsJoinComputedFieldsAndTelemetry` | `pojolens-workbench.png` |
| Query Studio | `/api/dashboard/query-studio` | `QueryStudioTest` | `RiskConsoleControllerTest.queryStudioEndpointReturnsNaturalTypedAndCancellationPayloads`, `RiskConsoleDashboardServiceTest.queryStudioExposesNaturalTypedAndCancellationFlows` | `query-studio.png` |
| Responsive layout | all page sections | `ResponsiveLayoutTest` |  | `responsive-mobile.png` |
| Accessibility sanity | main page landmarks and focus order | `AccessibilitySanityTest` |  | `accessibility-focus.png` |

## Browser Test Inventory

- `AccessibilitySanityTest`
- `AnalyticsTest`
- `DashboardOverviewTest`
- `DashboardSmokeTest`
- `DrilldownTest`
- `MerchantDetailTest`
- `QueryStudioTest`
- `ReportsInspectorTest`
- `ResponsiveLayoutTest`
- `ReviewQueueTest`
- `TableFilteringTest`
- `TableSortingTest`

## Backend Test Inventory

- `RiskConsoleControllerTest`
- `RiskConsoleDashboardServiceTest`

## Screenshots To Check

- `accessibility-focus.png`
- `analytics-filtered.png`
- `merchant-drilldown.png`
- `dashboard-overview.png`
- `transaction-drawer.png`
- `merchant-detail-panels.png`
- `query-studio.png`
- `report-inspector.png`
- `responsive-mobile.png`
- `review-queue-high-risk.png`
- `transaction-empty-state.png`
- `transaction-sorting.png`
- `pojolens-workbench.png`

## What Must Be True

- Summary cards change when filters change.
- Charts change when filters change.
- Drilldown is real. Not fake panel text.
- Review queue shows `OPEN` and `ESCALATED`.
- Empty search shows empty state. No crash.
- Invalid sort field returns `400`.
- Invalid cursor returns `400`.
- Report inspector shows query text, params, schema, diagnostics, pushdown preview, explain.
- Screenshots look like internal tool, not classroom CRUD.

## Known Deferred

- Real MySQL runtime verification can be run later on machine with Docker or local MySQL.
- Current automated suite proves H2-backed browser and backend behavior now.
