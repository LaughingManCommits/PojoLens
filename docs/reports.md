# Report Definitions

`ReportDefinition<T>` captures a reusable query + projection contract for
repeated execution against different in-memory dataset snapshots.

It also exposes deterministic table metadata through `schema()`.
It is the general reusable wrapper in PojoLens and the default reusable-query
contract for docs and new code.
SQL-like, natural, and fluent queries can all promote into it.
`ChartQueryPreset<T>` and `StatsViewPreset<T>` are specialized chart-first and
table-first wrappers that can bridge back to it.

Wrapper selection guide:
- [docs/reusable-wrappers.md](reusable-wrappers.md)

Use it when the same report/query shape is executed across:

- multiple requests
- scheduled jobs over refreshed snapshots
- endpoints that need both rows and chart payloads
- flows that may start simple now but later need more than one consumer

## SQL-like Report Definition

```java
ReportDefinition<DepartmentCount> report = ReportDefinition.sql(
    PojoLensSql.parse("select department, count(*) as total group by department order by department asc"),
    DepartmentCount.class,
    ChartSpec.of(ChartType.BAR, "department", "total"));

List<DepartmentCount> rows = report.rows(snapshotA);
ChartData chart = report.chart(snapshotB);
TabularSchema schema = report.schema();
```

## Natural Report Definition

```java
ReportDefinition<DepartmentCount> report = ReportDefinition.natural(
    PojoLensNatural.parse(
        "show department, count of employees as total "
            + "where active is true group by department sort by department ascending"),
    DepartmentCount.class,
    ChartSpec.of(ChartType.BAR, "department", "total"));

List<DepartmentCount> rows = report.rows(snapshotA);
ChartData chart = report.chart(snapshotB);
```

If runtime vocabulary or computed fields should apply, parse the query through
`runtime.natural()` first and then wrap that `NaturalQuery`:

```java
PojoLensRuntime runtime = new PojoLensRuntime();
runtime.setNaturalVocabulary(NaturalVocabulary.builder()
    .field("department", "team")
    .build());

ReportDefinition<DepartmentCount> report = ReportDefinition.natural(
    runtime.natural().parse(
        "show team as department, count of employees as total "
            + "where active is true group by team sort by department ascending"),
    DepartmentCount.class);
```

Natural report definitions support `JoinBindings` / `DatasetBundle` the same
way SQL-like report definitions do.

For SQL-like definitions, `JoinBindings` is the default one-off multi-source
execution input:

```java
List<Company> rows = report.rows(companies, JoinBindings.of("employees", employees));
```

If the same snapshot is reused across multiple report calls, wrap the primary
rows plus `JoinBindings` once in `DatasetBundle`:

```java
DatasetBundle bundle = DatasetBundle.of(
    companies,
    JoinBindings.of("employees", employees));

List<Company> rows = report.rows(bundle);
ChartData chart = report.chart(bundle);
```

## Fluent Report Definition

```java
ReportDefinition<DepartmentCount> report = ReportDefinition.fluent(
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

ReportDefinition<DepartmentAdjustedPayroll> report = ReportDefinition.sql(
    PojoLensSql.parse("select department, sum(adjustedSalary) as totalAdjustedPayroll group by department")
        .computedFields(registry),
    DepartmentAdjustedPayroll.class);
```

## Optional Chart Mapping

Chart mapping is optional.

If the report definition was created without a `ChartSpec`, `chart(...)` will throw.
This stays true for natural report definitions even when the source natural
query text contains `as <type> chart`; reusable report contracts keep chart
mapping explicit.

You can attach one later:

```java
ReportDefinition<DepartmentCount> rowsOnly = ReportDefinition.sql(
    PojoLensSql.parse("select department, count(*) as total group by department"),
    DepartmentCount.class);

ReportDefinition<DepartmentCount> chartReady = rowsOnly.withChartSpec(
    ChartSpec.of(ChartType.BAR, "department", "total"));
```

## Relation To ChartQueryPreset

`ChartQueryPreset<T>` remains the lightweight preset API for chart-first SQL-like flows.
Choose it when the preset factory already matches the chart workflow you want.

If you want the more general report abstraction, convert it:

```java
ReportDefinition<DepartmentCount> report = preset.reportDefinition();
TabularSchema schema = preset.schema();
```

## Relation To StatsViewPreset

`StatsViewPreset<T>` is the table-first preset API for common summary/grouped/leaderboard query shapes.
Choose it when totals and `StatsTable<T>` are part of the contract.

It adds optional totals and schema metadata through `StatsTable<T>`:

```java
StatsTable<DepartmentCount> table = StatsViewPresets
    .by("department", DepartmentCount.class)
    .table(source);

List<DepartmentCount> rows = table.rows();
Map<String, Object> totals = table.totals();
TabularSchema schema = table.schema();
```

Converting a stats preset to `ReportDefinition<T>` keeps the row query, but not the totals payload:

```java
ReportDefinition<DepartmentCount> report = StatsViewPresets
    .by("department", DepartmentCount.class)
    .reportDefinition();
```


