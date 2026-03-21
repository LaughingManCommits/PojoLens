package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLens;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.filter.Filter;
import laughing.man.commits.sqllike.SqlLikeQuery;
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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class StreamingExecutionJmhBenchmark {

    private static final int PAGE_WINDOW = 50;

    @Param({"1000", "10000"})
    public int size;

    private List<BenchmarkFoo> source;
    private Filter fluentFilter;
    private SqlLikeQuery sqlLikeFilterQuery;

    @Setup
    public void setup() {
        source = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String value = "dept" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1211L, i, 12);
            Date date = new Date(BenchmarkProfiles.STATS_BASE_EPOCH_MILLIS + (i * 86_400_000L));
            int integerField = BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1212L, i, 1000);
            source.add(new BenchmarkFoo(value, date, integerField));
        }
        fluentFilter = PojoLens.newQueryBuilder(source)
                .addRule("integerField", 100, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("stringField")
                .addField("integerField")
                .initFilter();
        sqlLikeFilterQuery = PojoLens.parse(
                "select stringField, integerField "
                        + "where integerField >= 100"
        );
    }

    @Benchmark
    public long fluentFilterListMaterialized() {
        return checksumRows(fluentFilter.filter(StreamProjectionRow.class), PAGE_WINDOW);
    }

    @Benchmark
    public long fluentFilterStreamLazy() {
        try (Stream<StreamProjectionRow> rows = fluentFilter.stream(StreamProjectionRow.class).limit(PAGE_WINDOW)) {
            return checksumRows(rows);
        }
    }

    @Benchmark
    public long sqlLikeFilterListMaterialized() {
        return checksumRows(sqlLikeFilterQuery.filter(source, StreamProjectionRow.class), PAGE_WINDOW);
    }

    @Benchmark
    public long sqlLikeFilterStreamLazy() {
        try (Stream<StreamProjectionRow> rows = sqlLikeFilterQuery.stream(source, StreamProjectionRow.class).limit(PAGE_WINDOW)) {
            return checksumRows(rows);
        }
    }

    private static long checksumRows(List<StreamProjectionRow> rows, int maxRows) {
        long checksum = 0L;
        int limit = Math.min(rows.size(), Math.max(0, maxRows));
        for (int i = 0; i < limit; i++) {
            checksum += rowChecksum(rows.get(i));
        }
        return checksum;
    }

    private static long checksumRows(Stream<StreamProjectionRow> rows) {
        long checksum = 0L;
        Iterator<StreamProjectionRow> iterator = rows.iterator();
        while (iterator.hasNext()) {
            checksum += rowChecksum(iterator.next());
        }
        return checksum;
    }

    private static long rowChecksum(StreamProjectionRow row) {
        if (row == null) {
            return 0L;
        }
        long sum = row.integerField;
        if (row.stringField != null) {
            sum += row.stringField.hashCode();
        }
        return sum;
    }

    public static class StreamProjectionRow {
        String stringField;
        int integerField;

        public StreamProjectionRow() {
        }
    }
}
