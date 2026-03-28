package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLensCore;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.filter.Filter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class PojoLensJoinJmhBenchmark {

    @Param({"1000", "10000"})
    public int size;

    private List<ParentRow> parents;
    private List<ChildRow> children;
    private List<ComputedParentRow> computedParents;
    private List<ComputedChildRow> computedChildren;
    private ComputedFieldRegistry computedFieldRegistry;
    private double minimumTotalComp;
    private Filter joinLeftFilter;
    private Filter computedJoinFilter;
    private Filter computedJoinOrderedFilter;

    @Setup
    public void setup() {
        parents = new ArrayList<>(size);
        children = new ArrayList<>(size);
        computedParents = new ArrayList<>(size);
        computedChildren = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            parents.add(new ParentRow(i, "p" + i));
            children.add(new ChildRow(i, "c" + i));
            int salary = 90_000 + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1101L, i, 70_000);
            int bonus = 5_000 + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1102L, i, 20_000);
            computedParents.add(new ComputedParentRow(i, "emp" + i, salary));
            computedChildren.add(new ComputedChildRow(i, bonus));
        }
        computedFieldRegistry = ComputedFieldRegistry.builder()
                .add("totalComp", "salary + bonus", Double.class)
                .build();
        minimumTotalComp = 140_000d;
        joinLeftFilter = PojoLensCore.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .initFilter();
        computedJoinFilter = PojoLensCore.newQueryBuilder(computedParents)
                .computedFields(computedFieldRegistry)
                .addJoinBeans("id", computedChildren, "parentId", Join.LEFT_JOIN)
                .addRule("totalComp", minimumTotalComp, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("name")
                .addField("totalComp")
                .initFilter();
        computedJoinOrderedFilter = PojoLensCore.newQueryBuilder(computedParents)
                .computedFields(computedFieldRegistry)
                .addJoinBeans("id", computedChildren, "parentId", Join.LEFT_JOIN)
                .addRule("totalComp", minimumTotalComp, Clauses.BIGGER_EQUAL, Separator.AND)
                .addOrder("totalComp", 1)
                .limit(100)
                .addField("name")
                .addField("totalComp")
                .initFilter();
    }

    @Benchmark
    public List<ParentRow> pojoLensJoinLeft() throws Exception {
        return joinLeftFilter.join().filter(ParentRow.class);
    }

    @Benchmark
    public List<ParentRow> manualHashJoinLeft() {
        Map<Integer, ChildRow> childByParent = new HashMap<>(children.size() * 2);
        for (ChildRow child : children) {
            childByParent.put(child.parentId, child);
        }
        List<ParentRow> out = new ArrayList<>(parents.size());
        for (ParentRow parent : parents) {
            ChildRow child = childByParent.get(parent.id);
            ParentRow row = new ParentRow();
            row.id = parent.id;
            row.name = parent.name;
            row.parentId = child == null ? 0 : child.parentId;
            row.tag = child == null ? "null" : child.tag;
            out.add(row);
        }
        return out;
    }

    @Benchmark
    public List<ComputedJoinProjection> pojoLensJoinLeftComputedField() throws Exception {
        return computedJoinFilter.join().filter(ComputedJoinProjection.class);
    }

    @Benchmark
    public List<ComputedJoinProjection> pojoLensJoinLeftComputedFieldOrderedLimited() throws Exception {
        return computedJoinOrderedFilter.join().filter(Sort.ASC, ComputedJoinProjection.class);
    }

    @Benchmark
    public List<ComputedJoinProjection> manualHashJoinLeftComputedField() {
        Map<Integer, ComputedChildRow> childByParent = new HashMap<>(computedChildren.size() * 2);
        for (ComputedChildRow child : computedChildren) {
            childByParent.put(child.parentId, child);
        }
        List<ComputedJoinProjection> out = new ArrayList<>(computedParents.size());
        for (ComputedParentRow parent : computedParents) {
            ComputedChildRow child = childByParent.get(parent.id);
            int bonus = child == null ? 0 : child.bonus;
            double totalComp = parent.salary + bonus;
            if (totalComp >= minimumTotalComp) {
                ComputedJoinProjection row = new ComputedJoinProjection();
                row.name = parent.name;
                row.totalComp = totalComp;
                out.add(row);
            }
        }
        return out;
    }

    @Benchmark
    public List<ComputedJoinProjection> manualHashJoinLeftComputedFieldOrderedLimited() {
        Map<Integer, ComputedChildRow> childByParent = new HashMap<>(computedChildren.size() * 2);
        for (ComputedChildRow child : computedChildren) {
            childByParent.put(child.parentId, child);
        }
        List<ComputedJoinProjection> out = new ArrayList<>(computedParents.size());
        for (ComputedParentRow parent : computedParents) {
            ComputedChildRow child = childByParent.get(parent.id);
            int bonus = child == null ? 0 : child.bonus;
            double totalComp = parent.salary + bonus;
            if (totalComp >= minimumTotalComp) {
                ComputedJoinProjection row = new ComputedJoinProjection();
                row.name = parent.name;
                row.totalComp = totalComp;
                out.add(row);
            }
        }
        out.sort(Comparator.comparingDouble(r -> r.totalComp));
        if (out.size() <= 100) {
            return out;
        }
        return new ArrayList<>(out.subList(0, 100));
    }

    public static class ParentRow {
        int id;
        String name;
        int parentId;
        String tag;

        public ParentRow() {
        }

        public ParentRow(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class ChildRow {
        int parentId;
        String tag;

        public ChildRow() {
        }

        public ChildRow(int parentId, String tag) {
            this.parentId = parentId;
            this.tag = tag;
        }
    }

    public static class ComputedParentRow {
        int id;
        String name;
        int salary;

        public ComputedParentRow() {
        }

        public ComputedParentRow(int id, String name, int salary) {
            this.id = id;
            this.name = name;
            this.salary = salary;
        }
    }

    public static class ComputedChildRow {
        int parentId;
        int bonus;

        public ComputedChildRow() {
        }

        public ComputedChildRow(int parentId, int bonus) {
            this.parentId = parentId;
            this.bonus = bonus;
        }
    }

    public static class ComputedJoinProjection {
        String name;
        double totalComp;

        public ComputedJoinProjection() {
        }
    }
}


