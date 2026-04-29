package laughing.man.commits.metamodel;

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

public class MetamodelBatchGeneratorTest {

    @Test
    public void writeDefaultStringsShouldWriteSharedFieldConstants(@TempDir Path tempDir) throws IOException {
        List<MetamodelGenerationResult> results =
                MetamodelBatchGenerator.writeDefaultStrings(tempDir, Employee.class);

        assertEquals(1, results.size());
        assertEquals("EmployeeFields", results.get(0).metamodel().simpleName());
        assertTrue(Files.exists(results.get(0).outputPath()));
        String source = Files.readString(results.get(0).outputPath());
        assertTrue(source.contains("public final class EmployeeFields"));
        assertTrue(source.contains("public static final String DEPARTMENT = \"department\";"));
    }

    @Test
    public void writeDefaultTypedShouldWriteTypedFieldConstants(@TempDir Path tempDir) throws IOException {
        List<MetamodelGenerationResult> results =
                MetamodelBatchGenerator.writeDefaultTyped(tempDir, Employee.class);

        assertEquals(1, results.size());
        assertEquals("EmployeeTypedFields", results.get(0).metamodel().simpleName());
        assertTrue(Files.exists(results.get(0).outputPath()));
        String source = Files.readString(results.get(0).outputPath());
        assertTrue(source.contains("public final class EmployeeTypedFields"));
        assertTrue(source.contains("TypedField<Employee, String> DEPARTMENT"));
        assertTrue(source.contains("TypedField<Employee, Integer> SALARY"));
    }

    @Test
    public void writeShouldRejectDuplicateQualifiedTargets(@TempDir Path tempDir) {
        List<MetamodelGenerationRequest> requests = List.of(
                MetamodelGenerationRequest.strings(Employee.class, "com.acme.generated", "EmployeeFields"),
                MetamodelGenerationRequest.typed(Employee.class, "com.acme.generated", "EmployeeFields")
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> MetamodelBatchGenerator.write(tempDir, requests)
        );

        assertTrue(ex.getMessage().contains("Duplicate metamodel target 'com.acme.generated.EmployeeFields'"));
    }
}
