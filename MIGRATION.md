# Migration Notes

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

