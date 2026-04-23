package laughing.man.commits.publicapi;

import laughing.man.commits.PojoLensSql;

import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.PageResult;
import laughing.man.commits.sqllike.PlanPreviewPredicate;
import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.QueryExposurePolicy;
import laughing.man.commits.sqllike.SqlLikePlanPreview;
import laughing.man.commits.sqllike.SqlLikePushdownAdapter;
import laughing.man.commits.sqllike.SqlLikePushdownMode;
import laughing.man.commits.sqllike.SqlLikePushdownPreview;
import laughing.man.commits.sqllike.SqlLikePushdownRequest;
import laughing.man.commits.sqllike.SqlLikePushdownResult;
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
        JoinBindings joinBindings = JoinBindings.of("employees", employees);

        List<Company> rows = SqlLikeQuery
                .of("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .bindTyped(companies, Company.class, joinBindings)
                .filter();

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
    }

    @Test
    public void sqlLikeTemplateFactoryAndSqlParamsShouldBeUsableFromPublicApi() {
        SqlLikeTemplate template = PojoLensSql.template(
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
        PojoLensRuntime runtime = new PojoLensRuntime();
        assertFalse(runtime.isStrictParameterTypes());

        runtime.setStrictParameterTypes(true);
        assertTrue(runtime.isStrictParameterTypes());

        SqlLikeQuery strictQuery = runtime.parse("where salary >= :minSalary");
        assertTrue(strictQuery.isStrictParameterTypesEnabled());
        assertFalse(strictQuery.strictParameterTypes(false).isStrictParameterTypesEnabled());
        assertTrue(PojoLensSql.parse("where salary >= :minSalary").strictParameterTypes().isStrictParameterTypesEnabled());
    }

    @Test
    public void keysetCursorControlsShouldBeUsableFromPublicApi() {
        SqlLikeCursor cursor = SqlLikeCursor.builder()
                .put("salary", 120000)
                .put("id", 1)
                .build();

        String token = cursor.toToken();
        SqlLikeCursor decoded = SqlLikeCursor.fromToken(token);
        assertEquals(cursor, decoded);

        List<Employee> rows = PojoLensSql.parse("where active = true order by salary desc, id desc limit 20")
                .keysetAfter(decoded)
                .filter(sampleEmployees(), Employee.class);

        assertEquals(1, rows.size());
        assertEquals("Bob", rows.get(0).name);
    }

    @Test
    public void pageResultHelperShouldBeUsableFromPublicApi() {
        PageResult<Employee> page = PojoLensSql
                .parse("where active = true order by salary desc, id desc limit 2")
                .filterPage(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), page.rows().stream().map(row -> row.name).toList());
        assertTrue(page.hasMore());
        assertTrue(page.nextCursor().isPresent());
    }

    @Test
    public void streamingControlsShouldBeUsableFromPublicApi() {
        List<String> sqlNames = PojoLensSql.parse("where active = true limit 2")
                .stream(sampleEmployees(), Employee.class)
                .map(row -> row.name)
                .toList();
        assertEquals(List.of("Alice", "Bob"), sqlNames);

        List<String> sqlBoundNames = PojoLensSql.parse("where active = true limit 2")
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
        List<SqlLikeRunningTotalRow> rows = PojoLensSql.parse(query)
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

        Map<String, Object> explain = PojoLensSql.parse(query)
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
        PojoLensRuntime runtime = new PojoLensRuntime();
        assertFalse(runtime.isLintMode());

        runtime.setLintMode(true);
        assertTrue(runtime.isLintMode());

        SqlLikeQuery lintQuery = runtime.parse("select * from companies limit 1");
        assertTrue(lintQuery.isLintModeEnabled());
        assertEquals(1, lintQuery.suppressLintWarnings(SqlLikeLintCodes.SELECT_WILDCARD).lintWarnings().size());
        assertFalse(PojoLensSql.parse("select * from companies limit 1").lintMode(false).isLintModeEnabled());
    }

    @Test
    public void diagnosticsShouldBeUsableFromPublicApi() {
        QueryDiagnostics diagnostics = PojoLensSql
                .parse("select name, salary where department = :dept order by salary desc")
                .diagnostics(Employee.class, Employee.class);

        assertTrue(diagnostics.valid());
        assertTrue(diagnostics.errors().isEmpty());
        assertEquals(List.of("dept"), diagnostics.requiredParams());
        assertTrue(diagnostics.referencedFields().contains("department"));
        assertTrue(diagnostics.outputFields().contains("salary"));
    }

    @Test
    public void exposurePolicyShouldBeUsableFromPublicApi() {
        QueryExposurePolicy policy = QueryExposurePolicy.builder()
                .allowFields("name", "department", "salary")
                .build();

        QueryDiagnostics diagnostics = PojoLensSql
                .parse("select name, salary where department = 'Engineering'")
                .exposurePolicy(policy)
                .diagnostics(Employee.class, Employee.class);

        assertTrue(policy.restrictsFields());
        assertTrue(policy.allowsField("salary"));
        assertTrue(diagnostics.valid());
        assertTrue(diagnostics.errors().isEmpty());
    }

    @Test
    public void planPreviewShouldBeUsableFromPublicApi() {
        SqlLikePlanPreview preview = PojoLensSql
                .parse("select name from Employee where department = :dept and active = true order by name asc")
                .planPreview();

        assertEquals("Employee", preview.source());
        assertEquals(List.of("dept"), preview.requiredParams());
        assertEquals("name", preview.selectFields().get(0).field());
        assertEquals("name", preview.orderFields().get(0).field());

        PlanPreviewPredicate expression = preview.filterExpression();
        assertEquals("AND", expression.operator());
        assertEquals("department", expression.children().get(0).filter().field());
        assertEquals("active", expression.children().get(1).filter().field());
    }

    @Test
    public void pushdownPreviewShouldBeUsableFromPublicApi() {
        SqlLikePushdownPreview preview = PojoLensSql
                .parse("select department, count(*) as total where active = true group by department")
                .pushdownPreview();

        assertEquals(SqlLikePushdownMode.SPLIT, preview.mode());
        assertTrue(preview.requiresSplitExecution());
        assertEquals(List.of("WHERE"), preview.pushableStages());
        assertTrue(preview.inMemoryStages().contains("GROUP_BY"));
        assertTrue(preview.fallbackReasons().contains("GROUPING_UNSUPPORTED"));
    }

    @Test
    public void pushdownAdapterBridgeShouldBeUsableFromPublicApi() {
        List<Employee> sourceRows = sampleEmployees();
        SqlLikePushdownAdapter adapter = new SqlLikePushdownAdapter() {
            @Override
            public <T> SqlLikePushdownResult<T> fetch(SqlLikePushdownRequest request, Class<T> rowClass) {
                assertEquals("where active = true order by salary desc limit 2", request.queryText());
                assertTrue(request.requestsStage("WHERE"));
                List<T> rows = sourceRows.stream()
                        .filter(row -> row.active)
                        .map(rowClass::cast)
                        .toList();
                return SqlLikePushdownResult.of(rows, request.requestedStages(), sourceRows.size(), Map.of());
            }
        };

        List<Employee> rows = PojoLensSql
                .parse("where active = true order by salary desc limit 2")
                .filterWithPushdown(adapter, Employee.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
    }
}






