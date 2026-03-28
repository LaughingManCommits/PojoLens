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
  `PojoLensCore.newQueryBuilder(rows)`
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
  `ReportDefinition.fluent(projectionClass, configurer, ...)`
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

If you were tuning caches through `PojoLens`, `PojoLensCore`, or
`PojoLensSql`, move that code onto a `PojoLensRuntime`.

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
- public static/global cache policy methods are removed from `PojoLensCore`
- the public `FilterExecutionPlanCache` compatibility facade is removed
- the default singleton caches remain internal implementation details for the
  direct non-runtime entry points

## Typed Selector API

You can replace string field names with method references for compile-time safety.

Before:

```java
PojoLensCore.newQueryBuilder(rows)
    .addRule("stringField", "abc", Clauses.EQUAL)
    .addOrder("integerField", 1)
    .addField("stringField")
    .initFilter()
    .filter(Foo.class);
```

After:

```java
PojoLensCore.newQueryBuilder(rows)
    .addRule(Foo::getStringField, "abc", Clauses.EQUAL)
    .addOrder(Foo::getIntegerField)
    .addField(Foo::getStringField)
    .initFilter()
    .filter(Foo.class);
```

`addOrder(...)` and `addGroup(...)` now support optional indexless overloads.
When no index is provided, priority follows insertion order.

Additional typed overloads now available:
- `addDistinct(Foo::getField)`
- `addRule(Foo::getDateField, value, clause, separator, dateFormat)`
- `addHaving(ResultRow::getMetricAlias, value, clause, separator, dateFormat)`
- `addJoinBeans(Parent::getId, children, Child::getParentId, Join.LEFT_JOIN)`

## Explicit Entry Points (Core / SQL / Chart)

Query and chart entry live on explicit types:
- `PojoLensCore.newQueryBuilder(...)`
- `PojoLensSql.parse(...)`
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

## Explicit Rule Groups

For clearer semantics on complex boolean logic, prefer grouped rules:

```java
import static laughing.man.commits.builder.QueryRule.of;

PojoLensCore.newQueryBuilder(rows)
    .allOf(
        of(Foo::getStringField, "abc", Clauses.EQUAL),
        of(Foo::getIntegerField, 10, Clauses.BIGGER_EQUAL)
    )
    .anyOf(
        of(Foo::getIntegerField, 20, Clauses.EQUAL),
        of(Foo::getIntegerField, 30, Clauses.EQUAL)
    )
    .initFilter()
    .filter(Foo.class);
```

Evaluation model:
- `allOf` groups: each group uses AND across its rules, and any one matching group satisfies the allOf side.
- `anyOf` groups: each group uses OR across its rules, and any one matching group satisfies the anyOf side.
- Final match: `(allOf satisfied) AND (anyOf satisfied)`.

## Join API

Use:
- `addJoinBeans(parentField, childBeans, childField, Join.LEFT_JOIN|RIGHT_JOIN|INNER_JOIN)`

Removed API:
- `addJoin(String, List<QueryRow>, String, Join)` is no longer available (internal row type API).

## Thread-Safe Execution Snapshot

You can safely reuse a configured template builder across threads by enabling
copy-on-build snapshots:

```java
QueryBuilder template = PojoLensCore.newQueryBuilder(rows)
    .addRule(Foo::getIntegerField, 10, Clauses.BIGGER_EQUAL)
    .copyOnBuild(true);

List<Foo> result = template.initFilter().filter(Foo.class);
```

## Fluent API and SQL-like API

You can now choose either style for currently supported operations (`SELECT`, `WHERE`, `ORDER BY`, `LIMIT`).

Fluent:

```java
List<Foo> rows = PojoLensCore.newQueryBuilder(source)
    .addRule("stringField", "abc", Clauses.EQUAL)
    .addOrder("integerField", 1)
    .limit(2)
    .initFilter()
    .filter(Sort.DESC, Foo.class);
```

SQL-like:

```java
List<Foo> rows = PojoLensSql
    .parse("select stringField, integerField where stringField = 'abc' order by integerField desc limit 2")
    .filter(source, Foo.class);
```

Migration guidance:
- For user-authored/config-defined queries, prefer SQL-like input.
- For compile-time safety and complex Java-side composition, keep fluent API.
- SQL-like validation is strict: unknown or `@Exclude` fields are rejected.
- Current SQL-like support includes a single `JOIN` (`INNER`, `LEFT`, `RIGHT`), aggregate functions, `GROUP BY`, and date bucketing via `bucket(dateField,'...')`.
- Current SQL-like support includes `HAVING` for grouped/aggregated queries (`AND`/`OR`).
- Current SQL-like supports limited `WHERE ... IN (select oneField ...)` subqueries.
- SQL-like chained joins are supported when each `JOIN ... ON ...` references the current plan or qualifies the previous source explicitly.
- Aggregate, grouped, and joined subquery plans are still unsupported.

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
- source paths: fluent results and SQL-like results
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
- `Filter.chart(Class<T>, ChartSpec)`
- `Filter.chart(Sort, Class<T>, ChartSpec)`
- `SqlLikeQuery.chart(List<?>, Class<T>, ChartSpec)`
- `SqlLikeQuery.chart(List<?>, JoinBindings, Class<T>, ChartSpec)`

## Builder Public Surface Cleanup

`FilterQueryBuilder` internal state mutator methods were removed from the public surface:
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

Use the fluent `QueryBuilder` methods instead (`addRule`, `addGroup`, `addOrder`, `addDistinct`, `addField`, `limit`, joins, metrics, time buckets). Internal pipeline state is now managed only inside the execution engine.

