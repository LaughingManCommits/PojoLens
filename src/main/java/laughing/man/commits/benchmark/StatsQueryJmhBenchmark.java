package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLens;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Metric;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class StatsQueryJmhBenchmark {

    @Param({"1000", "10000"})
    public int size;

    private List<BenchmarkFoo> source;
    private String groupedSql;
    private String bucketSql;
    private ChartSpec groupedChartSpec;
    private ChartSpec bucketChartSpec;
    private Filter fluentGroupedFilter;
    private Filter fluentTimeBucketFilter;
    private Filter fluentGroupedToChartFilter;
    private Filter fluentTimeBucketToChartFilter;
    private QueryBuilder fluentGroupedExplainBuilder;
    private SqlLikeQuery parsedGroupedSql;
    private SqlLikeQuery parsedBucketSql;

    @Setup
    public void setup() {
        source = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String value = "dept" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 101L, i, 12);
            Date date = new Date(BenchmarkProfiles.STATS_BASE_EPOCH_MILLIS + (i * 86_400_000L));
            int integerField = BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 202L, i, 1000);
            source.add(new BenchmarkFoo(value, date, integerField));
        }
        groupedSql = "select stringField, count(*) as total, sum(integerField) as totalValue "
                + "group by stringField";
        bucketSql = "select bucket(dateField,'month') as period, count(*) as total, sum(integerField) as totalValue "
                + "group by period";
        groupedChartSpec = ChartSpec.of(ChartType.BAR, "stringField", "totalValue");
        bucketChartSpec = ChartSpec.of(ChartType.LINE, "period", "totalValue");

        PojoLens.setStatsPlanCacheEnabled(true);
        PojoLens.setStatsPlanCacheStatsEnabled(true);
        PojoLens.setStatsPlanCacheMaxEntries(1024);
        PojoLens.setStatsPlanCacheMaxWeight(0L);
        PojoLens.setStatsPlanCacheExpireAfterWriteMillis(0L);
        PojoLens.clearStatsPlanCache();
        PojoLens.resetStatsPlanCacheStats();
        PojoLens.setSqlLikeCacheEnabled(true);
        PojoLens.setSqlLikeCacheStatsEnabled(true);
        PojoLens.setSqlLikeCacheExpireAfterWriteMillis(0L);

        fluentGroupedFilter = PojoLens.newQueryBuilder(source)
                .addGroup("stringField")
                .addCount("total")
                .addMetric("integerField", Metric.SUM, "totalValue")
                .initFilter();
        fluentTimeBucketFilter = PojoLens.newQueryBuilder(source)
                .addTimeBucket("dateField", TimeBucket.MONTH, "period")
                .addCount("total")
                .addMetric("integerField", Metric.SUM, "totalValue")
                .initFilter();
        fluentGroupedToChartFilter = PojoLens.newQueryBuilder(source)
                .addGroup("stringField")
                .addCount("total")
                .addMetric("integerField", Metric.SUM, "totalValue")
                .initFilter();
        fluentTimeBucketToChartFilter = PojoLens.newQueryBuilder(source)
                .addTimeBucket("dateField", TimeBucket.MONTH, "period")
                .addCount("total")
                .addMetric("integerField", Metric.SUM, "totalValue")
                .initFilter();
        fluentGroupedExplainBuilder = PojoLens.newQueryBuilder(source)
                .addGroup("stringField")
                .addCount("total")
                .addMetric("integerField", Metric.SUM, "totalValue");
        parsedGroupedSql = PojoLens.parse(groupedSql);
        parsedBucketSql = PojoLens.parse(bucketSql);
    }

    @Benchmark
    public List<GroupedStatsRow> fluentGroupedMetrics() {
        return fluentGroupedFilter.filter(GroupedStatsRow.class);
    }

    @Benchmark
    public List<BucketedStatsRow> fluentTimeBucketMetrics() {
        return fluentTimeBucketFilter.filter(BucketedStatsRow.class);
    }

    @Benchmark
    public List<GroupedStatsRow> sqlLikeParseAndGroupedMetrics() {
        return parsedGroupedSql.filter(source, GroupedStatsRow.class);
    }

    @Benchmark
    public List<BucketedStatsRow> sqlLikeParseAndTimeBucketMetrics() {
        return parsedBucketSql.filter(source, BucketedStatsRow.class);
    }

    @Benchmark
    public ChartData fluentGroupedMetricsToChart() {
        return fluentGroupedToChartFilter.chart(GroupedStatsRow.class, groupedChartSpec);
    }

    @Benchmark
    public ChartData fluentTimeBucketMetricsToChart() {
        return fluentTimeBucketToChartFilter.chart(BucketedStatsRow.class, bucketChartSpec);
    }

    @Benchmark
    public ChartData sqlLikeParseAndGroupedMetricsToChart() {
        return parsedGroupedSql.chart(source, GroupedStatsRow.class, groupedChartSpec);
    }

    @Benchmark
    public ChartData sqlLikeParseAndTimeBucketMetricsToChart() {
        return parsedBucketSql.chart(source, BucketedStatsRow.class, bucketChartSpec);
    }

    @Benchmark
    public Map<String, Object> fluentGroupedMetricsExplain() {
        return fluentGroupedExplainBuilder.explain();
    }

    @Benchmark
    public Map<String, Object> sqlLikeGroupedMetricsExplain() {
        return parsedGroupedSql.explain();
    }

    public static class GroupedStatsRow {
        public String stringField;
        public long total;
        public long totalValue;

        public GroupedStatsRow() {
        }
    }

    public static class BucketedStatsRow {
        public String period;
        public long total;
        public long totalValue;

        public BucketedStatsRow() {
        }
    }
}

