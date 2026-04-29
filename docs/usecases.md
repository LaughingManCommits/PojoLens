# Path Selection Guide

If you are new to PojoLens, start here.
Use this page to choose one authoring mode first, then add reusable contracts,
runtime policy, or helpers only where the workflow actually needs them.

Source guides:
- entry points: [docs/entry-points.md](entry-points.md)
- SQL-like query guide: [docs/sql-like.md](sql-like.md)
- natural query guide: [docs/natural.md](natural.md)
- reusable wrappers: [docs/reusable-wrappers.md](reusable-wrappers.md)
- file-boundary loaders: [docs/files.md](files.md)
- output helpers: [docs/output-helpers.md](output-helpers.md)
- optional advanced surface: [docs/advanced-features.md](advanced-features.md)

## 1. Pick Query Style

| If you need... | Choose... | Why |
| --- | --- | --- |
| Default query authoring over in-memory rows | `PojoLensSql.parse(...).params(...)` | Primary public path for filtering, ordering, grouping, joins, windows, subqueries, charts, schemas, and explain payloads. |
| Reusable SQL-like query shapes | `PojoLensSql.template(...)` | Keeps repeated query shapes on a fixed named-parameter schema. |
| Guided text queries for non-SQL users | `PojoLensNatural.parse(...).params(...)` | Default controlled plain-English path for deterministic text-driven queries without SQL syntax, including explicit joins, bounded subquery/existence phrases, grouped aggregates, deterministic window phrases with `qualify`, time buckets, and chart phrases; see [docs/natural.md](natural.md). |
| Code-owned typed filters and ordering | `TypedQuery.from(rowType)` | Stable Java-owned foundation for generated `TypedField<T,V>` constants, projection, filters, ordering, offset, limit, explain/schema, and execution guards. |

## 2. Pick Reusable Contract

| If you need... | Choose... | Why |
| --- | --- | --- |
| A reusable business query contract | `ReportDefinition<T>` | Default reusable wrapper for row-first queries that may feed more than one consumer. |
| A saved/versioned reusable contract | `SavedReport` | Default when the contract must be stored, reviewed, or replayed across sessions. |
| Advanced chart/table convenience after the reusable contract is already clear | `ChartQueryPresets` / `StatsViewPresets` | Optional sugar, not the default reusable-contract story. |

## 3. Add Boundary Or Output Helper

Output-helper guide:
- [output-helpers.md](output-helpers.md)

| If you need... | Choose... | Result |
| --- | --- | --- |
| Typed rows from a CSV, TSV, JSON, or JSONL file boundary | `PojoLensFiles.csv(...)` / `tsv(...)` / `json(...)` / `jsonl(...)` | `List<T>` |
| A subtree from flat parent-ID rows before query execution | `PojoLensTree.subtreeOf(...)` | `List<T>` |
| Typed rows only | `.filter(...)` or `report.rows(...)` | `List<T>` |
| Chart-ready payload | `.chart(...)`, `report.chart(...)`, or `preset.chart(...)` | `ChartData` |
| Table payload with rows, totals, and schema | `StatsViewPreset.table(...)` | `StatsTable<T>` |

## 4. Pick Runtime Model

| If you need... | Choose... | Why |
| --- | --- | --- |
| One shared app-level default policy | explicit entry points (`PojoLensSql` / `PojoLensNatural`) | Keeps the main query path simple after the authoring mode is chosen. |
| Environment-, tenant-, or test-scoped policy | `PojoLensRuntime` | Instance-scoped configuration and execution. |
| Optional diagnostics, cache tuning, telemetry, regression tooling, or build-time helpers | [docs/advanced-features.md](advanced-features.md) | Follow-on public surface after the main path is chosen. |

## 5. Scenario Index

| If you need... | Go to | Default path |
| --- | --- | --- |
| A service-owned search endpoint | Scenario 1 | `PojoLensSql.parse(...).params(...)` |
| Config-driven dynamic queries | Scenario 2 | `PojoLensSql.parse(...).params(SqlParams)` |
| Deterministic API pagination | Scenario 2B | `LIMIT/OFFSET` + `keysetAfter(...)` |
| Large data, first-page consumers | Scenario 2C | `.stream(...)` / `.iterator(...)` |
| Repeated query shape on one endpoint | Scenario 2D | `PojoLensSql.template(...)` |
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
List<EmployeeDirectoryRow> rows = PojoLensSql
    .parse("select name, department, level, salary "
        + "where active = :active and department = :dept and level >= :minLevel "
        + "order by salary desc limit 25")
    .params(SqlParams.builder()
        .put("active", true)
        .put("dept", "Engineering")
        .put("minLevel", 5)
        .build())
    .filter(employees, EmployeeDirectoryRow.class);
```

Outcome:
- Stable top-N API payload with query text, typed parameters, and deterministic
  ordering.

Reuse:
- Use `PojoLensSql.template(...)` when the same query shape runs repeatedly
  with different named parameter values.
- Use `ReportDefinition.sql(...)` when the query contract itself should be
  carried around with row/chart workflow methods.

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

### Scenario 2D: Repeated Query Shape on the Same Endpoint

Problem:
- A service executes the same query shape repeatedly with different parameter
  values.

Use:

```java
SqlLikeTemplate template = PojoLensSql.template(
    "where department = :dept and active = :active order by salary desc",
    "dept",
    "active"
);

List<EmployeeDirectoryRow> rows = template
    .bind(SqlParams.builder()
        .put("dept", "Engineering")
        .put("active", true)
        .build())
    .filter(employees, EmployeeDirectoryRow.class);
```

Outcome:
- One validated query shape can be reused with explicit parameter binding.

### Scenario 2E: Bounded Subquery Filter in SQL-like Code

Problem:
- A service-owned query needs departments that have at least one active row,
  without precomputing the department list outside the query.

Use:

```java
List<EmployeeDirectoryRow> rows = PojoLensSql
    .parse("where department in (select department where active = true)")
    .filter(employees, EmployeeDirectoryRow.class);
```

Explicit source:

```java
JoinBindings joinBindings = JoinBindings.of("employees", employees);

List<CompanyRow> rows = PojoLensSql
    .parse("where id in (select companyId from employees where active = true) "
        + "and exists (select * from employees where title = 'Engineer')")
    .filter(companies, joinBindings, CompanyRow.class);
```

Outcome:
- The bounded subquery is resolved by the shared execution engine.
  `EXISTS` and `NOT EXISTS` cover bounded existence checks without caller-side
  flags.

Grouped predicates:

```java
List<EmployeeDirectoryRow> rows = PojoLensSql
    .parse("where (department in (select department where active = true) "
        + "and region = 'EMEA') "
        + "or tier = 'Gold'")
    .filter(employees, EmployeeDirectoryRow.class);
```

Outcome:
- Supported subqueries can participate in grouped `AND` / `OR` expressions.
  Subqueries remain uncorrelated and bounded.

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

Five-line chart addition with advanced preset sugar + Chart.js adapter:

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

Use grouped stats convenience preset:

```java
StatsTablePayload table = StatsViewPresets
    .by("department", Metric.SUM, "salary", "payroll")
    .tablePayload(employees);
```

Use leaderboard convenience preset:

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
    .sql(
        QuerySnapshotFixture.of("employee-api", employees),
        PojoLensSql.parse("where active = true order by salary desc"),
        EmployeeApiRow.class);
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

- Use `PojoLensSql` for the default public query path and templates.
- Use `PojoLensNatural` for guided plain-English text queries.
- Use `PojoLensSql` for config/admin-driven query strings and templates.
- Use `PojoLensFiles` for typed CSV/TSV/JSON/JSONL loading at the file boundary.
- Use `PojoLensTree` for subtree selection from flat parent-ID row lists.
- Use `new PojoLensRuntime()` or `PojoLensRuntime.ofPreset(...)` when lint, cache, strict typing, telemetry, computed fields, or natural-query vocabulary should be instance-scoped.
- Use `PojoLensChart` when rows already exist and only chart mapping remains.
- Use `SqlLikeCursor`, `ReportDefinition`, `DatasetBundle`, and `SnapshotComparison` directly for those helper workflows.
- Use `JoinBindings` for one-off multi-source execution.
- Use `DatasetBundle` when the same multi-source snapshot will be executed repeatedly.
- Use `ChartData` as the boundary model between query and rendering.

## 8. Next Reads

- [docs/entry-points.md](entry-points.md)
- [docs/sql-like.md](sql-like.md)
- [docs/natural.md](natural.md)
- [docs/reusable-wrappers.md](reusable-wrappers.md)
- [docs/advanced-features.md](advanced-features.md)
- [docs/charts.md](charts.md)
- [docs/stats-presets.md](stats-presets.md)
- [docs/reports.md](reports.md)
- [docs/time-buckets.md](time-buckets.md)
- [docs/telemetry.md](telemetry.md)
- [docs/regression-fixtures.md](regression-fixtures.md)

