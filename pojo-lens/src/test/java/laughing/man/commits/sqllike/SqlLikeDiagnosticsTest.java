package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;
import laughing.man.commits.domain.Foo;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlLikeDiagnosticsTest {

    // --- AST-level diagnostics (no source class) ---

    @Test
    public void simpleFilterDiagnosticsReturnsValid() {
        QueryDiagnostics d = PojoLensSql.parse("where integerField >= 5").diagnostics();
        assertTrue(d.valid());
        assertTrue(d.errors().isEmpty());
        assertTrue(d.requiredParams().isEmpty());
        assertTrue(d.referencedFields().contains("integerField"));
        assertTrue(d.outputFields().isEmpty());
        assertTrue(d.joinSources().isEmpty());
        assertFalse(d.hasSubqueries());
    }

    @Test
    public void explicitSelectOutputFields() {
        QueryDiagnostics d = PojoLensSql.parse(
                "select stringField, integerField from Foo").diagnostics();
        assertTrue(d.valid());
        assertEquals(List.of("stringField", "integerField"), d.outputFields());
        assertTrue(d.referencedFields().contains("stringField"));
        assertTrue(d.referencedFields().contains("integerField"));
    }

    @Test
    public void selectWithAliasOutputFields() {
        QueryDiagnostics d = PojoLensSql.parse(
                "select integerField as score from Foo").diagnostics();
        assertTrue(d.valid());
        assertEquals(List.of("score"), d.outputFields());
        assertTrue(d.referencedFields().contains("integerField"));
    }

    @Test
    public void wildcardSelectHasEmptyOutputFields() {
        QueryDiagnostics d = PojoLensSql.parse("select * from Foo").diagnostics();
        assertTrue(d.valid());
        assertTrue(d.outputFields().isEmpty());
    }

    @Test
    public void requiredParamsCollected() {
        QueryDiagnostics d = PojoLensSql.parse(
                "where department = :dept and salary >= :minSalary").diagnostics();
        assertTrue(d.valid());
        assertTrue(d.requiredParams().contains("dept"));
        assertTrue(d.requiredParams().contains("minSalary"));
        assertEquals(2, d.requiredParams().size());
    }

    @Test
    public void joinSourceCollected() {
        QueryDiagnostics d = PojoLensSql.parse(
                "select id from Employee join companies on id = companyId").diagnostics();
        assertTrue(d.valid());
        assertTrue(d.joinSources().contains("companies"));
    }

    @Test
    public void subqueryDetected() {
        QueryDiagnostics d = PojoLensSql.parse(
                "select id from Employee where department in (select department from Employee)").diagnostics();
        assertTrue(d.valid());
        assertTrue(d.hasSubqueries());
    }

    @Test
    public void noSubqueryFlagForPlainQuery() {
        QueryDiagnostics d = PojoLensSql.parse("where integerField > 0").diagnostics();
        assertFalse(d.hasSubqueries());
    }

    @Test
    public void lintWarningForWildcardSelect() {
        QueryDiagnostics d = PojoLensSql.parse("select * from Foo").diagnostics();
        assertTrue(d.valid());
        assertFalse(d.lintWarnings().isEmpty());
        assertEquals(SqlLikeLintCodes.SELECT_WILDCARD, d.lintWarnings().get(0).code());
    }

    @Test
    public void lintWarningForLimitWithoutOrder() {
        QueryDiagnostics d = PojoLensSql.parse(
                "where integerField > 0 limit 10").diagnostics();
        assertTrue(d.valid());
        assertTrue(d.lintWarnings().stream()
                .anyMatch(w -> w.code().equals(SqlLikeLintCodes.LIMIT_WITHOUT_ORDER)));
    }

    @Test
    public void suppressedLintWarningsExcluded() {
        QueryDiagnostics d = PojoLensSql.parse("select * from Foo")
                .suppressLintWarnings(SqlLikeLintCodes.SELECT_WILDCARD)
                .diagnostics();
        assertTrue(d.valid());
        assertTrue(d.lintWarnings().isEmpty());
    }

    // --- Validation diagnostics (with source class) ---

    @Test
    public void validQueryPassesValidation() {
        QueryDiagnostics d = PojoLensSql.parse("where integerField >= 5")
                .diagnostics(Foo.class, Foo.class);
        assertTrue(d.valid());
        assertTrue(d.errors().isEmpty());
    }

    @Test
    public void unknownFieldProducesValidationError() {
        QueryDiagnostics d = PojoLensSql.parse("where missingField = 'abc'")
                .diagnostics(Foo.class, Foo.class);
        assertFalse(d.valid());
        assertEquals(1, d.errors().size());
        QueryDiagnosticsError error = d.errors().get(0);
        assertEquals("EQ-SQL-VAL-001", error.code());
        assertTrue(error.message().contains("missingField"));
    }

    @Test
    public void validationErrorDoesNotSuppressLintWarnings() {
        QueryDiagnostics d = PojoLensSql.parse(
                "select * from Foo where missingField = 'abc'")
                .diagnostics(Foo.class, Foo.class);
        assertFalse(d.valid());
        assertFalse(d.errors().isEmpty());
        assertFalse(d.lintWarnings().isEmpty());
    }

    @Test
    public void validationErrorCodeParsedFromMessage() {
        QueryDiagnostics d = PojoLensSql.parse("where missingField = 'abc'")
                .diagnostics(Foo.class, Foo.class);
        assertFalse(d.valid());
        assertTrue(d.errors().get(0).code().startsWith("EQ-SQL-"));
    }

    @Test
    public void validJoinQueryPassesValidation() {
        QueryDiagnostics d = PojoLensSql.parse(
                "select name, id from Employee join assignments on id = companyId")
                .diagnostics(Employee.class, Employee.class,
                        JoinBindings.of("assignments", List.of(new CompanyEmployee(1, "Engineer"))));
        assertTrue(d.valid());
    }

    @Test
    public void missingJoinSourceProducesValidationError() {
        QueryDiagnostics d = PojoLensSql.parse(
                "select name from Employee join companies on id = id")
                .diagnostics(Employee.class, Employee.class);
        assertFalse(d.valid());
        assertFalse(d.errors().isEmpty());
    }

    @Test
    public void groupByAndOrderByFieldsInReferencedFields() {
        QueryDiagnostics d = PojoLensSql.parse(
                "select department, count(*) as cnt from Employee group by department order by cnt")
                .diagnostics();
        assertTrue(d.referencedFields().contains("department"));
        assertEquals(List.of("department", "cnt"), d.outputFields());
    }
}
