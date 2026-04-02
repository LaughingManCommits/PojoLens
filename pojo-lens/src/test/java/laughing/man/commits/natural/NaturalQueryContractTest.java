package laughing.man.commits.natural;

import laughing.man.commits.PojoLensNatural;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NaturalQueryContractTest {

    @Test
    public void shouldExecuteWildcardFilterSortAndLimit() {
        List<Employee> rows = PojoLensNatural
                .parse("show employees where active is true sort by salary descending limit 2")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void shouldExecuteAliasedProjectionWithParameters() {
        List<EmployeeSummary> rows = PojoLensNatural
                .parse("show name as employee name, salary as annual salary "
                        + "where department is :dept and salary is at least :minSalary sort by salary ascending")
                .params(Map.of("dept", "Engineering", "minSalary", 120000))
                .filter(sampleEmployees(), EmployeeSummary.class);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).employeeName);
        assertEquals(120000, rows.get(0).annualSalary);
        assertEquals("Cara", rows.get(1).employeeName);
        assertEquals(130000, rows.get(1).annualSalary);
    }

    @Test
    public void explainShouldExposeNaturalTypeAndEquivalentSqlLike() {
        Map<String, Object> explain = PojoLensNatural
                .parse("show employees where active is true sort by salary descending limit 2")
                .explain(sampleEmployees(), Employee.class);

        assertEquals("natural", explain.get("type"));
        assertEquals("show employees where active is true sort by salary descending limit 2", explain.get("source"));
        assertEquals("select * where active = true order by salary desc limit 2", explain.get("normalizedQuery"));
        assertEquals(explain.get("normalizedQuery"), explain.get("equivalentSqlLike"));
    }

    @Test
    public void runtimeNaturalEntryPointShouldApplyTelemetryAndRemainChartCapable() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        NaturalQuery query = runtime.natural().parse("show salary, name where active is true sort by salary descending limit 2");

        ChartData chart = query.chart(
                sampleEmployees(),
                Employee.class,
                ChartSpec.of(ChartType.BAR, "name", "salary")
        );

        assertEquals(2, chart.getLabels().size());
        assertTrue(query.isStrictParameterTypesEnabled() == runtime.isStrictParameterTypes());
    }

    @Test
    public void shouldExecuteGroupedNaturalAggregateQueryWithHavingAndSort() {
        List<DepartmentCount> rows = PojoLensNatural
                .parse("show department, count of employees as total "
                        + "where active is true group by department having total is at least 2 sort by total descending")
                .filter(sampleEmployees(), DepartmentCount.class);

        assertEquals(1, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(2L, rows.get(0).total);
    }

    @Test
    public void runtimeNaturalVocabularyShouldResolveAliasesForExecutionAndExplain() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("salary", "annual pay", "pay")
                .field("department", "team")
                .build());

        NaturalQuery query = runtime.natural()
                .parse("show name, annual pay where team is Engineering sort by annual pay descending limit 2");

        List<Employee> rows = query.filter(sampleEmployees(), Employee.class);
        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
        assertEquals(List.of(130000, 120000), rows.stream().map(row -> row.salary).toList());

        Map<String, Object> explain = query.explain(sampleEmployees(), Employee.class);
        assertEquals(
                Map.of("annual pay", "salary", "team", "department"),
                explain.get("resolvedNaturalFields")
        );
        assertEquals(
                "select name, salary where department = 'Engineering' order by salary desc limit 2",
                explain.get("resolvedEquivalentSqlLike")
        );
    }

    @Test
    public void runtimeNaturalVocabularyShouldResolveGroupedAggregatesForExecutionAndExplain() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("salary", "annual pay")
                .field("department", "team")
                .build());

        NaturalQuery query = runtime.natural().parse(
                "show team as dept, sum of annual pay as total payroll, count of employees as headcount "
                        + "where active is true group by team having total payroll is at least 200000 "
                        + "sort by total payroll descending"
        );

        List<DepartmentPayrollRow> rows = query.filter(sampleEmployees(), DepartmentPayrollRow.class);
        assertEquals(1, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals(250000L, rows.get(0).totalPayroll);
        assertEquals(2L, rows.get(0).headcount);

        Map<String, Object> explain = query.explain(sampleEmployees(), DepartmentPayrollRow.class);
        assertEquals(
                Map.of("annual pay", "salary", "team", "department"),
                explain.get("resolvedNaturalFields")
        );
        assertEquals(
                "select department as dept, sum(salary) as totalPayroll, count(*) as headcount "
                        + "where active = true group by department having totalPayroll >= 200000 order by totalPayroll desc",
                explain.get("resolvedEquivalentSqlLike")
        );
    }

    @Test
    public void exactFieldMatchShouldWinOverVocabularyAlias() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("department", "salary")
                .build());

        List<Employee> rows = runtime.natural()
                .parse("show employees where salary is at least 120000 sort by salary descending")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void ambiguousVocabularyAliasShouldFailDeterministically() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("salary", "pay")
                .field("department", "pay")
                .build());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> runtime.natural()
                        .parse("show pay")
                        .filter(sampleEmployees(), Employee.class)
        );

        assertTrue(error.getMessage().contains("Ambiguous natural field term 'pay'"));
    }

    @Test
    public void unknownNaturalFieldTermShouldFailDeterministically() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PojoLensNatural.parse("show bonus").filter(sampleEmployees(), Employee.class)
        );

        assertTrue(error.getMessage().contains("Unknown natural field term 'bonus'"));
    }

    public static class DepartmentPayrollRow {
        public String dept;
        public long totalPayroll;
        public long headcount;

        public DepartmentPayrollRow() {
        }
    }
}
