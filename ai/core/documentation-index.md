# Documentation Index

## Highest-Value Product Docs

- `README.md`: primary product overview, public entry points, quick starts, and limitations. Mostly aligned with code and tests.
- `docs/sql-like.md`: strongest SQL-like reference. Backed by parser, validation, error-code, docs-example, and join tests.
- `docs/charts.md`: chart payload contract and examples. Backed by chart validation/mapping tests and XChart interop tests.
- `docs/benchmarking.md`: benchmark philosophy, suites, thresholds, hotspot microbenchmarks, a budgeted end-to-end computed-field join guardrail, and CI gates. Backed by benchmark classes, scripts, and benchmark utility tests.

## Feature References

- `docs/caching.md`: cache defaults and tuning guidance. Backed by cache config tests and runtime code.
- `docs/computed-fields.md`: computed registry contract. Backed by `ComputedFieldRegistryTest` and docs examples.
- `docs/reports.md`: reusable report definitions and chart bridge. Backed by `ReportDefinitionTest`.
- `docs/snapshot-comparison.md`: snapshot diff API and semantics. Backed by `SnapshotComparisonTest`.
- `docs/tabular-schema.md`: schema metadata contract. Backed by `ReportDefinitionTest` and `PublicApiCoverageTest`.
- `docs/telemetry.md`: telemetry stages and event shape. Backed by `QueryTelemetryTest`.
- `docs/time-buckets.md`: calendar preset behavior. Backed by `TimeBucketAggregationTest`.
- `docs/regression-fixtures.md`: snapshot/regression fixture usage. Backed by `QueryRegressionFixtureTest`.
- `docs/metamodel.md`: field-constant generation. Backed by `FieldMetamodelGeneratorTest`.
- `docs/modules.md`: staged entry-point split. Backed by facade classes and README.

## Process And History Docs

- `CONTRIBUTING.md`: local validation commands. Helpful, but benchmark jar examples are stale.
- `MIGRATION.md`: historical API changes and migration notes. Useful, but some SQL-like limitation notes are stale.
- `RELEASE.md`: release checklist. Useful, but version/tag examples and documented SQL-like limitations are stale.
- `LICENSE.md`: legal text.
- `ai/codex_bootstrap_prompt_with_docs_scan.md`: AI bootstrap process prompt, not product behavior.

## AI Memory Docs

- `ai/AGENTS.md`: future-agent load order and trust rules for the memory layer.
- `ai/core/*.md`: compact repo summaries, architecture map, alignment notes, runbook, and discovery notes.
- `ai/state/*.md`: current snapshot plus next-session handoff.
- `ai/indexes/*.json`: machine-friendly inventories of files, symbols, tests, config, and docs.

## Active Work And Future Plans

- `TODO.md`: primary planning document and the only explicit backlog-like file found.
- Verified current open focus from `TODO.md`: WP5 is closed, completed packages were purged from the backlog file, and the remaining work is now a small set of profiler-driven packages around computed-expression compilation, join/computed row-cloning removal, joined-schema reuse, and later budget rebaselining.
- No separate `ROADMAP.md`, `BACKLOG.md`, ADR directory, or design-notes folder was found.
