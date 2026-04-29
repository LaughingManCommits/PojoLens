# Field Metamodel Generator

Some PojoLens flows use string field names:

This is optional authoring/build-time tooling.
Use it when generated field constants are worth the extra build step.

- chart specs
- alias/result row projections
- shared constants across modules
- SQL-like query builders that assemble controlled query text
- typed DSL field constants

Use `FieldMetamodelGenerator` to generate a Java constants class for a model or projection type.
Use `generateTyped(...)` when code-owned typed queries should avoid hand-written field strings.

## Generate Source

```java
import laughing.man.commits.metamodel.FieldMetamodel;
import laughing.man.commits.metamodel.FieldMetamodelGenerator;

import java.nio.file.Path;

FieldMetamodel metamodel = FieldMetamodelGenerator.generate(Employee.class);
Path javaFile = metamodel.writeTo(Path.of("target/generated-sources/pojo-lens"));
```

Default output naming:

- package: same package as the model class
- class name: `<ModelSimpleName>Fields`

You can override both:

```java
FieldMetamodel metamodel = FieldMetamodelGenerator.generate(
    Employee.class,
    "com.acme.generated",
    "EmployeeFields");
```

## Generated Output Shape

Generated classes contain:

- one `public static final String` per eligible field
- `ALL` as `List<String>` in deterministic order
- a private constructor
- dotted nested paths for queryable simple properties (for example `location.city`)

Example:

```java
public final class EmployeeFields {
    public static final String ACTIVE = "active";
    public static final String DEPARTMENT = "department";
    public static final String SALARY = "salary";

    public static final List<String> ALL = List.of(
        ACTIVE,
        DEPARTMENT,
        SALARY
    );

    private EmployeeFields() {
    }
}
```

## Eligible Fields

The generator includes queryable instance fields that are:

- non-static
- non-final
- non-synthetic
- not annotated with `@Exclude`
- nested through non-JDK object types until a simple leaf field is reached

Field names are sorted alphabetically so generated output is deterministic in tests and build pipelines.

## SQL-like Query Usage

```java
String query = "where " + EmployeeFields.DEPARTMENT
    + " = :department order by " + EmployeeFields.SALARY + " desc limit 10";

List<Employee> rows = PojoLensSql.parse(query)
    .params(Map.of("department", "Engineering"))
    .filter(source, Employee.class);
```

## Chart Spec Usage

```java
ChartSpec spec = ChartSpec.of(
    ChartType.BAR,
    DepartmentPayrollFields.DEPARTMENT,
    DepartmentPayrollFields.PAYROLL);

ChartData chart = PojoLensChart.toChartData(rows, spec);
```

## Typed DSL Usage

`generateTyped(...)` emits `TypedField<T,V>` constants for the stable typed DSL.
Primitive model fields are boxed in the generated generic type, so an `int`
field is emitted as `TypedField<Employee, Integer>`.

```java
FieldMetamodel metamodel = FieldMetamodelGenerator.generateTyped(
    Employee.class,
    "com.acme.generated",
    "EmployeeTypedFields");
Path javaFile = metamodel.writeTo(Path.of("target/generated-sources/pojo-lens"));
```

Generated typed constants can be used with `TypedQuery`:

```java
List<Employee> rows = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.DEPARTMENT.eq("Engineering")
        .and(EmployeeTypedFields.ACTIVE.eq(true)))
    .orderByDesc(EmployeeTypedFields.SALARY)
    .limit(10)
    .filter(employees);
```

The current typed DSL foundation covers projection, filters, ordering, offset,
limit, explain, schema, and execution guards. Keep SQL-like or natural queries
for grouping, aggregation, joins, windows, subqueries, and user-authored query
text until those typed shapes are stabilized.

## Batch Generation

When multiple models should be generated in one build step, use
`MetamodelBatchGenerator` instead of writing your own loop:

```java
import laughing.man.commits.metamodel.MetamodelBatchGenerator;
import laughing.man.commits.metamodel.MetamodelGenerationRequest;

import java.nio.file.Path;
import java.util.List;

MetamodelBatchGenerator.write(
    Path.of("target/generated-sources/pojo-lens"),
    List.of(
        MetamodelGenerationRequest.typed(Employee.class),
        MetamodelGenerationRequest.strings(
            DepartmentPayrollRow.class,
            "com.acme.generated",
            "DepartmentPayrollFields")
    )
);
```

## Build Integration

The generator is intentionally library-level rather than annotation-processor-driven.

That means you can run it from:

- a small build-time Java main
- a Maven/Gradle source-generation task
- a test or internal codegen tool

Write generated source into a normal generated-sources directory and add that directory to compilation in the build tool you already use.

For the first-party build-tooling shape, including catalog validation and a
generated-sources Maven recipe, see [build-tooling.md](build-tooling.md).


