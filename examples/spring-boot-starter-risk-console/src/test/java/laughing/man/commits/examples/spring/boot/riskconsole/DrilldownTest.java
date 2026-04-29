package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class DrilldownTest extends PlaywrightE2EBase {

    @Test
    void transactionDrawerShowsTimeline() {
        page.navigate(baseUrl());
        activateTab("operationsTabButton");

        String transactionId = page.locator("#transactionsBody tr").first().locator("td").first().textContent();
        page.locator("#transactionsBody tr").first().evaluate("row => row.click()");
        assertThat(page.locator("[data-testid='transaction-drawer']")).isVisible();
        assertThat(page.locator("#transactionDrawerLabel")).containsText(transactionId);
        assertThat(page.locator("#transactionTimelineBody tr").first()).isVisible();
        capture("transaction-drawer.png");
        assertNoConsoleErrors();
    }
}
