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

`contains(value)` matches rows where the field includes the substring.
`matches(pattern)` matches rows where the field satisfies the regex pattern.
Both mirror the SQL-like `CONTAINS` and `MATCHES` operators.

```java
List<Employee> result = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.NAME.contains("Ali")
        .and(EmployeeTypedFields.DEPARTMENT.matches("Eng.*")))
    .filter(employees);
```

`NOT(CONTAINS)` and `NOT(MATCHES)` are not supported — use SQL-like or
filter in application code for negated string predicates.

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

## Sort Order

`orderBy(field)` and `orderByDesc(field)` sort a single field ascending or
descending. For explicit per-field direction use `TypedSortOrder`:

```java
// single field, explicit direction
List<Employee> rows = TypedQuery.from(Employee.class)
    .orderBy(TypedSortOrder.desc(EmployeeTypedFields.SALARY))
    .filter(employees);

// multiple fields, same direction
List<Employee> rows2 = TypedQuery.from(Employee.class)
    .orderBy(TypedSortOrder.asc(EmployeeTypedFields.DEPARTMENT),
             TypedSortOrder.asc(EmployeeTypedFields.NAME))
    .filter(employees);
```

The underlying engine requires all ORDER BY fields to share the same direction.
Mixing `asc` and `desc` in a single `orderBy(TypedSortOrder...)` call throws
`IllegalStateException` at execution time.

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

## Explain, Schema, And Guards

The typed surface keeps the same diagnostics and governance hooks as the text
surfaces:

```java
TypedQuery<Employee> query = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.ACTIVE.eq(true))
    .executionGuard(QueryExecutionGuard.builder()
        .maxRowsScanned(50_000)
        .maxRowsReturned(1_000)
        .build());

Map<String, Object> explain = query.explain(employees);
TabularSchema schema = query.schema(employees);
List<Employee> rows = query.filter(employees);
```

Use `schema(..., Projection.class)` when the output is a projection rather than
the source row type.

## Current Boundaries

- `TypedQuery` is the right path for Java-owned query logic, not user-authored text.
- `orderBy(TypedSortOrder...)` requires all fields to share the same direction; mixed directions throw `IllegalStateException`.
- `having(...)` only accepts grouped fields and metric aliases.
- `qualify(...)` only accepts selected window aliases.
- `NOT(IN_SUBQUERY)` is not supported; use `NOT EXISTS` instead.
- Correlated/scalar subqueries and broader named-source planning remain on
  [sql-like.md](sql-like.md) or [natural.md](natural.md).
- Field generation lives in [metamodel.md](metamodel.md); build-time catalog
  validation lives in [build-tooling.md](build-tooling.md).
