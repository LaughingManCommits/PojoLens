# Reusable Wrapper Guide

PojoLens has one general reusable wrapper and two specialized convenience wrappers.
They all reuse the same in-memory query engine; the difference is the default output shape and the amount of preset convenience they add.

## Abstraction Ladder

- `ReportDefinition<T>`: the general reusable row-query contract
- `ChartQueryPreset<T>`: chart-first SQL-like convenience wrapper
- `StatsViewPreset<T>`: table-first SQL-like convenience wrapper

For docs and new code, `ReportDefinition<T>` is the canonical reusable-query
contract.
When in doubt, start with `ReportDefinition<T>`.
Use the specialized presets only when the wrapper itself should encode a chart-first or table-first workflow.

## Capability Matrix

| Wrapper | Query source | Rows | Chart | Totals/table payload | Schema | Join/bundle reuse | Bridge | Best fit |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `ReportDefinition<T>` | Fluent or SQL-like | Yes | Optional via `ChartSpec` | No built-in totals payload | Yes | Yes for SQL-like reports; fluent reports are rows-only and do not accept per-call join sources | `withChartSpec(...)` adds chart mapping | Reusable business query contract shared across requests, jobs, or multiple consumers |
| `ChartQueryPreset<T>` | SQL-like preset factory | Yes | Yes, always | No | Yes | Yes | `reportDefinition()` promotes to the general reusable report contract and keeps the chart spec | Reusable chart-first flows built from the preset families in `ChartQueryPresets` |
| `StatsViewPreset<T>` | SQL-like preset factory | Yes | No built-in chart output | Yes via `totals(...)` and `StatsTable<T>` | Yes | Yes | `reportDefinition()` promotes the row query only; totals remain on `StatsViewPreset` / `StatsTable` | Reusable summary, grouped table, or leaderboard flows where totals/schema are part of the contract |

## Decision Rules

- Use `ReportDefinition<T>` when the reusable thing is the query itself.
- Use `ReportDefinition<T>` when the query may be fluent today, or may grow into multiple consumers later.
- Use `ChartQueryPreset<T>` when the reusable thing is primarily a chart shape and one of the preset factories already expresses it well.
- Use `StatsViewPreset<T>` when the reusable thing is primarily a table payload with optional totals and deterministic schema.
- If a specialized preset starts accumulating more generic reuse needs, convert it to `ReportDefinition<T>` and keep the specialized preset only where the chart/table-first API still adds value.

## Bridge Rules

- `ChartQueryPreset.reportDefinition()` keeps the same row query and chart spec, but moves the contract onto the general reusable wrapper.
- `StatsViewPreset.reportDefinition()` keeps the same row query, but it does not carry totals. Totals remain a stats-preset concern exposed through `totals(...)` and `StatsTable<T>`.
- `ReportDefinition.withChartSpec(...)` adds chart output to a row-first reusable query without switching to a chart preset.

## Overlap And Disposition

Documentation-noise overlap:
- all three wrappers expose `rows(...)`
- all three expose `schema()`
- all three support dataset-bundle and join-binding execution for SQL-like-backed flows

Those overlaps are expected because the wrappers share one execution engine and one snapshot-reuse model.

Current `WP8.3` keep/de-emphasize call:
- keep `ReportDefinition<T>` as the default general reusable wrapper
- keep `ChartQueryPreset<T>` as specialized chart-first convenience
- keep `StatsViewPreset<T>` as specialized table-first convenience
- de-emphasize the idea that these are separate product identities

No wrapper is a current deprecation candidate.
If later roadmap work reduces this surface, the first move should be lower docs prominence or stronger bridging guidance, not `1.x` API removal.
The formal decision record for this call lives in
[docs/entropy-wrapper-binding-decision.md](entropy-wrapper-binding-decision.md).
