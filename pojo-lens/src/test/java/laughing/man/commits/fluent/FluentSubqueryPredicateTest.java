package laughing.man.commits.fluent;

import laughing.man.commits.PojoLensCore;
import laughing.man.commits.builder.FluentQueryDefinition;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class FluentSubqueryPredicateTest {

    @Test
    public void inSubqueryShouldSupportSelfSourceFiltering() {
        List<DepartmentActive> source = Arrays.asList(
                new DepartmentActive("Engineering", true),
                new DepartmentActive("Engineering", false),
                new DepartmentActive("Finance", false),
                new DepartmentActive("HR", false)
        );

        List<DepartmentActive> results = PojoLensCore.newQueryBuilder(source)
                .addInSubquery("department", "department",
                        query -> query.addRule("active", true, Clauses.EQUAL))
                .initFilter()
                .filter(DepartmentActive.class);

        assertEquals(Arrays.asList("Engineering", "Engineering"),
                results.stream().map(row -> row.department).collect(Collectors.toList()));
    }

    @Test
    public void inSubqueryShouldSupportExplicitSourceFiltering() {
        List<Company> results = PojoLensCore.newQueryBuilder(sampleCompanies())
                .addInSubquery("id", sampleCompanyEmployees(), "companyId",
                        query -> query.addRule("title", "Engineer", Clauses.EQUAL))
                .initFilter()
                .filter(Company.class);

        assertEquals(1, results.size());
        assertEquals("Acme", results.get(0).name);
    }

    @Test
    public void inSubqueryShouldSupportJoinedExplicitSourceFiltering() {
        List<OrderRow> orders = Arrays.asList(
                new OrderRow(100, "Ada"),
                new OrderRow(101, "Ben"),
                new OrderRow(102, "Cara")
        );
        List<OrderLine> lines = Arrays.asList(
                new OrderLine(100, 10),
                new OrderLine(101, 20),
                new OrderLine(102, 10)
        );
        List<ProductRow> products = Arrays.asList(
                new ProductRow(10, "Book"),
                new ProductRow(20, "Game")
        );

        List<OrderRow> results = PojoLensCore.newQueryBuilder(orders)
                .addInSubquery("id", lines, "orderId",
                        query -> query.addJoinBeans("productId", products, "id", Join.INNER_JOIN)
                                .addRule("category", "Book", Clauses.EQUAL))
                .initFilter()
                .filter(OrderRow.class);

        assertEquals(Arrays.asList(100, 102),
                results.stream().map(row -> row.id).collect(Collectors.toList()));
    }

    @Test
    public void inSubqueryShouldSupportAggregateOutput() {
        List<Employee> results = PojoLensCore.newQueryBuilder(sampleEmployees())
                .addInSubquery("id", "total",
                        query -> query.addCount("total")
                                .addRule("active", true, Clauses.EQUAL))
                .initFilter()
                .filter(Employee.class);

        assertEquals(1, results.size());
        assertEquals(3, results.get(0).id);
    }

    @Test
    public void existsSubqueryShouldFilterByUncorrelatedPresence() {
        List<Employee> employees = sampleEmployees();

        List<Employee> present = PojoLensCore.newQueryBuilder(employees)
                .addExists(query -> query.addRule("department", "Engineering", Clauses.EQUAL))
                .initFilter()
                .filter(Employee.class);
        List<Employee> missing = PojoLensCore.newQueryBuilder(employees)
                .addRule("department", "Finance", Clauses.EQUAL)
                .addExists(query -> query.addRule("department", "Missing", Clauses.EQUAL))
                .initFilter()
                .filter(Employee.class);
        List<Employee> inverted = PojoLensCore.newQueryBuilder(employees)
                .addNotExists(query -> query.addRule("department", "Missing", Clauses.EQUAL))
                .initFilter()
                .filter(Employee.class);

        assertEquals(employees.size(), present.size());
        assertEquals(0, missing.size());
        assertEquals(employees.size(), inverted.size());
    }

    @Test
    public void existsSubqueryShouldRebindInsidePreparedFluentDefinition() {
        FluentQueryDefinition<Employee> definition = PojoLensCore.prepare(Employee.class,
                query -> query.addExists(subquery -> subquery.addRule("department", "Engineering", Clauses.EQUAL)));

        List<Employee> first = definition.rows(List.of(
                new Employee(1, "Bob", "Finance", 90000, new Date(), true)
        ));
        List<Employee> second = definition.rows(List.of(
                new Employee(2, "Alice", "Engineering", 120000, new Date(), true)
        ));

        assertEquals(0, first.size());
        assertEquals(1, second.size());
    }

    @Test
    public void explainShouldReportPendingSubqueries() {
        QueryBuilder builder = PojoLensCore.newQueryBuilder(sampleEmployees())
                .addInSubquery("id", "total",
                        query -> query.addCount("total")
                                .addRule("active", true, Clauses.EQUAL));

        Map<String, Object> explain = builder.explain();

        assertEquals(1, explain.get("whereSubqueryCount"));
        assertFalse((Boolean) explain.get("whereAlwaysFalse"));
    }

    public static class DepartmentActive {
        public String department;
        public boolean active;

        public DepartmentActive() {
        }

        public DepartmentActive(String department, boolean active) {
            this.department = department;
            this.active = active;
        }
    }

    public static class OrderRow {
        public int id;
        public String customer;

        public OrderRow() {
        }

        public OrderRow(int id, String customer) {
            this.id = id;
            this.customer = customer;
        }
    }

    public static class OrderLine {
        public int orderId;
        public int productId;

        public OrderLine() {
        }

        public OrderLine(int orderId, int productId) {
            this.orderId = orderId;
            this.productId = productId;
        }
    }

    public static class ProductRow {
        public int id;
        public String category;

        public ProductRow() {
        }

        public ProductRow(int id, String category) {
            this.id = id;
            this.category = category;
        }
    }
}
