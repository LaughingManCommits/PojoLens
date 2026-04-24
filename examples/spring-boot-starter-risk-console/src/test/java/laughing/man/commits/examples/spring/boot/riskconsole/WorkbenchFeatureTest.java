package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchFeatureTest extends PlaywrightE2EBase {

    @Test
    void workbenchShowsStatsJoinAndRuntimeInspectorData() {
        page.navigate(baseUrl());
        activateTab("exploreTabButton");

        assertThat(page.locator("#workbenchSubTabButton")).containsText("Workbench");
        assertThat(page.locator("[data-testid='workbench-panel']")).isVisible();
        assertThat(page.locator("#riskTierStatsBody tr").first()).isVisible();
        assertThat(page.locator("#analystWorkloadBody tr").first()).isVisible();
        assertFalse((Boolean) page.locator("#workbenchAdvanced").evaluate("el => el.hasAttribute('open')"));
        page.locator("#workbenchAdvanced summary").click();
        assertTrue((Boolean) page.locator("#workbenchAdvanced").evaluate("el => el.hasAttribute('open')"));
        assertThat(page.locator("#workbenchComputedFields")).containsText("riskWeightedAmount");
        assertThat(page.locator("#workbenchExposurePolicy")).containsText("transactions");
        assertThat(page.locator("#workbenchExecutionGuard")).containsText("maxRowsScanned");
        assertThat(page.locator("#workbenchTelemetry")).containsText("stage");
        assertThat(page.locator("#workbenchJoinExplain")).containsText("joinSourceBindings");
        capture("pojolens-workbench.png");
        assertNoConsoleErrors();
    }
}
