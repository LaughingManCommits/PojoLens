package laughing.man.commits.sqllike.internal.execution;

import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.RawQueryRow;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.parser.SqlLikeParser;
import laughing.man.commits.util.QueryFieldLookupUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlLikeExecutionSupportTest {

    @Test
    void projectAliasedRowsShouldResolveQueryRowFieldsByNameWhenRowOrderDiffers() {
        List<QueryRow> source = List.of(
                rawQueryRow(new Object[]{1, 2}, "a", "b"),
                rawQueryRow(new Object[]{20, 10}, "b", "a")
        );

        List<QueryRow> results = SqlLikeExecutionSupport.projectAliasedRows(
                source,
                QueryRow.class,
                select("select a as leftValue, b as rightValue")
        );

        assertEquals(2, results.size());
        assertEquals(1, queryRowValue(results.get(0), "leftValue"));
        assertEquals(2, queryRowValue(results.get(0), "rightValue"));
        assertEquals(10, queryRowValue(results.get(1), "leftValue"));
        assertEquals(20, queryRowValue(results.get(1), "rightValue"));
    }

    @Test
    void projectAliasedRowsShouldResolveComputedIdentifiersByNameWhenRowOrderDiffers() {
        List<QueryRow> source = List.of(
                rawQueryRow(new Object[]{1, 2}, "a", "b"),
                rawQueryRow(new Object[]{20, 10}, "b", "a")
        );

        List<QueryRow> results = SqlLikeExecutionSupport.projectAliasedRows(
                source,
                QueryRow.class,
                select("select a + b as total, a as leftValue")
        );

        assertEquals(2, results.size());
        assertEquals(3.0, queryRowNumericValue(results.get(0), "total"), 0.000001);
        assertEquals(1, queryRowValue(results.get(0), "leftValue"));
        assertEquals(30.0, queryRowNumericValue(results.get(1), "total"), 0.000001);
        assertEquals(10, queryRowValue(results.get(1), "leftValue"));
    }

    private static SelectAst select(String query) {
        return SqlLikeParser.parse(query).select();
    }

    private static QueryRow rawQueryRow(Object[] values, String... fieldNames) {
        return new RawQueryRow(values, List.of(fieldNames));
    }

    private static Object queryRowValue(QueryRow row, String fieldName) {
        return QueryFieldLookupUtil.findFieldValue(row.getFields(), fieldName);
    }

    private static double queryRowNumericValue(QueryRow row, String fieldName) {
        return ((Number) queryRowValue(row, fieldName)).doubleValue();
    }
}
