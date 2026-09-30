package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLensFiles;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * File-boundary loading from a {@code Path} next to the equivalent in-memory
 * {@code Reader} / {@code InputStream} source, for CSV and JSONL.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class StreamLoadJmhBenchmark {

    @Param({"1000", "10000"})
    public int size;

    private String csvText;
    private byte[] jsonlBytes;
    private Path csvFile;
    private Path jsonlFile;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        StringBuilder csv = new StringBuilder(Math.max(256, size * 40));
        StringBuilder jsonl = new StringBuilder(Math.max(256, size * 70));
        csv.append("id,name,salary,active\n");
        for (int i = 0; i < size; i++) {
            int salary = 50_000 + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 2301L, i, 100_000);
            boolean active = (i & 1) == 0;
            csv.append(i + 1).append(",employee-").append(i).append(',').append(salary).append(',').append(active).append('\n');
            jsonl.append("{\"id\":").append(i + 1)
                    .append(",\"name\":\"employee-").append(i)
                    .append("\",\"salary\":").append(salary)
                    .append(",\"active\":").append(active).append("}\n");
        }
        csvText = csv.toString();
        jsonlBytes = jsonl.toString().getBytes(StandardCharsets.UTF_8);
        csvFile = Files.createTempFile("pojolens-stream-load-", ".csv");
        Files.writeString(csvFile, csvText);
        jsonlFile = Files.createTempFile("pojolens-stream-load-", ".jsonl");
        Files.write(jsonlFile, jsonlBytes);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        Files.deleteIfExists(csvFile);
        Files.deleteIfExists(jsonlFile);
    }

    @Benchmark
    public long csvFromPath() {
        return checksum(PojoLensFiles.csv(csvFile, LoadRow.class));
    }

    @Benchmark
    public long csvFromReader() {
        return checksum(PojoLensFiles.csv(new StringReader(csvText), LoadRow.class));
    }

    @Benchmark
    public long jsonlFromPath() {
        return checksum(PojoLensFiles.jsonl(jsonlFile, LoadRow.class));
    }

    @Benchmark
    public long jsonlFromInputStream() {
        return checksum(PojoLensFiles.jsonl(new ByteArrayInputStream(jsonlBytes), LoadRow.class));
    }

    static long checksum(List<LoadRow> rows) {
        long checksum = 0L;
        for (LoadRow row : rows) {
            checksum += row.id + (long) row.salary + (row.active ? 1 : 0) + row.name.length();
        }
        return checksum;
    }

    public static class LoadRow {
        public int id;
        public String name;
        public int salary;
        public boolean active;

        public LoadRow() {
        }
    }
}
