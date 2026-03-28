package laughing.man.commits.testutil;

import laughing.man.commits.domain.Foo;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public final class SelectiveMaterializationFixtures {

    private SelectiveMaterializationFixtures() {
    }

    public static List<Foo> sampleFoos() {
        Date now = new Date();
        return Arrays.asList(
                new Foo("a", now, 2),
                new Foo("a", now, 1),
                new Foo("b", now, 3)
        );
    }

    public static List<Parent> sampleParents() {
        return List.of(new Parent(1, "p1"), new Parent(2, "p2"));
    }

    public static List<Child> sampleChildren() {
        return List.of(new Child(1, "c1"));
    }

    public static List<CompensationRow> sampleCompensation() {
        return List.of(
                new CompensationRow("a", 100, 20, "fin", 1),
                new CompensationRow("b", 120, 15, "eng", 2),
                new CompensationRow("c", 90, 5, "eng", 3)
        );
    }

    public static List<JoinCompensationParent> sampleJoinCompensationParents() {
        return List.of(
                new JoinCompensationParent(1, "a", 100, "fin"),
                new JoinCompensationParent(2, "b", 120, "eng")
        );
    }

    public static List<JoinCompensationChild> sampleJoinCompensationChildren() {
        return List.of(
                new JoinCompensationChild(1, 20, "legacy"),
                new JoinCompensationChild(2, 15, "retained")
        );
    }

    public static List<ComputedCollisionParent> sampleComputedCollisionParents() {
        return List.of(
                new ComputedCollisionParent(1, "a", 100),
                new ComputedCollisionParent(2, "b", 150)
        );
    }

    public static List<ComputedCollisionChild> sampleComputedCollisionChildren() {
        return List.of(
                new ComputedCollisionChild(1, 20, "first"),
                new ComputedCollisionChild(2, 25, "second")
        );
    }

    public static List<CollisionParent> sampleCollisionParents() {
        return List.of(new CollisionParent(1, "parent-name", "parent-tag"));
    }

    public static List<CollisionChild> sampleCollisionChildren() {
        return List.of(new CollisionChild(1, "child-name"));
    }

    public static List<MultiJoinParent> sampleMultiJoinParents() {
        return List.of(new MultiJoinParent(1, "parent-name", "parent-tag", "emea"));
    }

    public static List<LabelChild> sampleLabels() {
        return List.of(new LabelChild(1, "north"));
    }

    public static List<CodeChild> sampleCodes() {
        return List.of(new CodeChild(1, "x1"));
    }

    public static List<String> fieldNames(QueryRow row) {
        return row.getFields().stream()
                .map(QueryField::getFieldName)
                .toList();
    }

    public static final class Parent {
        int id;
        String name;

        public Parent(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Child {
        int parentId;
        String tag;

        public Child(int parentId, String tag) {
            this.parentId = parentId;
            this.tag = tag;
        }
    }

    public static final class JoinProjection {
        public String name;
        public String tag;

        public JoinProjection() {
        }
    }

    public static final class CompensationRow {
        String name;
        int salary;
        int bonus;
        String department;
        int level;

        public CompensationRow(String name, int salary, int bonus, String department, int level) {
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

        public CompensationProjection() {
        }
    }

    public static final class DepartmentCompensationProjection {
        public String department;
        public double totalCompSum;

        public DepartmentCompensationProjection() {
        }
    }

    public static final class JoinCompensationParent {
        int id;
        String name;
        int salary;
        String department;

        public JoinCompensationParent(int id, String name, int salary, String department) {
            this.id = id;
            this.name = name;
            this.salary = salary;
            this.department = department;
        }
    }

    public static final class JoinCompensationChild {
        int parentId;
        int bonus;
        String tag;

        public JoinCompensationChild(int parentId, int bonus, String tag) {
            this.parentId = parentId;
            this.bonus = bonus;
            this.tag = tag;
        }
    }

    public static final class JoinComputedProjection {
        public String name;
        public double totalComp;

        public JoinComputedProjection() {
        }
    }

    public static final class JoinComputedDetailProjection {
        public String name;
        public int bonus;
        public String tag;
        public double totalComp;

        public JoinComputedDetailProjection() {
        }
    }

    public static final class ComputedCollisionParent {
        int id;
        String name;
        int salary;

        public ComputedCollisionParent(int id, String name, int salary) {
            this.id = id;
            this.name = name;
            this.salary = salary;
        }
    }

    public static final class ComputedCollisionChild {
        int parentId;
        int bonus;
        String tag;

        public ComputedCollisionChild(int parentId, int bonus, String tag) {
            this.parentId = parentId;
            this.bonus = bonus;
            this.tag = tag;
        }
    }

    public static final class ComputedCollisionProjection {
        public String name;
        public double bonus;
        public int child_bonus;

        public ComputedCollisionProjection() {
        }
    }

    public static final class CollisionParent {
        int id;
        String name;
        String tag;

        public CollisionParent(int id, String name, String tag) {
            this.id = id;
            this.name = name;
            this.tag = tag;
        }
    }

    public static final class CollisionChild {
        int parentId;
        String name;

        public CollisionChild(int parentId, String name) {
            this.parentId = parentId;
            this.name = name;
        }
    }

    public static final class CollisionJoinProjection {
        public String tag;
        public String child_name;

        public CollisionJoinProjection() {
        }
    }

    public static final class MultiJoinParent {
        int id;
        String name;
        String tag;
        String region;

        public MultiJoinParent(int id, String name, String tag, String region) {
            this.id = id;
            this.name = name;
            this.tag = tag;
            this.region = region;
        }
    }

    public static final class LabelChild {
        int parentId;
        String label;

        public LabelChild(int parentId, String label) {
            this.parentId = parentId;
            this.label = label;
        }
    }

    public static final class CodeChild {
        int parentId;
        String code;

        public CodeChild(int parentId, String code) {
            this.parentId = parentId;
            this.code = code;
        }
    }

    public static final class MultiJoinProjection {
        public String tag;
        public String label;
        public String code;

        public MultiJoinProjection() {
        }
    }
}


