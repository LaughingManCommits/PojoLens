package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLensCsv;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CsvLoadJmhBenchmark {

    @Benchmark
    public List<CsvEmployeeRow> csvTypedLoad(TypedLoadState state) {
        return PojoLensCsv.read(state.csv, CsvEmployeeRow.class);
    }

    @Benchmark
    public List<CsvMultilineRow> csvTypedLoadMultiline(MultilineLoadState state) {
        return PojoLensCsv.read(state.csv, CsvMultilineRow.class);
    }

    @State(Scope.Thread)
    public static class TypedLoadState {

        @Param({"1000", "10000"})
        public int size;

        private Path csv;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            csv = Files.createTempFile("pojolens-csv-load-", ".csv");
            Files.writeString(csv, buildTypedCsv(size));

            PojoLensCsv.read(csv, CsvEmployeeRow.class);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            Files.deleteIfExists(csv);
        }
    }

    @State(Scope.Thread)
    public static class MultilineLoadState {

        @Param({"1000", "10000"})
        public int size;

        private Path csv;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            csv = Files.createTempFile("pojolens-csv-multiline-", ".csv");
            Files.writeString(csv, buildMultilineCsv(size));

            PojoLensCsv.read(csv, CsvMultilineRow.class);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            Files.deleteIfExists(csv);
        }
    }

    private static String buildTypedCsv(int size) {
        StringBuilder csv = new StringBuilder(Math.max(256, size * 64));
        csv.append("id,name,department,salary,active,hireDate\n");
        for (int i = 0; i < size; i++) {
            CsvDepartment department = CsvDepartment.values()[
                    BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1201L, i, 4)
            ];
            csv.append(i + 1)
                    .append(",employee-")
                    .append(i)
                    .append(",")
                    .append(department.name())
                    .append(",")
                    .append(70_000 + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1202L, i, 90_000))
                    .append(",")
                    .append((i & 1) == 0)
                    .append(",")
                    .append(LocalDate.of(
                            2024,
                            BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1203L, i, 12) + 1,
                            BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1204L, i, 28) + 1
                    ))
                    .append('\n');
        }
        return csv.toString();
    }

    private static String buildMultilineCsv(int size) {
        StringBuilder csv = new StringBuilder(Math.max(256, size * 72));
        csv.append("id,notes,active\n");
        for (int i = 0; i < size; i++) {
            csv.append(i + 1)
                    .append(",\"first line ")
                    .append(i)
                    .append("\nsecond line ")
                    .append(BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1301L, i, 32))
                    .append("\",")
                    .append((i & 1) == 0)
                    .append('\n');
        }
        return csv.toString();
    }

    public static class CsvEmployeeRow {
        int id;
        String name;
        CsvDepartment department;
        int salary;
        boolean active;
        LocalDate hireDate;

        public CsvEmployeeRow() {
        }
    }

    public static class CsvMultilineRow {
        int id;
        String notes;
        boolean active;

        public CsvMultilineRow() {
        }
    }

    public enum CsvDepartment {
        ENGINEERING,
        FINANCE,
        OPERATIONS,
        SALES
    }
}
