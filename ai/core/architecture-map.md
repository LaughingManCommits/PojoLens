# Architecture Map

- Verified: `PojoLens` is the compatibility facade and delegates to `PojoLensCore`, `PojoLensSql`, and `PojoLensChart`.
- Verified: `PojoLensRuntime` provides instance-scoped SQL parse cache, stats-plan cache, computed-field defaults, lint/strict-typing defaults, and telemetry hooks.

## Fluent Pipeline

- Verified: `builder/FilterQueryBuilder` captures fluent query shape, explain/schema payloads, computed fields, and selective/lazy materialization decisions.
- Verified: `filter/FilterImpl` executes filter, group, order, join, and chart flows over execution snapshots.
- Verified: `filter/FilterExecutionPlan` precompiles rule bindings plus distinct/order/group/metric metadata for repeated execution.
- Verified: `util/ReflectionUtil` is the main POJO flattening and projection utility.

## SQL-like Pipeline

- Verified: `sqllike/parser/SqlLikeParser` parses normalized text into AST and enforces guardrails such as max query length, token count, predicate count, and order/select limits.
- Verified: `sqllike/internal/validation/*` resolves join plans, validates fields and aggregates, enforces subquery and time-bucket rules, and normalizes grouped aliases.
- Verified: `sqllike/internal/binding/SqlLikeBinder` maps validated AST state onto the fluent pipeline.
- Verified: `sqllike/SqlLikeQuery` is the public contract for params, typed bind-first execution, charting, schema, explain, lint, telemetry, and computed fields.

## Feature Layers

- Verified: `chart/*` owns chart payload models, presets, mapping, and validation.
- Verified: `report/ReportDefinition` wraps reusable query plus projection plus optional chart metadata.
- Verified: `table/*` owns deterministic tabular schema metadata.
- Verified: `snapshot/*` owns keyed snapshot comparison and diff rows.
- Verified: `testing/*` owns parity helpers and regression fixtures.
- Verified: `benchmark/*` plus `scripts/*` and `benchmarks/*` own performance measurement, threshold checks, parity checks, and plot generation.
