# Stats View Presets

`StatsViewPresets` provides predefined table-oriented query shapes for common dashboard/report workloads.

Use it when you want reusable stats tables without hand-writing SQL-like strings for each endpoint.
`StatsViewPreset<T>` is the specialized table-first reusable wrapper in PojoLens.
If the reusable thing becomes a more general row query, bridge it to `ReportDefinition<T>`.

Wrapper selection guide:
- [docs/reusable-wrappers.md](reusable-wrappers.md)

Main contracts:
- `StatsViewPresets` factory methods (`summary`, `by`, `topNBy`)
- `StatsViewPreset<T>` executable preset
- `StatsTable<T>` output payload (`rows`, optional `totals`, `schema`)

## API Shapes

- `summary()`:
  single-row aggregate table (default `count(*) as total`)
- `by(field)`:
  grouped aggregate table ordered by metric desc, then group desc
- `topNBy(field, metric, n)`:
  grouped leaderboard table with deterministic ordering and `LIMIT`

All presets compile to regular SQL-like queries internally (no separate execution engine).

## Grouped Stats Table Example

```java
StatsViewPreset<DepartmentPayrollRow> preset = StatsViewPresets.by(
    "department",
    Metric.SUM,
    "salary",
    "payroll",
    DepartmentPayrollRow.class);

StatsTable<DepartmentPayrollRow> table = preset.table(employees);

List<DepartmentPayrollRow> rows = table.rows();
Map<String, Object> totals = table.totals();
TabularSchema schema = table.schema();
```

Behavior:
- `rows`: grouped rows ordered by `payroll desc, department desc`
- `totals`: optional overall aggregate values (`payroll` in this example)
- `schema`: deterministic ordered output columns for table rendering

Preset helpers:
- `preset.hasTotals()` tells you whether totals are part of the preset contract
- `preset.reportDefinition()` exports the row query as the general reusable wrapper

## Leaderboard Table Example

```java
StatsViewPreset<DepartmentPayrollRow> leaderboard = StatsViewPresets.topNBy(
    "department",
    Metric.SUM,
    "salary",
    "payroll",
    3,
    DepartmentPayrollRow.class);

StatsTable<DepartmentPayrollRow> topTable = leaderboard.table(employees);
List<DepartmentPayrollRow> topRows = topTable.rows();
```

This pattern is useful for "Top N categories by metric" endpoints with stable ordering and optional totals.

## Relation To ReportDefinition

If you later need to reuse the same row query outside the table-first flow, convert it:

```java
StatsViewPreset<DepartmentPayrollRow> preset = StatsViewPresets.by(
    "department",
    Metric.SUM,
    "salary",
    "payroll",
    DepartmentPayrollRow.class);

ReportDefinition<DepartmentPayrollRow> report = preset.reportDefinition();
```

`reportDefinition()` keeps the reusable row query, but totals remain on `StatsViewPreset<T>` and `StatsTable<T>`.
