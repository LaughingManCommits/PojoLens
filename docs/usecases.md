# Real-World Use Cases

If you are new to PojoLens, start here.  
Each section answers: when to use it, what to copy, and what outcome you get.

## Choose Fast

| If you need...                         | Go to      | Main API                          |
|----------------------------------------|------------|-----------------------------------|
| A service-owned search endpoint        | Use Case 1 | `PojoLens.newQueryBuilder(...)`   |
| Config-driven dynamic queries          | Use Case 2 | `PojoLens.parse(...).params(SqlParams)` |
| Deterministic API pagination           | Use Case 2B | `LIMIT/OFFSET` + `keysetAfter(...)` |
| Large data, first-page consumers       | Use Case 2C | `.stream(...)` / `.iterator(...)` |
| Repeated hot equality filters          | Use Case 2D | `.addIndex(...)` + normal rules |
| Time-based finance/product summaries   | Use Case 3 | `bucket(...) + group by + having` |
| Multi-source views with joins          | Use Case 4 | `JoinBindings` / `DatasetBundle`  |
| Chart payloads for frontend/reporting  | Use Case 5 | `.chart(...)` + `ChartData`       |
| Safe refactors + regression protection | Use Case 6 | `QueryRegressionFixture`          |
| Production slowdown triage             | Use Case 7 | `.explain(...)` + telemetry       |

## Use Case 1: PeopleOps Search API

Problem:
- HR needs "active senior engineers ordered by salary" from in-memory rows.

Use:

```java
List<EmployeeDirectoryRow> rows = PojoLens.newQueryBuilder(employees)
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

## Use Case 2: Admin-Configurable Queries

Problem:
- Ops wants to tune filters without a deploy.

Use:

```java
List<EmployeeCompRow> rows = PojoLens
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

## Use Case 2B: Cursor-Friendly Pagination API

Problem:
- A feed endpoint needs stable paging under concurrent inserts.

Use offset for shallow pages:

```java
List<EmployeeFeedRow> rows = PojoLens
    .parse("where active = true order by salary desc, id desc limit 20 offset 40")
    .filter(employees, EmployeeFeedRow.class);
```

Use keyset for deep pages:

```java
SqlLikeCursor cursor = PojoLens.newKeysetCursorBuilder()
    .put("salary", 120000)
    .put("id", 1)
    .build();

List<EmployeeFeedRow> rows = PojoLens
    .parse("where active = true order by salary desc, id desc limit 20")
    .keysetAfter(cursor)
    .filter(employees, EmployeeFeedRow.class);
```

Outcome:
- Predictable page windows and stable next-page behavior with deterministic sort keys.

## Use Case 2C: Memory-Efficient First-Page Reads

Problem:
- A pipeline only needs the first page/window and should avoid full list materialization.

Use:

```java
List<EmployeeCompRow> firstPage = PojoLens
    .parse("select name, department, salary where salary >= 100000")
    .stream(employees, EmployeeCompRow.class)
    .limit(50)
    .toList();
```

Outcome:
- Low-allocation first-page extraction via lazy streaming/iteration.

## Use Case 2D: Repeated Filter Endpoint on the Same Snapshot

Problem:
- A service executes the same equality-heavy filters repeatedly over one in-memory snapshot.

Use:

```java
List<EmployeeDirectoryRow> rows = PojoLens.newQueryBuilder(employees)
    .addIndex("department")
    .addIndex("active")
    .addRule("department", "Engineering", Clauses.EQUAL)
    .addRule("active", true, Clauses.EQUAL)
    .initFilter()
    .filter(EmployeeDirectoryRow.class);
```

Outcome:
- Optional index hints narrow candidate rows for compatible equality filters, with automatic fallback to scan when inapplicable.

## Use Case 3: Monthly Payroll Trend

Problem:
- Finance needs timezone-aware monthly totals with noise filtered out.

Use:

```java
List<MonthlyPayroll> rows = PojoLens
    .parse("select bucket(hireDate,'month','Europe/Amsterdam') as period, "
        + "sum(salary) as payroll "
        + "group by period having payroll > 250000 order by period asc")
    .filter(employees, MonthlyPayroll.class);
```

Outcome:
- Ready-to-plot time series using explicit calendar semantics.

## Use Case 4: Companies + Employees in One View

Problem:
- Analytics endpoint combines company rows with employee rows repeatedly.

Use:

```java
DatasetBundle bundle = PojoLens.bundle(
    companies,
    JoinBindings.of("employees", employees));

List<CompanyHiringRow> rows = PojoLens
    .parse("select companyName, title, salary "
        + "from companies left join employees on id = companyId "
        + "where active = true order by salary desc")
    .filter(bundle, CompanyHiringRow.class);
```

Outcome:
- Reusable join-source wiring and cleaner call sites.

## Use Case 5: Charts With Common Chart Libraries

Problem:
- Backend computes business aggregates.
- UI/reporting needs chart-library-specific payloads.

### Step 1: Produce ChartData once

```java
ChartData chartData = PojoLens
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
List<Map<String, Object>> datasets = new ArrayList<>();
for (ChartDataset ds : chartData.getDatasets()) {
    Map<String, Object> mapped = new LinkedHashMap<>();
    mapped.put("label", ds.getLabel());
    mapped.put("data", ds.getValues());
    if (ds.getColorHint() != null) {
        mapped.put("backgroundColor", ds.getColorHint());
    }
    if (ds.getStackGroupId() != null) {
        mapped.put("stack", ds.getStackGroupId());
    }
    if (ds.getAxisId() != null) {
        mapped.put("yAxisID", ds.getAxisId());
    }
    datasets.add(mapped);
}

Map<String, Object> payload = new LinkedHashMap<>();
payload.put("type", "bar");
payload.put("data", Map.of(
    "labels", chartData.getLabels(),
    "datasets", datasets
));
```

Outcome:
- One PojoLens query can feed multiple chart libraries cleanly.

## Use Case 6: Refactor Without Behavior Drift

Problem:
- You need to change query logic but keep API-visible results stable.

Use:

```java
QueryRegressionFixture<EmployeeApiRow> fixture = QueryRegressionFixture
    .builder("employee-api-v1", EmployeeApiRow.class)
    .fluent(builder -> builder
        .addRule("active", true, Clauses.EQUAL)
        .addOrder("salary", 1))
    .build();
```

Outcome:
- Fixture/snapshot tests catch accidental output changes before release.

## Use Case 7: Production Triage

Problem:
- Endpoint got slower after query changes.

Use:

```java
Map<String, Object> explain = PojoLens
    .parse("where active = true order by salary desc limit 10")
    .explain(employees, Employee.class);
```

Outcome:
- Stage-level row counts help isolate where latency increased.

## Good Defaults

- Use fluent API for service-owned queries.
- Use SQL-like for config/admin-driven queries.
- Use `DatasetBundle` for repeated multi-source execution.
- Use `ChartData` as the boundary model between query and rendering.

## Next Reads

- [docs/charts.md](charts.md)
- [docs/sql-like.md](sql-like.md)
- [docs/reports.md](reports.md)
- [docs/time-buckets.md](time-buckets.md)
- [docs/telemetry.md](telemetry.md)
- [docs/regression-fixtures.md](regression-fixtures.md)
