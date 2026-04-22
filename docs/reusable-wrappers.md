# Reusable Wrapper Guide

PojoLens has one general reusable wrapper and two specialized convenience
wrappers.
They all reuse the same in-memory query engine; the difference is the default output shape and the amount of preset convenience they add.

## Abstraction Ladder

- `SavedReport`: versioned, serialization-friendly saved-report contract (query
  text + params + chart + schema); designed for persistence, admin review, and
  migration-safe replay
- `ReportDefinition<T>`: the general reusable row-query contract for SQL-like
  or natural queries (in-process reuse)
- `ChartQueryPreset<T>`: chart-first SQL-like convenience wrapper
- `StatsViewPreset<T>`: table-first SQL-like convenience wrapper

For in-process reuse, `ReportDefinition<T>` is the canonical contract.
When the report must be stored, versioned, or reviewed without executing, start
with `SavedReport` and replay it as `ReportDefinition<T>` when ready.
Use the specialized presets only when the wrapper itself should encode a
chart-first or table-first workflow.

## Capability Matrix

| Wrapper | Query source | Serializable | Rows | Chart | Totals/table payload | Schema | Review without data | Bridge | Best fit |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `SavedReport` | SQL-like or natural text | Yes — plain data, no lambdas | Via `toDefinition(Class<T>)` | Via `toDefinition(Class<T>)` | No | Optional | `planPreview()`, `diagnostics()` | `toDefinition(Class<T>)` produces a live `ReportDefinition<T>` | Reports that must be stored, versioned, admin-reviewed, or replayed across sessions |
| `ReportDefinition<T>` | SQL-like or natural | No — holds a live executor | Yes | Optional via `ChartSpec` | No built-in totals payload | Yes | No data-free review | `withChartSpec(...)` adds chart mapping | Reusable business query contract shared across requests, jobs, or multiple in-process consumers |
| `ChartQueryPreset<T>` | SQL-like preset factory | No | Yes | Yes, always | No | Yes | No | `reportDefinition()` promotes to the general reusable report contract | Reusable chart-first flows built from the preset families in `ChartQueryPresets` |
| `StatsViewPreset<T>` | SQL-like preset factory | No | Yes | No built-in chart output | Yes via `totals(...)` and `StatsTable<T>` | Yes | No | `reportDefinition()` promotes the row query only; totals remain on `StatsViewPreset` / `StatsTable` | Reusable summary, grouped table, or leaderboard flows where totals/schema are part of the contract |

## Decision Rules

- Use `SavedReport` when the report must cross a persistence or API boundary —
  stored in a database, sent as JSON, versioned in config, or reviewed before execution.
- Use `ReportDefinition<T>` when the report only lives in-process and reuse
  across requests or snapshots is enough.
- Use `ReportDefinition<T>` when the query may be natural or SQL-like today,
  or may grow into multiple consumers later.
- Use `ChartQueryPreset<T>` when the reusable thing is primarily a chart shape and one of the preset factories already expresses it well.
- Use `StatsViewPreset<T>` when the reusable thing is primarily a table payload with optional totals and deterministic schema.
- If a specialized preset starts accumulating more generic reuse needs, convert it to `ReportDefinition<T>` and keep the specialized preset only where the chart/table-first API still adds value.

## Bridge Rules

- `SavedReport.toDefinition(Class<T>)` replays the saved contract as a live
  `ReportDefinition<T>` with default params, chart spec, and schema applied.
- `ChartQueryPreset.reportDefinition()` keeps the same row query and chart spec, but moves the contract onto the general reusable wrapper.
- `StatsViewPreset.reportDefinition()` keeps the same row query, but it does not carry totals. Totals remain a stats-preset concern exposed through `totals(...)` and `StatsTable<T>`.
- `ReportDefinition.withChartSpec(...)` adds chart output to a row-first reusable query without switching to a chart preset.
- Fluent-backed report bridges are internal engine infrastructure during the
  public-surface reset; do not use them in public examples.

## Overlap And Disposition

Documentation-noise overlap:
- the reusable wrappers expose `rows(...)`
- the reusable wrappers expose `schema()`
- `ReportDefinition<T>` supports dataset-bundle and join-binding execution for SQL-like and natural flows
- the specialized wrappers support dataset-bundle and join-binding execution for their SQL-like-backed flows

Those overlaps are expected because the wrappers share one execution engine and one snapshot-reuse model.

Current wrapper guidance:
- keep `ReportDefinition<T>` as the default general reusable wrapper
- keep `ChartQueryPreset<T>` as specialized chart-first convenience
- keep `StatsViewPreset<T>` as specialized table-first convenience
- de-emphasize the idea that these are separate product identities

