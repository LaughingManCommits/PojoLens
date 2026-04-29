package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCountAlias;
import laughing.man.commits.testutil.SqlLikeProjectionFixtures.ComputedBoostProjection;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlLikeQueryContractTest {

    @Test
    public void parseShouldNormalizeSource() {
        SqlLikeQuery query = PojoLensSql.parse("  where stringField = 'abc'  ");
        assertEquals("where stringField = 'abc'", query.source());
    }

    @Test
    public void parseShouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> PojoLensSql.parse(null));
    }

    @Test
    public void parseShouldRejectBlank() {
        assertThrows(IllegalArgumentException.class, () -> PojoLensSql.parse("   "));
    }

    @Test
    public void bindTypedShouldReturnExecutableBoundQuery() {
        Object bound = PojoLensSql.parse("where name = 'abc'")
                .bindTyped(Collections.emptyList(), TestBean.class);
        assertNotNull(bound);
    }

    @Test
    public void filterShouldExecuteAgainstBoundData() {
        List<TestBean> source = Arrays.asList(
                new TestBean("abc", 1),
                new TestBean("xyz", 2)
        );
        List<TestBean> results = PojoLensSql.parse("where name = 'abc'").filter(source, TestBean.class);
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

        List<DepartmentCount> results = PojoLensSql.parse("select department, count(*) as total group by department having total >= 2 order by total asc limit 1")
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

        List<DepartmentCount> results = PojoLensSql.parse("select department, count(*) as total group by department having count(*) >= 2 order by total desc")
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

        List<DepartmentCount> results = PojoLensSql.parse("select department, count(*) as total group by department having total >= 3 or total = 1 order by total desc")
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

        ChartData chart = PojoLensSql.parse("select department, count(*) as total group by department order by total desc")
                .chart(source, DepartmentCount.class, ChartSpec.of(ChartType.BAR, "department", "total"));

        assertEquals(2, chart.getLabels().size());
        assertEquals(1, chart.getDatasets().size());
    }

    @Test
    public void streamShouldMatchFilterForAliasedProjection() {
        List<TestBean> source = Arrays.asList(
                new TestBean("abc", 2),
                new TestBean("xyz", 5)
        );
        SqlLikeQuery query = PojoLensSql.parse(
                "select name as employeeName, value as annualSalary where value >= 2 order by value desc");

        List<TestBeanSummary> filtered = query.filter(source, TestBeanSummary.class);
        List<TestBeanSummary> streamed = query.stream(source, TestBeanSummary.class).toList();

        assertEquals(
                filtered.stream().map(r -> r.employeeName + ":" + r.annualSalary).toList(),
                streamed.stream().map(r -> r.employeeName + ":" + r.annualSalary).toList()
        );
    }

    @Test
    public void streamShouldMatchFilterForAliasedStatsProjection() {
        List<DepartmentEmployee> source = Arrays.asList(
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Finance")
        );
        SqlLikeQuery query = PojoLensSql.parse(
                "select department as dept, count(*) as total group by department order by total desc");

        List<DepartmentCountAlias> filtered = query.filter(source, DepartmentCountAlias.class);
        List<DepartmentCountAlias> streamed = query.stream(source, DepartmentCountAlias.class).toList();

        assertEquals(
                filtered.stream().map(r -> r.dept + ":" + r.total).toList(),
                streamed.stream().map(r -> r.dept + ":" + r.total).toList()
        );
    }

    @Test
    public void whereParenthesesShouldRespectBooleanPrecedence() {
        List<TestBean> source = Arrays.asList(
                new TestBean("abc", 10),
                new TestBean("abc", 1),
                new TestBean("xyz", 10),
                new TestBean("xyz", 2)
        );

        List<TestBean> results = PojoLensSql.parse("where (name = 'abc' or name = 'xyz') and value >= 10")
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

        List<DepartmentCount> results = PojoLensSql.parse("select department, count(*) as total group by department "
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

        List<ComputedBoostProjection> results = PojoLensSql.parse("select name as name, value * 1.5 + 2 as boosted where value >= 2")
                .filter(source, ComputedBoostProjection.class);

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

        List<DepartmentAvgProjection> results = PojoLensSql.parse("select department, sum(salary) as total, count(*) as people "
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

        List<DepartmentEmployeeWithActive> results = PojoLensSql.parse("where department in (select department where active = true)")
                .filter(activeSource, DepartmentEmployeeWithActive.class);

        assertEquals(2, results.size());
        assertEquals(Arrays.asList("Engineering", "Engineering"),
                results.stream().map(r -> r.department).collect(Collectors.toList()));
    }

    @Test
    public void whereInSubqueryShouldSupportAggregateMetricSelect() {
        List<Employee> source = sampleEmployees();

        List<Employee> results = PojoLensSql.parse("where id in (select count(*) as total where active = true)")
                .filter(source, Employee.class);

        assertEquals(1, results.size());
        assertEquals(3, results.get(0).id);
        assertEquals("Cara", results.get(0).name);
    }

    @Test
    public void whereInSubqueryShouldSupportGroupedFieldWithHavingAggregate() {
        List<DepartmentEmployee> source = Arrays.asList(
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Finance")
        );

        List<DepartmentEmployee> results = PojoLensSql.parse(
                        "where department in (select department group by department having count(*) > 1)")
                .filter(source, DepartmentEmployee.class);

        assertEquals(2, results.size());
        results.forEach(r -> assertEquals("Engineering", r.department));
    }

    @Test
    public void whereInSubqueryShouldSupportGroupedFieldAliasWithWhereFilter() {
        List<DepartmentEmployeeWithActive> source = Arrays.asList(
                new DepartmentEmployeeWithActive("Engineering", true),
                new DepartmentEmployeeWithActive("Engineering", false),
                new DepartmentEmployeeWithActive("Finance", false),
                new DepartmentEmployeeWithActive("HR", false)
        );

        List<DepartmentEmployeeWithActive> results = PojoLensSql.parse(
                        "where department in (select department as dept where active = true group by dept)")
                .filter(source, DepartmentEmployeeWithActive.class);

        assertEquals(2, results.size());
        assertEquals(Arrays.asList("Engineering", "Engineering"),
                results.stream().map(r -> r.department).collect(Collectors.toList()));
    }

    @Test
    public void whereInSubqueryShouldSupportNamedJoinSourceFiltering() {
        List<Company> companies = sampleCompanies();

        List<Company> results = PojoLensSql.parse("where id in (select companyId from employees where title = 'Engineer')")
                .filter(companies, JoinBindings.of("employees", sampleCompanyEmployees()), Company.class);

        assertEquals(1, results.size());
        assertEquals("Acme", results.get(0).name);
    }

    @Test
    public void whereInSubqueryShouldSupportUncorrelatedJoinedSourceFiltering() {
        List<CustomerOrder> orders = Arrays.asList(
                new CustomerOrder(100, "Ada"),
                new CustomerOrder(101, "Ben"),
                new CustomerOrder(102, "Cara")
        );

        List<CustomerOrder> results = PojoLensSql.parse("where id in "
                        + "(select orderId from lines join products on productId = id where category = 'Book')")
                .filter(orders, sampleOrderJoinBindings(), CustomerOrder.class);

        assertEquals(Arrays.asList(100, 102),
                results.stream().map(row -> row.id).collect(Collectors.toList()));
    }

    @Test
    public void whereInSubqueryShouldSupportJoinedSelectedField() {
        List<CustomerInterest> interests = Arrays.asList(
                new CustomerInterest("Book"),
                new CustomerInterest("Game"),
                new CustomerInterest("Desk")
        );

        List<CustomerInterest> results = PojoLensSql.parse("where category in "
                        + "(select category from lines join products on productId = id where orderId = 100)")
                .filter(interests, sampleOrderJoinBindings(), CustomerInterest.class);

        assertEquals(Arrays.asList("Book", "Game"),
                results.stream().map(row -> row.category).collect(Collectors.toList()));
    }

    @Test
    public void whereExistsSubqueryShouldFilterAllRowsWhenSelfSourceHasMatch() {
        List<Employee> employees = sampleEmployees();

        List<Employee> results = PojoLensSql.parse("where exists (select * where active = true)")
                .filter(employees, Employee.class);

        assertEquals(employees.size(), results.size());
    }

    @Test
    public void whereExistsSubqueryShouldFilterNoRowsWhenSelfSourceHasNoMatch() {
        List<Employee> results = PojoLensSql.parse("where exists (select * where department = 'Missing')")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(0, results.size());
    }

    @Test
    public void whereNotExistsSubqueryShouldInvertResult() {
        List<Employee> employees = sampleEmployees();

        List<Employee> results = PojoLensSql.parse("where not exists (select * where department = 'Missing')")
                .filter(employees, Employee.class);

        assertEquals(employees.size(), results.size());
    }

    @Test
    public void whereExistsSubqueryShouldCombineWithBooleanExpressions() {
        List<Employee> results = PojoLensSql.parse(
                        "where exists (select * where department = 'Missing') or department = 'Finance'")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(1, results.size());
        assertEquals("Finance", results.get(0).department);
    }

    @Test
    public void whereExistsSubqueryShouldSupportNamedJoinSource() {
        List<Company> companies = sampleCompanies();

        List<Company> results = PojoLensSql.parse("where exists (select * from employees where title = 'Engineer')")
                .filter(companies, JoinBindings.of("employees", sampleCompanyEmployees()), Company.class);

        assertEquals(companies.size(), results.size());
    }

    @Test
    public void whereExistsSubqueryShouldSupportUncorrelatedJoinedSource() {
        List<CustomerOrder> orders = Arrays.asList(
                new CustomerOrder(100, "Ada"),
                new CustomerOrder(101, "Ben"),
                new CustomerOrder(102, "Cara")
        );

        List<CustomerOrder> results = PojoLensSql.parse("where exists "
                        + "(select * from lines join products on productId = id where category = 'Book')")
                .filter(orders, sampleOrderJoinBindings(), CustomerOrder.class);

        assertEquals(orders.size(), results.size());
    }

    @Test
    public void whereExistsSubqueryShouldRebindToCurrentRows() {
        SqlLikeQuery query = PojoLensSql.parse("where exists (select * where department = 'Engineering')");

        List<Employee> first = query.filter(
                Collections.singletonList(new Employee(1, "Bob", "Finance", 90000, new Date(), true)),
                Employee.class
        );
        List<Employee> second = query.filter(
                Collections.singletonList(new Employee(2, "Alice", "Engineering", 120000, new Date(), true)),
                Employee.class
        );

        assertEquals(0, first.size());
        assertEquals(1, second.size());
    }

    @Test
    public void boundSubqueryShouldResolveBeforeReusableMaterialization() {
        SqlLikeBoundQuery<EmployeeName> bound = PojoLensSql
                .parse("select name where id in (select id where active = true)")
                .bindTyped(sampleEmployees(), EmployeeName.class);

        List<EmployeeName> results = bound.filter();

        assertEquals(Arrays.asList("Alice", "Bob", "Cara"),
                results.stream().map(row -> row.name).collect(Collectors.toList()));
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

        SqlLikeQuery query = PojoLensSql.parse(
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

    @Test
    public void repeatedBeanBackedFastStatsFiltersShouldRebindToCurrentRows() {
        List<DepartmentEmployee> firstRows = Arrays.asList(
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Finance")
        );
        List<DepartmentEmployee> secondRows = Arrays.asList(
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("HR")
        );

        SqlLikeQuery query = PojoLensSql.parse(
                "select department, count(*) as total group by department");

        List<DepartmentCount> first = query.filter(firstRows, DepartmentCount.class);
        List<DepartmentCount> second = query.filter(secondRows, DepartmentCount.class);

        assertEquals(Arrays.asList("Engineering", "Finance"),
                first.stream().map(row -> row.department).collect(Collectors.toList()));
        assertEquals(Arrays.asList(2L, 1L),
                first.stream().map(row -> row.total).collect(Collectors.toList()));
        assertEquals(Arrays.asList("Finance", "HR"),
                second.stream().map(row -> row.department).collect(Collectors.toList()));
        assertEquals(Arrays.asList(2L, 1L),
                second.stream().map(row -> row.total).collect(Collectors.toList()));
    }

    @Test
    public void repeatedBeanBackedAliasedFastStatsFiltersShouldRebindToCurrentRows() {
        List<DepartmentEmployee> firstRows = Arrays.asList(
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Engineering"),
                new DepartmentEmployee("Finance")
        );
        List<DepartmentEmployee> secondRows = Arrays.asList(
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("Finance"),
                new DepartmentEmployee("HR")
        );

        SqlLikeQuery query = PojoLensSql.parse(
                "select department as dept, count(*) as total group by department");

        List<DepartmentCountAlias> first = query.filter(firstRows, DepartmentCountAlias.class);
        List<DepartmentCountAlias> second = query.filter(secondRows, DepartmentCountAlias.class);

        assertEquals(Arrays.asList("Engineering", "Finance"),
                first.stream().map(row -> row.dept).collect(Collectors.toList()));
        assertEquals(Arrays.asList(2L, 1L),
                first.stream().map(row -> row.total).collect(Collectors.toList()));
        assertEquals(Arrays.asList("Finance", "HR"),
                second.stream().map(row -> row.dept).collect(Collectors.toList()));
        assertEquals(Arrays.asList(2L, 1L),
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

    public static class TestBeanSummary {
        String employeeName;
        int annualSalary;

        public TestBeanSummary() {
        }
    }

    public static class EmployeeName {
        String name;

        public EmployeeName() {
        }
    }

    public static class CustomerOrder {
        int id;
        String customer;

        public CustomerOrder() {
        }

        public CustomerOrder(int id, String customer) {
            this.id = id;
            this.customer = customer;
        }
    }

    public static class OrderLine {
        int orderId;
        int productId;

        public OrderLine() {
        }

        public OrderLine(int orderId, int productId) {
            this.orderId = orderId;
            this.productId = productId;
        }
    }

    public static class Product {
        int id;
        String category;

        public Product() {
        }

        public Product(int id, String category) {
            this.id = id;
            this.category = category;
        }
    }

    public static class CustomerInterest {
        String category;

        public CustomerInterest() {
        }

        public CustomerInterest(String category) {
            this.category = category;
        }
    }

    @Test
    public void filterPageShouldReturnPageResultWithRowsHasMoreAndCursor() {
        List<Employee> source = sampleEmployees();

        PageResult<Employee> page = PojoLensSql
                .parse("order by salary desc, id desc limit 2")
                .filterPage(source, Employee.class);

        assertNotNull(page);
        assertEquals(2, page.rows().size());
        assertTrue(page.hasMore());
        assertTrue(page.nextCursor().isPresent());
    }

    @Test
    public void filterPageShouldReturnEmptyNextCursorWhenAllRowsFit() {
        List<Employee> source = sampleEmployees();

        PageResult<Employee> page = PojoLensSql
                .parse("order by salary desc, id desc limit 100")
                .filterPage(source, Employee.class);

        assertNotNull(page);
        assertEquals(source.size(), page.rows().size());
        assertFalse(page.hasMore());
        assertFalse(page.nextCursor().isPresent());
    }

    @Test
    public void filterPageShouldRejectNullJoinBindings() {
        assertThrows(NullPointerException.class, () ->
                PojoLensSql.parse("order by salary desc limit 5")
                        .filterPage(sampleEmployees(), (JoinBindings) null, Employee.class));
    }

    private static JoinBindings sampleOrderJoinBindings() {
        return JoinBindings.builder()
                .add("lines", Arrays.asList(
                        new OrderLine(100, 10),
                        new OrderLine(100, 20),
                        new OrderLine(101, 20),
                        new OrderLine(102, 30)
                ))
                .add("products", Arrays.asList(
                        new Product(10, "Book"),
                        new Product(20, "Game"),
                        new Product(30, "Book")
                ))
                .build();
    }
}






