package laughing.man.commits.examples.spring.boot.riskconsole;

import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReportInspectPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReviewQueuePayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.QueryStudioPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.SummaryPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionsPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TrendsPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ValueStoryPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.WorkbenchPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = RiskConsoleApplication.class)
@ActiveProfiles("test")
class RiskConsoleDashboardServiceTest {

    @Autowired
    private RiskConsoleDashboardService dashboardService;

    @Test
    void summaryIncludesExpectedCards() {
        SummaryPayload payload = dashboardService.summary("30d", "ALL", "ALL", "ALL", "ALL", "");

        assertEquals(5, payload.cards().size());
        assertTrue(payload.recordCount() > 0);
        assertEquals("volume", payload.cards().get(0).id());
    }

    @Test
    void reviewQueueRespectsHighRiskFilter() {
        ReviewQueuePayload payload = dashboardService.reviewQueue("30d", "ALL", "HIGH", "ALL", "");

        assertFalse(payload.rows().isEmpty());
        assertTrue(payload.rows().stream().allMatch(row -> "HIGH".equals(row.get("riskBand"))));
        assertTrue(payload.rows().stream().allMatch(row -> {
            Object status = row.get("reviewStatus");
            return "OPEN".equals(status) || "ESCALATED".equals(status);
        }));
    }

    @Test
    void valueStoryMakesPojoLensReuseExplicit() {
        ValueStoryPayload payload = dashboardService.valueStory("30d", "ALL", "ALL", "ALL", "ALL", "");

        assertEquals("Why PojoLens matters here", payload.title());
        assertEquals(4, payload.metrics().size());
        assertTrue(payload.summary().contains("JDBC loads a bounded time window"));
        assertTrue(payload.evidence().stream().anyMatch(item -> item.contains("window query")));
        assertTrue(payload.featureStrip().contains("Saved reports"));
    }

    @Test
    void reportInspectExposesDiagnosticsAndPushdownPreview() {
        ReportInspectPayload payload = dashboardService.inspectReport(
                "regional-declines", "30d", "ALL", "ALL", "ALL", "ALL", "");

        assertEquals("regional-declines", payload.reportId());
        assertTrue(payload.queryText().contains("merchantRegion"));
        assertTrue(payload.schema().contains("merchantRegion"));
        assertTrue(((Map<?, ?>) payload.diagnostics()).containsKey("valid"));
        assertTrue(((Map<?, ?>) payload.pushdownPreview()).containsKey("mode"));
        assertFalse(payload.explain().isEmpty());
    }

    @Test
    void workbenchExposesStatsJoinComputedFieldsAndTelemetry() {
        WorkbenchPayload payload = dashboardService.workbench("30d", "ALL", "ALL", "ALL", "");

        assertFalse(payload.riskTierStats().rows().isEmpty());
        assertFalse(payload.analystWorkload().rows().isEmpty());
        assertTrue(payload.computedFields().contains("riskWeightedAmount"));
        assertTrue(payload.exposurePolicy().containsKey("allowedSources"));
        assertTrue(payload.executionGuard().containsKey("maxRowsScanned"));
        assertTrue(payload.joinExplain().containsKey("joinSourceBindings"));
        assertFalse(payload.telemetry().isEmpty());
    }

    @Test
    void workbenchReturnsEmptyTablesWhenFilteredSnapshotHasNoRows() {
        WorkbenchPayload payload = dashboardService.workbench("30d", "ALL", "ALL", "ALL", "no-hit-risk-console");

        assertTrue(payload.riskTierStats().rows().isEmpty());
        assertTrue(payload.analystWorkload().rows().isEmpty());
        assertTrue(payload.joinExplain().containsKey("joinSourceBindings"));
    }

    @Test
    void queryStudioExposesNaturalTypedAndCancellationFlows() {
        QueryStudioPayload payload = dashboardService.queryStudio("30d", "ALL", "ALL", "ALL", "ALL", "");

        assertTrue(payload.naturalQuery().queryText().contains("review state"));
        assertFalse(payload.naturalQuery().rows().isEmpty());
        assertTrue(payload.naturalQuery().explain().containsKey("equivalentSqlLike"));
        assertFalse(payload.typedQuery().rows().isEmpty());
        assertFalse(payload.typedQuery().explain().isEmpty());
        assertTrue(payload.cancellationDemo().cancelled());
        assertEquals("GUARD_CANCELLED", payload.cancellationDemo().blockCode());
        assertEquals(1, payload.cancellationDemo().rowsReturnedBeforeAbort());
        assertEquals(1, payload.cancellationDemo().rows().size());
    }

    @Test
    void naturalSavedReportInspectionWorks() {
        ReportInspectPayload payload = dashboardService.inspectReport(
                "open-reviews-by-region-natural", "30d", "ALL", "ALL", "ALL", "ALL", "");

        assertEquals("open-reviews-by-region-natural", payload.reportId());
        assertTrue(payload.queryText().contains("merchantRegion"));
        assertTrue(payload.diagnostics().toString().contains("valid"));
        assertTrue(payload.explain().containsKey("equivalentSqlLike"));
    }

    @Test
    void trendsHandlesEmptyDeclineSubset() {
        TrendsPayload payload = dashboardService.trends("30d", "ALL", "APPROVED", "ALL", "ALL", "");

        assertTrue(payload.volumeTrend().data().labels().size() >= 0);
        assertTrue(payload.declineTrend().data().labels().isEmpty());
        assertTrue(payload.declineReasonBreakdown().data().labels().isEmpty());
    }

    @Test
    void transactionsSortByAmountAscending() {
        TransactionsPayload payload = dashboardService.transactions(
                "30d", "ALL", "ALL", "ALL", "ALL", "", 25, "amount", "asc", null);

        assertFalse(payload.rows().isEmpty());
        assertTrue(payload.totalRows() >= payload.loadedRows());
        assertEquals(25, payload.pageSize());
        double first = ((Number) payload.rows().get(0).get("amount")).doubleValue();
        double second = ((Number) payload.rows().get(1).get("amount")).doubleValue();
        assertTrue(first <= second);
    }

    @Test
    void transactionsDefaultIdSortSupportsPaging() {
        TransactionsPayload firstPage = dashboardService.transactions(
                "30d", "ALL", "ALL", "ALL", "ALL", "", 25, "id", "desc", null);

        assertFalse(firstPage.rows().isEmpty());
        assertTrue(firstPage.hasMore());
        assertFalse(firstPage.nextCursor() == null || firstPage.nextCursor().isBlank());

        TransactionsPayload secondPage = dashboardService.transactions(
                "30d", "ALL", "ALL", "ALL", "ALL", "", 25, "id", "desc", firstPage.nextCursor());

        assertFalse(secondPage.rows().isEmpty(), "Expected next page to contain rows for default id sort");
        assertTrue(
                !String.valueOf(firstPage.rows().get(0).get("id")).equals(String.valueOf(secondPage.rows().get(0).get("id"))),
                "Expected second page to start after first page cursor");
    }

    @Test
    void transactionsCreatedAtSortSupportsPaging() {
        TransactionsPayload firstPage = dashboardService.transactions(
                "30d", "ALL", "ALL", "ALL", "ALL", "", 25, "createdAt", "desc", null);

        assertFalse(firstPage.rows().isEmpty());
        assertTrue(firstPage.hasMore());
        assertFalse(firstPage.nextCursor() == null || firstPage.nextCursor().isBlank());
        assertTrue(firstPage.totalRows() >= firstPage.loadedRows());

        TransactionsPayload secondPage = dashboardService.transactions(
                "30d", "ALL", "ALL", "ALL", "ALL", "", 25, "createdAt", "desc", firstPage.nextCursor());

        assertFalse(secondPage.rows().isEmpty());
        assertTrue(
                !String.valueOf(firstPage.rows().get(0).get("id")).equals(String.valueOf(secondPage.rows().get(0).get("id"))),
                "Expected second page to start after first page cursor");
    }

    @Test
    void transactionsRejectBadSortField() {
        assertThrows(ResponseStatusException.class, () ->
                dashboardService.transactions("30d", "ALL", "ALL", "ALL", "ALL", "",
                        25, "merchantRegion", "asc", null));
    }
}
