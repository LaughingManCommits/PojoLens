# Spring Boot Starter Basic Example

This example shows how to wire `pojo-lens-spring-boot-starter` into a Spring Boot app and use the auto-configured `PojoLensRuntime`.

## Run

From repository root:

```bash
mvn -B -ntp -pl pojo-lens-spring-boot-starter -am install -DskipTests
mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml spring-boot:run
```

## Try it

Open the dashboard UI:

```bash
start http://localhost:8080/
```

Preset-aware dashboard payload (switch stats/chart modes):

```bash
curl "http://localhost:8080/api/employees/dashboard?statsMode=PRESET_TOP3_PAYROLL&chartMode=PRESET_REPORT"
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

## Playwright E2E tests (Java)

From repository root:

```bash
mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -Dtest=DashboardPlaywrightE2eTest test
```

Notes:
- Tests are Java/JUnit based (`com.microsoft.playwright:playwright`) under `src/test/java`.
- The suite starts the app with `@SpringBootTest(webEnvironment=RANDOM_PORT)` and drives UI/API against that test server.
- On first run Playwright downloads browser binaries automatically.

Inspect runtime starter settings:

```bash
curl "http://localhost:8080/api/employees/runtime"
```

## What this demonstrates

- `PojoLensRuntime` is provided by Boot auto-configuration from the starter.
- Runtime behavior is controlled with `pojo-lens.*` properties (`preset`, strict/lint modes, telemetry).
- SQL-like queries are executed directly against in-memory POJO lists with typed projection.
- The dashboard can switch between direct SQL-like mode and reusable preset modes:
  `StatsViewPresets` and `ChartQueryPresets` (including report-definition chart execution).
- A Bootstrap + Chart.js frontend (`/`) consumes chart-ready payloads from PojoLens-backed API endpoints.
