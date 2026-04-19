package laughing.man.commits.sqllike.internal.binding;

import laughing.man.commits.internal.builder.FilterQueryBuilder;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.parser.SqlLikeParser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlLikeBinderSubqueryLoweringTest {

    @Test
    public void directInSubqueryShouldLowerToFluentPredicate() {
        List<DepartmentActive> rows = Arrays.asList(
                new DepartmentActive("Engineering", true),
                new DepartmentActive("Engineering", false),
                new DepartmentActive("Finance", false)
        );
        QueryAst ast = SqlLikeParser.parse("where department in (select department where active = true)");

        FilterQueryBuilder builder = bind(ast, rows, Map.of(), DepartmentActive.class);

        assertEquals(1, builder.explain().get("whereSubqueryCount"));
        assertEquals(0, builder.getFilterValues().size());

        List<DepartmentActive> results = builder.initFilter().filter(DepartmentActive.class);
        assertEquals(Arrays.asList("Engineering", "Engineering"),
                results.stream().map(row -> row.department).toList());
    }

    @Test
    public void directExistsSubqueryShouldLowerToFluentPredicate() {
        List<DepartmentActive> rows = Arrays.asList(
                new DepartmentActive("Engineering", true),
                new DepartmentActive("Finance", false)
        );
        QueryAst ast = SqlLikeParser.parse("where exists (select * where active = true)");

        FilterQueryBuilder builder = bind(ast, rows, Map.of(), DepartmentActive.class);

        assertEquals(1, builder.explain().get("whereSubqueryCount"));
        assertEquals(0, builder.getFilterValues().size());
        assertEquals(2, builder.initFilter().filter(DepartmentActive.class).size());
    }

    @Test
    public void booleanOrExistsSubqueryShouldLowerToGroupedFluentPredicate() {
        List<DepartmentActive> rows = Arrays.asList(
                new DepartmentActive("Engineering", true),
                new DepartmentActive("Finance", false)
        );
        QueryAst ast = SqlLikeParser.parse(
                "where exists (select * where department = 'Missing') or department = 'Finance'");

        FilterQueryBuilder builder = bind(ast, rows, Map.of(), DepartmentActive.class);

        assertEquals(1, builder.explain().get("whereSubqueryCount"));
        List<DepartmentActive> results = builder.initFilter().filter(DepartmentActive.class);
        assertEquals(List.of("Finance"), results.stream().map(row -> row.department).toList());
    }

    @Test
    public void booleanOrInSubqueryShouldLowerToGroupedFluentPredicate() {
        List<DepartmentActive> rows = Arrays.asList(
                new DepartmentActive("Engineering", true),
                new DepartmentActive("Finance", false),
                new DepartmentActive("HR", false)
        );
        QueryAst ast = SqlLikeParser.parse(
                "where department in (select department where active = true) or department = 'Finance'");

        FilterQueryBuilder builder = bind(ast, rows, Map.of(), DepartmentActive.class);

        assertEquals(1, builder.explain().get("whereSubqueryCount"));
        List<DepartmentActive> results = builder.initFilter().filter(DepartmentActive.class);
        assertEquals(Arrays.asList("Engineering", "Finance"),
                results.stream().map(row -> row.department).toList());
    }

    @Test
    public void joinedSourceInSubqueryShouldLowerToFluentPredicate() {
        List<CustomerOrder> orders = Arrays.asList(
                new CustomerOrder(100, "Ada"),
                new CustomerOrder(101, "Ben"),
                new CustomerOrder(102, "Cara")
        );
        Map<String, List<?>> joinSources = Map.of(
                "lines", Arrays.asList(
                        new OrderLine(100, 10),
                        new OrderLine(101, 20),
                        new OrderLine(102, 30)
                ),
                "products", Arrays.asList(
                        new Product(10, "Book"),
                        new Product(20, "Game"),
                        new Product(30, "Book")
                )
        );
        QueryAst ast = SqlLikeParser.parse("where id in "
                + "(select orderId from lines join products on productId = id where category = 'Book')");

        FilterQueryBuilder builder = bind(ast, orders, joinSources, CustomerOrder.class);

        assertEquals(1, builder.explain().get("whereSubqueryCount"));
        assertEquals(0, builder.getFilterValues().size());

        List<CustomerOrder> results = builder.initFilter().filter(CustomerOrder.class);
        assertEquals(Arrays.asList(100, 102), results.stream().map(row -> row.id).toList());
    }

    private static FilterQueryBuilder bind(QueryAst ast,
                                           List<?> rows,
                                           Map<String, List<?>> joinSources,
                                           Class<?> sourceClass) {
        return (FilterQueryBuilder) SqlLikeBinder.bind(
                ast,
                rows,
                joinSources,
                sourceClass,
                ComputedFieldRegistry.empty()
        );
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

    public static class CustomerOrder {
        public int id;
        public String customer;

        public CustomerOrder() {
        }

        public CustomerOrder(int id, String customer) {
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

    public static class Product {
        public int id;
        public String category;

        public Product() {
        }

        public Product(int id, String category) {
            this.id = id;
            this.category = category;
        }
    }
}
