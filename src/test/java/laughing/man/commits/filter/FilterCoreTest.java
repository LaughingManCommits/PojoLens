package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.Foo;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilterCoreTest {

    @Test
    public void cleanMethodsShouldRemoveUnknownRuleFields() {
        FilterCore core = new FilterCore(builder(sampleFoos()));
        QueryRow first = core.getBuilder().getRows().get(0);

        HashMap<Integer, String> rules = new HashMap<>();
        rules.put(1, "stringField");
        rules.put(2, "missingField");
        Map<Integer, String> cleaned = core.clean(first.getFields(), rules, "order");

        assertEquals(1, cleaned.size());
        assertTrue(cleaned.containsValue("stringField"));

        core.getBuilder().addRule("missingField", "x", Clauses.EQUAL, Separator.AND);
        core.clean(first);

        assertFalse(core.getBuilder().getFilterFields().containsValue("missingField"));
    }

    @Test
    public void orderByFieldsOverloadsShouldSort() {
        FilterCore core = new FilterCore(builder(sampleFoos()));
        core.getBuilder().addOrder("integerField", 1);

        List<QueryRow> source = core.getBuilder().getRows();
        List<QueryRow> sortedWithInternalPlan = core.orderByFields(source, Sort.ASC);
        List<QueryRow> sortedWithExplicitPlan = core.orderByFields(source, Sort.ASC, core.buildExecutionPlan());

        int first = intField(sortedWithInternalPlan.get(0), "integerField");
        int second = intField(sortedWithExplicitPlan.get(0), "integerField");

        assertEquals(1, first);
        assertEquals(1, second);
    }

    @Test
    public void joinShouldWorkWithBeanInput() {
        List<Parent> parents = Arrays.asList(new Parent(1, "p1"), new Parent(2, "p2"));
        List<Child> children = List.of(new Child(1, "c1"));

        FilterQueryBuilder builder = builder(new ArrayList<Object>(parents));
        builder.addJoinBeans("id", children, "parentId", Join.LEFT_JOIN);
        FilterCore core = new FilterCore(builder);

        List<QueryRow> joined = core.join(parents);
        assertEquals(2, joined.size());
    }

    @Test
    public void groupByFieldsOverloadsShouldGroup() {
        FilterCore core = new FilterCore(builder(sampleFoos()));
        core.getBuilder().addGroup("stringField", 1);

        List<QueryRow> source = core.getBuilder().getRows();
        Map<String, List<QueryRow>> groupedA = core.groupByFields(source, source);
        Map<String, List<QueryRow>> groupedB = core.groupByFields(source, source, core.buildExecutionPlan());

        assertFalse(groupedA.isEmpty());
        assertFalse(groupedB.isEmpty());
        assertEquals(2, groupedA.size());
    }

    @Test
    public void filterFieldsOverloadsShouldApplyRules() {
        FilterCore core = new FilterCore(builder(sampleFoos()));
        core.getBuilder().addRule("stringField", "a", Clauses.EQUAL, Separator.AND);

        List<QueryRow> source = core.getBuilder().getRows();
        List<QueryRow> filteredA = core.filterFields(source);
        List<QueryRow> filteredB = core.filterFields(source, core.buildExecutionPlan());

        assertEquals(2, filteredA.size());
        assertEquals(2, filteredB.size());
    }

    @Test
    public void filterDisplayFieldsOverloadsShouldProjectSelectedFields() {
        FilterCore core = new FilterCore(builder(sampleFoos()));
        core.getBuilder().addField("stringField");

        List<QueryRow> source = core.getBuilder().getRows();
        List<QueryRow> projectedA = core.filterDisplayFields(source);
        List<QueryRow> projectedB = core.filterDisplayFields(source, core.buildExecutionPlan());

        assertEquals(1, projectedA.get(0).getFields().size());
        assertEquals(1, projectedB.get(0).getFields().size());
    }

    @Test
    public void filterDistinctFieldsOverloadsShouldDeduplicate() {
        FilterCore core = new FilterCore(builder(sampleFoos()));
        core.getBuilder().addDistinct("stringField", 1);

        List<QueryRow> distinctA = core.filterDistinctFields();
        List<QueryRow> distinctB = core.filterDistinctFields(core.buildExecutionPlan());

        assertEquals(2, distinctA.size());
        assertEquals(2, distinctB.size());
    }

    @Test
    public void aggregateMetricsShouldBuildSingleProjectionRow() {
        FilterCore core = new FilterCore(builder(sampleFoos()));
        core.getBuilder().addCount("rowCount");
        core.getBuilder().addMetric("integerField", Metric.SUM, "sumInt");

        FilterExecutionPlan plan = core.buildExecutionPlan();
        List<QueryRow> filtered = core.filterFields(core.getBuilder().getRows(), plan);
        List<QueryRow> aggregated = core.aggregateMetrics(filtered, plan);

        assertEquals(1, aggregated.size());
        assertEquals(2, aggregated.get(0).getFields().size());
        assertEquals("rowCount", aggregated.get(0).getFields().get(0).getFieldName());
        assertEquals("sumInt", aggregated.get(0).getFields().get(1).getFieldName());
    }

    @Test
    public void buildExecutionPlanShouldCompileResolvedExecutionMetadata() {
        FilterCore core = new FilterCore(builder(sampleFoos()));
        core.getBuilder().addDistinct("stringField", 1);
        core.getBuilder().addOrder("integerField", 1);
        core.getBuilder().addGroup("stringField", 1);
        core.getBuilder().addField("stringField");
        core.getBuilder().addCount("rowCount");
        core.getBuilder().addMetric("integerField", Metric.SUM, "sumInt");
        core.getBuilder().addHaving("rowCount", 2, Clauses.BIGGER_EQUAL, Separator.AND);

        FilterExecutionPlan sourcePlan = core.buildExecutionPlan();

        assertEquals(List.of(0), sourcePlan.getDistinctFieldIndexes());
        assertEquals(List.of(0), sourcePlan.getReturnFieldIndexes());
        assertEquals(1, sourcePlan.getOrderColumns().size());
        assertEquals(2, sourcePlan.getOrderColumns().get(0).fieldIndex());
        assertEquals(1, sourcePlan.getGroupColumns().size());
        assertEquals("stringField", sourcePlan.getGroupColumns().get(0).fieldName());
        assertEquals(2, sourcePlan.getMetricPlans().size());
        assertEquals(-1, sourcePlan.getMetricPlans().get(0).fieldIndex());
        assertEquals(2, sourcePlan.getMetricPlans().get(1).fieldIndex());

        List<QueryRow> aggregated = core.aggregateMetrics(core.getBuilder().getRows(), sourcePlan);
        FilterQueryBuilder havingBuilder = core.getBuilder().snapshotForRows(aggregated);
        FilterExecutionPlan havingPlan = new FilterCore(havingBuilder).buildExecutionPlan();

        assertEquals(1, havingPlan.getHavingRulesByFieldIndex().size());
        assertTrue(havingPlan.getHavingRulesByFieldIndex().containsKey(havingPlan.findFieldIndex("rowCount")));
    }

    @Test
    public void gettersAndPlanBuildShouldReturnExpectedObjects() {
        FilterQueryBuilder builder = builder(sampleFoos());
        FilterCore core = new FilterCore(builder);

        assertSame(builder, core.getBuilder());

        FilterExecutionPlan plan = core.buildExecutionPlan();

        assertNotNull(plan);
        assertTrue(plan.findFieldIndex("stringField") >= 0);
    }

    private static FilterQueryBuilder builder(List<?> beans) {
        return new FilterQueryBuilder(beans);
    }

    private static List<Foo> sampleFoos() {
        Date now = new Date();
        return Arrays.asList(
                new Foo("a", now, 2),
                new Foo("a", now, 1),
                new Foo("b", now, 3)
        );
    }

    private static int intField(QueryRow row, String fieldName) {
        Object value = fieldValue(row, fieldName);
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static Object fieldValue(QueryRow row, String fieldName) {
        for (QueryField field : row.getFields()) {
            if (fieldName.equals(field.getFieldName())) {
                return field.getValue();
            }
        }
        return null;
    }

    public static class Parent {
        int id;
        String name;

        public Parent() {
        }

        public Parent(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class Child {
        int parentId;
        String tag;

        public Child() {
        }

        public Child(int parentId, String tag) {
            this.parentId = parentId;
            this.tag = tag;
        }
    }
}

