package laughing.man.commits.filter;

import laughing.man.commits.PojoLensCore;

import laughing.man.commits.PojoLens;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FilterImplFastPathTest {

    @Test
    void selectiveComputedSingleJoinShouldActivateFastArrayState() throws Exception {
        Filter filter = PojoLensCore.newQueryBuilder(List.of(
                new Parent(1, "a", 100),
                new Parent(2, "b", 120)
        ))
                .computedFields(ComputedFieldRegistry.builder()
                        .add("totalComp", "salary + bonus", Double.class)
                        .build())
                .addJoinBeans("id", List.of(
                        new Child(1, 20),
                        new Child(2, 15)
                ), "parentId", Join.LEFT_JOIN)
                .addRule("totalComp", 135.0, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("name")
                .addField("totalComp")
                .initFilter();

        filter.join();

        Field fastArrayState = FilterImpl.class.getDeclaredField("fastArrayState");
        fastArrayState.setAccessible(true);
        assertNotNull(fastArrayState.get(filter));
    }

    @Test
    void benchmarkShapeShouldActivateFastArrayState() throws Exception {
        ArrayList<Parent> parents = new ArrayList<>(250);
        ArrayList<Child> children = new ArrayList<>(250);
        for (int i = 0; i < 250; i++) {
            parents.add(new Parent(i, "parent-" + i, 90_000 + (i % 120)));
            children.add(new Child(i, 3_000 + (i % 4_000)));
        }

        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("totalComp", "salary + bonus", Double.class)
                .build();

        Filter filter = PojoLensCore.newQueryBuilder(parents)
                .computedFields(registry)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .addRule("totalComp", 93_000.0, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("name")
                .addField("totalComp")
                .initFilter();

        filter.join();

        Field fastArrayState = FilterImpl.class.getDeclaredField("fastArrayState");
        fastArrayState.setAccessible(true);
        assertNotNull(fastArrayState.get(filter));
    }

    @Test
    void fastArrayPathShouldRespectOrderAndLimit() throws Exception {
        ArrayList<Parent> parents = new ArrayList<>(400);
        ArrayList<Child> children = new ArrayList<>(400);
        for (int i = 0; i < 400; i++) {
            parents.add(new Parent(i, "p" + i, i % 80));
            children.add(new Child(i, 0));
        }

        Filter filter = PojoLensCore.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .addOrder("salary", 1)
                .limit(20)
                .addField("name")
                .addField("salary")
                .initFilter();

        filter.join();

        Field fastArrayState = FilterImpl.class.getDeclaredField("fastArrayState");
        fastArrayState.setAccessible(true);
        assertNotNull(fastArrayState.get(filter));

        List<JoinOrderRow> actual = filter.filter(Sort.ASC, JoinOrderRow.class);

        ArrayList<Parent> expectedSorted = new ArrayList<>(parents);
        expectedSorted.sort(Comparator.comparingInt(p -> p.salary));

        assertEquals(20, actual.size());
        for (int i = 0; i < 20; i++) {
            Parent expected = expectedSorted.get(i);
            JoinOrderRow row = actual.get(i);
            assertEquals(expected.name, row.name);
            assertEquals(expected.salary, row.salary);
        }
    }

    @Test
    void fastArrayDenseIndexShouldPreserveOneToManyJoinRows() throws Exception {
        List<Parent> parents = List.of(new Parent(1, "p1", 100));
        List<ChildWithTag> children = List.of(
                new ChildWithTag(1, 5, "a"),
                new ChildWithTag(1, 7, "b")
        );

        Filter filter = PojoLensCore.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .addField("name")
                .addField("tag")
                .initFilter();
        filter.join();

        List<JoinTagRow> rows = filter.filter(JoinTagRow.class);
        rows.sort(Comparator.comparing(r -> r.tag));

        assertEquals(2, rows.size());
        assertEquals("p1", rows.get(0).name);
        assertEquals("a", rows.get(0).tag);
        assertEquals("p1", rows.get(1).name);
        assertEquals("b", rows.get(1).tag);
    }

    static final class Parent {
        int id;
        String name;
        int salary;

        Parent(int id, String name, int salary) {
            this.id = id;
            this.name = name;
            this.salary = salary;
        }
    }

    static final class Child {
        int parentId;
        int bonus;

        Child(int parentId, int bonus) {
            this.parentId = parentId;
            this.bonus = bonus;
        }
    }

    static final class ChildWithTag {
        int parentId;
        int bonus;
        String tag;

        ChildWithTag(int parentId, int bonus, String tag) {
            this.parentId = parentId;
            this.bonus = bonus;
            this.tag = tag;
        }
    }

    public static final class JoinOrderRow {
        public String name;
        public int salary;
    }

    public static final class JoinTagRow {
        public String name;
        public String tag;
    }
}
