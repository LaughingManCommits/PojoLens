# Architecture Map

- Public query entry points are `PojoLensSql`, `PojoLensNatural`, and `TypedQuery`; the old `PojoLens` and public fluent facades are removed.
- `PojoLensRuntime` adds instance-scoped caches, computed-field defaults, telemetry, and lint or strict-typing configuration.

Fluent path:

- internal fluent/core query primitives are the canonical capability layer; SQL-like,
  natural, and typed should normally lower onto this path instead of owning
  independent semantics
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

Typed path:

- `dsl/TypedQuery` keeps Java-owned query composition immutable, then lowers to
  the internal fluent engine for execution

Feature layers:

- `chart/*` maps rows to `ChartData`
- `report/*`, `table/*`, `snapshot/*`, `testing/*`, `computed/*`, `telemetry/*`, and `metamodel/*` build on the core query engine
- `benchmark/*` plus `benchmarks/*` and `scripts/*` provide performance tooling and threshold checks

Single owners for core semantics (established 2026-09-27, WP-18 to WP-23):

- value comparison: `util/ObjectUtil.compareObject` (also `NOT_CONTAINS`/`NOT_MATCHES`: complement over non-null text, none-of for lists, invalid regex never matches); a null `dateFormat` means default temporal rules in `util/TemporalComparison` (values exact, literals at written precision); only an explicit fluent format truncates
- numeric precision: `ObjectUtil.compareNumbers`/`compareNumeric` for filters and sorting; `filter/NumericAccumulator` for SUM/AVG/MIN/MAX (long overflow throws)
- join keys: `filter/JoinKeys` for both the fast array and legacy row join paths; null keys never match
- group/distinct keys: value-based (`filter/QueryKey` holds `Object[]`); an explicit group date format groups by formatted text; `SELECT DISTINCT` = `filter/DistinctRowSupport` (after ORDER BY, before OFFSET/LIMIT, via `QueryBuilder.distinctRows()`), `COUNT(DISTINCT)` = `filter/DistinctValueCounter`; MEDIAN/PERCENTILE/STDDEV/VARIANCE = `internal/NumericStatistics` (PERCENTILE fraction = `QueryMetric.getArgument()`); aggregate reference text = `AggregateExpressionSupport.canonical`
- literal-to-regex lowering: `internal/TextPattern`, implemented by `internal/LiteralMatchPattern` (typed startsWith/endsWith/containsIgnoreCase, natural starts/ends with and `ignoring case`) and `internal/LikePattern` (SQL-like `LIKE`/`ILIKE`); natural `:param` patterns bind via `sqllike/internal/params/PatternParameterValue`
- keyset cursors: `sqllike/internal/cursor/SqlLikeKeysetSupport` places the predicate in WHERE, HAVING, or QUALIFY; nulls sort first in ASC and last in DESC
- records: `util/RecordProjectionSupport` (canonical constructor); `final` fields of regular classes stay outside the query schema
- stream file loaders: `files/internal/LoadSource` (never closes caller streams; strict UTF-8)
- text-surface negation: `sqllike/internal/expression/FilterExpressionNegation` rewrites SQL-like `NOT`/`NOT BETWEEN` and natural `not (...)`/`is not between` with the typed `not()` rules (WP-25, text clauses since WP-26); typed `NOT(text leaf)` lowers in `TypedQuery.toTextRule`; `BETWEEN` lowers to `>=`/`<=` in each parser
