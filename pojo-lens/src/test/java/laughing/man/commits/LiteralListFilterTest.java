package laughing.man.commits;

import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.sqllike.PlanPreviewFilter;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-24: literal {@code IN} / {@code NOT IN} lists on SQL-like and natural, in parity with
 * typed {@code in(...)}.
 */
class LiteralListFilterTest {

    private static final TypedField<Employee, String> DEPARTMENT = TypedField.of("department", String.class);

    @Test
    void sqlLikeInListMatchesTypedIn() {
        List<String> sql = names(PojoLensSql.parse("where department in ('Finance', 'Sales') order by name")
                .filter(sampleEmployees(), Employee.class));
        List<String> typed = names(TypedQuery.from(Employee.class)
                .where(DEPARTMENT.in("Finance", "Sales"))
                .orderBy(TypedField.of("name", String.class))
                .filter(sampleEmployees()));

        assertEquals(List.of("Bob"), sql);
        assertEquals(typed, sql);
    }

    @Test
    void sqlLikeNotInExcludesListedValuesAndNullFields() {
        List<Employee> rows = withNullDepartment();

        List<String> notIn = names(PojoLensSql.parse("where department not in ('Finance') order by name")
                .filter(rows, Employee.class));

        assertEquals(List.of("Alice", "Cara", "Dan"), notIn);
    }

    @Test
    void numericListsCompareAcrossNumberTypes() {
        assertEquals(List.of("Alice", "Cara"), names(PojoLensSql
                .parse("where salary in (120000, 130000.0) order by name")
                .filter(sampleEmployees(), Employee.class)));
        assertEquals(List.of("Bob", "Dan"), names(PojoLensSql
                .parse("where id not in (1, 3) order by name")
                .filter(sampleEmployees(), Employee.class)));
    }

    @Test
    void nullInsideListNeverMatchesNullFields() {
        List<Employee> rows = withNullDepartment();

        assertEquals(List.of("Bob"), names(PojoLensSql.parse("where department in ('Finance', null)")
                .filter(rows, Employee.class)));
        assertEquals(List.of("Alice", "Cara", "Dan"), names(PojoLensSql
                .parse("where department not in ('Finance', null) order by name")
                .filter(rows, Employee.class)));
    }

    @Test
    void listParameterBindsToInAndNotIn() {
        List<String> in = names(PojoLensSql.parse("where department in :departments order by name")
                .params(Map.of("departments", List.of("Finance", "Engineering")))
                .filter(sampleEmployees(), Employee.class));
        List<String> notIn = names(PojoLensSql.parse("where department not in :departments")
                .params(Map.of("departments", List.of("Engineering")))
                .filter(sampleEmployees(), Employee.class));

        assertEquals(List.of("Alice", "Bob", "Cara", "Dan"), in);
        assertEquals(List.of("Bob"), notIn);
    }

    @Test
    void strictParameterTypesCheckListParameterElements() {
        assertThrows(IllegalArgumentException.class, () -> PojoLensSql
                .parse("where salary in :salaries")
                .strictParameterTypes()
                .params(Map.of("salaries", List.of("not-a-number")))
                .filter(sampleEmployees(), Employee.class));
    }

    @Test
    void listsWorkInBooleanExpressionsAndHaving() {
        assertEquals(List.of("Bob", "Cara"), names(PojoLensSql
                .parse("where department in ('Finance') or salary > 125000 order by name")
                .filter(sampleEmployees(), Employee.class)));

        List<DepartmentCount> grouped = PojoLensSql
                .parse("select department, count(*) as total group by department having department in ('Finance')")
                .filter(sampleEmployees(), DepartmentCount.class);
        assertEquals(1, grouped.size());
        assertEquals("Finance", grouped.get(0).department);
    }

    @Test
    void planPreviewReportsInAndNotInAndPushdownFallsBack() {
        List<PlanPreviewFilter> filters = PojoLensSql
                .parse("where department in ('Finance') and id not in (1, 2)")
                .planPreview()
                .filters();

        assertEquals(List.of("IN", "NOT IN"), filters.stream().map(PlanPreviewFilter::operator).toList());
        assertTrue(PojoLensSql.parse("where id not in (1, 2)").pushdownPreview()
                .fallbackReasons().contains("FILTER_OPERATOR_UNSUPPORTED"));
    }

    @Test
    void malformedListsFailWithActionableMessages() {
        assertTrue(parseError("where department in ()").contains("at least one value"));
        assertTrue(parseError("where department in ('a', :b)").contains("IN :values"));
        assertTrue(parseError("where id not in (select id where active = true)").contains("NOT EXISTS"));
        assertTrue(parseError("where department in 'Finance'").contains("list parameter"));
    }

    @Test
    void naturalOneOfMatchesSqlLikeIn() {
        List<String> natural = names(PojoLensNatural
                .parse("show employees where department is one of Finance, 'Human Resources' sort by name")
                .filter(sampleEmployees(), Employee.class));
        List<String> notOneOf = names(PojoLensNatural
                .parse("show employees where department is not one of Engineering, Sales")
                .filter(sampleEmployees(), Employee.class));

        assertEquals(List.of("Bob"), natural);
        assertEquals(List.of("Bob"), notOneOf);
    }

    @Test
    void naturalOneOfAcceptsListParameterAndRendersSqlLike() {
        var query = PojoLensNatural.parse("show employees where department is one of :departments");

        List<String> rows = names(query.params(Map.of("departments", List.of("Finance")))
                .filter(sampleEmployees(), Employee.class));

        assertEquals(List.of("Bob"), rows);
        assertEquals("select * where department in :departments", query.equivalentSqlLike());
        assertEquals("select * where department in ('Finance', 'Sales')",
                PojoLensNatural.parse("show employees where department is one of Finance, Sales").equivalentSqlLike());
        assertEquals("select * where department not in ('Finance')",
                PojoLensNatural.parse("show employees where department is not one of Finance").equivalentSqlLike());
    }

    @Test
    void naturalParametersInsideListsAreRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PojoLensNatural.parse("show employees where department is one of Finance, :other"));

        assertTrue(error.getMessage().contains("one list parameter"), error::getMessage);
    }

    private static String parseError(String query) {
        return assertThrows(IllegalArgumentException.class, () -> PojoLensSql.parse(query)).getMessage();
    }

    private static List<Employee> withNullDepartment() {
        List<Employee> rows = new java.util.ArrayList<>(sampleEmployees());
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
