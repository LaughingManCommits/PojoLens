package laughing.man.commits.benchmark;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StreamsBaselineSupport {

    private StreamsBaselineSupport() {
    }

    public static List<FilterProjectionRow> filterProjection(List<BenchmarkFoo> source, String matchValue) {
        return source.stream()
                .filter(row -> matchValue.equals(row.getStringField()))
                .filter(row -> row.getIntegerField() >= 100)
                .sorted(Comparator.comparingInt(BenchmarkFoo::getIntegerField))
                .limit(200)
                .map(row -> new FilterProjectionRow(row.getStringField(), row.getIntegerField()))
                .toList();
    }

    public static List<GroupedStatsRow> groupedMetrics(List<BenchmarkFoo> source) {
        Map<String, Aggregate> totals = new LinkedHashMap<>();
        for (BenchmarkFoo row : source) {
            Aggregate aggregate = totals.computeIfAbsent(row.getStringField(), ignored -> new Aggregate());
            aggregate.count++;
            aggregate.sum += row.getIntegerField();
        }
        ArrayList<GroupedStatsRow> rows = new ArrayList<>(totals.size());
        for (Map.Entry<String, Aggregate> entry : totals.entrySet()) {
            Aggregate aggregate = entry.getValue();
            rows.add(new GroupedStatsRow(entry.getKey(), aggregate.count, aggregate.sum));
        }
        rows.sort(Comparator.comparing(r -> r.stringField));
        return rows;
    }

    public static List<BucketedStatsRow> monthlyBucketMetrics(List<BenchmarkFoo> source) {
        Map<String, Aggregate> totals = new LinkedHashMap<>();
        for (BenchmarkFoo row : source) {
            String period = bucketMonth(row);
            Aggregate aggregate = totals.computeIfAbsent(period, ignored -> new Aggregate());
            aggregate.count++;
            aggregate.sum += row.getIntegerField();
        }
        ArrayList<BucketedStatsRow> rows = new ArrayList<>(totals.size());
        for (Map.Entry<String, Aggregate> entry : totals.entrySet()) {
            Aggregate aggregate = entry.getValue();
            rows.add(new BucketedStatsRow(entry.getKey(), aggregate.count, aggregate.sum));
        }
        rows.sort(Comparator.comparing(r -> r.period));
        return rows;
    }

    private static String bucketMonth(BenchmarkFoo row) {
        Instant instant = row.getDateField().toInstant();
        YearMonth yearMonth = YearMonth.from(instant.atZone(ZoneOffset.UTC));
        return yearMonth.toString();
    }

    private static final class Aggregate {
        private long count;
        private long sum;
    }

    public static class FilterProjectionRow {
        public String stringField;
        public int integerField;

        public FilterProjectionRow() {
        }

        public FilterProjectionRow(String stringField, int integerField) {
            this.stringField = stringField;
            this.integerField = integerField;
        }
    }

    public static class GroupedStatsRow {
        public String stringField;
        public long total;
        public long totalValue;

        public GroupedStatsRow() {
        }

        public GroupedStatsRow(String stringField, long total, long totalValue) {
            this.stringField = stringField;
            this.total = total;
            this.totalValue = totalValue;
        }
    }

    public static class BucketedStatsRow {
        public String period;
        public long total;
        public long totalValue;

        public BucketedStatsRow() {
        }

        public BucketedStatsRow(String period, long total, long totalValue) {
            this.period = period;
            this.total = total;
            this.totalValue = totalValue;
        }
    }
}

