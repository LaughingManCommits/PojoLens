package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsTest extends PlaywrightE2EBase {

    @Test
    void analyticsPanelsReactToFilterChanges() {
        page.navigate(baseUrl());

        String before = page.locator("#summaryCards .summary-card .value").first().textContent();
        int beforeLabels = chartLabelCount("volumeChart");

        page.selectOption("#rangeFilter", "7d");
        page.selectOption("#riskBandFilter", "HIGH");
        page.waitForTimeout(800);
        activateTab("analyticsTabButton");

        String after = page.locator("#summaryCards .summary-card .value").first().textContent();
        int afterLabels = chartLabelCount("volumeChart");
        assertNotEquals(before, after);
        assertTrue(afterLabels >= 0);
        assertThat(page.locator("#volumeChart")).isVisible();
        assertThat(page.locator("#declineChart")).isVisible();
        assertThat(page.locator("#riskBreakdownChart")).isVisible();
        assertThat(page.locator("#regionalStatusChart")).isVisible();
        assertThat(page.locator("#paymentMethodShareChart")).isVisible();
        assertThat(page.locator("#declineReasonChart")).isVisible();
        assertTrue("pie".equals(chartType("paymentMethodShareChart")));
        assertTrue("bar".equals(chartType("declineReasonChart")));
        assertTrue(beforeLabels >= 0);
        capture("analytics-filtered.png");
        assertNoConsoleErrors();
    }

    private int chartLabelCount(String chartId) {
        Object value = page.evaluate("""
                chartId => {
                  const chart = Chart.getChart(document.getElementById(chartId));
                  return chart ? chart.data.labels.length : -1;
                }
                """, chartId);
        return ((Number) value).intValue();
    }

    private String chartType(String chartId) {
        Object value = page.evaluate("""
                chartId => {
                  const chart = Chart.getChart(document.getElementById(chartId));
                  return chart ? chart.config.type : "";
                }
                """, chartId);
        return String.valueOf(value);
    }
}
