package laughing.man.commits;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.internal.FluentEngine;
import laughing.man.commits.sqllike.PageResult;
import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.SqlLikeCursor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for WP-23 core follow-ups.
 */
class CoreFollowUpTest {

    private static final TypedField<Event, Instant> AT = TypedField.of("at", Instant.class);

    // --- date precision: values exact, literals at their written precision ---

    @Test
    void temporalValuesCompareExactly() {
        List<Event> rows = List.of(
                event(1, Instant.parse("2024-01-02T10:00:00.100Z")),
                event(2, Instant.parse("2024-01-02T10:00:00.900Z"))
        );

        assertEquals(List.of(1), ids(TypedQuery.from(Event.class)
                .where(AT.eq(Instant.parse("2024-01-02T10:00:00.100Z"))).filter(rows)));
        assertEquals(List.of(2), ids(TypedQuery.from(Event.class)
                .where(AT.gt(Instant.parse("2024-01-02T10:00:00.100Z"))).filter(rows)));
        assertEquals(List.of(2), ids(PojoLensSql.parse("where at > :t")
                .params(Map.of("t", Instant.parse("2024-01-02T10:00:00.100Z"))).filter(rows, Event.class)));
    }

    @Test
    void textLiteralsCompareAtTheirWrittenPrecision() {
        List<Event> rows = List.of(
                event(1, LocalDateTime.of(2024, 1, 2, 10, 0, 0, 500_000_000)),
                event(2, LocalDateTime.of(2024, 1, 2, 10, 0, 1)),
                event(3, LocalDateTime.of(2024, 1, 3, 0, 0))
        );

        assertEquals(List.of(1), ids(PojoLensSql.parse("where local = '2024-01-02 10:00:00'").filter(rows, Event.class)));
        assertEquals(List.of(1), ids(PojoLensSql.parse("where local = '2024-01-02T10:00:00.500'").filter(rows, Event.class)));
        assertEquals(List.of(), ids(PojoLensSql.parse("where local = '2024-01-02T10:00:00.499'").filter(rows, Event.class)));
        assertEquals(List.of(1, 2), ids(PojoLensSql.parse("where local = '2024-01-02'").filter(rows, Event.class)));
        assertEquals(List.of(3), ids(PojoLensSql.parse("where local > '2024-01-02'").filter(rows, Event.class)));
    }

    @Test
    void keysetPagingOnSubSecondTimestampsDoesNotSkipRows() {
        List<Event> rows = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Event event = event(i, (Instant) null);
            event.ts = new Date(1_700_000_000_000L + i * 100L);
            rows.add(event);
        }

        PageResult<Event> first = PojoLensSql.parse("order by ts asc limit 3").filterPage(rows, Event.class);
        PageResult<Event> second = PojoLensSql.parse("order by ts asc limit 3")
                .keysetAfter(first.nextCursor().orElseThrow())
                .filterPage(rows, Event.class);

        assertEquals(List.of(1, 2, 3), ids(first.rows()));
        assertEquals(List.of(4, 5, 6), ids(second.rows()));
        assertTrue(second.hasMore());
    }

    // --- keyset paging over aliases, aggregates, windows, and null sort values ---

    @Test
    void keysetPagesOverAggregateAliases() {
        List<Staff> rows = List.of(staff(1, "A", 10), staff(2, "A", 20), staff(3, "A", 30),
                staff(4, "B", 10), staff(5, "B", 20), staff(6, "C", 10), staff(7, "D", 5));
        String query = "select dept, count(*) as c group by dept order by c desc, dept asc limit 2";

        PageResult<DeptCount> first = PojoLensSql.parse(query).filterPage(rows, DeptCount.class);
        PageResult<DeptCount> second = PojoLensSql.parse(query)
                .keysetAfter(first.nextCursor().orElseThrow()).filterPage(rows, DeptCount.class);

        assertEquals(List.of("A", "B"), first.rows().stream().map(row -> row.dept).toList());
        assertEquals(List.of("C", "D"), second.rows().stream().map(row -> row.dept).toList());
    }

    @Test
    void keysetPagesOverSelectAliases() {
        List<Staff> rows = List.of(staff(1, "B", 1), staff(2, "A", 1), staff(3, "B", 1), staff(4, "A", 1));
        String query = "select dept as d, id order by d asc, id asc limit 2";

        PageResult<AliasRow> first = PojoLensSql.parse(query).filterPage(rows, AliasRow.class);
        PageResult<AliasRow> second = PojoLensSql.parse(query)
                .keysetAfter(first.nextCursor().orElseThrow()).filterPage(rows, AliasRow.class);

        assertEquals(List.of(2, 4), first.rows().stream().map(row -> row.id).toList());
        assertEquals(List.of(1, 3), second.rows().stream().map(row -> row.id).toList());
    }

    @Test
    void keysetPagesOverWindowAliases() {
        List<Staff> rows = List.of(staff(1, "A", 50), staff(2, "A", 40), staff(3, "A", 30), staff(4, "A", 20));
        String query = "select id, row_number() over (order by salary desc) as rn order by rn asc limit 2";

        PageResult<RankRow> first = PojoLensSql.parse(query).filterPage(rows, RankRow.class);
        PageResult<RankRow> second = PojoLensSql.parse(query)
                .keysetAfter(first.nextCursor().orElseThrow()).filterPage(rows, RankRow.class);

        assertEquals(List.of(1, 2), first.rows().stream().map(row -> row.id).toList());
        assertEquals(List.of(3, 4), second.rows().stream().map(row -> row.id).toList());
    }

    @Test
    void keysetPagingReachesRowsWithNullSortValues() {
        List<Staff> rows = new ArrayList<>();
        Integer[] scores = {10, 20, null, 40, 50, null, 70, 80, null};
        for (int i = 0; i < scores.length; i++) {
            Staff row = staff(i + 1, "A", 1);
            row.score = scores[i];
            rows.add(row);
        }
        String query = "order by score desc, id desc limit 4";

        List<Integer> seen = new ArrayList<>();
        PageResult<Staff> page = PojoLensSql.parse(query).filterPage(rows, Staff.class);
        seen.addAll(page.rows().stream().map(row -> row.id).toList());
        while (page.hasMore()) {
            page = PojoLensSql.parse(query)
                    .keysetAfter(SqlLikeCursor.fromToken(page.nextCursor().orElseThrow().toToken()))
                    .filterPage(rows, Staff.class);
            seen.addAll(page.rows().stream().map(row -> row.id).toList());
        }

        assertEquals(List.of(8, 7, 5, 4, 2, 1, 9, 6, 3), seen);
        List<Staff> beforeFirstNull = PojoLensSql.parse(query)
                .keysetBefore(SqlLikeCursor.builder().put("score", null).put("id", 9).build())
                .filter(rows, Staff.class);
        assertEquals(List.of(5, 4, 2, 1), beforeFirstNull.stream().map(row -> row.id).toList());
    }

    @Test
    void cursorTokensRoundTripTemporalEnumAndUuidValues() {
        SqlLikeCursor cursor = SqlLikeCursor.builder()
                .put("day", java.time.LocalDate.of(2024, 1, 2))
                .put("at", Instant.parse("2024-01-02T10:00:00.123Z"))
                .put("status", java.time.DayOfWeek.MONDAY)
                .put("uid", java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .put("missing", null)
                .build();

        Map<String, Object> decoded = SqlLikeCursor.fromToken(cursor.toToken()).values();

        assertEquals(java.time.LocalDate.of(2024, 1, 2), decoded.get("day"));
        assertEquals(Instant.parse("2024-01-02T10:00:00.123Z"), decoded.get("at"));
        assertEquals("MONDAY", decoded.get("status"));
        assertEquals(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"), decoded.get("uid"));
        assertTrue(decoded.containsKey("missing"));
        assertNull(decoded.get("missing"));
    }

    // --- typed field validation ---

    @Test
    void typedQueryRejectsUnknownFieldNamesWithSuggestion() {
        TypedField<Event, Integer> typo = TypedField.of("idd", Integer.class);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TypedQuery.from(Event.class).where(typo.eq(1)).filter(List.of(event(1, (Instant) null))));
        QueryDiagnostics diagnostics = TypedQuery.from(Event.class).where(typo.eq(1)).diagnostics();

        assertTrue(error.getMessage().contains("Unknown field 'idd'"), error::getMessage);
        assertTrue(error.getMessage().contains("did you mean id"), error::getMessage);
        assertFalse(diagnostics.valid());
    }

    @Test
    void typedQueryAcceptsSourceComputedAndAliasFields() {
        List<Event> rows = List.of(event(1, (Instant) null), event(2, (Instant) null));
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder().add("twice", "id * 2", Integer.class).build();

        assertEquals(List.of(2), ids(TypedQuery.from(Event.class)
                .computedFields(registry)
                .where(TypedField.<Event, Integer>of("twice", Integer.class).eq(4))
                .filter(rows)));
    }

    // --- smaller follow-ups ---

    @Test
    void sqlCountOfFieldCountsNonNullValues() {
        Event withAt = event(1, Instant.parse("2024-01-02T00:00:00Z"));
        List<Event> rows = List.of(withAt, event(2, (Instant) null), event(3, (Instant) null));

        Counts counts = PojoLensSql.parse("select count(*) as total, count(at) as withAt").filter(rows, Counts.class).get(0);

        assertEquals(3L, counts.total);
        assertEquals(1L, counts.withAt);
    }

    @Test
    void naturalFieldNamedLikeFillerWordStaysReferencable() {
        Letter first = new Letter();
        first.a = 5;
        Letter second = new Letter();
        second.a = 7;

        List<Letter> rows = PojoLensNatural.parse("show rows where a is 5").filter(List.of(first, second), Letter.class);

        assertEquals(1, rows.size());
        assertEquals(5, rows.get(0).a);
    }

    @Test
    void groupKeysWithSeparatorCharactersStayDistinct() {
        Pair left = new Pair();
        left.x = "x,y";
        left.y = "z";
        Pair right = new Pair();
        right.x = "x";
        right.y = "y,z";

        Map<String, List<Pair>> groups = FluentEngine.newQueryBuilder(List.of(left, right))
                .addGroup("x", 1)
                .addGroup("y", 2)
                .initFilter()
                .filterGroups(Pair.class);

        assertEquals(2, groups.size());
    }

    // --- records ---

    @Test
    void recordsWorkAsSourcesAndProjectionTargets() {
        List<Sale> sales = List.of(new Sale("east", 10, new Region("EU", "Amsterdam")),
                new Sale("west", 25, new Region("US", "Denver")));

        List<Sale> filtered = PojoLensSql.parse("where amount > 15").filter(sales, Sale.class);
        List<SaleSummary> summary = PojoLensSql.parse("select name, amount order by amount desc").filter(sales, SaleSummary.class);
        List<Sale> typed = TypedQuery.from(Sale.class)
                .where(TypedField.<Sale, String>of("region.code", String.class).eq("EU"))
                .filter(sales);

        assertEquals(List.of(new Sale("west", 25, new Region("US", "Denver"))), filtered);
        assertEquals(List.of(new SaleSummary("west", 25), new SaleSummary("east", 10)), summary);
        assertEquals(List.of("east"), typed.stream().map(Sale::name).toList());
    }

    @Test
    void csvLoaderMaterializesRecordRows() {
        List<SaleSummary> rows = PojoLensFiles.csv(
                new java.io.StringReader("name,amount\neast,10\nwest,25\n"), SaleSummary.class);

        assertEquals(List.of(new SaleSummary("east", 10), new SaleSummary("west", 25)), rows);
    }

    // --- fixtures ---

    private static List<Integer> ids(List<Event> rows) {
        return rows.stream().map(row -> row.id).toList();
    }

    private static Event event(int id, Instant at) {
        Event event = new Event();
        event.id = id;
        event.at = at;
        return event;
    }

    private static Event event(int id, LocalDateTime local) {
        Event event = new Event();
        event.id = id;
        event.local = local;
        return event;
    }

    public static class Event {
        public int id;
        public Instant at;
        public LocalDateTime local;
        public Date ts;

        Event() {
        }
    }

    private static Staff staff(int id, String dept, int salary) {
        Staff staff = new Staff();
        staff.id = id;
        staff.dept = dept;
        staff.salary = salary;
        return staff;
    }

    public static class Staff {
        public int id;
        public String dept;
        public int salary;
        public Integer score;

        Staff() {
        }
    }

    public static class DeptCount {
        public String dept;
        public Long c;

        DeptCount() {
        }
    }

    public static class AliasRow {
        public String d;
        public int id;

        AliasRow() {
        }
    }

    public static class RankRow {
        public int id;
        public Long rn;

        RankRow() {
        }
    }

    public static class Counts {
        public Long total;
        public Long withAt;

        Counts() {
        }
    }

    public static class Letter {
        public int a;

        Letter() {
        }
    }

    public static class Pair {
        public String x;
        public String y;

        Pair() {
        }
    }

    public record Region(String code, String city) {
    }

    public record Sale(String name, int amount, Region region) {
    }

    public record SaleSummary(String name, int amount) {
    }
}
