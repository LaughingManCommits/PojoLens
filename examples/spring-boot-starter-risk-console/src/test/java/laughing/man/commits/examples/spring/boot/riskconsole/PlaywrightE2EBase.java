package laughing.man.commits.examples.spring.boot.riskconsole;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = RiskConsoleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
abstract class PlaywrightE2EBase {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    protected BrowserContext context;
    protected Page page;
    protected List<String> consoleErrors;

    @BeforeAll
    static void startPlaywright() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void stopPlaywright() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void setUp() {
        consoleErrors = new ArrayList<>();
        context = browser.newContext();
        page = context.newPage();
        page.onConsoleMessage(message -> {
            if ("error".equals(message.type())) {
                consoleErrors.add(message.text());
            }
        });
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected void assertNoConsoleErrors() {
        assertTrue(consoleErrors.isEmpty(), "Unexpected browser console errors: " + consoleErrors);
    }

    protected void jsClick(String locator) {
        page.locator(locator).evaluate("node => node.click()");
    }

    protected void activateTab(String tabButtonId) {
        page.locator("#" + tabButtonId).click();
        page.waitForTimeout(250);
    }

    protected void activateSubTab(String subTabButtonId) {
        page.locator("#" + subTabButtonId).click();
        page.waitForTimeout(250);
    }

    protected double moneyCellValue(int rowIndex) {
        String value = page.locator("#transactionsBody tr").nth(rowIndex).locator("td").nth(7).textContent();
        return Double.parseDouble(value.replace("$", "").replace(",", "").trim());
    }

    protected LocalDateTime createdAtValue(int rowIndex) {
        String value = page.locator("#transactionsBody tr").nth(rowIndex).locator("td").nth(1).locator("time").getAttribute("datetime");
        return LocalDateTime.parse(value);
    }

    protected void capture(String fileName) {
        try {
            Path dir = Path.of("target", "playwright-screenshots");
            Files.createDirectories(dir);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(dir.resolve(fileName))
                    .setFullPage(true));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to capture screenshot " + fileName, ex);
        }
    }
}
