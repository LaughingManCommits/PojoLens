package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryStudioTest extends PlaywrightE2EBase {

    @Test
    void queryStudioShowsNaturalTypedAndCancellationFlows() {
        page.navigate(baseUrl());
        activateTab("exploreTabButton");
        activateSubTab("queryStudioSubTabButton");

        assertThat(page.locator("[data-testid='query-studio-panel']")).isVisible();
        assertThat(page.locator("#queryStudioSubTabButton")).containsText("Query Studio");
        assertThat(page.locator("#naturalQueryBody tr").first()).isVisible();
        assertThat(page.locator("#typedQueryBody tr").first()).isVisible();
        assertThat(page.locator("#cancellationDemoSummary")).containsText("GUARD_CANCELLED");
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*pojoLens=queryStudioSubTab.*"));

        assertFalse((Boolean) page.locator("#naturalQueryAdvanced").evaluate("el => el.hasAttribute('open')"));
        page.locator("#naturalQueryAdvanced summary").click();
        assertTrue((Boolean) page.locator("#naturalQueryAdvanced").evaluate("el => el.hasAttribute('open')"));
        assertThat(page.locator("#naturalQueryText")).containsText("review state");
        assertThat(page.locator("#naturalQueryExplain")).containsText("equivalentSqlLike");

        page.locator("#typedQueryAdvanced summary").click();
        assertThat(page.locator("#typedQuerySchema")).containsText("riskScore");

        page.locator("#cancellationDemoAdvanced summary").click();
        assertThat(page.locator("#cancellationDemoGuard")).containsText("maxRowsReturned");

        capture("query-studio.png");
        assertNoConsoleErrors();
    }
}
