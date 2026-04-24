package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewQueueTest extends PlaywrightE2EBase {

    @Test
    void reviewQueueHonorsRiskBandFilterAndShowsExpectedStatuses() {
        page.navigate(baseUrl());
        activateTab("operationsTabButton");

        assertThat(page.locator("#reviewQueueBody tr").first()).isVisible();
        List<String> initialStatuses = textValues("#reviewQueueBody tr td:nth-child(1)");
        assertTrue(initialStatuses.stream().allMatch(value -> value.equals("OPEN") || value.equals("ESCALATED")));

        page.selectOption("#riskBandFilter", "HIGH");
        page.waitForTimeout(700);

        int rows = page.locator("#reviewQueueBody tr").count();
        assertTrue(rows > 0, "Expected review queue rows for HIGH risk band");
        List<String> riskBands = textValues("#reviewQueueBody tr td:nth-child(8)");
        assertTrue(riskBands.stream().allMatch("HIGH"::equals), "Review queue risk band filter failed");
        capture("review-queue-high-risk.png");
        assertNoConsoleErrors();
    }

    @SuppressWarnings("unchecked")
    private List<String> textValues(String selector) {
        return (List<String>) page.evaluate("""
                selector => Array.from(document.querySelectorAll(selector))
                    .map(node => node.textContent.trim())
                """, selector);
    }
}
