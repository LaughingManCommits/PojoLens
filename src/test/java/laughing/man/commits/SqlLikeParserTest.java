package laughing.man.commits;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.ParameterValueAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.sqllike.parser.SqlLikeParseException;
import laughing.man.commits.sqllike.parser.SqlLikeParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SqlLikeParserTest {

    @Test
    public void shouldParseWhereOrderLimitCaseInsensitive() {
        QueryAst ast = SqlLikeParser.parse("SeLeCt * FrOm rows WhErE stringField = 'abc' "
                + "AnD integerField >= 10 OR active = true ORDER BY integerField DESC, stringField ASC LIMIT 25");

        assertNotNull(ast.select());
        assertTrue(ast.select().wildcard());
        assertEquals("rows", ast.select().sourceName());

        assertEquals(3, ast.filters().size());
        FilterAst f0 = ast.filters().get(0);
        assertEquals("stringField", f0.field());
        assertEquals(Clauses.EQUAL, f0.clause());
        assertEquals("abc", f0.value());
        assertNull(f0.separator());

        FilterAst f1 = ast.filters().get(1);
        assertEquals("integerField", f1.field());
        assertEquals(Clauses.BIGGER_EQUAL, f1.clause());
        assertEquals(10, f1.value());
        assertEquals(Separator.AND, f1.separator());

        FilterAst f2 = ast.filters().get(2);
        assertEquals("active", f2.field());
        assertEquals(Clauses.EQUAL, f2.clause());
        assertEquals(Boolean.TRUE, f2.value());
        assertEquals(Separator.OR, f2.separator());

        assertEquals(2, ast.orders().size());
        assertEquals("integerField", ast.orders().get(0).field());
        assertEquals(Sort.DESC, ast.orders().get(0).sort());
        assertEquals("stringField", ast.orders().get(1).field());
        assertEquals(Sort.ASC, ast.orders().get(1).sort());

        assertEquals(Integer.valueOf(25), ast.limit());
    }

    @Test
    public void shouldParseSelectAliases() {
        QueryAst ast = SqlLikeParser.parse("select stringField as label, integerField as amount where integerField >= 2");
        assertNotNull(ast.select());
        assertEquals(2, ast.select().fields().size());
        assertEquals("stringField", ast.select().fields().get(0).field());
        assertEquals("label", ast.select().fields().get(0).alias());
        assertEquals("integerField", ast.select().fields().get(1).field());
        assertEquals("amount", ast.select().fields().get(1).alias());
    }

    @Test
    public void shouldParseEscapedSingleQuote() {
        QueryAst ast = SqlLikeParser.parse("where name = 'O''Reilly'");
        assertEquals(1, ast.filters().size());
        assertEquals("O'Reilly", ast.filters().get(0).value());
    }

    @Test
    public void shouldParseContainsAndMatchesOperators() {
        QueryAst ast = SqlLikeParser.parse("where name contains 'ab' and code matches '^[0-9]+$'");
        assertEquals(2, ast.filters().size());
        assertEquals(Clauses.CONTAINS, ast.filters().get(0).clause());
        assertEquals(Clauses.MATCHES, ast.filters().get(1).clause());
    }

    @Test
    public void shouldParseNamedParametersAsFilterValues() {
        QueryAst ast = SqlLikeParser.parse("where department = :dept and salary >= :minSalary");
        assertEquals(2, ast.filters().size());
        assertTrue(ast.filters().get(0).value() instanceof ParameterValueAst);
        assertEquals("dept", ((ParameterValueAst) ast.filters().get(0).value()).name());
        assertTrue(ast.filters().get(1).value() instanceof ParameterValueAst);
        assertEquals("minSalary", ((ParameterValueAst) ast.filters().get(1).value()).name());
    }

    @Test
    public void shouldParseWhereInSubquery() {
        QueryAst ast = SqlLikeParser.parse("where department in (select department where active = true)");
        assertEquals(1, ast.filters().size());
        assertEquals(Clauses.IN, ast.filters().get(0).clause());
        assertTrue(ast.filters().get(0).value() instanceof SubqueryValueAst);
        SubqueryValueAst subquery = (SubqueryValueAst) ast.filters().get(0).value();
        assertEquals("select department where active=true", subquery.source());
        assertEquals(1, subquery.query().filters().size());
        assertEquals("department", subquery.query().select().fields().get(0).field());
    }

    @Test
    public void shouldRejectMissingValue() {
        try {
            PojoLens.parse("where name =");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Expected value in WHERE clause"));
            assertTrue(ex.getMessage().contains("in WHERE clause"));
            assertTrue(ex.getMessage().contains("line 1, column 13"));
            assertTrue(ex.getMessage().contains("span"));
            assertTrue(ex.getMessage().contains("^"));
        }
    }

    @Test
    public void shouldRejectUnterminatedString() {
        try {
            PojoLens.parse("where name = 'abc");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unterminated string literal"));
            assertTrue(ex.getMessage().contains("in QUERY clause"));
            assertTrue(ex.getMessage().contains("line 1, column 14"));
            assertTrue(ex.getMessage().contains("^"));
        }
    }

    @Test
    public void shouldParseGroupByWithCountStar() {
        QueryAst ast = SqlLikeParser.parse("select department, count(*) as total group by department");
        assertEquals(1, ast.groupByFields().size());
        assertEquals("department", ast.groupByFields().get(0));
        assertTrue(ast.hasAggregation());
        assertEquals(Metric.COUNT, ast.select().fields().get(1).metric());
        assertTrue(ast.select().fields().get(1).countAll());
        assertEquals("total", ast.select().fields().get(1).alias());
    }

    @Test
    public void shouldParseAggregateFunctionsWithAliasesAndGroupBy() {
        QueryAst ast = SqlLikeParser.parse("select department, sum(salary) as totalSalary, avg(salary) as avgSalary, min(salary), max(salary) group by department");

        assertNotNull(ast.select());
        assertEquals(5, ast.select().fields().size());
        assertEquals("department", ast.select().fields().get(0).field());
        assertFalse(ast.select().fields().get(0).metricField());

        assertEquals(Metric.SUM, ast.select().fields().get(1).metric());
        assertEquals("salary", ast.select().fields().get(1).field());
        assertEquals("totalSalary", ast.select().fields().get(1).alias());

        assertEquals(Metric.AVG, ast.select().fields().get(2).metric());
        assertEquals("avgSalary", ast.select().fields().get(2).alias());

        assertEquals(Metric.MIN, ast.select().fields().get(3).metric());
        assertEquals("min_salary", ast.select().fields().get(3).outputName());

        assertEquals(Metric.MAX, ast.select().fields().get(4).metric());
        assertEquals("max_salary", ast.select().fields().get(4).outputName());
        assertEquals(1, ast.groupByFields().size());
    }

    @Test
    public void shouldParseBucketFunctionInSelect() {
        QueryAst ast = SqlLikeParser.parse("select bucket(hireDate,'month') as period, count(*) as total group by period");
        assertEquals(2, ast.select().fields().size());
        assertTrue(ast.select().fields().get(0).timeBucketField());
        assertEquals("hireDate", ast.select().fields().get(0).field());
        assertEquals("period", ast.select().fields().get(0).outputName());
        assertEquals(1, ast.groupByFields().size());
        assertEquals("period", ast.groupByFields().get(0));
    }

    @Test
    public void shouldParseBucketFunctionWithExplicitCalendarPreset() {
        QueryAst ast = SqlLikeParser.parse(
                "select bucket(hireDate,'week','Europe/Amsterdam','sunday') as period, count(*) as total group by period");
        assertEquals(2, ast.select().fields().size());
        assertTrue(ast.select().fields().get(0).timeBucketField());
        assertEquals("Europe/Amsterdam", ast.select().fields().get(0).timeBucketPreset().zoneId().getId());
        assertEquals(java.time.DayOfWeek.SUNDAY, ast.select().fields().get(0).timeBucketPreset().weekStart());
    }

    @Test
    public void shouldParseHavingWithAliasesAndAggregateExpressions() {
        QueryAst ast = SqlLikeParser.parse(
                "select department, count(*) as total group by department having total >= 2 and count(*) > 1 order by total desc limit 5");
        assertEquals(1, ast.groupByFields().size());
        assertEquals(2, ast.havingFilters().size());

        FilterAst h0 = ast.havingFilters().get(0);
        assertEquals("total", h0.field());
        assertEquals(Clauses.BIGGER_EQUAL, h0.clause());
        assertEquals(2, h0.value());
        assertNull(h0.separator());

        FilterAst h1 = ast.havingFilters().get(1);
        assertEquals("count(*)", h1.field());
        assertEquals(Clauses.BIGGER, h1.clause());
        assertEquals(1, h1.value());
        assertEquals(Separator.AND, h1.separator());
    }

    @Test
    public void shouldParseHavingWithoutGroupByForValidationStage() {
        QueryAst ast = SqlLikeParser.parse("select count(*) as total having total > 1");
        assertEquals(1, ast.havingFilters().size());
        assertEquals("total", ast.havingFilters().get(0).field());
    }

    @Test
    public void shouldParseHavingWithOrSeparator() {
        QueryAst ast = SqlLikeParser.parse(
                "select department, count(*) as total group by department having total >= 2 or count(*) = 1");
        assertEquals(2, ast.havingFilters().size());
        assertEquals(Separator.OR, ast.havingFilters().get(1).separator());
        assertEquals("count(*)", ast.havingFilters().get(1).field());
    }

    @Test
    public void shouldRejectGroupByAfterHaving() {
        try {
            SqlLikeParser.parse("select department, count(*) as total having total >= 2 group by department");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unexpected token 'group'"));
        }
    }

    @Test
    public void shouldRejectHavingAfterOrderBy() {
        try {
            SqlLikeParser.parse("select department, count(*) as total group by department order by total desc having total >= 2");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unexpected token 'having'"));
        }
    }

    @Test
    public void shouldRejectMalformedAggregateSyntax() {
        try {
            SqlLikeParser.parse("select sum salary where salary > 1");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Expected '(' after aggregate function"));
        }

        try {
            SqlLikeParser.parse("select sum(salary where salary > 1");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Expected ')' after aggregate function argument"));
        }
    }

    @Test
    public void shouldRejectDuplicateSelectOutputNames() {
        try {
            SqlLikeParser.parse("select salary as metric, sum(salary) as metric");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Duplicate SELECT output name 'metric'"));
        }
    }

    @Test
    public void shouldRejectUnsupportedBucketGranularity() {
        try {
            SqlLikeParser.parse("select bucket(hireDate,'hour') as period, count(*) as total group by period");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unsupported time bucket"));
        }
    }

    @Test
    public void shouldRejectWeekStartForNonWeekBucket() {
        try {
            SqlLikeParser.parse("select bucket(hireDate,'month','UTC','sunday') as period, count(*) as total group by period");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Week start is only supported for week time buckets"));
        }
    }

    @Test
    public void shouldRejectNonIntegerLimit() {
        try {
            PojoLens.parse("where name = 'a' limit 1.5");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("LIMIT must be an integer"));
            assertTrue(ex.getMessage().contains("line 1, column 24"));
        }
    }

    @Test
    public void shouldRejectBlankParameterNameToken() {
        try {
            PojoLens.parse("where salary >= :");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Expected parameter name after ':'"));
        }
    }

    @Test
    public void shouldRejectInWithoutSelectSubquery() {
        try {
            PojoLens.parse("where department in ('Engineering')");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("IN currently requires a subquery starting with SELECT"));
        }
    }

    @Test
    public void shouldReportLineAndColumnForMultilineErrors() {
        String query = "select stringField\nwhere name =\norder by integerField";
        try {
            PojoLens.parse(query);
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Expected value in WHERE clause"));
            assertTrue(ex.getMessage().contains("in WHERE clause"));
            assertTrue(ex.getMessage().contains("line 3, column 1"));
            assertTrue(ex.getMessage().contains("order by integerField"));
            assertTrue(ex.getMessage().contains("^"));
        }
    }

    @Test
    public void shouldBuildWhereBooleanTreeWithParenthesesAndPrecedence() {
        QueryAst ast = SqlLikeParser.parse(
                "where (active = true or integerField >= 10) and stringField = 'abc'");
        assertNotNull(ast.whereExpression());
        assertTrue(ast.whereExpression() instanceof FilterBinaryAst);

        FilterBinaryAst root = (FilterBinaryAst) ast.whereExpression();
        assertEquals(Separator.AND, root.operator());
        assertTrue(root.left() instanceof FilterBinaryAst);
        assertTrue(root.right() instanceof FilterPredicateAst);
        assertEquals(3, ast.filters().size());
    }

    @Test
    public void shouldBuildHavingBooleanTreeWithParenthesesAndPrecedence() {
        QueryAst ast = SqlLikeParser.parse(
                "select department, count(*) as total group by department "
                        + "having count(*) >= 2 or (count(*) = 1 and department = 'HR')");
        assertNotNull(ast.havingExpression());
        assertTrue(ast.havingExpression() instanceof FilterBinaryAst);

        FilterBinaryAst root = (FilterBinaryAst) ast.havingExpression();
        assertEquals(Separator.OR, root.operator());
        assertTrue(root.left() instanceof FilterPredicateAst);
        assertTrue(root.right() instanceof FilterBinaryAst);
        assertEquals(3, ast.havingFilters().size());
    }

    @Test
    public void shouldParseComputedSelectExpressionWithAlias() {
        QueryAst ast = SqlLikeParser.parse("select value * 1.2 as boosted where value >= 2");
        assertNotNull(ast.select());
        assertEquals(1, ast.select().fields().size());
        SelectFieldAst field = ast.select().fields().get(0);
        assertTrue(field.computedField());
        assertEquals("boosted", field.outputName());
        assertEquals("value*1.2", field.field());
    }

    @Test
    public void shouldRejectComputedSelectExpressionWithoutAlias() {
        try {
            SqlLikeParser.parse("select value * 2 where value >= 2");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Computed SELECT expressions require AS alias"));
        }
    }

    @Test
    public void parseExceptionShouldExposeClauseAndSpan() {
        try {
            SqlLikeParser.parse("select name where = 'x'");
            fail("Expected parse error");
        } catch (SqlLikeParseException ex) {
            assertEquals("WHERE", ex.clause());
            assertTrue(ex.clauseEnd() >= ex.clauseStart());
            assertNotNull(ex.snippet());
            assertTrue(ex.snippet().contains("^"));
        }
    }
}

