package laughing.man.commits;

import laughing.man.commits.sqllike.SqlParams;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SqlLikePaginationParameterSupportTest {

    @Test
    public void shouldApplyLimitAndOffsetFromNamedParameters() {
        List<Employee> rows = PojoLens
                .parse("where active = true order by salary desc limit :limit offset :offset")
                .params(Map.of("limit", 2, "offset", 1))
                .filter(samplePageSource(), Employee.class);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).name);
        assertEquals("Dan", rows.get(1).name);
    }

    @Test
    public void shouldApplyLimitAndOffsetFromTypedSqlParams() {
        List<Employee> rows = PojoLens
                .parse("where active = true order by salary desc limit :limit offset :offset")
                .params(SqlParams.builder()
                        .put("limit", 2L)
                        .put("offset", 1)
                        .build())
                .filter(samplePageSource(), Employee.class);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).name);
        assertEquals("Dan", rows.get(1).name);
    }

    @Test
    public void paramsShouldRequirePaginationParameters() {
        try {
            PojoLens.parse("where active = true order by salary desc limit :limit offset :offset")
                    .params(Map.of("limit", 2));
            fail("Expected missing pagination parameter failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PARAM_MISSING));
            assertTrue(ex.getMessage().contains("offset"));
        }
    }

    @Test
    public void executionShouldRejectUnresolvedPaginationParameters() {
        try {
            PojoLens.parse("where active = true order by salary desc limit :limit")
                    .filter(samplePageSource(), Employee.class);
            fail("Expected unresolved pagination parameter failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PARAM_UNRESOLVED));
            assertTrue(ex.getMessage().contains("limit"));
        }
    }

    @Test
    public void paramsShouldRejectInvalidPaginationParameterTypeAndRange() {
        try {
            PojoLens.parse("where active = true order by salary desc limit :limit offset :offset")
                    .params(Map.of("limit", "two", "offset", 1));
            fail("Expected invalid LIMIT parameter type");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH));
            assertTrue(ex.getMessage().contains("LIMIT"));
        }

        try {
            PojoLens.parse("where active = true order by salary desc limit :limit offset :offset")
                    .params(Map.of("limit", 2, "offset", 1.5));
            fail("Expected invalid OFFSET integer check failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH));
            assertTrue(ex.getMessage().contains("OFFSET"));
        }

        try {
            PojoLens.parse("where active = true order by salary desc limit :limit offset :offset")
                    .params(Map.of("limit", 2, "offset", -1));
            fail("Expected invalid OFFSET range failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH));
            assertTrue(ex.getMessage().contains(">= 0"));
        }
    }

    private static List<Employee> samplePageSource() {
        Date now = new Date();
        return Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, true)
        );
    }
}

