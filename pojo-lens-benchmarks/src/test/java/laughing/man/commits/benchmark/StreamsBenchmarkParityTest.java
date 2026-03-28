package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLensCore;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.testing.FluentSqlLikeParity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StreamsBenchmarkParityTest {

    @Test
    public void streamsFilterProjectionShouldMatchFluentFilterProjection() {
        List<BenchmarkFoo> source = sampleRows(1000);
        List<StreamsBaselineSupport.FilterProjectionRow> fluent = PojoLensCore.newQueryBuilder(source)
                .addRule("stringField", "dept3", Clauses.EQUAL, Separator.AND)
                .addRule("integerField", 100, Clauses.BIGGER_EQUAL, Separator.AND)
                .addOrder("integerField", 1)
                .limit(200)
                .addField("stringField")
                .addField("integerField")
                .initFilter()
                .filter(Sort.ASC, StreamsBaselineSupport.FilterProjectionRow.class);

        List<StreamsBaselineSupport.FilterProjectionRow> streams = StreamsBaselineSupport.filterProjection(source, "dept3");

        FluentSqlLikeParity.assertOrderedEquals(
                fluent,
                streams,
                row -> row.stringField + ":" + row.integerField
        );
    }

    @Test
    public void streamsGroupedMetricsShouldMatchFluentGroupedMetrics() {
        List<BenchmarkFoo> source = sampleRows(1000);
        List<StreamsBaselineSupport.GroupedStatsRow> fluent = PojoLensCore.newQueryBuilder(source)
                .addGroup("stringField")
                .addCount("total")
                .addMetric("integerField", Metric.SUM, "totalValue")
                .initFilter()
                .filter(StreamsBaselineSupport.GroupedStatsRow.class);
        fluent.sort(java.util.Comparator.comparing(row -> row.stringField));

        List<StreamsBaselineSupport.GroupedStatsRow> streams = StreamsBaselineSupport.groupedMetrics(source);

        FluentSqlLikeParity.assertOrderedEquals(
                fluent,
                streams,
                row -> row.stringField + ":" + row.total + ":" + row.totalValue
        );
    }

    @Test
    public void streamsTimeBucketMetricsShouldMatchFluentTimeBucketMetrics() {
        List<BenchmarkFoo> source = sampleRows(1000);
        List<StreamsBaselineSupport.BucketedStatsRow> fluent = PojoLensCore.newQueryBuilder(source)
                .addTimeBucket("dateField", TimeBucket.MONTH, "period")
                .addCount("total")
                .addMetric("integerField", Metric.SUM, "totalValue")
                .initFilter()
                .filter(StreamsBaselineSupport.BucketedStatsRow.class);
        fluent.sort(java.util.Comparator.comparing(row -> row.period));

        List<StreamsBaselineSupport.BucketedStatsRow> streams = StreamsBaselineSupport.monthlyBucketMetrics(source);

        FluentSqlLikeParity.assertOrderedEquals(
                fluent,
                streams,
                row -> row.period + ":" + row.total + ":" + row.totalValue
        );
    }

    private static List<BenchmarkFoo> sampleRows(int size) {
        List<BenchmarkFoo> source = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String value = "dept" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 101L, i, 12);
            Date date = new Date(BenchmarkProfiles.STATS_BASE_EPOCH_MILLIS + (i * 86_400_000L));
            int integerField = BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 202L, i, 1000);
            source.add(new BenchmarkFoo(value, date, integerField));
        }
        return source;
    }
}


