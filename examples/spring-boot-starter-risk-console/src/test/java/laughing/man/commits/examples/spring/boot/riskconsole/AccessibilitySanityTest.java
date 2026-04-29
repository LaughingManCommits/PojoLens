package laughing.man.commits.examples.spring.boot.riskconsole;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessibilitySanityTest extends PlaywrightE2EBase {

    @Test
    void keyControlsHaveNamesAndKeyboardFocusWorks() {
        page.navigate(baseUrl());

        assertThat(page.getByRole(AriaRole.NAVIGATION)).isVisible();
        assertThat(page.getByLabel("Range")).isVisible();
        assertThat(page.getByLabel("Search")).isVisible();
        activateTab("reportsTabButton");
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Run Report"))).isVisible();
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Inspect Shape"))).isVisible();

        int headingCount = page.locator("h1, h2, h3").count();
        assertTrue(headingCount >= 8, "Expected meaningful heading structure");

        page.locator("#overviewTabButton").focus();
        page.keyboard().press("ArrowRight");
        assertTrue("analyticsTabButton".equals(page.evaluate("() => document.activeElement.id")),
                "Expected ArrowRight to move focus to analytics tab");
        page.keyboard().press("End");
        assertTrue("reportsTabButton".equals(page.evaluate("() => document.activeElement.id")),
                "Expected End to move focus to last tab");
        page.keyboard().press("Space");
        assertThat(page.locator("[data-testid='reports-panel']")).isVisible();

        activateTab("overviewTabButton");
        page.keyboard().press("Tab");
        boolean focusedRange = false;
        for (int index = 0; index < 16; index++) {
            page.keyboard().press("Tab");
            if ("rangeFilter".equals(page.evaluate("() => document.activeElement.id"))) {
                focusedRange = true;
                break;
            }
        }
        assertTrue(focusedRange, "Expected keyboard focus to reach range filter");
        capture("accessibility-focus.png");
        assertNoConsoleErrors();
    }
}
