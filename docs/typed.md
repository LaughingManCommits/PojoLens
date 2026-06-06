# Typed Query Guide

Typed queries are the Java-owned authoring path for PojoLens.
Use `TypedQuery` when query logic should live in code, field references should
be refactor-friendly, and predicate composition should stay type-checked.

Use SQL-like or natural queries when callers author query text directly, when
you need correlated or scalar subqueries, or when broader named-source planning
reads better as text than Java builder code.

## Field Constants

`TypedQuery` composes `TypedField<T,V>` and `TypedPredicate<T>`.
For one-off usage you can hand-write fields:

```java
TypedField<Employee, String> DEPARTMENT = TypedField.of("department", String.class);
TypedField<Employee, Integer> SALARY = TypedField.of("salary", Integer.class);
TypedField<Employee, Boolean> ACTIVE = TypedField.of("active", Boolean.class);
```

For shared domain types, generate typed constants at compile time with
`@GeneratePojoLensTypedFields`:

```java
import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

@GeneratePojoLensTypedFields
public class Employee {
    public String department;
    public int salary;
    public boolean active;
}
```

This emits `EmployeeTypedFields` as ordinary generated Java source for javac
and IDE completion. For explicit build-helper generation, use
[metamodel.md](metamodel.md):

```java
FieldMetamodel metamodel = FieldMetamodelGenerator.generateTyped(
    Employee.class,
    "com.acme.generated",
    "EmployeeTypedFields");
metamodel.writeTo(Path.of("target/generated-sources/pojo-lens"));
```

Generated constants are the default recommendation for long-lived application
code. Hand-written `TypedField.of(...)` calls are still useful for quick
one-offs and for joined-field references that do not belong to the base row
type.

## String Predicates

`contains(value)` matches rows where the field includes the substring (case-sensitive).
`containsIgnoreCase(value)` is the case-insensitive equivalent — lowers to a
`MATCHES` pattern using `(?i)` and `Pattern.quote` so regex special characters in
the value are treated as literals.
`matches(pattern)` matches rows where the field satisfies the regex pattern.
`contains` and `matches` mirror the SQL-like `CONTAINS` and `MATCHES` operators.

```java
// case-sensitive: "Ali" matches "Alice", not "alice"
List<Employee> result = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.NAME.contains("Ali")
        .and(EmployeeTypedFields.DEPARTMENT.matches("Eng.*")))
    .filter(employees);

// case-insensitive: "ALI", "ali", and "Ali" all match "Alice"
List<Employee> result2 = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.NAME.containsIgnoreCase("ALI"))
    .filter(employees);

// static factory equivalent
TypedPredicate<Employee> pred = TypedPredicate.containsIgnoreCase(
    EmployeeTypedFields.NAME, "ali");
```

`NOT(CONTAINS)`, `NOT(CONTAINS_IGNORE_CASE)`, and `NOT(MATCHES)` are not
supported — use SQL-like or filter in application code for negated string predicates.

## Basic Filtering, Ordering, And Limits

`TypedQuery` is immutable. Each fluent call returns a new query definition.

```java
List<Employee> rows = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.DEPARTMENT.eq("Engineering")
        .and(EmployeeTypedFields.ACTIVE.eq(true))
        .and(EmployeeTypedFields.SALARY.gte(120000)))
    .orderByDesc(EmployeeTypedFields.SALARY)
    .limit(10)
    .filter(employees);
```

`.not()` composes a NOT predicate and is lowered via DeMorgan's laws at
execution time. Negated convenience operators (`ne(...)`, `lte(...)`,
`isNotNull()`) are equivalent and preferred for simple cases, but `.not()` is
useful when negating a compound or externally-built predicate:

```java
TypedPredicate<Employee> baseFilter =
    EmployeeTypedFields.DEPARTMENT.eq("Engineering")
        .and(EmployeeTypedFields.ACTIVE.eq(true));

List<Employee> excluded = TypedQuery.from(Employee.class)
    .where(baseFilter.not())
    .filter(employees);
```

`NOT(IN_SUBQUERY)` is not supported — use `NOT EXISTS` instead.

## Sentinel Predicates

`TypedPredicate.any()` and `TypedPredicate.none()` are always-true and always-false
sentinels useful for building predicate chains conditionally without null guards:

```java
// build a predicate chain; any() is the identity for and()
TypedPredicate<Employee> filter = TypedPredicate.any();
if (onlyActive) {
    filter = filter.and(EmployeeTypedFields.ACTIVE.eq(true));
}
if (department != null) {
    filter = filter.and(EmployeeTypedFields.DEPARTMENT.eq(department));
}
List<Employee> result = TypedQuery.from(Employee.class)
    .where(filter)
    .filter(employees);
```

Identity and absorption laws hold at composition time:

| Expression | Simplifies to |
|---|---|
| `pred.and(any())` | `pred` |
| `pred.or(none())` | `pred` |
| `pred.and(none())` | `none()` |
| `pred.or(any())` | `any()` |
| `any().not()` | `none()` |
| `none().not()` | `any()` |

`any()` applied as the sole WHERE predicate returns all rows.
`none()` applied as the sole WHERE predicate returns no rows.

## Execution Convenience

Beyond `filter(rows)` that returns a `List<T>`, `TypedQuery` provides short-circuit
execution methods that avoid full materialisation where possible:

```java
// count matching rows without building a list
long n = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.ACTIVE.eq(true))
    .count(employees);

// check for at least one match — applies limit(1) internally
boolean hasEngineer = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.DEPARTMENT.eq("Engineering"))
    .exists(employees);

// first result in defined order, or empty
Optional<Employee> top = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.ACTIVE.eq(true))
    .orderByDesc(EmployeeTypedFields.SALARY)
    .findFirst(employees);

// exactly one match or empty; throws IllegalStateException if more than one
Optional<Employee> alice = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.NAME.eq("Alice"))
    .findOne(employees);
```

All methods have `DatasetBundle` overloads. `exists` and `findFirst` apply
`limit(1)` internally; `findOne` applies `limit(2)` to detect ambiguity cheaply.

## Pagination

`filterPage(...)` executes a count pass without `limit/offset`, then executes the
configured paged query. Use a positive `limit(...)`; `offset(...)` is optional
and defaults to the first page.

```java
PageResult<Employee> page = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.ACTIVE.eq(true))
    .orderByDesc(EmployeeTypedFields.SALARY)
    .limit(20)
    .offset(40)
    .filterPage(employees);

List<Employee> rows = page.rows();
long totalRows = page.totalRows();
boolean more = page.hasMore();
```

Typed pages are offset-based. `nextCursor()` is empty; advance by creating the
next query with a larger `offset(...)`. Projection and multi-source overloads
mirror `filter(...)`, including `filterPage(rows, Projection.class)`,
`filterPage(rows, joins, Projection.class)`, and `filterPage(datasetBundle,
Projection.class)`.

## Stream Execution

`stream(rows)` executes the query and exposes results through a `Stream<T>`,
enabling downstream `map`, `flatMap`, `collect`, or early-exit patterns without
a named `List` variable:

```java
// stream with predicate and order
Stream<Employee> s = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.ACTIVE.eq(true))
    .orderByDesc(EmployeeTypedFields.SALARY)
    .stream(employees);

// collect into a custom container
Map<String, Long> byDept = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.ACTIVE.eq(true))
    .stream(employees)
    .collect(Collectors.groupingBy(e -> e.department, Collectors.counting()));
```

Overloads follow the same pattern as `filter`:

```java
stream(List<T> rows)
stream(DatasetBundle bundle)
stream(List<T> rows, JoinBindings joins)
stream(List<T> rows, JoinBindings joins, Class<P> projectionClass)
```

**Laziness caveat:** the current implementation is a thin wrapper over
`filter(...)` — rows are fully materialised into a `List` before the stream
is returned. The stream API surface is identical to what callers would write,
so if a future version introduces true lazy streaming from the engine, call
sites will not need to change.

## Range Checks

`between(lo, hi)` is a convenience for `gte(lo).and(lte(hi))` and is available
on both `TypedField` and as a static factory on `TypedPredicate`:

```java
// instance method on TypedField
List<Employee> midRange = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.SALARY.between(60_000, 120_000))
    .filter(employees);

// static factory — symmetric with other TypedPredicate factories
TypedPredicate<Employee> range = TypedPredicate.between(EmployeeTypedFields.SALARY, 60_000, 120_000);
```

Both bounds are inclusive.

## Sort Order

`orderBy(field)` and `orderByDesc(field)` sort a single field ascending or
descending. For explicit per-field direction use `TypedSortOrder`:

```java
// single field, explicit direction
List<Employee> rows = TypedQuery.from(Employee.class)
    .orderBy(TypedSortOrder.desc(EmployeeTypedFields.SALARY))
    .filter(employees);

// multiple fields, mixed directions
List<Employee> rows2 = TypedQuery.from(Employee.class)
    .orderBy(TypedSortOrder.asc(EmployeeTypedFields.DEPARTMENT),
             TypedSortOrder.desc(EmployeeTypedFields.SALARY))
    .filter(employees);
```

Each `TypedSortOrder` keeps its own direction, so common sort keys such as
`department ASC, salary DESC` execute in one query.

## Time Buckets

`timeBucket(dateField, unit, alias)` truncates a date/timestamp field to a
calendar period, creating a computed group-by column that can be aggregated over:

```java
List<PeriodCount> result = TypedQuery.from(Event.class)
    .timeBucket(EventTypedFields.OCCURRED_AT, TimeBucket.MONTH, "period")
    .count("total")
    .filter(events, PeriodCount.class);
```

Accepts a `TimeBucketPreset` for explicit zone and week-start control:

```java
TimeBucketPreset preset = TimeBucketPreset.of(TimeBucket.WEEK)
    .withZone("America/New_York")
    .withWeekStart(DayOfWeek.SUNDAY);

List<PeriodCount> result = TypedQuery.from(Event.class)
    .timeBucket(EventTypedFields.OCCURRED_AT, preset, "period")
    .count("total")
    .filter(events, PeriodCount.class);
```

Both overloads accept a `TypedField` as the alias argument for type-safe output
field naming. The bucket alias is automatically added to the GROUP BY — no
explicit `.groupBy(alias)` is required.

Defaults: UTC zone, Monday week-start.

Supported granularities are `HOUR`, `DAY`, `WEEK`, `MONTH`, `QUARTER`, and
`YEAR`. Hour buckets are formatted as `YYYY-MM-DDTHH`.

## Projection

Use `select(...)` when the output type is a projection rather than the source
row type:

```java
List<EmployeeSummary> rows = TypedQuery.from(Employee.class)
    .select(EmployeeTypedFields.NAME, EmployeeTypedFields.DEPARTMENT)
    .orderBy(EmployeeTypedFields.NAME)
    .filter(employees, EmployeeSummary.class);
```

The projection type should expose fields that match the selected output names.

## Joins And Reused Sources

`join(...)` declares the named secondary source relationship.
`JoinBindings` or `DatasetBundle` provide the actual secondary rows at
execution time.

```java
TypedField<Company, Integer> COMPANY_ID = TypedField.of("id", Integer.class);
TypedField<CompanyEmployee, Integer> EMPLOYEE_COMPANY_ID =
    TypedField.of("companyId", Integer.class);
TypedField<Company, String> JOINED_TITLE = TypedField.of("title", String.class);

JoinBindings joins = JoinBindings.of("employees", companyEmployees);

List<Company> rows = TypedQuery.from(Company.class)
    .join("employees", COMPANY_ID, EMPLOYEE_COMPANY_ID, Join.LEFT_JOIN)
    .where(JOINED_TITLE.eq("Engineer"))
    .filter(companies, joins);
```

When the same multi-source snapshot is reused, package it once:

```java
DatasetBundle bundle = DatasetBundle.of(companies, joins);
List<Company> rows = TypedQuery.from(Company.class)
    .join("employees", COMPANY_ID, EMPLOYEE_COMPANY_ID, Join.LEFT_JOIN)
    .where(JOINED_TITLE.eq("Engineer"))
    .filter(bundle);
```

## Grouped Aggregates And HAVING

Grouped output aliases are usually modeled as typed fields on the projection
type:

```java
List<DepartmentCount> rows = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.ACTIVE.eq(true))
    .groupBy(EmployeeTypedFields.DEPARTMENT)
    .count(DepartmentCountTypedFields.TOTAL)
    .having(DepartmentCountTypedFields.TOTAL.gte(2L))
    .orderByDesc(DepartmentCountTypedFields.TOTAL)
    .filter(employees, DepartmentCount.class);
```

Metric output works the same way:

```java
List<DepartmentPayroll> rows = TypedQuery.from(Employee.class)
    .groupBy(EmployeeTypedFields.DEPARTMENT)
    .metric(EmployeeTypedFields.SALARY, Metric.SUM, DepartmentPayrollTypedFields.PAYROLL)
    .filter(employees, DepartmentPayroll.class);
```

`having(...)` is limited to grouped fields and metric aliases.

## Windows And QUALIFY

Rank windows and post-window filtering stay on the same immutable surface:

```java
List<DepartmentRank> rows = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.ACTIVE.eq(true))
    .window(
        WindowFunction.ROW_NUMBER,
        DepartmentRankTypedFields.RN,
        List.of(TypedWindowOrder.desc(EmployeeTypedFields.SALARY)),
        EmployeeTypedFields.DEPARTMENT)
    .qualify(DepartmentRankTypedFields.RN.lte(1L))
    .filter(employees, DepartmentRank.class);
```

Aggregate windows accept explicit `QueryWindowFrame` values:

```java
TypedQuery.from(WindowMetricInput.class)
    .window(
        WindowFunction.SUM,
        WindowMetricInputTypedFields.AMOUNT,
        WindowMetricProjectionTypedFields.RUNNING_SUM,
        QueryWindowFrame.rowsPrecedingToCurrentRow(6),
        List.of(TypedWindowOrder.asc(WindowMetricInputTypedFields.SEQ)),
        WindowMetricInputTypedFields.DEPARTMENT);
```

Use `windowCountAll(...)` when the value field is `COUNT(*)`.
`qualify(...)` is limited to selected window aliases.

## Bounded Subqueries

Same-source bounded subqueries compose directly from typed predicates:

```java
List<Employee> rows = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.NAME.inSubquery(
        EmployeeTypedFields.NAME,
        TypedQuery.from(Employee.class)
            .where(EmployeeTypedFields.ACTIVE.eq(true)
                .and(EmployeeTypedFields.SALARY.gte(120000)))))
    .filter(employees);
```

Explicit-source-list subqueries are also supported:

```java
TypedField<Company, Integer> COMPANY_ID = TypedField.of("id", Integer.class);

List<Company> rows = TypedQuery.from(Company.class)
    .where(COMPANY_ID.inSubquery(
        CompanyEmployeeTypedFields.COMPANY_ID,
        companyEmployees,
        TypedQuery.from(CompanyEmployee.class)
            .where(CompanyEmployeeTypedFields.TITLE.eq("Engineer"))))
    .filter(companies);
```

`TypedPredicate.exists(...)` and `TypedPredicate.notExists(...)` follow the
same pattern. Bounded typed subqueries are supported only in `where(...)`, not
in `having(...)` or `qualify(...)`.

## Computed Fields

`computedFields(ComputedFieldRegistry)` attaches derived numeric fields so they
can be referenced in `where(...)`, `having(...)`, and metrics — matching the same
capability available on SQL-like and natural queries:

```java
ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
    .add("adjustedSalary", "salary * 1.1", Double.class)
    .build();

TypedField<Employee, Double> ADJUSTED_SALARY =
    TypedField.of("adjustedSalary", Double.class);

List<Employee> highEarners = TypedQuery.from(Employee.class)
    .computedFields(registry)
    .where(ADJUSTED_SALARY.gte(130_000.0))
    .orderBy(EmployeeTypedFields.NAME)
    .filter(employees);
```

The registry is retained across fluent calls and accessible via
`computedFieldRegistry()`. `hasComputedFields()` returns false when no registry
was set or the registry is empty.

## Diagnostics, Preview, Schema, And Guards

The typed surface keeps the same diagnostics and governance hooks as the text
surfaces:

```java
TypedQuery<Employee> query = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.ACTIVE.eq(true))
    .executionGuard(QueryExecutionGuard.builder()
        .maxRowsScanned(50_000)
        .maxRowsReturned(1_000)
        .build());

QueryDiagnostics diagnostics = query.diagnostics();
TypedPlanPreview preview = query.planPreview();
Map<String, Object> explain = query.explain(employees);
TabularSchema schema = query.schema(employees);
List<Employee> rows = query.filter(employees);
```

`diagnostics()` returns no-data validation plus summary metadata such as
referenced fields, output fields, join sources, and subquery presence. Invalid
typed query shapes such as `select(...)` combined with grouped metrics are
reported as `QueryDiagnosticsError` entries instead of requiring execution.

`planPreview()` returns the richer typed-only structural shape:

```java
TypedPlanPreview preview = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.ACTIVE.eq(true))
    .window(
        WindowFunction.ROW_NUMBER,
        DepartmentRankTypedFields.RN,
        List.of(TypedWindowOrder.desc(EmployeeTypedFields.SALARY)),
        EmployeeTypedFields.DEPARTMENT)
    .qualify(DepartmentRankTypedFields.RN.lte(1L))
    .orderBy(EmployeeTypedFields.DEPARTMENT)
    .planPreview();

List<TypedPlanWindow> windows = preview.windows();
List<PlanPreviewOrder> ordering = preview.orderFields();
TypedPlanPredicate qualify = preview.qualifyExpression();
```

The preview is data-free and reports joins, group keys, metrics, windows, sort
orders, paging, time buckets, computed fields, and any attached execution
guard.

Use `schema(..., Projection.class)` when the output is a projection rather than
the source row type.

`schema(Projection.class)` is also available without source rows when you need
deterministic metadata for a reusable contract before execution:

```java
TabularSchema preview = TypedQuery.from(Employee.class)
    .groupBy(EmployeeTypedFields.DEPARTMENT)
    .count(DepartmentCountTypedFields.TOTAL)
    .schema(DepartmentCount.class);
```

## Reusable Report Definitions

Wrap a typed query in `ReportDefinition<T>` when the same in-process workflow
needs reusable rows, schema, and optional chart mapping across refreshed
snapshots:

```java
ReportDefinition<DepartmentCount> report = ReportDefinition.typed(
    TypedQuery.from(Employee.class)
        .where(EmployeeTypedFields.ACTIVE.eq(true))
        .groupBy(EmployeeTypedFields.DEPARTMENT)
        .count(DepartmentCountTypedFields.TOTAL)
        .orderBy(EmployeeTypedFields.DEPARTMENT),
    DepartmentCount.class);

List<DepartmentCount> rows = report.rows(employees);
TabularSchema schema = report.schema();
```

Add chart mapping with `ReportDefinition.typed(query, Projection.class,
ChartSpec)` or later with `withChartSpec(...)`. The reusable report source label
is synthetic, for example `typed:Employee`.

## Current Boundaries

- `TypedQuery` is the right path for Java-owned query logic, not user-authored text.
- `orderBy(TypedSortOrder...)` supports per-field direction.
- `having(...)` only accepts grouped fields and metric aliases.
- `qualify(...)` only accepts selected window aliases.
- `NOT(CONTAINS)`, `NOT(CONTAINS_IGNORE_CASE)`, and `NOT(MATCHES)` are not supported; use SQL-like or application-code filtering instead.
- `NOT(IN_SUBQUERY)` is not supported; use `NOT EXISTS` instead.
- Correlated/scalar subqueries and broader named-source planning remain on
  [sql-like.md](sql-like.md) or [natural.md](natural.md).
- Field generation lives in [metamodel.md](metamodel.md); build-time catalog
  validation lives in [build-tooling.md](build-tooling.md).
