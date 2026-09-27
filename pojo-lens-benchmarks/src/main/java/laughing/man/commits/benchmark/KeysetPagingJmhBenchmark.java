package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLensSql;
import laughing.man.commits.sqllike.PageResult;
import laughing.man.commits.sqllike.SqlLikeCursor;
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
 * Keyset page cost for the cursor placements: WHERE (plain ORDER BY), null-aware WHERE
 * (nullable sort key), HAVING (aggregate alias), and the reversed previous-page window.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class KeysetPagingJmhBenchmark {

    static final String PLAIN_QUERY = "order by salary desc, id desc limit 50";
    static final String NULLABLE_QUERY = "order by score desc, id desc limit 50";
    static final String AGGREGATE_QUERY = "select dept, count(*) as c group by dept order by c desc, dept asc limit 5";
    private static final int DEPARTMENTS = 20;

    @Param({"1000", "10000"})
    public int size;

    private List<PagedRow> rows;
    private SqlLikeQuery plainAfter;
    private SqlLikeQuery plainBefore;
    private SqlLikeQuery nullableAfter;
    private SqlLikeQuery aggregateAfter;

    @Setup
    public void setup() {
        rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            PagedRow row = new PagedRow();
            row.id = i + 1;
            row.dept = "dept-" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 2201L, i, DEPARTMENTS);
            row.salary = 50_000 + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 2202L, i, 100_000);
            row.score = i % 7 == 0 ? null : BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 2203L, i, 1_000);
            rows.add(row);
        }
        SqlLikeCursor plainCursor = firstPageCursor(PLAIN_QUERY, PagedRow.class);
        plainAfter = PojoLensSql.parse(PLAIN_QUERY).keysetAfter(plainCursor);
        SqlLikeCursor secondPageCursor = plainAfter.filterPage(rows, PagedRow.class).nextCursor().orElseThrow();
        plainBefore = PojoLensSql.parse(PLAIN_QUERY).keysetBefore(secondPageCursor);
        nullableAfter = PojoLensSql.parse(NULLABLE_QUERY).keysetAfter(firstPageCursor(NULLABLE_QUERY, PagedRow.class));
        aggregateAfter = PojoLensSql.parse(AGGREGATE_QUERY).keysetAfter(firstPageCursor(AGGREGATE_QUERY, DeptCount.class));
    }

    /** Second page, cursor predicate in WHERE. */
    @Benchmark
    public long plainNextPage() {
        return checksum(plainAfter.filterPage(rows, PagedRow.class).rows());
    }

    /** The 50 rows before page 2's last row: reversed ORDER BY, LIMIT, then flipped back. */
    @Benchmark
    public long plainPreviousPage() {
        return checksum(plainBefore.filter(rows, PagedRow.class));
    }

    /** Second page over a nullable sort key (adds {@code OR score = null} branches). */
    @Benchmark
    public long nullableNextPage() {
        return checksum(nullableAfter.filterPage(rows, PagedRow.class).rows());
    }

    /** Second page ordered by an aggregate alias, cursor predicate in HAVING. */
    @Benchmark
    public long aggregateAliasNextPage() {
        long checksum = 0L;
        for (DeptCount row : aggregateAfter.filterPage(rows, DeptCount.class).rows()) {
            checksum = checksum * 31 + row.dept.hashCode() + row.c;
        }
        return checksum;
    }

    List<PagedRow> rows() {
        return rows;
    }

    private <T> SqlLikeCursor firstPageCursor(String query, Class<T> type) {
        PageResult<T> first = PojoLensSql.parse(query).filterPage(rows, type);
        return first.nextCursor().orElseThrow();
    }

    static long checksum(List<PagedRow> rows) {
        long checksum = 0L;
        for (PagedRow row : rows) {
            checksum = checksum * 31 + row.id;
        }
        return checksum;
    }

    public static class PagedRow {
        public int id;
        public String dept;
        public int salary;
        public Integer score;

        public PagedRow() {
        }
    }

    public static class DeptCount {
        public String dept;
        public long c;

        public DeptCount() {
        }
    }
}
