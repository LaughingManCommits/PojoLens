# Core Library Improvement Candidates

## Candidate: Spring/JDBC bridge module

Problem: dashboard app loads MySQL rows first. Flat rows now use `SqlLikeResultSetAdapter`, but app code still owns `JdbcTemplate` query execution, repository boundaries, and manual mapping for non-flat payloads.

Current workaround: app uses `JdbcTemplate` plus `SqlLikeResultSetAdapter.read(...)` for flat mutable POJOs, and keeps explicit manual mapping for record-shaped detail payloads.

Why this may belong in core: many real apps start from SQL-backed data and want a small bridge.

Why this may not belong in core: risk of turning PojoLens into ORM or query framework. Keep adapter small and optional.

Possible API:

```java
// sketch only
PojoLensJdbc.query(jdbcTemplate, sql, params, TransactionRecord.class);
PojoLensJdbc.read(resultSet, TransactionRecord.class);
```

Priority: Must consider

Evidence: `examples/spring-boot-starter-risk-console/src/main/java/laughing/man/commits/examples/spring/boot/riskconsole/RiskConsoleJdbcRepository.java`

## Candidate: Period comparison helper

Problem: KPI cards need current-window vs previous-window deltas.

Current workaround: app runs current and previous snapshot queries and computes deltas in service code.

Why this may belong in core: dashboard KPI comparison is common.

Why this may not belong in core: comparison rules can be domain-specific.

Possible API:

```java
// sketch only
ComparisonResult result = ReportComparisons.compare(currentRows, previousRows, metricSpec);
```

Priority: Should consider

Evidence: `examples/spring-boot-starter-risk-console/src/main/java/laughing/man/commits/examples/spring/boot/riskconsole/RiskConsoleDashboardService.java`

## Candidate: Facet option helper

Problem: dashboard filter menus need stable distinct values and counts.

Current workaround: app reads distinct values directly from SQL tables.

Why this may belong in core: filter bars are common in admin dashboards.

Why this may not belong in core: many apps will want DB-backed facets, not only in-memory facets.

Possible API:

```java
// sketch only
FacetOptions options = FacetPresets.distinctCounts("merchantRegion").options(rows);
```

Priority: Should consider

Evidence: `/api/bootstrap` flow in risk console example

## Candidate: Chart.js scatter bridge

Problem: showcase app needs a real scatter chart for outlier analysis, but the current `ChartJsAdapter` payload shape is not safe enough for a live Chart.js dashboard path.

Current workaround: risk console showcase uses stable `LINE`, `AREA`, `BAR`, `PIE`, and stacked `BAR` charts instead of exposing PojoLens `SCATTER` through the current Chart.js bridge.

Why this may belong in core: `SCATTER` is already part of the public chart contract and docs. A first-party Chart.js bridge should handle public chart types safely.

Why this may not belong in core: rendering-library adapters should stay small. If richer point-shape support grows fast, it may need its own adapter module boundary.

Possible API:

```java
// sketch only
ChartJsPayload payload = ChartJsAdapter.toPayload(chartData);
// for SCATTER, dataset data should emit [{x:..., y:...}] points
```

Priority: Must consider

Evidence: `examples/spring-boot-starter-risk-console/src/main/java/laughing/man/commits/examples/spring/boot/riskconsole/RiskConsoleDashboardService.java`
