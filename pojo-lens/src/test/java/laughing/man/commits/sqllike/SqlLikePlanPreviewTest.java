package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlLikePlanPreviewTest {

    // --- basic structure ---

    @Test
    public void wildcardSelectIsMarked() {
        SqlLikePlanPreview p = PojoLensSql.parse("select * from Employee").planPreview();
        assertTrue(p.isWildcard());
        assertTrue(p.selectFields().isEmpty());
        assertEquals("Employee", p.source());
    }

    @Test
    public void filterOnlyQueryHasNoSelectFields() {
        SqlLikePlanPreview p = PojoLensSql.parse("where salary >= 50000").planPreview();
        assertTrue(p.isWildcard());
        assertTrue(p.selectFields().isEmpty());
    }

    @Test
    public void sourceFromSelectClause() {
        SqlLikePlanPreview p = PojoLensSql.parse("select id from Employee").planPreview();
        assertEquals("Employee", p.source());
    }

    // --- SELECT fields ---

    @Test
    public void explicitSelectFieldsReturned() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select name, salary from Employee").planPreview();
        assertFalse(p.isWildcard());
        List<PlanPreviewField> fields = p.selectFields();
        assertEquals(2, fields.size());
        assertEquals("name", fields.get(0).field());
        assertEquals("name", fields.get(0).outputName());
        assertNull(fields.get(0).alias());
        assertEquals("salary", fields.get(1).field());
    }

    @Test
    public void aliasedFieldReportsAlias() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select salary as annualSalary from Employee").planPreview();
        PlanPreviewField f = p.selectFields().get(0);
        assertEquals("salary", f.field());
        assertEquals("annualSalary", f.outputName());
        assertEquals("annualSalary", f.alias());
        assertFalse(f.isMetric());
        assertFalse(f.isWindow());
    }

    @Test
    public void metricFieldReportsMetricName() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select count(*) from Employee group by department").planPreview();
        PlanPreviewField countField = p.selectFields().get(0);
        assertTrue(countField.isMetric());
        assertEquals("COUNT", countField.metric());
        assertTrue(countField.isCountAll());
    }

    @Test
    public void sumMetricFieldReportsMetricName() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select sum(salary) from Employee group by department").planPreview();
        PlanPreviewField f = p.selectFields().get(0);
        assertTrue(f.isMetric());
        assertEquals("SUM", f.metric());
        assertEquals("salary", f.field());
        assertFalse(f.isCountAll());
    }

    @Test
    public void windowFieldReportsWindowFunction() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select row_number() over (partition by department order by salary desc) as rank "
                        +
                "from Employee").planPreview();
        assertFalse(p.selectFields().isEmpty());
        PlanPreviewField f = p.selectFields().get(0);
        assertTrue(f.isWindow());
        assertEquals("ROW_NUMBER", f.windowFunction());
        assertEquals(List.of("department"), f.windowPartitionFields());
        assertEquals(List.of("salary"), f.windowOrderFields());
        assertNotNull(f.windowFrame());
        assertTrue(p.hasWindows());
    }

    // --- WHERE filters ---

    @Test
    public void singleEqualFilterReported() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "where department = 'Engineering'").planPreview();
        assertEquals(1, p.filters().size());
        PlanPreviewFilter f = p.filters().get(0);
        assertEquals("department", f.field());
        assertEquals("=", f.operator());
        assertEquals("LITERAL", f.valueKind());
        assertNull(f.parameterName());
    }

    @Test
    public void parameterFilterReportedWithName() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "where department = :dept and salary >= :min").planPreview();
        List<PlanPreviewFilter> filters = p.filters();
        assertEquals(2, filters.size());

        PlanPreviewFilter deptFilter = filters.stream()
                .filter(f -> f.field().equals("department")).findFirst().orElseThrow();
        assertEquals("=", deptFilter.operator());
        assertEquals("PARAMETER", deptFilter.valueKind());
        assertEquals("dept", deptFilter.parameterName());

        PlanPreviewFilter salaryFilter = filters.stream()
                .filter(f -> f.field().equals("salary")).findFirst().orElseThrow();
        assertEquals(">=", salaryFilter.operator());
        assertEquals("PARAMETER", salaryFilter.valueKind());
        assertEquals("min", salaryFilter.parameterName());
    }

    @Test
    public void groupedPredicateExpressionPreservesBooleanShape() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "where (name = 'Alice' or name = 'Bob') and active = true").planPreview();

        PlanPreviewPredicate root = p.filterExpression();
        assertNotNull(root);
        assertFalse(root.isLeaf());
        assertEquals("AND", root.operator());
        assertEquals(2, root.children().size());

        PlanPreviewPredicate leftGroup = root.children().get(0);
        assertFalse(leftGroup.isLeaf());
        assertEquals("OR", leftGroup.operator());
        assertEquals(2, leftGroup.children().size());
        assertEquals("name", leftGroup.children().get(0).filter().field());
        assertEquals("name", leftGroup.children().get(1).filter().field());

        PlanPreviewPredicate rightLeaf = root.children().get(1);
        assertTrue(rightLeaf.isLeaf());
        assertEquals("active", rightLeaf.filter().field());
    }

    @Test
    public void repeatedLiteralPredicatesAreNotCollapsed() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "where id = 1 or id = 2").planPreview();

        assertEquals(2, p.filters().size());
        assertEquals("OR", p.filterExpression().operator());
        assertEquals("id", p.filterExpression().children().get(0).filter().field());
        assertEquals("id", p.filterExpression().children().get(1).filter().field());
    }

    @Test
    public void requiredParamsCollectedFromFilters() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "where department = :dept and salary >= :min").planPreview();
        List<String> params = p.requiredParams();
        assertTrue(params.contains("dept"));
        assertTrue(params.contains("min"));
        assertEquals(2, params.size());
    }

    @Test
    public void inSubqueryFilterReported() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "where id in (select id from employees where active = true)").planPreview();
        assertTrue(p.hasSubqueries());
        List<PlanPreviewFilter> filters = p.filters();
        PlanPreviewFilter inFilter = filters.stream()
                .filter(f -> f.field().equals("id")).findFirst().orElseThrow();
        assertEquals("IN", inFilter.operator());
        assertEquals("SUBQUERY", inFilter.valueKind());
    }

    @Test
    public void inSubqueryIncludesNestedPreviewShape() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "where id in (select companyId from employees "
                        +
                "where title = :title order by companyId desc limit 2)").planPreview();

        PlanPreviewFilter inFilter = p.filters().get(0);
        SqlLikePlanPreview subquery = inFilter.subqueryPreview();
        assertNotNull(subquery);
        assertEquals("employees", subquery.source());
        assertEquals("companyId", subquery.selectFields().get(0).field());
        assertEquals(List.of("title"), subquery.requiredParams());
        assertEquals("title", subquery.filters().get(0).field());
        assertEquals("PARAMETER", subquery.filters().get(0).valueKind());
        assertEquals("companyId", subquery.orderFields().get(0).field());
        assertEquals("DESC", subquery.orderFields().get(0).direction());
        assertEquals(2, subquery.paging().limit());
    }

    @Test
    public void existsSubqueryFilterReported() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "where exists (select id from managers where active = true)").planPreview();
        assertTrue(p.hasSubqueries());
        List<PlanPreviewFilter> filters = p.filters();
        assertFalse(filters.isEmpty());
        PlanPreviewFilter existsFilter = filters.get(0);
        assertEquals("EXISTS", existsFilter.operator());
        assertEquals("EXISTS_SUBQUERY", existsFilter.valueKind());
        assertEquals("managers", existsFilter.subqueryPreview().source());
    }

    // --- grouping ---

    @Test
    public void groupByFieldsReported() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select department, count(*) from Employee group by department").planPreview();
        assertTrue(p.hasGrouping());
        assertEquals(List.of("department"), p.groupByFields());
        assertTrue(p.hasAggregation());
    }

    @Test
    public void noGroupingWhenAbsent() {
        SqlLikePlanPreview p = PojoLensSql.parse("where salary > 0").planPreview();
        assertFalse(p.hasGrouping());
        assertTrue(p.groupByFields().isEmpty());
    }

    // --- ORDER BY ---

    @Test
    public void orderFieldsReported() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select name from Employee order by salary desc, name asc").planPreview();
        List<PlanPreviewOrder> orders = p.orderFields();
        assertEquals(2, orders.size());
        assertEquals("salary", orders.get(0).field());
        assertEquals("DESC", orders.get(0).direction());
        assertEquals("name", orders.get(1).field());
        assertEquals("ASC", orders.get(1).direction());
    }

    // --- joins ---

    @Test
    public void joinDetailsReported() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select id from Employee join companies on id = companyId").planPreview();
        assertTrue(p.hasJoins());
        List<PlanPreviewJoin> joins = p.joins();
        assertEquals(1, joins.size());
        PlanPreviewJoin j = joins.get(0);
        assertEquals("companies", j.source());
        assertEquals("id", j.parentField());
        assertEquals("companyId", j.childField());
    }

    @Test
    public void noJoinsWhenAbsent() {
        SqlLikePlanPreview p = PojoLensSql.parse("where active = true").planPreview();
        assertFalse(p.hasJoins());
        assertTrue(p.joins().isEmpty());
    }

    // --- paging ---

    @Test
    public void literalLimitReported() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select name from Employee order by salary desc limit 10").planPreview();
        assertTrue(p.hasPaging());
        PlanPreviewPaging paging = p.paging();
        assertNotNull(paging);
        assertTrue(paging.hasLimit());
        assertEquals(10, paging.limit());
        assertNull(paging.limitParameter());
        assertFalse(paging.hasOffset());
    }

    @Test
    public void limitAndOffsetReported() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select name from Employee order by salary asc limit 20 offset 40").planPreview();
        assertTrue(p.hasPaging());
        PlanPreviewPaging paging = p.paging();
        assertTrue(paging.hasLimit());
        assertEquals(20, paging.limit());
        assertTrue(paging.hasOffset());
        assertEquals(40, paging.offset());
    }

    @Test
    public void parameterLimitReported() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select name from Employee order by id asc limit :pageSize").planPreview();
        assertTrue(p.hasPaging());
        PlanPreviewPaging paging = p.paging();
        assertTrue(paging.hasLimit());
        assertNull(paging.limit());
        assertEquals("pageSize", paging.limitParameter());
        assertTrue(p.requiredParams().contains("pageSize"));
    }

    @Test
    public void noPagingWhenAbsent() {
        SqlLikePlanPreview p = PojoLensSql.parse("where active = true").planPreview();
        assertFalse(p.hasPaging());
        assertNull(p.paging());
    }

    // --- combined query ---

    @Test
    public void complexQueryPreviewCoversAllClauses() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select department, count(*) as headcount from Employee "
                        +
                "where active = true and salary >= :min "
                        +
                "group by department "
                        +
                "order by headcount desc "
                        +
                "limit :top").planPreview();

        assertEquals("Employee", p.source());
        assertFalse(p.isWildcard());
        assertEquals(2, p.selectFields().size());
        assertFalse(p.filters().isEmpty());
        assertTrue(p.hasGrouping());
        assertEquals(List.of("department"), p.groupByFields());
        assertEquals(1, p.orderFields().size());
        assertEquals("DESC", p.orderFields().get(0).direction());
        assertTrue(p.hasPaging());
        assertTrue(p.requiredParams().contains("min"));
        assertTrue(p.requiredParams().contains("top"));
        assertFalse(p.hasJoins());
        assertFalse(p.hasSubqueries());
        assertTrue(p.hasAggregation());
    }

    // --- HAVING / QUALIFY ---

    @Test
    public void havingFiltersReported() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select department, count(*) as cnt from Employee "
                        +
                "group by department having cnt > :min").planPreview();
        assertTrue(p.hasGrouping());
        assertFalse(p.havingFilters().isEmpty());
        assertNotNull(p.havingExpression());
        PlanPreviewFilter h = p.havingFilters().get(0);
        assertEquals("cnt", h.field());
        assertEquals(">", h.operator());
        assertEquals("PARAMETER", h.valueKind());
        assertEquals("min", h.parameterName());
    }

    @Test
    public void qualifyFiltersReportedForWindowQueries() {
        SqlLikePlanPreview p = PojoLensSql.parse(
                "select row_number() over (partition by department order by salary desc) as rank "
                        +
                "from Employee qualify rank <= 3").planPreview();
        assertFalse(p.qualifyFilters().isEmpty());
        assertNotNull(p.qualifyExpression());
        PlanPreviewFilter q = p.qualifyFilters().get(0);
        assertEquals("rank", q.field());
        assertEquals("<=", q.operator());
    }
}
