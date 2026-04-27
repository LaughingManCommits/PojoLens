package laughing.man.commits.dsl;

import laughing.man.commits.sqllike.QueryExecutionGuard;
import laughing.man.commits.sqllike.QueryExecutionGuardException;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypedQueryContractTest {

    private static final TypedField<Employee, String> NAME = TypedField.of("name", String.class);
    private static final TypedField<Employee, Integer> SALARY = TypedField.of("salary", Integer.class);
    private static final TypedField<Employee, Boolean> ACTIVE = TypedField.of("active", Boolean.class);
    private static final TypedField<Employee, String> DEPT = TypedField.of("department", String.class);

    // fixtures: Alice(Eng,120k,active), Bob(Fin,90k,active), Cara(Eng,130k,active), Dan(Eng,110k,inactive)

    // --- Public API surface contract ---

    @Test
    void stableTypedQueryContractShouldRemainAvailable() throws Exception {
        requirePublicStaticMethod(TypedQuery.class, "from", Class.class);
        requirePublicMethod(TypedQuery.class, "select", TypedField[].class);
        requirePublicMethod(TypedQuery.class, "where", TypedPredicate.class);
        requirePublicMethod(TypedQuery.class, "orderBy", TypedField.class);
        requirePublicMethod(TypedQuery.class, "orderByDesc", TypedField.class);
        requirePublicMethod(TypedQuery.class, "limit", int.class);
        requirePublicMethod(TypedQuery.class, "offset", int.class);
        requirePublicMethod(TypedQuery.class, "filter", List.class);
        requirePublicMethod(TypedQuery.class, "filter", List.class, Class.class);
        requirePublicMethod(TypedQuery.class, "executionGuard", QueryExecutionGuard.class);
        requirePublicMethod(TypedQuery.class, "explain", List.class);
        requirePublicMethod(TypedQuery.class, "schema", List.class);
        requirePublicMethod(TypedQuery.class, "schema", List.class, Class.class);
        requirePublicMethod(TypedQuery.class, "entityClass");
        requirePublicMethod(TypedQuery.class, "selectFields");
        requirePublicMethod(TypedQuery.class, "wherePredicate");
        requirePublicMethod(TypedQuery.class, "hasWhere");
        requirePublicMethod(TypedQuery.class, "hasSelect");
        requirePublicMethod(TypedQuery.class, "hasOrderBy");
        requirePublicMethod(TypedQuery.class, "hasLimit");
        requirePublicMethod(TypedQuery.class, "hasOffset");
    }

    // --- Execution behavior ---

    @Test
    void filterWithNoConfigurationReturnsAllRows() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .filter(sampleEmployees());
        assertEquals(4, result.size());
    }

    @Test
    void whereEqFiltersToMatchingRow() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(NAME.eq("Alice"))
                .filter(sampleEmployees());
        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).name);
    }

    @Test
    void whereGtFiltersRowsAboveThreshold() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(SALARY.gt(100_000))
                .filter(sampleEmployees());
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(e -> e.salary > 100_000));
    }

    @Test
    void whereLteFiltersRowsAtOrBelowThreshold() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(SALARY.lte(110_000))
                .filter(sampleEmployees());
        assertEquals(2, result.size()); // Bob(90k), Dan(110k)
        assertTrue(result.stream().allMatch(e -> e.salary <= 110_000));
    }

    @Test
    void whereNeExcludesMatchingRow() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(NAME.ne("Dan"))
                .filter(sampleEmployees());
        assertEquals(3, result.size());
        assertTrue(result.stream().noneMatch(e -> e.name.equals("Dan")));
    }

    @Test
    void whereInFiltersToNamedRows() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(NAME.in("Alice", "Cara"))
                .filter(sampleEmployees());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> e.name.equals("Alice")));
        assertTrue(result.stream().anyMatch(e -> e.name.equals("Cara")));
    }

    @Test
    void whereAndCombinesPredicates() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(DEPT.eq("Engineering").and(ACTIVE.eq(true)))
                .filter(sampleEmployees());
        assertEquals(2, result.size()); // Alice and Cara (Dan is inactive)
        assertTrue(result.stream().allMatch(e -> e.department.equals("Engineering") && e.active));
    }

    @Test
    void whereOrIncludesEitherPredicate() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(NAME.eq("Alice").or(NAME.eq("Bob")))
                .filter(sampleEmployees());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> e.name.equals("Alice")));
        assertTrue(result.stream().anyMatch(e -> e.name.equals("Bob")));
    }

    @Test
    void nestedAndOrPreservesPredicateTreeSemantics() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(DEPT.eq("Engineering").and(NAME.eq("Alice").or(NAME.eq("Bob"))))
                .filter(sampleEmployees());

        assertEquals(List.of("Alice"), result.stream().map(e -> e.name).toList());
    }

    @Test
    void nestedOrAndPreservesPredicateTreeSemantics() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(NAME.eq("Bob").or(DEPT.eq("Engineering").and(ACTIVE.eq(false))))
                .orderBy(NAME)
                .filter(sampleEmployees());

        assertEquals(List.of("Bob", "Dan"), result.stream().map(e -> e.name).toList());
    }

    @Test
    void limitCapsResultSize() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .limit(2)
                .filter(sampleEmployees());
        assertEquals(2, result.size());
    }

    @Test
    void orderByDescSortsBySalaryDescending() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .orderByDesc(SALARY)
                .filter(sampleEmployees());
        assertEquals(4, result.size());
        assertEquals("Cara", result.get(0).name);
        assertTrue(result.get(0).salary >= result.get(1).salary);
    }

    @Test
    void whereOrderByDescLimitReturnTopActiveEmployees() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(ACTIVE.eq(true))
                .orderByDesc(SALARY)
                .limit(2)
                .filter(sampleEmployees());
        assertEquals(2, result.size());
        assertEquals("Cara", result.get(0).name);
        assertEquals("Alice", result.get(1).name);
    }

    @Test
    void emptyRowsReturnEmptyList() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(NAME.eq("Alice"))
                .filter(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void queryBuilderIsImmutable() {
        TypedQuery<Employee> base = TypedQuery.from(Employee.class);
        TypedQuery<Employee> withWhere = base.where(NAME.eq("Alice"));
        TypedQuery<Employee> withLimit = base.limit(1);

        assertFalse(base.hasWhere());
        assertTrue(withWhere.hasWhere());
        assertFalse(withLimit.hasWhere());
        assertFalse(base.hasLimit());
        assertTrue(withLimit.hasLimit());
        assertFalse(withWhere.hasLimit());
    }

    @Test
    void accessorsReflectConfiguredState() {
        TypedQuery<Employee> q = TypedQuery.from(Employee.class)
                .where(SALARY.gt(100_000))
                .orderByDesc(SALARY)
                .limit(5)
                .offset(1);

        assertEquals(Employee.class, q.entityClass());
        assertTrue(q.hasWhere());
        assertTrue(q.hasOrderBy());
        assertTrue(q.hasLimit());
        assertTrue(q.hasOffset());
        assertEquals(5, q.limit());
        assertEquals(1, q.offset());
        assertFalse(q.hasSelect());
    }

    @Test
    void notPredicateThrowsUnsupportedOperationException() {
        TypedQuery<Employee> q = TypedQuery.from(Employee.class)
                .where(ACTIVE.eq(true).not());
        assertThrows(UnsupportedOperationException.class, () -> q.filter(sampleEmployees()));
    }

    // --- Guard interop ---

    @Test
    void guardBlocksWhenRowCountExceedsScanLimit() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder().maxRowsScanned(2).build();
        TypedQuery<Employee> q = TypedQuery.from(Employee.class).executionGuard(guard);
        assertThrows(QueryExecutionGuardException.class, () -> q.filter(sampleEmployees()));
    }

    @Test
    void guardBlocksWhenResultExceedsReturnedLimit() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder().maxRowsReturned(1).build();
        TypedQuery<Employee> q = TypedQuery.from(Employee.class)
                .where(ACTIVE.eq(true))
                .executionGuard(guard);
        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> q.filter(sampleEmployees()));
        assertEquals("GUARD_ROWS_RETURNED_EXCEEDED", ex.outcome().blockCode());
    }

    @Test
    void unrestrictedGuardAllowsExecution() {
        QueryExecutionGuard guard = QueryExecutionGuard.unrestricted();
        List<Employee> result = TypedQuery.from(Employee.class)
                .executionGuard(guard)
                .filter(sampleEmployees());
        assertEquals(4, result.size());
    }

    @Test
    void guardIsCarriedThroughFluentChain() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder().maxRowsScanned(2).build();
        TypedQuery<Employee> q = TypedQuery.from(Employee.class)
                .executionGuard(guard)
                .where(ACTIVE.eq(true))
                .orderByDesc(SALARY)
                .limit(1);
        assertThrows(QueryExecutionGuardException.class, () -> q.filter(sampleEmployees()));
    }

    // --- Explain interop ---

    @Test
    void explainReturnsNonNullMap() {
        Map<String, Object> plan = TypedQuery.from(Employee.class)
                .where(SALARY.gt(100_000))
                .orderByDesc(SALARY)
                .limit(5)
                .explain(sampleEmployees());
        assertNotNull(plan);
    }

    @Test
    void explainOnEmptyRowsReturnsMap() {
        Map<String, Object> plan = TypedQuery.from(Employee.class)
                .where(NAME.eq("Alice"))
                .explain(List.of());
        assertNotNull(plan);
    }

    // --- Schema interop ---

    @Test
    void schemaReturnsNonNull() {
        TabularSchema s = TypedQuery.from(Employee.class)
                .where(SALARY.gt(100_000))
                .schema(sampleEmployees());
        assertNotNull(s);
    }

    @Test
    void schemaWithProjectionClassReturnsNonNull() {
        TabularSchema s = TypedQuery.from(Employee.class)
                .schema(sampleEmployees(), Employee.class);
        assertNotNull(s);
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
