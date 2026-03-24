package laughing.man.commits.publicapi;

import laughing.man.commits.PojoLens;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.sqllike.SqlLikeCursor;
import laughing.man.commits.sqllike.SqlLikeLintCodes;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.SqlLikeTemplate;
import laughing.man.commits.sqllike.SqlParams;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.PublicApiModels.SqlLikeRunningTotalRow;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublicApiSqlCoverageTest extends AbstractPublicApiCoverageTest {

    @Test
    public void sqlLikeQueryOfShouldNormalizeAndExposeSource() {
        SqlLikeQuery query = SqlLikeQuery.of("  where integerField >= 2  ");
        assertEquals("where integerField >= 2", query.source());
    }

    @Test
    public void sqlLikeQuerySortShouldReturnNullWithoutOrderAndDirectionWithOrder() {
        assertNull(SqlLikeQuery.of("where integerField >= 1").sort());
        assertEquals(Sort.DESC, SqlLikeQuery.of("where integerField >= 1 order by integerField desc").sort());
    }

    @Test
    public void sqlLikeQueryBindTypedWithJoinSourcesShouldReturnExecutableRows() {
        List<Company> companies = sampleCompanies();
        List<CompanyEmployee> employees = sampleCompanyEmployees();
        Map<String, List<?>> joinSources = new HashMap<>();
        joinSources.put("employees", employees);

        List<Company> rows = SqlLikeQuery
                .of("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .bindTyped(companies, Company.class, joinSources)
                .filter();

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
    }

    @Test
    public void sqlLikeTemplateFactoryAndSqlParamsShouldBeUsableFromPublicApi() {
        SqlLikeTemplate template = PojoLens.template(
                "where department = :dept and active = :active",
                "dept",
                "active"
        );
        List<Employee> rows = template
                .bind(SqlParams.builder().put("dept", "Engineering").put("active", true).build())
                .filter(sampleEmployees(), Employee.class);

        assertEquals(2, rows.size());
        assertEquals(List.of("Alice", "Cara"),
                rows.stream().map(r -> r.name).toList());
    }

    @Test
    public void strictParameterTypingControlsShouldBeUsableFromPublicApi() {
        PojoLensRuntime runtime = PojoLens.newRuntime();
        assertFalse(runtime.isStrictParameterTypes());

        runtime.setStrictParameterTypes(true);
        assertTrue(runtime.isStrictParameterTypes());

        SqlLikeQuery strictQuery = runtime.parse("where salary >= :minSalary");
        assertTrue(strictQuery.isStrictParameterTypesEnabled());
        assertFalse(strictQuery.strictParameterTypes(false).isStrictParameterTypesEnabled());
        assertTrue(PojoLens.parse("where salary >= :minSalary").strictParameterTypes().isStrictParameterTypesEnabled());
    }

    @Test
    public void keysetCursorControlsShouldBeUsableFromPublicApi() {
        SqlLikeCursor cursor = PojoLens.newKeysetCursorBuilder()
                .put("salary", 120000)
                .put("id", 1)
                .build();

        String token = cursor.toToken();
        SqlLikeCursor decoded = PojoLens.parseKeysetCursor(token);
        assertEquals(cursor, decoded);

        List<Employee> rows = PojoLens
                .parse("where active = true order by salary desc, id desc limit 20")
                .keysetAfter(decoded)
                .filter(sampleEmployees(), Employee.class);

        assertEquals(1, rows.size());
        assertEquals("Bob", rows.get(0).name);
    }

    @Test
    public void streamingControlsShouldBeUsableFromPublicApi() {
        List<String> sqlNames = PojoLens
                .parse("where active = true limit 2")
                .stream(sampleEmployees(), Employee.class)
                .map(row -> row.name)
                .toList();
        assertEquals(List.of("Alice", "Bob"), sqlNames);

        List<String> sqlBoundNames = PojoLens
                .parse("where active = true limit 2")
                .bindTyped(sampleEmployees(), Employee.class)
                .stream()
                .map(row -> row.name)
                .toList();
        assertEquals(List.of("Alice", "Bob"), sqlBoundNames);
    }

    @Test
    public void sqlLikeAggregateWindowFilterAndExplainShouldBeUsableFromPublicApi() {
        String query = "select department as dept, name, salary, "
                + "sum(salary) over (partition by department order by salary desc "
                + "rows between unbounded preceding and current row) as runningTotal "
                + "where active = true order by dept asc, runningTotal asc";
        List<SqlLikeRunningTotalRow> rows = PojoLens
                .parse(query)
                .filter(sampleEmployees(), SqlLikeRunningTotalRow.class);

        assertEquals(3, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals("Cara", rows.get(0).name);
        assertEquals(130000L, rows.get(0).runningTotal);
        assertEquals("Engineering", rows.get(1).dept);
        assertEquals("Alice", rows.get(1).name);
        assertEquals(250000L, rows.get(1).runningTotal);
        assertEquals("Finance", rows.get(2).dept);
        assertEquals("Bob", rows.get(2).name);
        assertEquals(90000L, rows.get(2).runningTotal);

        Map<String, Object> explain = PojoLens
                .parse(query)
                .explain(sampleEmployees(), SqlLikeRunningTotalRow.class);

        assertEquals("alias/computed", explain.get("projectionMode"));
        @SuppressWarnings("unchecked")
        Map<String, Object> stageCounts = (Map<String, Object>) explain.get("stageRowCounts");
        @SuppressWarnings("unchecked")
        Map<String, Object> whereStage = (Map<String, Object>) stageCounts.get("where");
        @SuppressWarnings("unchecked")
        Map<String, Object> qualifyStage = (Map<String, Object>) stageCounts.get("qualify");
        assertEquals(true, whereStage.get("applied"));
        assertEquals(4, ((Number) whereStage.get("before")).intValue());
        assertEquals(3, ((Number) whereStage.get("after")).intValue());
        assertEquals(false, qualifyStage.get("applied"));
    }

    @Test
    public void lintControlsShouldBeUsableFromPublicApi() {
        PojoLensRuntime runtime = PojoLens.newRuntime();
        assertFalse(runtime.isLintMode());

        runtime.setLintMode(true);
        assertTrue(runtime.isLintMode());

        SqlLikeQuery lintQuery = runtime.parse("select * from companies limit 1");
        assertTrue(lintQuery.isLintModeEnabled());
        assertEquals(1, lintQuery.suppressLintWarnings(SqlLikeLintCodes.SELECT_WILDCARD).lintWarnings().size());
        assertFalse(PojoLens.parse("select * from companies limit 1").lintMode(false).isLintModeEnabled());
    }
}
