# Build Tooling

PojoLens build tooling stays library-first in this release.

The first-party shape is:

- metamodel generation APIs in `laughing.man.commits.metamodel`
- validation APIs in `laughing.man.commits.tooling`
- deterministic generated source output
- deterministic validation results for CI
- build-tool recipes you can wire into Maven or Gradle without waiting for a
  dedicated plugin or annotation processor

Use this when you want stronger typed authoring support or when config-owned
query catalogs should fail fast in CI before any live data is loaded.

## Available Tooling APIs

- `FieldMetamodel`
- `FieldMetamodelGenerator`
- `MetamodelBatchGenerator`
- `MetamodelGenerationRequest`
- `MetamodelGenerationResult`
- `SavedReportCatalogValidator`
- `SavedReportValidationResult`
- `SavedReportCatalogValidationResult`
- `ToolingValidationIssue`

## Batch Metamodel Generation

Use `MetamodelBatchGenerator` when multiple models or projection rows need
generated constants in one build step.

```java
import laughing.man.commits.metamodel.MetamodelBatchGenerator;
import laughing.man.commits.metamodel.MetamodelGenerationRequest;
import laughing.man.commits.metamodel.MetamodelGenerationResult;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCountRow;

import java.nio.file.Path;
import java.util.List;

Path outputDir = Path.of("target/generated-sources/pojo-lens");

List<MetamodelGenerationResult> generated = MetamodelBatchGenerator.write(
    outputDir,
    List.of(
        MetamodelGenerationRequest.typed(Employee.class),
        MetamodelGenerationRequest.strings(
            DepartmentCountRow.class,
            "com.acme.generated",
            "DepartmentCountFields")
    )
);
```

Shortcuts:

- `writeDefaultStrings(outputDir, Class<?>...)`
- `writeDefaultTyped(outputDir, Class<?>...)`

The batch writer rejects duplicate generated qualified names before writing, so
misconfigured build inputs fail deterministically instead of silently
overwriting each other.

## Saved Query And Catalog Validation

Use `SavedReportCatalogValidator` when saved reports or config-owned query text
should be checked in CI without executing against rows.

```java
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.report.SavedReport;
import laughing.man.commits.tooling.SavedReportCatalogValidationResult;
import laughing.man.commits.tooling.SavedReportCatalogValidator;

import java.util.List;

SavedReport activeByDepartment = SavedReport
    .sqlLike(
        "dept-count",
        "Department count",
        "select department, count(*) as total group by department order by department asc")
    .withChartSpec(ChartSpec.of(ChartType.BAR, "department", "total"));

SavedReportCatalogValidationResult validation =
    SavedReportCatalogValidator.validate(List.of(activeByDepartment));

if (!validation.valid()) {
    throw new IllegalStateException(
        validation.reports().stream()
            .flatMap(report -> report.issues().stream())
            .map(issue -> issue.code() + ": " + issue.message())
            .reduce((left, right) -> left + System.lineSeparator() + right)
            .orElse("Saved report validation failed"));
}
```

Validation covers:

- duplicate saved-report ids
- default parameter mismatches
- chart field vs query output mismatches
- schema column vs query output mismatches
- SQL-like diagnostics errors and lint warnings
- natural-query diagnostics lowered through the same engine
- raw SQL-like or natural query text via `validateSqlLike(...)` and
  `validateNatural(...)`

Machine-readable issue codes currently include:

- `PLT-SAVED-001` duplicate saved-report id
- `PLT-SAVED-002` default parameter not required by query
- `PLT-SAVED-003` required parameter missing a default
- `PLT-SAVED-004` chart field missing from query output
- `PLT-SAVED-005` schema column missing from query output
- `PLT-SAVED-006` wildcard output prevents chart validation
- `PLT-SAVED-007` wildcard output prevents schema validation
- `PLT-SAVED-008` raw query text could not be parsed on the natural side or
  before a full saved-report contract could be built

SQL-like raw-text validation preserves existing SQL-like parser and API error
codes when those are already available.

## Maven Build Recipe

One practical shape is a tiny build helper main plus normal generated-sources
wiring.

Build helper:

```java
public final class PojoLensBuildToolingMain {

    private PojoLensBuildToolingMain() {
    }

    public static void main(String[] args) {
        Path generatedSources = Path.of(args[0]);

        MetamodelBatchGenerator.writeDefaultTyped(
            generatedSources,
            Employee.class,
            DepartmentCountRow.class
        );

        SavedReportCatalogValidationResult validation = SavedReportCatalogValidator.validate(
            List.of(
                SavedReport.sqlLike(
                    "dept-count",
                    "Department count",
                    "select department, count(*) as total group by department order by department asc")
            )
        );

        if (!validation.valid()) {
            throw new IllegalStateException("PojoLens build validation failed");
        }
    }
}
```

Maven wiring:

```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <version>3.5.0</version>
  <executions>
    <execution>
      <id>pojolens-build-tooling</id>
      <phase>generate-sources</phase>
      <goals>
        <goal>java</goal>
      </goals>
      <configuration>
        <mainClass>com.acme.build.PojoLensBuildToolingMain</mainClass>
        <arguments>
          <argument>${project.build.directory}/generated-sources/pojo-lens</argument>
        </arguments>
      </configuration>
    </execution>
  </executions>
</plugin>

<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>build-helper-maven-plugin</artifactId>
  <version>3.6.0</version>
  <executions>
    <execution>
      <id>add-pojolens-generated-sources</id>
      <phase>generate-sources</phase>
      <goals>
        <goal>add-source</goal>
      </goals>
      <configuration>
        <sources>
          <source>${project.build.directory}/generated-sources/pojo-lens</source>
        </sources>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## Incremental Build Guidance

- Keep the model list and saved-report catalog in one small build helper so the
  build graph stays obvious.
- Treat model classes, saved-report definitions, and config-owned query files
  as inputs.
- Treat generated Java sources and any exported validation report as outputs.
- `MetamodelBatchGenerator` output is deterministic, so normal build caching
  works as long as the input list is stable.
- `SavedReportCatalogValidator` does not require live row execution, so it is
  safe to run in CI and in `generate-sources` or `verify`.

## What This Does Not Do

This release does not add:

- an annotation processor
- a dedicated Maven plugin
- a dedicated Gradle plugin
- live data execution during validation

If those become necessary later, they should wrap these same public tooling
contracts rather than creating a second build-time surface.
