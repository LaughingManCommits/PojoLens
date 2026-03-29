package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;

import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SqlLikeStrictParameterTypeModeTest {

    @Test
    public void defaultModeShouldRemainBackwardCompatibleForLateTypeMismatch() {
        List<Employee> rows = PojoLensSql.parse("where salary >= :minSalary")
                .params(Map.of("minSalary", "not-a-number"))
                .filter(sampleEmployees(), Employee.class);

        assertEquals(0, rows.size());
    }

    @Test
    public void strictQueryModeShouldRejectWhereParameterTypeMismatchEarly() {
        try {
            PojoLensSql.parse("where salary >= :minSalary")
                    .strictParameterTypes()
                    .params(Map.of("minSalary", "not-a-number"))
                    .filter(sampleEmployees(), Employee.class);
            fail("Expected strict parameter typing failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH));
            assertTrue(ex.getMessage().contains("minSalary"));
            assertTrue(ex.getMessage().contains("salary"));
            assertTrue(ex.getMessage().contains("numeric value"));
        }
    }

    @Test
    public void strictRuntimeModeShouldRejectBooleanParameterTypeMismatchEarly() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setStrictParameterTypes(true);

        try {
            runtime.parse("where active = :active")
                    .params(Map.of("active", "true"))
                    .filter(sampleEmployees(), Employee.class);
            fail("Expected strict runtime parameter typing failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH));
            assertTrue(ex.getMessage().contains("active"));
            assertTrue(ex.getMessage().contains("Boolean value"));
        }
    }

    @Test
    public void strictModeShouldAllowCompatibleNumericParameterTypes() {
        List<Employee> rows = PojoLensSql.parse("where salary >= :minSalary order by salary desc")
                .strictParameterTypes()
                .params(Map.of("minSalary", 110000L))
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice", "Dan"),
                rows.stream().map(r -> r.name).toList());
    }

    @Test
    public void strictModeShouldRejectHavingParameterTypeMismatchEarly() {
        try {
            PojoLensSql.parse("select department, count(*) as total group by department having total >= :minCount")
                    .strictParameterTypes()
                    .params(Map.of("minCount", "2"))
                    .filter(sampleEmployees(), DepartmentCount.class);
            fail("Expected strict HAVING parameter typing failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH));
            assertTrue(ex.getMessage().contains("minCount"));
            assertTrue(ex.getMessage().contains("HAVING"));
        }
    }

}







