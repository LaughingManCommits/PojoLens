package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;

import laughing.man.commits.PojoLens;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlLikeLintModeTest {

    @Test
    public void lintWarningsShouldBeDeterministicAndNonBlocking() {
        SqlLikeQuery query = PojoLensSql.parse("select * from companies where name = 'Acme' limit 1");

        List<SqlLikeLintWarning> warnings = query.lintWarnings();
        assertEquals(3, warnings.size());
        assertEquals(SqlLikeLintCodes.SELECT_WILDCARD, warnings.get(0).code());
        assertEquals(SqlLikeLintCodes.LIMIT_WITHOUT_ORDER, warnings.get(1).code());
        assertEquals(SqlLikeLintCodes.INLINE_STRING_LITERAL, warnings.get(2).code());

        List<Company> rows = query.filter(sampleCompanies(), Company.class);
        assertEquals(1, rows.size());
        assertEquals("Acme", rows.get(0).name);
    }

    @Test
    public void lintWarningsShouldSupportSuppressionByCode() {
        SqlLikeQuery query = PojoLensSql.parse("select * from companies where name = 'Acme' limit 1")
                .suppressLintWarnings(SqlLikeLintCodes.SELECT_WILDCARD, SqlLikeLintCodes.INLINE_STRING_LITERAL);

        List<SqlLikeLintWarning> warnings = query.lintWarnings();
        assertEquals(1, warnings.size());
        assertEquals(SqlLikeLintCodes.LIMIT_WITHOUT_ORDER, warnings.get(0).code());
    }

    @Test
    public void explainShouldIncludeLintWarningsOnlyWhenLintModeEnabled() {
        SqlLikeQuery query = PojoLensSql.parse("select * from companies where name = 'Acme' limit 1");

        assertFalse(query.isLintModeEnabled());
        assertFalse(query.explain().containsKey("lintWarnings"));

        Map<String, Object> explain = query.lintMode().explain();
        assertTrue(query.lintMode().isLintModeEnabled());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) explain.get("lintWarnings");
        assertEquals(3, warnings.size());
        assertEquals(SqlLikeLintCodes.SELECT_WILDCARD, warnings.get(0).get("code"));
        assertEquals(SqlLikeLintCodes.LIMIT_WITHOUT_ORDER, warnings.get(1).get("code"));
        assertEquals(SqlLikeLintCodes.INLINE_STRING_LITERAL, warnings.get(2).get("code"));
    }

    @Test
    public void runtimeShouldApplyLintModeIndependentlyFromStrictTyping() {
        PojoLensRuntime runtime = PojoLens.newRuntime();
        assertFalse(runtime.isLintMode());
        assertFalse(runtime.isStrictParameterTypes());

        runtime.setLintMode(true);
        SqlLikeQuery lintQuery = runtime.parse("select * from companies limit 1");
        assertTrue(runtime.isLintMode());
        assertTrue(lintQuery.isLintModeEnabled());
        assertFalse(lintQuery.isStrictParameterTypesEnabled());

        runtime.setStrictParameterTypes(true);
        SqlLikeQuery bothModesQuery = runtime.parse("select * from companies where id = :id limit 1");
        assertTrue(bothModesQuery.isLintModeEnabled());
        assertTrue(bothModesQuery.isStrictParameterTypesEnabled());
    }

    @Test
    public void lintWarningsShouldDetectParameterizedPaginationWithoutOrderBy() {
        SqlLikeQuery query = PojoLensSql.parse("select * from companies limit :limit");

        List<SqlLikeLintWarning> warnings = query.lintWarnings();
        assertEquals(2, warnings.size());
        assertEquals(SqlLikeLintCodes.SELECT_WILDCARD, warnings.get(0).code());
        assertEquals(SqlLikeLintCodes.LIMIT_WITHOUT_ORDER, warnings.get(1).code());
    }
}





