package laughing.man.commits.dsl;

import laughing.man.commits.enums.Join;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.QueryDiagnostics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-32: typed field-name validation for joined queries.
 */
class TypedJoinValidationTest {

    private static final TypedField<Order, Integer> ORDER_ID = TypedField.of("id", Integer.class);
    private static final TypedField<Order, Integer> CUSTOMER_ID = TypedField.of("customerId", Integer.class);
    private static final TypedField<Order, Integer> AMOUNT = TypedField.of("amount", Integer.class);
    private static final TypedField<Customer, Integer> ID = TypedField.of("id", Integer.class);
    private static final TypedField<Order, String> NAME = TypedField.of("name", String.class);
    private static final TypedField<Order, Integer> JOINED_CUSTOMER_ID = TypedField.of("child_id", Integer.class);
    private static final TypedField<Order, String> REGION_LABEL = TypedField.of("label", String.class);
    private static final TypedField<Region, Integer> REGION_KEY = TypedField.of("key", Integer.class);
    private static final TypedField<Order, String> TYPO = TypedField.of("nmae", String.class);
    private static final TypedField<Region, Integer> REGION_KEY_AS_CUSTOMER_KEY = TypedField.of("key", Integer.class);

    @Test
    void joinedFieldsIncludingRenamedCollisionsValidateAndRun() {
        List<OrderView> rows = TypedQuery.from(Order.class)
                .join("customers", CUSTOMER_ID, ID, Join.INNER_JOIN)
                .where(NAME.eq("Ann"))
                .orderBy(ORDER_ID)
                .filter(orders(), bindings(), OrderView.class);

        // The customer's colliding "id" column is reachable as "child_id".
        assertEquals(List.of(1, 3), rows.stream().map(row -> row.id).toList());
        assertEquals(List.of("Ann", "Ann"), rows.stream().map(row -> row.name).toList());
        assertEquals(1, TypedQuery.from(Order.class)
                .join("customers", CUSTOMER_ID, ID, Join.INNER_JOIN)
                .where(JOINED_CUSTOMER_ID.eq(11))
                .filter(orders(), bindings(), OrderView.class).size());
    }

    @Test
    void executionRejectsUnknownFieldsOnceJoinBindingsAreKnown() {
        TypedQuery<Order> query = TypedQuery.from(Order.class)
                .join("customers", CUSTOMER_ID, ID, Join.LEFT_JOIN)
                .where(TYPO.eq("Ann"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> query.filter(orders(), bindings(), OrderView.class));

        assertTrue(ex.getMessage().contains("Unknown field 'nmae' in where(...) for Order joined with customers"),
                ex::getMessage);
        assertTrue(ex.getMessage().contains("did you mean name"), ex::getMessage);
    }

    @Test
    void joinKeysMustExistOnBothSides() {
        TypedField<Customer, Integer> childTypo = TypedField.of("idd", Integer.class);
        TypedField<Order, Integer> parentTypo = TypedField.of("customer", Integer.class);

        String child = assertThrows(IllegalArgumentException.class, () -> TypedQuery.from(Order.class)
                .join("customers", CUSTOMER_ID, childTypo, Join.INNER_JOIN)
                .filter(orders(), bindings(), OrderView.class)).getMessage();
        String parent = assertThrows(IllegalArgumentException.class, () -> TypedQuery.from(Order.class)
                .join("customers", parentTypo, ID, Join.INNER_JOIN)
                .filter(orders(), bindings(), OrderView.class)).getMessage();

        assertTrue(child.contains("Unknown field 'idd' in join(...) for source 'customers' (Customer)"), child);
        assertTrue(parent.contains("Unknown field 'customer' in join(...) for Order joined with customers"), parent);
    }

    @Test
    void chainedAndRightJoinsFollowTheEngineNaming() {
        JoinBindings bindings = JoinBindings.builder()
                .add("customers", customers())
                .add("regions", List.of(new Region(1, "North"), new Region(2, "South")))
                .build();

        List<OrderView> chained = TypedQuery.from(Order.class)
                .join("customers", CUSTOMER_ID, ID, Join.INNER_JOIN)
                .join("regions", TypedField.<Order, Integer>of("regionId", Integer.class), REGION_KEY, Join.INNER_JOIN)
                .where(REGION_LABEL.eq("South"))
                .filter(orders(), bindings, OrderView.class);
        // RIGHT JOIN drives from customers, so the order's colliding "id" becomes "child_id".
        List<OrderView> right = TypedQuery.from(Order.class)
                .join("customers", CUSTOMER_ID, ID, Join.RIGHT_JOIN)
                .where(AMOUNT.gt(0))
                .orderBy(JOINED_CUSTOMER_ID)
                .filter(orders(), bindings, OrderView.class);

        assertEquals(List.of(2), chained.stream().map(row -> row.id).toList());
        // Rows follow the order ids (child_id); "id" is now the customer's.
        assertEquals(List.of(10, 11, 10), right.stream().map(row -> row.id).toList());
        assertEquals(List.of(50, 70, 20), right.stream().map(row -> row.amount).toList());
    }

    @Test
    void diagnosticsAndPlanPreviewValidateDeclaredJoinSourceClasses() {
        TypedQuery<Order> undeclared = TypedQuery.from(Order.class)
                .join("customers", CUSTOMER_ID, ID, Join.LEFT_JOIN)
                .where(TYPO.eq("Ann"));
        TypedQuery<Order> declared = TypedQuery.from(Order.class)
                .join("customers", Customer.class, CUSTOMER_ID, ID, Join.LEFT_JOIN)
                .where(TYPO.eq("Ann"));

        QueryDiagnostics diagnostics = declared.diagnostics();

        assertTrue(undeclared.diagnostics().valid(), "join fields are unknown without data or a declared class");
        assertFalse(diagnostics.valid());
        assertTrue(diagnostics.errors().get(0).message().contains("Unknown field 'nmae' in where(...)"),
                () -> diagnostics.errors().get(0).message());
        assertThrows(IllegalArgumentException.class, declared::planPreview);
        assertTrue(TypedQuery.from(Order.class)
                .join("customers", Customer.class, CUSTOMER_ID, ID, Join.LEFT_JOIN)
                .where(NAME.eq("Ann"))
                .diagnostics()
                .valid());
    }

    @Test
    void declaredClassesCoverEmptyBindingsAndMustMatchBoundRows() {
        TypedQuery<Order> declared = TypedQuery.from(Order.class)
                .join("customers", Customer.class, CUSTOMER_ID, ID, Join.LEFT_JOIN)
                .where(TYPO.eq("Ann"));
        TypedQuery<Order> wrongClass = TypedQuery.from(Order.class)
                .join("customers", Region.class, CUSTOMER_ID, REGION_KEY_AS_CUSTOMER_KEY, Join.LEFT_JOIN);

        assertThrows(IllegalArgumentException.class,
                () -> declared.filter(orders(), JoinBindings.of("customers", List.of()), OrderView.class));
        String mismatch = assertThrows(IllegalArgumentException.class,
                () -> wrongClass.filter(orders(), bindings(), OrderView.class)).getMessage();
        assertTrue(mismatch.contains("bound to Customer rows, but join(...) declared Region"), mismatch);
    }

    @Test
    void joinsWithoutAKnownSourceClassSkipValidation() {
        TypedQuery<Order> query = TypedQuery.from(Order.class)
                .join("customers", CUSTOMER_ID, ID, Join.LEFT_JOIN)
                .where(NAME.eq("Ann"));

        assertDoesNotThrow(() -> query.filter(orders(), JoinBindings.of("customers", List.of())));
    }

    private static JoinBindings bindings() {
        return JoinBindings.of("customers", customers());
    }

    private static List<Customer> customers() {
        return List.of(new Customer(10, "Ann", 1), new Customer(11, "Bob", 2), new Customer(12, "Cy", 1));
    }

    private static List<Order> orders() {
        return List.of(new Order(1, 10, 50), new Order(2, 11, 70), new Order(3, 10, 20));
    }

    public static class Order {
        public int id;
        public int customerId;
        public int amount;

        Order() {
        }

        Order(int id, int customerId, int amount) {
            this.id = id;
            this.customerId = customerId;
            this.amount = amount;
        }
    }

    public static class Customer {
        public int id;
        public String name;
        public int regionId;

        Customer() {
        }

        Customer(int id, String name, int regionId) {
            this.id = id;
            this.name = name;
            this.regionId = regionId;
        }
    }

    public static class Region {
        public int key;
        public String label;

        Region() {
        }

        Region(int key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    public static class OrderView {
        public int id;
        public int amount;
        public String name;

        OrderView() {
        }
    }
}
