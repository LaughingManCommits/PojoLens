# PojoLens
From `List<T>` to chart-ready insights, without a database.

`PojoLens` is a POJO-first in-memory query engine for Java. It gives teams a clean
alternative to complex Streams for filtering, grouping, and metrics, with built-in
chart payload mapping.

## Why PojoLens

- POJO-first model: query your existing domain classes directly.
- Two query styles: SQL-like for dynamic input, typed fluent API for Java composition.
- Chart pipeline built in: execute query logic and map to `ChartData` in one flow.
- Optional chart/query presets for common dashboard shapes.
- One runtime: filtering, ordering, grouping, HAVING, time buckets, and deterministic multi-join support.

## Installation

```xml
<dependency>
  <groupId>laughing.man.commits</groupId>
  <artifactId>pojo-lens</artifactId>
  <version>1.0.0</version>
</dependency>
```

Requirements:
- JDK 17+

## Quick Start (Chart-Ready SQL-like)

```java
ChartData chart = PojoLens
    .parse("select department, count(*) as total group by department order by total desc")
    .chart(source, DepartmentCount.class, ChartSpec.of(ChartType.BAR, "department", "total"));
```

## Quick Start (Chart Preset)

```java
ChartQueryPreset<DepartmentHeadcount> preset = ChartQueryPresets
    .categoryCounts("department", "headcount", DepartmentHeadcount.class);

ChartData chart = preset.chart(source);
```

## Quick Start (Reusable Report Definition)

```java
ReportDefinition<DepartmentCount> report = PojoLens.report(
    PojoLens.parse("select department, count(*) as total group by department order by department asc"),
    DepartmentCount.class,
    ChartSpec.of(ChartType.BAR, "department", "total"));

List<DepartmentCount> rows = report.rows(sourceSnapshot);
ChartData chart = report.chart(sourceSnapshot);
```

## Quick Start (Dataset Bundle)

```java
DatasetBundle bundle = PojoLens.bundle(
    companies,
    JoinBindings.of("employees", employees));

List<Company> rows = PojoLens
    .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
    .filter(bundle, Company.class);
```

## Quick Start (Computed Field Registry)

```java
ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
    .add("adjustedSalary", "salary * 1.1", Double.class)
    .build();

List<AdjustedSalaryRow> rows = PojoLens
    .parse("select name, adjustedSalary where adjustedSalary >= 120000 order by adjustedSalary desc")
    .computedFields(registry)
    .filter(source, AdjustedSalaryRow.class);
```

## Quick Start (Snapshot Comparison)

```java
SnapshotComparison<Employee, Integer> comparison = PojoLens
    .compareSnapshots(currentSnapshot, previousSnapshot)
    .byKey(employee -> employee.id);

List<SnapshotDeltaRow<Employee, Integer>> deltas = comparison.rows();
List<ChangeSummary> changed = PojoLens
    .parse("select keyText, changedFieldCount where changeType = 'CHANGED'")
    .filter(deltas, ChangeSummary.class);
```

## Quick Start (Fluent)

```java
List<Employee> results = PojoLens.newQueryBuilder(source)
    .addRule("department", "Engineering", Clauses.EQUAL)
    .addOrder("salary", 1)
    .limit(10)
    .initFilter()
    .filter(Sort.ASC, Employee.class);
```

## Quick Start (SQL-like)

```java
List<Employee> results = PojoLens
    .parse("select name, salary where department = 'Engineering' and active = true order by salary desc limit 10")
    .filter(source, Employee.class);
```

## Quick Start (SQL-like Parameters)

```java
List<Employee> rows = PojoLens
    .parse("where department = :dept and salary >= :minSalary and active = :active order by salary desc")
    .params(Map.of("dept", "Engineering", "minSalary", 120000, "active", true))
    .filter(source, Employee.class);
```

## Bind-First (SQL-like, Typed)

```java
SqlLikeQuery query = PojoLens.parse("where salary >= 90000 order by salary asc");

List<Employee> rows = query
    .bindTyped(source, Employee.class)
    .filter();
```

## Runtime Presets

```java
PojoLensRuntime runtime = PojoLens.newRuntime(PojoLensRuntimePreset.DEV);
```

Available presets:
- `DEV`: diagnostics-friendly defaults (`strictParameterTypes` + `lintMode`, cache stats enabled)
- `PROD`: lower-overhead cache defaults with diagnostics toggles off
- `TEST`: caches disabled, strict validation/lint enabled

All preset values can still be overridden manually afterward.

Typed join bindings are available for SQL-like joins:

```java
JoinBindings joinBindings = JoinBindings.of("employees", employees);

List<Company> rows = PojoLens
    .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
    .filter(companies, joinBindings, Company.class);
```

Chained SQL-like joins are supported as long as each new `JOIN ... ON ...` references the current plan and the new child source explicitly when needed:

```java
List<Company> rows = PojoLens
    .parse("select * from companies "
            + "left join employees on companies.id = employees.companyId "
            + "left join badges on employees.id = badges.employeeId "
            + "where code = 'A1'")
    .filter(companies, JoinBindings.builder()
            .add("employees", employees)
            .add("badges", badges)
            .build(), Company.class);
```

## Developer Recipes

Use these as copy/paste starting points:
- parameterized filters: `parse(...:param...).params(...).filter(...)`
- lint diagnostics: `parse(...).lintMode().lintWarnings()`
- alias projections: `select a as b ...`
- nested paths: `where address.city = 'Amsterdam'` / `addRule("address.city", ...)`
- telemetry hooks: `runtime.setTelemetryListener(listener)` / `builder.telemetry(listener)`
- typed bind-first execution: `bindTyped(...).filter()`
- typed join bindings: `filter(..., JoinBindings.of(...), ...)`
- dataset bundles: `PojoLens.bundle(primaryRows, JoinBindings.of(...))`
- computed fields: `ComputedFieldRegistry.builder().add(...).build()`
- snapshot comparison: `PojoLens.compareSnapshots(current, previous).byKey(...)`
- explicit calendar presets: `TimeBucketPreset.week().withZone(...).withWeekStart(...)`
- grouped HAVING queries: `group by ... having ...`
- chart payload mapping: `query.chart(..., ChartSpec.of(...))`
- chart/report presets: `ChartQueryPresets.categoryCounts(...)`
- reusable report definitions: `PojoLens.report(...).rows(...)` / `.chart(...)`
- tabular schema metadata: `query.schema(...)`, `report.schema()`, `preset.schema()`
- regression fixtures: `QuerySnapshotFixture` + `QueryRegressionFixture`
- generated field constants: `FieldMetamodelGenerator.generate(...).writeTo(...)`

Anti-patterns to avoid:
- building SQL-like strings via concatenation for dynamic values
- repeating projection class and sort in bind-first SQL-like flows
- using ad-hoc join-source string maps when `JoinBindings` can be used
- rebuilding the same multi-source execution context when `DatasetBundle` can carry the snapshot once

## Best Fit

- Dashboard/reporting backends over in-memory, cached, or service-aggregated data.
- Teams that want a simpler filtering/aggregation path than verbose Streams chains.
- Projects needing both user-authored query text and typed Java query composition.

## Public API

Entry points:
- `PojoLens.newQueryBuilder(List<?>)`
- `PojoLens.parse(String)`
- `PojoLens.bundle(List<?>)`
- `PojoLens.bundle(List<?>, JoinBindings)`
- `PojoLens.compareSnapshots(List<T>, List<T>)`
- `PojoLens.report(...)`
- `ComputedFieldRegistry.builder()`
- `TimeBucketPreset.of(TimeBucket)`
- `PojoLens.newRuntime()` (instance-scoped runtime with isolated caches)
- `PojoLens.newRuntime(PojoLensRuntimePreset)`
- `FieldMetamodelGenerator.generate(Class<?>)`
- `SqlLikeQuery.schema(Class<?>)`
- `QuerySnapshotFixture.of(...)`
- `QueryRegressionFixture.sql(...)`
- `PojoLensCore.newQueryBuilder(List<?>)` (core-only entry point)
- `PojoLensSql.parse(String)` (SQL-like entry point)
- `PojoLensChart.toChartData(List<T>, ChartSpec)` (chart mapping entry point)

SQL-like cache controls:
- `PojoLens.setSqlLikeCacheEnabled(boolean)`
- `PojoLens.setSqlLikeCacheStatsEnabled(boolean)`
- `PojoLens.setSqlLikeCacheMaxEntries(int)`
- `PojoLens.setSqlLikeCacheMaxWeight(long)`
- `PojoLens.setSqlLikeCacheExpireAfterWriteMillis(long)`
- `PojoLens.getSqlLikeCacheMaxEntries()`
- `PojoLens.getSqlLikeCacheMaxWeight()`
- `PojoLens.getSqlLikeCacheExpireAfterWriteMillis()`
- `PojoLens.clearSqlLikeCache()`
- `PojoLens.resetSqlLikeCacheStats()`
- `PojoLens.getSqlLikeCacheHits()`
- `PojoLens.getSqlLikeCacheMisses()`
- `PojoLens.getSqlLikeCacheSize()`
- `PojoLens.getSqlLikeCacheEvictions()`
- `PojoLens.getSqlLikeCacheSnapshot()`

Stats plan cache controls:
- `PojoLens.setStatsPlanCacheEnabled(boolean)`
- `PojoLens.setStatsPlanCacheStatsEnabled(boolean)`
- `PojoLens.setStatsPlanCacheMaxEntries(int)`
- `PojoLens.setStatsPlanCacheMaxWeight(long)`
- `PojoLens.setStatsPlanCacheExpireAfterWriteMillis(long)`
- `PojoLens.getStatsPlanCacheMaxEntries()`
- `PojoLens.getStatsPlanCacheMaxWeight()`
- `PojoLens.getStatsPlanCacheExpireAfterWriteMillis()`
- `PojoLens.clearStatsPlanCache()`
- `PojoLens.resetStatsPlanCacheStats()`
- `PojoLens.getStatsPlanCacheHits()`
- `PojoLens.getStatsPlanCacheMisses()`
- `PojoLens.getStatsPlanCacheSize()`
- `PojoLens.getStatsPlanCacheEvictions()`
- `PojoLens.getStatsPlanCacheSnapshot()`

Instance-scoped cache controls:
- `PojoLensRuntime runtime = PojoLens.newRuntime()`
- `runtime.newQueryBuilder(List<?>)`
- `runtime.parse(String)`
- `runtime.setComputedFieldRegistry(ComputedFieldRegistry)`
- `runtime.setLintMode(boolean)`
- `runtime.setStrictParameterTypes(boolean)`
- `runtime.setTelemetryListener(QueryTelemetryListener)`
- `runtime.applyPreset(PojoLensRuntimePreset)`
- `runtime.sqlLikeCache()` (configure SQL-like cache policy per runtime)
- `runtime.statsPlanCache()` (configure stats-plan cache policy per runtime)

Core builder operations:
- `addRule(...)`, `addHaving(...)`, `allOf(...)`, `anyOf(...)`
: includes selector overloads and date-format overloads for rule/having methods
- `addOrder(...)`, `addGroup(...)`, `addDistinct(...)`, `addField(...)`
: includes selector overloads for indexless and indexed variants
- `computedFields(ComputedFieldRegistry)`
- `addMetric(...)`, `addCount(...)`, `addTimeBucket(...)`
: includes `TimeBucketPreset` overloads for explicit timezone and week-start behavior
- `addJoinBeans(parentField, childRows, childField, Join.*)`
: includes selector-based join overloads
- `limit(maxRows)`
- `copyOnBuild(boolean)`
- `telemetry(QueryTelemetryListener)`
- `explain()`
- `schema(Class<?>)`
- `initFilter()`

Filter execution:
- `filter(Class<T>)`
- `filter(Sort, Class<T>)`
- `filterGroups(Class<T>)`
- `chart(Class<T>, ChartSpec)`
- `chart(Sort, Class<T>, ChartSpec)`

## Current Limitations

- SQL-like subqueries currently support only `WHERE field IN (select oneField ...)`.
- SQL-like subquery joins and aggregate subqueries are not supported yet.
- SQL-like aggregate queries require explicit `SELECT` fields.
- SQL-like aggregate `ORDER BY` must reference a group-by field or aggregate output alias/name.
- Time bucket input fields must be `java.util.Date` values.
- SQL-like week-start arguments are supported only for `bucket(..., 'week', ...)`.
- Builders are mutable and not safe for concurrent mutation; use `copyOnBuild(true)` for reusable templates.

## Staged Packaging Boundaries

The compatibility facade `PojoLens` remains stable, but runtime boundaries are now explicit:
- core fluent pipeline + cache controls: `PojoLensCore`
- SQL-like parser/cache controls: `PojoLensSql`
- chart-data mapping helpers: `PojoLensChart`

Dependency footprint notes:
- `xchart` is now optional (used by benchmark plotting utilities only)
- `jmh-core` is optional (used by benchmark execution only)

## Documentation

- SQL-like grammar, execution model, and errors: [sql-like.md](docs/sql-like.md)
- Generated field constants and codegen flow: [metamodel.md](docs/metamodel.md)
- Chart contract and examples: [charts.md](docs/charts.md)
- Performance budgets, baselines, and CI gates: [benchmarking.md](docs/benchmarking.md)
- Reusable report definitions: [reports.md](docs/reports.md)
- Tabular result schema metadata: [tabular-schema.md](docs/tabular-schema.md)
- Query snapshot regression fixtures: [regression-fixtures.md](docs/regression-fixtures.md)
- Query telemetry hooks: [telemetry.md](docs/telemetry.md)
- Computed field registry: [computed-fields.md](docs/computed-fields.md)
- Snapshot comparison helpers: [snapshot-comparison.md](docs/snapshot-comparison.md)
- Time bucket presets and calendar behavior: [time-buckets.md](docs/time-buckets.md)
- Cache policy defaults and tuning tradeoffs: [caching.md](docs/caching.md)
- Staged module boundaries and entry points: [modules.md](docs/modules.md)
- Migration notes: [MIGRATION.md](MIGRATION.md)
- Release workflow: [RELEASE.md](RELEASE.md)
- Contribution and local validation: [CONTRIBUTING.md](CONTRIBUTING.md)

## Notes

- Nested object properties are queryable via dotted paths such as `address.city`.
- Paths under `@Exclude` fields are not queryable.
- Invalid fluent rule fields are cleaned/ignored by legacy cleaner behavior.
- Runtime pipeline failures throw `IllegalStateException`.
- Join field collisions are renamed using `child_` prefixes.

Grouped SQL-like queries also support:
- aliasing grouped plain fields, for example `select department as dept, count(*) as total group by dept`
- using grouped aliases in `HAVING`
- ordering by aggregate expressions such as `order by sum(salary) desc` even when that aggregate is not selected

