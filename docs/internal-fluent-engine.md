# Internal Fluent Engine DSL

This page is maintainer documentation, not user-facing API guidance.

The fluent builder surface remains useful inside PojoLens as the structured
engine DSL for execution planning, parity tests, benchmarks, and lower-level
feature work. It is not the public product story for new users.

Public docs should lead with:

- `PojoLensSql` for the primary query surface
- `PojoLensNatural` for guided plain-English text
- `ReportDefinition.sql(...)` and `ReportDefinition.natural(...)` for reusable
  query contracts
- `PojoLensCsv`, `PojoLensTree`, `PojoLensChart`, and runtime policy helpers
  where those workflows apply

## Internal Role

Use the fluent builder internally when a test, benchmark, or implementation
needs a direct structured query shape without SQL-like parsing.

Useful internal cases:

- verifying SQL-like and natural lowering against a known execution shape
- testing grouped predicates, windows, joins, time buckets, streaming, and
  subqueries at the engine layer
- measuring engine execution paths separately from parse/bind overhead
- building maintainer-only examples for engine behavior

## Boundary Rules

- Do not add fluent examples to README quick starts or public path-selection
  tables.
- Do not document `QueryBuilder`, `FilterQueryBuilder`, `Filter`, `QueryRule`,
  or `FluentQueryDefinition` as stable public API.
- Prefer "shared execution engine" in public docs instead of "fluent
  pipeline".
- Keep public reusable-query examples on SQL-like or natural report
  definitions.
- Add a future Java-native query API only if a real public use case requires a
  narrow immutable contract; do not expose the current mutable builder as that
  API.

## Current Bridge

Internal fluent entry points now live under:

- `laughing.man.commits.internal.FluentEngine`
- `laughing.man.commits.internal.builder.QueryBuilder`
- `laughing.man.commits.internal.builder.FilterQueryBuilder`
- `laughing.man.commits.internal.builder.FluentQueryDefinition`

`PojoLensCore`, `PojoLensRuntime.newQueryBuilder(...)`, and
`ReportDefinition.fluent(...)` are no longer public entry points.

## Import Boundary

Internal tests, binders, benchmarks, and maintainer tools may import the
internal engine DSL directly:

```java
import laughing.man.commits.internal.FluentEngine;
import laughing.man.commits.internal.builder.QueryBuilder;
import laughing.man.commits.internal.builder.QueryRule;
import laughing.man.commits.internal.builder.QueryWindowFrame;
import laughing.man.commits.internal.builder.QueryWindowOrder;
```

Keep those imports out of public examples, README snippets, and stable API
contract docs. If a public example needs Java-owned query composition, use
SQL-like templates, natural templates, `ReportDefinition.sql(...)`, or a
purpose-built public wrapper.

## Basic Execution Shape

Create a builder from already-loaded rows, add planning clauses, call
`initFilter()`, then execute through the returned `Filter`.

```java
List<Employee> rows = FluentEngine.newQueryBuilder(employees)
        .addRule("active", true, Clauses.EQUAL)
        .addOrder("department", 1)
        .addOrder("salary", 2)
        .limit(50)
        .initFilter()
        .filter(Sort.ASC, Employee.class);
```

Use `iterator(...)` or `stream(...)` when a test or benchmark specifically
needs lazy execution coverage:

```java
List<String> names = FluentEngine.newQueryBuilder(employees)
        .addRule("active", true, Clauses.EQUAL)
        .limit(10)
        .initFilter()
        .stream(Employee.class)
        .map(employee -> employee.name)
        .toList();
```

`explain()` and `schema(Class<?>)` inspect the configured plan before
execution:

```java
Map<String, Object> explain = FluentEngine.newQueryBuilder(employees)
        .addIndex("department")
        .addRule("department", "Engineering", Clauses.EQUAL)
        .explain();

TabularSchema schema = FluentEngine.newQueryBuilder(employees)
        .addField("department")
        .addCount("employeeCount")
        .schema(DepartmentSummary.class);
```

## Builder Lifecycle

`QueryBuilder` is mutable. Configure it on one thread, then build an executable
`Filter` with `initFilter()`.

`copyOnBuild(true)` is the default and captures an isolated execution snapshot
for each filter. Prefer the default in tests and implementation code unless a
benchmark intentionally measures mutable shared-state behavior.

```java
QueryBuilder builder = FluentEngine.newQueryBuilder(employees)
        .addRule("department", "Engineering", Clauses.EQUAL)
        .copyOnBuild(true);

Filter filter = builder.initFilter();

builder.addRule("active", true, Clauses.EQUAL);

List<Employee> originalSnapshot = filter.filter(Employee.class);
```

With `copyOnBuild(false)`, the filter can observe later builder mutation. This
mode is for narrow engine diagnostics only.

## Method Groups

Filtering:

- `addRule(field, value, clause)` adds an `AND` rule.
- `addRule(field, value, clause, separator)` adds an explicit `AND` or `OR`
  rule.
- `allOf(QueryRule...)` adds a grouped `AND` predicate set.
- `anyOf(QueryRule...)` adds a grouped `OR` predicate set.
- Date-sensitive rule overloads accept an explicit date format.

Projection and row shaping:

- `addField(field)` selects output fields.
- `addDistinct(field)` deduplicates by field.
- `limit(maxRows)` and `offset(rowOffset)` page the final row stream.
- `addIndex(field)` declares an optional equality-index hint. Execution falls
  back to scan when the index cannot apply.

Ordering and grouping:

- `addOrder(field)` adds ordering metadata.
- `addOrder(field, priorityIndex)` controls multi-column order priority.
- `addGroup(field)` groups rows.
- `addMetric(field, Metric.SUM, "alias")` adds aggregate metrics.
- `addCount("alias")` adds row count output.
- `addHaving(...)`, `addHavingAllOf(...)`, and `addHavingAnyOf(...)` filter
  aggregate output.

Joins:

- `addJoinBeans(parentField, children, childField, Join.INNER_JOIN)` configures
  an in-memory join against child POJOs.
- Call `initFilter().join().filter(...)` when executing joined shapes.
- Typed selector overloads are available for internal tests that need refactor
  safety.

Windows and qualify:

- `addWindow(alias, WindowFunction.ROW_NUMBER, partitions, orderFields)` adds
  rank-style window output.
- Aggregate windows use
  `addWindow(alias, function, valueField, countAll, partitions, orderFields)`.
- Frame-aware aggregate windows add a `QueryWindowFrame`.
- `addQualify(...)`, `addQualifyAllOf(...)`, and `addQualifyAnyOf(...)` filter
  after window computation.

Subqueries:

- `addInSubquery(field, outputField, configurer)` uses the parent source rows.
- `addInSubquery(field, sourceRows, outputField, configurer)` uses explicit
  subquery source rows.
- `addExists(...)` and `addNotExists(...)` test uncorrelated subquery presence.
- `QueryRule.inSubquery(...)`, `QueryRule.exists(...)`, and
  `QueryRule.notExists(...)` participate in `allOf(...)` and `anyOf(...)`.

Other controls:

- `telemetry(listener)` attaches stage telemetry.
- `computedFields(registry)` materializes reusable derived numeric fields.
- `FluentEngine.prepare(projectionClass, configurer)` creates an internal
  immutable query definition that rebuilds a fresh builder for each execution.

## Common Engine Examples

Grouped aggregate with HAVING:

```java
List<DepartmentSummary> summaries = FluentEngine.newQueryBuilder(employees)
        .addGroup("department")
        .addMetric("salary", Metric.SUM, "totalSalary")
        .addCount("employeeCount")
        .addHaving("totalSalary", 250000, Clauses.BIGGER_EQUAL)
        .initFilter()
        .filter(DepartmentSummary.class);
```

Left join:

```java
List<EmployeeDepartment> joined = FluentEngine.newQueryBuilder(employees)
        .addJoinBeans("departmentId", departments, "id", Join.LEFT_JOIN)
        .initFilter()
        .join()
        .filter(EmployeeDepartment.class);
```

Top row per partition:

```java
List<EmployeeRank> topEarners = FluentEngine.newQueryBuilder(employees)
        .addRule("active", true, Clauses.EQUAL)
        .addWindow(
                "rn",
                WindowFunction.ROW_NUMBER,
                List.of("department"),
                List.of(QueryWindowOrder.of("salary", Sort.DESC))
        )
        .addQualify("rn", 1, Clauses.SMALLER_EQUAL)
        .addOrder("department", 1)
        .initFilter()
        .filter(Sort.ASC, EmployeeRank.class);
```

Running aggregate window:

```java
List<PayrollRow> payroll = FluentEngine.newQueryBuilder(payrollRows)
        .addWindow(
                "runningTotal",
                WindowFunction.SUM,
                "amount",
                false,
                List.of("department"),
                List.of(QueryWindowOrder.of("payPeriod", Sort.ASC)),
                QueryWindowFrame.rowsPrecedingToCurrentRow(2)
        )
        .addOrder("department", 1)
        .addOrder("payPeriod", 2)
        .initFilter()
        .filter(Sort.ASC, PayrollRow.class);
```

Explicit-source `IN` subquery:

```java
List<Company> engineeringCompanies = FluentEngine.newQueryBuilder(companies)
        .addInSubquery("id", employees, "companyId",
                subquery -> subquery.addRule("title", "Engineer", Clauses.EQUAL))
        .initFilter()
        .filter(Company.class);
```

Grouped subquery predicate:

```java
List<Employee> rows = FluentEngine.newQueryBuilder(employees)
        .anyOf(
                QueryRule.exists(subquery ->
                        subquery.addRule("department", "Missing", Clauses.EQUAL)),
                QueryRule.of("department", "Finance", Clauses.EQUAL)
        )
        .initFilter()
        .filter(Employee.class);
```

Prepared internal definition:

```java
FluentQueryDefinition<Employee> activeEngineering = FluentEngine.prepare(
        Employee.class,
        query -> query.addRule("active", true, Clauses.EQUAL)
                .addRule("department", "Engineering", Clauses.EQUAL)
);

List<Employee> rows = activeEngineering.rows(employees);
```

## Lowering Guidance

When adding SQL-like or natural features, bind them into the shared execution
engine through the same concepts documented above:

- `WHERE` maps to `addRule(...)`, `allOf(...)`, `anyOf(...)`, and subquery
  rules.
- `SELECT` maps to `addField(...)`, `addMetric(...)`, `addCount(...)`, window
  aliases, and time-bucket aliases.
- `GROUP BY` maps to `addGroup(...)`; `HAVING` maps to `addHaving(...)`.
- `ORDER BY` maps to `addOrder(...)`; `LIMIT` and `OFFSET` map to their builder
  methods.
- `JOIN` maps to `addJoinBeans(...)` plus `join()` at execution time.
- `QUALIFY` maps to `addQualify(...)` after `addWindow(...)`.

Keep lowering tests focused on semantic parity rather than fluent API
stability. The internal builder may change when the SQL-like or natural surface
needs a cleaner engine representation.
