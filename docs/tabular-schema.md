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
- `SqlLikeQuery.schema(Projection.class)`
- `NaturalQuery.schema(Projection.class)`
- `NaturalQuery.schema(rows, Projection.class)`
- `NaturalQuery.schema(rows, joinBindings, Projection.class)`
- `NaturalQuery.schema(datasetBundle, Projection.class)`
- `ReportDefinition.schema()`
- `ChartQueryPreset.schema()`

SQL-like example:

```java
TabularSchema schema = PojoLensSql
    .parse("select department, count(*) as total group by department")
    .schema(DepartmentCount.class);
```

Natural example:

```java
TabularSchema schema = PojoLensNatural
    .parse("show department, count of employees as total group by department")
    .schema(DepartmentCount.class);
```

Report example:

```java
ReportDefinition<DepartmentCount> report = ReportDefinition.sql(
    PojoLensSql.parse("select department, count(*) as total group by department"),
    DepartmentCount.class);

TabularSchema schema = report.schema();
```

Current format hints:
- metrics: `metric:COUNT`, `metric:SUM`, ...
- time buckets: `time-bucket:MONTH:UTC:MONDAY`, etc.

## See Also

- [reports.md](reports.md)
- [stats-presets.md](stats-presets.md)
- [entry-points.md](entry-points.md)


