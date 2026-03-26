package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLensCore;

import laughing.man.commits.PojoLens;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
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
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class PojoLensJmhBenchmark {

    @Param({"1000", "10000"})
    public int size;

    private List<BenchmarkFoo> source;
    private String matchValue;
    private Filter pojoLensFilterPlan;

    @Setup
    public void setup() {
        source = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String value = "v" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 505L, i, 50);
            int integerField = BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 606L, i, size + 500);
            source.add(new BenchmarkFoo(value, new Date(BenchmarkProfiles.BASE_EPOCH_MILLIS + i), integerField));
        }
        matchValue = "v25";
        pojoLensFilterPlan = PojoLensCore.newQueryBuilder(source)
                .addRule("stringField", matchValue, Clauses.EQUAL, Separator.OR)
                .addDistinct("stringField", 1)
                .addOrder("integerField", 1)
                .initFilter();
    }

    @Benchmark
    public List<BenchmarkFoo> pojoLensFilter() throws Exception {
        return pojoLensFilterPlan.filter(BenchmarkFoo.class);
    }

    @Benchmark
    public List<BenchmarkFoo> manualFilter() {
        List<BenchmarkFoo> out = new ArrayList<>();
        boolean found = false;
        BenchmarkFoo best = null;
        for (BenchmarkFoo foo : source) {
            if (matchValue.equals(foo.getStringField())) {
                if (!found || foo.getIntegerField() < best.getIntegerField()) {
                    best = foo;
                    found = true;
                }
            }
        }
        if (best != null) {
            out.add(best);
        }
        return out;
    }
}


