package laughing.man.commits.filter;

import laughing.man.commits.PojoLens;
import laughing.man.commits.benchmark.PojoLensJoinJmhBenchmark;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FilterImplFastPathTest {

    @Test
    void selectiveComputedSingleJoinShouldActivateFastArrayState() throws Exception {
        Filter filter = PojoLens.newQueryBuilder(List.of(
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
        PojoLensJoinJmhBenchmark benchmark = new PojoLensJoinJmhBenchmark();
        benchmark.size = 10;
        benchmark.setup();

        Field computedParents = PojoLensJoinJmhBenchmark.class.getDeclaredField("computedParents");
        computedParents.setAccessible(true);
        Field computedChildren = PojoLensJoinJmhBenchmark.class.getDeclaredField("computedChildren");
        computedChildren.setAccessible(true);
        Field computedFieldRegistry = PojoLensJoinJmhBenchmark.class.getDeclaredField("computedFieldRegistry");
        computedFieldRegistry.setAccessible(true);
        Field minimumTotalComp = PojoLensJoinJmhBenchmark.class.getDeclaredField("minimumTotalComp");
        minimumTotalComp.setAccessible(true);

        Filter filter = PojoLens.newQueryBuilder((List<?>) computedParents.get(benchmark))
                .computedFields((ComputedFieldRegistry) computedFieldRegistry.get(benchmark))
                .addJoinBeans("id", (List<?>) computedChildren.get(benchmark), "parentId", Join.LEFT_JOIN)
                .addRule("totalComp", minimumTotalComp.getDouble(benchmark), Clauses.BIGGER_EQUAL, Separator.AND)
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

        Filter filter = PojoLens.newQueryBuilder(parents)
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

    public static final class JoinOrderRow {
        public String name;
        public int salary;

        public JoinOrderRow() {
        }
    }
}
