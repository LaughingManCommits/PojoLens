# TODO

## Current Goal

Close the everyday-filtering gaps on the primary SQL-like surface (and its natural
counterpart) identified in the 2026-09-27 docs-based feature audit
(`feature-audit.md`), then broaden output and aggregation coverage. New capability
lands in the shared engine first, then SQL-like, then natural where the controlled
grammar can express it. Keep PojoLens focused on the Java library, benchmarks,
release flow, docs, and repo-memory helpers.

Next priority: WP-33 (lazy typed `stream()`). WP-7 to WP-32 are unreleased;
a release cut (`RELEASE.md`) can happen before or after the P1 filtering packages.

### Quick fixes (no WP needed)

- [x] **README JDK requirement** — README says `JDK 17+`; root POM uses
  `<maven.compiler.release>25</maven.compiler.release>`. Update to Java 25,
  or intentionally lower the build target.
- [x] **Mixed-sort error text** — `TypedQuery.resolveGlobalSort()` tells
  callers to "Use SQL-like for mixed directions", but SQL-like has the same
  global-direction limit. Fix the error text to reflect reality.
- [x] **Repo-memory drift** — `ai/core/module-index.md` and
  `ai/core/architecture-map.md` mention a removed `PojoLens` facade;
  `ai/core/system-boundaries.md` says time buckets require `java.util.Date`.
  Scrub those files.

---

## Work Packages

### ~~WP-6 — Execution convenience methods on `TypedQuery`~~ ✓ DONE 2026-05-18

---

### ~~WP-7 — Case-insensitive string matching~~ ✓ DONE 2026-05-18

**Problem:** `TypedField.contains(value)` is case-sensitive. The single most
common reason callers fall back from typed to SQL-like. No `icontains`
equivalent exists on any surface today.

**Work:**
- Audit `FilterQueryBuilder` / `Clauses` for an existing case-insensitive path
- If engine supports it: add `Operator.CONTAINS_IGNORE_CASE`, wire lowering,
  expose `TypedField.containsIgnoreCase(String)` and
  `TypedPredicate.containsIgnoreCase(field, value)`
- If engine does not: implement as a scan-level post-filter in `TypedQuery`
  (apply `String.toLowerCase` on both sides before match)
- Consider whether SQL-like/natural should get an equivalent operator or a
  documented `MATCHES`-based recipe alongside the typed implementation
- Add contract tests (parity with `filter(…).stream().filter(…)` reference)
- Update `docs/typed.md`

---

### ~~WP-8 — `stream()` lazy execution on `TypedQuery`~~ ✓ DONE 2026-05-18

**Problem:** `TypedQuery.filter()` is eager and returns a `List<T>`. SQL-like
and natural both expose `stream()`. Callers doing downstream `flatMap`,
`collect`, or early-exit patterns pay for full materialisation unnecessarily.

**Work:**
- `TypedQuery.stream(List<T>)` → `Stream<T>`
- `TypedQuery.stream(DatasetBundle)` → `Stream<T>`
- `TypedQuery.stream(List<T>, JoinBindings)` → `Stream<T>`
- `TypedQuery.stream(List<T>, JoinBindings, Class<P>)` → `Stream<P>`
- Thin wrapper over `filter(…).stream()` for now; document laziness caveat
- Add stable-surface assertions and basic stream-chain tests
- Update `docs/typed.md`

---

### ~~WP-9 — `TypedPredicate.any()` / `.none()` sentinels~~ ✓ DONE 2026-05-18

**Problem:** Building conditional predicate chains without null guards requires
boilerplate. Spring Data `Specification.where(null)`, jOOQ `trueCondition()`,
and QueryDSL `BooleanBuilder` all solve this with sentinel values.

**Work:**
- `TypedPredicate.any()` — always-true predicate; lowers to no WHERE clause
- `TypedPredicate.none()` — always-false predicate; returns zero rows
- Add `Operator.ANY` / `Operator.NONE` or handle as special cases in lowering
- Add contract tests: `any()` returns all rows, `none()` returns empty,
  `pred.and(any())` ≡ `pred`, `pred.or(none())` ≡ `pred`
- Update `docs/typed.md`

---

### ~~WP-10 — `computedFields(ComputedFieldRegistry)` on `TypedQuery`~~ ✓ DONE 2026-05-18

**Problem:** SQL-like and Natural both expose `.computedFields(registry)`.
TypedQuery is the only entry point that cannot attach one, forcing callers to
switch surfaces just for computed fields.

**Work:**
- Add `TypedQuery.computedFields(ComputedFieldRegistry registry)` fluent method
- Store in builder state; apply via `builder.computedFields(registry)` in
  `applyToBuilder`
- Add `hasComputedFields()` / `computedFieldRegistry()` accessors
- Add stable-surface assertion and a basic computed-field filter test
- Update `docs/typed.md`

---

### ~~WP-11 - TODO-work audit follow-up hardening~~ ✓ DONE 2026-05-18

**Problem:** The 2026-05-18 TODO-work audit found that WP-7 through WP-10 are
mostly complete, but the follow-up work should be closed as one hardening PR
before starting new feature packages.

**Work:**
- Fix WP-10 ordering in `TypedQuery.applyToBuilder(...)`: apply
  `builder.computedFields(computedFieldRegistry)` before any step that validates
  or uses computed fields (`where`, time buckets, group by, metrics, HAVING,
  windows, QUALIFY, schema/explain paths as applicable).
- Add typed computed-field metric coverage:
  `computedFields(...).groupBy(...).metric(computedField, Metric.SUM, alias)`.
- Add typed computed-field HAVING coverage, including a grouped query that
  filters by a computed-field-derived metric/alias.
- Normalize WP-9 sentinels in `TypedPredicate.allOf(...)` and `anyOf(...)`:
  `allOf(pred, any()) -> pred`, `allOf(pred, none()) -> none()`,
  `anyOf(pred, none()) -> pred`, and `anyOf(pred, any()) -> any()`.
- Add contract tests for static-factory sentinel combinations, not only
  instance `and(...)` / `or(...)` combinations.
- Update `StablePublicApiContractTest` to lock the public methods added in
  WP-7 through WP-10: `containsIgnoreCase`, `any`, `none`, `stream(...)`,
  `computedFields(...)`, `hasComputedFields()`, and
  `computedFieldRegistry()`.
- Refresh stale hot memory after the fix: `ai/state/handoff.md` must say
  WP-7 through WP-10 are done and WP-11 is the next priority.
- Update `CHANGELOG.md` with a WP-11 hardening entry.

**Validation:**
- `mvn -B -ntp test`
- `scripts/docs/check-doc-consistency.ps1`
- If `ai/**` changes: `scripts/ai/refresh-ai-memory.ps1` and
  `scripts/ai/refresh-ai-memory.ps1 -Check`

---

### ~~WP-12 — `filterPage()` / `PageResult<T>` on `TypedQuery`~~ ✓ DONE 2026-05-19

**Problem:** SQL-like exposes `filterPage(…)` → `PageResult<T>`. TypedQuery
has `limit()` and `offset()` but no `filterPage()`, so callers run two queries
manually. Also: assess whether Natural should gain `filterPage()` for parity.

**Work:**
- `TypedQuery.filterPage(List<T>)` → `PageResult<T>`
- `TypedQuery.filterPage(DatasetBundle)` → `PageResult<T>`
- `TypedQuery.filterPage(List<T>, JoinBindings)` → `PageResult<T>`
- Reuse existing `PageResult` from the `sqllike` package or extract to shared
- Run filtered-count pass (no limit/offset) + paginated-result pass
- Consider `NaturalQuery.filterPage(…)` for cross-surface pagination parity
- Add contract tests: total reflects unfiltered count, rows respect limit/offset
- Update `docs/typed.md` (and `docs/natural.md` if natural gets it)

---

### ~~WP-13 — `TimeBucket.HOUR` granularity~~ ✓ DONE 2026-05-19

**Problem:** `TimeBucket` has DAY → YEAR but no HOUR. Event-stream workloads
routinely bucket by hour. `ObjectUtil` already parses `DATE_HOUR` and
`DATE_MINUTE` formats, suggesting the engine can handle sub-day granularity.

**Work:**
- Check `TimeBucketUtil` and `FilterCore` for existing HOUR/MINUTE support
- If supported: add `TimeBucket.HOUR` (and `MINUTE` if stable) to the enum
- Verify `TimeBucketPreset`, `QueryTimeBucket`, and `TimeBucketAggregationTest`
  handle the new granularity correctly
- Add a contract test: bucket by HOUR groups rows into correct hour slots
- Update `docs/typed.md`

---

### ~~WP-14 — Mixed-direction sort (engine-level)~~ ✓ DONE 2026-06-06

**Problem:** The engine enforces one global sort direction. This blocks common
ordering (`department ASC, salary DESC`) and weakens keyset cursor expressions.
`TypedSortOrder` models per-field direction at the descriptor level, but
`resolveGlobalSort()` rejects mixed configurations at execution time. SQL-like
has the same limitation.

**Work:**
- Audit `OrderEngine`, `FilterCore`, and `FilterQueryBuilder` for what changes
  are needed to preserve per-field direction through execution
- If feasible: implement per-field direction in the engine and remove
  `resolveGlobalSort()` mixed-direction rejection from `TypedQuery`
- Update SQL-like lowering and parser to pass per-field direction if engine gains it
- Fix keyset cursor metadata to reflect per-field directions
- Update `docs/sql-like.md`, `docs/typed.md`, and the SQL-like mixed-direction
  error message
- If not feasible in one pass: at minimum fix the misleading error text (see
  Quick fixes above) and document the limitation clearly on all surfaces

---

### ~~WP-15 — `ReportDefinition.typed(…)` reusable typed workflow~~ ✓ DONE 2026-06-06

**Problem:** `ReportDefinition` and `SavedReport` wrap SQL-like and natural
queries for reusable report definitions, schema review, replay, and validation.
`TypedQuery` has no equivalent entry point — callers must hold a raw
`TypedQuery<?>` reference and re-execute it manually.

**Work:**
- Add `ReportDefinition.typed(TypedQuery<?> query, Class<?> projectionClass)`
  factory (or a `TypedReportDefinition` adapter preserving immutability)
- Keep `SavedReport` text-only unless a serialization-safe typed descriptor is
  introduced separately
- Wire into `SavedReportCatalogValidator` if applicable
- Add contract test: typed report definition executes against a data source
  and returns the correct projection
- Update `docs/typed.md` or `docs/output-helpers.md` with a usage example

---

### ~~WP-16 — `NaturalQuery.filterPage(…)` pagination parity~~ ✓ DONE 2026-05-19

**Problem:** SQL-like has `filterPage()` / `PageResult<T>` but natural queries
do not, despite natural being a first-class endpoint/query-studio surface.

**Work:**
- Add `NaturalQuery.filterPage(List<?> rows, Class<T> cls)` → `PageResult<T>`
- Add `NaturalQuery.filterPage(DatasetBundle, Class<T>)` → `PageResult<T>`
- Resolve the natural query to SQL-like and delegate to SQL-like `filterPage`
- Add contract tests mirroring SQL-like pagination behaviour
- Update `docs/natural.md`

---

### ~~WP-17 — Typed diagnostics / plan preview~~ ✓ DONE 2026-06-06

**Problem:** SQL-like and natural have `diagnostics()` and `QueryDiagnostics`
for no-data pre-execution review. TypedQuery exposes only `explain(rows)` and
`schema(rows)`, both of which require data. There is no pre-execution typed
plan review that reports referenced fields, joins, windows, limits, guard
policy, or projection issues.

**Work:**
- Add `TypedQuery.diagnostics()` → a typed plan/review object (could reuse
  `QueryDiagnostics` or introduce `TypedPlanPreview`)
- Should work without a data source (no rows argument); read builder state
  directly
- Report: referenced field names, joins, group keys, metrics, windows,
  sort orders, limit/offset, guard policy, time buckets, computed fields
- Add contract tests: diagnostics reflects configured query shape
- Update `docs/typed.md`

---

### ~~WP-18 — File loader `Reader`/`InputStream` overloads~~ ✓ DONE 2026-09-27

**Problem:** `PojoLensFiles` accepts only `Path`. This is inconvenient for
classpath resources, in-memory uploads, object-store streams, and tests that
already hold a `Reader` or `InputStream`.

**Work:**
- Add `PojoLensFiles.csv(Reader reader, CsvOptions options, Class<T> cls)`
  and equivalent for TSV, JSON, and JSONL
- Preserve `Path`-based overloads as the primary convenience API
- Include synthetic source-name metadata in `CsvLoadReport` / `JsonLoadReport`
  so diagnostics remain useful when no path is available
- Add contract tests using `StringReader` as a source
- Update `docs/onboarding.md` or the relevant data-loading guide

---

### ~~WP-19 — `startsWith` / `endsWith` on typed and SQL-like~~ ✓ DONE 2026-09-27

**Problem:** Natural maps starts-with/ends-with phrases to `MATCHES` regex
under the hood. Typed and SQL-like expose only `contains()`/`matches()`.
Callers who want prefix/suffix matching must write regex patterns by hand.

**Work:**
- Add `TypedField.startsWith(String)` → `TypedPredicate` backed by a
  `MATCHES` lowering with `^` prefix anchor
- Add `TypedField.endsWith(String)` with `$` suffix anchor
- Add `TypedPredicate.startsWith(field, value)` and `.endsWith(field, value)`
  static factories
- Consider SQL-like `STARTS_WITH(field, value)` / `ENDS_WITH(field, value)`
  function syntax or just document the `MATCHES` recipe
- Add contract tests and natural parity coverage
- Update `docs/typed.md`

---

### ~~WP-20 — Literal-pattern correctness fixes~~ ✓ DONE 2026-09-27

**Problem:** Natural `starts with :param` / `ends with :param` bound the value
as a raw full-match regex (acted like equality). Typed `containsIgnoreCase`
missed matches across line breaks and did not fold non-ASCII case.

**Done:** internal `LiteralMatchPattern` owns all literal-to-regex lowering;
natural pattern parameters bind through `PatternParameterValue` and lower at
execution; `containsIgnoreCase` uses `(?siu)`.

---

### ~~WP-21 — `TypedQuery.iterator(...)`~~ ✓ DONE 2026-09-27

**Problem:** Last typed execution-parity gap from the feature audit; SQL-like
exposes `iterator(...)`, typed did not.

**Done:** four overloads mirroring `stream(...)`.

---

### ~~WP-22 — Core engine correctness pass~~ ✓ DONE 2026-09-27

**Problem:** A reproduced bug hunt across comparison, ordering, aggregation,
grouping, paging, joins, and projection found ~25 silent wrong-result defects.

**Done:** see `CHANGELOG.md` `[Unreleased]` Fixed/Changed (WP-22). Regression
coverage in `CoreComparisonSemanticsTest` and `CoreEngineRegressionTest`.

---

### ~~WP-23 — Core follow-ups deferred from WP-22~~ ✓ DONE 2026-09-27

Reproduced but deferred because each needs a design decision or a larger change:

- **Date precision:** comparisons normalize to the configured format (whole
  seconds by default), so sub-second instants compare equal and keyset paging on
  a `Date`/`Instant` ORDER BY field without a tie-breaker skips rows. Decide
  whether the default should compare temporal values exactly.
- **Keyset over aggregates/aliases:** the cursor is lowered into the
  pre-aggregation WHERE, so `ORDER BY` an aggregate alias fails on page 2, rows
  with null sort values vanish after page 1, and `java.time`/enum cursor values
  cannot be tokenized (EQ-SQL-CUR-004).
- **Typed field validation:** `TypedField.of("typo")` silently matches nothing;
  SQL-like reports EQ-SQL-VAL-001. Needs join/computed/alias-aware validation.
- **Records and `final` fields:** not part of the query schema; projection
  would need canonical-constructor support.
- **Smaller:** SQL `count(field)` is rejected with a fluent-API message; the
  internal fluent `filterGroups` external key joins parts with an unescaped
  `,`; a natural field named `a`/`an`/`the` is stripped as a filler word; the
  HOUR bucket label is ambiguous in a DST fall-back hour.

**Done:** hybrid date precision (values exact, literals at written precision;
user decision); keyset cursor lands in WHERE/HAVING/QUALIFY with null-aware
predicates and null/`java.time`/enum/UUID tokens; typed field validation (skipped
for joins); records as sources/targets (final fields in regular classes stay out,
documented); `COUNT(field)`; group-key escaping; filler-word fix; HOUR DST
behavior documented. Coverage in `CoreFollowUpTest`.

---

### ~~WP-24 — Literal `IN` / `NOT IN` lists~~ ✓ DONE 2026-09-27

**Problem:** SQL-like `IN` requires a subquery (`IN currently requires a subquery
starting with SELECT`), and natural `is in` only accepts `is in query ... end query`.
`where department in ('Engineering', 'Finance')` is one of the most common filters
and only works today through a bound list parameter. Typed already has `in(...)`.

**Work:**
- SQL-like: parse `field IN (literal, literal, ...)` and `field NOT IN (...)`; keep
  `IN (select ...)` and `IN :listParam` working
- Lower to the existing engine `IN` / list `!=` semantics (null elements never
  match, per WP-22)
- Natural: `is one of A, B, C` and `is not one of ...` (decide separator rules for
  multi-word values and quoting)
- Plan preview, explain, diagnostics, and lint handle literal lists
- Tests across SQL-like, natural, and typed parity; update `docs/sql-like.md`,
  `docs/natural.md`

---

### ~~WP-25 — `IS [NOT] NULL`, `BETWEEN`, and general `NOT`~~ ✓ DONE 2026-09-27

**Problem:** SQL-like only offers `= null` / `!= null`, has `BETWEEN` for window
frames but not in `WHERE`, and only supports `NOT` before `EXISTS`. Natural has no
null or range phrase. Typed has `isNull()`, `between(...)`, and `not()`.

**Work:**
- SQL-like: `field IS NULL`, `field IS NOT NULL`, `field [NOT] BETWEEN a AND b`
  (inclusive, like typed), and `NOT (<predicate or group>)` in `WHERE`/`HAVING`
- Lower `NOT` with the typed DeMorgan rules; negations the engine cannot express
  yet (text matching) wait for WP-26 and fail with an actionable message until then
- Natural: `is empty` / `is not empty` (or `is missing`), `is between X and Y`,
  and a negation form for grouped conditions
- Tests and docs as in WP-24

**Done:** SQL-like `IS [NOT] NULL`, `[NOT] BETWEEN` (lowered to `>=`/`<=`), `NOT`
before a predicate or group in `WHERE`/`HAVING`/`QUALIFY`, rewritten by the shared
`FilterExpressionNegation` (typed `not()` rules). Natural `is [not] between`,
parenthesized groups, and `not (...)`; the existing `is null` / `is not null`
phrases cover the null case (no `is empty` alias: it would read as empty text).
Negating `CONTAINS`/`MATCHES`/prefix/suffix or an `IN` subquery fails at parse time
until WP-26. Plan preview reports null tests as `IS [NOT] NULL` (pushdown
fallback). Coverage in `NullRangeNegationFilterTest`.

---

### ~~WP-26 — Negated text matching in the engine~~ ✓ DONE 2026-09-27

**Problem:** The engine has no negated `CONTAINS` / `MATCHES`, so typed
`NOT(CONTAINS | CONTAINS_IGNORE_CASE | MATCHES | STARTS_WITH | ENDS_WITH)` throws,
SQL-like has no `NOT CONTAINS`, and natural has no `does not contain`.

**Work:**
- Add negated text-match support to the shared engine (new `Clauses` constants or a
  negation flag on rules; `Clauses` is a public enum, so review compatibility and
  document that constants may grow)
- Null fields keep WP-22 semantics (a null field never matches a comparison)
- Typed: make the five `NOT(...)` text cases lower instead of throwing
- SQL-like `NOT CONTAINS` / `NOT MATCHES`; natural `does not contain`,
  `does not start with`, `does not end with`
- Remove the "not supported" notes from `docs/typed.md`

**Done:** new `Clauses.NOT_CONTAINS` / `NOT_MATCHES` (appended; ordinals unchanged;
`Clauses` Javadoc and `public-api-stability.md` say constants may grow), evaluated in
`ObjectUtil.compareObject` as the complement over non-null text (lists: none match;
invalid regex never matches). SQL-like `NOT CONTAINS` / `NOT MATCHES`; `NOT (...)`
over text now lowers. Natural `does not contain / start with / end with`. Typed
`not()` keeps `NOT(text leaf)` and lowers it to the negated clause, so no new
`TypedPredicate.Operator` constants. Coverage in `NegatedTextMatchTest`.

---

### ~~WP-27 — `LIKE` / `ILIKE`~~ ✓ DONE 2026-09-27

**Problem:** SQL users expect `LIKE 'Al%'`. Today prefix/suffix/contains in SQL-like
need the documented `MATCHES` regex recipe, and SQL-like/natural have no
case-insensitive text matching at all (typed has `containsIgnoreCase`).

**Work:**
- SQL-like `field [NOT] LIKE 'pattern'` and `ILIKE` (case-insensitive), `%` and `_`
  wildcards, optional `ESCAPE` character
- Lower through `internal.LiteralMatchPattern` to `MATCHES` (quote literal runs,
  `(?s)`, Unicode case folding for `ILIKE`); `NOT LIKE` uses WP-26
- Natural: consider `contains ... ignoring case` for the case-insensitive gap
- Replace the `MATCHES` recipe in `docs/sql-like.md` with `LIKE` examples
- Reserved-word review: `LIKE` / `ILIKE` / `ESCAPE` as new keywords

**Done:** `internal.LikePattern` (record implementing the new `internal.TextPattern`,
also implemented by `LiteralMatchPattern`) lowers `%`/`_`/escape to a quoted `(?s)` or
`(?siu)` regex; `[NOT] LIKE|ILIKE` lowers to `MATCHES`/`NOT_MATCHES`, and `:param`
patterns lower at bind time through `PatternParameterValue`. Reserved-word review:
`LIKE`/`ILIKE`/`ESCAPE` stay contextual identifiers (recognized only before a
pattern/parameter/`NULL`, or a string after a pattern), so no field names break.
Default escape is backslash (PostgreSQL/MySQL); `ESCAPE ''` disables. Natural
`... ignoring case` on contains/starts with/ends with and `does not` forms (new
`STARTS_WITH_IGNORE_CASE`/`ENDS_WITH_IGNORE_CASE`); other phrases reject it. Plan
preview reports `MATCHES`/`NOT MATCHES`. Coverage in `LikeFilterTest`.

---

### ~~WP-28 — `SELECT DISTINCT` and `COUNT(DISTINCT field)`~~ ✓ DONE 2026-09-27

**Problem:** The engine can de-duplicate rows (internal `addDistinct`, value-based
keys since WP-22), but SQL-like, natural, and typed cannot request it, and there is
no distinct-count aggregate.

**Work:**
- SQL-like `SELECT DISTINCT ...`; typed `distinct()`; natural phrase (`show distinct`)
- `COUNT(DISTINCT field)` metric in the shared aggregation (grouped, global, and fast
  stats paths) with SQL-like/typed/natural exposure
- Decide DISTINCT interaction with ORDER BY, LIMIT, and keyset paging

**Done:** engine `QueryBuilder.distinctRows()` (the old `addDistinct` stays a
pre-filter DISTINCT ON by key fields) dedups projected rows with `GROUP BY` key
semantics after ORDER BY and before OFFSET/LIMIT (fast array/stats/stream paths opt
out; no top-N sort window). Decisions: ORDER BY must use selected outputs (SQL rule,
`EQ-SQL-VAL-012`); DISTINCT + GROUP BY requires all group fields selected (then a
no-op); `filterPage` totals and keyset cursors work on distinct rows. SQL-like
`SELECT DISTINCT` (contextual word), typed `distinct()`, natural `show distinct`.
`Metric.COUNT_DISTINCT` in grouped, global, and fast stats paths plus
`ReportComparisons`; SQL-like `count(distinct x)` in SELECT/HAVING/ORDER BY (canonical
text via `AggregateExpressionSupport.canonical`), typed `countDistinct`, natural
`count of distinct`; rejected in windows and inside other aggregates. Coverage in
`DistinctQueryTest`.

---

### ~~WP-29 — Text and date functions~~ ✓ DONE 2026-09-28

**Problem:** Computed fields and select expressions are numeric only
(`SqlExpressionEvaluator`), so there is no `LOWER`/`UPPER`/`TRIM`/`COALESCE` and no
date-part extraction (`year(hireDate)`), which blocks case-normalized comparisons
and date-part grouping outside time buckets.

**Work:**
- Design typed (non-numeric) expressions: string functions, `COALESCE`, date parts
- Allow them in `SELECT`, `WHERE`, `GROUP BY`, `ORDER BY`, and computed-field
  registries; keep null propagation (WP-22)
- Largest item on the list: start with a design note before implementation

**Design:** `docs/design/wp-29-expression-functions.md` (decisions accepted
2026-09-28): four slices (engine + text/null functions, date parts, non-numeric
registry outputs, GROUP BY/ORDER BY expressions).

**Progress:** slice 1 done 2026-09-28 (uncommitted): `lower`, `upper`, `trim`,
`length`, `substring`, `concat`, `coalesce`, `nullif`, text/null literals, typed
validation, and computed SELECT output types. Coverage in `TextFunctionQueryTest` and
`SqlExpressionEvaluatorTest`. Slice 2 done 2026-09-28 (uncommitted): `year`, `quarter`,
`month`, `day`, `hour`, `minute`, `day_of_week` with an optional zone, sharing the
bucket normalization (`DatePartFunctionTest`, `DatePartBucketParityTest`,
`DatePartQueryTest`). Slice 3 done 2026-09-28 (uncommitted): computed fields with
`String`, date/time, and enum outputs, type-checked at definition and query time
(`NonNumericComputedFieldTest`, fast path in `FilterImplFastPathTest`). Slice 4 done
2026-09-28 (uncommitted): expressions in `GROUP BY`/`ORDER BY` and grouped computed
outputs, lowered to query-scoped computed columns (`SqlLikeExpressionFields`,
`ExpressionGroupOrderQueryTest`).

---

### ~~WP-30 — Statistical aggregates~~ ✓ DONE 2026-09-27

**Problem:** Metrics are limited to COUNT/SUM/AVG/MIN/MAX.

**Work:**
- `MEDIAN`, `PERCENTILE(field, p)`, `STDDEV` / `VARIANCE` (population vs sample
  decision) through `filter/NumericAccumulator` and the fast-stats path
- SQL-like, typed `metric(...)`, and natural phrases; window variants only if cheap

**Done:** `Metric` gains `MEDIAN`, `PERCENTILE`, `STDDEV`, `STDDEV_POP`, `VARIANCE`,
`VAR_POP`, computed by `internal.NumericStatistics` (double values, Welford variance,
sorted-copy percentiles) in grouped, global, and fast-stats paths plus
`ReportComparisons`. Decisions: `STDDEV`/`VARIANCE` are sample (PostgreSQL; `null` for
one value), `_POP` divide by `n`, `*_SAMP` are SQL-like aliases; `PERCENTILE` =
`percentile_cont` with a 0-1 fraction carried as a metric argument (`QueryMetric`,
`MetricPlan`, `SelectFieldAst.metricArgument`, plan-cache shape; canonical text
`percentile(x, 0.9)`); results are `Double`. Window variants skipped (not cheap:
windows use a separate accumulator); rejected at parse time. Function names are
contextual, not reserved. Coverage in `StatisticalAggregateTest`.

---

### ~~WP-31 — Window gaps: `LAG`/`LEAD` and windows on grouped queries~~ ✓ DONE 2026-09-30

**Problem:** Window functions are rank and aggregate windows only, and cannot be
combined with `GROUP BY`/metrics in the same query.

**Work:**
- `LAG(field[, offset[, default]])` / `LEAD(...)` over the existing partition/order
  machinery
- Allow windows over grouped rows (window stage after aggregation); `QUALIFY` for
  grouped queries
- Reconsider `RANGE` frames only if a concrete use case appears

**Done:** design note `docs/design/wp-31-window-gaps.md` (PostgreSQL semantics).
`WindowFunction.LAG`/`LEAD` run in `FluentWindowSupport` (offset + default, default only
outside the partition, no frame); `WindowOffsetDefaults` owns the default-fits-field
rule. SQL-like `lag/lead(field[, offset[, default]])`, typed `lag`/`lead`, natural
`previous`/`next ... [for <n> rows] [defaulting to <value>]`. Windows and `QUALIFY` run
over grouped rows after `HAVING` (`FilterImpl.havingOrderAndPage`), referencing group
fields, bucket aliases, and metric aliases; aggregate calls inside `OVER` are rejected.
`RANGE` frames stay out. Coverage in `WindowOffsetQueryTest`, `GroupedWindowQueryTest`,
`TypedWindowGapsTest`, `NaturalOffsetWindowTest`; 1494 runtime tests.

---

### ~~WP-32 — Typed field validation for joined queries~~ ✓ DONE 2026-09-30

**Problem:** WP-23 typed field-name validation skips queries with joins because
joined field names come from the bindings at execution time, so typos in joined
typed queries still match nothing silently.

**Work:**
- Validate at execution once join bindings are known (reuse SQL-like join
  resolution naming), or validate against declared join source classes
- `diagnostics()` reports the same error when join source classes are available

**Done:** `TypedQuery.rowFieldTypes` merges the entity's fields with each joined source's
through the new single owner `internal/JoinFieldNames` (the `child_` rule, formerly four
copies in `JoinEngine`, `FilterQueryBuilder`, and `SqlLikeJoinResolution`), and checks join
keys (an unknown key used to skip the join). Classes come from the bound rows; the new
`join(sourceName, sourceClass, ...)` overload declares them so `diagnostics()` and
`planPreview()` validate without data, and a declared class must match the bound rows.
`QueryRow`/map rows and empty lists without a declared class skip validation. Coverage in
`TypedJoinValidationTest`; 1501 runtime tests.

---

### WP-33 — Lazy typed `stream()`  [P3]

**Problem:** `TypedQuery.stream(...)` and `iterator(...)` materialise the full result
before streaming (`docs/typed.md` laziness caveat), so early-exit consumers pay for
full execution.

**Work:**
- Reuse the SQL-like/fluent streaming path (`StreamingExecutionJmhBenchmark`) for
  simple typed shapes (filter, order with limit, projection); keep materialising
  for grouped/window shapes
- Benchmark with the streaming suite; update the laziness caveat

---

## Working Rules

- Do not add unrelated services, runtime infrastructure, or extra subsystems.
- Each WP ships as its own commit with a `CHANGELOG.md` entry under
  `[Unreleased]`.
- Run `mvn -B -ntp test` before marking a WP done.
- After any `docs/**` change run `scripts/docs/check-doc-consistency.ps1`.
- After any `ai/**` change run `scripts/ai/refresh-ai-memory.ps1` and
  `scripts/ai/refresh-ai-memory.ps1 -Check`.

---

## Done Recently

- [x] `2026-05-18`: WP-1 — `TypedPredicate.not()` lowered via DeMorgan; 1141 tests.
- [x] `2026-05-18`: WP-2 — `contains()` / `matches()` on TypedField and TypedPredicate; 1153 tests.
- [x] `2026-05-18`: WP-3 — `TypedSortOrder` + `orderBy(TypedSortOrder...)` vararg; 1160 tests.
- [x] `2026-05-18`: WP-4 — `TypedQuery.timeBucket(…)` wired to engine; 1165 tests.
- [x] `2026-05-18`: WP-5 — `TypedField.between(lo, hi)` / `TypedPredicate.between(…)`; 1171 tests.
- [x] `2026-05-18`: WP-6 — `count`, `exists`, `findFirst`, `findOne` on TypedQuery; 1183 tests.
- [x] `2026-05-18`: WP-7 — `containsIgnoreCase` on TypedField/TypedPredicate; lowers to `MATCHES(?i)`; 1191 tests.
- [x] `2026-05-18`: WP-8 — `stream()` overloads on TypedQuery (4 overloads, wraps `filter`); 1197 tests.
- [x] `2026-05-18`: WP-9 — `TypedPredicate.any()` / `none()` sentinels with identity/absorption laws; 1211 tests.
- [x] `2026-05-18`: WP-10 — `computedFields(ComputedFieldRegistry)` on TypedQuery; 1215 tests.
- [x] `2026-05-18`: WP-11 — typed-surface hardening for computed-field ordering and sentinel laws; 1223 tests.
- [x] `2026-05-19`: WP-12 — `TypedQuery.filterPage(...)` offset pages with `PageResult.totalRows()`.
- [x] `2026-05-19`: WP-13 — `TimeBucket.HOUR` across fluent, SQL-like, natural, and typed paths.
- [x] `2026-05-19`: WP-16 — `NaturalQuery.filterPage(...)` delegates to SQL-like page helper.
- [x] `2026-06-06`: WP-14 — mixed-direction ORDER BY executes through fluent, typed, and SQL-like paths.
- [x] `2026-09-27`: WP-18 — `Reader`/`InputStream` overloads on `PojoLensFiles` and `runtime.files()`; reports gain `sourceName()`.
- [x] `2026-09-27`: WP-19 — typed `startsWith`/`endsWith` via shared `(?s)` MATCHES pattern owner; natural multi-line fix; SQL-like recipe documented.
- [x] `2026-09-27`: WP-20 — natural prefix/suffix parameters bind as literals; `containsIgnoreCase` multi-line + Unicode fix.
- [x] `2026-09-27`: WP-21 — `TypedQuery.iterator(...)` overloads.
- [x] `2026-09-27`: WP-22 — core engine correctness pass (comparison, precision, grouping, paging, joins, projection); 1299 runtime tests.
- [x] `2026-09-27`: WP-24 — literal `IN`/`NOT IN` lists and list parameters on SQL-like; natural `is [not] one of`.
- [x] `2026-09-27`: WP-25 — SQL-like `IS [NOT] NULL`, `[NOT] BETWEEN`, `NOT`; natural `is [not] between`, groups, `not (...)`; 1341 runtime tests.
- [x] `2026-09-30`: WP-32 — typed field validation for joined queries (merged join naming, join keys, declared join source classes); 1501 runtime tests.
- [x] `2026-09-30`: WP-31 — `LAG`/`LEAD` and windows/`QUALIFY` over grouped rows on SQL-like, typed, natural, fluent; 1494 runtime tests.
- [x] `2026-09-27`: WP-30 — MEDIAN, PERCENTILE, STDDEV/STDDEV_POP, VARIANCE/VAR_POP on SQL-like, typed, natural; 1393 runtime tests.
- [x] `2026-09-27`: WP-28 — `SELECT DISTINCT` (SQL-like, typed, natural) and `COUNT(DISTINCT field)`; 1383 runtime tests.
- [x] `2026-09-27`: WP-27 — SQL-like `[NOT] LIKE`/`ILIKE` with `ESCAPE`; natural `ignoring case`; 1367 runtime tests.
- [x] `2026-09-27`: WP-26 — negated text matching: `Clauses.NOT_CONTAINS`/`NOT_MATCHES`, SQL-like `NOT CONTAINS`/`NOT MATCHES`, natural `does not ...`, typed `not()`; 1354 runtime tests.
- [x] `2026-09-27`: WP-23 — date precision, keyset over aliases/nulls, typed field validation, records, `COUNT(field)`; 1314 runtime tests.
