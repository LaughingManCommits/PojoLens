package laughing.man.commits.filter;

import laughing.man.commits.PojoLens;
import laughing.man.commits.benchmark.PojoLensJoinJmhBenchmark;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Separator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

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
}
