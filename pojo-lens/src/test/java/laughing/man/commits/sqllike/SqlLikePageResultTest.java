package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.testutil.BusinessFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SqlLikePageResultTest {

    // Ordered by salary desc, id desc: Cara(130000,3), Alice(120000,1), Dan(110000,4), Bob(90000,2)
    private static List<BusinessFixtures.Employee> source() {
        return BusinessFixtures.sampleEmployees();
    }

    // -----------------------------------------------------------------------
    // First-page behaviour
    // -----------------------------------------------------------------------

    @Test
    public void firstPageReturnsTrimmedRowsWithCursorWhenMoreExist() {
        PageResult<BusinessFixtures.Employee> page = PojoLensSql
                .parse("order by salary desc, id desc limit 2")
                .filterPage(source(), BusinessFixtures.Employee.class);

        assertEquals(2, page.rows().size());
        assertEquals("Cara", page.rows().get(0).name);
        assertEquals("Alice", page.rows().get(1).name);
        assertTrue(page.hasMore());
        assertTrue(page.nextCursor().isPresent());
    }

    @Test
    public void firstPageCursorPositionedAtLastVisibleRow() {
        PageResult<BusinessFixtures.Employee> page = PojoLensSql
                .parse("order by salary desc, id desc limit 2")
                .filterPage(source(), BusinessFixtures.Employee.class);

        SqlLikeCursor cursor = page.nextCursor().orElseThrow();
        assertEquals(120000, cursor.values().get("salary")); // Alice's salary
        assertEquals(1, cursor.values().get("id"));          // Alice's id
    }

    @Test
    public void firstPageReturnsAllRowsWithNoCursorWhenFewExist() {
        PageResult<BusinessFixtures.Employee> page = PojoLensSql
                .parse("order by salary desc, id desc limit 10")
                .filterPage(source(), BusinessFixtures.Employee.class);

        assertEquals(4, page.rows().size());
        assertFalse(page.hasMore());
        assertFalse(page.nextCursor().isPresent());
    }

    @Test
    public void emptySourceReturnsEmptyPage() {
        PageResult<BusinessFixtures.Employee> page = PojoLensSql
                .parse("order by salary desc, id desc limit 5")
                .filterPage(List.of(), BusinessFixtures.Employee.class);

        assertEquals(0, page.rows().size());
        assertFalse(page.hasMore());
        assertFalse(page.nextCursor().isPresent());
    }

    @Test
    public void exactlyLimitRowsDoesNotSetHasMore() {
        PageResult<BusinessFixtures.Employee> page = PojoLensSql
                .parse("order by salary desc, id desc limit 4")
                .filterPage(source(), BusinessFixtures.Employee.class);

        assertEquals(4, page.rows().size());
        assertFalse(page.hasMore());
        assertFalse(page.nextCursor().isPresent());
    }

    // -----------------------------------------------------------------------
    // Multi-page traversal
    // -----------------------------------------------------------------------

    @Test
    public void secondPageViaCursorReturnsCorrectRows() {
        SqlLikeQuery query = PojoLensSql.parse("order by salary desc, id desc limit 2");

        PageResult<BusinessFixtures.Employee> firstPage = query.filterPage(source(), BusinessFixtures.Employee.class);
        assertTrue(firstPage.hasMore());

        SqlLikeCursor cursor = firstPage.nextCursor().orElseThrow();
        PageResult<BusinessFixtures.Employee> secondPage = query
                .keysetAfter(cursor)
                .filterPage(source(), BusinessFixtures.Employee.class);

        assertEquals(2, secondPage.rows().size());
        assertEquals("Dan", secondPage.rows().get(0).name);
        assertEquals("Bob", secondPage.rows().get(1).name);
        assertFalse(secondPage.hasMore());
        assertFalse(secondPage.nextCursor().isPresent());
    }

    @Test
    public void fullTraversalCoversAllRows() {
        SqlLikeQuery query = PojoLensSql.parse("order by salary desc, id desc limit 2");
        List<BusinessFixtures.Employee> allRows = new java.util.ArrayList<>();

        PageResult<BusinessFixtures.Employee> page = query.filterPage(source(), BusinessFixtures.Employee.class);
        allRows.addAll(page.rows());

        while (page.hasMore()) {
            Optional<SqlLikeCursor> nextCursor = page.nextCursor();
            assertTrue(nextCursor.isPresent());
            page = query.keysetAfter(nextCursor.get()).filterPage(source(), BusinessFixtures.Employee.class);
            allRows.addAll(page.rows());
        }

        assertEquals(4, allRows.size());
        assertEquals(List.of("Cara", "Alice", "Dan", "Bob"),
                allRows.stream().map(e -> e.name).toList());
    }

    // -----------------------------------------------------------------------
    // Composite sort keys
    // -----------------------------------------------------------------------

    @Test
    public void compositeSortKeysAreAllPresentInCursor() {
        PageResult<BusinessFixtures.Employee> page = PojoLensSql
                .parse("order by department asc, name asc, id asc limit 1")
                .filterPage(source(), BusinessFixtures.Employee.class);

        assertTrue(page.hasMore());
        SqlLikeCursor cursor = page.nextCursor().orElseThrow();
        assertTrue(cursor.values().containsKey("department"));
        assertTrue(cursor.values().containsKey("name"));
        assertTrue(cursor.values().containsKey("id"));
        assertEquals(3, cursor.values().size());
    }

    @Test
    public void tieBrokenBySortFieldOrderInCursor() {
        // Two employees share salary 110000+: none in sample; use salary desc, id desc
        // First row must be Cara (130000, id=3); cursor uses both fields for exact page boundary
        PageResult<BusinessFixtures.Employee> page = PojoLensSql
                .parse("order by salary desc, id desc limit 1")
                .filterPage(source(), BusinessFixtures.Employee.class);

        SqlLikeCursor cursor = page.nextCursor().orElseThrow();
        assertEquals(130000, cursor.values().get("salary"));
        assertEquals(3, cursor.values().get("id"));
    }

    // -----------------------------------------------------------------------
    // Cursor token round-trip
    // -----------------------------------------------------------------------

    @Test
    public void pageResultCursorSurvivesTokenRoundtrip() {
        PageResult<BusinessFixtures.Employee> page = PojoLensSql
                .parse("order by salary desc, id desc limit 2")
                .filterPage(source(), BusinessFixtures.Employee.class);

        SqlLikeCursor original = page.nextCursor().orElseThrow();
        SqlLikeCursor decoded = SqlLikeCursor.fromToken(original.toToken());
        assertEquals(original, decoded);
    }

    // -----------------------------------------------------------------------
    // Error: missing ORDER BY
    // -----------------------------------------------------------------------

    @Test
    public void missingOrderByThrowsPageOrderRequired() {
        try {
            PojoLensSql.parse("where active = true limit 10")
                    .filterPage(source(), BusinessFixtures.Employee.class);
            fail("Expected PAGE_ORDER_REQUIRED failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PAGE_ORDER_REQUIRED));
        }
    }

    // -----------------------------------------------------------------------
    // Error: missing LIMIT
    // -----------------------------------------------------------------------

    @Test
    public void missingLimitThrowsPageLimitRequired() {
        try {
            PojoLensSql.parse("order by salary desc")
                    .filterPage(source(), BusinessFixtures.Employee.class);
            fail("Expected PAGE_LIMIT_REQUIRED failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PAGE_LIMIT_REQUIRED));
        }
    }

    @Test
    public void parameterizedLimitWithoutBindingThrowsPageLimitRequired() {
        try {
            PojoLensSql.parse("order by salary desc limit :n")
                    .filterPage(source(), BusinessFixtures.Employee.class);
            fail("Expected PAGE_LIMIT_REQUIRED failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PAGE_LIMIT_REQUIRED));
        }
    }

    @Test
    public void parameterizedLimitBoundBeforeFilterPageSucceeds() {
        PageResult<BusinessFixtures.Employee> page = PojoLensSql
                .parse("order by salary desc, id desc limit :n")
                .params(java.util.Map.of("n", 2))
                .filterPage(source(), BusinessFixtures.Employee.class);

        assertEquals(2, page.rows().size());
        assertTrue(page.hasMore());
    }

    @Test
    public void zeroLimitThrowsPageLimitInvalid() {
        try {
            PojoLensSql.parse("order by salary desc, id desc limit 0")
                    .filterPage(source(), BusinessFixtures.Employee.class);
            fail("Expected PAGE_LIMIT_INVALID failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PAGE_LIMIT_INVALID));
        }
    }

    @Test
    public void parameterizedLimitBoundToZeroThrowsPageLimitInvalid() {
        try {
            PojoLensSql.parse("order by salary desc, id desc limit :n")
                    .params(java.util.Map.of("n", 0))
                    .filterPage(source(), BusinessFixtures.Employee.class);
            fail("Expected PAGE_LIMIT_INVALID failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PAGE_LIMIT_INVALID));
        }
    }

    @Test
    public void offsetThrowsPageOffsetUnsupported() {
        try {
            PojoLensSql.parse("order by salary desc, id desc limit 2 offset 1")
                    .filterPage(source(), BusinessFixtures.Employee.class);
            fail("Expected PAGE_OFFSET_UNSUPPORTED failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PAGE_OFFSET_UNSUPPORTED));
        }
    }

    @Test
    public void parameterizedOffsetThrowsPageOffsetUnsupportedBeforeCursorCreation() {
        try {
            PojoLensSql.parse("order by salary desc, id desc limit 2 offset :offset")
                    .filterPage(source(), BusinessFixtures.Employee.class);
            fail("Expected PAGE_OFFSET_UNSUPPORTED failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PAGE_OFFSET_UNSUPPORTED));
        }
    }

    // -----------------------------------------------------------------------
    // Error: null ORDER BY field value
    // -----------------------------------------------------------------------

    @Test
    public void nullOrderByFieldValueThrowsPageCursorFieldUnreadable() {
        List<NullNameRow> rows = List.of(
                new NullNameRow(1, null),
                new NullNameRow(2, "Bob")
        );
        try {
            PojoLensSql.parse("order by name asc, id asc limit 1")
                    .filterPage(rows, NullNameRow.class);
            fail("Expected PAGE_CURSOR_FIELD_UNREADABLE failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.PAGE_CURSOR_FIELD_UNREADABLE));
        }
    }

    // -----------------------------------------------------------------------
    // JoinBindings variant
    // -----------------------------------------------------------------------

    @Test
    public void filterPageWithJoinBindingsAcceptsEmptyBindings() {
        PageResult<BusinessFixtures.Employee> page = PojoLensSql
                .parse("order by salary desc, id desc limit 2")
                .filterPage(source(), JoinBindings.empty(), BusinessFixtures.Employee.class);

        assertEquals(2, page.rows().size());
        assertTrue(page.hasMore());
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    public static class NullNameRow {
        public int id;
        public String name;

        public NullNameRow() {
        }

        public NullNameRow(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
