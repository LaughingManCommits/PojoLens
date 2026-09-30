package laughing.man.commits.dsl;

import laughing.man.commits.PojoLensSql;
import laughing.man.commits.enums.Join;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.QueryExecutionGuard;
import laughing.man.commits.sqllike.QueryExecutionGuardException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-33: lazy typed {@code stream()}/{@code iterator()}. Laziness is observed through
 * the number of projection instances the engine creates.
 */
class TypedLazyStreamTest {

    private static final TypedField<Item, Integer> ID = TypedField.of("id", Integer.class);
    private static final TypedField<Item, String> CATEGORY = TypedField.of("category", String.class);
    private static final TypedField<Item, Integer> PRICE = TypedField.of("price", Integer.class);
    private static final TypedField<Item, String> NAME = TypedField.of("name", String.class);

    private static final int ROWS = 1_000;

    @BeforeEach
    void resetCounter() {
        ItemView.created = 0;
    }

    @Test
    void simpleFilterStreamsProjectOnlyTheConsumedRows() {
        List<Item> items = items();

        List<ItemView> firstThree = TypedQuery.from(Item.class)
                .where(CATEGORY.eq("a").or(PRICE.gte(900)).and(NAME.isNotNull()))
                .stream(items, JoinBindings.empty(), ItemView.class)
                .limit(3)
                .toList();

        assertEquals(List.of(0, 3, 6), firstThree.stream().map(row -> row.id).toList());
        assertEquals(3, ItemView.created);
    }

    @Test
    void iteratorPullsRowsOnDemand() {
        Iterator<ItemView> iterator = TypedQuery.from(Item.class)
                .where(PRICE.between(100, 199))
                .iterator(items(), JoinBindings.empty(), ItemView.class);

        assertEquals(100, iterator.next().id);
        assertEquals(101, iterator.next().id);
        assertEquals(2, ItemView.created);
    }

    @Test
    void lazyStreamsMatchFilterResultsForEveryPredicateShape() {
        List<Item> items = items();
        List<TypedQuery<Item>> queries = List.of(
                TypedQuery.from(Item.class).where(CATEGORY.eq("b")),
                TypedQuery.from(Item.class).where(CATEGORY.eq("b").or(PRICE.lt(10))),
                TypedQuery.from(Item.class).where(CATEGORY.in(List.of("a", "c")).not()),
                TypedQuery.from(Item.class).where(NAME.startsWith("item-9").and(PRICE.between(900, 950))),
                TypedQuery.from(Item.class).where(NAME.containsIgnoreCase("ITEM-12").or(NAME.isNull())),
                TypedQuery.from(Item.class).where(TypedPredicate.none()),
                TypedQuery.from(Item.class).where(TypedPredicate.any()).offset(5).limit(4),
                TypedQuery.from(Item.class).select(ID, PRICE).where(PRICE.gt(990)),
                TypedQuery.from(Item.class).where(CATEGORY.eq("c")).offset(10).limit(3));

        for (TypedQuery<Item> query : queries) {
            List<Integer> expected = query.filter(items).stream().map(item -> item.id).toList();
            List<Integer> streamed = query.stream(items).map(item -> item.id).toList();
            assertEquals(expected, streamed);
        }
    }

    @Test
    void subqueryPredicatesResolveUpFrontAndStillStreamLazily() {
        List<Item> items = items();
        TypedQuery<Item> query = TypedQuery.from(Item.class)
                .where(ID.inSubquery(ID, TypedQuery.from(Item.class).where(PRICE.gte(990))));

        List<ItemView> first = query.stream(items, JoinBindings.empty(), ItemView.class).limit(2).toList();

        assertEquals(List.of(990, 991), first.stream().map(row -> row.id).toList());
        assertEquals(2, ItemView.created);
        assertEquals(query.filter(items).stream().map(item -> item.id).toList(),
                query.stream(items).map(item -> item.id).toList());
    }

    @Test
    void orderedGroupedAndJoinedStreamsStillMaterialiseCorrectly() {
        List<Item> items = items();

        List<Integer> ordered = TypedQuery.from(Item.class)
                .where(CATEGORY.eq("a"))
                .orderByDesc(PRICE)
                .limit(3)
                .stream(items)
                .map(item -> item.id)
                .toList();
        long joined = TypedQuery.from(Item.class)
                .join("tags", ID, TypedField.<Tag, Integer>of("itemId", Integer.class), Join.INNER_JOIN)
                .stream(items, JoinBindings.of("tags", List.of(new Tag(1, "x"), new Tag(2, "y"))))
                .count();

        assertEquals(List.of(999, 996, 993), ordered);
        assertEquals(2, joined);
    }

    @Test
    void guardedStreamsKeepEveryGuardCheck() {
        TypedQuery<Item> guarded = TypedQuery.from(Item.class)
                .where(CATEGORY.eq("a"))
                .executionGuard(QueryExecutionGuard.builder().maxRowsReturned(5).build());

        assertThrows(QueryExecutionGuardException.class, () -> guarded.stream(items()).findFirst());
    }

    @Test
    void sqlLikeOrPredicatesStreamLazilyToo() {
        List<Item> items = items();

        List<ItemView> rows = PojoLensSql.parse("where category = 'a' or price >= 900")
                .stream(items, ItemView.class)
                .limit(2)
                .collect(Collectors.toList());

        assertEquals(List.of(0, 3), rows.stream().map(row -> row.id).toList());
        assertEquals(2, ItemView.created);
        assertTrue(PojoLensSql.parse("where not (category = 'a' or price >= 900)")
                .stream(items, ItemView.class)
                .allMatch(row -> row.id % 3 != 0 && row.id < 900));
    }

    private static List<Item> items() {
        ArrayList<Item> items = new ArrayList<>(ROWS);
        IntStream.range(0, ROWS).forEach(i -> items.add(new Item(i, "abc".substring(i % 3, i % 3 + 1), i,
                i % 250 == 7 ? null : "item-" + i)));
        return items;
    }

    public static class Item {
        public int id;
        public String category;
        public int price;
        public String name;

        Item() {
        }

        Item(int id, String category, int price, String name) {
            this.id = id;
            this.category = category;
            this.price = price;
            this.name = name;
        }
    }

    public static class ItemView {
        static int created;

        public int id;
        public int price;

        ItemView() {
            created++;
        }
    }

    public static class Tag {
        public int itemId;
        public String label;

        Tag() {
        }

        Tag(int itemId, String label) {
            this.itemId = itemId;
            this.label = label;
        }
    }
}
