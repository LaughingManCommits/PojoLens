package laughing.man.commits.builder;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.domain.Foo;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

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

        List<DepartmentCompensationProjection> rows = builder.initFilter().filter(DepartmentCompensationProjection.class);
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
        assertEquals(List.of("parentId", "bonus"), fieldNames(builder.getJoinClassesForExecution().get(1).get(0)));

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
        assertEquals(List.of("parentId", "bonus", "tag"), fieldNames(builder.getJoinClassesForExecution().get(1).get(0)));

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

    private static List<Foo> sampleFoos() {
        Date now = new Date();
        return Arrays.asList(
                new Foo("a", now, 2),
                new Foo("a", now, 1),
                new Foo("b", now, 3)
        );
    }

    private static List<Parent> sampleParents() {
        return List.of(new Parent(1, "p1"), new Parent(2, "p2"));
    }

    private static List<Child> sampleChildren() {
        return List.of(new Child(1, "c1"));
    }

    private static List<CompensationRow> sampleCompensation() {
        return List.of(
                new CompensationRow("a", 100, 20, "fin", 1),
                new CompensationRow("b", 120, 15, "eng", 2),
                new CompensationRow("c", 90, 5, "eng", 3)
        );
    }

    private static List<JoinCompensationParent> sampleJoinCompensationParents() {
        return List.of(
                new JoinCompensationParent(1, "a", 100, "fin"),
                new JoinCompensationParent(2, "b", 120, "eng")
        );
    }

    private static List<JoinCompensationChild> sampleJoinCompensationChildren() {
        return List.of(
                new JoinCompensationChild(1, 20, "legacy"),
                new JoinCompensationChild(2, 15, "retained")
        );
    }

    private static List<ComputedCollisionParent> sampleComputedCollisionParents() {
        return List.of(
                new ComputedCollisionParent(1, "a", 100),
                new ComputedCollisionParent(2, "b", 150)
        );
    }

    private static List<ComputedCollisionChild> sampleComputedCollisionChildren() {
        return List.of(
                new ComputedCollisionChild(1, 20, "first"),
                new ComputedCollisionChild(2, 25, "second")
        );
    }

    private static List<CollisionParent> sampleCollisionParents() {
        return List.of(new CollisionParent(1, "parent-name", "parent-tag"));
    }

    private static List<CollisionChild> sampleCollisionChildren() {
        return List.of(new CollisionChild(1, "child-name"));
    }

    private static List<MultiJoinParent> sampleMultiJoinParents() {
        return List.of(new MultiJoinParent(1, "parent-name", "parent-tag", "emea"));
    }

    private static List<LabelChild> sampleLabels() {
        return List.of(new LabelChild(1, "north"));
    }

    private static List<CodeChild> sampleCodes() {
        return List.of(new CodeChild(1, "x1"));
    }

    private static List<String> fieldNames(QueryRow row) {
        return row.getFields().stream()
                .map(QueryField::getFieldName)
                .toList();
    }

    static final class Parent {
        int id;
        String name;

        Parent(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class Child {
        int parentId;
        String tag;

        Child(int parentId, String tag) {
            this.parentId = parentId;
            this.tag = tag;
        }
    }

    public static final class JoinProjection {
        public String name;
        public String tag;

        JoinProjection() {
        }
    }

    static final class CompensationRow {
        String name;
        int salary;
        int bonus;
        String department;
        int level;

        CompensationRow(String name, int salary, int bonus, String department, int level) {
            this.name = name;
            this.salary = salary;
            this.bonus = bonus;
            this.department = department;
            this.level = level;
        }
    }

    public static final class CompensationProjection {
        public String name;
        public double totalComp;

        CompensationProjection() {
        }
    }

    public static final class DepartmentCompensationProjection {
        public String department;
        public double totalCompSum;

        DepartmentCompensationProjection() {
        }
    }

    static final class JoinCompensationParent {
        int id;
        String name;
        int salary;
        String department;

        JoinCompensationParent(int id, String name, int salary, String department) {
            this.id = id;
            this.name = name;
            this.salary = salary;
            this.department = department;
        }
    }

    static final class JoinCompensationChild {
        int parentId;
        int bonus;
        String tag;

        JoinCompensationChild(int parentId, int bonus, String tag) {
            this.parentId = parentId;
            this.bonus = bonus;
            this.tag = tag;
        }
    }

    public static final class JoinComputedProjection {
        public String name;
        public double totalComp;

        JoinComputedProjection() {
        }
    }

    public static final class JoinComputedDetailProjection {
        public String name;
        public int bonus;
        public String tag;
        public double totalComp;

        JoinComputedDetailProjection() {
        }
    }

    static final class ComputedCollisionParent {
        int id;
        String name;
        int salary;

        ComputedCollisionParent(int id, String name, int salary) {
            this.id = id;
            this.name = name;
            this.salary = salary;
        }
    }

    static final class ComputedCollisionChild {
        int parentId;
        int bonus;
        String tag;

        ComputedCollisionChild(int parentId, int bonus, String tag) {
            this.parentId = parentId;
            this.bonus = bonus;
            this.tag = tag;
        }
    }

    public static final class ComputedCollisionProjection {
        public String name;
        public double bonus;
        public int child_bonus;

        ComputedCollisionProjection() {
        }
    }

    static final class CollisionParent {
        int id;
        String name;
        String tag;

        CollisionParent(int id, String name, String tag) {
            this.id = id;
            this.name = name;
            this.tag = tag;
        }
    }

    static final class CollisionChild {
        int parentId;
        String name;

        CollisionChild(int parentId, String name) {
            this.parentId = parentId;
            this.name = name;
        }
    }

    public static final class CollisionJoinProjection {
        public String tag;
        public String child_name;

        CollisionJoinProjection() {
        }
    }

    static final class MultiJoinParent {
        int id;
        String name;
        String tag;
        String region;

        MultiJoinParent(int id, String name, String tag, String region) {
            this.id = id;
            this.name = name;
            this.tag = tag;
            this.region = region;
        }
    }

    static final class LabelChild {
        int parentId;
        String label;

        LabelChild(int parentId, String label) {
            this.parentId = parentId;
            this.label = label;
        }
    }

    static final class CodeChild {
        int parentId;
        String code;

        CodeChild(int parentId, String code) {
            this.parentId = parentId;
            this.code = code;
        }
    }

    public static final class MultiJoinProjection {
        public String tag;
        public String label;
        public String code;

        MultiJoinProjection() {
        }
    }
}
