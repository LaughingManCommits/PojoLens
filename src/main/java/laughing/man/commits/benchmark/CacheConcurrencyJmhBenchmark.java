package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLens;
import laughing.man.commits.enums.Metric;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class CacheConcurrencyJmhBenchmark {

    private static final String[] SQL_HOT_SET = new String[] {
            "where stringField = 'dept1' and integerField >= 200",
            "where stringField = 'dept2' and integerField >= 200",
            "where integerField >= 500",
            "where integerField >= 700 and stringField = 'dept3'",
            "where stringField = 'dept4' order by integerField desc limit 100"
    };

    private List<BenchmarkFoo> source;

    @Setup
    public void setup() {
        source = new ArrayList<>(20_000);
        for (int i = 0; i < 20_000; i++) {
            String value = "dept" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 701L, i, 8);
            Date date = new Date(BenchmarkProfiles.BASE_EPOCH_MILLIS + i);
            int integerField = BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 702L, i, 1_000);
            source.add(new BenchmarkFoo(value, date, integerField));
        }

        PojoLens.setSqlLikeCacheEnabled(true);
        PojoLens.setSqlLikeCacheMaxEntries(2048);
        PojoLens.setSqlLikeCacheExpireAfterWriteMillis(0L);
        PojoLens.setSqlLikeCacheStatsEnabled(true);
        PojoLens.clearSqlLikeCache();
        PojoLens.resetSqlLikeCacheStats();

        PojoLens.setStatsPlanCacheEnabled(true);
        PojoLens.setStatsPlanCacheMaxEntries(2048);
        PojoLens.setStatsPlanCacheExpireAfterWriteMillis(0L);
        PojoLens.setStatsPlanCacheStatsEnabled(true);
        PojoLens.clearStatsPlanCache();
        PojoLens.resetStatsPlanCacheStats();
    }

    @Benchmark
    @Threads(8)
    public Object sqlLikeParseHotSetConcurrent() {
        int index = ThreadLocalRandom.current().nextInt(SQL_HOT_SET.length);
        return PojoLens.parse(SQL_HOT_SET[index]);
    }

    @Benchmark
    @Threads(8)
    public List<GroupedStatsRow> statsPlanBuildHotSetConcurrent() {
        int mode = ThreadLocalRandom.current().nextInt(3);
        if (mode == 0) {
            return PojoLens.newQueryBuilder(source)
                    .addGroup("stringField")
                    .addCount("total")
                    .initFilter()
                    .filter(GroupedStatsRow.class);
        }
        if (mode == 1) {
            return PojoLens.newQueryBuilder(source)
                    .addGroup("stringField")
                    .addMetric("integerField", Metric.SUM, "sumValue")
                    .initFilter()
                    .filter(GroupedStatsRow.class);
        }
        return PojoLens.newQueryBuilder(source)
                .addGroup("stringField")
                .addMetric("integerField", Metric.MAX, "sumValue")
                .initFilter()
                .filter(GroupedStatsRow.class);
    }

    public static class GroupedStatsRow {
        public String stringField;
        public long total;
        public long sumValue;

        public GroupedStatsRow() {
        }
    }
}

