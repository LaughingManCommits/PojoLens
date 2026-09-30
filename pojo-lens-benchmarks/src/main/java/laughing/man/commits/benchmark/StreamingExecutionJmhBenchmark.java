package laughing.man.commits.benchmark;

import laughing.man.commits.internal.FluentEngine;
import laughing.man.commits.PojoLensSql;
import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.sqllike.JoinBindings;
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
    private static final TypedField<BenchmarkFoo, String> STRING_FIELD = TypedField.of("stringField", String.class);
    private static final TypedField<BenchmarkFoo, Integer> INTEGER_FIELD = TypedField.of("integerField", Integer.class);

    @Param({"1000", "10000"})
    public int size;

    private List<BenchmarkFoo> source;
    private Filter fluentFilter;
    private SqlLikeQuery sqlLikeFilterQuery;
    private SqlLikeQuery sqlLikeOrFilterQuery;
    private TypedQuery<BenchmarkFoo> typedFilterQuery;

    @Setup
    public void setup() {
        source = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String value = "dept" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1211L, i, 12);
            Date date = new Date(BenchmarkProfiles.STATS_BASE_EPOCH_MILLIS + (i * 86_400_000L));
            int integerField = BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1212L, i, 1000);
            source.add(new BenchmarkFoo(value, date, integerField));
        }
        fluentFilter = FluentEngine.newQueryBuilder(source)
                .addRule("integerField", 100, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("stringField")
                .addField("integerField")
                .initFilter();
        sqlLikeFilterQuery = PojoLensSql.parse(
                "select stringField, integerField "
                        + "where integerField >= 100"
        );
        // OR predicates lower to rule groups, which stream lazily since WP-33.
        sqlLikeOrFilterQuery = PojoLensSql.parse(
                "select stringField, integerField "
                        + "where integerField >= 100 or stringField = 'dept0'"
        );
        typedFilterQuery = TypedQuery.from(BenchmarkFoo.class)
                .select(STRING_FIELD, INTEGER_FIELD)
                .where(INTEGER_FIELD.gte(100));
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

    @Benchmark
    public long sqlLikeOrFilterListMaterialized() {
        return checksumRows(sqlLikeOrFilterQuery.filter(source, StreamProjectionRow.class), PAGE_WINDOW);
    }

    @Benchmark
    public long sqlLikeOrFilterStreamLazy() {
        try (Stream<StreamProjectionRow> rows = sqlLikeOrFilterQuery.stream(source, StreamProjectionRow.class).limit(PAGE_WINDOW)) {
            return checksumRows(rows);
        }
    }

    @Benchmark
    public long typedFilterListMaterialized() {
        return checksumRows(typedFilterQuery.filter(source, StreamProjectionRow.class), PAGE_WINDOW);
    }

    @Benchmark
    public long typedFilterStreamLazy() {
        try (Stream<StreamProjectionRow> rows = typedFilterQuery
                .stream(source, JoinBindings.empty(), StreamProjectionRow.class)
                .limit(PAGE_WINDOW)) {
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

