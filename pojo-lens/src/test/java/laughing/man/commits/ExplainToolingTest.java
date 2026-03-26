package laughing.man.commits;

import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExplainToolingTest {

    @Test
    public void fluentExplainShouldIncludePipelineAndCacheMetadata() {
        List<Employee> employees = sampleEmployees();
        QueryBuilder builder = PojoLensCore.newQueryBuilder(employees)
                .addRule("active", true, Clauses.EQUAL, Separator.AND)
                .addGroup("department")
                .addCount("total")
                .addMetric("salary", Metric.SUM, "totalSalary")
                .addTimeBucket("hireDate", TimeBucket.MONTH, "period")
                .addHaving("total", 1, Clauses.BIGGER_EQUAL)
                .addOrder("totalSalary", 1)
                .offset(1)
                .limit(5);

        Map<String, Object> explain = builder.explain();

        assertEquals("fluent", explain.get("type"));
        assertEquals(1, explain.get("offset"));
        assertEquals(5, explain.get("limit"));
        assertEquals(1, ((Number) explain.get("whereRuleCount")).intValue());
        assertEquals(1, ((Number) explain.get("havingRuleCount")).intValue());
        assertTrue(((List<?>) explain.get("metrics")).toString().contains("SUM:salary:totalSalary"));
        assertTrue(((List<?>) explain.get("timeBuckets")).toString().contains("period:hireDate:MONTH"));
        assertTrue(explain.containsKey("statsPlanCache"));
    }

    @Test
    public void sqlLikeExplainShouldIncludeClauseSummaryAndCacheMetadata() {
        PojoLensSql.parse("where active = true");
        Map<String, Object> explain = PojoLensSql.parse("select department, count(*) as total, bucket(hireDate,'month') as period "
                        + "where active = true and department = :dept "
                        + "group by department, period having total >= :minTotal order by total desc limit 3 offset 2")
                .explain();

        assertEquals("sql-like", explain.get("type"));
        assertEquals(
                "select department, count(*) as total, bucket(hireDate,'month') as period "
                        + "where active = true and department = :dept "
                        + "group by department, period having total >= :minTotal order by total desc limit 3 offset 2",
                explain.get("source"));
        assertEquals(explain.get("source"), explain.get("normalizedQuery"));
        assertEquals(2, ((Number) explain.get("whereRuleCount")).intValue());
        assertEquals(1, ((Number) explain.get("havingRuleCount")).intValue());
        assertEquals(2, explain.get("offset"));
        assertEquals(3, explain.get("limit"));
        assertEquals("DESC", explain.get("resolvedSortDirection"));
        assertEquals("alias/computed", explain.get("projectionMode"));
        assertTrue(((List<?>) explain.get("metrics")).toString().contains("COUNT:*:total"));
        assertTrue(((List<?>) explain.get("timeBuckets")).toString().contains("period:hireDate:MONTH"));
        assertTrue(((Map<?, ?>) explain.get("joinSourceBindings")).isEmpty());
        Map<String, Object> parameterSnapshot = parameterSnapshot(explain);
        assertEquals("unresolved", parameter("dept", parameterSnapshot).get("status"));
        assertEquals("unresolved", parameter("minTotal", parameterSnapshot).get("status"));
        assertTrue(explain.containsKey("sqlLikeCache"));
        assertTrue(explain.containsKey("statsPlanCache"));
    }

    @Test
    public void sqlLikeExplainShouldIncludeBoundParameterMetadata() {
        Map<String, Object> explain = PojoLensSql.parse("where department = :dept and salary >= :minSalary")
                .params(Map.of("dept", "Engineering", "minSalary", 100000))
                .explain();

        Map<String, Object> parameterSnapshot = parameterSnapshot(explain);
        assertEquals("bound", parameter("dept", parameterSnapshot).get("status"));
        assertEquals("scalar", parameter("dept", parameterSnapshot).get("shape"));
        assertEquals("String", parameter("dept", parameterSnapshot).get("type"));
        assertEquals(Boolean.TRUE, parameter("dept", parameterSnapshot).get("redacted"));
        assertEquals("bound", parameter("minSalary", parameterSnapshot).get("status"));
        assertEquals("Integer", parameter("minSalary", parameterSnapshot).get("type"));
    }

    @Test
    public void sqlLikeExplainShouldReportJoinBindingStatus() {
        Map<String, Object> explain = PojoLensSql.parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .explain();

        Map<String, String> bindings = joinSourceBindings(explain);
        assertEquals("unbound", bindings.get("employees"));

        Map<String, Object> executedExplain = PojoLensSql.parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .explain(sampleCompanies(), Map.of("employees", sampleCompanyEmployees()), Company.class);

        assertEquals("bound", joinSourceBindings(executedExplain).get("employees"));
    }

    @Test
    public void sqlLikeExplainShouldReportAllJoinBindingsForMultiJoinQueries() {
        Map<String, Object> explain = PojoLensSql.parse("select * from companies "
                        + "left join employees on companies.id = employees.companyId "
                        + "left join badges on employees.id = badges.employeeId")
                .explain();

        Map<String, String> bindings = joinSourceBindings(explain);
        assertEquals("unbound", bindings.get("employees"));
        assertEquals("unbound", bindings.get("badges"));
    }

    @Test
    public void sqlLikeExecutionExplainShouldIncludeStageCountsForNonAggregateQueries() {
        Map<String, Object> explain = PojoLensSql.parse("where active = true order by salary desc limit 1 offset 1")
                .explain(sampleEmployees(), Employee.class);

        assertEquals("direct", explain.get("projectionMode"));
        assertEquals("DESC", explain.get("resolvedSortDirection"));
        assertEquals("where active = true order by salary desc limit 1 offset 1", explain.get("normalizedQuery"));
        assertEquals(1, explain.get("offset"));
        assertTrue(parameterSnapshot(explain).isEmpty());
        assertTrue(joinSourceBindings(explain).isEmpty());

        Map<String, Object> stageCounts = stageCounts(explain);
        assertStage(stageCounts, "where", true, 4, 3);
        assertStage(stageCounts, "group", false, 3, 3);
        assertStage(stageCounts, "having", false, 3, 3);
        assertStage(stageCounts, "order", true, 3, 3);
        assertStage(stageCounts, "limit", true, 3, 1);
    }

    @Test
    public void sqlLikeExecutionExplainShouldIncludeStageCountsForAggregateQueries() {
        Map<String, Object> explain = PojoLensSql.parse("select department, count(*) as total group by department having total >= 2 order by total desc limit 1")
                .explain(sampleEmployees(), DepartmentCount.class);

        assertEquals("alias/computed", explain.get("projectionMode"));
        Map<String, Object> stageCounts = stageCounts(explain);
        assertStage(stageCounts, "where", false, 4, 4);
        assertStage(stageCounts, "group", true, 4, 2);
        assertStage(stageCounts, "having", true, 2, 1);
        assertStage(stageCounts, "order", true, 1, 1);
        assertStage(stageCounts, "limit", true, 1, 1);
    }

    @Test
    public void sqlLikeExecutionExplainShouldIncludeQualifyStageCounts() {
        Map<String, Object> explain = PojoLensSql.parse("select department as dept, name, salary, "
                        + "row_number() over (partition by department order by salary desc) as rn "
                        + "where active = true qualify rn <= 1 order by dept asc")
                .explain(sampleEmployees(), RankedEmployee.class);

        Map<String, Object> stageCounts = stageCounts(explain);
        assertStage(stageCounts, "where", true, 4, 3);
        assertStage(stageCounts, "group", false, 3, 3);
        assertStage(stageCounts, "having", false, 3, 3);
        assertStage(stageCounts, "qualify", true, 3, 2);
        assertStage(stageCounts, "order", true, 2, 2);
        assertStage(stageCounts, "limit", false, 2, 2);
    }

    @Test
    public void explainShouldSurfaceConfiguredComputedFields() {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .build();

        Map<String, Object> sqlExplain = PojoLensSql.parse("select name, adjustedSalary where adjustedSalary >= 120000")
                .computedFields(registry)
                .explain();

        Map<String, Object> fluentExplain = PojoLensCore.newQueryBuilder(sampleEmployees())
                .computedFields(registry)
                .addRule("adjustedSalary", 120000.0, Clauses.BIGGER_EQUAL)
                .addField("name")
                .addField("adjustedSalary")
                .explain();

        assertTrue(((List<?>) sqlExplain.get("computedFields")).toString().contains("adjustedSalary:salary * 1.1:Double"));
        assertTrue(((List<?>) fluentExplain.get("computedFields")).toString().contains("adjustedSalary:salary * 1.1:Double"));
    }

    @Test
    public void explainShouldSurfaceExplicitTimeBucketCalendarPreset() {
        Map<String, Object> sqlExplain = PojoLensSql.parse("select bucket(hireDate,'week','Europe/Amsterdam','sunday') as period, count(*) as total group by period")
                .explain();

        Map<String, Object> fluentExplain = PojoLensCore.newQueryBuilder(sampleEmployees())
                .addTimeBucket("hireDate", TimeBucketPreset.week().withZone("Europe/Amsterdam").withWeekStart("sunday"), "period")
                .addCount("total")
                .explain();

        assertTrue(((List<?>) sqlExplain.get("timeBuckets")).toString().contains("period:hireDate:WEEK:Europe/Amsterdam:SUNDAY"));
        assertTrue(((List<?>) fluentExplain.get("timeBuckets")).toString().contains("period:hireDate:WEEK:Europe/Amsterdam:SUNDAY"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parameterSnapshot(Map<String, Object> explain) {
        return (Map<String, Object>) explain.get("parameterSnapshot");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parameter(String name, Map<String, Object> parameterSnapshot) {
        return (Map<String, Object>) parameterSnapshot.get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> joinSourceBindings(Map<String, Object> explain) {
        return (Map<String, String>) explain.get("joinSourceBindings");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stageCounts(Map<String, Object> explain) {
        return (Map<String, Object>) explain.get("stageRowCounts");
    }

    @SuppressWarnings("unchecked")
    private static void assertStage(Map<String, Object> stageCounts,
                                    String stageName,
                                    boolean expectedApplied,
                                    int expectedBefore,
                                    int expectedAfter) {
        Map<String, Object> stage = (Map<String, Object>) stageCounts.get(stageName);
        assertEquals(expectedApplied, stage.get("applied"));
        assertEquals(expectedBefore, ((Number) stage.get("before")).intValue());
        assertEquals(expectedAfter, ((Number) stage.get("after")).intValue());
    }

    public static class RankedEmployee {
        public String dept;
        public String name;
        public int salary;
        public long rn;

        public RankedEmployee() {
        }
    }
}


