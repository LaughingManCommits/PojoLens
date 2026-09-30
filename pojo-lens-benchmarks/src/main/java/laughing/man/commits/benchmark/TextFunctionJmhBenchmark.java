package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLensSql;
import laughing.man.commits.computed.ComputedFieldRegistry;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * WP-29 text-function cost: the typed expression lane ({@code lower(...)} evaluated per
 * row), the same match through the regex {@code ILIKE} path, and a {@code String}
 * computed field materialized before filtering.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class TextFunctionJmhBenchmark {

    private static final String[] DEPARTMENTS = {"Engineering", "ENGINEERING", "engineering", "Finance", "Sales"};

    @Param({"1000", "10000"})
    public int size;

    private List<TextRow> rows;
    private SqlLikeQuery lowerEquals;
    private SqlLikeQuery ilike;
    private SqlLikeQuery computedField;

    @Setup
    public void setup() {
        rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            TextRow row = new TextRow();
            row.id = i + 1;
            row.name = "Employee-" + i;
            row.department = DEPARTMENTS[BenchmarkProfiles.deterministicInt(
                    BenchmarkProfiles.DATA_SEED + 2901L, i, DEPARTMENTS.length)];
            rows.add(row);
        }
        lowerEquals = PojoLensSql.parse("where lower(department) = 'engineering'");
        ilike = PojoLensSql.parse("where department ilike 'engineering'");
        computedField = PojoLensSql.parse("where deptKey = 'engineering'")
                .computedFields(ComputedFieldRegistry.builder()
                        .add("deptKey", "lower(department)", String.class)
                        .build());
    }

    /** {@code lower(department) = '...'}: the expression is evaluated for every row. */
    @Benchmark
    public long sqlLikeLowerEquals() {
        return checksum(lowerEquals.filter(rows, TextRow.class));
    }

    /** {@code department ILIKE '...'}: the same match through the case-insensitive regex. */
    @Benchmark
    public long sqlLikeIlike() {
        return checksum(ilike.filter(rows, TextRow.class));
    }

    /** A {@code String} computed field ({@code lower(department)}) filtered by equality. */
    @Benchmark
    public long sqlLikeTextComputedField() {
        return checksum(computedField.filter(rows, TextRow.class));
    }

    List<TextRow> rows() {
        return rows;
    }

    static long checksum(List<TextRow> rows) {
        long checksum = 0L;
        for (TextRow row : rows) {
            checksum += row.id;
        }
        return checksum;
    }

    public static class TextRow {
        public int id;
        public String name;
        public String department;

        public TextRow() {
        }
    }
}
