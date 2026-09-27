package laughing.man.commits;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.enums.Join;
import laughing.man.commits.internal.FluentEngine;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.PageResult;
import laughing.man.commits.sqllike.SqlLikeCursor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for core engine defects: numeric precision in ordering and
 * aggregation, grouping keys, paging, joins, computed fields, and projection.
 */
class CoreEngineRegressionTest {

    // --- ordering and aggregation precision ---

    @Test
    void orderByKeepsLongOrderAboveDoublePrecision() {
        List<Item> rows = List.of(item(1_234_567_890_123_456_789L), item(1_234_567_890_123_456_790L), item(1_234_567_890_123_456_788L));

        assertEquals(List.of(1_234_567_890_123_456_788L, 1_234_567_890_123_456_789L, 1_234_567_890_123_456_790L),
                values(PojoLensSql.parse("order by value asc").filter(rows, Item.class)));
        assertEquals(List.of(1_234_567_890_123_456_790L, 1_234_567_890_123_456_789L, 1_234_567_890_123_456_788L),
                values(TypedQuery.from(Item.class).orderByDesc(TypedField.of("value", Long.class)).filter(rows)));
    }

    @Test
    void sumMinMaxOfLongsAreExact() {
        List<Item> rows = List.of(item("a", 9_007_199_254_740_993L), item("a", 2L), item("a", 9_007_199_254_740_995L));

        Totals totals = PojoLensSql
                .parse("select dept, sum(value) as total, min(value) as low, max(value) as high group by dept")
                .filter(rows, Totals.class).get(0);

        assertEquals(18_014_398_509_481_990L, totals.total);
        assertEquals(2L, totals.low);
        assertEquals(9_007_199_254_740_995L, totals.high);
    }

    @Test
    void sumOverflowFailsInsteadOfSaturating() {
        List<Item> rows = List.of(item("a", Long.MAX_VALUE), item("a", 1L));

        assertThrows(ArithmeticException.class, () -> PojoLensSql
                .parse("select dept, sum(value) as total group by dept")
                .filter(rows, Totals.class));
    }

    @Test
    void slidingWindowSumDoesNotCancelPrecision() {
        List<Item> rows = List.of(item(1, "a", 9_007_199_254_740_992L), item(2, "a", 1L), item(3, "a", 1L));

        List<QueryRow> out = PojoLensSql.parse("select id, "
                        + "sum(value) over (partition by dept order by id asc rows between 1 preceding and current row) as total "
                        + "order by id asc")
                .filter(rows, QueryRow.class);

        assertEquals(List.of(9_007_199_254_740_992L, 9_007_199_254_740_993L, 2L),
                out.stream().map(row -> row.getValueAt(1)).toList(),
                () -> "types: " + out.stream().map(row -> String.valueOf(row.getValueAt(1).getClass())).toList());
    }

    // --- grouping, offset, and empty aggregates ---

    @Test
    void offsetAppliesToGroupedAggregatesWithoutLimit() {
        List<Item> rows = List.of(item("A", 1L), item("A", 1L), item("B", 1L));

        List<Totals> sql = PojoLensSql.parse("select dept, count(*) as c group by dept offset 1").filter(rows, Totals.class);

        assertEquals(List.of("B"), sql.stream().map(row -> row.dept).toList());
    }

    @Test
    void groupByKeepsDistinctKeysApart() {
        List<Item> text = List.of(item("", 1L), item(null, 1L), item("<NULL>", 1L), item("x", 1L));
        List<Item> days = List.of(day(LocalDate.of(2024, 1, 1)), day(LocalDate.of(2024, 1, 2)),
                day(LocalDate.of(2024, 1, 3)), day(LocalDate.of(2024, 1, 1)));
        List<Item> stamps = List.of(stamp(1_700_000_000_100L), stamp(1_700_000_000_900L), stamp(1_700_000_000_900L));

        assertEquals(4, PojoLensSql.parse("select dept, count(*) as c group by dept").filter(text, Totals.class).size());
        assertEquals(List.of(2L, 1L, 1L), PojoLensSql.parse("select day, count(*) as c group by day")
                .filter(days, DayCount.class).stream().map(row -> row.c).toList());
        assertEquals(2, PojoLensSql.parse("select at, count(*) as c group by at").filter(stamps, DayCount.class).size());
    }

    @Test
    void globalAggregateOverEmptyInputReturnsOneRow() {
        List<Totals> sql = PojoLensSql.parse("select count(*) as c").filter(List.<Item>of(), Totals.class);

        assertEquals(1, sql.size());
        assertEquals(0L, sql.get(0).c);
    }

    // --- keyset paging ---

    @Test
    void keysetBeforeReturnsThePreviousPageInDeclaredOrder() {
        List<Item> rows = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            rows.add(item(i, "a", i));
        }
        SqlLikeCursor cursor = SqlLikeCursor.builder().put("id", 7).build();

        List<Item> page = PojoLensSql.parse("order by id asc limit 3").keysetBefore(cursor).filter(rows, Item.class);
        PageResult<Item> result = PojoLensSql.parse("order by id asc limit 3").keysetBefore(cursor).filterPage(rows, Item.class);

        assertEquals(List.of(4, 5, 6), ids(page));
        assertEquals(List.of(4, 5, 6), ids(result.rows()));
        assertTrue(result.hasMore());
        assertEquals(4, result.nextCursor().orElseThrow().values().get("id"));
    }

    // --- joins ---

    @Test
    void localDateJoinKeysMatchByValueOnBothJoinPaths() {
        List<Sale> sales = List.of(sale(10, LocalDate.of(2024, 1, 1)), sale(20, LocalDate.of(2024, 1, 2)));
        List<Holiday> holidays = List.of(holiday("NewYear", LocalDate.of(2024, 1, 1)), holiday("Xmas", LocalDate.of(2024, 12, 25)));
        JoinBindings bindings = JoinBindings.of("holidays", holidays);

        List<SaleHoliday> fast = PojoLensSql.parse("select amount, label from sales join holidays on day = holidayDay")
                .filter(sales, bindings, SaleHoliday.class);
        List<SaleHoliday> legacy = PojoLensSql.parse("select * from sales join holidays on day = holidayDay")
                .filter(sales, bindings, SaleHoliday.class);

        assertEquals(List.of("NewYear"), fast.stream().map(row -> row.label).toList());
        assertEquals(List.of("NewYear"), legacy.stream().map(row -> row.label).toList());
    }

    @Test
    void joinKeysMatchAcrossNumericTypesAndNullsNeverMatch() {
        List<Parent> parents = List.of(parent(1, "p1"), parent(2, "p2"), parent(null, "pNull"));
        List<Child> children = List.of(child(1L, "c1"), child(2L, "c2"), child(null, "cNull"));
        JoinBindings bindings = JoinBindings.of("children", children);

        List<ParentChild> fast = PojoLensSql.parse("select name, tag from parents join children on id = parentId")
                .filter(parents, bindings, ParentChild.class);
        List<ParentChild> legacy = PojoLensSql.parse("select * from parents join children on id = parentId")
                .filter(parents, bindings, ParentChild.class);

        assertEquals(List.of("p1/c1", "p2/c2"), fast.stream().map(ParentChild::pair).toList());
        assertEquals(List.of("p1/c1", "p2/c2"), legacy.stream().map(ParentChild::pair).toList());
    }

    @Test
    void innerJoinAgainstEmptyChildRowsReturnsNothing() {
        // SQL-like rejects empty join sources up front (EQ-SQL-VAL-004); the engine must still
        // treat an empty inner-join side as "no matches" rather than leaving rows unjoined.
        List<Parent> rows = FluentEngine.newQueryBuilder(List.of(parent(1, "p1")))
                .addJoinBeans("id", List.<Child>of(), "parentId", Join.INNER_JOIN)
                .initFilter()
                .join()
                .filter(Parent.class);

        assertEquals(List.of(), rows);
    }

    @Test
    void qualifiedReferencesResolveCollidingFieldsForInnerAndRightJoins() {
        List<Named> parents = List.of(named(1, 0, "P1"));
        List<Named> children = List.of(named(10, 1, "C1"), named(11, 3, "C3"));
        JoinBindings bindings = JoinBindings.of("children", children);

        assertEquals(1, PojoLensSql.parse("select * from parents join children on parents.id = children.ref "
                + "where parents.name = 'P1'").filter(parents, bindings, Named.class).size());
        assertEquals(1, PojoLensSql.parse("select * from parents right join children on parents.id = children.ref "
                + "where children.name = 'C1'").filter(parents, bindings, Named.class).size());
        assertEquals(1, PojoLensSql.parse("select * from parents right join children on parents.id = children.ref "
                + "where children.name = 'C3'").filter(parents, bindings, Named.class).size());
    }

    // --- computed fields and projection ---

    @Test
    void computedFieldWithNullDependencyIsNull() {
        List<Parent> parents = List.of(parent(1, "p1"), parent(2, "p2"));
        List<Child> children = List.of(child(1L, "c1", 5));
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("total", "id + bonus", Double.class)
                .build();

        List<ComputedOut> out = PojoLensSql.parse("select name, total from parents left join children on id = parentId")
                .computedFields(registry)
                .filter(parents, JoinBindings.of("children", children), ComputedOut.class);

        assertEquals(6.0, out.get(0).total);
        assertNull(out.get(1).total);
    }

    @Test
    void projectionFillsShortByteAndCharFields() {
        List<Item> rows = List.of(item(7, "x", 42L));

        SmallOut out = PojoLensSql.parse("select id, value, dept").filter(rows, SmallOut.class).get(0);

        assertEquals((short) 7, out.id);
        assertEquals((byte) 42, out.value);
        assertEquals('x', out.dept);
    }

    @Test
    void inheritedAndValueTypedFieldsAreQueryableAndSurviveProjection() {
        UUID uid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Account rich = account("a", new BigDecimal("150.25"), uid, List.of("vip"));
        Account poor = account("b", new BigDecimal("10.00"), UUID.randomUUID(), List.of());

        List<Account> filtered = PojoLensSql.parse("where amount > 100 and owner = 'a'").filter(List.of(rich, poor), Account.class);
        List<Account> byUid = TypedQuery.from(Account.class)
                .where(TypedField.<Account, UUID>of("uid", UUID.class).eq(uid))
                .filter(List.of(rich, poor));

        assertEquals(1, filtered.size());
        assertEquals("a", filtered.get(0).owner);
        assertEquals(new BigDecimal("150.25"), filtered.get(0).amount);
        assertEquals(uid, filtered.get(0).uid);
        assertEquals(List.of("vip"), filtered.get(0).tags);
        assertEquals(List.of("a"), byUid.stream().map(account -> account.owner).toList());
    }

    // --- fixtures ---

    private static List<Long> values(List<Item> rows) {
        return rows.stream().map(row -> row.value).toList();
    }

    private static List<Integer> ids(List<Item> rows) {
        return rows.stream().map(row -> row.id).toList();
    }

    private static Item item(long value) {
        return item(0, "a", value);
    }

    private static Item item(String dept, long value) {
        return item(0, dept, value);
    }

    private static Item item(int id, String dept, long value) {
        Item item = new Item();
        item.id = id;
        item.dept = dept;
        item.value = value;
        return item;
    }

    private static Item day(LocalDate day) {
        Item item = item(0, "a", 1L);
        item.day = day;
        return item;
    }

    private static Item stamp(long epochMillis) {
        Item item = item(0, "a", 1L);
        item.at = new Date(epochMillis);
        return item;
    }

    private static Sale sale(int amount, LocalDate day) {
        Sale sale = new Sale();
        sale.amount = amount;
        sale.day = day;
        return sale;
    }

    private static Holiday holiday(String label, LocalDate day) {
        Holiday holiday = new Holiday();
        holiday.label = label;
        holiday.holidayDay = day;
        return holiday;
    }

    private static Parent parent(Integer id, String name) {
        Parent parent = new Parent();
        parent.id = id;
        parent.name = name;
        return parent;
    }

    private static Child child(Long parentId, String tag) {
        return child(parentId, tag, null);
    }

    private static Child child(Long parentId, String tag, Integer bonus) {
        Child child = new Child();
        child.parentId = parentId;
        child.tag = tag;
        child.bonus = bonus;
        return child;
    }

    private static Named named(int id, int ref, String name) {
        Named named = new Named();
        named.id = id;
        named.ref = ref;
        named.name = name;
        return named;
    }

    private static Account account(String owner, BigDecimal amount, UUID uid, List<String> tags) {
        Account account = new Account();
        account.owner = owner;
        account.amount = amount;
        account.uid = uid;
        account.tags = tags;
        return account;
    }

    public static class Item {
        public int id;
        public String dept;
        public long value;
        public LocalDate day;
        public Date at;

        Item() {
        }
    }

    public static class Totals {
        public String dept;
        public Long total;
        public Long low;
        public Long high;
        public Long c;

        Totals() {
        }
    }

    public static class DayCount {
        public LocalDate day;
        public Date at;
        public Long c;

        DayCount() {
        }
    }

    public static class WindowOut {
        public int id;
        public Long total;

        WindowOut() {
        }
    }

    public static class Sale {
        public int amount;
        public LocalDate day;

        Sale() {
        }
    }

    public static class Holiday {
        public String label;
        public LocalDate holidayDay;

        Holiday() {
        }
    }

    public static class SaleHoliday {
        public int amount;
        public String label;

        SaleHoliday() {
        }
    }

    public static class Parent {
        public Integer id;
        public String name;

        Parent() {
        }
    }

    public static class Child {
        public Long parentId;
        public String tag;
        public Integer bonus;

        Child() {
        }
    }

    public static class ParentChild {
        public String name;
        public String tag;

        ParentChild() {
        }

        String pair() {
            return name + "/" + tag;
        }
    }

    public static class Named {
        public int id;
        public int ref;
        public String name;

        Named() {
        }
    }

    public static class ComputedOut {
        public String name;
        public Double total;

        ComputedOut() {
        }
    }

    public static class SmallOut {
        public short id;
        public byte value;
        public char dept;

        SmallOut() {
        }
    }

    public static class BaseAccount {
        public String owner;

        BaseAccount() {
        }
    }

    public static class Account extends BaseAccount {
        public BigDecimal amount;
        public UUID uid;
        public List<String> tags;

        Account() {
        }
    }
}
