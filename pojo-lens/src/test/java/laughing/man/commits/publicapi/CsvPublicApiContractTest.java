package laughing.man.commits.publicapi;

import laughing.man.commits.csv.CsvLoadReport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvPublicApiContractTest {

    @Test
    void stableCsvLoadReportDiagnosticsShouldRemainAvailable() throws Exception {
        requirePublicMethod(CsvLoadReport.class, "missingColumns");
        requirePublicMethod(CsvLoadReport.class, "dataRecordCount");
    }

    private static Method requirePublicMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = type.getMethod(name, parameterTypes);
        assertTrue(Modifier.isPublic(method.getModifiers()),
                () -> "Expected public method: " + type.getSimpleName() + "." + name);
        return method;
    }
}
