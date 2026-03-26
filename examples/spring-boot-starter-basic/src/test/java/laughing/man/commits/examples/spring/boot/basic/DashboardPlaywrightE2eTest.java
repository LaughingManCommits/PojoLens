package laughing.man.commits.examples.spring.boot.basic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = BasicExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DashboardPlaywrightE2eTest {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BrowserContext browserContext;
    private Page page;
    private APIRequestContext api;

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
        browserContext = browser.newContext();
        page = browserContext.newPage();
        api = playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(baseUrl()));
    }

    @AfterEach
    void tearDown() {
        if (api != null) {
            api.dispose();
        }
        if (browserContext != null) {
            browserContext.close();
        }
    }

    @Test
    @Order(1)
    void apiExposesRuntimeEmployeesDepartmentsAndTopPaid() throws Exception {
        JsonNode runtime = getJson("/api/employees/runtime");
        assertTrue(runtime.get("strictParameterTypes").isBoolean());
        assertTrue(runtime.get("lintMode").isBoolean());
        assertTrue(runtime.get("sqlLikeCacheEnabled").isBoolean());
        assertTrue(runtime.get("statsPlanCacheEnabled").isBoolean());

        JsonNode employees = getJson("/api/employees");
        assertTrue(employees.isArray());
        assertTrue(employees.size() >= 7);
        assertEquals(1L, employees.get(0).get("id").asLong());

        JsonNode departments = getJson("/api/employees/departments");
        assertTrue(departments.isArray());
        assertTrue(departments.size() >= 3);
        assertTrue(containsText(departments, "Engineering"));

        JsonNode topPaid = getJson("/api/employees/top-paid?department=Engineering&minSalary=100000&limit=2");
        assertTrue(topPaid.isArray());
        assertTrue(topPaid.size() <= 2);
        assertTrue(topPaid.size() >= 1);
    }

    @Test
    @Order(2)
    void dashboardApiSupportsAllConfiguredViewsAndChartTypes() throws Exception {
        JsonNode options = getJson("/api/employees/dashboard-options");
        JsonNode statsViews = options.get("statsViews");
        JsonNode chartTypes = options.get("chartTypes");
        assertEquals(4, statsViews.size());
        assertEquals(4, chartTypes.size());
        assertEquals("DEPARTMENT_PAYROLL", options.get("defaultStatsView").asText());
        assertEquals("BAR", options.get("defaultChartType").asText());
        assertEquals(4, options.get("statsViewDetails").size());
        assertEquals(4, options.get("chartTypeDetails").size());

        for (JsonNode statsView : statsViews) {
            for (JsonNode chartType : chartTypes) {
                String statsValue = statsView.asText();
                String chartValue = chartType.asText();
                JsonNode payload = getJson(
                        "/api/employees/dashboard?statsView=" + statsValue + "&chartType=" + chartValue
                );
                assertEquals(statsValue, payload.get("selectedStatsView").asText());
                assertEquals(chartValue, payload.get("selectedChartType").asText());
                assertTrue(payload.get("employees").isArray());
                assertTrue(payload.get("stats").get("columns").isArray());
                assertTrue(payload.get("stats").get("rows").isArray());
                assertTrue(payload.get("stats").get("source").asText().length() > 0);
                assertEquals(statsValue, payload.get("stats").get("view").asText());
                assertTrue(payload.get("payrollChart").get("data").get("labels").size() > 0);
                assertTrue(payload.get("headcountChart").get("data").get("labels").size() > 0);
                assertEquals(expectedChartJsType(chartValue), payload.get("payrollChart").get("type").asText());
                assertEquals(expectedChartJsType(chartValue), payload.get("headcountChart").get("type").asText());
            }
        }
    }

    @Test
    @Order(3)
    void apiAddEmployeeReflectsAcrossListAndTopPaid() throws Exception {
        String uniqueName = "API-E2E-" + System.currentTimeMillis();
        APIResponse create = api.post(
                "/api/employees",
                RequestOptions.create()
                        .setHeader("Content-Type", "application/json")
                        .setData(Map.of(
                                "name", uniqueName,
                                "department", "Engineering",
                                "salary", 260000
                        ))
        );
        assertEquals(201, create.status());
        JsonNode created = objectMapper.readTree(create.text());
        assertEquals(uniqueName, created.get("name").asText());

        JsonNode employees = getJson("/api/employees");
        assertTrue(containsEmployeeName(employees, uniqueName));

        JsonNode topPaid = getJson("/api/employees/top-paid?department=Engineering&minSalary=0&limit=1");
        assertEquals(uniqueName, topPaid.get(0).get("name").asText());
    }

    @Test
    @Order(4)
    void uiSupportsStatsFocusChartTypesTopPaidAndAddEmployeeFlow() {
        page.navigate(baseUrl() + "/");

        assertThat(page.locator("h1")).containsText("PojoLens Starter Dashboard");
        assertThat(page.locator("#statsView")).isVisible();
        assertThat(page.locator("#chartType")).isVisible();
        assertThat(page.locator("#employeeTable tr").first()).isVisible();
        assertNoClientErrors();
        assertChartsRendered("bar");

        page.selectOption("#statsView", "TEAM_SUMMARY");
        page.selectOption("#chartType", "PIE");
        page.click("#dashboardForm button[type='submit']");

        assertThat(page.locator("#statsTitle")).hasText("TEAM_SUMMARY");
        assertThat(page.locator("#statsSource")).containsText("Team Summary");
        assertThat(page.locator("#statsTableHead")).containsText("headcount");
        assertThat(page.locator("#statsTableHead")).containsText("payroll");
        assertThat(page.locator("#statsTableHead")).containsText("averageSalary");
        assertThat(page.locator("#statsViewHelp")).containsText("/docs/sql-like.md");
        assertThat(page.locator("#chartTypeHelp")).containsText("/docs/charts.md");
        assertThat(page.locator("#payrollChart")).isVisible();
        assertThat(page.locator("#headcountChart")).isVisible();
        assertNoClientErrors();
        assertChartsRendered("pie");

        page.selectOption("#statsView", "DEPARTMENT_HEADCOUNT");
        page.selectOption("#chartType", "AREA");
        page.click("#dashboardForm button[type='submit']");
        assertThat(page.locator("#statsTitle")).hasText("DEPARTMENT_HEADCOUNT");
        assertThat(page.locator("#statsSource")).containsText("Headcount by Department");
        assertThat(page.locator("#statsTableHead")).containsText("department");
        assertThat(page.locator("#statsTableHead")).containsText("headcount");
        assertThat(page.locator("#statsViewHelp")).containsText("/docs/stats-presets.md");
        assertThat(page.locator("#chartTypeHelp")).containsText("/docs/charts.md");
        assertThat(page.locator("#payrollChart")).isVisible();
        assertThat(page.locator("#headcountChart")).isVisible();
        assertNoClientErrors();
        assertChartsRendered("line");
        assertAreaChartsRendered();

        page.selectOption("#topPaidDepartment", "Engineering");
        page.fill("#topPaidMinSalary", "0");
        page.fill("#topPaidLimit", "2");
        page.click("#topPaidForm button[type='submit']");
        assertThat(page.locator("#topPaidTable tr").first()).isVisible();
        assertThat(page.locator("#addEmployeeForm")).isVisible();
        assertThat(page.locator("#employeeName")).isVisible();
        assertThat(page.locator("#employeeDepartment")).isVisible();
        assertThat(page.locator("#employeeSalary")).isVisible();

        int initialRows = page.locator("#employeeTable tr").count();
        String uniqueName = "UI-E2E-" + System.currentTimeMillis();
        page.fill("#employeeName", uniqueName);
        page.fill("#employeeDepartment", "Engineering");
        page.fill("#employeeSalary", "280000");

        Response createResponse = page.waitForResponse(
                response -> response.url().endsWith("/api/employees")
                        && "POST".equalsIgnoreCase(response.request().method()),
                () -> page.click("#addEmployeeForm button[type='submit']")
        );
        assertEquals(201, createResponse.status());
        assertTrue(waitForEmployeeByApi(uniqueName, 10_000));

        assertThat(page.locator("#employeeTable")).containsText(uniqueName);
        assertThat(page.locator("#employeeTable tr")).hasCount(initialRows + 1);

        page.selectOption("#topPaidDepartment", "Engineering");
        page.fill("#topPaidMinSalary", "0");
        page.fill("#topPaidLimit", "1");
        page.click("#topPaidForm button[type='submit']");
        assertThat(page.locator("#topPaidTable tr").first()).containsText(uniqueName);
    }

    @Test
    @Order(5)
    void uiCanApplyAllStatsViewsAndChartTypes() {
        page.navigate(baseUrl() + "/");

        String[] statsViews = {
                "DEPARTMENT_PAYROLL",
                "DEPARTMENT_HEADCOUNT",
                "TOP_3_PAYROLL_DEPARTMENTS",
                "TEAM_SUMMARY"
        };
        String[] chartTypes = {
                "BAR",
                "PIE",
                "LINE",
                "AREA"
        };

        for (String statsView : statsViews) {
            for (String chartType : chartTypes) {
                page.selectOption("#statsView", statsView);
                page.selectOption("#chartType", chartType);
                assertEquals(statsView, page.inputValue("#statsView"));
                assertEquals(chartType, page.inputValue("#chartType"));
                Response refreshResponse = page.waitForResponse(
                        response -> response.url().contains("/api/employees/dashboard?")
                                && "GET".equalsIgnoreCase(response.request().method()),
                        () -> page.click("#dashboardForm button[type='submit']")
                );
                assertEquals(200, refreshResponse.status());

                assertThat(page.locator("#statsTitle")).hasText(statsView);
                assertTrue(page.locator("#statsTableHead th").count() > 0);
                assertThat(page.locator("#payrollChart")).isVisible();
                assertThat(page.locator("#headcountChart")).isVisible();
                assertNoClientErrors();
                assertChartsRendered(expectedChartJsType(chartType));
                if ("AREA".equals(chartType)) {
                    assertAreaChartsRendered();
                }
            }
        }
    }

    @Test
    @Order(6)
    void uiShowsErrorForInvalidEmployeePayload() {
        page.navigate(baseUrl() + "/");
        String badName = "UI-BAD-" + System.currentTimeMillis();

        page.fill("#employeeName", badName);
        page.fill("#employeeDepartment", "Engineering");
        page.fill("#employeeSalary", "0");

        Response createResponse = page.waitForResponse(
                response -> response.url().endsWith("/api/employees")
                        && "POST".equalsIgnoreCase(response.request().method()),
                () -> page.evalOnSelector(
                        "#addEmployeeForm",
                        "form => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))"
                )
        );
        assertEquals(400, createResponse.status());
        assertThat(page.locator("#feedback")).containsText("Add employee failed");
        assertNoClientErrors();
        assertTrue(!waitForEmployeeByApi(badName, 2_000));
    }

    private JsonNode getJson(String path) throws Exception {
        APIResponse response = api.get(path);
        assertTrue(response.ok(), "GET failed for " + path + " with status " + response.status());
        return objectMapper.readTree(response.text());
    }

    private boolean containsText(JsonNode arrayNode, String value) {
        for (JsonNode node : arrayNode) {
            if (value.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsEmployeeName(JsonNode employees, String name) {
        for (JsonNode employee : employees) {
            if (name.equals(employee.get("name").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean waitForEmployeeByApi(String name, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                JsonNode employees = getJson("/api/employees");
                if (containsEmployeeName(employees, name)) {
                    return true;
                }
                Thread.sleep(250);
            } catch (Exception ignored) {
                // continue until timeout
            }
        }
        return false;
    }

    private void assertNoClientErrors() {
        assertEquals(
                "false",
                page.locator("#clientErrorPanel").getAttribute("data-has-error"),
                page.locator("#clientErrorText").textContent()
        );
    }

    private void assertChartsRendered(String expectedType) {
        assertTrue((Boolean) page.evaluate("() => !!window.charts.payroll && !!window.charts.headcount"));
        assertEquals(
                expectedType,
                page.evaluate("() => window.charts.payroll.config.type").toString()
        );
        assertEquals(
                expectedType,
                page.evaluate("() => window.charts.headcount.config.type").toString()
        );
    }

    private void assertAreaChartsRendered() {
        assertEquals(
                "true",
                page.evaluate("() => String(window.charts.payroll.data.datasets[0].fill)").toString()
        );
        assertEquals(
                "true",
                page.evaluate("() => String(window.charts.headcount.data.datasets[0].fill)").toString()
        );
    }

    private String expectedChartJsType(String chartType) {
        if ("AREA".equals(chartType) || "LINE".equals(chartType)) {
            return "line";
        }
        return chartType.toLowerCase();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }
}
