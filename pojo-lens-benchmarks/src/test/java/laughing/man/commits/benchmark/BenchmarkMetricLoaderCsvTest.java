package laughing.man.commits.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkMetricLoaderCsvTest {

    @Test
    void shouldClassifyCsvBenchmarksAsLoadWorkloads(@TempDir Path tempDir) throws IOException {
        Path results = tempDir.resolve("csv-results.json");
        Files.writeString(
                results,
                """
                        [
                          {
                            "benchmark": "laughing.man.commits.benchmark.CsvLoadJmhBenchmark.csvTypedLoad",
                            "params": { "size": "1000" },
                            "primaryMetric": { "score": 12.5 }
                          }
                        ]
                        """
        );
        Path thresholds = tempDir.resolve("csv-thresholds.json");
        Files.writeString(
                thresholds,
                """
                        {
                          "laughing.man.commits.benchmark.CsvLoadJmhBenchmark.csvTypedLoad|size=1000": 20.0
                        }
                        """
        );

        List<BenchmarkMetricRow> rows = BenchmarkMetricLoader.load(results, thresholds, "core-suite");

        assertEquals(1, rows.size());
        assertEquals("CSV", rows.get(0).family);
        assertEquals("LOAD", rows.get(0).benchmarkCategory);
        assertEquals("LOAD_PIPELINE", rows.get(0).metricStage);
        assertTrue(rows.get(0).pass);
    }
}
