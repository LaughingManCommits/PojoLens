# Migration Notes

## Upgrading From 2026.05.18.1353

The next release fixes a set of silent wrong-result defects in the core engine.
Most upgrades need no code changes, but some queries now return different, correct
results. Review these before upgrading:

Results that change:
- **`!=` / `ne` exclude null fields.** A null field never matches a comparison with
  a value on any query shape. Compound SQL-like queries and typed `ne(...)` or
  `eq(...).not()` previously included null rows. To keep them, add
  `or field = null` (SQL-like) or `.or(FIELD.isNull())` (typed).
- **Date/time precision.** Two date/time values (typed arguments, bound parameters,
  keyset cursors) now compare exactly; previously both were cut to whole seconds.
  Text literals compare at the precision they are written: `'2024-01-02'` covers the
  whole day, `'2024-01-02 10:00:00'` that second. ISO-8601 literals are accepted.
- **Grouping keys.** `GROUP BY` and `DISTINCT` keep `null`, `''`, and `'<NULL>'`
  apart, group `LocalDate` values per day (all values previously fell into one
  group), and keep sub-second timestamps distinct. Group counts can change.
- **Empty aggregates.** An aggregate without `GROUP BY` returns one row over empty
  input (`count(*) = 0`, other aggregates `null`) instead of no rows.
- **Large numbers.** Values above 2^53 now compare, sort, and sum exactly.
  `SUM` over whole numbers that exceeds the `long` range throws
  `ArithmeticException` instead of returning a saturated total. Window `SUM` over
  whole-number fields returns `Long` (previously `Double`).
- **Joins.** `LocalDate` join keys match by day (previously every pair joined),
  numeric keys match across types (`int` vs `long`), and null keys never match.
- **Keyset paging.** `keysetBefore(...)` returns the page immediately before the
  cursor (previously the first page). Rows with null sort values are now reached,
  and `filterPage(...)` no longer throws `EQ-SQL-PAG-003` when the last row has a
  null sort value: the cursor carries the null.
- **Plan preview null tests.** `= null` / `!= null` filters preview as `IS NULL` /
  `IS NOT NULL` and fall back in pushdown preview (previously a pushable `=` / `!=`).
- **Natural prefix/suffix parameters.** `name starts with :p` treats the bound value
  as literal text (previously as a full-match regex, which behaved like equality).

Stricter validation:
- **Typed field names.** `TypedQuery` rejects a field name the entity does not have
  (`IllegalArgumentException` with suggestions) instead of silently matching
  nothing. Queries with joins are not checked.

Wider schemas:
- Inherited fields of user-defined superclasses, `BigDecimal`, `BigInteger`,
  `UUID`, `LocalTime`, collection, and array fields are now part of the query
  schema. `select *` results and projections carry them, and generated typed-field
  classes gain matching constants.
- Records work as source rows and as result classes.

New, additive:
- `PojoLensFiles` / `runtime.files()` accept `Reader` and `InputStream` sources;
  load reports gain `sourceName()`.
- Typed `startsWith`, `endsWith`, `iterator(...)`; SQL `COUNT(field)`; keyset
  paging over select, aggregate, and window aliases.
- SQL-like literal `IN ('a', 'b')` / `NOT IN (...)` lists and `IN :values` list
  parameters; natural `is one of` / `is not one of`.
- SQL-like `IS [NOT] NULL`, `[NOT] BETWEEN`, and `NOT`; natural `is [not] between`,
  parenthesized groups, and `not (...)` (natural parentheses were previously rejected).
- Negated text matching: `Clauses.NOT_CONTAINS` / `Clauses.NOT_MATCHES`, SQL-like
  `NOT CONTAINS` / `NOT MATCHES`, natural `does not contain / start with / end with`.
  Typed `not()` over string predicates now runs instead of throwing
  `UnsupportedOperationException`. `Clauses` gained two constants: a `switch` over
  `Clauses` without a `default` branch needs the new cases.
- Statistical aggregates: `Metric.MEDIAN`, `PERCENTILE`, `STDDEV`, `STDDEV_POP`,
  `VARIANCE`, `VAR_POP` (SQL-like functions, typed `percentile(...)`, natural phrases).
  `Metric.requiresNumericField()` is now true for every metric except `COUNT` and
  `COUNT_DISTINCT`.
- `SELECT DISTINCT` / typed `distinct()` / natural `show distinct`, and
  `COUNT(DISTINCT field)` via the new `Metric.COUNT_DISTINCT` (a `switch` over `Metric`
  without a `default` branch needs the new case).
- SQL-like `[NOT] LIKE` / `[NOT] ILIKE` with `ESCAPE`; natural `... ignoring case` on
  contains / starts with / ends with. `LIKE`, `ILIKE`, and `ESCAPE` are not reserved,
  so fields with those names keep working.

See `CHANGELOG.md` for the complete list.

## Maven Coordinates

Published coordinates now use GitHub namespace style:

- old: `laughing.man.commits:pojo-lens`
- new: `io.github.laughingmancommits:pojo-lens`

Update your dependency declarations accordingly.

## Facade Removal

If you are upgrading from older unpublished or pre-public-release builds, treat the
explicit owning types below as the supported path now.

`PojoLens` is removed from the public surface.

Replacement map:
- `PojoLens.newQueryBuilder(rows)` ->
  `PojoLensSql.parse(queryText).filter(rows, Projection.class)`
- `PojoLens.parse(queryText)` ->
  `PojoLensSql.parse(queryText)`
- `PojoLens.template(queryText, params...)` ->
  `PojoLensSql.template(queryText, params...)`
- `PojoLens.toChartData(rows, spec)` ->
  `PojoLensChart.toChartData(rows, spec)`
- `PojoLens.newRuntime()` ->
  `new PojoLensRuntime()`
- `PojoLens.newRuntime(preset)` ->
  `PojoLensRuntime.ofPreset(preset)`
- `PojoLens.newKeysetCursorBuilder()` ->
  `SqlLikeCursor.builder()`
- `PojoLens.parseKeysetCursor(token)` ->
  `SqlLikeCursor.fromToken(token)`
- `PojoLens.report(sqlQuery, projectionClass, ...)` ->
  `ReportDefinition.sql(sqlQuery, projectionClass, ...)`
- `PojoLens.report(projectionClass, configurer, ...)` ->
  `ReportDefinition.sql(...)` or `ReportDefinition.natural(...)`
- `PojoLens.bundle(...)` ->
  `DatasetBundle.of(...)`
- `PojoLens.compareSnapshots(currentRows, previousRows)` ->
  `SnapshotComparison.builder(currentRows, previousRows)`

## Advanced Helper Narrowing

`laughing.man.commits.chart.validation.ChartValidation` is no longer a
supported public helper contract.

Migration direction:
- remove direct imports/usages of `ChartValidation`
- use `PojoLensChart.toChartData(...)`, `Filter.chart(...)`,
  `SqlLikeQuery.chart(...)`, `ChartMapper`, or `ChartResultMapper`
  instead
- treat chart validation as internal runtime behavior rather than an API you
  call directly

## SQL-like Execution Explain Alignment

`SqlLikeQuery.explain(rows, projectionClass)` stage counts now come from the
same live bound execution path used by normal SQL-like execution.

Behavior note:
- `where`, `group`, `having`, `qualify`, and `order` counts now reflect the
  unpaged live execution stages
- `limit` reflects the final `OFFSET`/`LIMIT` window

Migration direction:
- refresh explain snapshots or assertions that depended on the old
  explain-only replay behavior, especially around `HAVING` or `QUALIFY`
  queries without `ORDER BY`

## Runtime-First Cache Policy

If you were tuning caches through static/global entry points, move that code
onto a `PojoLensRuntime`.

Before:

```java
PojoLens.setSqlLikeCacheMaxEntries(1024);
PojoLens.setStatsPlanCacheExpireAfterWriteMillis(30_000L);
```

After:

```java
PojoLensRuntime runtime = new PojoLensRuntime();
runtime.sqlLikeCache().setMaxEntries(1024);
runtime.statsPlanCache().setExpireAfterWriteMillis(30_000L);
```

Replacement direction:
- SQL-like cache controls and snapshots ->
  `runtime.sqlLikeCache().*`
- stats-plan cache controls and snapshots ->
  `runtime.statsPlanCache().*`

Current cache-policy state:
- public static/global cache policy methods are removed from `PojoLens`
- public static/global cache policy methods are removed from `PojoLensSql`
- the public `FilterExecutionPlanCache` compatibility facade is removed
- the default singleton caches remain internal implementation details for the
  direct non-runtime entry points

## Explicit Entry Points (Core / SQL / Chart)

Query and chart entry live on explicit types:
- `PojoLensSql.parse(...)`
- `PojoLensNatural.parse(...)`
- `PojoLensChart.toChartData(...)`

Use these directly if you want explicit dependency boundaries in your application modules.

## Instance-Scoped Runtime Caches

If you need DI/test isolation or per-tenant cache policy, use:

```java
PojoLensRuntime runtime = new PojoLensRuntime();
runtime.sqlLikeCache().setMaxEntries(1024);
runtime.statsPlanCache().setMaxEntries(2048);
```

The static/global cache-policy APIs have been removed. Direct non-runtime entry
points still use internal default singleton caches, but public tuning now lives
on `PojoLensRuntime`.

## Logging Facade Migration

Runtime logging moved from `commons-logging` to `slf4j`:
- remove `commons-logging` adapters from app-level dependency management if they were only present for PojoLens
- wire your preferred SLF4J backend (`logback-classic`, `slf4j-simple`, etc.) at application level

## SQL-like Public Query API

SQL-like:

```java
List<Foo> rows = PojoLensSql
    .parse("select stringField, integerField where stringField = 'abc' order by integerField desc limit 2")
    .filter(source, Foo.class);
```

Migration guidance:
- For user-authored/config-defined queries, prefer SQL-like input.
- For guided non-SQL text, use `PojoLensNatural`.
- For reusable flows, use `ReportDefinition.sql(...)` or
  `ReportDefinition.natural(...)`.
- `ChartQueryPreset` / `ChartQueryPresets` and `StatsViewPreset` /
  `StatsViewPresets` remain public as advanced convenience sugar; prefer
  `ReportDefinition` unless the chart-first or table-first preset itself is the
  thing you want to reuse.
- SQL-like validation is strict: unknown or `@Exclude` fields are rejected.
- Current SQL-like support includes a single `JOIN` (`INNER`, `LEFT`, `RIGHT`), aggregate functions, `GROUP BY`, and date bucketing via `bucket(dateField,'...')`.
- Current SQL-like support includes `HAVING` for grouped/aggregated queries (`AND`/`OR`).
- Current SQL-like supports uncorrelated `WHERE ... IN (select ...)`
  subqueries, including grouped/aggregate output aliases and subquery `JOIN`
  clauses backed by `JoinBindings`.
- Current SQL-like supports bounded uncorrelated `WHERE EXISTS (select ...)`
  and `WHERE NOT EXISTS (select ...)` subqueries.
- SQL-like chained joins are supported when each `JOIN ... ON ...` references the current plan or qualifies the previous source explicitly.
- Correlated, scalar, and broad nested SQL subquery plans are still unsupported.

## SQL-like Typed Bind-First Execution

Bind-first SQL-like execution uses the typed path that removes repeated
`Class` arguments.

Example:

```java
List<Employee> rows = query
    .bindTyped(source, Employee.class)
    .filter();
```

Join-aware typed bind is also supported:

```java
JoinBindings joinBindings = JoinBindings.of("employees", employees);

List<Company> rows = query
    .bindTyped(companies, Company.class, joinBindings)
    .filter();
```

## SQL-like Named Parameters

SQL-like queries now support named parameters so you can avoid string concatenation.

Example:

```java
List<Employee> rows = PojoLensSql
    .parse("where department = :dept and salary >= :minSalary and active = :active")
    .params(Map.of("dept", "Engineering", "minSalary", 120000, "active", true))
    .filter(source, Employee.class);
```

Validation behavior:
- Missing parameters fail with `Missing SQL-like parameter(s): [...]`.
- Extra parameters fail with `Unknown SQL-like parameter(s): [...]`.
- Executing without binding required parameters fails with unresolved-parameter guidance.

## SQL-like Typed Join Bindings

Public SQL-like multi-source execution now accepts `JoinBindings` only.

Direct usage:

```java
JoinBindings joinBindings = JoinBindings.of("employees", employees);
List<Company> rows = query.filter(companies, joinBindings, Company.class);
```

Boundary adaptation from an existing map:

```java
JoinBindings joinBindings = JoinBindings.from(joinSources);
List<Company> rows = query.filter(companies, joinBindings, Company.class);
```

## HAVING Design Note

This section defines the SQL-like `HAVING` behavior.

Scope:
- `HAVING` applies to grouped/aggregated query results, not raw source rows.
- Evaluation order is: `WHERE` -> `GROUP BY/AGG` -> `HAVING` -> `ORDER BY` -> `LIMIT`.

Allowed in `HAVING`:
- aggregate expressions (`COUNT(*)`, `SUM(field)`, `AVG(field)`, `MIN(field)`, `MAX(field)`)
- aggregate aliases from `SELECT`
- grouped fields

Not allowed in the current release:
- non-grouped, non-aggregated fields

Validation expectations:
- `HAVING` without grouped/aggregate context is rejected.
- unknown, ambiguous, or illegal references are rejected with deterministic error text.
- `HAVING` must appear after `GROUP BY` and before `ORDER BY`/`LIMIT`.

## Chart Data Design Note

This section defines the chart-data contract.

Policy:
- `PojoLensChart` will map query rows to chart payloads.
- `PojoLens` will not implement native image/chart rendering.
- external chart libraries are allowed in tests/examples only (test scope dependencies).

Current scope:
- chart types: `BAR`, `LINE`, `PIE`, `AREA`, `SCATTER`
- source paths: existing rows and SQL-like or natural query results
- output: typed chart payload (`ChartData` + datasets)

Public models:
- `ChartType`
- `ChartSpec`
- `ChartData`
- `ChartDataset`

`ChartSpec` fields:
- required: `type`, `xField`, `yField`
- optional: `seriesField`, `title`, `xLabel`, `yLabel`, `dateFormat`, `sortLabels`, `stacked`, `percentStacked`, `nullPointPolicy`

Validation expectations:
- missing required fields are rejected deterministically.
- unknown fields are rejected.
- y-field values must be numeric and non-null.
- null rows are skipped.
- empty inputs produce empty chart payloads.
- `PIE` does not support `seriesField`.
- `percentStacked` requires `stacked=true`.
- stacked modes require multi-series and currently support `BAR`/`AREA` only.

API entry points:
- `PojoLensChart.toChartData(List<T>, ChartSpec)`
- `SqlLikeQuery.chart(List<?>, Class<T>, ChartSpec)`
- `SqlLikeQuery.chart(List<?>, JoinBindings, Class<T>, ChartSpec)`
- `NaturalQuery.chart(List<?>, Class<T>, ChartSpec)`

## Builder Public Surface Cleanup

Mutable builder implementation types were removed from the public surface.
The old internal state mutator methods are not public API:
- `setLimit`
- `setGroupFields`
- `setOrderFields`
- `setDistinctFields`
- `setFilterValues`
- `setFilterFields`
- `setFilterClause`
- `setFilterSeparator`
- `setFilterDateFormats`
- `setFilterIDs`
- `setJoinClasses`
- `setJoinMethods`
- `setJoinParentFields`
- `setJoinChildFields`
- `setReturnFields`

Use SQL-like, natural, report, chart, schema, and runtime APIs instead.
Internal pipeline state is managed only inside the execution engine.

