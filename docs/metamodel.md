# Field Metamodel Generator

`PojoLens` already supports lambda selectors in many fluent APIs, but some flows still need string field names:

- chart specs
- alias/result row projections
- shared constants across modules
- fluent code paths that prefer generated constants over inline strings

Use `FieldMetamodelGenerator` to generate a Java constants class for a model or projection type.

## Generate Source

```java
import metamodel.pojo.lens.FieldMetamodel;
import metamodel.pojo.lens.FieldMetamodelGenerator;

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

## Fluent Builder Usage

```java
List<Employee> rows = PojoLens.newQueryBuilder(source)
    .addRule(EmployeeFields.DEPARTMENT, "Engineering", Clauses.EQUAL)
    .addOrder(EmployeeFields.SALARY, 1)
    .limit(10)
    .initFilter()
    .filter(Sort.DESC, Employee.class);
```

## Chart Spec Usage

```java
ChartSpec spec = ChartSpec.of(
    ChartType.BAR,
    DepartmentPayrollFields.DEPARTMENT,
    DepartmentPayrollFields.PAYROLL);

ChartData chart = PojoLens.toChartData(rows, spec);
```

## Build Integration

The generator is intentionally library-level rather than annotation-processor-driven.

That means you can run it from:

- a small build-time Java main
- a Maven/Gradle source-generation task
- a test or internal codegen tool

Write generated source into a normal generated-sources directory and add that directory to compilation in the build tool you already use.

