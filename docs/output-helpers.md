# Output Helper Guide

Once the query authoring mode is chosen, PojoLens output helpers shape the
result for the consumer.

Start from one of these first:
- `PojoLensSql`
- `PojoLensNatural`
- `TypedQuery`
- `ReportDefinition` when the reusable contract itself is the thing you carry

Then add only the output helper the workflow actually needs.

## Output Helper Routes

| If you need... | Default route | Why |
| --- | --- | --- |
| Typed rows only | `.filter(...)` or `report.rows(...)` | Keep the query contract row-first when no extra output shape is needed. |
| Chart-ready output from a query | `.chart(...)` or `report.chart(...)` | Keep chart mapping attached to the query/report contract that owns the rows. |
| Chart mapping from rows you already have | `PojoLensChart.toChartData(...)` | Use the chart helper directly when query execution already happened elsewhere. |
| Table payload with rows, totals, and schema | `StatsViewPreset.table(...)` / `StatsTable` | Use the table-first helper only when totals and table payload shape are part of the contract. |
| Schema metadata for renderers or admin surfaces | `.schema(...)`, `report.schema()`, `StatsTable.schema()` | Keep schema attached to the query or output contract that already owns the columns. |

## Layering Rules

- Prefer direct query `.chart(...)` for one-off chart-producing flows.
- Prefer `ReportDefinition` when the same contract should serve rows, chart
  output, and schema metadata across requests or snapshots.
- Use `PojoLensChart` only when rows are already materialized and only chart
  mapping remains.
- Use `StatsViewPresets` only when the consumer contract is table-first and
  totals are part of the output.
- Use `ChartQueryPresets` only as advanced preset sugar after the reusable
  contract is already clear.
- Treat chart/table/schema helpers as layered output surfaces over the same
  engine, not as separate query-authoring modes.

## Helper Guides

- Chart mapping and `ChartJsAdapter`:
  [charts.md](charts.md)
- Table-first stats payloads:
  [stats-presets.md](stats-presets.md)
- Deterministic schema metadata:
  [tabular-schema.md](tabular-schema.md)
- Reusable report-centered flows:
  [reports.md](reports.md)
