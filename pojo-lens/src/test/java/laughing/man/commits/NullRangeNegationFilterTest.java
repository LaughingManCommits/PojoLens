package laughing.man.commits;

import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedPredicate;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.sqllike.PlanPreviewFilter;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-25: {@code IS [NOT] NULL}, {@code [NOT] BETWEEN}, and general {@code NOT} on SQL-like and
 * natural, in parity with typed {@code isNull()}, {@code between(...)}, and {@code not()}.
 */
class NullRangeNegationFilterTest {

    private static final TypedField<Employee, String> DEPARTMENT = TypedField.of("department", String.class);
    private static final TypedField<Employee, Integer> SALARY = TypedField.of("salary", Integer.class);
    private static final TypedField<Employee, String> NAME = TypedField.of("name", String.class);

    @Test
    void sqlLikeIsNullMatchesTypedNullChecks() {
        List<Employee> rows = withNullDepartment();

        assertEquals(List.of("Eve"), sql("where department is null", rows));
        assertEquals(List.of("Alice", "Bob", "Cara", "Dan"), sql("where department IS NOT NULL order by name", rows));
        assertEquals(typed(DEPARTMENT.isNull(), rows), sql("where department is null order by name", rows));
        assertEquals(typed(DEPARTMENT.isNotNull(), rows), sql("where department is not null order by name", rows));
    }

    @Test
    void sqlLikeBetweenIsInclusiveAndMatchesTyped() {
        List<Employee> rows = sampleEmployees();

        assertEquals(List.of("Alice", "Dan"), sql("where salary between 110000 and 120000 order by name", rows));
        assertEquals(typed(SALARY.between(110000, 120000), rows),
                sql("where salary between 110000 and 120000 order by name", rows));
        assertEquals(List.of("Bob", "Cara"), sql("where salary not between 110000 and 120000 order by name", rows));
        assertEquals(typed(SALARY.between(110000, 120000).not(), rows),
                sql("where salary not between 110000 and 120000 order by name", rows));
    }

    @Test
    void betweenConsumesItsOwnAndInsideBooleanExpressions() {
        List<Employee> rows = sampleEmployees();

        assertEquals(List.of("Alice"), sql("where salary between 100000 and 125000 and active = true", rows));
        assertEquals(List.of("Bob", "Cara"),
                sql("where department = 'Finance' or salary between 125000 and 140000 order by name", rows));
    }

    @Test
    void betweenAcceptsParametersAndWorksInHaving() {
        List<String> rows = names(PojoLensSql.parse("where salary between :low and :high order by name")
                .params(Map.of("low", 90000, "high", 110000))
                .filter(sampleEmployees(), Employee.class));
        assertEquals(List.of("Bob", "Dan"), rows);

        List<DepartmentCount> grouped = PojoLensSql
                .parse("select department, count(*) as total group by department having count(*) between 2 and 5")
                .filter(sampleEmployees(), DepartmentCount.class);
        assertEquals(1, grouped.size());
        assertEquals("Engineering", grouped.get(0).department);
    }

    @Test
    void notNegatesPredicatesAndGroupsWithDeMorgan() {
        List<Employee> rows = sampleEmployees();

        assertEquals(List.of("Bob", "Dan"), sql("where not salary > 110000 order by name", rows));
        assertEquals(List.of("Bob", "Dan"),
                sql("where not (department = 'Engineering' and active = true) order by name", rows));
        assertEquals(List.of("Dan"),
                sql("where not (department = 'Finance' or active = true)", rows));
        assertEquals(List.of("Bob", "Cara"), sql("where not (salary between 100000 and 125000) order by name", rows));
        assertEquals(List.of("Bob"), sql("where not department in ('Engineering')", rows));
        assertEquals(List.of("Alice"), sql("where not not name = 'Alice'", rows));
        assertEquals(typed(NAME.eq("Alice").or(SALARY.gt(125000)).not(), rows),
                sql("where not (name = 'Alice' or salary > 125000) order by name", rows));
    }

    @Test
    void notKeepsSqlNullSemantics() {
        List<Employee> rows = withNullDepartment();

        // NOT (null = 'Finance') is unknown in SQL, so the null row stays out.
        assertEquals(List.of("Alice", "Cara", "Dan"), sql("where not (department = 'Finance') order by name", rows));
        assertEquals(List.of("Alice", "Bob", "Cara", "Dan"), sql("where not department is null order by name", rows));
        assertEquals(List.of("Bob", "Dan"),
                sql("where not (department = 'Engineering' and active = true) order by name", rows));
    }

    @Test
    void notFlipsExistsSubqueries() {
        List<Employee> rows = sampleEmployees();

        assertEquals(4, sql("where not (exists (select * where salary > 200000))", rows).size());
        assertEquals(0, sql("where not (not exists (select * where salary > 200000))", rows).size());
    }

    @Test
    void planPreviewReportsNullTestsAndLoweredRanges() {
        List<PlanPreviewFilter> filters = PojoLensSql
                .parse("where department is null and id is not null and salary between 1 and 2")
                .planPreview()
                .filters();

        assertEquals(List.of("IS NULL", "IS NOT NULL", ">=", "<="),
                filters.stream().map(PlanPreviewFilter::operator).toList());
        assertTrue(PojoLensSql.parse("where department is null").pushdownPreview()
                .fallbackReasons().contains("FILTER_OPERATOR_UNSUPPORTED"));
    }

    @Test
    void unsupportedNegationsAndMalformedRangesFailWithActionableMessages() {
        assertTrue(parseError("where not (id in (select id where active = true))").contains("NOT EXISTS"));
        assertTrue(parseError("where salary between 1 or 2").contains("BETWEEN low AND high"));
    }

    @Test
    void naturalBetweenAndNullPhrasesMatchSqlLike() {
        List<Employee> rows = withNullDepartment();

        assertEquals(List.of("Alice", "Dan"),
                natural("show employees where salary is between 110000 and 120000 sort by name", rows));
        assertEquals(List.of("Bob", "Cara"),
                natural("show employees where salary is not between 110000 and 120000 and department is not null"
                        + " sort by name", rows));
        assertEquals(List.of("Eve"), natural("show employees where department is null", rows));
        assertEquals("select * where (salary >= 1 and salary <= 2)",
                PojoLensNatural.parse("show employees where salary is between 1 and 2").equivalentSqlLike());
    }

    @Test
    void naturalGroupsAndNegatedGroups() {
        List<Employee> rows = sampleEmployees();

        assertEquals(List.of("Bob", "Cara"), natural(
                "show employees where (department is Finance or salary is above 125000) and active is true"
                        + " sort by name", rows));
        assertEquals(List.of("Bob", "Dan"), natural(
                "show employees where not (department is Engineering and active is true) sort by name", rows));
        assertEquals(List.of("Dan"), natural("show employees where not (active is true)", rows));
    }

    @Test
    void naturalNegationErrorsAreActionable() {
        IllegalArgumentException unclosed = assertThrows(IllegalArgumentException.class,
                () -> PojoLensNatural.parse("show employees where (active is true"));
        IllegalArgumentException range = assertThrows(IllegalArgumentException.class,
                () -> PojoLensNatural.parse("show employees where salary is between 1"));

        assertTrue(unclosed.getMessage().contains("Expected ')'"), unclosed::getMessage);
        assertTrue(range.getMessage().contains("is between <low> and <high>"), range::getMessage);
    }

    private static List<String> sql(String query, List<Employee> rows) {
        return names(PojoLensSql.parse(query).filter(rows, Employee.class));
    }

    private static List<String> natural(String query, List<Employee> rows) {
        return names(PojoLensNatural.parse(query).filter(rows, Employee.class));
    }

    private static List<String> typed(TypedPredicate<Employee> predicate,
                                      List<Employee> rows) {
        return names(TypedQuery.from(Employee.class).where(predicate).orderBy(NAME).filter(rows));
    }

    private static String parseError(String query) {
        return assertThrows(IllegalArgumentException.class, () -> PojoLensSql.parse(query)).getMessage();
    }

    private static List<Employee> withNullDepartment() {
        List<Employee> rows = new ArrayList<>(sampleEmployees());
        rows.add(new Employee(5, "Eve", null, 100000, null, true));
        return rows;
    }

    private static List<String> names(List<Employee> rows) {
        return rows.stream().map(row -> row.name).toList();
    }

    static class DepartmentCount {
        String department;
        long total;

        DepartmentCount() {
        }
    }
}
