# WP-29 Design Note — Text, Null, and Date-Part Functions

Status: complete 2026-09-28 (all four slices implemented, uncommitted).

## 1. Goal

Expressions are numeric only today, so users cannot:

- compare case-normalized or trimmed text (`lower(email) = :email`)
- fall back across nullable fields (`coalesce(nickname, name)`)
- group or sort by a date part outside time buckets (`group by month(hireDate)`)

WP-29 adds typed (non-numeric) expressions and makes them usable in `SELECT`,
`WHERE`, `GROUP BY`, `ORDER BY`, and computed-field registries. The null rules from
WP-22 and the comparison owners from WP-23 stay unchanged.

## 2. Current State (verified 2026-09-28)

Expressions are **strings** that travel through the AST and the engine. They are
recognised by `SqlExpressionEvaluator.looksLikeExpression` (the text contains
`+ - * / ( )`) and always evaluated as `double`.

| Area | Today | File |
|---|---|---|
| Engine | Recursive-descent parser. It supports numbers, identifiers, `+ - * /`, unary sign, and `ABS/ROUND/FLOOR/CEIL[ING]`. Nodes return `double`, a null operand becomes `NaN` and maps back to `null`, and a non-`Number` value throws "must be numeric". The tokenizer rejects quotes. Parsed expressions are cached by text in Caffeine. | `sqllike/internal/expression/SqlExpressionEvaluator` |
| SELECT | `parseReferenceUntilSelectBoundary` marks any non-identifier span as computed. `AS` is required. Allowed only in non-aggregate queries. Values are computed during projection. | `SqlLikeParser`, `SqlLikeExecutionSupport.projectAliasedRows` |
| WHERE | The expression text becomes a `QueryRule` column. The row engine evaluates it for each row with `evaluateNumeric`. Text operators (`CONTAINS/MATCHES/LIKE` and their `NOT` forms) are rejected for expressions. | `FilterCore.matchesRule`, `SqlLikeValidator.ensureExpressionClauseSupported` |
| GROUP BY | Accepts identifiers only (`expectIdentifier`). | `SqlLikeParser.parseGroupBy` |
| ORDER BY | The parser captures an expression span, but validation rejects every expression. Non-aggregate queries require a known field. In aggregate queries, `validateAggregateOrderExpression` always throws. | `SqlLikeValidator.validateOrders` |
| HAVING / QUALIFY | Numeric expressions over grouped fields, aggregate outputs, or window outputs. | `SqlLikeValidator` |
| Registry | `ComputedFieldDefinition.of` requires a `Number` output type. Rows are materialized **before** WHERE/GROUP/ORDER, so registry fields behave like real columns everywhere, including aggregates (`sum(adjustedSalary)`). | `computed/*`, `FilterQueryBuilder.materializedRows`, `FilterImpl.join` |
| Fast path | Compiles registry definitions to a `double`-returning `BoundExpression`. | `FastArrayQuerySupport` |
| Types | A computed SELECT is reported as `Number` in the tabular schema but as `Double` for strict parameter typing (the two disagree). | `TabularSchemaSupport`, `SqlLikeParameterTypeValidator` |
| Typed / natural | No inline expressions. Registry names only (`TypedField.of(name, type)` and natural vocabulary). | `TypedQuery`, `NaturalQuery` |

Duplication to consolidate: `castNumericValue` exists in both `ComputedFieldSupport` and
`FastArrayQuerySupport`.

Other facts that constrain the design:

- Group and distinct keys compare with `Arrays.equals` (`filter/QueryKey`), so
  `Integer 0` and `Long 0` are **different** keys.
- `TimeBucketUtil` normalizes every supported temporal type into a zone. The default
  zone is UTC, and `ZonedDateTime`/`OffsetDateTime` are converted to an instant and
  then into the bucket zone.
- Hidden aggregate aliases already exist (`__having_expr_*`, `__order_expr_*` in
  `AggregateExpressionSupport`).

## 3. Decisions (proposed)

### D1. Semantics follow PostgreSQL

This is the same baseline WP-30 used. Where the proposal deviates, the deviation is
stated.

### D2. Function set v1

Function names are contextual: a name only counts as a function when `(` follows it,
so fields called `year` or `length` keep working. This matches the WP-30 statistical
functions.

| Group | Functions | Result |
|---|---|---|
| Text | `lower(s)`, `upper(s)`, `trim(s)`, `length(s)`, `substring(s, start[, count])`, `concat(a, b, ...)` | text; `length` → `Integer` |
| Null handling | `coalesce(a, b, ...)`, `nullif(a, b)` | common type of the arguments |
| Date parts | `year`, `quarter`, `month`, `day`, `hour`, `minute`, `day_of_week` — each `(value[, 'zone'])` | `Integer` |
| Numeric (existing) | `abs`, `round`, `floor`, `ceil`/`ceiling` | `Double` |

Literals inside expressions: numbers, `'text'` (with `''` as the escape, as the SQL-like
tokenizer already writes it), and `null`.

### D3. Null rules

- Every function returns `null` for a `null` argument, except the three below. This
  extends WP-22: a null result never matches a value comparison.
- `coalesce` returns its first non-null argument.
- `nullif(a, b)` returns `null` when `a = b` (compared through `ObjectUtil.compareObject`) and `a` otherwise.
- `concat` **skips** null arguments. This is PostgreSQL `concat()` behavior, not `||`.
  `concat()` with every argument null returns `''`.

### D4. Text rules

- `lower`/`upper` use `Locale.ROOT`. Caveat: `ILIKE` and `containsIgnoreCase` match
  through regex `(?siu)` in `internal/LiteralMatchPattern`. For a few characters (`ß`,
  Turkish dotted/dotless `i`), `lower(x) = lower(y)` can therefore disagree with
  `x ILIKE y`. The docs will say this; no change to the matchers.
- `trim` uses `String.strip()` (Unicode whitespace). PostgreSQL strips spaces only;
  this is a deliberate deviation for in-memory data.
- `length` and `substring` count Unicode code points. `substring` is 1-based and uses
  PostgreSQL window semantics: `substring('abc', 0, 2)` returns `'a'`.
- Text inputs are `CharSequence`, `Character`, or `Enum`. An enum is read through
  `name()`, the same form enum comparisons use. Any other type fails, as numeric
  inputs fail today.

### D5. Date-part rules

- Supported inputs are the time-bucket types in `TimeBucketUtil`: `Date`, `Instant`,
  `LocalDate`, `LocalDateTime`, `OffsetDateTime`, and `ZonedDateTime`.
- Zone handling reuses the bucket normalization. The default zone is UTC, and the
  optional zone argument works like the zone argument of `bucket(...)`.
- Invariant: `year(x, z)` equals the year component of `bucket(x, 'year', z)`, and the
  same holds for the other parts.
- `hour`/`minute` on a `LocalDate` return `0` (start of day), like the `'hour'` bucket.
- `day_of_week` is ISO: 1 = Monday through 7 = Sunday (PostgreSQL `isodow`).
- Only the function form is supported. `EXTRACT(YEAR FROM x)` is not, because `FROM`
  is a clause boundary for the SQL-like span scanner.
- Buckets and date parts differ in one way: buckets are labels that include the year
  (`2026-03`), while date parts are numbers that repeat every year (`3`, useful for
  seasonality). `docs/time-buckets.md` will explain this.

### D6. One Java type per expression column

Group keys are type-sensitive (see §2), so an expression column must never mix
`Integer` and `Long`. The engine therefore infers a static result type and converts
every result to it:

- Arithmetic returns `Double` (unchanged behavior).
- Date parts and `length` return `Integer`.
- Text functions return `String`.
- `coalesce`/`nullif` with numeric arguments: when every argument is one numeric
  class, the result is that class and literals are converted to it; otherwise the
  result is `Double`. With text or temporal arguments, every argument must be the
  same static kind or validation fails. Example: `coalesce(endDate, startDate)`
  returns a temporal value, and `TemporalComparison` compares it exactly as a field.
- A registry field's declared `outputType` wins. The existing conversion
  (`castNumericValue`, merged into one owner) is extended to `String` and `Integer`.

### D7. Static typing at validation

A small type lattice, `NUMBER | TEXT | TEMPORAL | ANY`, is inferred from the
queryable field types the validator already builds. `ANY` covers fields of unknown
type (for example `QueryRow` sources); those are checked at runtime, as today.

Known types enable:

- rejecting `lower(salary)`, `name * 2`, and mismatched `coalesce(...)` at validation
  time instead of at runtime
- allowing `CONTAINS/MATCHES/LIKE` (and their `NOT` forms) on `TEXT`/`ANY`
  expressions, which lifts the blanket expression restriction
- reporting the correct output type in tabular schema, strict parameter typing, and
  explain. This fixes the `Number` vs `Double` mismatch.

Error codes: validation failures reuse `EQ-SQL-VAL-009` (expression reference/operator)
and `EQ-SQL-VAL-007` (computed SELECT). No new codes are needed.

## 4. Architecture

### 4.1 One owner: extend `SqlExpressionEvaluator`

The evaluator stays in `sqllike/internal/expression`, which avoids package churn. It
becomes the single owner of expression parsing, typing, evaluation, and canonical
text:

- `Node.evaluate(...)` returns `Object`. A **numeric lane** is kept:
  `compileNumeric`/`BoundExpression.evaluate(Object[]) : double` stay for pure-numeric
  trees, so the existing computed-field, join, and HAVING benchmarks do not pay for
  boxing.
- Function tables behind the sealed `ExpressionFunction` interface (`ScalarFunction`,
  `DatePartFunction`) hold name → arity, argument kinds, result kind, and implementation.
  Adding a function then means adding one row. The old `FunctionNode` if-chains go away.
- `CompiledExpression.resultType(Map<String, Class<?>> fieldTypes)` does the static
  typing from D7. `canonical()` renders one normalized text (upper-case function names,
  single spacing). That text is used to match `GROUP BY`/`ORDER BY`/`DISTINCT`
  references to SELECT expressions, as `AggregateExpressionSupport.canonical` does for
  aggregates.
- The tokenizer learns string literals and `NULL`. `rewriteIdentifiers`, which join
  canonicalization and grouped aliases rely on, must skip literals.
- The owner of "temporal value → local date-time in zone" moves out of the private
  `TimeBucketUtil` normalizers into a shared helper that buckets and date parts both
  call. This keeps the D5 invariant true by construction.

The package layering is left as is: `filter/`, `computed/`, and `dsl/` import
`sqllike.internal.expression`. Moving the package to `internal/expression` is optional
cleanup and not part of WP-29.

### 4.2 Lowering per clause

| Clause | Mechanism | New work |
|---|---|---|
| `WHERE` | Existing expression-column rule. The row engine evaluates it with the general engine and compares through `ObjectUtil.compareObject`. | Swap `evaluateNumeric` for the typed evaluation; lift the text-operator ban for TEXT/ANY. Optional: compile once in the execution plan instead of a cache lookup per row. |
| `SELECT` (non-aggregate) | Existing projection-time evaluation. | Use the typed evaluation; apply the D6 output type. |
| `GROUP BY expr` / grouped `SELECT expr AS a` | Lower to a **query-scoped computed field**. The SELECT alias names it; a bare GROUP BY expression gets a hidden `__expr_<canonical>_<hash>` name. It is merged over the user registry, so grouping, `QueryKey`, and the fast/legacy engines only ever see a column. | Parser: parse `GROUP BY` as spans. Validator: allow computed SELECT in grouped queries when its alias or canonical text is grouped. Binder: build the merged registry. |
| `ORDER BY expr` | Same lowering: a selected alias if one matches, otherwise a hidden computed column. | Validator accepts expressions whose identifiers are allowed order fields. |
| Registry | `ComputedFieldDefinition` accepts non-numeric output types. The expression and output type are checked against source types at query validation, because the types are unknown at `of(...)` time. | The fast path gets a generic `Object` lane for non-numeric definitions. |
| Typed / natural | Unchanged. They reach functions through registry names (`TypedField.of("hireYear", Integer.class)`, natural vocabulary). | Docs examples only. |

Hidden `__expr_*` columns never appear in output. Explicit SELECTs already project only
the selected fields. For `SELECT *` with `ORDER BY lower(name)`, the hidden columns must
be removed before `QueryRow` output; covered by a test.

### 4.3 Interactions

- `SELECT DISTINCT`: `ORDER BY expr` must match a selected expression by canonical
  text or alias, which is the existing rule applied to expressions.
- Keyset cursors: values are a caller map keyed by the `ORDER BY` text
  (`SqlLikeCursor.of(Map)`). With WHERE placement, `SqlLikeKeysetSupport.predicateFields`
  already maps a select alias to its source expression. The cursor predicate is then an
  expression-column rule and works once slice 1 lands. An un-aliased `ORDER BY lower(name)`
  also works (key = expression text), but callers cannot read that value back from
  output rows. The docs will recommend selecting the expression with an alias. No new
  validation.
- HAVING over grouped fields such as `having lower(dept) = 'ops'` works through the same
  engine with no extra work. It needs tests only.
- Plan cache: expressions are part of the query text and AST strings, so cache keys do
  not change.
- Aggregates over expressions (`sum(price * qty)`) stay out of scope. The workaround,
  which already works, is to register the expression and aggregate its name.

## 5. Public API and Compatibility

- **No signature changes are planned.** japicmp should report 0 changes.
- `ComputedFieldDefinition.of(...)` now accepts non-numeric output types. Input that
  used to be rejected becomes valid; the Javadoc no longer says "numeric expression".
- AST (library surface, see `ai/core/agent-invariants.md`): no shape change.
  `QueryAst.groupByFields()` and `OrderAst.field()` may now hold expression text, and
  `SelectFieldAst.computedField()` can appear in grouped queries. This goes in
  `docs/public-api-stability.md` and `MIGRATION.md` as a content change.
- Behavior changes for MIGRATION:
  - Expressions over text fields used to fail at runtime ("must be numeric") and now
    evaluate. Arithmetic on text fails at validation instead of at runtime when the
    types are known.
  - The tabular-schema type of a computed SELECT changes from `Number` to the inferred
    type (for example `Double` or `String`).

## 6. Performance

- The numeric lane stays `double`-based, so existing computed-field paths must not
  regress. Guardrails:
  - `PojoLensJoinJmhBenchmark.pojoLensJoinLeftComputedField*`
  - `HotspotMicroJmhBenchmark.computedFieldJoinSelectiveMaterialization`
  - `SqlLikePipelineJmhBenchmark.parseAndFilterHavingComputed`
- Add `SqlLikePipelineJmhBenchmark` cases for `where lower(name) = :n` and
  `where name ilike :n`, so the generic lane's cost is tracked against the existing
  regex path.
- Removing the per-row Caffeine lookup for WHERE expressions (§4.2) is a possible
  improvement. Take it only if the benchmark shows a measurable gain.

## 7. Out of Scope (v1)

- `CASE WHEN`
- the `||` operator
- `EXTRACT(... FROM ...)`
- `CAST`
- regex or replace functions
- date arithmetic and `date_trunc` (buckets cover truncation)
- aggregates or windows over inline expressions
- function phrases in the natural grammar
- a typed expression builder

## 8. Delivery Slices

Each slice ships as its own commit with tests, a CHANGELOG entry, docs, and a full
`mvn -B -ntp test` run.

1. **Engine + WHERE/SELECT text and null functions.** Typed values, literals, the
   function table, and static types. Also: the one owner for output conversion,
   lifting the text-operator ban, and fixing the schema/parameter types. Docs:
   `docs/sql-like.md` gets a functions section. *Done 2026-09-28 (uncommitted).*
   Delivered as `ExpressionNode` (tree with a `value`/`number` pair per node),
   the function table (see slice 2), and `ExpressionTypes` (kinds and
   conversion), all behind `SqlExpressionEvaluator`. `SqlLikeValidator.validateForExecution`
   returns the computed SELECT types, and projection converts to them (D6).
   `compileNumeric` rejects text results, so `ComputedFieldDefinition` does too.
   Canonical text moved to slice 4, the first slice that uses it.
2. **Date parts.** The shared temporal normalizer and the bucket-parity tests (D5
   invariant). Docs: `docs/time-buckets.md` (date parts vs buckets). *Done 2026-09-28
   (uncommitted).* `ExpressionFunction` became a sealed interface with two tables:
   `ScalarFunction` (slice 1 functions) and `DatePartFunction` (one row per part, an
   extractor over `LocalDateTime`). A compile-time `prepare` hook parses the zone literal
   once into a `ZoneLiteral` node, reusing `TimeBucketPreset` zone parsing and its `UTC`
   default. `TimeBucketUtil.localDate`/`localDateTime` are the public normalizer.
   `DatePartBucketParityTest` checks the invariant over 3,000+ random instants,
   6 value types, and 6 zones. Grouping by a date part works now through a numeric
   registry field; inline `GROUP BY year(x)` is slice 4.
3. **Registry non-numeric outputs.** The fast-path generic lane, plus typed and natural
   usage through the registry. Docs: `docs/computed-fields.md`. Benchmark guardrails.
   *Done 2026-09-28 (uncommitted).* `ComputedFieldDefinition.of` checks the output type
   against the result type without fields (`SqlExpressionEvaluator.requireOutputType`),
   and `ComputedFieldSupport.augmentFieldTypes` checks each applicable definition
   against the source types. SQL-like queries wrap that failure as `EQ-SQL-VAL-009`.
   Numeric outputs keep the `double` lane; other outputs use the value lane
   (`BoundExpression.evaluateValue` on the fast join path) and
   `ComputedFieldSupport.outputValue`, which converts and verifies each value. Typed
   and natural queries read the fields by name with no changes. The planned
   `SqlLikePipelineJmhBenchmark` cases became a diagnostic `TextFunctionJmhBenchmark`
   in the semantics suite, because every `SqlLikePipelineJmhBenchmark.parseAndFilter*`
   method matches a threshold-gated main-suite regex.
4. **GROUP BY / ORDER BY expressions and grouped computed SELECT.** Query-scoped
   computed fields, hidden columns, and the DISTINCT and keyset rules. MIGRATION and
   public-api-stability notes. *Done 2026-09-28 (uncommitted).* Validation runs
   `SqlLikeExpressionFields.lower`, after join canonicalization and alias
   normalization, and returns the effective registry plus hidden names in
   `ValidatedQuery`. Execution binds that registry and forces row projection when hidden
   columns exist; `SELECT *` into `QueryRow` becomes an explicit field list. Choices
   made while building:
   - "Expression" means `SqlExpressionEvaluator.isScalarExpression`: every call is an
     expression function. So `sum(x)`, `count(*)`, and `sum(a) / 2` keep their existing
     aggregate paths and errors.
   - In grouped queries, only computed outputs over source fields become columns.
     Outputs over aggregate aliases keep the "non-aggregate only" error.
   - `ORDER BY` in a grouped query lowers only expressions that match a grouped
     expression.
   - A non-grouped `ORDER BY alias` still means the source field when the alias is
     also a field name, as before.
   - Subqueries reject these items, because `SqlLikeBinder.bind` runs without
     validation.
   - `canonical()` renders from the node tree: upper-case function names, `double`
     numbers, and every operation parenthesized.

Slices 1–3 can ship without slice 4. Slice 4 depends on slice 3, which makes
non-numeric registry fields possible.

## 9. Resolved Questions

All proposals were accepted on 2026-09-28:

1. PostgreSQL is the semantics baseline (D1).
2. `concat` skips nulls (D3).
3. `length`/`substring` count code points (D4).
4. `trim` uses `strip()` (D4).
5. Slice 4 stays the last slice of WP-29.
