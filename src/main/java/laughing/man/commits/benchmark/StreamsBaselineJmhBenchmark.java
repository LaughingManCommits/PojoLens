package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLens;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.TimeBucket;
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
public class StreamsBaselineJmhBenchmark {

    @Param({"1000", "10000"})
    public int size;

    private List<BenchmarkFoo> source;
    private String matchValue;
    private Filter filterProjectionFilter;
    private Filter groupedMetricsFilter;
    private Filter timeBucketMetricsFilter;

    @Setup
    public void setup() {
        source = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String value = "dept" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 101L, i, 12);
            Date date = new Date(BenchmarkProfiles.STATS_BASE_EPOCH_MILLIS + (i * 86_400_000L));
            int integerField = BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 202L, i, 1000);
            source.add(new BenchmarkFoo(value, date, integerField));
        }
        matchValue = "dept3";
        filterProjectionFilter = PojoLens.newQueryBuilder(source)
                .addRule("stringField", matchValue, Clauses.EQUAL, Separator.AND)
                .addRule("integerField", 100, Clauses.BIGGER_EQUAL, Separator.AND)
                .addOrder("integerField", 1)
                .limit(200)
                .addField("stringField")
                .addField("integerField")
                .initFilter();
        groupedMetricsFilter = PojoLens.newQueryBuilder(source)
                .addGroup("stringField")
                .addCount("total")
                .addMetric("integerField", Metric.SUM, "totalValue")
                .initFilter();
        timeBucketMetricsFilter = PojoLens.newQueryBuilder(source)
                .addTimeBucket("dateField", TimeBucket.MONTH, "period")
                .addCount("total")
                .addMetric("integerField", Metric.SUM, "totalValue")
                .initFilter();
    }

    @Benchmark
    public List<StreamsBaselineSupport.FilterProjectionRow> fluentFilterProjection() {
        return filterProjectionFilter.filter(Sort.ASC, StreamsBaselineSupport.FilterProjectionRow.class);
    }

    @Benchmark
    public List<StreamsBaselineSupport.FilterProjectionRow> streamsFilterProjection() {
        return StreamsBaselineSupport.filterProjection(source, matchValue);
    }

    @Benchmark
    public List<StreamsBaselineSupport.GroupedStatsRow> fluentGroupedMetrics() {
        return groupedMetricsFilter.filter(StreamsBaselineSupport.GroupedStatsRow.class);
    }

    @Benchmark
    public List<StreamsBaselineSupport.GroupedStatsRow> streamsGroupedMetrics() {
        return StreamsBaselineSupport.groupedMetrics(source);
    }

    @Benchmark
    public List<StreamsBaselineSupport.BucketedStatsRow> fluentTimeBucketMetrics() {
        return timeBucketMetricsFilter.filter(StreamsBaselineSupport.BucketedStatsRow.class);
    }

    @Benchmark
    public List<StreamsBaselineSupport.BucketedStatsRow> streamsTimeBucketMetrics() {
        return StreamsBaselineSupport.monthlyBucketMetrics(source);
    }
}

