package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLensSql;
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
 * Record sources and record result classes (canonical-constructor projection) next to
 * the equivalent mutable POJOs, for a filter and a select projection.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class RecordProjectionJmhBenchmark {

    static final String FILTER_QUERY = "where amount > 500";
    static final String SELECT_QUERY = "select region, amount where amount > 500";
    private static final String[] REGIONS = {"north", "south", "east", "west"};

    @Param({"1000", "10000"})
    public int size;

    private List<PojoSale> pojoRows;
    private List<RecordSale> recordRows;
    private SqlLikeQuery filterQuery;
    private SqlLikeQuery selectQuery;

    @Setup
    public void setup() {
        pojoRows = new ArrayList<>(size);
        recordRows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String region = REGIONS[BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 2401L, i, REGIONS.length)];
            int amount = BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 2402L, i, 1_000);
            PojoSale pojo = new PojoSale();
            pojo.id = i + 1L;
            pojo.region = region;
            pojo.amount = amount;
            pojoRows.add(pojo);
            recordRows.add(new RecordSale(i + 1L, region, amount));
        }
        filterQuery = PojoLensSql.parse(FILTER_QUERY);
        selectQuery = PojoLensSql.parse(SELECT_QUERY);
    }

    @Benchmark
    public long pojoFilter() {
        long checksum = 0L;
        for (PojoSale row : filterQuery.filter(pojoRows, PojoSale.class)) {
            checksum += row.id + row.amount;
        }
        return checksum;
    }

    @Benchmark
    public long recordFilter() {
        long checksum = 0L;
        for (RecordSale row : filterQuery.filter(recordRows, RecordSale.class)) {
            checksum += row.id() + row.amount();
        }
        return checksum;
    }

    @Benchmark
    public long pojoSelectProjection() {
        long checksum = 0L;
        for (PojoSummary row : selectQuery.filter(pojoRows, PojoSummary.class)) {
            checksum += row.amount + row.region.length();
        }
        return checksum;
    }

    @Benchmark
    public long recordSelectProjection() {
        long checksum = 0L;
        for (RecordSummary row : selectQuery.filter(recordRows, RecordSummary.class)) {
            checksum += row.amount() + row.region().length();
        }
        return checksum;
    }

    public static class PojoSale {
        public long id;
        public String region;
        public int amount;

        public PojoSale() {
        }
    }

    public static class PojoSummary {
        public String region;
        public int amount;

        public PojoSummary() {
        }
    }

    public record RecordSale(long id, String region, int amount) {
    }

    public record RecordSummary(String region, int amount) {
    }
}
