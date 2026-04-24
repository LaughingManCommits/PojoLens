# Spring Boot Starter Risk Console Example

Real dashboard example. Not toy CRUD.

Stack:
- Spring Boot
- MySQL
- PojoLens
- HTML/CSS/vanilla JavaScript
- Java Playwright

Current slice:
- real MySQL schema
- deterministic synthetic seed data
- PojoLens-backed summary, trends, top-merchants, and transactions endpoints
- PojoLens workbench for stats presets, computed fields, dataset-bundle joins, exposure policy, execution guard, telemetry, and runtime inspector metadata
- PojoLens query studio for guided natural text, typed DSL, and cooperative cancellation demo on the same filtered snapshot
- responsive dashboard shell
- saved reports and query inspector
- Java Playwright smoke, overview, drilldown, merchant detail, review queue, filtering, sorting, responsive, analytics, and accessibility sanity checks

Run:

```bash
docker compose -f examples/spring-boot-starter-risk-console/docker-compose.yml up -d
mvn -B -ntp -pl pojo-lens-spring-boot-starter -am install -DskipTests
mvn -B -ntp -f examples/spring-boot-starter-risk-console/pom.xml spring-boot:run
```

Test:

```bash
mvn -B -ntp -f examples/spring-boot-starter-risk-console/pom.xml test
```

Open:

```bash
start http://localhost:8080/
```

Notes:
- MySQL is the source of truth.
- `JdbcTemplate` loads rows.
- `SqlLikeResultSetAdapter` now bridges flat JDBC `ResultSet` rows into mutable POJOs for the main dashboard snapshot and related flat repository queries.
- Manual JDBC mapping stays only where the example needs non-flat or record-shaped detail payloads.
- PojoLens shapes loaded POJOs in memory.
- Browser screenshots land in `target/playwright-screenshots/`.
- H2 test profile is fallback for automated test runs.
- Reviewer handoff lives in `examples/spring-boot-starter-risk-console/REVIEWER.md`.
- Reviewer report template lives in `examples/spring-boot-starter-risk-console/REVIEW-REPORT-TEMPLATE.md`.
- Latest completed review lives in `examples/spring-boot-starter-risk-console/REVIEW-REPORT.md`.

Reviewer workflow:
- Run `mvn -B -ntp -f examples/spring-boot-starter-risk-console/pom.xml test`.
- Open `examples/spring-boot-starter-risk-console/REVIEWER.md`.
- Check screenshots in `examples/spring-boot-starter-risk-console/target/playwright-screenshots/`.
- Write final result with `examples/spring-boot-starter-risk-console/REVIEW-REPORT-TEMPLATE.md`.
- Mark MySQL runtime deferred if Docker or local MySQL is not available.

Main endpoints:
- `GET /api/bootstrap`
- `GET /api/dashboard/summary`
- `GET /api/dashboard/trends`
- `GET /api/dashboard/top-merchants`
- `GET /api/dashboard/workbench`
- `GET /api/dashboard/query-studio`
- `GET /api/transactions`
- `GET /api/transactions/{transactionId}`
- `GET /api/reviews/queue`
- `GET /api/merchants/{merchantId}/overview`
- `GET /api/reports`
- `GET /api/reports/{reportId}/run`
- `GET /api/reports/{reportId}/inspect`

Current screenshot inventory:
- `accessibility-focus.png`
- `analytics-filtered.png`
- `merchant-drilldown.png`
- `dashboard-overview.png`
- `transaction-drawer.png`
- `merchant-detail-panels.png`
- `query-studio.png`
- `report-inspector.png`
- `responsive-mobile.png`
- `review-queue-high-risk.png`
- `transaction-empty-state.png`
- `transaction-sorting.png`
- `pojolens-workbench.png`
