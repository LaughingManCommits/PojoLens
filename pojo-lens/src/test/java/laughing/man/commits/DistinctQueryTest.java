package laughing.man.commits;

import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.sqllike.PageResult;
import laughing.man.commits.sqllike.SqlLikePlanPreview;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-28: {@code SELECT DISTINCT} on SQL-like, natural, and typed queries.
 */
class DistinctQueryTest {

    private static final TypedField<Employee, String> DEPARTMENT = TypedField.of("department", String.class);
    private static final TypedField<Employee, Boolean> ACTIVE = TypedField.of("active", Boolean.class);
    private static final TypedField<Employee, String> NAME = TypedField.of("name", String.class);

    @Test
    void selectDistinctCollapsesDuplicateOutputRows() {
        List<DepartmentRow> rows = PojoLensSql.parse("select distinct department order by department")
                .filter(sampleEmployees(), DepartmentRow.class);

        assertEquals(List.of("Engineering", "Finance"), rows.stream().map(r -> r.department).toList());
    }

    @Test
    void distinctUsesEverySelectedValue() {
        List<DepartmentRow> rows = PojoLensSql
                .parse("select distinct department, active order by department, active")
                .filter(sampleEmployees(), DepartmentRow.class);

        assertEquals(List.of("Engineering:false", "Engineering:true", "Finance:true"),
                rows.stream().map(r -> r.department + ":" + r.active).toList());
    }

    @Test
    void offsetAndLimitApplyAfterDistinct() {
        List<DepartmentRow> rows = PojoLensSql
                .parse("select distinct department order by department limit 1 offset 1")
                .filter(sampleEmployees(), DepartmentRow.class);

        assertEquals(List.of("Finance"), rows.stream().map(r -> r.department).toList());
    }

    @Test
    void distinctKeepsNullAndEmptyTextApart() {
        List<Employee> source = new ArrayList<>(sampleEmployees());
        source.add(new Employee(5, "Eve", null, 1, null, true));
        source.add(new Employee(6, "Fay", "", 1, null, true));
        source.add(new Employee(7, "Gus", null, 1, null, true));

        List<DepartmentRow> rows = PojoLensSql.parse("select distinct department")
                .filter(source, DepartmentRow.class);

        assertEquals(4, rows.size());
    }

    @Test
    void distinctWildcardRemovesIdenticalRows() {
        List<Employee> source = new ArrayList<>(sampleEmployees());
        source.add(source.get(0));

        assertEquals(5, PojoLensSql.parse("select *").filter(source, Employee.class).size());
        assertEquals(4, PojoLensSql.parse("select distinct *").filter(source, Employee.class).size());
    }

    @Test
    void pagesAndKeysetCursorsWalkDistinctRows() {
        List<Employee> source = new ArrayList<>(sampleEmployees());
        source.add(new Employee(5, "Eve", "Sales", 1, null, true));
        source.add(new Employee(6, "Fay", "Sales", 1, null, true));
        source.add(new Employee(7, "Gus", "HR", 1, null, true));
        var query = PojoLensSql.parse("select distinct department order by department limit 2");

        PageResult<DepartmentRow> first = query.filterPage(source, DepartmentRow.class);
        PageResult<DepartmentRow> second = query.keysetAfter(first.nextCursor().orElseThrow())
                .filterPage(source, DepartmentRow.class);

        assertEquals(List.of("Engineering", "Finance"), first.rows().stream().map(r -> r.department).toList());
        assertEquals(4, first.totalRows());
        assertTrue(first.hasMore());
        assertEquals(List.of("HR", "Sales"), second.rows().stream().map(r -> r.department).toList());
        assertFalse(second.hasMore());
    }

    @Test
    void distinctValidationRequiresSelectedOrderAndGroupFields() {
        IllegalArgumentException order = assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse("select distinct department order by salary")
                        .filter(sampleEmployees(), DepartmentRow.class));
        IllegalArgumentException group = assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse("select distinct count(*) as total group by department")
                        .filter(sampleEmployees(), DepartmentCount.class));

        assertTrue(order.getMessage().contains("EQ-SQL-VAL-012"), order::getMessage);
        assertTrue(group.getMessage().contains("EQ-SQL-VAL-012"), group::getMessage);
        assertEquals(2, PojoLensSql.parse("select distinct department, count(*) as total group by department")
                .filter(sampleEmployees(), DepartmentCount.class).size());
    }

    @Test
    void distinctWorksWithStreamsAndPlanPreview() {
        var query = PojoLensSql.parse("select distinct department where active = true");

        assertEquals(2, query.stream(sampleEmployees(), DepartmentRow.class).count());
        SqlLikePlanPreview preview = query.planPreview();
        assertTrue(preview.isDistinct());
        assertFalse(PojoLensSql.parse("select department").planPreview().isDistinct());
        assertTrue(query.pushdownPreview().fallbackReasons().contains("DISTINCT_UNSUPPORTED"));
        assertTrue(query.pushdownPreview().pushableStages().contains("WHERE"));
    }

    @Test
    void typedDistinctMatchesSqlLike() {
        List<Employee> rows = TypedQuery.from(Employee.class)
                .select(DEPARTMENT, ACTIVE)
                .distinct()
                .orderBy(DEPARTMENT)
                .filter(sampleEmployees());

        assertEquals(3, rows.size());
        assertEquals(List.of("Engineering", "Engineering", "Finance"), rows.stream().map(r -> r.department).toList());
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> TypedQuery.from(Employee.class)
                .select(DEPARTMENT)
                .distinct()
                .orderBy(NAME)
                .filter(sampleEmployees()));
        assertTrue(error.getMessage().contains("selected field"), error::getMessage);
    }

    @Test
    void naturalShowDistinctMatchesSqlLike() {
        var query = PojoLensNatural.parse("show distinct department sort by department");

        List<DepartmentRow> rows = query.filter(sampleEmployees(), DepartmentRow.class);

        assertEquals(List.of("Engineering", "Finance"), rows.stream().map(r -> r.department).toList());
        assertEquals("select distinct department order by department asc", query.equivalentSqlLike());
    }

    @Test
    void countDistinctCountsDistinctNonNullValues() {
        List<Employee> source = new ArrayList<>(sampleEmployees());
        source.add(new Employee(5, "Eve", null, 1, null, true));
        source.add(new Employee(6, "Fay", "", 1, null, true));

        // Fast stats path (no WHERE) and the regular aggregation path agree.
        assertEquals(3L, PojoLensSql.parse("select count(distinct department) as total")
                .filter(source, DepartmentCount.class).get(0).total);
        assertEquals(3L, PojoLensSql.parse("select count(distinct department) as total where id > 0")
                .filter(source, DepartmentCount.class).get(0).total);
        assertEquals(0L, PojoLensSql.parse("select count(distinct department) as total where id > 99")
                .filter(source, DepartmentCount.class).get(0).total);
    }

    @Test
    void countDistinctWorksPerGroupInHavingAndOrderBy() {
        List<DepartmentCount> grouped = PojoLensSql
                .parse("select department, count(distinct active) as total group by department "
                        + "order by count(distinct active) desc")
                .filter(sampleEmployees(), DepartmentCount.class);
        List<DepartmentCount> having = PojoLensSql
                .parse("select department, count(*) as total group by department having count(distinct active) > 1")
                .filter(sampleEmployees(), DepartmentCount.class);

        assertEquals(List.of("Engineering:2", "Finance:1"),
                grouped.stream().map(r -> r.department + ":" + r.total).toList());
        assertEquals(List.of("Engineering:3"), having.stream().map(r -> r.department + ":" + r.total).toList());
    }

    @Test
    void countDistinctMatchesAcrossTypedAndNatural() {
        List<DepartmentCount> typed = TypedQuery.from(Employee.class)
                .groupBy(DEPARTMENT)
                .countDistinct(ACTIVE, "total")
                .orderBy(DEPARTMENT)
                .filter(sampleEmployees(), DepartmentCount.class);
        var natural = PojoLensNatural.parse(
                "show department, count of distinct active as total group by department sort by department");

        assertEquals(List.of("Engineering:2", "Finance:1"), typed.stream().map(r -> r.department + ":" + r.total).toList());
        assertEquals(List.of("Engineering:2", "Finance:1"), natural.filter(sampleEmployees(), DepartmentCount.class)
                .stream().map(r -> r.department + ":" + r.total).toList());
        assertTrue(natural.equivalentSqlLike().contains("count(distinct active) as total"), natural::equivalentSqlLike);
    }

    @Test
    void distinctOutsideCountAndCountDistinctWindowsAreRejected() {
        IllegalArgumentException sum = assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse("select sum(distinct salary) as total"));
        IllegalArgumentException window = assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse("select name, count(distinct department) over (partition by active) as c"));

        assertTrue(sum.getMessage().contains("only supported inside COUNT"), sum::getMessage);
        assertTrue(window.getMessage().contains("not supported as a window function"), window::getMessage);
    }

    @Test
    void reportComparisonsCountDistinctValues() {
        List<Employee> previous = sampleEmployees().subList(0, 1);

        var comparison = laughing.man.commits.report.ReportComparisons
                .compare(sampleEmployees(), previous, "department", laughing.man.commits.enums.Metric.COUNT_DISTINCT);

        assertEquals(2d, comparison.currentValue());
        assertEquals(1d, comparison.previousValue());
    }

    static class DepartmentRow {
        String department;
        boolean active;

        DepartmentRow() {
        }
    }

    static class DepartmentCount {
        String department;
        long total;

        DepartmentCount() {
        }
    }
}
