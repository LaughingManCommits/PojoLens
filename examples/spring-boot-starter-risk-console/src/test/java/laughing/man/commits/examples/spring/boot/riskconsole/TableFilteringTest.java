package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class TableFilteringTest extends PlaywrightE2EBase {

    @Test
    void transactionFilteringAndEmptyStateWork() {
        page.navigate(baseUrl());
        activateTab("operationsTabButton");

        assertThat(page.locator("#transactionsBody tr").first()).isVisible();
        page.locator("#searchFilter").fill("no-hit-risk-console");
        page.waitForTimeout(700);
        assertThat(page.locator("#transactionsEmpty")).isVisible();

        page.locator("#searchFilter").fill("");
        page.waitForTimeout(700);
        assertThat(page.locator("#transactionsBody tr").first()).isVisible();
        capture("transaction-empty-state.png");
        assertNoConsoleErrors();
    }
}
