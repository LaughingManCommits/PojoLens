package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportsInspectorTest extends PlaywrightE2EBase {

    @Test
    void savedReportRunAndInspectorWork() {
        page.navigate(baseUrl());
        activateTab("reportsTabButton");

        page.selectOption("#reportSelect", "regional-declines");
        jsClick("#runReportButton");
        page.waitForTimeout(400);
        assertThat(page.locator("#reportRowsBody tr").first()).isVisible();
        assertThat(page.locator("#reportRunLabel")).containsText("Regional Declines");

        activateSubTab("reportInspectorSubTabButton");
        jsClick("#inspectReportButton");
        page.waitForTimeout(400);
        assertThat(page.locator("#reportInspectorSubTabButton")).containsText("Inspector");
        assertFalse((Boolean) page.locator("#reportAdvanced").evaluate("el => el.hasAttribute('open')"));
        page.locator("#reportAdvanced summary").click();
        assertTrue((Boolean) page.locator("#reportAdvanced").evaluate("el => el.hasAttribute('open')"));
        assertThat(page.locator("#reportQueryText")).containsText("merchantRegion");
        assertThat(page.locator("#reportDefaultParams")).containsText("DECLINED");
        assertThat(page.locator("#reportSchema")).containsText("merchantRegion");
        assertThat(page.locator("#reportPlanPreview")).containsText("groupByFields");
        assertThat(page.locator("#reportPlanPreview")).containsText("requiredParams");
        assertThat(page.locator("#reportDiagnostics")).containsText("\"valid\": true");
        assertThat(page.locator("#reportPushdownPreview")).containsText("mode");
        assertThat(page.locator("#reportExplain")).containsText("stage");
        capture("report-inspector.png");
        assertNoConsoleErrors();
    }
}
