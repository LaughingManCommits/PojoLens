# Tabular Result Schema Metadata

`PojoLens` can expose deterministic tabular metadata for query outputs.

Types:
- `TabularSchema`
- `TabularColumn`

Each column includes:
- `name` — field name in the output row
- `label` — human-readable display label
- `type` — Java `Class<?>` for the column value type
- `typeName` — `type().getSimpleName()` as a plain string (e.g. `"String"`, `"Long"`) — use this for JSON-friendly metadata or UI builder payloads
- `order` — insertion-order index
- optional `formatHint` — rendering hint (e.g. `"metric:COUNT"`)

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

## UI-Builder Metadata

`typeName()` is the JSON-safe column type accessor. Use it when building
admin schemas, form renderers, or column-config APIs:

```java
TabularSchema schema = PojoLensSql
    .parse("select department, count(*) as total group by department")
    .schema(DepartmentCount.class);

for (TabularColumn col : schema.columns()) {
    System.out.printf("%-20s %-10s %s%n",
        col.name(), col.typeName(), col.label());
    // department           String     department
    // total                Long       total
}
```

This is also the accessor used by `SavedReport.schema()` when passing schema
metadata through a persistence or API boundary.
See [reports.md](reports.md) for the full `SavedReport` workflow.

## See Also

- [reports.md](reports.md)
- [stats-presets.md](stats-presets.md)
- [entry-points.md](entry-points.md)


