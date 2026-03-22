package laughing.man.commits;

import laughing.man.commits.enums.Join;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PojoLensJoinBehaviorTest {

    @Test
    public void joinOperatorsShouldAllBeExercised() throws Exception {
        List<PojoLensBehaviorFixtures.ParentBean> parents = Collections.singletonList(
                new PojoLensBehaviorFixtures.ParentBean(1, "p1")
        );
        List<PojoLensBehaviorFixtures.ChildBean> children = Arrays.asList(
                new PojoLensBehaviorFixtures.ChildBean(1, "c1"),
                new PojoLensBehaviorFixtures.ChildBean(99, "c99")
        );

        List<PojoLensBehaviorFixtures.ParentBean> inner = PojoLens.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.INNER_JOIN)
                .initFilter()
                .join()
                .filter(PojoLensBehaviorFixtures.ParentBean.class);
        assertEquals(1, inner.size());

        List<PojoLensBehaviorFixtures.ParentBean> left = PojoLens.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .initFilter()
                .join()
                .filter(PojoLensBehaviorFixtures.ParentBean.class);
        assertEquals(1, left.size());

        List<PojoLensBehaviorFixtures.ChildBean> right = PojoLens.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.RIGHT_JOIN)
                .initFilter()
                .join()
                .filter(PojoLensBehaviorFixtures.ChildBean.class);
        assertEquals(2, right.size());
        List<Integer> parentIds = right.stream()
                .map(r -> r.parentId)
                .toList();
        assertTrue(parentIds.contains(1));
        assertTrue(parentIds.contains(99));
    }

    @Test
    public void joinShouldRespectInnerAndLeftSemantics() throws Exception {
        List<PojoLensBehaviorFixtures.ParentBean> parents = Arrays.asList(
                new PojoLensBehaviorFixtures.ParentBean(1, "p1"),
                new PojoLensBehaviorFixtures.ParentBean(2, "p2")
        );
        List<PojoLensBehaviorFixtures.ChildBean> children = Collections.singletonList(
                new PojoLensBehaviorFixtures.ChildBean(1, "c1")
        );
        List<PojoLensBehaviorFixtures.ParentBean> inner = PojoLens.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.INNER_JOIN)
                .initFilter()
                .join()
                .filter(PojoLensBehaviorFixtures.ParentBean.class);
        assertEquals(1, inner.size());

        List<PojoLensBehaviorFixtures.ParentBean> left = PojoLens.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .initFilter()
                .join()
                .filter(PojoLensBehaviorFixtures.ParentBean.class);
        assertEquals(2, left.size());
    }

    @Test
    public void joinFieldNameCollisionsShouldUseChildPrefix() throws Exception {
        List<PojoLensBehaviorFixtures.ParentBean> parents = Collections.singletonList(
                new PojoLensBehaviorFixtures.ParentBean(1, "parent-name")
        );
        List<PojoLensBehaviorFixtures.ChildCollisionBean> children = Collections.singletonList(
                new PojoLensBehaviorFixtures.ChildCollisionBean(1, "child-name")
        );

        List<PojoLensBehaviorFixtures.JoinedCollisionProjection> rows = PojoLens.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .initFilter()
                .join()
                .filter(PojoLensBehaviorFixtures.JoinedCollisionProjection.class);

        assertEquals(1, rows.size());
        assertEquals("parent-name", rows.get(0).name);
        assertEquals("child-name", rows.get(0).child_name);
    }

    @Test
    public void joinFieldNameCollisionsShouldUseDeterministicSuffixes() throws Exception {
        List<PojoLensBehaviorFixtures.ParentCollisionBean> parents = Collections.singletonList(
                new PojoLensBehaviorFixtures.ParentCollisionBean(1, "parent-name", "already-present")
        );
        List<PojoLensBehaviorFixtures.ChildCollisionBean> children = Collections.singletonList(
                new PojoLensBehaviorFixtures.ChildCollisionBean(1, "child-name")
        );

        List<PojoLensBehaviorFixtures.JoinedCollisionProjectionWithSuffix> rows = PojoLens.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .initFilter()
                .join()
                .filter(PojoLensBehaviorFixtures.JoinedCollisionProjectionWithSuffix.class);

        assertEquals(1, rows.size());
        assertEquals("parent-name", rows.get(0).name);
        assertEquals("already-present", rows.get(0).child_name);
        assertEquals("child-name", rows.get(0).child_name_1);
    }

    @Test
    public void joinFieldNameCollisionsShouldScaleToLargerSuffixSets() throws Exception {
        List<PojoLensBehaviorFixtures.ParentMultiCollisionBean> parents = Collections.singletonList(
                new PojoLensBehaviorFixtures.ParentMultiCollisionBean(
                        1, "parent-name", "already-present-0", "already-present-1"
                )
        );
        List<PojoLensBehaviorFixtures.ChildCollisionBean> children = Collections.singletonList(
                new PojoLensBehaviorFixtures.ChildCollisionBean(1, "child-name")
        );

        List<PojoLensBehaviorFixtures.JoinedCollisionProjectionWithDeepSuffix> rows = PojoLens.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .initFilter()
                .join()
                .filter(PojoLensBehaviorFixtures.JoinedCollisionProjectionWithDeepSuffix.class);

        assertEquals(1, rows.size());
        assertEquals("parent-name", rows.get(0).name);
        assertEquals("already-present-0", rows.get(0).child_name);
        assertEquals("already-present-1", rows.get(0).child_name_1);
        assertEquals("child-name", rows.get(0).child_name_2);
    }

    @Test
    public void rightJoinShouldHandleCollisionsAndProjectionMapping() throws Exception {
        List<PojoLensBehaviorFixtures.ParentCollisionBean> parents = Collections.singletonList(
                new PojoLensBehaviorFixtures.ParentCollisionBean(1, "parent-name-1", "parent-shadow")
        );
        List<PojoLensBehaviorFixtures.ChildCollisionBean> children = Arrays.asList(
                new PojoLensBehaviorFixtures.ChildCollisionBean(1, "child-name-1"),
                new PojoLensBehaviorFixtures.ChildCollisionBean(2, "child-name-2")
        );

        List<PojoLensBehaviorFixtures.RightJoinCollisionProjection> rows = PojoLens.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.RIGHT_JOIN)
                .initFilter()
                .join()
                .filter(PojoLensBehaviorFixtures.RightJoinCollisionProjection.class);

        assertEquals(2, rows.size());
        Map<Integer, PojoLensBehaviorFixtures.RightJoinCollisionProjection> byParentId = rows.stream()
                .collect(Collectors.toMap(r -> r.parentId, r -> r));

        PojoLensBehaviorFixtures.RightJoinCollisionProjection matched = byParentId.get(1);
        assertEquals("child-name-1", matched.name);
        assertEquals(1, matched.id);
        assertEquals("parent-name-1", matched.child_name);
        assertEquals("parent-shadow", matched.child_child_name);

        PojoLensBehaviorFixtures.RightJoinCollisionProjection unmatched = byParentId.get(2);
        assertEquals("child-name-2", unmatched.name);
        assertEquals(0, unmatched.id);
        assertNull(unmatched.child_name);
        assertNull(unmatched.child_child_name);
    }
}

