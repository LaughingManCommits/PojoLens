package laughing.man.commits;

import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikeCursor;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.parser.SqlLikeParseException;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SqlLikeErrorCodesContractTest {

    @Test
    public void parseErrorsShouldExposeStableCodeAndTroubleshootingLink() {
        try {
            PojoLens.parse("where name =");
            fail("Expected parse error");
        } catch (SqlLikeParseException ex) {
            assertEquals(SqlLikeErrorCodes.PARSE_SYNTAX, ex.code());
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PARSE_SYNTAX));
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.troubleshootingLink(SqlLikeErrorCodes.PARSE_SYNTAX)));
        }
    }

    @Test
    public void validationErrorsShouldExposeStableCodeAndTroubleshootingLink() {
        try {
            PojoLens.parse("where missingField = 'abc'")
                    .filter(sampleEmployees(), Employee.class);
            fail("Expected validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD));
            assertTrue(ex.getMessage().contains(
                    SqlLikeErrorCodes.troubleshootingLink(SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD)));
        }
    }

    @Test
    public void parameterErrorsShouldExposeStableCodeAndTroubleshootingLink() {
        try {
            PojoLens.parse("where salary >= :min and department = :dept")
                    .params(Map.of("min", 100000));
            fail("Expected parameter error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PARAM_MISSING));
            assertTrue(ex.getMessage().contains(
                    SqlLikeErrorCodes.troubleshootingLink(SqlLikeErrorCodes.PARAM_MISSING)));
        }
    }

    @Test
    public void bindErrorsShouldExposeStableCodeAndTroubleshootingLink() {
        try {
            PojoLens.parse("where salary >= 1 order by salary asc, name desc").sort();
            fail("Expected bind error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.BIND_MIXED_ORDER_DIRECTIONS));
            assertTrue(ex.getMessage().contains(
                    SqlLikeErrorCodes.troubleshootingLink(SqlLikeErrorCodes.BIND_MIXED_ORDER_DIRECTIONS)));
        }
    }

    @Test
    public void joinBindingErrorsShouldExposeStableCodeAndTroubleshootingLink() {
        try {
            JoinBindings.builder()
                    .add("employees", sampleCompanyEmployees())
                    .add("employees", sampleCompanyEmployees());
            fail("Expected join binding error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.JOIN_DUPLICATE_SOURCE_BINDING));
            assertTrue(ex.getMessage().contains(
                    SqlLikeErrorCodes.troubleshootingLink(SqlLikeErrorCodes.JOIN_DUPLICATE_SOURCE_BINDING)));
        }
    }

    @Test
    public void runtimeErrorsShouldExposeStableCodeAndTroubleshootingLink() {
        try {
            PojoLens.parse("select name as employeeName where active = true")
                    .filter(sampleEmployees(), BrokenProjection.class);
            fail("Expected runtime projection error");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.RUNTIME_ALIASED_PROJECTION_FAILED));
            assertTrue(ex.getMessage().contains(
                    SqlLikeErrorCodes.troubleshootingLink(SqlLikeErrorCodes.RUNTIME_ALIASED_PROJECTION_FAILED)));
        }
    }

    @Test
    public void cursorErrorsShouldExposeStableCodeAndTroubleshootingLink() {
        try {
            PojoLens.parse("where active = true limit 10")
                    .keysetAfter(SqlLikeCursor.builder().put("salary", 120000).build());
            fail("Expected cursor error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.CURSOR_ORDER_REQUIRED));
            assertTrue(ex.getMessage().contains(
                    SqlLikeErrorCodes.troubleshootingLink(SqlLikeErrorCodes.CURSOR_ORDER_REQUIRED)));
        }
    }

    public static class BrokenProjection {
        public String employeeName;

        public BrokenProjection(String employeeName) {
            this.employeeName = employeeName;
        }
    }
}

