# Path Selection Guide

If you are new to PojoLens, start here.
Use this page to choose one path first, then jump to the deeper guide for that
path.
For new code, keep one default path per job:
`PojoLensCore`, `PojoLensNatural`, `PojoLensSql`, `PojoLensRuntime`, `PojoLensChart`, or
`ReportDefinition<T>`.

Source guides:
- entry points: [docs/entry-points.md](entry-points.md)
- reusable wrappers: [docs/reusable-wrappers.md](reusable-wrappers.md)
- optional advanced surface: [docs/advanced-features.md](advanced-features.md)

## 1. Pick Query Style

| If you need... | Choose... | Why |
| --- | --- | --- |
| Service-owned query logic in code | `PojoLensCore.newQueryBuilder(...)` | Default fluent path for application-owned query composition. |
| Guided text queries for non-SQL users | `PojoLensNatural.parse(...).params(...)` | Default controlled plain-English path for deterministic text-driven queries without SQL syntax, including grouped aggregate phrases. |
| Config-driven or dynamic query strings | `PojoLensSql.parse(...).params(...)` | Default SQL-like path for text-driven query authoring. |
| Runtime-scoped policy, DI, or multi-tenant behavior | `PojoLensRuntime.ofPreset(...)` | Keeps lint, strict typing, telemetry, caches, computed fields, and natural-query vocabulary scoped to a runtime instance. |
| Rows already exist and only chart mapping remains | `PojoLensChart.toChartData(...)` | Uses the chart helper directly without re-entering query authoring. |

## 2. Pick Reusable Wrapper

| If you need... | Choose... | Why |
| --- | --- | --- |
| A reusable business query contract | `ReportDefinition<T>` | Default reusable wrapper for row-first queries that may feed more than one consumer. |
| A reusable chart-first preset | `ChartQueryPreset<T>` | Specialized SQL-like convenience wrapper for chart-shaped workflows. |
| A reusable table payload with totals and schema | `StatsViewPreset<T>` / `StatsTable<T>` | Specialized table-first wrapper for grouped stats and leaderboard flows. |

## 3. Pick Output Contract

| If you need... | Choose... | Result |
| --- | --- | --- |
| Typed rows only | `.filter(...)` or `report.rows(...)` | `List<T>` |
| Chart-ready payload | `.chart(...)`, `report.chart(...)`, or `preset.chart(...)` | `ChartData` |
| Table payload with rows, totals, and schema | `StatsViewPreset.table(...)` | `StatsTable<T>` |

## 4. Pick Runtime Model

| If you need... | Choose... | Why |
| --- | --- | --- |
| One shared app-level default policy | explicit entry points (`PojoLensCore` / `PojoLensNatural` / `PojoLensSql`) | Keeps the main query path simple. |
| Environment-, tenant-, or test-scoped policy | `PojoLensRuntime` | Instance-scoped configuration and execution. |
| Optional diagnostics, cache tuning, telemetry, regression tooling, or build-time helpers | [docs/advanced-features.md](advanced-features.md) | Follow-on public surface after the main path is chosen. |

## 5. Scenario Index

| If you need... | Go to | Default path |
| --- | --- | --- |
| A service-owned search endpoint | Scenario 1 | `PojoLensCore.newQueryBuilder(...)` |
| Config-driven dynamic queries | Scenario 2 | `PojoLensSql.parse(...).params(SqlParams)` |
| Deterministic API pagination | Scenario 2B | `LIMIT/OFFSET` + `keysetAfter(...)` |
| Large data, first-page consumers | Scenario 2C | `.stream(...)` / `.iterator(...)` |
| Repeated hot equality filters | Scenario 2D | `.addIndex(...)` + normal rules |
| Time-based finance/product summaries | Scenario 3 | `bucket(...) + group by + having` |
| Multi-source views with joins | Scenario 4 | `JoinBindings`, promoted to `DatasetBundle` for repeated execution |
| Chart payloads for frontend/reporting | Scenario 5 | `.chart(...)` + `ChartData` |
| Dashboard-ready stats tables | Scenario 5B | `StatsViewPresets` + `StatsTable` |
| Safe refactors + regression protection | Scenario 6 | `QueryRegressionFixture` |
| Production slowdown triage | Scenario 7 | `.explain(...)` + telemetry |

## 6. Scenario Catalog

### Scenario 1: People-Ops Search API

Problem:
- HR needs "active senior engineers ordered by salary" from in-memory rows.

Use:

```java
List<EmployeeDirectoryRow> rows = PojoLensCore.newQueryBuilder(employees)
    .addRule("active", true, Clauses.EQUAL)
    .addRule("department", "Engineering", Clauses.EQUAL)
    .addRule("level", 5, Clauses.BIGGER_EQUAL)
    .addOrder("salary", 1)
    .limit(25)
    .initFilter()
    .filter(Sort.DESC, EmployeeDirectoryRow.class);
```

Outcome:
- Stable top-N API payload with type-safe query construction.

### Scenario 2: Admin-Configurable Queries

Problem:
- Ops wants to tune filters without a deploy.

Use:

```java
List<EmployeeCompRow> rows = PojoLensSql
    .parse("select name, department, salary "
        + "where department = :dept and salary >= :minSalary "
        + "order by salary desc limit 50")
    .params(SqlParams.builder()
        .put("dept", "Engineering")
        .put("minSalary", 120000)
        .build())
    .filter(employees, EmployeeCompRow.class);
```

Outcome:
- Query logic in config, runtime values in typed named params.

### Scenario 2B: Cursor-Friendly Pagination API

Problem:
- A feed endpoint needs stable paging under concurrent inserts.

Use offset for shallow pages:

```java
List<EmployeeFeedRow> rows = PojoLensSql
    .parse("where active = true order by salary desc, id desc limit 20 offset 40")
    .filter(employees, EmployeeFeedRow.class);
```

Use keyset for deep pages:

```java
SqlLikeCursor cursor = SqlLikeCursor.builder()
    .put("salary", 120000)
    .put("id", 1)
    .build();

List<EmployeeFeedRow> rows = PojoLensSql
    .parse("where active = true order by salary desc, id desc limit 20")
    .keysetAfter(cursor)
    .filter(employees, EmployeeFeedRow.class);
```

Outcome:
- Predictable page windows and stable next-page behavior with deterministic sort keys.

### Scenario 2C: Memory-Efficient First-Page Reads

Problem:
- A pipeline only needs the first page/window and should avoid full list materialization.

Use:

```java
List<EmployeeCompRow> firstPage = PojoLensSql
    .parse("select name, department, salary where salary >= 100000")
    .stream(employees, EmployeeCompRow.class)
    .limit(50)
    .toList();
```

Outcome:
- Low-allocation first-page extraction via lazy streaming/iteration.

### Scenario 2D: Repeated Filter Endpoint on the Same Snapshot

Problem:
- A service executes the same equality-heavy filters repeatedly over one in-memory snapshot.

Use:

```java
List<EmployeeDirectoryRow> rows = PojoLensCore.newQueryBuilder(employees)
    .addIndex("department")
    .addIndex("active")
    .addRule("department", "Engineering", Clauses.EQUAL)
    .addRule("active", true, Clauses.EQUAL)
    .initFilter()
    .filter(EmployeeDirectoryRow.class);
```

Outcome:
- Optional index hints narrow candidate rows for compatible equality filters, with automatic fallback to scan when inapplicable.

### Scenario 3: Monthly Payroll Trend

Problem:
- Finance needs timezone-aware monthly totals with noise filtered out.

Use:

```java
List<MonthlyPayroll> rows = PojoLensSql
    .parse("select bucket(hireDate,'month','Europe/Amsterdam') as period, "
        + "sum(salary) as payroll "
        + "group by period having payroll > 250000 order by period asc")
    .filter(employees, MonthlyPayroll.class);
```

Outcome:
- Ready-to-plot time series using explicit calendar semantics.

### Scenario 4: Companies + Employees in One View

Problem:
- Analytics endpoint combines company rows with employee rows repeatedly.

Use:

```java
DatasetBundle bundle = DatasetBundle.of(
    companies,
    JoinBindings.of("employees", employees));

List<CompanyHiringRow> rows = PojoLensSql
    .parse("select companyName, title, salary "
        + "from companies left join employees on id = companyId "
        + "where active = true order by salary desc")
    .filter(bundle, CompanyHiringRow.class);
```

Outcome:
- Reusable join-source wiring and cleaner call sites.

### Scenario 5: Charts With Common Chart Libraries

Problem:
- Backend computes business aggregates.
- UI/reporting needs chart-library-specific payloads.

### Step 1: Produce ChartData once

```java
ChartData chartData = PojoLensSql
    .parse("select department, count(*) as headcount group by department order by headcount desc")
    .chart(employees, DepartmentHeadcount.class, ChartSpec.of(ChartType.BAR, "department", "headcount"));
```

### Option A: XChart (server-side images/PDFs)

```java
CategoryChart chart = new CategoryChartBuilder()
    .title("Headcount by Department")
    .xAxisTitle("Department")
    .yAxisTitle("Headcount")
    .width(900)
    .height(600)
    .build();

for (ChartDataset dataset : chartData.getDatasets()) {
    chart.addSeries(dataset.getLabel(), chartData.getLabels(), dataset.getValues());
}
```

### Option B: Chart.js (frontend apps)

```java
ChartJsPayload payload = ChartJsAdapter.toPayload(chartData);
```

Outcome:
- One PojoLens query can feed multiple chart libraries cleanly.

Five-line chart addition with preset + Chart.js adapter:

```java
ChartJsPayload payload = ChartQueryPresets
    .categoryTotals("department", Metric.SUM, "salary", "payroll")
    .chartJs(employees);
```

Without PojoLens, the same endpoint usually means:
- manual grouping/aggregation
- manual sorting
- manual row-to-chart mapping
- manual Chart.js payload assembly

### Scenario 5B: Dashboard Stats Tables and Leaderboards

Problem:
- Teams need table payloads with rows, totals, and schema metadata without repeating aggregate query strings.

Use grouped stats preset:

```java
StatsTablePayload table = StatsViewPresets
    .by("department", Metric.SUM, "salary", "payroll")
    .tablePayload(employees);
```

Use leaderboard preset:

```java
StatsTablePayload top3 = StatsViewPresets
    .topNBy("department", Metric.SUM, "salary", "payroll", 3)
    .tablePayload(employees);
```

Outcome:
- Deterministic table rows, optional totals, and reusable schema metadata for dashboard rendering.

### Scenario 6: Refactor Without Behavior Drift

Problem:
- You need to change query logic but keep API-visible results stable.

Use:

```java
QueryRegressionFixture<EmployeeApiRow> fixture = QueryRegressionFixture
    .builder("employee-api", EmployeeApiRow.class)
    .fluent(builder -> builder
        .addRule("active", true, Clauses.EQUAL)
        .addOrder("salary", 1))
    .build();
```

Outcome:
- Fixture/snapshot tests catch accidental output changes before release.

### Scenario 7: Production Triage

Problem:
- Endpoint got slower after query changes.

Use:

```java
Map<String, Object> explain = PojoLensSql
    .parse("where active = true order by salary desc limit 10")
    .explain(employees, Employee.class);
```

Outcome:
- Stage-level row counts help isolate where latency increased.

## 7. Default Calls

- Use `PojoLensCore` for service-owned fluent queries.
- Use `PojoLensSql` for config/admin-driven query strings and templates.
- Use `new PojoLensRuntime()` or `PojoLensRuntime.ofPreset(...)` when lint, cache, strict typing, telemetry, computed fields, or natural-query vocabulary should be instance-scoped.
- Use `PojoLensChart` when rows already exist and only chart mapping remains.
- Use `SqlLikeCursor`, `ReportDefinition`, `DatasetBundle`, and `SnapshotComparison` directly for those helper workflows.
- Use `JoinBindings` for one-off multi-source execution.
- Use `DatasetBundle` when the same multi-source snapshot will be executed repeatedly.
- Use `ChartData` as the boundary model between query and rendering.

## 8. Next Reads

- [docs/entry-points.md](entry-points.md)
- [docs/reusable-wrappers.md](reusable-wrappers.md)
- [docs/advanced-features.md](advanced-features.md)
- [docs/charts.md](charts.md)
- [docs/stats-presets.md](stats-presets.md)
- [docs/sql-like.md](sql-like.md)
- [docs/reports.md](reports.md)
- [docs/time-buckets.md](time-buckets.md)
- [docs/telemetry.md](telemetry.md)
- [docs/regression-fixtures.md](regression-fixtures.md)

