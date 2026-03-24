package laughing.man.commits;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.PojoLensBehaviorFixtures.ChildBean;
import laughing.man.commits.PojoLensBehaviorFixtures.ParentBean;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.parser.SqlLikeParser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SqlLikeJoinTest {

    @Test
    public void parserShouldParseJoinClause() {
        QueryAst ast = SqlLikeParser
                .parse("select * from parents left join children on parents.id = children.parentId where tag = null");
        assertEquals(1, ast.joins().size());
        assertEquals(Join.LEFT_JOIN, ast.join().joinType());
        assertEquals("children", ast.join().childSource());
        assertEquals("parents.id", ast.join().parentField());
        assertEquals("parentId", ast.join().childField());
    }

    @Test
    public void sqlLikeLeftJoinShouldMatchFluentJoinPipeline() {
        List<ParentBean> parents = Arrays.asList(
                new ParentBean(1, "p1"),
                new ParentBean(2, "p2")
        );
        List<ChildBean> children = Collections.singletonList(
                new ChildBean(1, "c1")
        );

        List<ParentBean> fluent = PojoLens.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .addRule("tag", null, Clauses.EQUAL, Separator.AND)
                .initFilter()
                .join()
                .filter(ParentBean.class);

        Map<String, List<?>> joinSources = new HashMap<>();
        joinSources.put("children", children);
        List<ParentBean> sqlLike = PojoLens
                .parse("select * from parents left join children on id = parentId where tag = null")
                .filter(parents, joinSources, ParentBean.class);

        assertEquals(ids(fluent), ids(sqlLike));
    }

    @Test
    public void missingJoinBindingShouldFail() {
        List<ParentBean> parents = Arrays.asList(
                new ParentBean(1, "p1"),
                new ParentBean(2, "p2")
        );
        try {
            PojoLens.parse("select * from parents left join children on id = parentId")
                    .filter(parents, ParentBean.class);
            fail("Expected missing join binding failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Missing JOIN source binding for 'children'"));
        }
    }

    @Test
    public void sqlLikeShouldSupportChainedJoinExecution() {
        List<ParentBean> parents = Arrays.asList(
                new ParentBean(1, "p1"),
                new ParentBean(2, "p2")
        );
        List<ChildWithIdBean> children = Arrays.asList(
                new ChildWithIdBean(10, 1, "child-1"),
                new ChildWithIdBean(20, 2, "child-2")
        );
        List<ToyBean> toys = Arrays.asList(
                new ToyBean(10, "truck"),
                new ToyBean(20, "puzzle")
        );

        Map<String, List<?>> joinSources = new HashMap<>();
        joinSources.put("children", children);
        joinSources.put("toys", toys);

        List<ParentBean> rows = PojoLens
                .parse("select * from parents "
                        + "left join children on parents.id = children.parentId "
                        + "left join toys on children.id = toys.childId "
                        + "where label = 'truck'")
                .filter(parents, joinSources, ParentBean.class);

        assertEquals(List.of(1), ids(rows));
    }

    @Test
    public void repeatedJoinExecutionsShouldRebindCurrentJoinSources() {
        List<ParentBean> parents = Arrays.asList(
                new ParentBean(1, "p1"),
                new ParentBean(2, "p2")
        );
        SqlLikeQuery query = PojoLens.parse("select * from parents left join children on id = parentId where tag = 'match'");

        List<ParentBean> first = query.filter(
                parents,
                Map.of("children", List.of(new ChildBean(1, "match"))),
                ParentBean.class
        );
        List<ParentBean> second = query.filter(
                parents,
                Map.of("children", List.of(new ChildBean(2, "match"))),
                ParentBean.class
        );

        assertEquals(List.of(1), ids(first));
        assertEquals(List.of(2), ids(second));
    }

    @Test
    public void multiJoinShouldRequireJoinSourceOrderingThatMatchesExistingPlan() {
        List<ParentBean> parents = Arrays.asList(
                new ParentBean(1, "p1"),
                new ParentBean(2, "p2")
        );
        List<ChildWithIdBean> children = Arrays.asList(
                new ChildWithIdBean(10, 1, "child-1")
        );
        List<ToyBean> toys = Arrays.asList(
                new ToyBean(10, "truck")
        );

        Map<String, List<?>> joinSources = new HashMap<>();
        joinSources.put("children", children);
        joinSources.put("toys", toys);

        try {
            PojoLens.parse("select * from parents "
                            + "left join toys on children.id = toys.childId "
                            + "left join children on parents.id = children.parentId")
                    .filter(parents, joinSources, ParentBean.class);
            fail("Expected JOIN ordering validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown field 'children.id'"));
        }
    }

    private static List<Integer> ids(List<ParentBean> rows) {
        return rows.stream().map(r -> r.id).collect(Collectors.toList());
    }

    public static class ChildWithIdBean {
        int id;
        int parentId;
        String tag;

        public ChildWithIdBean() {
        }

        public ChildWithIdBean(int id, int parentId, String tag) {
            this.id = id;
            this.parentId = parentId;
            this.tag = tag;
        }
    }

    public static class ToyBean {
        int childId;
        String label;

        public ToyBean() {
        }

        public ToyBean(int childId, String label) {
            this.childId = childId;
            this.label = label;
        }
    }
}

