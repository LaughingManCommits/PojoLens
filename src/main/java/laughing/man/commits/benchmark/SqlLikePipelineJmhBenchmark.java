package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLens;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import laughing.man.commits.sqllike.parser.SqlLikeParser;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class SqlLikePipelineJmhBenchmark {

    @Param({"1000", "10000"})
    public int size;

    private List<BenchmarkFoo> source;
    private String query;
    private String havingQuery;
    private String explainQuery;
    private String booleanDepthQuery;
    private String havingComputedQuery;

    @Setup
    public void setup() {
        source = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String value = "s" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED, i, 100);
            int integerField = BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 77L, i, 500);
            source.add(new BenchmarkFoo(value, new Date(BenchmarkProfiles.BASE_EPOCH_MILLIS + i), integerField));
        }
        query = "select stringField, integerField "
                + "where stringField = 's42' and integerField >= 100 "
                + "order by integerField asc limit 200";
        havingQuery = "select stringField, count(*) as total "
                + "group by stringField "
                + "having total >= 5 "
                + "order by total desc limit 20";
        explainQuery = "select stringField, count(*) as total "
                + "where integerField >= 100 "
                + "group by stringField "
                + "having total >= 5 or count(*) >= 2 "
                + "order by total desc limit 20";
        booleanDepthQuery = "select stringField, integerField "
                + "where (integerField - 10 >= 100 and (stringField = 's42' or stringField = 's84')) "
                + "or (integerField - 10 >= 100 and stringField = 's21') "
                + "order by integerField asc limit 200";
        havingComputedQuery = "select stringField, count(*) as total, sum(integerField) as totalValue "
                + "group by stringField "
                + "having totalValue / total >= 200 and (total >= 5 or total = 3) "
                + "order by totalValue desc limit 20";
    }

    @Benchmark
    public Object parseOnly() {
        return SqlLikeParser.parse(query);
    }

    @Benchmark
    public List<BenchmarkFoo> parseAndFilter() {
        return PojoLens.parse(query).filter(source, BenchmarkFoo.class);
    }

    @Benchmark
    public List<BenchmarkGroupRow> parseAndFilterHaving() {
        return PojoLens.parse(havingQuery).filter(source, BenchmarkGroupRow.class);
    }

    @Benchmark
    public Map<String, Object> parseAndExplain() {
        return PojoLens.parse(explainQuery).explain();
    }

    @Benchmark
    public List<BenchmarkFoo> parseAndFilterBooleanDepth() {
        return PojoLens.parse(booleanDepthQuery).filter(source, BenchmarkFoo.class);
    }

    @Benchmark
    public List<BenchmarkHavingRow> parseAndFilterHavingComputed() {
        return PojoLens.parse(havingComputedQuery).filter(source, BenchmarkHavingRow.class);
    }

    @Benchmark
    public Map<String, Object> sqlLikeCacheSnapshotRead() {
        return PojoLens.getSqlLikeCacheSnapshot();
    }

    public static class BenchmarkGroupRow {
        String stringField;
        long total;

        public BenchmarkGroupRow() {
        }
    }

    public static class BenchmarkHavingRow {
        String stringField;
        long total;
        long totalValue;

        public BenchmarkHavingRow() {
        }
    }
}

