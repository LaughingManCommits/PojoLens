# Architecture Map

- `PojoLens` is the compatibility facade over `PojoLensCore`, `PojoLensSql`, and `PojoLensChart`.
- `PojoLensRuntime` adds instance-scoped caches, computed-field defaults, telemetry, and lint or strict-typing configuration.

Fluent path:

- fluent/core query primitives are the canonical capability layer; SQL-like and
  natural should normally lower onto this path instead of owning independent
  semantics
- `builder/FilterQueryBuilder` captures query shape, source schemas, and materialization decisions
- `filter/FilterExecutionPlan` compiles reusable execution metadata for both legacy row execution and schema-driven array execution
- `filter/FilterImpl` dispatches between the legacy `QueryRow` engine and `filter/FastArrayQuerySupport` for the selective single-join computed/filter/projection hot path

SQL-like path:

- `sqllike/parser/SqlLikeParser` parses text into AST
- `sqllike/internal/validation/*` validates fields, joins, aggregates, subqueries, and time buckets
- `sqllike/internal/binding/SqlLikeBinder` binds validated queries onto the fluent engine
- `sqllike/SqlLikeQuery` exposes the public SQL-like contract

Natural path:

- `natural/parser/NaturalQueryParser` parses controlled grammar into SQL-like
  AST shape
- runtime-owned natural queries resolve vocabulary, then reuse SQL-like
  validation/binding/execution behavior

Feature layers:

- `chart/*` maps rows to `ChartData`
- `report/*`, `table/*`, `snapshot/*`, `testing/*`, `computed/*`, `telemetry/*`, and `metamodel/*` build on the core query engine
- `benchmark/*` plus `benchmarks/*` and `scripts/*` provide performance tooling and threshold checks
