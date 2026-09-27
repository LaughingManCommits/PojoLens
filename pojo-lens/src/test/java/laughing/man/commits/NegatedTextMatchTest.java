package laughing.man.commits;

import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedPredicate;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.internal.FluentEngine;
import laughing.man.commits.natural.NaturalQuery;
import laughing.man.commits.sqllike.PlanPreviewFilter;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-26: negated text matching ({@code NOT_CONTAINS} / {@code NOT_MATCHES}) in the engine,
 * SQL-like {@code NOT CONTAINS} / {@code NOT MATCHES}, natural {@code does not contain /
 * start with / end with}, and typed {@code not()} over the five text predicates.
 */
class NegatedTextMatchTest {

    private static final TypedField<Employee, String> NAME = TypedField.of("name", String.class);
    private static final TypedField<Employee, String> DEPARTMENT = TypedField.of("department", String.class);

    @Test
    void sqlLikeNotContainsMatchesNotGroupAndTyped() {
        List<Employee> rows = withNullDepartment();

        List<String> operator = sql("where department not contains 'ngin' order by name", rows);

        assertEquals(List.of("Bob"), operator);
        assertEquals(operator, sql("where not (department contains 'ngin') order by name", rows));
        assertEquals(operator, typed(DEPARTMENT.contains("ngin").not(), rows));
    }

    @Test
    void sqlLikeNotMatchesExcludesFullMatchesAndNullFields() {
        List<Employee> rows = withNullDepartment();

        assertEquals(List.of("Bob"), sql("where department not matches 'Eng.*' order by name", rows));
        assertEquals(List.of("Bob", "Cara", "Dan", "Eve"), sql("where name not matches 'A.*' order by name", rows));
        assertEquals(typed(NAME.matches("A.*").not(), rows), sql("where name not matches 'A.*' order by name", rows));
    }

    @Test
    void nullFieldsMatchNeitherTextMatchNorItsNegation() {
        List<Employee> rows = withNullDepartment();

        int contains = sql("where department contains 'n'", rows).size();
        int notContains = sql("where department not contains 'n'", rows).size();

        assertEquals(rows.size() - 1, contains + notContains);
    }

    @Test
    void negationFollowsDeMorganAndDoubleNegation() {
        List<Employee> rows = sampleEmployees();

        assertEquals(List.of("Alice", "Bob"),
                sql("where not (name contains 'a' and department = 'Engineering') order by name", rows));
        assertEquals(List.of("Cara", "Dan"), sql("where not (name not contains 'a') order by name", rows));
        assertEquals(List.of("Alice", "Bob"),
                typed(NAME.contains("a").and(DEPARTMENT.eq("Engineering")).not(), rows));
        assertEquals(List.of("Cara", "Dan"), typed(NAME.contains("a").not().not(), rows));
    }

    @Test
    void invalidRegexNeverMatchesEvenWhenNegated() {
        assertEquals(List.of(), sql("where name not matches '['", sampleEmployees()));
    }

    @Test
    void listParameterNotContainsMeansContainsNone() {
        List<String> rows = names(PojoLensSql.parse("where department not contains :parts order by name")
                .params(Map.of("parts", List.of("ngin", "xyz")))
                .filter(sampleEmployees(), Employee.class));

        assertEquals(List.of("Bob"), rows);
    }

    @Test
    void fluentEngineAcceptsNegatedClauses() {
        List<Employee> rows = FluentEngine.newQueryBuilder(sampleEmployees())
                .addRule("name", "a", Clauses.NOT_CONTAINS, Separator.AND)
                .addRule("name", ".*o.*", Clauses.NOT_MATCHES, Separator.AND)
                .initFilter()
                .filter(Employee.class);

        assertEquals(List.of("Alice"), names(rows));
    }

    @Test
    void planPreviewReportsNegatedTextOperatorsAndPushdownFallsBack() {
        List<PlanPreviewFilter> filters = PojoLensSql
                .parse("where name not contains 'a' and not (name matches 'B.*')")
                .planPreview()
                .filters();

        assertEquals(List.of("NOT CONTAINS", "NOT MATCHES"),
                filters.stream().map(PlanPreviewFilter::operator).toList());
        assertTrue(PojoLensSql.parse("where name not contains 'a'").pushdownPreview()
                .fallbackReasons().contains("FILTER_OPERATOR_UNSUPPORTED"));
    }

    @Test
    void naturalDoesNotPhrasesMatchTyped() {
        List<Employee> rows = sampleEmployees();

        assertEquals(List.of("Alice", "Bob"), natural("show employees where name does not contain a sort by name", rows));
        assertEquals(typed(NAME.startsWith("Al").not(), rows),
                natural("show employees where name does not start with Al sort by name", rows));
        assertEquals(typed(NAME.endsWith("ce").not(), rows),
                natural("show employees where name does not end with ce sort by name", rows));
        assertEquals(List.of("Alice", "Bob"),
                natural("show employees where not (name contains a) sort by name", rows));
    }

    @Test
    void naturalDoesNotStartWithTreatsValuesAndParametersAsLiteralText() {
        List<Employee> rows = sampleEmployees();

        assertEquals(4, natural("show employees where name does not start with 'A.'", rows).size());
        List<String> bound = names(PojoLensNatural.parse("show employees where name does not start with :prefix")
                .params(Map.of("prefix", "A."))
                .filter(rows, Employee.class));
        assertEquals(4, bound.size());
    }

    @Test
    void naturalRendersNegatedTextAsRunnableSqlLike() {
        NaturalQuery query = PojoLensNatural.parse("show employees where name does not contain a");

        assertEquals("select * where name not contains 'a'", query.equivalentSqlLike());
        NaturalQuery prefix = PojoLensNatural.parse("show employees where name does not start with Al");
        assertEquals(natural("show employees where name does not start with Al", sampleEmployees()),
                sql(prefix.equivalentSqlLike(), sampleEmployees()));
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

    private static List<Employee> withNullDepartment() {
        List<Employee> rows = new ArrayList<>(sampleEmployees());
        rows.add(new Employee(5, "Eve", null, 100000, null, true));
        return rows;
    }

    private static List<String> names(List<Employee> rows) {
        return rows.stream().map(row -> row.name).toList();
    }
}
