# Report Definitions

`ReportDefinition<T>` captures a reusable query + projection contract for repeated execution against different in-memory dataset snapshots.

It also exposes deterministic table metadata through `schema()`.

Use it when the same report/query shape is executed across:

- multiple requests
- scheduled jobs over refreshed snapshots
- endpoints that need both rows and chart payloads

## SQL-like Report Definition

```java
ReportDefinition<DepartmentCount> report = PojoLens.report(
    PojoLens.parse("select department, count(*) as total group by department order by department asc"),
    DepartmentCount.class,
    ChartSpec.of(ChartType.BAR, "department", "total"));

List<DepartmentCount> rows = report.rows(snapshotA);
ChartData chart = report.chart(snapshotB);
TabularSchema schema = report.schema();
```

For SQL-like definitions, join sources can still be supplied per execution:

```java
List<Company> rows = report.rows(companies, JoinBindings.of("employees", employees));
```

If the same snapshot is reused across multiple report calls, wrap it once:

```java
DatasetBundle bundle = PojoLens.bundle(
    companies,
    JoinBindings.of("employees", employees));

List<Company> rows = report.rows(bundle);
ChartData chart = report.chart(bundle);
```

## Fluent Report Definition

```java
ReportDefinition<DepartmentCount> report = PojoLens.report(
    DepartmentCount.class,
    builder -> builder
        .addRule("active", true, Clauses.EQUAL)
        .addGroup("department")
        .addCount("total")
        .addOrder("department", 1),
    ChartSpec.of(ChartType.BAR, "department", "total"));

List<DepartmentCount> rows = report.rows(snapshotA);
```

Fluent report definitions build a fresh `QueryBuilder` for each execution, so the same definition can be reused safely across multiple dataset snapshots.

If the underlying query depends on reusable derived fields, attach the registry at query/build time:

```java
ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
    .add("adjustedSalary", "salary * 1.1", Double.class)
    .build();

ReportDefinition<DepartmentAdjustedPayroll> report = PojoLens.report(
    PojoLens.parse("select department, sum(adjustedSalary) as totalAdjustedPayroll group by department")
        .computedFields(registry),
    DepartmentAdjustedPayroll.class);
```

## Optional Chart Mapping

Chart mapping is optional.

If the report definition was created without a `ChartSpec`, `chart(...)` will throw.

You can attach one later:

```java
ReportDefinition<DepartmentCount> rowsOnly = PojoLens.report(
    PojoLens.parse("select department, count(*) as total group by department"),
    DepartmentCount.class);

ReportDefinition<DepartmentCount> chartReady = rowsOnly.withChartSpec(
    ChartSpec.of(ChartType.BAR, "department", "total"));
```

## Relation To ChartQueryPreset

`ChartQueryPreset<T>` remains the lightweight preset API for chart-first SQL-like flows.

If you want the more general report abstraction, convert it:

```java
ReportDefinition<DepartmentCount> report = preset.reportDefinition();
TabularSchema schema = preset.schema();
```

## Relation To StatsViewPreset

`StatsViewPreset<T>` is the table-first preset API for common summary/grouped/leaderboard query shapes.

It adds optional totals and schema metadata through `StatsTable<T>`:

```java
StatsTable<DepartmentCount> table = StatsViewPresets
    .by("department", DepartmentCount.class)
    .table(source);

List<DepartmentCount> rows = table.rows();
Map<String, Object> totals = table.totals();
TabularSchema schema = table.schema();
```

