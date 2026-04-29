package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantDetailTest extends PlaywrightE2EBase {

    @Test
    void merchantDetailPanelsUpdateWhenLeaderboardSelectionChanges() {
        page.navigate(baseUrl());

        assertThat(page.locator("#topMerchantsBody tr").first()).isVisible();
        String firstLabel = page.locator("#merchantDetailLabel").textContent();
        int leaderboardRows = page.locator("#topMerchantsBody tr").count();
        int targetRow = leaderboardRows > 1 ? 1 : 0;

        page.locator("#topMerchantsBody tr").nth(targetRow).evaluate("row => row.click()");
        page.waitForTimeout(500);

        String updatedLabel = page.locator("#merchantDetailLabel").textContent();
        assertNotEquals(firstLabel, updatedLabel);
        assertThat(page.locator("#merchantSummary .merchant-summary-card")).hasCount(4);
        assertThat(page.locator("#merchantVolumeChart")).isVisible();
        assertThat(page.locator("#merchantTransactionsBody tr").first()).isVisible();
        assertTrue(updatedLabel.contains("|"));
        capture("merchant-detail-panels.png");
        assertNoConsoleErrors();
    }
}
