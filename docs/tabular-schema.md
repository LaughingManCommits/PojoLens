# Tabular Result Schema Metadata

`PojoLens` can expose deterministic tabular metadata for query outputs.

Types:
- `TabularSchema`
- `TabularColumn`

Each column includes:
- `name`
- `label`
- `type`
- `order`
- optional `formatHint`

Entry points:
- `QueryBuilder.schema(Projection.class)`
- `SqlLikeQuery.schema(Projection.class)`
- `ReportDefinition.schema()`
- `ChartQueryPreset.schema()`

Fluent example:

```java
TabularSchema schema = PojoLensCore.newQueryBuilder(source)
    .addGroup("department")
    .addCount("total")
    .schema(DepartmentCount.class);
```

SQL-like example:

```java
TabularSchema schema = PojoLensSql
    .parse("select department, count(*) as total group by department")
    .schema(DepartmentCount.class);
```

Report example:

```java
ReportDefinition<DepartmentCount> report = PojoLens.report(
    PojoLensSql.parse("select department, count(*) as total group by department"),
    DepartmentCount.class);

TabularSchema schema = report.schema();
```

Current format hints:
- metrics: `metric:COUNT`, `metric:SUM`, ...
- time buckets: `time-bucket:MONTH:UTC:MONDAY`, etc.

