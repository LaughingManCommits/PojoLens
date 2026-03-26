package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;

import laughing.man.commits.PojoLens;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlLikeTypedBindContractTest {

    @Test
    public void typedBindShouldApplyOrderByAscWithoutExplicitSort() {
        List<Employee> source = sampleEmployees();

        List<Employee> rows = PojoLensSql.parse("where salary >= 90000 order by salary asc")
                .bindTyped(source, Employee.class)
                .filter();

        assertEquals(Arrays.asList(90000, 110000, 120000, 130000), salaries(rows));
    }

    @Test
    public void typedBindShouldApplyOrderByDescWithoutExplicitSort() {
        List<Employee> source = sampleEmployees();

        List<Employee> rows = PojoLensSql.parse("where salary >= 90000 order by salary desc")
                .bindTyped(source, Employee.class)
                .filter();

        assertEquals(Arrays.asList(130000, 120000, 110000, 90000), salaries(rows));
    }

    @Test
    public void typedBindWithoutOrderByShouldRetainCurrentUnsortedBehavior() {
        List<Employee> source = sampleEmployees();

        List<Employee> rows = PojoLensSql.parse("where salary >= 100000")
                .bindTyped(source, Employee.class)
                .filter();

        assertEquals(Arrays.asList("Alice", "Cara", "Dan"), names(rows));
    }

    @Test
    public void typedBindShouldCaptureProjectionTypeAtBindTime() {
        List<Employee> source = sampleEmployees();

        List<EmployeeSummary> rows = PojoLensSql.parse("select name as employeeName, salary as annualSalary where salary >= 120000 order by salary desc")
                .bindTyped(source, EmployeeSummary.class)
                .filter();

        assertEquals(2, rows.size());
        assertEquals("Cara", rows.get(0).employeeName);
        assertEquals(130000, rows.get(0).annualSalary);
        assertEquals("Alice", rows.get(1).employeeName);
        assertEquals(120000, rows.get(1).annualSalary);
    }

    @Test
    public void typedBindShouldSupportJoinExecutionPath() {
        List<Company> companies = sampleCompanies();
        List<CompanyEmployee> employees = sampleCompanyEmployees();
        Map<String, List<?>> joinSources = new HashMap<>();
        joinSources.put("employees", employees);

        List<Company> rows = PojoLensSql.parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .bindTyped(companies, Company.class, joinSources)
                .filter();

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
    }

    @Test
    public void typedBindShouldMatchDirectFilterExecution() {
        List<Employee> source = sampleEmployees();
        SqlLikeQuery query = PojoLensSql.parse("select name as employeeName, salary as annualSalary where salary >= 90000 order by salary desc");

        List<EmployeeSummary> direct = query.filter(source, EmployeeSummary.class);
        List<EmployeeSummary> typed = query.bindTyped(source, EmployeeSummary.class).filter();

        assertEquals(summaryRows(direct), summaryRows(typed));
    }

    @Test
    public void typedBindShouldRemainReusableAcrossRepeatedJoinFilters() {
        List<Company> companies = sampleCompanies();
        List<CompanyEmployee> employees = sampleCompanyEmployees();
        Map<String, List<?>> joinSources = new HashMap<>();
        joinSources.put("employees", employees);

        SqlLikeBoundQuery<Company> bound = PojoLensSql.parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .bindTyped(companies, Company.class, joinSources);

        List<Company> first = bound.filter();
        List<Company> second = bound.filter();

        assertEquals(companyIds(first), companyIds(second));
    }

    @Test
    public void typedBindShouldRemainReusableAcrossRepeatedStatsCharts() {
        List<Employee> source = sampleEmployees();
        SqlLikeBoundQuery<DepartmentCount> bound = PojoLensSql.parse("select department, count(*) as total group by department")
                .bindTyped(source, DepartmentCount.class);
        ChartSpec spec = ChartSpec.of(ChartType.BAR, "department", "total");

        ChartData first = bound.chart(spec);
        ChartData second = bound.chart(spec);

        assertEquals(first.getLabels(), second.getLabels());
        assertEquals(first.getDatasets().get(0).getValues(), second.getDatasets().get(0).getValues());
    }

    private static List<Integer> salaries(List<Employee> rows) {
        return rows.stream().map(r -> r.salary).collect(Collectors.toList());
    }

    private static List<String> names(List<Employee> rows) {
        return rows.stream().map(r -> r.name).collect(Collectors.toList());
    }

    private static List<String> summaryRows(List<EmployeeSummary> rows) {
        return rows.stream()
                .map(r -> r.employeeName + ":" + r.annualSalary)
                .collect(Collectors.toList());
    }

    private static List<Integer> companyIds(List<Company> rows) {
        return rows.stream().map(r -> r.id).collect(Collectors.toList());
    }

}





