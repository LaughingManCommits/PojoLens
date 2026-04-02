package laughing.man.commits.natural;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.natural.parser.NaturalQueryParser;
import laughing.man.commits.sqllike.ast.ParameterValueAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    public void shouldTranslateStartsWithToRegexMatch() {
        QueryAst ast = NaturalQueryParser.parse("show all where department starts with Eng");
        assertEquals(Clauses.MATCHES, ast.filters().get(0).clause());
        assertEquals("^\\QEng\\E.*", ast.filters().get(0).value());
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
