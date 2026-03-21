package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLens;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
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
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class IndexHintJmhBenchmark {

    @Param({"1000", "10000"})
    public int size;

    private List<BenchmarkFoo> source;
    private String targetKey;

    @Setup
    public void setup() {
        source = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            source.add(new BenchmarkFoo(
                    "row-" + i,
                    new Date(BenchmarkProfiles.STATS_BASE_EPOCH_MILLIS + (i * 86_400_000L)),
                    BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1312L, i, 10_000)
            ));
        }
        targetKey = "row-" + (size / 2);
    }

    @Benchmark
    public long fluentFilterScan() {
        return checksum(PojoLens.newQueryBuilder(source)
                .addRule("stringField", targetKey, Clauses.EQUAL, Separator.AND)
                .addRule("integerField", 0, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("stringField")
                .addField("integerField")
                .initFilter()
                .filter(BenchmarkFoo.class));
    }

    @Benchmark
    public long fluentFilterIndexed() {
        return checksum(PojoLens.newQueryBuilder(source)
                .addIndex("stringField")
                .addRule("stringField", targetKey, Clauses.EQUAL, Separator.AND)
                .addRule("integerField", 0, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("stringField")
                .addField("integerField")
                .initFilter()
                .filter(BenchmarkFoo.class));
    }

    private static long checksum(List<BenchmarkFoo> rows) {
        long checksum = 0L;
        for (BenchmarkFoo row : rows) {
            checksum += row.getIntegerField();
            if (row.getStringField() != null) {
                checksum += row.getStringField().hashCode();
            }
        }
        return checksum;
    }
}
