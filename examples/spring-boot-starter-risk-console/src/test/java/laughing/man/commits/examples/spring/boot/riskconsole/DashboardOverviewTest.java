package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DashboardOverviewTest extends PlaywrightE2EBase {

    @Test
    void filtersAndMerchantDrilldownWork() {
        page.navigate(baseUrl());

        assertThat(page.locator("#valueStoryEvidence")).containsText("window query");
        String before = page.locator("#summaryCards .summary-card .value").first().textContent();
        page.selectOption("#statusFilter", "DECLINED");
        page.waitForTimeout(600);
        String after = page.locator("#summaryCards .summary-card .value").first().textContent();
        assertNotEquals(before, after);

        String merchantName = page.locator("#topMerchantsBody tr").first().locator("td").nth(1).textContent();
        page.locator("#topMerchantsBody tr").first().evaluate("row => row.click()");
        assertThat(page.locator("#merchantDetailLabel")).containsText(merchantName);
        assertThat(page.locator("#merchantTransactionsBody tr").first()).isVisible();
        capture("merchant-drilldown.png");
        assertNoConsoleErrors();
    }
}
