# Entropy Wrapper And Binding Decision

This document is the `WP8.3` decision artifact for the `Entropy Reduction`
roadmap.
It narrows the overlapping public wrapper and multi-source binding stories onto
one default reusable-query contract and one default binding progression for
docs and new code.

## Decision Summary

- `ReportDefinition<T>` is the canonical reusable-query contract for docs and
  new code.
- `ChartQueryPreset<T>` and `StatsViewPreset<T>` remain public advanced
  conveniences, but they are specialized wrappers rather than peer-level
  defaults.
- `JoinBindings` is the canonical named multi-source binding input for ad-hoc
  execution.
- `DatasetBundle` is the canonical reusable execution snapshot, built from
  primary rows plus `JoinBindings`, when the same multi-source snapshot is
  executed more than once.
- raw `Map<String, List<?>>` join-source overloads remain public stable
  compatibility APIs in `1.x`, but docs and new code should treat them as
  adapter or interop-only rather than the default binding model.

## Compatibility Inputs

- [public-api-stability.md](public-api-stability.md) keeps `JoinBindings`,
  `DatasetBundle`, and the relevant `PojoLens.bundle(...)` overloads inside the
  stable `1.x` surface.
- [reusable-wrappers.md](reusable-wrappers.md), [reports.md](reports.md),
  [charts.md](charts.md), and [stats-presets.md](stats-presets.md) already
  position `ReportDefinition<T>` as the general wrapper and expose bridging
  methods from specialized presets.
- `ReportDefinition`, `ChartQueryPreset`, and `StatsViewPreset` already share
  the same execution engine and dataset-bundle or join-binding execution paths;
  `WP8.3` narrows the narrative rather than introducing new runtime mechanics.
- `SqlLikeQuery` and the wrapper types still expose raw map overloads, so any
  stronger surface reduction waits for `2.x` or a later compatibility review.

## Wrapper Disposition

| Surface | Role | Disposition | Preferred story | Notes |
| --- | --- | --- | --- | --- |
| `ReportDefinition<T>` | General reusable query contract | `Keep default` | Start here when the reusable thing is the query itself | Supports fluent or SQL-like definitions, schema metadata, optional chart mapping, and SQL-like join or bundle execution. |
| `ChartQueryPreset<T>` / `ChartQueryPresets` | Chart-first convenience wrapper | `Keep` + `de-emphasize as specialized` | Use only when preset factories and a baked-in `ChartSpec` are the point | Promote to `ReportDefinition<T>` through `reportDefinition()` when reuse broadens beyond chart-first output. |
| `StatsViewPreset<T>` / `StatsViewPresets` / `StatsTable<T>` | Table-first convenience wrapper | `Keep` + `de-emphasize as specialized` | Use only when totals, schema, or `StatsTable<T>` are part of the contract | Promote the row query through `reportDefinition()` when the reusable thing becomes broader than the table payload. |

## Binding Disposition

| Surface | Role | Disposition | Preferred story | Notes |
| --- | --- | --- | --- | --- |
| raw `Map<String, List<?>>` join-source overloads | Compatibility or adapter input | `Keep stable` + `de-emphasize for new code` | Use only at boundaries that already speak map-shaped join sources | This remains a `1.x` compatibility path, but it should not lead docs or new examples. |
| `JoinBindings` | Named multi-source binding contract | `Keep default` | Default ad-hoc binding input for multi-source execution | Gives clearer source names and validation than ad-hoc maps. |
| `DatasetBundle` | Reusable execution snapshot | `Keep default reusable form` | Wrap primary rows plus `JoinBindings` when the same snapshot will be executed repeatedly | This is the reusable or snapshot form of the same binding model, not a competing concept. |

## Canonical Reusable-Wrapper Story

1. Start with `ReportDefinition<T>` when the reusable thing is the query.
2. Stay on `ChartQueryPreset<T>` only when a chart-first preset family is the
   intended public contract.
3. Stay on `StatsViewPreset<T>` only when totals, schema, or `StatsTable<T>`
   are the contract.
4. If a specialized preset grows broader reuse needs, bridge it to
   `ReportDefinition<T>` and keep the specialized preset only where its
   chart-first or table-first API still adds value.

## Canonical Multi-Source Binding Story

1. Bind named secondary sources with `JoinBindings`.
2. Execute directly with `JoinBindings` for one-off or request-local
   multi-source calls.
3. Promote the same primary rows plus `JoinBindings` into `DatasetBundle` when
   the snapshot will be executed more than once across rows, chart, report, or
   preset calls.
4. Use raw `Map<String, List<?>>` only when adapting from existing map-shaped
   code or external configuration.

## Result

- `WP8.3` resolves the overlapping wrapper story onto one default reusable
  contract: `ReportDefinition<T>`.
- `WP8.3` resolves the overlapping binding story onto one default progression:
  `JoinBindings` first, `DatasetBundle` when reuse begins, raw maps only for
  compatibility edges.
- `WP8.5` can now implement any code deletions or deprecations against a
  settled docs and product-story baseline.
