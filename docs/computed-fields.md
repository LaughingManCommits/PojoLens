# Computed Fields

`ComputedFieldRegistry` lets you register reusable named numeric expressions once and reuse them across fluent queries, SQL-like queries, reports, and chart flows.

Use it when the same derived measure would otherwise be repeated inline:

- adjusted salary
- margin amount
- unit-price totals
- numeric score bands

## Define A Registry

```java
ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
    .add("adjustedSalary", "salary * 1.1", Double.class)
    .add("salaryDelta", "adjustedSalary - 100000", Double.class)
    .build();
```

Current scope:

- expressions are numeric
- output types must be numeric
- definitions can depend on earlier source fields or earlier computed fields

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

## Fluent Queries

Attach the registry to the builder before using computed-field names:

```java
List<DepartmentAdjustedPayroll> rows = PojoLensCore.newQueryBuilder(source)
    .computedFields(registry)
    .addGroup("department")
    .addMetric("adjustedSalary", Metric.SUM, "totalAdjustedPayroll")
    .addOrder("totalAdjustedPayroll", 1)
    .initFilter()
    .filter(DepartmentAdjustedPayroll.class);
```

Once attached, computed-field names can be used anywhere the materialized row schema is valid:

- `addRule(...)`
- `addField(...)`
- `addGroup(...)`
- `addOrder(...)`
- `addMetric(...)`

## Runtime-Wide Registry

For app-wide defaults, attach the registry to a runtime:

```java
PojoLensRuntime runtime = new PojoLensRuntime();
runtime.setComputedFieldRegistry(registry);

List<AdjustedSalaryRow> rows = runtime
    .parse("select name, adjustedSalary where adjustedSalary >= 120000")
    .filter(source, AdjustedSalaryRow.class);
```

Runtime-created fluent builders also inherit the registry.

## Explain Output

Explain payloads surface computed fields in use:

- fluent `explain()` includes configured computed fields for the current schema
- SQL-like `explain()` includes computed fields referenced by the query

## Validation Notes

- computed field names must be unique within a registry
- output types must be numeric
- unknown computed-field references still fail with deterministic validation errors
- strict parameter typing uses the computed field output type when available


