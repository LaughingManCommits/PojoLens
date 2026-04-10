package laughing.man.commits;

import laughing.man.commits.csv.CsvCoercionPolicy;
import laughing.man.commits.csv.CsvLoadException;
import laughing.man.commits.csv.CsvLoadResult;
import laughing.man.commits.csv.CsvOptions;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PojoLensCsvTest {

    @Test
    void csvRowsShouldLoadIntoTypedObjectsAndReuseSqlLikeQuerying(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "employees.csv",
                """
                        id,name,department,salary,active
                        1,Alice,Engineering,120000,true
                        2,Bob,Finance,90000,true
                        3,Cara,Engineering,130000,true
                        4,Dan,Engineering,110000,false
                        """
        );

        List<Employee> rows = PojoLensCsv.read(csv, Employee.class);
        List<Employee> result = PojoLensSql
                .parse("where department = 'Engineering' and active = true order by salary desc")
                .filter(rows, Employee.class);

        assertEquals(4, rows.size());
        assertNull(rows.get(0).hireDate);
        assertEquals(List.of("Cara", "Alice"), result.stream().map(row -> row.name).toList());
    }

    @Test
    void csvRowsShouldHonorDelimiterAndTrimOptions(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "summaries.csv",
                """
                        employeeName ; annualSalary
                         Alice ; 120000
                         Cara ; 130000
                        """
        );

        List<EmployeeSummary> rows = PojoLensCsv.read(
                csv,
                EmployeeSummary.class,
                CsvOptions.builder().delimiter(';').trim(true).build()
        );

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).employeeName);
        assertEquals(120000, rows.get(0).annualSalary);
        assertEquals("Cara", rows.get(1).employeeName);
        assertEquals(130000, rows.get(1).annualSalary);
    }

    @Test
    void csvRowsShouldReturnLoadReportForSuccessfulRead(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "report-success.csv",
                """
                        employeeName ; annualSalary
                         Alice ; 120000
                         Cara ; 130000
                        """
        );

        CsvLoadResult<EmployeeSummary> result = PojoLensCsv.readWithReport(
                csv,
                EmployeeSummary.class,
                CsvOptions.builder().delimiter(';').trim(true).build()
        );

        assertEquals(2, result.rows().size());
        assertTrue(result.report().success());
        assertEquals(csv, result.report().path());
        assertEquals(EmployeeSummary.class, result.report().rowType());
        assertEquals(List.of("employeeName", "annualSalary"), result.report().resolvedSchema());
        assertEquals(3, result.report().logicalRecordCount());
        assertEquals(2, result.report().dataRecordCount());
        assertEquals(2, result.report().loadedRowCount());
        assertTrue(result.report().rejectedColumns().isEmpty());
        assertTrue(result.report().missingColumns().isEmpty());
        assertNull(result.report().failureStage());
        assertNull(result.report().failureRowNumber());
        assertNull(result.report().failureColumn());
        assertNull(result.report().failureMessage());
        assertTrue(result.report().durationNanos() >= 0L);
    }

    @Test
    void csvRowsShouldMaterializeNestedPathsFromHeaders(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "nested.csv",
                """
                        id,address.city,address.geo.countryCode
                        7,Amsterdam,NL
                        """
        );

        List<NestedRow> rows = PojoLensCsv.read(csv, NestedRow.class);

        assertEquals(1, rows.size());
        assertEquals(7, rows.get(0).id);
        assertNotNull(rows.get(0).address);
        assertEquals("Amsterdam", rows.get(0).address.city);
        assertNotNull(rows.get(0).address.geo);
        assertEquals("NL", rows.get(0).address.geo.countryCode);
    }

    @Test
    void csvRowsShouldLoadQuotedMultilineFieldsAcrossBomCrLfAndBlankLines(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "multiline.csv",
                "\uFEFFid,notes,active\r\n"
                        + "1,\"first line\r\nsecond line\",true\r\n"
                        + "\r\n"
                        + "2,\"top\r\n\r\nbottom\",false\r\n"
        );

        List<MultilineRow> rows = PojoLensCsv.read(csv, MultilineRow.class);

        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).id);
        assertEquals("first line\nsecond line", rows.get(0).notes);
        assertTrue(rows.get(0).active);
        assertEquals(2, rows.get(1).id);
        assertEquals("top\n\nbottom", rows.get(1).notes);
        assertFalse(rows.get(1).active);
    }

    @Test
    void csvRowsShouldReportLogicalRecordStartLineForInvalidIntegerInMultilineRecord(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "multiline-invalid-integer.csv",
                "id,notes,salary\n"
                        + "1,\"first line\nsecond line\",120000\n"
                        + "2,\"third line\nfourth line\",12k\n"
        );

        CsvLoadException error = assertThrows(
                CsvLoadException.class,
                () -> PojoLensCsv.read(csv, MultilineSalaryRow.class)
        );

        assertEquals("coerce", error.report().failureStage());
        assertEquals(4, error.report().failureRowNumber());
        assertEquals("salary", error.report().failureColumn());
        assertEquals(3, error.report().logicalRecordCount());
        assertEquals(2, error.report().dataRecordCount());
        assertEquals(1, error.report().loadedRowCount());
        assertTrue(error.getMessage().contains("CSV row 4 column salary"));
        assertTrue(error.getMessage().contains("12k"));
    }

    @Test
    void csvRowsShouldReportLogicalRecordStartLineForUnmatchedQuotedMultilineField(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "multiline-unmatched-quote.csv",
                "id,notes,active\n"
                        + "1,\"first line\nsecond line,true\n"
        );

        CsvLoadException error = assertThrows(
                CsvLoadException.class,
                () -> PojoLensCsv.read(csv, MultilineRow.class)
        );

        assertEquals("parse", error.report().failureStage());
        assertEquals(2, error.report().failureRowNumber());
        assertEquals(1, error.report().logicalRecordCount());
        assertEquals(0, error.report().dataRecordCount());
        assertTrue(error.getMessage().contains("CSV row 2 has an unmatched quote"));
    }

    @Test
    void runtimeCsvShouldApplyConfiguredDefaults(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "runtime-defaults.csv",
                """
                        employeeName ; annualSalary
                         Alice ; 120000
                         Cara ; 130000
                        """
        );

        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setCsvDefaults(CsvOptions.builder().delimiter(';').trim(true).build());

        List<EmployeeSummary> rows = runtime.csv().read(csv, EmployeeSummary.class);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).employeeName);
        assertEquals(120000, rows.get(0).annualSalary);
    }

    @Test
    void runtimeCsvShouldAllowPerCallOverridesLayeredOnRuntimeDefaults(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "runtime-override.csv",
                """
                        nickname,bonus,salary,hireDate,reviewedAt,department
                        ,NULL,"1.234,50",15/01/2024,15/01/2024 10:30:00,engineering
                        """
        );

        CsvCoercionPolicy policy = CsvCoercionPolicy.builder()
                .blankStringAsNull(true)
                .nullToken("NULL")
                .enumCaseInsensitive(true)
                .decimalSeparator(',')
                .groupingSeparator('.')
                .datePattern("dd/MM/uuuu")
                .dateTimePattern("dd/MM/uuuu HH:mm:ss")
                .build();
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setCsvDefaults(
                CsvOptions.builder()
                        .delimiter(';')
                        .trim(true)
                        .coercionPolicy(policy)
                        .build()
        );

        List<CoercionRow> rows = runtime.csv().read(
                csv,
                CoercionRow.class,
                runtime.getCsvDefaults().toBuilder().delimiter(',').build()
        );

        assertEquals(1, rows.size());
        assertNull(rows.get(0).nickname);
        assertNull(rows.get(0).bonus);
        assertEquals(1234.5d, rows.get(0).salary);
        assertEquals(LocalDate.of(2024, 1, 15), rows.get(0).hireDate);
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), rows.get(0).reviewedAt);
        assertEquals(DepartmentCode.ENGINEERING, rows.get(0).department);
        assertEquals(';', runtime.getCsvDefaults().delimiter());
        assertTrue(runtime.getCsvDefaults().coercionPolicy().blankStringAsNull());
    }

    @Test
    void csvRowsShouldHonorConfiguredCoercionPolicy(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "coercion.csv",
                """
                        nickname;bonus;salary;hireDate;reviewedAt;department
                        ;NULL;1.234,50;15/01/2024;15/01/2024 10:30:00;engineering
                        """
        );

        CsvCoercionPolicy policy = CsvCoercionPolicy.builder()
                .blankStringAsNull(true)
                .nullToken("NULL")
                .enumCaseInsensitive(true)
                .decimalSeparator(',')
                .groupingSeparator('.')
                .datePattern("dd/MM/uuuu")
                .dateTimePattern("dd/MM/uuuu HH:mm:ss")
                .build();

        List<CoercionRow> rows = PojoLensCsv.read(
                csv,
                CoercionRow.class,
                CsvOptions.builder()
                        .delimiter(';')
                        .coercionPolicy(policy)
                        .build()
        );

        assertEquals(1, rows.size());
        assertNull(rows.get(0).nickname);
        assertNull(rows.get(0).bonus);
        assertEquals(1234.5d, rows.get(0).salary);
        assertEquals(LocalDate.of(2024, 1, 15), rows.get(0).hireDate);
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), rows.get(0).reviewedAt);
        assertEquals(DepartmentCode.ENGINEERING, rows.get(0).department);
    }

    @Test
    void csvRowsShouldRejectConfiguredNullTokensForPrimitiveTargets(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "primitive-null-token.csv",
                """
                        id
                        NULL
                        """
        );

        CsvLoadException error = assertThrows(
                CsvLoadException.class,
                () -> PojoLensCsv.read(
                        csv,
                        PrimitiveIdRow.class,
                        CsvOptions.builder()
                                .coercionPolicy(CsvCoercionPolicy.builder().nullToken("NULL").build())
                                .build()
                )
        );

        assertEquals("coerce", error.report().failureStage());
        assertEquals("id", error.report().failureColumn());
        assertTrue(error.getMessage().contains("CSV row 2 column id"));
        assertTrue(error.getMessage().contains("null value is not allowed for primitive targets"));
    }

    @Test
    void csvRowsShouldRejectInvalidIntegerWithRowAndColumnContext(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "bad-employees.csv",
                """
                        id,name,department,salary,active
                        1,Alice,Engineering,120000,true
                        2,Bob,Finance,12k,true
                        """
        );

        CsvLoadException error = assertThrows(
                CsvLoadException.class,
                () -> PojoLensCsv.read(csv, Employee.class)
        );

        assertEquals("coerce", error.report().failureStage());
        assertEquals(3, error.report().failureRowNumber());
        assertEquals("salary", error.report().failureColumn());
        assertTrue(error.getMessage().contains("CSV row 3 column salary"));
        assertTrue(error.getMessage().contains("12k"));
        assertTrue(error.getMessage().contains("Integer"));
    }

    @Test
    void csvRowsShouldExposeCoercionFailureReportThroughDefaultReadPath(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "bad-employees-report.csv",
                """
                        id,name,department,salary,active
                        1,Alice,Engineering,120000,true
                        2,Bob,Finance,12k,true
                        """
        );

        CsvLoadException error = assertThrows(
                CsvLoadException.class,
                () -> PojoLensCsv.read(csv, Employee.class)
        );

        assertEquals("coerce", error.report().failureStage());
        assertEquals(3, error.report().failureRowNumber());
        assertEquals("salary", error.report().failureColumn());
        assertTrue(error.report().failureMessage().contains("12k"));
        assertEquals(List.of("id", "name", "department", "salary", "active"), error.report().resolvedSchema());
        assertEquals(3, error.report().logicalRecordCount());
        assertEquals(2, error.report().dataRecordCount());
        assertEquals(1, error.report().loadedRowCount());
        assertTrue(error.report().rejectedColumns().isEmpty());
        assertTrue(error.report().missingColumns().isEmpty());
        assertFalse(error.report().success());
    }

    @Test
    void csvRowsShouldRejectDuplicateHeaders(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "duplicate-headers.csv",
                """
                        id,id
                        1,2
                        """
        );

        CsvLoadException error = assertThrows(
                CsvLoadException.class,
                () -> PojoLensCsv.read(csv, Employee.class)
        );

        assertEquals("header", error.report().failureStage());
        assertEquals(List.of("id"), error.report().rejectedColumns());
        assertTrue(error.getMessage().contains("CSV header column 'id' is duplicated"));
    }

    @Test
    void csvRowsShouldExposeRejectedColumnsInHeaderFailureReport(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "unknown-header.csv",
                """
                        id,unknown
                        1,value
                        """
        );

        CsvLoadException error = assertThrows(
                CsvLoadException.class,
                () -> PojoLensCsv.read(csv, Employee.class)
        );

        assertEquals("header", error.report().failureStage());
        assertEquals(1, error.report().failureRowNumber());
        assertEquals("unknown", error.report().failureColumn());
        assertEquals(List.of("unknown"), error.report().rejectedColumns());
        assertTrue(error.report().missingColumns().isEmpty());
        assertEquals(2, error.report().logicalRecordCount());
        assertEquals(1, error.report().dataRecordCount());
        assertEquals(0, error.report().loadedRowCount());
        assertTrue(error.report().failureMessage().contains("does not map to Employee"));
    }

    @Test
    void csvRowsShouldRejectMissingPrimitiveBackedHeaderColumns(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "missing-required-column.csv",
                """
                        id,name,department,hireDate,active
                        1,Alice,Engineering,2024-01-15,true
                        """
        );

        CsvLoadException error = assertThrows(
                CsvLoadException.class,
                () -> PojoLensCsv.read(csv, Employee.class)
        );

        assertEquals("header", error.report().failureStage());
        assertTrue(error.report().rejectedColumns().isEmpty());
        assertEquals(List.of("salary"), error.report().missingColumns());
        assertEquals(2, error.report().logicalRecordCount());
        assertEquals(1, error.report().dataRecordCount());
        assertTrue(error.getMessage().contains("CSV header for Employee is missing required columns"));
        assertTrue(error.getMessage().contains("salary"));
    }

    @Test
    void csvRowsShouldExposePreflightFailureReportForMissingFiles(@TempDir Path tempDir) {
        Path csv = tempDir.resolve("missing.csv");

        CsvLoadException error = assertThrows(
                CsvLoadException.class,
                () -> PojoLensCsv.read(csv, Employee.class)
        );

        assertEquals("preflight", error.report().failureStage());
        assertEquals(csv, error.report().path());
        assertEquals(Employee.class, error.report().rowType());
        assertTrue(error.report().resolvedSchema().isEmpty());
        assertTrue(error.report().rejectedColumns().isEmpty());
        assertTrue(error.report().missingColumns().isEmpty());
        assertEquals(0, error.report().logicalRecordCount());
        assertEquals(0, error.report().dataRecordCount());
        assertEquals(0, error.report().loadedRowCount());
        assertTrue(error.getMessage().contains("path must point to an existing file"));
    }

    @Test
    void csvRowsShouldRejectRowTypesWithoutBindableFields(@TempDir Path tempDir) throws IOException {
        Path csv = writeCsv(
                tempDir,
                "empty-row.csv",
                """
                        ignored
                        value
                        """
        );

        CsvLoadException error = assertThrows(
                CsvLoadException.class,
                () -> PojoLensCsv.read(csv, EmptyRow.class)
        );

        assertEquals("schema", error.report().failureStage());
        assertEquals(0, error.report().logicalRecordCount());
        assertEquals(0, error.report().dataRecordCount());
        assertTrue(error.report().resolvedSchema().isEmpty());
        assertTrue(error.getMessage().contains("exposes no bindable fields"));
    }

    private static Path writeCsv(Path tempDir, String fileName, String contents) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, contents);
        return path;
    }

    static final class NestedRow {
        int id;
        Address address;

        public NestedRow() {
        }
    }

    static final class Address {
        String city;
        Geo geo;

        public Address() {
        }
    }

    static final class Geo {
        String countryCode;

        public Geo() {
        }
    }

    static final class MultilineRow {
        int id;
        String notes;
        boolean active;

        public MultilineRow() {
        }
    }

    static final class MultilineSalaryRow {
        int id;
        String notes;
        int salary;

        public MultilineSalaryRow() {
        }
    }

    static final class CoercionRow {
        String nickname;
        Integer bonus;
        double salary;
        LocalDate hireDate;
        LocalDateTime reviewedAt;
        DepartmentCode department;

        public CoercionRow() {
        }
    }

    static final class PrimitiveIdRow {
        int id;

        public PrimitiveIdRow() {
        }
    }

    static final class EmptyRow {
        public EmptyRow() {
        }
    }

    enum DepartmentCode {
        ENGINEERING,
        FINANCE
    }
}
