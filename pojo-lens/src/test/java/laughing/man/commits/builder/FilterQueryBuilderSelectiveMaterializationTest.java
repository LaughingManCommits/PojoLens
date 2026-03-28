package laughing.man.commits.builder;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.domain.Foo;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.testutil.SelectiveMaterializationFixtures.CollisionJoinProjection;
import laughing.man.commits.testutil.SelectiveMaterializationFixtures.CompensationProjection;
import laughing.man.commits.testutil.SelectiveMaterializationFixtures.ComputedCollisionProjection;
import laughing.man.commits.testutil.SelectiveMaterializationFixtures.DepartmentCompensationProjection;
import laughing.man.commits.testutil.SelectiveMaterializationFixtures.JoinCompensationChild;
import laughing.man.commits.testutil.SelectiveMaterializationFixtures.JoinCompensationParent;
import laughing.man.commits.testutil.SelectiveMaterializationFixtures.JoinComputedDetailProjection;
import laughing.man.commits.testutil.SelectiveMaterializationFixtures.JoinComputedProjection;
import laughing.man.commits.testutil.SelectiveMaterializationFixtures.JoinProjection;
import laughing.man.commits.testutil.SelectiveMaterializationFixtures.MultiJoinProjection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.fieldNames;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleChildren;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleCodes;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleCollisionChildren;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleCollisionParents;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleCompensation;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleComputedCollisionChildren;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleComputedCollisionParents;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleFoos;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleJoinCompensationChildren;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleJoinCompensationParents;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleLabels;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleMultiJoinParents;
import static laughing.man.commits.testutil.SelectiveMaterializationFixtures.sampleParents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterQueryBuilderSelectiveMaterializationTest {

    @Test
    void builderShouldMaterializeOnlyReferencedSourceFieldsForSimpleProjectionQueries() throws Exception {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleFoos())
                .addRule("stringField", "a", Clauses.EQUAL, Separator.AND)
                .addField("integerField");

        List<QueryRow> rows = builder.getRows();

        assertEquals(List.of("stringField", "integerField"), fieldNames(rows.get(0)));
        assertEquals(2, builder.initFilter().filter(Foo.class).size());
    }

    @Test
    void builderShouldExpandMaterializedSourceFieldsWhenQueryShapeNeedsMoreColumns() throws Exception {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleFoos())
                .addRule("stringField", "a", Clauses.EQUAL, Separator.AND)
                .addField("stringField");

        assertEquals(List.of("stringField"), fieldNames(builder.getRows().get(0)));

        builder.addOrder("integerField", 1);

        assertEquals(List.of("stringField", "integerField"), fieldNames(builder.getRows().get(0)));
        List<Foo> rows = builder.initFilter().filter(Sort.ASC, Foo.class);
        assertEquals(2, rows.size());
        assertEquals("a", rows.get(0).getStringField());
    }

    @Test
    void joinQueriesShouldSelectOnlyNeededParentSourceFieldsBeforeJoinExecution() throws Exception {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleParents())
                .addJoinBeans("id", sampleChildren(), "parentId", Join.LEFT_JOIN)
                .addRule("tag", "c1", Clauses.EQUAL, Separator.AND)
                .addField("name")
                .addField("tag");

        assertEquals(List.of("id", "name"), fieldNames(builder.getRows().get(0)));

        List<JoinProjection> rows = builder.initFilter().join().filter(JoinProjection.class);
        assertEquals(1, rows.size());
        assertEquals("p1", rows.get(0).name);
        assertEquals("c1", rows.get(0).tag);
    }

    @Test
    void unmatchedLeftJoinShouldRetainChildFieldTypesWithoutRescanningJoinedRows() {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleParents())
                .copyOnBuild(false)
                .addJoinBeans("id", sampleChildren(), "parentId", Join.LEFT_JOIN)
                .addField("name")
                .addField("tag");

        builder.initFilter().join();

        assertSame(String.class, builder.getFieldTypes().get("tag"));
    }

    @Test
    void computedFieldQueriesShouldMaterializeOnlyReferencedDependencies() throws Exception {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .add("totalComp", "adjustedSalary + bonus", Double.class)
                .build();

        FilterQueryBuilder builder = new FilterQueryBuilder(sampleCompensation())
                .computedFields(registry)
                .addRule("totalComp", 140.0, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("name")
                .addField("totalComp");

        assertEquals(List.of("name", "salary", "bonus", "adjustedSalary", "totalComp"),
                fieldNames(builder.getRows().get(0)));

        List<CompensationProjection> rows = builder.initFilter().filter(CompensationProjection.class);
        assertEquals(1, rows.size());
        assertEquals("b", rows.get(0).name);
        assertEquals(147.0, rows.get(0).totalComp, 0.0001);
    }

    @Test
    void computedMetricQueriesShouldMaterializeOnlyGroupedAndComputedDependencies() throws Exception {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .add("totalComp", "adjustedSalary + bonus", Double.class)
                .build();

        FilterQueryBuilder builder = new FilterQueryBuilder(sampleCompensation())
                .computedFields(registry)
                .addGroup("department")
                .addMetric("totalComp", Metric.SUM, "totalCompSum")
                .addOrder("totalCompSum", 1);

        assertEquals(List.of("salary", "bonus", "department", "adjustedSalary", "totalComp"),
                fieldNames(builder.getRows().get(0)));

        List<DepartmentCompensationProjection> rows = builder.initFilter()
                .filter(DepartmentCompensationProjection.class);
        assertEquals(2, rows.size());
        Map<String, Double> totalsByDepartment = rows.stream()
                .collect(java.util.stream.Collectors.toMap(row -> row.department, row -> row.totalCompSum));
        assertEquals(130.0, totalsByDepartment.get("fin"), 0.0001);
        assertEquals(251.0, totalsByDepartment.get("eng"), 0.0001);
    }

    @Test
    void computedJoinQueriesShouldMaterializeOnlyNeededParentAndChildDependencies() throws Exception {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("totalComp", "salary + bonus", Double.class)
                .build();

        FilterQueryBuilder builder = new FilterQueryBuilder(sampleJoinCompensationParents())
                .computedFields(registry)
                .addJoinBeans("id", sampleJoinCompensationChildren(), "parentId", Join.LEFT_JOIN)
                .addRule("totalComp", 135.0, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("name")
                .addField("totalComp");

        assertEquals(List.of("id", "name", "salary"), fieldNames(builder.getRows().get(0)));
        assertEquals(List.of("parentId", "bonus"),
                fieldNames(builder.getJoinClassesForExecution().get(1).get(0)));

        List<JoinComputedProjection> rows = builder.initFilter().join().filter(JoinComputedProjection.class);
        assertEquals(1, rows.size());
        assertEquals("b", rows.get(0).name);
        assertEquals(135.0, rows.get(0).totalComp, 0.0001);
    }

    @Test
    void computedJoinQueriesShouldKeepDistinctComputedValuesAcrossMultipleChildMatches() throws Exception {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("totalComp", "salary + bonus", Double.class)
                .build();

        List<JoinComputedDetailProjection> rows = new FilterQueryBuilder(List.of(
                new JoinCompensationParent(1, "a", 100, "fin")
        ))
                .computedFields(registry)
                .addJoinBeans("id", List.of(
                        new JoinCompensationChild(1, 20, "legacy"),
                        new JoinCompensationChild(1, 35, "retained")
                ), "parentId", Join.LEFT_JOIN)
                .addField("name")
                .addField("bonus")
                .addField("tag")
                .addField("totalComp")
                .initFilter()
                .join()
                .filter(JoinComputedDetailProjection.class);

        assertEquals(2, rows.size());
        assertEquals("a", rows.get(0).name);
        assertEquals(20, rows.get(0).bonus);
        assertEquals("legacy", rows.get(0).tag);
        assertEquals(120.0, rows.get(0).totalComp, 0.0001);
        assertEquals("a", rows.get(1).name);
        assertEquals(35, rows.get(1).bonus);
        assertEquals("retained", rows.get(1).tag);
        assertEquals(135.0, rows.get(1).totalComp, 0.0001);
    }

    @Test
    void computedJoinQueriesShouldFallBackWhenComputedOutputsCollideWithChildFields() throws Exception {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("bonus", "salary * 0.1", Double.class)
                .build();

        FilterQueryBuilder builder = new FilterQueryBuilder(sampleComputedCollisionParents())
                .computedFields(registry)
                .addJoinBeans("id", sampleComputedCollisionChildren(), "parentId", Join.LEFT_JOIN)
                .addRule("bonus", 12.0, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("name")
                .addField("bonus")
                .addField("child_bonus");

        assertEquals(List.of("id", "name", "salary", "bonus"), fieldNames(builder.getRows().get(0)));
        assertEquals(List.of("parentId", "bonus", "tag"),
                fieldNames(builder.getJoinClassesForExecution().get(1).get(0)));

        List<ComputedCollisionProjection> rows = builder.initFilter().join().filter(ComputedCollisionProjection.class);
        assertEquals(1, rows.size());
        assertEquals("b", rows.get(0).name);
        assertEquals(15.0, rows.get(0).bonus, 0.0001);
        assertEquals(25, rows.get(0).child_bonus);
    }

    @Test
    void collidingJoinQueriesShouldFallBackToFullParentMaterialization() throws Exception {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleCollisionParents())
                .addJoinBeans("id", sampleCollisionChildren(), "parentId", Join.LEFT_JOIN)
                .addRule("tag", "parent-tag", Clauses.EQUAL, Separator.AND)
                .addField("tag")
                .addField("child_name");

        assertEquals(List.of("id", "name", "tag"), fieldNames(builder.getRows().get(0)));
        assertEquals(List.of("parentId", "name"), fieldNames(builder.getJoinClassesForExecution().get(1).get(0)));

        List<CollisionJoinProjection> rows = builder.initFilter().join().filter(CollisionJoinProjection.class);
        assertEquals(1, rows.size());
        assertEquals("parent-tag", rows.get(0).tag);
        assertEquals("child-name", rows.get(0).child_name);
    }

    @Test
    void multipleJoinQueriesShouldFallBackToFullParentMaterialization() throws Exception {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleMultiJoinParents())
                .addJoinBeans("id", sampleLabels(), "parentId", Join.LEFT_JOIN)
                .addJoinBeans("id", sampleCodes(), "parentId", Join.LEFT_JOIN)
                .addRule("tag", "parent-tag", Clauses.EQUAL, Separator.AND)
                .addField("tag")
                .addField("label")
                .addField("code");

        assertEquals(List.of("id", "name", "tag", "region"), fieldNames(builder.getRows().get(0)));

        List<MultiJoinProjection> rows = builder.initFilter().join().filter(MultiJoinProjection.class);
        assertEquals(1, rows.size());
        assertEquals("parent-tag", rows.get(0).tag);
        assertEquals("north", rows.get(0).label);
        assertEquals("x1", rows.get(0).code);
    }

    @Test
    void executionSnapshotsShouldStayIsolatedFromTheirSourceBuilders() {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleFoos())
                .addField("stringField");

        FilterQueryBuilder snapshot = builder.snapshotForExecution();
        snapshot.addField("integerField")
                .addOrder("integerField", 1);

        assertEquals(List.of("stringField"), builder.getReturnFields());
        assertTrue(builder.getOrderFields().isEmpty());
        assertEquals(List.of("stringField", "integerField"), snapshot.getReturnFields());

        FilterQueryBuilder preparedTemplate = builder.snapshotForPreparedExecution();
        FilterQueryBuilder preparedExecution = preparedTemplate.preparedExecutionCopy(sampleFoos(), Map.of());
        preparedExecution.addGroup("stringField");

        assertTrue(preparedTemplate.getRows().isEmpty());
        assertTrue(preparedTemplate.getGroupFields().isEmpty());
        assertEquals(Map.of(1, "stringField"), preparedExecution.getGroupFields());
    }
}


