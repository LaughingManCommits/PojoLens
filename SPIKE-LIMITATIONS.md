# SPIKE: Reducing Current Query Limitations

## Question

Which documented limitations are still real, which have already been reduced,
and what should be fixed next without turning `pojo-lens` into a full SQL
engine or a concurrency framework?

## Current Scan (2026-04-14)

Completed:

- Time-bucket input broadening is implemented. Bucket source fields now support
  `java.util.Date`, `Instant`, `LocalDate`, `LocalDateTime`,
  `OffsetDateTime`, and `ZonedDateTime`.
- SQL-like `WHERE ... IN (select ...)` subqueries now support one uncorrelated
  output column produced from a simple field, grouped alias, or aggregate alias.
  Subqueries may also use explicit `JOIN` clauses when the subquery `FROM`
  source and joined sources are provided through `JoinBindings`.
- SQL-like `WHERE EXISTS (select ...)` and `WHERE NOT EXISTS (select ...)`
  subqueries are now supported for bounded, uncorrelated existence checks.
  `EXISTS` may use a self-source subquery or a named `FROM`/`JOIN` subquery
  backed by the existing `JoinBindings` model.
- Fluent/core now exposes bounded uncorrelated subquery predicates through
  `QueryBuilder.addInSubquery(...)`, `addExists(...)`, and `addNotExists(...)`.
  These resolve at execution-snapshot time and support self-source and
  explicit-source subqueries without caller-side precomputation.
- Natural now exposes controlled bounded subquery/existence grammar:
  `is in query ... end query`, `exists query ... end query`, and
  `not exists query ... end query`. Runtime natural vocabulary is resolved
  through both the outer query and the bounded subquery.
- SQL-like simple bounded `WHERE ... IN (select ...)` and
  `WHERE [NOT] EXISTS (select ...)` predicates now lower onto the fluent/core
  subquery predicate path. SQL-like still keeps its precomputed fallback for
  boolean `OR` / normalized DNF shapes that the fluent API cannot yet represent
  as grouped subquery predicates.
- Aggregate SQL-like `ORDER BY` already supports grouped fields, aggregate
  output aliases/names, and aggregate expressions.
- Aggregate SQL-like `ORDER BY` diagnostics now distinguish invalid raw source
  fields from unknown-field typos in aggregate query shapes.
- `PojoLensCore.prepare(...)` now exposes a fluent-only immutable prepared query
  definition for reusable code-owned builder recipes.
- SQL-like aggregate windows now support the bounded frame menu:
  `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`,
  `ROWS BETWEEN <n> PRECEDING AND CURRENT ROW`, and
  `ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING`.
- Fluent builder execution isolation is documented through default
  `copyOnBuild(true)` and reusable `ReportDefinition.fluent(...)` guidance.

Still valid:

- No user-facing cross-surface parity gap is currently open for bounded
  uncorrelated subquery/existence predicates. Fluent/core, SQL-like, and
  natural all expose first-class bounded forms without caller-side
  precomputation.
- SQL-like subqueries are still intentionally bounded to uncorrelated
  `WHERE <field> IN (select ...)` and `WHERE [NOT] EXISTS (select ...)`
  shapes.
- Correlated subqueries, scalar subqueries, and arbitrary nested SQL planning
  are still unsupported.
- SQL-like aggregate windows still reject broad SQL frame families such as
  `RANGE`, `GROUPS`, following-row frames, and expression-based offsets.
- Window functions still run only in non-aggregate query shapes.
- Mutable fluent builders are still configuration objects and are not safe for
  concurrent mutation.

## Thesis

The right strategy is not "remove every limitation."

The right strategy is:

- expand limits that unlock meaningful query power while preserving the current
  execution model
- keep fluent-led parity across fluent, SQL-like, and natural; syntax can
  differ, but the core/fluent capability should normally lead and the other
  surfaces should lower onto it
- clarify or reframe limits that are mostly about API shape or documentation
- avoid drifting into a general SQL planner, correlated-subquery engine, or
  synchronized mutable-builder runtime

## Fluent-Led Parity Requirement

New query capability should normally be modeled first as a fluent/core query
primitive. SQL-like and natural are facade surfaces over that engine, not
separate feature owners. A capability is not considered fully complete until
fluent, SQL-like, and natural have equivalent first-class ways to express it, or
the spike records an explicit product exception.

For limitation work, "equivalent" means:

- Fluent exposes the capability through typed builder/API primitives.
- SQL-like may use SQL-style syntax.
- Natural may use controlled grammar phrases that lower to the same bounded
  engine behavior.
- No surface should require users to manually precompute intermediate filter
  values or existence flags to reach behavior available in another surface.
- If a facade surface temporarily lands a capability first, the next work is to
  move the capability back into fluent/core and regain parity.

Current parity status:

- SQL-like now supports bounded uncorrelated `WHERE ... IN (select ...)` and
  `WHERE [NOT] EXISTS (select ...)`.
- Fluent/core now has canonical bounded subquery/existence primitives.
- Natural now has controlled `query ... end query` grammar for bounded
  `is in`, `exists`, and `not exists` predicates.
- No user-facing precomputation workaround is required for this capability.
- SQL-like simple bounded subquery predicates now use the shared fluent/core
  path; complex boolean `OR`/DNF subquery shapes retain SQL-like fallback
  resolution until grouped fluent subquery predicates exist.

## Limitation Review

### 1. Time Bucket Input Types (Completed)

Current behavior:

- supported inputs are `Date`, `Instant`, `LocalDate`, `LocalDateTime`,
  `OffsetDateTime`, and `ZonedDateTime`
- `LocalDate` and `LocalDateTime` are interpreted in the active bucket preset
  timezone
- instant-based inputs are normalized into the active bucket preset timezone
  before bucketing

Evidence:

- `TimeBucketUtil.supportsTimeBucketType(...)` covers all supported types
- `TimeBucketUtilTest` covers the shared formatter and timezone semantics
- `TimeBucketAggregationTest` covers fluent and SQL-like execution
- `SqlLikeValidationTest` covers SQL-like validation for broadened field types
- public docs in `docs/time-buckets.md`, `docs/sql-like.md`, and
  `docs/natural.md` already describe the broadened support

Status:

- completed; no implementation work remains for the original Date-only limit

### 2. Bounded Subquery/Existence Predicates (Parity Closed)

Current behavior:

- fluent supports uncorrelated `addInSubquery(...)`, `addExists(...)`, and
  `addNotExists(...)` predicates
- fluent subqueries may run against the parent source rows or an explicit
  subquery source list
- fluent subqueries are configured with the normal `QueryBuilder`, so they can
  use filters, grouping, aggregate outputs, limits, and explicit joins
- SQL-like supports uncorrelated `WHERE field IN (select oneColumn ...)`
- SQL-like supports uncorrelated `WHERE EXISTS (select ...)` and
  `WHERE NOT EXISTS (select ...)`
- `IN` subquery `SELECT` must contain exactly one explicit output
- that `IN` output may be a simple field, grouped alias, or aggregate alias
- `EXISTS` ignores selected output and may use `SELECT *` or explicit fields
- named subquery `FROM <source>` can read from provided join-source bindings
- subquery `JOIN` clauses can read from provided join-source bindings
- natural supports equivalent uncorrelated forms with `query ... end query`
  bounds instead of SQL parentheses
- SQL-like simple bounded subquery predicates are stored as fluent/core
  subquery predicates during binding; complex boolean `OR`/DNF cases keep the
  previous precomputed fallback
- no correlated subqueries

Status:

- `2026-04-11`: grouped/aggregate subquery widening landed and was live-tested
- `2026-04-13`: uncorrelated joined subqueries landed for existing
  `JoinBindings` workflows
- `2026-04-13`: bounded uncorrelated `EXISTS` / `NOT EXISTS` subqueries
  landed for self-source, named-source, and joined-source checks
- `2026-04-14`: fluent/core bounded subquery predicates landed for
  self-source and explicit-source `IN`, `EXISTS`, and `NOT EXISTS` workflows
- `2026-04-14`: natural bounded subquery/existence grammar landed with runtime
  vocabulary resolution across outer and nested natural query fields
- `2026-04-14`: SQL-like simple bounded subquery predicates were converged onto
  the fluent/core subquery predicate path, with boolean `OR`/DNF fallback left
  intentionally precomputed

Remaining valid limits:

- SQL-like complex boolean subquery groups still use a precomputed fallback
  because fluent/core does not yet expose grouped subquery predicates
- correlated subqueries
- scalar subqueries
- arbitrary nested SQL-engine semantics

Recommendation:

- keep the current uncorrelated boundary
- keep parity tests across fluent, SQL-like, and natural; do not ask users to
  precompute filter lists or existence flags
- do not add correlated, scalar, or arbitrary nested SQL semantics by default

### 3. Aggregate SQL-like ORDER BY (Resolved Boundary)

Current behavior:

- aggregate queries can order by grouped fields
- aggregate queries can order by aggregate output aliases/names
- aggregate queries can order by aggregate expressions, including unselected
  aggregate expressions such as `order by sum(salary) desc`
- aggregate queries cannot order by arbitrary non-grouped raw source fields

Why the remaining limit exists:

- after grouping, the working row shape is no longer the raw source row shape
- ordering aggregate rows by non-grouped source fields would require hidden
  pre-aggregation or tie-break semantics

Recommendation:

- treat this as a semantic boundary, not an engine gap
- diagnostics now call out invalid aggregate `ORDER BY` references when a raw
  source field is known but not grouped or aggregated
- typo cases still use the unknown-field suggestion path
- do not add raw-field aggregate ordering by default

### 4. Aggregate Window Frames (Completed Narrow Widening)

Current behavior:

- aggregate windows support
  `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`
- aggregate windows support
  `ROWS BETWEEN <n> PRECEDING AND CURRENT ROW`
- aggregate windows support
  `ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING`
- window functions are still limited to non-aggregate query shapes

Completed on 2026-04-12:

1. `ROWS BETWEEN <n> PRECEDING AND CURRENT ROW`
   - trailing windows
   - practical for rolling sums/averages
2. `ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING`
   - full-partition totals
   - useful for percent-of-total style calculations

Keep out of scope initially:

- `RANGE` frames
- `GROUPS` frames
- expression-based frame offsets
- mixed aggregate-window plus grouped-query execution

Implementation direction:

- `QueryWindowFrame` now represents the supported frame menu
- SQL-like parsing stays narrow and fails fast for unsupported shapes
- fluent aggregate-window execution honors the supported frame menu
- SQL-like parser/runtime and fluent tests cover running, bounded trailing, and
  full-partition frames
- add benchmark coverage only if future frame work changes performance-critical
  execution paths

### 5. Fluent Builder Mutability (Prepared Wrapper Added)

Current behavior:

- fluent `QueryBuilder` is mutable
- concurrent mutation is not supported
- `copyOnBuild(true)` snapshots execution state and is enabled by default
- `ReportDefinition.fluent(...)` already provides a reusable business-query
  contract that builds a fresh builder per execution
- `PojoLensCore.prepare(...)` returns a `FluentQueryDefinition<T>` that stores
  the fluent builder recipe, rebuilds a fresh builder per execution, exposes
  `schema()`/`explain()`, and can promote to `ReportDefinition<T>`

Recommendation:

- keep the mutable builder lightweight
- use `PojoLensCore.prepare(...)` when the reusable object should remain
  fluent-only
- use `ReportDefinition.fluent(...)` when the reusable object should be the
  general row/chart business-query contract
- do not make the mutable builder synchronized by default

## Recommended Work Order

No default bounded subquery parity slice remains. Choose the next limitation
slice only from concrete demand, and keep any future subquery work
uncorrelated and bounded unless a separate design explicitly justifies broader
planning.

## Non-Goals

This spike should not be read as a plan to add:

- correlated subqueries
- general nested SQL semantics
- full SQL window-frame parity
- arbitrary temporal coercion rules with hidden timezone guessing
- a thread-safe mutable builder
- permanent one-surface-only query capabilities that force user-side
  precomputation on fluent or natural users

## Adjacent Natural Limits

These adjacent natural-query constraints have been reduced:

- natural `qualify` accepts window output aliases and controlled inline window
  phrases
- natural window phrasing supports multiple partition fields, additional
  order/partition wording, and the supported aggregate `ROWS` frame menu
- runtime-owned natural queries reuse resolved delegates by execution shape
- joined natural schema metadata can resolve vocabulary through the dataset and
  join-binding schema overloads

## Recommendation

The bounded subquery/existence parity gap is closed at the product-surface
level. Treat the remaining correlated subquery, broad scalar, broad
window-frame, and mutable-builder concurrency ideas as opt-in only.
