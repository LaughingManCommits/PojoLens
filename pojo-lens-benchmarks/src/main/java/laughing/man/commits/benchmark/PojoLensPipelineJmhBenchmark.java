package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLens;
import laughing.man.commits.enums.Clauses;
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class PojoLensPipelineJmhBenchmark {

    @Param({"1000", "10000"})
    public int size;

    private List<BenchmarkFoo> source;
    private String matchValue;
    private Filter filterPipeline;
    private Filter groupPipeline;

    @Setup
    public void setup() {
        source = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String value = "s" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 303L, i, 100);
            int integerField = BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 404L, i, 500);
            source.add(new BenchmarkFoo(value, new Date(BenchmarkProfiles.BASE_EPOCH_MILLIS + i), integerField));
        }
        matchValue = "s42";
        filterPipeline = PojoLens.newQueryBuilder(source)
                .addDistinct("stringField", 1)
                .addRule("stringField", matchValue, Clauses.EQUAL, Separator.AND)
                .addRule("integerField", 100, Clauses.BIGGER_EQUAL, Separator.AND)
                .addOrder("integerField", 1)
                .addField("stringField")
                .addField("integerField")
                .initFilter();
        groupPipeline = PojoLens.newQueryBuilder(source)
                .addRule("integerField", 200, Clauses.SMALLER_EQUAL, Separator.AND)
                .addGroup("stringField", 1)
                .addGroup("integerField", 2)
                .addField("stringField")
                .addField("integerField")
                .initFilter();
    }

    @Benchmark
    public List<BenchmarkFoo> fullFilterPipeline() throws Exception {
        return filterPipeline.filter(Sort.ASC, BenchmarkFoo.class);
    }

    @Benchmark
    public Map<String, List<BenchmarkFoo>> fullGroupPipeline() throws Exception {
        return groupPipeline.filterGroups(BenchmarkFoo.class);
    }
}

