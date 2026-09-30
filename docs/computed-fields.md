# Computed Fields

`ComputedFieldRegistry` lets you register reusable named expressions once and reuse them across SQL-like queries, natural queries, typed queries, reports, and chart flows.

Use it when the same derived value would otherwise be repeated inline:

- adjusted salary, margin amount, unit-price totals
- normalized text keys (`lower(department)`) and display labels
- date parts to group by (`year(hireDate)`)
- fallbacks across nullable fields (`coalesce(endDate, startDate)`)

## Define A Registry

```java
ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
    .add("adjustedSalary", "salary * 1.1", Double.class)
    .add("salaryDelta", "adjustedSalary - 100000", Double.class)
    .build();
```

Expressions use the SQL-like expression grammar and functions (see Expressions And
Functions in `docs/sql-like.md`). Definitions can depend on source fields or earlier
computed fields.

## Output Types

The output type is the Java type the field holds:

- a numeric type (`Double`, `Integer`, `Long`, `BigDecimal`, ...): the result converts
  to it, and whole types round (`salary * 1.1` as `Integer` rounds)
- `String`: text results, such as `lower(department)` or `concat(first, ' ', last)`;
  enum values are stored by name
- a date/time or enum type: the result must already be that type, such as
  `coalesce(endDate, startDate)` over `LocalDate` fields

```java
ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
    .add("deptKey", "lower(department)", String.class)
    .add("hireYear", "year(hireDate)", Integer.class)
    .add("lastDay", "coalesce(endDate, startDate)", LocalDate.class)
    .build();

List<DepartmentCount> rows = PojoLensSql
    .parse("select deptKey, count(*) as total group by deptKey")
    .computedFields(registry)
    .filter(source, DepartmentCount.class);
```

Types are checked twice:

- `add(...)` rejects an output type that cannot hold the result when the result type is
  known without source fields: `lower(name)` as `Integer`, or `salary * 2` as `String`
- a query that uses the registry checks every applicable definition against the source
  field types: `upper(salary)` over a number, or `coalesce(salary, 0)` declared as
  `String`, fails validation (`EQ-SQL-VAL-009` for SQL-like queries)

When a type cannot be known up front (for example over `QueryRow` sources), a value
that does not fit the output type fails when it is computed.

## Typed And Natural Queries

Typed and natural queries read registry fields by name:

```java
TypedField<Employee, String> DEPT_KEY = TypedField.of("deptKey", String.class);

List<Employee> finance = TypedQuery.from(Employee.class)
    .computedFields(registry)
    .where(DEPT_KEY.eq("finance"))
    .filter(source);

List<Employee> sameRows = PojoLensNatural.parse("show employees where deptKey is 'finance'")
    .computedFields(registry)
    .filter(source, Employee.class);
```

## SQL-like Queries

Attach the registry to the parsed query:

```java
List<AdjustedSalaryRow> rows = PojoLensSql
    .parse("select name, adjustedSalary where adjustedSalary >= 120000 order by adjustedSalary desc")
    .computedFields(registry)
    .filter(source, AdjustedSalaryRow.class);
```

The same registry also works with:

- `bindTyped(...)`
- `explain(...)`
- reusable `ReportDefinition`
- chart mapping via `chart(...)`

## Runtime-Wide Registry

For app-wide defaults, attach the registry to a runtime:

```java
PojoLensRuntime runtime = new PojoLensRuntime();
runtime.setComputedFieldRegistry(registry);

List<AdjustedSalaryRow> rows = runtime
    .parse("select name, adjustedSalary where adjustedSalary >= 120000")
    .filter(source, AdjustedSalaryRow.class);
```

Runtime-created SQL-like and natural queries inherit the registry.

## Explain Output

Explain payloads surface computed fields in use:

- SQL-like `explain()` includes computed fields referenced by the query
- natural `explain()` includes computed fields referenced through the resolved query

## Validation Notes

- computed field names must be unique within a registry
- output types must be able to hold the expression result (see Output Types)
- unknown computed-field references still fail with deterministic validation errors
- strict parameter typing uses the computed field output type when available


