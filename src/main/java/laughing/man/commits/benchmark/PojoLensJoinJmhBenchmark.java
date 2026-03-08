package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLens;
import laughing.man.commits.enums.Join;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.ArrayList;
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

    @Setup
    public void setup() {
        parents = new ArrayList<>(size);
        children = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            parents.add(new ParentRow(i, "p" + i));
            children.add(new ChildRow(i, "c" + i));
        }
    }

    @Benchmark
    public List<ParentRow> pojoLensJoinLeft() throws Exception {
        return PojoLens.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .initFilter()
                .join()
                .filter(ParentRow.class);
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
}

