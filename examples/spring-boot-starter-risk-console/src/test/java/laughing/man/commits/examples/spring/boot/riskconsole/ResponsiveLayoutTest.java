package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class ResponsiveLayoutTest extends PlaywrightE2EBase {

    @Test
    void responsiveLayoutStaysUsable() {
        page.setViewportSize(1440, 900);
        page.navigate(baseUrl());
        assertThat(page.locator("[data-testid='sidebar']")).isVisible();
        assertThat(page.locator("[data-testid='summary-cards']")).isVisible();

        page.setViewportSize(768, 900);
        assertThat(page.locator("[data-testid='main-content']")).isVisible();
        activateTab("operationsTabButton");
        assertThat(page.locator("#transactions")).isVisible();

        page.setViewportSize(390, 844);
        assertThat(page.locator("[data-testid='main-content']")).isVisible();
        activateTab("reportsTabButton");
        page.locator("#reports").scrollIntoViewIfNeeded();
        assertThat(page.locator("[data-testid='reports-panel']")).isVisible();
        capture("responsive-mobile.png");
        assertNoConsoleErrors();
    }
}
