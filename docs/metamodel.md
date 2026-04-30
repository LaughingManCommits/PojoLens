# Field Metamodel Generator

Some PojoLens flows use string field names:

This is optional authoring/build-time tooling.
Use it when generated field constants are worth the extra build step.
For day-to-day typed DSL composition, see [typed.md](typed.md).

- chart specs
- alias/result row projections
- shared constants across modules
- SQL-like query builders that assemble controlled query text
- typed DSL field constants

For typed DSL constants, prefer compiler-time generation with
`@GeneratePojoLensTypedFields`. Use `FieldMetamodelGenerator` when a build
helper, test fixture, or internal codegen tool needs to write source manually.
Use `generateTyped(...)` when code-owned typed queries should avoid hand-written
field strings without enabling annotation processing.

## Compiler-Time Typed Constants

```java
import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

@GeneratePojoLensTypedFields
public class Employee {
    public String department;
    public int salary;
    public boolean active;
}
```

The annotation processor emits ordinary generated Java source:

- package: same package as the model class
- class name: `<ModelSimpleName>TypedFields`
- one `TypedField<T,V>` constant per eligible field
- `ALL` as `List<TypedField<T, ?>>` in deterministic order

Override the generated package or class name when applications keep generated
source in a dedicated namespace:

```java
@GeneratePojoLensTypedFields(
    packageName = "com.acme.generated",
    simpleName = "EmployeeLens")
public class Employee {
    public String department;
    public int salary;
}
```

The processor does not rewrite ASTs or change Java syntax. It only creates
source files during compilation, so IDE completion works through the same
generated-source support used by javac. Maven wiring examples live in
[build-tooling.md](build-tooling.md). Gradle-specific incremental metadata is
deferred.

See `examples/typed-authoring-compiler` for a standalone Maven project that
compiles an annotated model and consumes the generated typed constants in the
same module.

## Manual Source Generation

Use `FieldMetamodelGenerator` to generate a Java constants class for a model or
projection type when annotation processing is not part of the build.

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
This page focuses on generating those constants; the typed authoring guide
lives in [typed.md](typed.md).

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

The same generator can emit output-field constants for grouped projection
types:

```java
List<DepartmentCount> rows = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.ACTIVE.eq(true))
    .groupBy(EmployeeTypedFields.DEPARTMENT)
    .count(DepartmentCountTypedFields.TOTAL)
    .orderByDesc(DepartmentCountTypedFields.TOTAL)
    .filter(employees, DepartmentCount.class);
```

Window-output constants work the same way:

```java
List<DepartmentRank> rows = TypedQuery.from(Employee.class)
    .where(EmployeeTypedFields.ACTIVE.eq(true))
    .window(WindowFunction.ROW_NUMBER, DepartmentRankTypedFields.RN,
        List.of(TypedWindowOrder.desc(EmployeeTypedFields.SALARY)),
        EmployeeTypedFields.DEPARTMENT)
    .qualify(DepartmentRankTypedFields.RN.lte(1L))
    .filter(employees, DepartmentRank.class);
```

The current typed DSL foundation covers projection, filters, join
declarations, `JoinBindings` / `DatasetBundle` execution, grouped aggregates,
grouped `HAVING` over grouped fields and metric aliases, rank windows,
aggregate window outputs, `QUALIFY` over selected window aliases,
totals-style metrics, explicit aggregate window frames via `QueryWindowFrame`,
bounded `IN` / `EXISTS` / `NOT EXISTS` subqueries over the same source or an
explicit source list, ordering, offset, limit, explain, schema, and
execution guards. Keep SQL-like or natural queries for user-authored query
text plus correlated/scalar subqueries and broader named-source planning.

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

The annotation processor is the compiler-integrated typed-field path. The
manual generator remains the fallback for builds that want an explicit codegen
step instead of annotation processing.

You can run the manual generator from:

- a small build-time Java main
- a Maven source-generation task or another explicit build step
- a test or internal codegen tool

Write generated source into a normal generated-sources directory and add that directory to compilation in the build tool you already use.

For the first-party build-tooling shape, including catalog validation and a
generated-sources Maven recipe, see [build-tooling.md](build-tooling.md).


