package laughing.man.commits;

import laughing.man.commits.csv.CsvOptions;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PojoLensCsv.read(csv, MultilineSalaryRow.class)
        );

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

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PojoLensCsv.read(csv, MultilineRow.class)
        );

        assertTrue(error.getMessage().contains("CSV row 2 has an unmatched quote"));
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

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PojoLensCsv.read(csv, Employee.class)
        );

        assertTrue(error.getMessage().contains("CSV row 3 column salary"));
        assertTrue(error.getMessage().contains("12k"));
        assertTrue(error.getMessage().contains("Integer"));
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

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PojoLensCsv.read(csv, Employee.class)
        );

        assertTrue(error.getMessage().contains("CSV header column 'id' is duplicated"));
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

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PojoLensCsv.read(csv, Employee.class)
        );

        assertTrue(error.getMessage().contains("CSV header for Employee is missing required columns"));
        assertTrue(error.getMessage().contains("salary"));
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
}
