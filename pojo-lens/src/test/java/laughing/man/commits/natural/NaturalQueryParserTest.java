package laughing.man.commits.natural;

import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.natural.parser.NaturalQueryParseResult;
import laughing.man.commits.natural.parser.NaturalQueryParser;
import laughing.man.commits.sqllike.ast.ParameterValueAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NaturalQueryParserTest {

    @Test
    public void shouldParseControlledProjectionFilterSortAndPaging() {
        QueryAst ast = NaturalQueryParser.parse(
                "show name, salary where department is :dept and salary is at least :minSalary "
                        + "sort by salary descending limit 10 offset :offset"
        );

        assertEquals(2, ast.select().fields().size());
        assertEquals("name", ast.select().fields().get(0).field());
        assertEquals("salary", ast.select().fields().get(1).field());

        assertEquals(2, ast.filters().size());
        assertEquals("department", ast.filters().get(0).field());
        assertEquals(Clauses.EQUAL, ast.filters().get(0).clause());
        assertTrue(ast.filters().get(0).value() instanceof ParameterValueAst);
        assertEquals("dept", ((ParameterValueAst) ast.filters().get(0).value()).name());

        assertEquals("salary", ast.filters().get(1).field());
        assertEquals(Clauses.BIGGER_EQUAL, ast.filters().get(1).clause());
        assertEquals(Separator.AND, ast.filters().get(1).separator());
        assertTrue(ast.filters().get(1).value() instanceof ParameterValueAst);
        assertEquals("minSalary", ((ParameterValueAst) ast.filters().get(1).value()).name());

        assertEquals(1, ast.orders().size());
        assertEquals("salary", ast.orders().get(0).field());
        assertEquals(Sort.DESC, ast.orders().get(0).sort());
        assertEquals(Integer.valueOf(10), ast.limit());
        assertEquals("offset", ast.offsetParameter());
    }

    @Test
    public void shouldNormalizeMultiWordFieldPhrasesAndWildcardTerms() {
        QueryAst ast = NaturalQueryParser.parse(
                "show employees where hire date is before :cutoff sort by hire date ascending"
        );

        assertTrue(ast.select().wildcard());
        assertEquals("hireDate", ast.filters().get(0).field());
        assertEquals(Clauses.SMALLER, ast.filters().get(0).clause());
        assertEquals("hireDate", ast.orders().get(0).field());
        assertEquals(Sort.ASC, ast.orders().get(0).sort());
    }

    @Test
    public void shouldTreatBoundedFillerWordsAsOptionalNoOps() {
        QueryAst ast = NaturalQueryParser.parse(
                "show me the employees where the hire date is before :cutoff sort by the hire date ascending"
        );

        assertTrue(ast.select().wildcard());
        assertEquals("hireDate", ast.filters().get(0).field());
        assertEquals(Clauses.SMALLER, ast.filters().get(0).clause());
        assertEquals("hireDate", ast.orders().get(0).field());
        assertEquals(Sort.ASC, ast.orders().get(0).sort());
    }

    @Test
    public void shouldTreatConnectorLeadInsAsBoundedWhereAliases() {
        QueryAst ast = NaturalQueryParser.parse(
                "show employees who are active and salary greater than or equal to 120000 "
                        + "ordered by salary in descending order limit 2"
        );

        assertTrue(ast.select().wildcard());
        assertEquals(2, ast.filters().size());
        assertEquals("active", ast.filters().get(0).field());
        assertEquals(Clauses.EQUAL, ast.filters().get(0).clause());
        assertEquals(true, ast.filters().get(0).value());
        assertEquals("salary", ast.filters().get(1).field());
        assertEquals(Clauses.BIGGER_EQUAL, ast.filters().get(1).clause());
        assertEquals(120000, ast.filters().get(1).value());
        assertEquals("salary", ast.orders().get(0).field());
        assertEquals(Sort.DESC, ast.orders().get(0).sort());
        assertEquals(Integer.valueOf(2), ast.limit());
    }

    @Test
    public void shouldTranslateStartsWithToRegexMatch() {
        QueryAst ast = NaturalQueryParser.parse("show all where department starts with Eng");
        assertEquals(Clauses.MATCHES, ast.filters().get(0).clause());
        assertEquals("^\\QEng\\E.*", ast.filters().get(0).value());
    }

    @Test
    public void shouldTranslateInflectedOperatorAliasesToExistingPredicates() {
        QueryAst ast = NaturalQueryParser.parse(
                "show all where department containing ine and name starting with A and name ending with e"
        );

        assertEquals(3, ast.filters().size());
        assertEquals("department", ast.filters().get(0).field());
        assertEquals(Clauses.CONTAINS, ast.filters().get(0).clause());
        assertEquals("ine", ast.filters().get(0).value());

        assertEquals("name", ast.filters().get(1).field());
        assertEquals(Clauses.MATCHES, ast.filters().get(1).clause());
        assertEquals("^\\QA\\E.*", ast.filters().get(1).value());

        assertEquals("name", ast.filters().get(2).field());
        assertEquals(Clauses.MATCHES, ast.filters().get(2).clause());
        assertEquals(".*\\Qe\\E$", ast.filters().get(2).value());
    }

    @Test
    public void shouldTranslateComparisonAliasesAndClauseAliases() {
        NaturalQueryParseResult parseResult = NaturalQueryParser.parseResult(
                "show department, count of employees as total "
                        + "where active equals true grouped by department having total greater than or equal to 2 "
                        + "ordered by total in descending order as a bar chart"
        );

        QueryAst ast = parseResult.ast();
        assertEquals(ChartType.BAR, parseResult.chartType());
        assertEquals(Clauses.EQUAL, ast.filters().get(0).clause());
        assertEquals(true, ast.filters().get(0).value());
        assertEquals(List.of("department"), ast.groupByFields());
        assertEquals(Clauses.BIGGER_EQUAL, ast.havingFilters().get(0).clause());
        assertEquals(2, ast.havingFilters().get(0).value());
        assertEquals("total", ast.orders().get(0).field());
        assertEquals(Sort.DESC, ast.orders().get(0).sort());
    }

    @Test
    public void shouldParseGroupedAggregateQueryWithHavingAndAggregateOrder() {
        QueryAst ast = NaturalQueryParser.parse(
                "show department, count of employees as total, sum of salary as total salary "
                        + "where active is true group by department having total is at least 2 "
                        + "sort by sum of salary descending limit 5"
        );

        assertEquals(3, ast.select().fields().size());
        assertEquals("department", ast.select().fields().get(0).field());
        assertTrue(ast.select().fields().get(1).metricField());
        assertEquals(Metric.COUNT, ast.select().fields().get(1).metric());
        assertTrue(ast.select().fields().get(1).countAll());
        assertEquals("total", ast.select().fields().get(1).outputName());
        assertTrue(ast.select().fields().get(2).metricField());
        assertEquals(Metric.SUM, ast.select().fields().get(2).metric());
        assertEquals("salary", ast.select().fields().get(2).field());
        assertEquals("totalSalary", ast.select().fields().get(2).outputName());

        assertEquals(List.of("department"), ast.groupByFields());
        assertEquals(1, ast.havingFilters().size());
        assertEquals("total", ast.havingFilters().get(0).field());
        assertEquals(Clauses.BIGGER_EQUAL, ast.havingFilters().get(0).clause());
        assertEquals(2, ast.havingFilters().get(0).value());

        assertEquals(1, ast.orders().size());
        assertEquals("sum(salary)", ast.orders().get(0).field());
        assertEquals(Sort.DESC, ast.orders().get(0).sort());
        assertEquals(Integer.valueOf(5), ast.limit());
    }

    @Test
    public void shouldParseTimeBucketPhraseAndTerminalChartPhrase() {
        NaturalQueryParseResult parseResult = NaturalQueryParser.parseResult(
                "show bucket hire date by week in Europe/Amsterdam starting sunday as period, "
                        + "sum of salary as payroll group by period sort by period ascending as line chart"
        );

        QueryAst ast = parseResult.ast();
        assertEquals(ChartType.LINE, parseResult.chartType());
        assertEquals(2, ast.select().fields().size());
        assertTrue(ast.select().fields().get(0).timeBucketField());
        assertEquals("hireDate", ast.select().fields().get(0).field());
        assertEquals("period", ast.select().fields().get(0).outputName());
        assertEquals("Europe/Amsterdam", ast.select().fields().get(0).timeBucketPreset().zoneId().getId());
        assertEquals("SUNDAY", ast.select().fields().get(0).timeBucketPreset().weekStart().name());
        assertTrue(ast.select().fields().get(1).metricField());
        assertEquals(Metric.SUM, ast.select().fields().get(1).metric());
        assertEquals("period", ast.orders().get(0).field());
        assertEquals(Sort.ASC, ast.orders().get(0).sort());
    }

    @Test
    public void shouldParseJoinQueryWithExplicitSourceLabels() {
        QueryAst ast = NaturalQueryParser.parse(
                "from companies as company join employees as employee "
                        + "on company id equals employee company id "
                        + "show company name as company name where employee title is Engineer "
                        + "sort by company name ascending"
        );

        assertEquals("companies", ast.select().sourceName());
        assertEquals(1, ast.joins().size());
        assertEquals(Join.INNER_JOIN, ast.joins().get(0).joinType());
        assertEquals("employees", ast.joins().get(0).childSource());
        assertEquals("companies.id", ast.joins().get(0).parentField());
        assertEquals("employees.companyId", ast.joins().get(0).childField());

        assertEquals("companies.name", ast.select().fields().get(0).field());
        assertEquals("companyName", ast.select().fields().get(0).outputName());
        assertEquals("employees.title", ast.filters().get(0).field());
        assertEquals("companies.name", ast.orders().get(0).field());
        assertEquals(Sort.ASC, ast.orders().get(0).sort());
    }

    @Test
    public void shouldParseJoinQueryWithOptionalArticlesAroundSourcesAndReferences() {
        QueryAst ast = NaturalQueryParser.parse(
                "from the companies as company join the employees as employee "
                        + "on the company id equals the employee company id "
                        + "show the company name as company name where the employee title is Engineer "
                        + "sort by the company name ascending"
        );

        assertEquals("companies", ast.select().sourceName());
        assertEquals(1, ast.joins().size());
        assertEquals("companies.id", ast.joins().get(0).parentField());
        assertEquals("employees.companyId", ast.joins().get(0).childField());
        assertEquals("companies.name", ast.select().fields().get(0).field());
        assertEquals("employees.title", ast.filters().get(0).field());
        assertEquals("companies.name", ast.orders().get(0).field());
    }

    @Test
    public void shouldParseWindowPhraseAndQualifyClause() {
        QueryAst ast = NaturalQueryParser.parse(
                "show department as dept, name, salary, "
                        + "row number by department ordered by salary descending then id ascending as rn "
                        + "where active is true qualify rn is at most 1 sort by dept ascending"
        );

        assertEquals(4, ast.select().fields().size());
        assertTrue(ast.select().fields().get(3).windowField());
        assertEquals("ROW_NUMBER", ast.select().fields().get(3).windowFunction());
        assertEquals(List.of("department"), ast.select().fields().get(3).windowPartitionFields());
        assertEquals(2, ast.select().fields().get(3).windowOrderFields().size());
        assertEquals("salary", ast.select().fields().get(3).windowOrderFields().get(0).field());
        assertEquals(Sort.DESC, ast.select().fields().get(3).windowOrderFields().get(0).sort());
        assertEquals("id", ast.select().fields().get(3).windowOrderFields().get(1).field());
        assertEquals(Sort.ASC, ast.select().fields().get(3).windowOrderFields().get(1).sort());

        assertEquals(1, ast.qualifyFilters().size());
        assertEquals("rn", ast.qualifyFilters().get(0).field());
        assertEquals(Clauses.SMALLER_EQUAL, ast.qualifyFilters().get(0).clause());
        assertEquals(1, ast.qualifyFilters().get(0).value());
        assertEquals("dept", ast.orders().get(0).field());
    }

    @Test
    public void shouldParseRunningWindowAggregatePhrase() {
        QueryAst ast = NaturalQueryParser.parse(
                "show department, seq, amount, "
                        + "running sum of amount by department ordered by seq ascending as running sum, "
                        + "running count of employees by department ordered by seq ascending as running rows"
        );

        assertEquals(5, ast.select().fields().size());
        assertTrue(ast.select().fields().get(3).windowField());
        assertEquals("SUM", ast.select().fields().get(3).windowFunction());
        assertEquals("amount", ast.select().fields().get(3).windowValueField());
        assertFalse(ast.select().fields().get(3).windowCountAll());
        assertEquals("runningSum", ast.select().fields().get(3).outputName());

        assertTrue(ast.select().fields().get(4).windowField());
        assertEquals("COUNT", ast.select().fields().get(4).windowFunction());
        assertTrue(ast.select().fields().get(4).windowCountAll());
        assertEquals("runningRows", ast.select().fields().get(4).outputName());
    }

    @Test
    public void shouldRejectParenthesesInWhereClause() {
        try {
            NaturalQueryParser.parse("show all where (active is true)");
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Parentheses are not supported"));
        }
    }
}
