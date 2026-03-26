# Spring Boot Starter Basic Example

This example shows how to wire `pojo-lens-spring-boot-starter` into a Spring Boot app and use the auto-configured `PojoLensRuntime`.

The example is intentionally split so each file demonstrates one concern:
- `EmployeeQueryController` stays as the HTTP adapter.
- `EmployeeDashboardService` owns the PojoLens runtime, reusable stats presets, reusable chart presets, and the report-definition bridge.
- `EmployeeStore` keeps the demo self-contained with an in-memory dataset.
- `static/app.js` and `static/app.css` keep the frontend readable instead of hiding the behavior inside `index.html`.

## Read Next In This Repo

The code comments point back to these docs:
- `/docs/entry-points.md`
- `/docs/stats-presets.md`
- `/docs/charts.md`
- `/docs/reports.md`
- `examples/spring-boot-starter-basic/README.md`

## Run

From repository root:

```bash
mvn -B -ntp -pl pojo-lens-spring-boot-starter -am install -DskipTests
mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml spring-boot:run
```

## Try It

Open the dashboard UI:

```bash
start http://localhost:8080/
```

Dashboard payload with user-facing stats focus and chart type selectors:

```bash
curl "http://localhost:8080/api/employees/dashboard?statsView=TOP_3_PAYROLL_DEPARTMENTS&chartType=PIE"
```

Top paid employees in a department:

```bash
curl "http://localhost:8080/api/employees/top-paid?department=Engineering&minSalary=100000&limit=2"
```

Add an employee:

```bash
curl -X POST "http://localhost:8080/api/employees" ^
  -H "Content-Type: application/json" ^
  -d "{\"name\":\"Sam\",\"department\":\"Engineering\",\"salary\":132000}"
```

Inspect runtime starter settings:

```bash
curl "http://localhost:8080/api/employees/runtime"
```

## What This Demonstrates

- `PojoLensRuntime` is injected by Spring Boot auto-configuration from the starter.
- Runtime behavior is controlled with `pojo-lens.*` properties.
- Direct SQL-like endpoints and reusable preset-backed endpoints can coexist in one service.
- The primary dashboard controls are user-facing: stats focus and chart type, not internal implementation modes.
- `StatsViewPresets` provide table-first reusable query shapes with `schema()` and `totals()`.
- `ChartQueryPresets` provide chart-first reusable query shapes and can now emit `ChartJsPayload` directly.
- `preset.reportDefinition().chartJs(...)` shows how to bridge a chart preset into the general report wrapper without custom mapping code.
- The frontend consumes Chart.js-ready payloads produced from PojoLens chart models.
- The selected chart type (`BAR`, `PIE`, `LINE`, `AREA`) is applied to the same PojoLens chart definitions so readers can see how presentation changes without rewriting backend queries.
- The selected stats view changes the table focus between department payroll, department headcount, top payroll departments, and a compact team summary.
- The example serves Bootstrap and Chart.js from local app dependencies (`/webjars/...`) instead of runtime CDNs, so the dashboard stays self-contained.
- The page surfaces frontend/runtime render errors in a visible error panel, and the Playwright suite asserts that the panel stays empty during happy-path flows.

## Why The New API Surface Matters

Adding a new chart can now be this small:

```java
ChartJsPayload payrollChart = ChartQueryPresets
    .categoryTotals("department", Metric.SUM, "salary", "payroll")
    .chartJs(employees);
```

And a new dashboard table can be this small:

```java
StatsTablePayload payrollTable = StatsViewPresets
    .by("department", Metric.SUM, "salary", "payroll")
    .tablePayload(employees);
```

The example app uses those projection-free shortcuts where they keep the code easier to read.

## Playwright E2E Tests (Java)

From repository root:

```bash
mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -Dtest=DashboardPlaywrightE2eTest test
```

Notes:
- Tests are Java/JUnit based (`com.microsoft.playwright:playwright`) under `src/test/java`.
- The suite starts the app with `@SpringBootTest(webEnvironment = RANDOM_PORT)` and drives both UI and API coverage against that server.
- On first run Playwright downloads browser binaries automatically.
