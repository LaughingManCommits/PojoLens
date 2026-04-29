# Tabular Result Schema Metadata

`PojoLens` can expose deterministic tabular metadata for query outputs.
Schema is part of the output-helper layer, not a separate query path.
Start from the query or reusable contract that already owns the rows, then ask
that contract for schema metadata.

Output-helper route:
- [output-helpers.md](output-helpers.md)

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

## Output-Helper Route

Recommended defaults:
- use `SqlLikeQuery.schema(...)` or `NaturalQuery.schema(...)` for one-off
  query contracts
- use `ReportDefinition.schema()` when the same reusable contract should serve
  rows, chart output, and schema metadata
- use `StatsViewPreset.schema()` or `StatsTable.schema()` when the consumer is
  already table-first
- use `ChartQueryPreset.schema()` only for advanced chart-first preset flows

Entry points:
- `SqlLikeQuery.schema(Projection.class)`
- `NaturalQuery.schema(Projection.class)`
- `NaturalQuery.schema(rows, Projection.class)`
- `NaturalQuery.schema(rows, joinBindings, Projection.class)`
- `NaturalQuery.schema(datasetBundle, Projection.class)`
- `ReportDefinition.schema()`
- `StatsViewPreset.schema()`
- `StatsTable.schema()`
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

Table-first output example:

```java
StatsTable<DepartmentCount> table = StatsViewPresets
    .by("department", DepartmentCount.class)
    .table(source);

TabularSchema schema = table.schema();
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

- [output-helpers.md](output-helpers.md)
- [reports.md](reports.md)
- [stats-presets.md](stats-presets.md)
- [charts.md](charts.md)
- [entry-points.md](entry-points.md)


