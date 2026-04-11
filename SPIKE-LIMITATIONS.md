# SPIKE: Reducing Current Query Limitations

## Question

Which current documented limitations are worth fixing, and how should they be
reduced without turning `pojo-lens` into a full SQL engine or a concurrency
framework?

Current README limitations:

- SQL-like subqueries currently support only uncorrelated single-column
  `WHERE <field> IN (select ...)` subqueries
- joined and correlated subqueries remain unsupported
- aggregate SQL-like `ORDER BY` is restricted
- aggregate windows currently support only
  `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`
- time-bucket input fields must be `java.util.Date`
- fluent query builders are mutable and not safe for concurrent mutation

## Thesis

The right strategy is not "remove every limitation."

The right strategy is:

- expand limits that unlock meaningful query power while preserving the current
  execution model
- clarify or reframe limits that are mostly about API shape or documentation
- avoid drifting into a general SQL planner, correlated-subquery engine, or
  highly synchronized builder runtime

In practice, that means:

- subquery expansion should stay deterministic and uncorrelated at first
- window-frame expansion should stay narrow and explicit
- time-bucket broadening should normalize more Java time types into the same
  existing bucket logic
- fluent builder mutability should probably be addressed with an immutable
  prepared wrapper, not by making the builder itself concurrent

## Verified Current State

As of `2026-04-03`, the current constraints are real and come from the current
code path, not just README wording.

Highlights:

- SQL-like subqueries are parsed only under `IN (select ...)` and now support
  single-column grouped/aggregate outputs; joins and correlation remain out of
  scope.
- Aggregate `ORDER BY` is intentionally constrained to the aggregate row shape.
- Aggregate windows require the running frame
  `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`.
- Time buckets require `java.util.Date`.
- Fluent `QueryBuilder` is mutable; `copyOnBuild(true)` is already the default
  execution-isolation mechanism.

## Limitation Review

### 1. SQL-like Subqueries

Current behavior:

- only uncorrelated `WHERE field IN (select oneColumn ...)`
- subquery `SELECT` must contain exactly one explicit field
- single-column grouped and aggregate outputs are supported
- no joined subqueries
- no correlated subqueries

Why it exists:

- the current binder resolves subqueries as a flat list of values
- the validator still rejects joins early
- the binder now extracts either raw field values or aggregate output aliases
  depending on the supported subquery shape

What is worth fixing:

- possibly allow uncorrelated joined subqueries later if existing
  `JoinBindings`-driven use cases genuinely need it

Status:

- `2026-04-11`: uncorrelated single-column grouped/aggregate subquery widening
  has landed and been live-tested.

What is not worth fixing first:

- correlated subqueries
- arbitrary nested SQL-engine semantics
- broad `EXISTS` / scalar-subquery support before single-column grouped
  subqueries are stable

Recommended first slice:

1. keep the outer shape as `WHERE field IN (select oneColumn ...)`
2. allow the subquery to produce that one column via:
   - grouped field
   - aggregate output alias
   - time-bucket alias
3. update subquery value extraction to read the actual output column name, not
   only the raw selected field
4. keep joins and correlation out of scope for this first widening

Expected repo fit:

- good
- this extends the current SQL-like path without creating a new planner model

Risk:

- medium
- subquery validation, binder extraction, and explain/error messages all need
  coordinated updates

### 2. Aggregate SQL-like ORDER BY

Current behavior:

- aggregate queries can order by grouped fields, aggregate output aliases/names,
  and aggregate expressions that resolve against the aggregate row shape
- they cannot order by arbitrary raw source fields once the query has been
  grouped

Why it exists:

- after grouping, the working row shape is no longer the raw source row shape
- allowing arbitrary source-field ordering would be semantically unclear and
  would force hidden tie-break or pre-aggregation semantics

What is worth fixing:

- improve wording and validation messages so the supported shapes are obvious
- normalize docs around "group-by field or aggregate output/expression"

What is probably not worth fixing:

- ordering aggregate results by non-grouped raw source fields

Recommendation:

- treat this mainly as a documentation and diagnostics refinement item, not a
  major engine-expansion spike

Expected repo fit:

- strong
- this keeps semantics crisp instead of emulating surprising SQL edge cases

Risk:

- low

### 3. Aggregate Window Frames

Current behavior:

- aggregate windows support only
  `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`

Why it exists:

- the current implementation is optimized around deterministic running-window
  behavior
- this keeps parser, AST, validation, and execution simpler

What is worth fixing:

- a small explicit frame menu that stays deterministic and practical

Recommended frame expansion order:

1. `ROWS BETWEEN <n> PRECEDING AND CURRENT ROW`
   - trailing windows
   - practical for rolling sums/averages
2. `ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING`
   - full-partition totals
   - useful for percent-of-total style calculations

What should stay out of scope initially:

- `RANGE` frames
- arbitrary expression-based frames
- mixed aggregate + grouped query shapes in the same statement

Expected repo fit:

- good
- this is a real analytic capability expansion without changing the public story

Risk:

- medium
- execution cost and explain output will need fresh coverage and probably new
  benchmark cases

### 4. Time Bucket Input Types

Current behavior:

- time buckets require `java.util.Date`

Why it exists:

- the bucketing utility currently normalizes from `Date`
- older repo behavior and tests are centered on `Date`

What is worth fixing:

- accept more common Java time inputs while keeping deterministic bucket output

Recommended expansion order:

1. `Instant`
2. `LocalDate`
3. `LocalDateTime`
4. `OffsetDateTime` / `ZonedDateTime`

Implementation direction:

- centralize conversion into one helper that normalizes supported time types
  into an instant-or-date-equivalent value before bucket formatting
- keep `TimeBucketPreset` as the owning timezone/week-start policy model

Expected repo fit:

- very good
- this removes a practical ergonomics limit without changing query semantics

Risk:

- low to medium
- `LocalDate` / `LocalDateTime` need clear zone semantics

### 5. Fluent Builder Mutability

Current behavior:

- fluent `QueryBuilder` is mutable
- it is not safe for concurrent mutation
- `copyOnBuild(true)` already snapshots execution state and is enabled by
  default

Why it exists:

- the builder is a configuration object, not an immutable query plan
- mutability keeps the fluent API lightweight

What is worth fixing:

- add an explicit immutable prepared fluent wrapper if the repo wants a
  reusable first-class fluent "template" contract

What is probably not worth fixing:

- making the mutable builder itself synchronized or deeply immutable by default

Recommended direction:

- keep the existing mutable builder
- if more reuse is desired, add something like:
  - `FluentTemplate`
  - `PreparedFluentQuery`
  - or a stronger `ReportDefinition.fluent(...)`-style promoted contract

Expected repo fit:

- medium
- useful if there is a real repeated-builder authoring need

Risk:

- medium
- this touches public API shape more than execution semantics

## Recommended Work Order

Recommended priority:

1. time-bucket input broadening
2. single-column grouped/aggregate subquery widening
3. bounded aggregate window frames
4. aggregate `ORDER BY` doc/diagnostic cleanup
5. immutable fluent prepared-wrapper design

Why this order:

- item 1 is high-value and relatively contained
- item 2 meaningfully expands SQL-like power without demanding full SQL-engine
  semantics
- item 3 is valuable but should follow the subquery/time-bucket wins
- item 4 is mostly clarity work
- item 5 is useful only if the repo wants another reusable wrapper family

## Non-Goals

This spike should not be read as a plan to add:

- correlated subqueries
- general nested SQL semantics
- full window-frame parity with SQL engines
- arbitrary temporal coercion rules with hidden timezone guessing
- a thread-safe mutable builder

## Adjacent Limits Not In README

These are real adjacent constraints, but they are not the core focus of this
spike unless explicitly pulled in later:

- natural `qualify` is still alias-based
- natural window phrasing is still intentionally narrow
- natural `schema(...)` is still structural, not vocabulary-resolved
- natural reusable presets are still not implemented
- natural execution still rebuilds a resolved delegate per execution/explain

## Recommendation

Treat this as a narrowing spike, not a "remove all limits" spike.

Recommended conclusion:

- fix the limits that expand real capability with bounded semantic cost
- keep the limits that preserve product clarity unless there is strong evidence
  they are blocking adoption
- prefer explicit new wrappers over hidden builder/concurrency magic

If this spike turns into execution work, the best first implementation slice is:

1. broaden time-bucket input types
2. then widen uncorrelated single-column grouped/aggregate subqueries

That gives the repo useful capability gains while staying aligned with its
current architecture.
