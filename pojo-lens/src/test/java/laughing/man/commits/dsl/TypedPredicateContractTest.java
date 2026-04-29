package laughing.man.commits.dsl;

import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;

import static laughing.man.commits.dsl.TypedPredicate.Operator.AND;
import static laughing.man.commits.dsl.TypedPredicate.Operator.EQ;
import static laughing.man.commits.dsl.TypedPredicate.Operator.GT;
import static laughing.man.commits.dsl.TypedPredicate.Operator.GTE;
import static laughing.man.commits.dsl.TypedPredicate.Operator.IN;
import static laughing.man.commits.dsl.TypedPredicate.Operator.IS_NOT_NULL;
import static laughing.man.commits.dsl.TypedPredicate.Operator.IS_NULL;
import static laughing.man.commits.dsl.TypedPredicate.Operator.LT;
import static laughing.man.commits.dsl.TypedPredicate.Operator.LTE;
import static laughing.man.commits.dsl.TypedPredicate.Operator.NE;
import static laughing.man.commits.dsl.TypedPredicate.Operator.NOT;
import static laughing.man.commits.dsl.TypedPredicate.Operator.OR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypedPredicateContractTest {

    private static final TypedField<Employee, String> NAME = TypedField.of("name", String.class);
    private static final TypedField<Employee, Integer> SALARY = TypedField.of("salary", Integer.class);
    private static final TypedField<Employee, Boolean> ACTIVE = TypedField.of("active", Boolean.class);
    private static final TypedField<CompanyEmployee, Integer> COMPANY_ID = TypedField.of("companyId", Integer.class);

    // --- Public API surface contract ---

    @Test
    void stableTypedPredicateContractShouldRemainAvailable() throws Exception {
        requirePublicMethod(TypedPredicate.class, "operator");
        requirePublicMethod(TypedPredicate.class, "field");
        requirePublicMethod(TypedPredicate.class, "value");
        requirePublicMethod(TypedPredicate.class, "values");
        requirePublicMethod(TypedPredicate.class, "children");
        requirePublicMethod(TypedPredicate.class, "isLeaf");
        requirePublicMethod(TypedPredicate.class, "and", TypedPredicate.class);
        requirePublicMethod(TypedPredicate.class, "or", TypedPredicate.class);
        requirePublicMethod(TypedPredicate.class, "not");
        requirePublicStaticMethod(TypedPredicate.class, "eq", TypedField.class, Object.class);
        requirePublicStaticMethod(TypedPredicate.class, "ne", TypedField.class, Object.class);
        requirePublicStaticMethod(TypedPredicate.class, "gt", TypedField.class, Object.class);
        requirePublicStaticMethod(TypedPredicate.class, "gte", TypedField.class, Object.class);
        requirePublicStaticMethod(TypedPredicate.class, "lt", TypedField.class, Object.class);
        requirePublicStaticMethod(TypedPredicate.class, "lte", TypedField.class, Object.class);
        requirePublicStaticMethod(TypedPredicate.class, "isNull", TypedField.class);
        requirePublicStaticMethod(TypedPredicate.class, "isNotNull", TypedField.class);
        requirePublicStaticMethod(TypedPredicate.class, "in", TypedField.class, Collection.class);
        requirePublicStaticMethod(TypedPredicate.class, "inSubquery",
                TypedField.class, TypedField.class, TypedQuery.class);
        requirePublicStaticMethod(TypedPredicate.class, "inSubquery",
                TypedField.class, TypedField.class, List.class, TypedQuery.class);
        requirePublicStaticMethod(TypedPredicate.class, "exists", TypedQuery.class);
        requirePublicStaticMethod(TypedPredicate.class, "exists", Class.class, TypedQuery.class);
        requirePublicStaticMethod(TypedPredicate.class, "exists", List.class, TypedQuery.class);
        requirePublicStaticMethod(TypedPredicate.class, "exists", Class.class, List.class, TypedQuery.class);
        requirePublicStaticMethod(TypedPredicate.class, "notExists", TypedQuery.class);
        requirePublicStaticMethod(TypedPredicate.class, "notExists", Class.class, TypedQuery.class);
        requirePublicStaticMethod(TypedPredicate.class, "notExists", List.class, TypedQuery.class);
        requirePublicStaticMethod(TypedPredicate.class, "notExists", Class.class, List.class, TypedQuery.class);
        requirePublicStaticMethod(TypedPredicate.class, "allOf", TypedPredicate[].class);
        requirePublicStaticMethod(TypedPredicate.class, "anyOf", TypedPredicate[].class);
    }

    @Test
    void stableTypedFieldPredicateMethodsShouldRemainAvailable() throws Exception {
        requirePublicMethod(TypedField.class, "eq", Object.class);
        requirePublicMethod(TypedField.class, "ne", Object.class);
        requirePublicMethod(TypedField.class, "gt", Object.class);
        requirePublicMethod(TypedField.class, "gte", Object.class);
        requirePublicMethod(TypedField.class, "lt", Object.class);
        requirePublicMethod(TypedField.class, "lte", Object.class);
        requirePublicMethod(TypedField.class, "in", Collection.class);
        requirePublicMethod(TypedField.class, "isNull");
        requirePublicMethod(TypedField.class, "isNotNull");
        requirePublicMethod(TypedField.class, "inSubquery", TypedField.class, TypedQuery.class);
        requirePublicMethod(TypedField.class, "inSubquery", TypedField.class, List.class, TypedQuery.class);
    }

    // --- Leaf predicate behavior ---

    @Test
    void eqPredicateCarriesFieldOperatorAndValue() {
        TypedPredicate<Employee> p = NAME.eq("Alice");
        assertTrue(p.isLeaf());
        assertEquals(EQ, p.operator());
        assertSame(NAME, p.field());
        assertEquals("Alice", p.value());
        assertTrue(p.values().isEmpty());
        assertTrue(p.children().isEmpty());
    }

    @Test
    void nePredicateCarriesCorrectOperator() {
        TypedPredicate<Employee> p = NAME.ne("Bob");
        assertEquals(NE, p.operator());
        assertEquals("Bob", p.value());
    }

    @Test
    void gtGteLtLteCarryCorrectOperatorsAndValues() {
        assertEquals(GT, SALARY.gt(100_000).operator());
        assertEquals(GTE, SALARY.gte(100_000).operator());
        assertEquals(LT, SALARY.lt(200_000).operator());
        assertEquals(LTE, SALARY.lte(200_000).operator());
        assertEquals(100_000, SALARY.gt(100_000).value());
    }

    @Test
    void isNullAndIsNotNullCarryNoValue() {
        TypedPredicate<Employee> n = NAME.isNull();
        TypedPredicate<Employee> nn = NAME.isNotNull();
        assertEquals(IS_NULL, n.operator());
        assertEquals(IS_NOT_NULL, nn.operator());
        assertNull(n.value());
        assertNull(nn.value());
        assertTrue(n.isLeaf());
    }

    @Test
    void inPredicateWithVarargsCarriesAllValues() {
        TypedPredicate<Employee> p = NAME.in("Alice", "Bob");
        assertEquals(IN, p.operator());
        assertTrue(p.isLeaf());
        assertEquals(List.of("Alice", "Bob"), p.values());
        assertNull(p.value());
    }

    @Test
    void inPredicateWithCollectionCarriesAllValues() {
        TypedPredicate<Employee> p = SALARY.in(List.of(90_000, 110_000, 130_000));
        assertEquals(IN, p.operator());
        assertEquals(List.of(90_000, 110_000, 130_000), p.values());
    }

    @Test
    void inSubqueryPredicateCarriesTargetFieldAndNoScalarValue() {
        TypedPredicate<Employee> p = NAME.inSubquery(
                NAME,
                TypedQuery.from(Employee.class).where(ACTIVE.eq(true))
        );

        assertTrue(p.isLeaf());
        assertEquals(TypedPredicate.Operator.IN_SUBQUERY, p.operator());
        assertSame(NAME, p.field());
        assertNull(p.value());
        assertTrue(p.values().isEmpty());
    }

    @Test
    void existsPredicateCarriesNoTargetField() {
        TypedPredicate<Employee> p = TypedPredicate.exists(
                TypedQuery.from(Employee.class).where(ACTIVE.eq(true))
        );

        assertTrue(p.isLeaf());
        assertEquals(TypedPredicate.Operator.EXISTS, p.operator());
        assertNull(p.field());
        assertNull(p.value());
        assertTrue(p.values().isEmpty());
    }

    @Test
    void explicitSourceSubqueryPredicateCarriesCorrectOperator() {
        TypedPredicate<Employee> p = SALARY.inSubquery(
                COMPANY_ID,
                List.of(new CompanyEmployee(1, "Engineer")),
                TypedQuery.from(CompanyEmployee.class)
        );

        assertEquals(TypedPredicate.Operator.IN_SUBQUERY, p.operator());
        assertSame(SALARY, p.field());
    }

    // --- Compound predicate behavior ---

    @Test
    void andInstanceCombinatorProducesAndWithTwoChildren() {
        TypedPredicate<Employee> p = NAME.eq("Alice").and(ACTIVE.eq(true));
        assertFalse(p.isLeaf());
        assertEquals(AND, p.operator());
        assertEquals(2, p.children().size());
        assertNull(p.field());
        assertNull(p.value());
    }

    @Test
    void orInstanceCombinatorProducesOrWithTwoChildren() {
        TypedPredicate<Employee> p = NAME.eq("Alice").or(NAME.eq("Bob"));
        assertEquals(OR, p.operator());
        assertEquals(2, p.children().size());
    }

    @Test
    void notProducesNotWithOneChild() {
        TypedPredicate<Employee> p = ACTIVE.eq(true).not();
        assertFalse(p.isLeaf());
        assertEquals(NOT, p.operator());
        assertEquals(1, p.children().size());
        assertEquals(EQ, p.children().get(0).operator());
    }

    @Test
    void allOfFactoryWithMultiplePredicates() {
        TypedPredicate<Employee> p = TypedPredicate.allOf(
                NAME.eq("Alice"),
                SALARY.gt(100_000),
                ACTIVE.eq(true));
        assertEquals(AND, p.operator());
        assertEquals(3, p.children().size());
    }

    @Test
    void allOfWithSinglePredicateReturnsItself() {
        TypedPredicate<Employee> leaf = NAME.eq("Alice");
        assertSame(leaf, TypedPredicate.allOf(leaf));
    }

    @Test
    void anyOfWithSinglePredicateReturnsItself() {
        TypedPredicate<Employee> leaf = ACTIVE.eq(true);
        assertSame(leaf, TypedPredicate.anyOf(leaf));
    }

    @Test
    void chainedAndOrBuildCorrectTree() {
        TypedPredicate<Employee> p =
                NAME.eq("Alice")
                    .and(SALARY.gte(100_000))
                    .or(ACTIVE.eq(false));

        assertEquals(OR, p.operator());
        assertEquals(AND, p.children().get(0).operator());
        assertEquals(EQ, p.children().get(1).operator());
    }

    // --- Validation ---

    @Test
    void nullFieldThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> TypedPredicate.eq(null, "value"));
        assertThrows(NullPointerException.class, () -> TypedPredicate.isNull(null));
    }

    @Test
    void nullValueForComparisonOperatorsThrows() {
        assertThrows(NullPointerException.class, () -> SALARY.gt(null));
        assertThrows(NullPointerException.class, () -> SALARY.gte(null));
        assertThrows(NullPointerException.class, () -> SALARY.lt(null));
        assertThrows(NullPointerException.class, () -> SALARY.lte(null));
    }

    @Test
    void emptyInCollectionThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> NAME.in(List.of()));
    }

    @Test
    void nullSubqueryInputsThrow() {
        assertThrows(NullPointerException.class, () -> NAME.inSubquery(NAME, (TypedQuery<Employee>) null));
        assertThrows(NullPointerException.class, () -> TypedPredicate.exists((TypedQuery<Employee>) null));
        assertThrows(NullPointerException.class, () -> TypedPredicate.notExists((TypedQuery<Employee>) null));
    }

    @Test
    void nullOtherInAndOrThrowsNullPointerException() {
        TypedPredicate<Employee> p = NAME.eq("Alice");
        assertThrows(NullPointerException.class, () -> p.and(null));
        assertThrows(NullPointerException.class, () -> p.or(null));
    }

    @Test
    void emptyAllOfAnyOfThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> TypedPredicate.allOf());
        assertThrows(IllegalArgumentException.class, () -> TypedPredicate.anyOf());
    }

    // --- Helpers ---

    private static Method requirePublicMethod(Class<?> type, String name, Class<?>... params)
            throws NoSuchMethodException {
        Method m = type.getMethod(name, params);
        assertTrue(Modifier.isPublic(m.getModifiers()),
                () -> "Expected public: " + type.getSimpleName() + "." + name);
        return m;
    }

    private static Method requirePublicStaticMethod(Class<?> type, String name, Class<?>... params)
            throws NoSuchMethodException {
        Method m = requirePublicMethod(type, name, params);
        assertTrue(Modifier.isStatic(m.getModifiers()),
                () -> "Expected static: " + type.getSimpleName() + "." + name);
        return m;
    }
}
