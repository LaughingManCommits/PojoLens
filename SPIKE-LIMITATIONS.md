# SPIKE: Reducing Current Query Limitations

## Question

Which documented limitations are still real, which have already been reduced,
and what should be fixed next without turning `pojo-lens` into a full SQL
engine or a concurrency framework?

## Current Scan (2026-04-11)

Completed:

- Time-bucket input broadening is implemented. Bucket source fields now support
  `java.util.Date`, `Instant`, `LocalDate`, `LocalDateTime`,
  `OffsetDateTime`, and `ZonedDateTime`.
- SQL-like `WHERE ... IN (select ...)` subqueries now support one uncorrelated
  output column produced from a simple field, grouped alias, or aggregate alias.
- Aggregate SQL-like `ORDER BY` already supports grouped fields, aggregate
  output aliases/names, and aggregate expressions.
- Fluent builder execution isolation is documented through default
  `copyOnBuild(true)` and reusable `ReportDefinition.fluent(...)` guidance.

Still valid:

- SQL-like subqueries are still intentionally bounded to uncorrelated,
  single-output `WHERE <field> IN (select ...)` shapes.
- Joined and correlated subqueries are still unsupported.
- SQL-like aggregate windows still support only
  `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`.
- Window functions still run only in non-aggregate query shapes.
- Mutable fluent builders are still configuration objects and are not safe for
  concurrent mutation.

## Thesis

The right strategy is not "remove every limitation."

The right strategy is:

- expand limits that unlock meaningful query power while preserving the current
  execution model
- clarify or reframe limits that are mostly about API shape or documentation
- avoid drifting into a general SQL planner, correlated-subquery engine, or
  synchronized mutable-builder runtime

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

### 2. SQL-like Subqueries (First Widening Completed)

Current behavior:

- only uncorrelated `WHERE field IN (select oneColumn ...)`
- subquery `SELECT` must contain exactly one explicit output
- that output may be a simple field, grouped alias, or aggregate alias
- named subquery `FROM <source>` can read from provided join-source bindings
- no `JOIN` clauses inside subqueries
- no correlated subqueries

Status:

- `2026-04-11`: grouped/aggregate subquery widening landed and was live-tested

Remaining valid limits:

- joined subqueries
- correlated subqueries
- broad `EXISTS` / scalar-subquery support
- arbitrary nested SQL-engine semantics

Recommendation:

- keep the current subquery boundary unless real `JoinBindings` use cases prove
  that uncorrelated joined subqueries are worth the added planner complexity

### 3. Aggregate SQL-like ORDER BY (Mostly Resolved)

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
- polish wording or diagnostics if they drift, but do not add raw-field
  aggregate ordering by default

### 4. Aggregate Window Frames (Next Active Slice)

Current behavior:

- aggregate windows support only
  `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`
- parser/tests reject unsupported frames such as
  `ROWS BETWEEN 1 PRECEDING AND CURRENT ROW`
- window functions are still limited to non-aggregate query shapes

Worth fixing next:

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

- add an explicit frame model instead of encoding the current frame as parser
  text only
- keep frame parsing narrow and fail fast for unsupported shapes
- extend fluent window execution only for the supported frame menu
- update SQL-like parser/runtime/parity/docs tests together
- add benchmark coverage only after semantics are stable

### 5. Fluent Builder Mutability (Valid, Optional Follow-Up)

Current behavior:

- fluent `QueryBuilder` is mutable
- concurrent mutation is not supported
- `copyOnBuild(true)` snapshots execution state and is enabled by default
- `ReportDefinition.fluent(...)` already provides a reusable business-query
  contract that builds a fresh builder per execution

Recommendation:

- keep the mutable builder lightweight
- only add a first-class immutable fluent template/prepared-query wrapper if
  repeated fluent authoring demand justifies another public type
- do not make the mutable builder synchronized by default

## Recommended Work Order

1. Bounded aggregate window frames.
2. Aggregate `ORDER BY` wording/diagnostic polish if docs or errors drift.
3. Immutable fluent prepared-wrapper design only if real reuse demand appears.
4. Uncorrelated joined subqueries only if existing `JoinBindings` workflows
   prove the need.

## Non-Goals

This spike should not be read as a plan to add:

- correlated subqueries
- general nested SQL semantics
- full SQL window-frame parity
- arbitrary temporal coercion rules with hidden timezone guessing
- a thread-safe mutable builder

## Adjacent Limits Not In README

These remain real adjacent constraints, but they are not the core focus unless
explicitly pulled in later:

- natural `qualify` is still alias-based
- natural window phrasing is intentionally narrow
- natural `schema(...)` is structural, not vocabulary-resolved
- natural execution still rebuilds a resolved delegate per execution/explain

## Recommendation

The next useful implementation slice is bounded aggregate window frames.

Time-bucket input broadening and grouped/aggregate subquery widening are already
done, so future limitation work should not start there unless a regression is
found.
