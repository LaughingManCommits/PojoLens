package laughing.man.commits.natural;

import laughing.man.commits.PojoLensNatural;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static laughing.man.commits.testutil.TimeBucketTestFixtures.sampleRows;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class NaturalDocsExamplesTest {

    @Test
    public void readmeControlledPlainEnglishQuickStartShouldWork() {
        List<Employee> rows = PojoLensNatural
                .parse("show employees where department is :dept and salary is at least :minSalary "
                        + "sort by salary descending limit 10")
                .params(Map.of("dept", "Engineering", "minSalary", 120000))
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void docsRecipeRuntimeVocabularyShouldWork() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("salary", "annual pay", "pay")
                .field("department", "team")
                .build());

        List<Employee> rows = runtime.natural()
                .parse("show employees where team is Engineering and active is true sort by annual pay descending limit 10")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void docsRecipeGroupedNaturalQueryShouldWork() {
        List<DepartmentHeadcountRow> rows = PojoLensNatural
                .parse("show department, count of employees as headcount "
                        + "where active is true "
                        + "group by department having headcount is at least 2 "
                        + "sort by headcount descending")
                .filter(sampleEmployees(), DepartmentHeadcountRow.class);

        assertEquals(1, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(2L, rows.get(0).headcount);
    }

    @Test
    public void docsRecipeNaturalTimeBucketQueryShouldWork() {
        List<PeriodPayrollRow> rows = PojoLensNatural
                .parse("show bucket hire date by month as period, sum of salary as payroll "
                        + "group by period sort by period ascending")
                .filter(sampleRows(), PeriodPayrollRow.class);

        assertEquals(List.of("2025-01", "2025-02"), rows.stream().map(row -> row.period).toList());
        assertEquals(List.of(300L, 450L), rows.stream().map(row -> row.payroll).toList());
    }

    @Test
    public void docsRecipeNaturalChartPhraseShouldWork() {
        ChartData chart = PojoLensNatural
                .parse("show department, count of employees as total "
                        + "where active is true group by department sort by total descending as bar chart")
                .chart(sampleEmployees(), DepartmentCount.class);

        assertEquals(ChartType.BAR, chart.getType());
        assertEquals(List.of("Engineering", "Finance"), chart.getLabels());
    }

    @Test
    public void docsRecipeAliasedProjectionShouldWork() {
        List<EmployeeSummary> rows = PojoLensNatural
                .parse("show name as employee name, salary as annual salary "
                        + "where department is Engineering and active is true and salary is at least 120000 "
                        + "sort by salary ascending")
                .filter(sampleEmployees(), EmployeeSummary.class);

        assertEquals(List.of("Alice", "Cara"), rows.stream().map(row -> row.employeeName).toList());
    }

    public static class PeriodPayrollRow {
        public String period;
        public long payroll;

        public PeriodPayrollRow() {
        }
    }

    public static class DepartmentHeadcountRow {
        public String department;
        public long headcount;

        public DepartmentHeadcountRow() {
        }
    }
}
