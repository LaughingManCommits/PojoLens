package laughing.man.commits.publicapi;

import laughing.man.commits.PojoLensCsv;
import laughing.man.commits.csv.CsvLoadException;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvPublicApiCoverageTest {

    @Test
    void csvFailureReportsShouldExposeSplitHeaderDiagnosticsFromPublicApi(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("employees-missing-salary.csv");
        Files.writeString(
                csv,
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
        assertEquals(List.of("salary"), error.report().missingColumns());
        assertTrue(error.report().rejectedColumns().isEmpty());
        assertEquals(2, error.report().logicalRecordCount());
        assertEquals(1, error.report().dataRecordCount());
    }
}
