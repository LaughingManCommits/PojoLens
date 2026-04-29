package laughing.man.commits.examples.spring.boot.riskconsole;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardSmokeTest extends PlaywrightE2EBase {

    @Test
    void dashboardLoadsWithNavigationCardsAndMainTable() {
        page.navigate(baseUrl());

        assertThat(page).hasTitle(Pattern.compile(".*ClearCharge.*"));
        assertThat(page.getByRole(AriaRole.NAVIGATION)).isVisible();
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Payments Risk Operations"))).isVisible();
        assertThat(page.locator("[data-testid='value-story-panel']")).isVisible();
        assertThat(page.locator("#valueStoryTitle")).containsText("Why PojoLens matters here");
        assertThat(page.locator("#valueStoryFeatures")).containsText("Window ranking");
        assertThat(page.locator("[data-testid='summary-cards']")).isVisible();
        assertThat(page.getByRole(AriaRole.TABLIST, new Page.GetByRoleOptions().setName("Dashboard Sections"))).isVisible();
        assertThat(page.locator("#overviewTabButton")).containsText("Overview");
        assertThat(page.locator("#analyticsTabButton")).containsText("Analytics");
        assertThat(page.locator("#scopeSummary")).containsText("Flow:");
        assertThat(page.locator("#merchantDetailPanel")).isVisible();

        activateTab("operationsTabButton");
        assertThat(page.locator("#transactions table")).isVisible();
        assertThat(page.locator("#transactionsPagingCopy")).containsText("Showing");
        assertThat(page.locator("#previousPageButton")).isVisible();
        assertThat(page.locator("#nextPageButton")).isVisible();
        assertThat(page.locator("#reviewQueuePanel table")).isVisible();

        activateTab("reportsTabButton");
        assertTrue(page.url().contains("tab=reportsTab"), "Expected active tab in URL: " + page.url());
        assertThat(page.locator("[data-testid='reports-panel']")).isVisible();
        assertThat(page.locator("#reportOutputSubTabButton")).containsText("Run Output");

        page.reload();
        assertThat(page.locator("[data-testid='reports-panel']")).isVisible();

        activateTab("overviewTabButton");

        page.waitForTimeout(1200);
        int heightBefore = pageHeight();
        page.waitForTimeout(1200);
        int heightAfter = pageHeight();
        assertTrue(Math.abs(heightAfter - heightBefore) < 20,
                "Page height kept changing after load: " + heightBefore + " -> " + heightAfter);

        capture("dashboard-overview.png");
        assertNoConsoleErrors();
    }

    private int pageHeight() {
        Object value = page.evaluate("() => document.scrollingElement.scrollHeight");
        return ((Number) value).intValue();
    }
}
