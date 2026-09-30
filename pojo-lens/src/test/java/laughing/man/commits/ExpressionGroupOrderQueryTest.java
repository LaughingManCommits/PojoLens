package laughing.man.commits;

import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.PageResult;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-29 slice 4: expressions in GROUP BY and ORDER BY, and computed SELECT outputs of
 * grouped queries.
 */
class ExpressionGroupOrderQueryTest {

    @Test
    void groupByExpressionSharesTheSelectedColumn() {
        List<YearCount> counts = PojoLensSql
                .parse("select year(hireDate) as hireYear, count(*) as total where hireDate is not null "
                        + "group by YEAR( hireDate ) order by hireYear")
                .filter(employees(), YearCount.class);

        assertEquals(List.of(2024, 2025), counts.stream().map(row -> row.hireYear).toList());
        assertEquals(List.of(2L, 2L), counts.stream().map(row -> row.total).toList());
    }

    @Test
    void groupBySelectedAlias() {
        List<YearCount> counts = PojoLensSql
                .parse("select year(hireDate) as hireYear, count(*) as total where hireDate is not null "
                        + "group by hireYear order by hireYear desc")
                .filter(employees(), YearCount.class);

        assertEquals(List.of(2025, 2024), counts.stream().map(row -> row.hireYear).toList());
    }

    @Test
    void textExpressionMergesGroups() {
        List<DepartmentCount> counts = PojoLensSql
                .parse("select lower(department) as dept, count(*) as total group by dept order by dept")
                .filter(employees(), DepartmentCount.class);

        assertEquals(List.of("engineering", "finance"), counts.stream().map(row -> row.dept).toList());
        assertEquals(List.of(3L, 2L), counts.stream().map(row -> row.total).toList());
    }

    @Test
    void unselectedGroupExpressionStaysHidden() {
        List<QueryRow> rows = PojoLensSql
                .parse("select count(*) as total where hireDate is not null group by year(hireDate) order by total")
                .filter(employees(), QueryRow.class);

        assertEquals(2, rows.size());
        assertEquals(List.of(List.of("total"), List.of("total")), rows.stream().map(ExpressionGroupOrderQueryTest::fieldNames).toList());
    }

    @Test
    void havingMatchesAGroupedExpression() {
        List<YearCount> counts = PojoLensSql
                .parse("select year(hireDate) as hireYear, count(*) as total where hireDate is not null "
                        + "group by year(hireDate) having year(hireDate) > 2024")
                .filter(employees(), YearCount.class);

        assertEquals(List.of(2025), counts.stream().map(row -> row.hireYear).toList());
    }

    @Test
    void orderByExpression() {
        List<Employee> rows = List.of(employee(1, "alice"), employee(2, "Bob"), employee(3, "cara"));

        assertEquals(List.of("cara", "Bob", "alice"), names(PojoLensSql.parse("order by lower(name) desc").filter(rows, Employee.class)));
        assertEquals(List.of("cara", "alice", "Bob"), names(PojoLensSql.parse("order by name desc").filter(rows, Employee.class)));
        assertEquals(List.of("Bob", "cara", "alice"),
                names(PojoLensSql.parse("order by length(name), name").filter(rows, Employee.class)));
    }

    @Test
    void orderByExpressionKeepsHiddenColumnsOutOfQueryRows() {
        List<Employee> rows = List.of(employee(1, "alice"), employee(2, "Bob"));

        List<QueryRow> wildcard = PojoLensSql.parse("select * order by lower(name) desc").filter(rows, QueryRow.class);
        List<QueryRow> explicit = PojoLensSql.parse("select name order by upper(name)").filter(rows, QueryRow.class);
        List<QueryRow> noSelect = PojoLensSql.parse("where id > 0 order by lower(name)").filter(rows, QueryRow.class);

        assertEquals("Bob", wildcard.get(0).getFields().get(1).getValue());
        assertFalse(fieldNames(wildcard.get(0)).stream().anyMatch(name -> name.startsWith("__")), () -> fieldNames(wildcard.get(0)).toString());
        assertEquals(List.of("name"), fieldNames(explicit.get(0)));
        assertFalse(fieldNames(noSelect.get(0)).stream().anyMatch(name -> name.startsWith("__")));
    }

    @Test
    void orderBySelectedExpressionOrAlias() {
        List<Employee> rows = List.of(employee(1, "alice"), employee(2, "Bob"), employee(3, "cara"));

        List<Keyed> byAlias = PojoLensSql.parse("select name, lower(name) as sortKey order by sortKey desc").filter(rows, Keyed.class);
        List<Keyed> byExpression = PojoLensSql.parse("select name, lower(name) as sortKey order by lower(name)").filter(rows, Keyed.class);

        assertEquals(List.of("cara", "bob", "alice"), byAlias.stream().map(row -> row.sortKey).toList());
        assertEquals(List.of("alice", "Bob", "cara"), byExpression.stream().map(row -> row.name).toList());
    }

    @Test
    void keysetPagesOverAnAliasedExpression() {
        List<Employee> rows = List.of(employee(1, "dan"), employee(2, "Bob"), employee(3, "alice"), employee(4, "Cara"));
        SqlLikeQuery query = PojoLensSql.parse("select name, lower(name) as sortKey order by sortKey limit 2");

        PageResult<Keyed> first = query.filterPage(rows, Keyed.class);
        List<Keyed> second = query.keysetAfter(first.nextCursor().orElseThrow()).filter(rows, Keyed.class);

        assertEquals(List.of("alice", "Bob"), first.rows().stream().map(row -> row.name).toList());
        assertEquals(List.of("Cara", "dan"), second.stream().map(row -> row.name).toList());
    }

    @Test
    void groupedComputedOutputsMustBeGrouped() {
        String notGrouped = validationError("select upper(department) as dept, count(*) as n group by department");
        String collision = validationError("select upper(department) as department, count(*) as n group by department");

        assertTrue(notGrouped.contains("Non-aggregated SELECT field 'dept' must be present in GROUP BY"), notGrouped);
        assertTrue(collision.contains("Computed SELECT alias 'department' is also a field name"), collision);
    }

    @Test
    void invalidGroupAndOrderExpressionsAreReported() {
        assertTrue(validationError("select count(*) as n group by lower(nmae)").contains("Unknown field 'nmae'"));
        assertTrue(validationError("select count(*) as n group by lower(salary)")
                .contains(SqlLikeErrorCodes.VALIDATION_EXPRESSION_REFERENCE));
        assertTrue(validationError("select count(*) as n group by reverse(name)")
                .contains("Unsupported expression function 'reverse'"));
        assertTrue(validationError("select department, count(*) as n group by department order by lower(department)")
                .contains("Invalid aggregate ORDER BY expression"));
        assertTrue(validationError("select distinct department order by lower(department)")
                .contains("ORDER BY 'lower(department)' must reference a selected field or alias with SELECT DISTINCT"));
        assertTrue(validationError("where exists (select * order by lower(name)) and id > 0")
                .contains("Subqueries do not support expressions in GROUP BY or ORDER BY"));
    }

    @Test
    void joinedFieldsGroupThroughExpressions() {
        List<QueryRow> rows = PojoLensSql
                .parse("select upper(staff.title) as jobTitle, count(*) as total from companies "
                        + "left join staff on id = companyId group by upper(staff.title) order by jobTitle")
                .filter(sampleCompanies(), JoinBindings.of("staff", sampleCompanyEmployees()), QueryRow.class);

        assertEquals(List.of("ANALYST", "ENGINEER"), rows.stream().map(row -> row.getFields().get(0).getValue()).toList());
    }

    @Test
    void distinctOrderBySelectedExpression() {
        List<QueryRow> rows = PojoLensSql
                .parse("select distinct lower(department) as dept order by lower(department) desc")
                .filter(employees(), QueryRow.class);

        assertEquals(List.of("finance", "engineering"), rows.stream().map(row -> row.getFields().get(0).getValue()).toList());
    }

    private static List<Employee> employees() {
        return List.of(
                new Employee(1, "Alice", "Engineering", 120000, date("2024-01-15T09:00:00Z"), true),
                new Employee(2, "Bob", "Finance", 90000, date("2024-06-15T09:00:00Z"), true),
                new Employee(3, "Cara", "ENGINEERING", 130000, date("2025-08-04T09:00:00Z"), true),
                new Employee(4, "Dan", "engineering", 110000, date("2025-12-31T23:30:00Z"), false),
                new Employee(5, "Eve", "Finance", 100000, null, true));
    }

    private static Employee employee(int id, String name) {
        return new Employee(id, name, "Engineering", 100000, null, true);
    }

    private static Date date(String instant) {
        return Date.from(Instant.parse(instant));
    }

    private static List<String> names(List<Employee> rows) {
        return rows.stream().map(row -> row.name).toList();
    }

    private static List<String> fieldNames(QueryRow row) {
        return row.getFields().stream().map(QueryField::getFieldName).toList();
    }

    private static String validationError(String query) {
        return assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse(query).filter(employees(), QueryRow.class)).getMessage();
    }

    static class YearCount {
        Integer hireYear;
        long total;

        YearCount() {
        }
    }

    static class DepartmentCount {
        String dept;
        long total;

        DepartmentCount() {
        }
    }

    static class Keyed {
        String name;
        String sortKey;

        Keyed() {
        }
    }
}
