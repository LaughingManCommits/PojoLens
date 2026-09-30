# WP-31 Design Note — `LAG`/`LEAD` and Windows over Grouped Rows

Status: complete 2026-09-30 (all three slices implemented, uncommitted).

## 1. Goal

Windows are rank (`ROW_NUMBER`/`RANK`/`DENSE_RANK`) and running aggregates
(`COUNT/SUM/AVG/MIN/MAX` with `ROWS` frames) only, and they are rejected together
with `GROUP BY`/metrics. Users cannot:

- compare a row with its neighbour (`salary - lag(salary)`, month-over-month deltas)
- rank or run totals over grouped output (`rank() over (order by total desc)` per
  department, running total of monthly sums)
- keep the top N groups with `QUALIFY`

## 2. Current State (verified 2026-09-30)

| Area | Today | File |
|---|---|---|
| Engine | Partitions rows, sorts each partition stably (source order breaks ties), then assigns rank values or runs an aggregate accumulator over the frame. Works on any `QueryRow` list by field name. | `filter/FluentWindowSupport` |
| Pipeline | Non-aggregate: `WHERE` → window → `QUALIFY` → `ORDER BY` → page → display projection. Aggregate: `WHERE` → aggregate → `HAVING` → `ORDER BY` → `DISTINCT` → page. `validateWindowShape` and `FluentQualifySupport.validate` reject windows/`QUALIFY` on aggregate shapes. | `filter/FilterImpl`, `filter/FluentQualifySupport` |
| Model | `WindowFunction` enum; `QueryWindow` (alias, function, value field, count-all, partitions, orders, frame). | `enums/WindowFunction`, `internal/builder/QueryWindow` |
| SQL-like | `tryParseWindowFunction` parses one identifier argument (or `*`), `OVER (PARTITION BY … ORDER BY … [ROWS …])`. `SelectFieldAst` carries the window parts. Validator rejects windows and `QUALIFY` in aggregate queries. Binder lowers to `addWindow`. | `SqlLikeParser`, `SqlLikeValidator`, `SqlLikeBinder` |
| Typed | `window(...)`/`windowCountAll(...)` lowered through the fluent builder after metrics. `validateWindowShape` rejects grouped shapes. | `dsl/TypedQuery` |
| Natural | `rank`/`dense rank`/`row number`/`running <agg> of` phrases rendered to SQL-like window text. | `natural/*` |

## 3. Decisions

### D1. Semantics follow PostgreSQL

Same baseline as WP-29/WP-30; deviations are stated.

### D2. `LAG`/`LEAD`

- Syntax: `lag(value[, offset[, default]]) over ([partition by …] order by …)`, same
  for `lead`. Function names stay contextual (a function only when `(` and `OVER`
  follow).
- `value` is a field reference (a source field, or a grouped output under D3).
- `offset` is a non-negative integer literal, default `1`; `0` is the current row.
  PostgreSQL also accepts negative offsets; PojoLens rejects them (use the other
  function) to keep one spelling per meaning.
- `default` is a literal (number, `'text'`, `true`/`false`, `null`); it is returned
  only when the offset row is outside the partition — a `null` value at the offset
  row stays `null` (PostgreSQL).
- `default` must fit the value type: a numeric default is converted to the value
  field's numeric type (a fractional default for an integral field is rejected),
  text needs `'text'`, booleans need `true`/`false`; other types accept only `null`.
- `ORDER BY` is required (as for every PojoLens window). A `ROWS` frame is rejected:
  offset functions ignore frames in PostgreSQL, so accepting one would mislead.
- Result type is the value field's type. Ties keep the stable source-order tiebreak.
- `offset`/`default` are literals only in v1 (no `:params`).

### D3. Windows over grouped rows

- Stage order: `WHERE` → `GROUP BY`/aggregate → `HAVING` → window → `QUALIFY` →
  `ORDER BY` → `DISTINCT` → `OFFSET`/`LIMIT` (SQL logical order).
- Window value, `PARTITION BY`, and window `ORDER BY` references must be grouped
  outputs: `GROUP BY` fields (or their `SELECT` aliases), time-bucket aliases, and
  aggregate aliases. Aggregate calls inside `OVER (…)` (`order by sum(x)`) are not
  supported in v1; reference the `SELECT` alias instead (PostgreSQL accepts both).
- Aggregate windows over grouped rows need numeric grouped outputs (metric aliases
  are numeric; group fields use the source type).
- `QUALIFY` keeps its contract on grouped queries: it filters window outputs.
  `HAVING` already filters grouped/aggregate outputs.
- Query `ORDER BY` on grouped queries may also use window aliases.
- Fast-stats and streaming paths already fall back when windows are present.

### D4. Surfaces

- SQL-like: D2 and D3.
- Typed: `lag(...)`/`lead(...)` methods; `window(...)` allowed with
  `groupBy`/metrics (grouped outputs referenced with `TypedField.of(alias, type)`).
- Natural: `previous|next <field> ... [for <n> rows] [defaulting to <value>]` for D2
  (only an item with `ordered by` starts the phrase, so fields named `previous`/`next`
  keep working). Grouped natural windows work through the shared SQL-like validation.

## 4. Public API and Compatibility (additive only)

- `WindowFunction` gains `LAG`, `LEAD`, and `isOffsetFunction()`;
  `isAggregateFunction()` stays `true` only for `COUNT/SUM/AVG/MIN/MAX`.
- `SelectFieldAst` gains `windowOffset()`, `windowDefault()`, and a
  `withWindowOffset(offset, default)` copy method; existing constructors are
  unchanged.
- `QueryWindow` gains an offset factory and accessors; `QueryBuilder` gains
  `addOffsetWindow(...)` (internal engine DSL).
- `TypedQuery` gains `lag`/`lead` (and `window(LAG|LEAD, ...)` short forms);
  `TypedPlanWindow` gains `offset()`/`defaultValue()` and a wider constructor.
- `PlanPreviewField` keeps its shape: the field text carries the offset arguments and
  `windowFrame()` is `null` for offset windows.

## 5. Out of Scope (v1)

- `FIRST_VALUE`/`LAST_VALUE`/`NTH_VALUE`, `NTILE`, `PERCENT_RANK`, `CUME_DIST`
- `RANGE`/`GROUPS` frames (no concrete use case yet)
- aggregate calls or expressions inside `OVER (…)` and as window arguments
- `:param` offsets/defaults

## 6. Resolved During Implementation

- Single owners: `WindowFunction.fromName` replaces five string classifiers
  (parser, validator, join resolution, natural, schema/parameter typing);
  `WindowExpressionText` renders canonical window text for joins and natural;
  `WindowOffsetDefaults` owns the default-fits-field rule; `QueryMetric.outputType`
  owns metric result types (now matching the engine: `SUM` over whole numbers is
  `Long`).
- Explain stage counts for grouped `QUALIFY` come from the live path: the `AGGREGATE`
  telemetry event is emitted after `HAVING` with `rowsAfterHaving` metadata.
- Keyset cursors on a window alias apply at `QUALIFY` on grouped queries too; other
  cursors keep the pre-window stage (documented caveat).
- `normalizeAggregationAliases` rewrites window references to `GROUP BY` select aliases
  into grouped source names (window text unchanged).

## 7. Delivery Slices

1. **LAG/LEAD engine + SQL-like** — `WindowFunction`, `QueryWindow`,
   `FluentWindowSupport`, builder; parser, AST, validator, binder, output types,
   join canonicalization, plan preview; tests.
2. **Windows over grouped rows** — engine stage after `HAVING`, `QUALIFY` on grouped
   rows; SQL-like validation/typing/keyset/explain; tests.
3. **Typed + natural + docs** — typed `lag`/`lead` and grouped windows, natural
   `previous`/`next`, docs, CHANGELOG, MIGRATION, benchmark smoke.
