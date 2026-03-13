# Architecture Map

- `PojoLens` is the compatibility facade over `PojoLensCore`, `PojoLensSql`, and `PojoLensChart`.
- `PojoLensRuntime` adds instance-scoped caches, computed-field defaults, telemetry, and lint or strict-typing configuration.

Fluent path:

- `builder/FilterQueryBuilder` captures query shape and materialization decisions
- `filter/FilterExecutionPlan` compiles reusable execution metadata
- `filter/FilterImpl` and related engines execute filtering, grouping, ordering, joins, and chart preparation

SQL-like path:

- `sqllike/parser/SqlLikeParser` parses text into AST
- `sqllike/internal/validation/*` validates fields, joins, aggregates, subqueries, and time buckets
- `sqllike/internal/binding/SqlLikeBinder` binds validated queries onto the fluent engine
- `sqllike/SqlLikeQuery` exposes the public SQL-like contract

Feature layers:

- `chart/*` maps rows to `ChartData`
- `report/*`, `table/*`, `snapshot/*`, `testing/*`, `computed/*`, `telemetry/*`, and `metamodel/*` build on the core query engine
- `benchmark/*` plus `benchmarks/*` and `scripts/*` provide performance tooling and threshold checks
