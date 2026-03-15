package laughing.man.commits;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SqlLikeQueryContractTest {

    @Test
    public void parseShouldNormalizeSource() {
        SqlLikeQuery query = PojoLens.parse("  where stringField = 'abc'  ");
        assertEquals("where stringField = 'abc'", query.source());
    }

    @Test
    public void parseShouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> PojoLens.parse(null));
    }

    @Test
    public void parseShouldRejectBlank() {
        assertThrows(IllegalArgumentException.class, () -> PojoLens.parse("   "));
    }

    @Test
    public void bindTypedShouldReturnExecutableBoundQuery() {
        Object bound = PojoLens.parse("where name = 'abc'")
                .bindTyped(Collections.emptyList(), TestBean.class);
        assertNotNull(bound);
    }

    @Test
    public void filterShouldExecuteAgainstBoundData() {
        List<TestBean> source = Arrays.asList(
                new TestBean("abc", 1),
                new TestBean("xyz", 2)
        );
        List<TestBean> results = PojoLens.parse("where name = 'abc'").filter(source, TestBean.class);
        assertEquals(1, results.size());
        assertEquals("abc", results.get(0).name);
    }

    @Test
    public void havingShouldRunAfterAggregationBeforeOrderAndLimit() {
        List<DepartmentEmployee> source = Arrays.asList(
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("HR")
        );

        List<DepartmentCount> results = PojoLens
                .parse("select department, count(*) as total group by department having total >= 2 order by total asc limit 1")
                .filter(source, DepartmentCount.class);

        assertEquals(1, results.size());
        assertEquals("Finance", results.get(0).department);
        assertEquals(2L, results.get(0).total);
    }

    @Test
    public void havingAggregateExpressionShouldResolveToSelectedAggregateOutput() {
        List<DepartmentEmployee> source = Arrays.asList(
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Finance")
        );

        List<DepartmentCount> results = PojoLens
                .parse("select department, count(*) as total group by department having count(*) >= 2 order by total desc")
                .filter(source, DepartmentCount.class);

        assertEquals(1, results.size());
        assertEquals("Engineering", results.get(0).department);
        assertEquals(2L, results.get(0).total);
    }

    @Test
    public void havingShouldSupportOrConditions() {
        List<DepartmentEmployee> source = Arrays.asList(
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("HR")
        );

        List<DepartmentCount> results = PojoLens
                .parse("select department, count(*) as total group by department having total >= 3 or total = 1 order by total desc")
                .filter(source, DepartmentCount.class);

        assertEquals(2, results.size());
        List<String> departments = results.stream().map(r -> r.department).collect(Collectors.toList());
        assertEquals(Arrays.asList("Engineering", "HR"), departments);
    }

    @Test
    public void sqlLikeQueryShouldExposeChartEntryPoint() {
        List<DepartmentEmployee> source = Arrays.asList(
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Finance")
        );

        ChartData chart = PojoLens
                .parse("select department, count(*) as total group by department order by total desc")
                .chart(source, DepartmentCount.class, ChartSpec.of(ChartType.BAR, "department", "total"));

        assertEquals(2, chart.getLabels().size());
        assertEquals(1, chart.getDatasets().size());
    }

    @Test
    public void whereParenthesesShouldRespectBooleanPrecedence() {
        List<TestBean> source = Arrays.asList(
                new TestBean("abc", 10),
                new TestBean("abc", 1),
                new TestBean("xyz", 10),
                new TestBean("xyz", 2)
        );

        List<TestBean> results = PojoLens
                .parse("where (name = 'abc' or name = 'xyz') and value >= 10")
                .filter(source, TestBean.class);

        assertEquals(2, results.size());
        List<String> names = results.stream().map(r -> r.name).sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList("abc", "xyz"), names);
    }

    @Test
    public void havingParenthesesShouldRespectBooleanPrecedence() {
        List<DepartmentEmployee> source = Arrays.asList(
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("HR")
        );

        List<DepartmentCount> results = PojoLens
                .parse("select department, count(*) as total group by department "
                        + "having total >= 3 or (total = 2 and department = 'Finance') order by total desc")
                .filter(source, DepartmentCount.class);

        assertEquals(2, results.size());
        List<String> departments = results.stream().map(r -> r.department).collect(Collectors.toList());
        assertEquals(Arrays.asList("Engineering", "Finance"), departments);
    }

    @Test
    public void selectComputedExpressionShouldProjectDeterministicNumericOutput() {
        List<TestBean> source = Arrays.asList(
                new TestBean("abc", 2),
                new TestBean("xyz", 5)
        );

        List<ComputedProjection> results = PojoLens
                .parse("select name as name, value * 1.5 + 2 as boosted where value >= 2")
                .filter(source, ComputedProjection.class);

        assertEquals(2, results.size());
        assertEquals("abc", results.get(0).name);
        assertEquals(5.0, results.get(0).boosted, 0.000001);
        assertEquals("xyz", results.get(1).name);
        assertEquals(9.5, results.get(1).boosted, 0.000001);
    }

    @Test
    public void havingComputedExpressionShouldFilterOnAggregateAliases() {
        List<DepartmentEmployeeWithSalary> source = Arrays.asList(
                new DepartmentEmployeeWithSalary("Engineering", 120_000),
                new DepartmentEmployeeWithSalary("Engineering", 110_000),
                new DepartmentEmployeeWithSalary("Engineering", 130_000),
                new DepartmentEmployeeWithSalary("Finance", 90_000),
                new DepartmentEmployeeWithSalary("Finance", 95_000)
        );

        List<DepartmentAvgProjection> results = PojoLens
                .parse("select department, sum(salary) as total, count(*) as people "
                        + "group by department having total / people >= 100000")
                .filter(source, DepartmentAvgProjection.class);

        assertEquals(1, results.size());
        assertEquals("Engineering", results.get(0).department);
        assertEquals(360000L, results.get(0).total);
        assertEquals(3L, results.get(0).people);
    }

    @Test
    public void whereInSubqueryShouldSupportSelfSourceFiltering() {
        List<DepartmentEmployee> source = Arrays.asList(
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("HR")
        );
        List<DepartmentEmployeeWithActive> activeSource = Arrays.asList(
                new DepartmentEmployeeWithActive("Engineering", true),
                new DepartmentEmployeeWithActive("Engineering", false),
                new DepartmentEmployeeWithActive("Finance", false),
                new DepartmentEmployeeWithActive("HR", false)
        );

        List<DepartmentEmployeeWithActive> results = PojoLens
                .parse("where department in (select department where active = true)")
                .filter(activeSource, DepartmentEmployeeWithActive.class);

        assertEquals(2, results.size());
        assertEquals(Arrays.asList("Engineering", "Engineering"),
                results.stream().map(r -> r.department).collect(Collectors.toList()));
    }

    @Test
    public void whereInSubqueryShouldSupportNamedJoinSourceFiltering() {
        List<Company> companies = sampleCompanies();

        List<Company> results = PojoLens
                .parse("where id in (select companyId from employees where title = 'Engineer')")
                .filter(companies, Map.of("employees", sampleCompanyEmployees()), Company.class);

        assertEquals(1, results.size());
        assertEquals("Acme", results.get(0).name);
    }

    @Test
    public void repeatedBeanBackedStatsExecutionsShouldRebindToCurrentRows() {
        List<DepartmentEmployee> firstRows = Arrays.asList(
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Finance")
        );
        List<DepartmentEmployee> secondRows = Arrays.asList(
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("HR")
        );

        SqlLikeQuery query = PojoLens.parse(
                "select department, count(*) as total group by department order by department asc");

        List<DepartmentCount> first = query.filter(firstRows, DepartmentCount.class);
        List<DepartmentCount> second = query.filter(secondRows, DepartmentCount.class);

        assertEquals(Arrays.asList("Engineering", "Finance"),
                first.stream().map(row -> row.department).collect(Collectors.toList()));
        assertEquals(Arrays.asList(2L, 1L),
                first.stream().map(row -> row.total).collect(Collectors.toList()));
        assertEquals(Arrays.asList("Finance", "HR"),
                second.stream().map(row -> row.department).collect(Collectors.toList()));
        assertEquals(Arrays.asList(3L, 1L),
                second.stream().map(row -> row.total).collect(Collectors.toList()));
    }

    public static class TestBean {
        String name;
        int value;
        Date createdAt;

        public TestBean() {
        }

        public TestBean(String name, int value) {
            this.name = name;
            this.value = value;
            this.createdAt = new Date();
        }
    }

    public static class DepartmentEmployee {
        String department;

        public DepartmentEmployee() {
        }

        public DepartmentEmployee(String department) {
            this.department = department;
        }
    }

    public static class DepartmentCount {
        String department;
        long total;

        public DepartmentCount() {
        }
    }

    public static class ComputedProjection {
        String name;
        double boosted;

        public ComputedProjection() {
        }
    }

    public static class DepartmentEmployeeWithSalary {
        String department;
        long salary;

        public DepartmentEmployeeWithSalary() {
        }

        public DepartmentEmployeeWithSalary(String department, long salary) {
            this.department = department;
            this.salary = salary;
        }
    }

    public static class DepartmentEmployeeWithActive {
        String department;
        boolean active;

        public DepartmentEmployeeWithActive() {
        }

        public DepartmentEmployeeWithActive(String department, boolean active) {
            this.department = department;
            this.active = active;
        }
    }

    public static class DepartmentAvgProjection {
        String department;
        long total;
        long people;

        public DepartmentAvgProjection() {
        }
    }
}

