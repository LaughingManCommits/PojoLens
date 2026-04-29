package laughing.man.commits;

import laughing.man.commits.csv.CsvLoadResult;
import laughing.man.commits.csv.CsvOptions;
import laughing.man.commits.files.JsonLoadException;
import laughing.man.commits.files.JsonLoadResult;
import laughing.man.commits.files.JsonOptions;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PojoLensFilesTest {

    @Test
    void filesSurfaceShouldLoadCsvRowsThroughGeneralLoader(@TempDir Path tempDir) throws IOException {
        Path csv = writeFile(
                tempDir,
                "employees.csv",
                """
                        id,name,department,salary,active
                        1,Alice,Engineering,120000,true
                        2,Bob,Finance,90000,true
                        3,Cara,Engineering,130000,true
                        """
        );

        List<Employee> rows = PojoLensFiles.csv(csv, Employee.class);
        List<Employee> result = PojoLensSql
                .parse("where department = 'Engineering' order by salary desc")
                .filter(rows, Employee.class);

        assertEquals(3, rows.size());
        assertEquals(List.of("Cara", "Alice"), result.stream().map(row -> row.name).toList());
    }

    @Test
    void filesSurfaceShouldLoadTsvRowsWithTabDefaults(@TempDir Path tempDir) throws IOException {
        Path tsv = writeFile(
                tempDir,
                "employees.tsv",
                "employeeName\tannualSalary\n"
                        + "Alice\t120000\n"
                        + "Cara\t130000\n"
        );

        List<EmployeeSummary> rows = PojoLensFiles.tsv(tsv, EmployeeSummary.class);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).employeeName);
        assertEquals(130000, rows.get(1).annualSalary);
    }

    @Test
    void runtimeFilesSurfaceShouldApplyRuntimeDefaultsAndForceTabForTsv(@TempDir Path tempDir) throws IOException {
        Path csv = writeFile(
                tempDir,
                "employees-semicolon.csv",
                """
                        employeeName ; annualSalary
                         Alice ; 120000
                         Cara ; 130000
                        """
        );
        Path tsv = writeFile(
                tempDir,
                "employees.tsv",
                "employeeName\tannualSalary\n"
                        + " Alice \t 120000 \n"
                        + " Cara \t 130000 \n"
        );

        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setCsvDefaults(CsvOptions.builder().delimiter(';').trim(true).build());

        List<EmployeeSummary> csvRows = runtime.files().csv(csv, EmployeeSummary.class);
        CsvLoadResult<EmployeeSummary> tsvRows = runtime.files().tsvWithReport(tsv, EmployeeSummary.class);

        assertEquals(2, csvRows.size());
        assertEquals("Alice", csvRows.get(0).employeeName);
        assertEquals(2, tsvRows.rows().size());
        assertEquals("Alice", tsvRows.rows().get(0).employeeName);
        assertTrue(tsvRows.report().success());
        assertEquals(List.of("employeeName", "annualSalary"), tsvRows.report().resolvedSchema());
    }

    @Test
    void filesSurfaceShouldLoadJsonRowsThroughGeneralLoader(@TempDir Path tempDir) throws IOException {
        Path json = writeFile(
                tempDir,
                "employees.json",
                """
                        [
                          {"id": 1, "name": "Alice", "department": "Engineering", "salary": 120000, "active": true},
                          {"id": 2, "name": "Bob", "department": "Finance", "salary": 90000, "active": true},
                          {"id": 3, "name": "Cara", "department": "Engineering", "salary": 130000, "active": true}
                        ]
                        """
        );

        List<Employee> rows = PojoLensFiles.json(json, Employee.class);
        List<Employee> result = PojoLensSql
                .parse("where department = 'Engineering' order by salary desc")
                .filter(rows, Employee.class);

        assertEquals(3, rows.size());
        assertEquals(List.of("Cara", "Alice"), result.stream().map(row -> row.name).toList());
    }

    @Test
    void filesSurfaceShouldLoadJsonlRowsWithRuntimeDefaults(@TempDir Path tempDir) throws IOException {
        Path jsonl = writeFile(
                tempDir,
                "employees.jsonl",
                """
                        {"employeeName":"Alice","department":"engineering","annualSalary":120000}

                        {"employeeName":"Cara","department":"engineering","annualSalary":130000}
                        """
        );

        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setJsonDefaults(
                JsonOptions.builder()
                        .skipEmptyLines(true)
                        .enumCaseInsensitive(true)
                        .build()
        );

        JsonLoadResult<JsonDepartmentRow> rows = runtime.files().jsonlWithReport(jsonl, JsonDepartmentRow.class);

        assertEquals(2, rows.rows().size());
        assertEquals(JsonDepartmentCode.ENGINEERING, rows.rows().get(0).department);
        assertTrue(rows.report().success());
        assertEquals(List.of("employeeName", "department", "annualSalary"), rows.report().resolvedSchema());
    }

    @Test
    void jsonRowsShouldMaterializeNestedPathsFromObjects(@TempDir Path tempDir) throws IOException {
        Path json = writeFile(
                tempDir,
                "nested.json",
                """
                        {
                          "id": 7,
                          "address": {
                            "city": "Amsterdam",
                            "geo": {
                              "countryCode": "NL"
                            }
                          }
                        }
                        """
        );

        List<NestedJsonRow> rows = PojoLensFiles.json(json, NestedJsonRow.class);

        assertEquals(1, rows.size());
        assertEquals(7, rows.get(0).id);
        assertNotNull(rows.get(0).address);
        assertEquals("Amsterdam", rows.get(0).address.city);
        assertNotNull(rows.get(0).address.geo);
        assertEquals("NL", rows.get(0).address.geo.countryCode);
    }

    @Test
    void jsonRowsShouldReportUnknownFieldsWithStructuredDiagnostics(@TempDir Path tempDir) throws IOException {
        Path json = writeFile(
                tempDir,
                "unknown-field.json",
                """
                        [
                          {"employeeName": "Alice", "annualSalary": 120000, "unknown": true}
                        ]
                        """
        );

        JsonLoadException error = assertThrows(
                JsonLoadException.class,
                () -> PojoLensFiles.json(json, EmployeeSummary.class)
        );

        assertEquals("schema", error.report().failureStage());
        assertEquals(1, error.report().failureRowNumber());
        assertEquals("unknown", error.report().failureField());
        assertEquals(List.of("unknown"), error.report().rejectedFields());
        assertTrue(error.getMessage().contains("contains unmapped fields"));
    }

    @Test
    void jsonRowsShouldReportMissingPrimitiveFieldsWithStructuredDiagnostics(@TempDir Path tempDir) throws IOException {
        Path json = writeFile(
                tempDir,
                "missing-primitive.json",
                """
                        [
                          {"employeeName": "Alice"}
                        ]
                        """
        );

        JsonLoadException error = assertThrows(
                JsonLoadException.class,
                () -> PojoLensFiles.json(json, EmployeeSummary.class)
        );

        assertEquals("schema", error.report().failureStage());
        assertEquals(1, error.report().failureRowNumber());
        assertEquals(List.of("annualSalary"), error.report().missingFields());
        assertTrue(error.getMessage().contains("missing required fields"));
    }

    private static Path writeFile(Path tempDir, String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    static final class JsonDepartmentRow {
        String employeeName;
        JsonDepartmentCode department;
        int annualSalary;

        JsonDepartmentRow() {
        }
    }

    enum JsonDepartmentCode {
        ENGINEERING,
        FINANCE
    }

    static final class NestedJsonRow {
        int id;
        Address address;

        NestedJsonRow() {
        }
    }

    static final class Address {
        String city;
        Geo geo;

        Address() {
        }
    }

    static final class Geo {
        String countryCode;

        Geo() {
        }
    }
}
