package laughing.man.commits.natural;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.PojoLensNatural;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import laughing.man.commits.testutil.WindowTestFixtures.WindowRowNumberProjection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
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
                .parse("show employees where team is Engineering and active is true "
                        + "sort by annual pay descending limit 10")
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

    @Test
    public void docsRecipeNaturalJoinQueryShouldWork() {
        DatasetBundle bundle = DatasetBundle.of(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees())
        );

        List<Company> rows = PojoLensNatural
                .parse("from the companies as company join the employees as employee "
                        + "on the company id equals the employee company id "
                        + "show the company where the employee title is Engineer")
                .filter(bundle, Company.class);

        assertEquals(List.of("Acme"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void docsRecipeNaturalWindowQualifyQueryShouldWork() {
        List<WindowRowNumberProjection> rows = PojoLensNatural
                .parse("show department as dept, name, salary, "
                        + "row number by department ordered by salary descending then id ascending as rn "
                        + "where active is true qualify rn is at most 1 sort by dept ascending")
                .filter(sampleEmployees(), WindowRowNumberProjection.class);

        assertEquals(2, rows.size());
        assertEquals(List.of("Cara", "Bob"), rows.stream().map(row -> row.name).toList());
        assertEquals(List.of(1L, 1L), rows.stream().map(row -> row.rn).toList());
    }

    @Test
    public void docsRecipeNaturalTemplateWithComputedFieldShouldWork() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setComputedFieldRegistry(ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .build());

        List<ComputedSalaryRow> rows = runtime.natural()
                .template(
                        "show me name, adjusted salary "
                                + "where adjusted salary is at least :minSalary sort by adjusted salary descending",
                        "minSalary"
                )
                .bind(Map.of("minSalary", 130000.0))
                .filter(sampleEmployees(), ComputedSalaryRow.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
        assertEquals(List.of(143000.0, 132000.0), rows.stream().map(row -> row.adjustedSalary).toList());
    }

    @Test
    public void docsRecipeNaturalReportDefinitionShouldWork() {
        ReportDefinition<DepartmentCount> report = ReportDefinition.natural(
                PojoLensNatural.parse(
                        "show department, count of employees as total "
                                + "where active is true group by department sort by department ascending"
                ),
                DepartmentCount.class,
                ChartSpec.of(ChartType.BAR, "department", "total")
        );

        List<DepartmentCount> rows = report.rows(sampleEmployees());
        ChartData chart = report.chart(sampleEmployees());

        assertEquals(List.of("Engineering", "Finance"), rows.stream().map(row -> row.department).toList());
        assertEquals(List.of(2L, 1L), rows.stream().map(row -> row.total).toList());
        assertEquals(ChartType.BAR, chart.getType());
    }

    @Test
    public void docsRecipeNaturalOperatorAliasesShouldWork() {
        List<Employee> rows = PojoLensNatural
                .parse("show me the employees who are active and the department containing ine "
                        + "and the salary greater than or equal to 120000 "
                        + "ordered by the salary in descending order")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
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

    public static class ComputedSalaryRow {
        public String name;
        public double adjustedSalary;

        public ComputedSalaryRow() {
        }
    }
}
