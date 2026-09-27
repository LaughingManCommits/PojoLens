package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLensSql;
import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.sqllike.SqlLikeQuery;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Date/time predicate cost under the default precision rules: text literals compared at
 * their written precision (day, second, millisecond) and typed values compared exactly.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class TemporalFilterJmhBenchmark {

    private static final long STEP_MILLIS = 37L * 60_000L;
    private static final TypedField<TemporalRow, Instant> CREATED_AT = TypedField.of("createdAt", Instant.class);
    private static final DateTimeFormatter SECOND_TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MILLIS_UTC_TEXT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @Param({"1000", "10000"})
    public int size;

    private List<TemporalRow> rows;
    private SqlLikeQuery dayLiteral;
    private SqlLikeQuery secondLiteral;
    private SqlLikeQuery millisLiteral;
    private TypedQuery<TemporalRow> typedInstant;

    @Setup
    public void setup() {
        rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            long epochMillis = BenchmarkProfiles.STATS_BASE_EPOCH_MILLIS + i * STEP_MILLIS
                    + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 2101L, i, 1000);
            TemporalRow row = new TemporalRow();
            row.id = i + 1;
            row.createdAt = Instant.ofEpochMilli(epochMillis);
            row.localAt = LocalDateTime.ofInstant(row.createdAt, ZoneOffset.UTC);
            rows.add(row);
        }
        TemporalRow middle = rows.get(size / 2);
        dayLiteral = PojoLensSql.parse("where localAt >= '" + middle.localAt.toLocalDate() + "'");
        // Fixed-width formats: toString() would drop ':00' seconds or a '.000' fraction and
        // silently change the literal's precision.
        secondLiteral = PojoLensSql.parse("where localAt > '" + SECOND_TEXT.format(middle.localAt) + "'");
        millisLiteral = PojoLensSql.parse("where createdAt > '" + MILLIS_UTC_TEXT.format(middle.createdAt) + "'");
        typedInstant = TypedQuery.from(TemporalRow.class).where(CREATED_AT.gt(middle.createdAt));
    }

    /** Day-precision local literal ({@code '2025-05-15'}) against a LocalDateTime field. */
    @Benchmark
    public long sqlLikeDayLiteral() {
        return checksum(dayLiteral.filter(rows, TemporalRow.class));
    }

    /** Second-precision literal written with a space ({@code '2025-05-15 10:00:00'}). */
    @Benchmark
    public long sqlLikeSecondLiteral() {
        return checksum(secondLiteral.filter(rows, TemporalRow.class));
    }

    /** Millisecond-precision zoned literal ({@code '...T10:00:00.123Z'}) against an Instant field. */
    @Benchmark
    public long sqlLikeMillisLiteral() {
        return checksum(millisLiteral.filter(rows, TemporalRow.class));
    }

    /** Typed Instant argument compared exactly (value vs value). */
    @Benchmark
    public long typedInstantValue() {
        return checksum(typedInstant.filter(rows));
    }

    List<TemporalRow> rows() {
        return rows;
    }

    static long checksum(List<TemporalRow> rows) {
        long checksum = 0L;
        for (TemporalRow row : rows) {
            checksum += row.id;
        }
        return checksum;
    }

    public static class TemporalRow {
        public int id;
        public Instant createdAt;
        public LocalDateTime localAt;

        public TemporalRow() {
        }
    }
}
