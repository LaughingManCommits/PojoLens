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
 * WP-27: SQL-like {@code [NOT] LIKE} / {@code ILIKE} with {@code %}, {@code _}, and
 * {@code ESCAPE}, and natural {@code ... ignoring case}.
 */
class LikeFilterTest {

    private static final TypedField<Employee, String> NAME = TypedField.of("name", String.class);

    @Test
    void likeWildcardsMatchWholeValue() {
        List<Employee> rows = sampleEmployees();

        assertEquals(List.of("Alice"), sql("where name like 'A%'", rows));
        assertEquals(List.of("Alice"), sql("where name like '%ce'", rows));
        assertEquals(List.of("Cara", "Dan"), sql("where name like '%a%' order by name", rows));
        assertEquals(List.of("Bob"), sql("where name like '_ob'", rows));
        assertEquals(List.of(), sql("where name like 'Al'", rows));
        assertEquals(List.of("Bob", "Cara", "Dan"), sql("where name not like 'A%' order by name", rows));
    }

    @Test
    void likeMatchesTypedPrefixAndSuffix() {
        List<Employee> rows = sampleEmployees();

        assertEquals(typed(NAME.startsWith("Al"), rows), sql("where name like 'Al%' order by name", rows));
        assertEquals(typed(NAME.endsWith("n"), rows), sql("where name like '%n' order by name", rows));
    }

    @Test
    void ilikeIgnoresCaseIncludingNonAsciiLetters() {
        List<Employee> rows = withExtraNames("École");

        assertEquals(List.of("Alice"), sql("where name ilike 'a%'", rows));
        assertEquals(List.of("École"), sql("where name ilike 'éCO%'", rows));
        assertEquals(List.of("Alice", "Cara", "Dan"), sql("where department ilike '%ENGIN%' order by name", rows));
        assertEquals(typed(NAME.containsIgnoreCase("A"), rows), sql("where name ilike '%a%' order by name", rows));
        assertEquals(List.of("Bob", "École"), sql("where name not ilike '%a%' order by name", rows));
    }

    @Test
    void regexCharactersInPatternsAreLiteral() {
        List<Employee> rows = withExtraNames("A.B", "(x)");

        assertEquals(List.of("A.B"), sql("where name like 'A.%'", rows));
        assertEquals(List.of("(x)"), sql("where name like '(_)'", rows));
    }

    @Test
    void escapeCharacterMakesWildcardsLiteral() {
        List<Employee> rows = withExtraNames("100%", "1000", "a\\b", "5_5");

        assertEquals(List.of("100%"), sql("where name like '100\\%'", rows));
        assertEquals(List.of("100%", "1000"), sql("where name like '100%' order by name", rows));
        assertEquals(List.of("100%"), sql("where name like '100!%' escape '!'", rows));
        assertEquals(List.of("5_5"), sql("where name like '5\\_5'", rows));
        assertEquals(List.of("a\\b"), sql("where name like 'a\\b' escape ''", rows));
    }

    @Test
    void percentAndUnderscoreSpanLineTerminators() {
        List<Employee> rows = withExtraNames("line1\nA");

        assertEquals(List.of("line1\nA"), sql("where name like 'line1%'", rows));
        assertEquals(List.of("line1\nA"), sql("where name like 'line1_A'", rows));
    }

    @Test
    void likeParametersAreLoweredWhenBound() {
        List<String> prefix = names(PojoLensSql.parse("where name like :pattern")
                .params(Map.of("pattern", "A%"))
                .filter(sampleEmployees(), Employee.class));
        List<String> notPrefix = names(PojoLensSql.parse("where name not ilike :pattern order by name")
                .params(Map.of("pattern", "a%"))
                .filter(sampleEmployees(), Employee.class));

        assertEquals(List.of("Alice"), prefix);
        assertEquals(List.of("Bob", "Cara", "Dan"), notPrefix);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse("where name like :pattern")
                        .params(Map.of("pattern", "A\\"))
                        .filter(sampleEmployees(), Employee.class));
        assertTrue(error.getMessage().contains("escape character"), error::getMessage);
    }

    @Test
    void likeComposesWithNotGroupsHavingAndNulls() {
        List<Employee> rows = withNullDepartment();

        assertEquals(sql("where name not like 'A%' order by name", rows),
                sql("where not (name like 'A%') order by name", rows));
        assertEquals(4, sql("where department like '%'", rows).size());
        assertEquals(0, sql("where department not like '%'", rows).size());
        assertEquals(0, sql("where name like null", rows).size());

        List<DepartmentCount> grouped = PojoLensSql
                .parse("select department, count(*) as total group by department having department like 'Eng%'")
                .filter(sampleEmployees(), DepartmentCount.class);
        assertEquals(1, grouped.size());
        assertEquals("Engineering", grouped.get(0).department);
    }

    @Test
    void planPreviewReportsLikeAsMatches() {
        List<PlanPreviewFilter> filters = PojoLensSql
                .parse("where name like 'A%' and name not ilike 'b%'")
                .planPreview()
                .filters();

        assertEquals(List.of("MATCHES", "NOT MATCHES"), filters.stream().map(PlanPreviewFilter::operator).toList());
    }

    @Test
    void malformedLikeFailsAtParseTime() {
        assertTrue(parseError("where name like 'abc\\'").contains("escape character"));
        assertTrue(parseError("where name like 'a%' escape 'ab'").contains("ESCAPE must be one character"));
    }

    @Test
    void naturalIgnoringCaseCoversContainsPrefixAndSuffix() {
        List<Employee> rows = sampleEmployees();

        assertEquals(List.of("Alice"), natural("show employees where name contains ALI ignoring case", rows));
        assertEquals(List.of("Alice"), natural("show employees where name starts with al ignoring case", rows));
        assertEquals(List.of("Alice"), natural("show employees where name ends with 'CE' ignoring case", rows));
        assertEquals(List.of("Bob"), natural("show employees where name does not contain A ignoring case", rows));
        assertEquals(typed(NAME.containsIgnoreCase("ali"), rows),
                natural("show employees where name contains ali ignoring case sort by name", rows));
    }

    @Test
    void naturalIgnoringCaseAcceptsParametersAndRejectsOtherPhrases() {
        List<String> rows = names(PojoLensNatural.parse("show employees where name starts with :prefix ignoring case")
                .params(Map.of("prefix", "AL"))
                .filter(sampleEmployees(), Employee.class));
        assertEquals(List.of("Alice"), rows);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PojoLensNatural.parse("show employees where name is bob ignoring case"));
        assertTrue(error.getMessage().contains("only applies to contains"), error::getMessage);
    }

    private static List<String> sql(String query, List<Employee> rows) {
        return names(PojoLensSql.parse(query).filter(rows, Employee.class));
    }

    private static List<String> natural(String query, List<Employee> rows) {
        return names(PojoLensNatural.parse(query).filter(rows, Employee.class));
    }

    private static List<String> typed(TypedPredicate<Employee> predicate, List<Employee> rows) {
        return names(TypedQuery.from(Employee.class).where(predicate).orderBy(NAME).filter(rows));
    }

    private static String parseError(String query) {
        return assertThrows(IllegalArgumentException.class, () -> PojoLensSql.parse(query)).getMessage();
    }

    private static List<Employee> withExtraNames(String... names) {
        List<Employee> rows = new ArrayList<>(sampleEmployees());
        int id = 10;
        for (String name : names) {
            rows.add(new Employee(id++, name, "Other", 1, null, true));
        }
        return rows;
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
