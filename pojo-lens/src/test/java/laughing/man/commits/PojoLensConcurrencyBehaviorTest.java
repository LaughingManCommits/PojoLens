package laughing.man.commits;

import laughing.man.commits.domain.Foo;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Separator;
import org.junit.jupiter.api.Test;
import laughing.man.commits.builder.QueryBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PojoLensConcurrencyBehaviorTest {

    @Test
    public void concurrentFilterExecutionShouldBeStableWithCopyOnBuild() throws Exception {
        Date now = new Date();
        List<Foo> source = Arrays.asList(
                new Foo("a", now, 1),
                new Foo("a", now, 2),
                new Foo("b", now, 3)
        );

        QueryBuilder template = PojoLensCore.newQueryBuilder(source)
                .addRule("stringField", "a", Clauses.EQUAL, Separator.OR)
                .copyOnBuild(true);

        List<Integer> sizes = PojoLensBehaviorFixtures.runConcurrent(
                40, 8, () -> template.initFilter().filter(Foo.class).size()
        );
        for (Integer size : sizes) {
            assertEquals(Integer.valueOf(2), size);
        }
    }

    @Test
    public void concurrentFilterGroupsExecutionShouldBeStableWithCopyOnBuild() throws Exception {
        Date now = new Date();
        List<Foo> source = Arrays.asList(
                new Foo("a", now, 1),
                new Foo("a", now, 2),
                new Foo("b", now, 1)
        );

        QueryBuilder template = PojoLensCore.newQueryBuilder(source)
                .addGroup("stringField", 1)
                .copyOnBuild(true);

        List<Integer> sizes = PojoLensBehaviorFixtures.runConcurrent(
                40, 8, () -> template.initFilter().filterGroups(Foo.class).size()
        );
        for (Integer size : sizes) {
            assertEquals(Integer.valueOf(2), size);
        }
    }

    @Test
    public void concurrentJoinExecutionShouldBeStableWithCopyOnBuild() throws Exception {
        List<PojoLensBehaviorFixtures.ParentBean> parents = Arrays.asList(
                new PojoLensBehaviorFixtures.ParentBean(1, "p1"),
                new PojoLensBehaviorFixtures.ParentBean(2, "p2")
        );
        List<PojoLensBehaviorFixtures.ChildBean> children = Collections.singletonList(
                new PojoLensBehaviorFixtures.ChildBean(1, "c1")
        );

        QueryBuilder template = PojoLensCore.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .copyOnBuild(true);

        List<Integer> sizes = PojoLensBehaviorFixtures.runConcurrent(
                40, 8, () -> template.initFilter().join().filter(PojoLensBehaviorFixtures.ParentBean.class).size()
        );
        for (Integer size : sizes) {
            assertEquals(Integer.valueOf(2), size);
        }
    }
}

