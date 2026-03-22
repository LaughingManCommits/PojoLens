package laughing.man.commits.benchmark;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BenchmarkMetricsPlotGeneratorTest {

    @Test
    public void shouldGenerateBenchmarkPerformancePlotsAndIndex() throws Exception {
        Path outputDir = Path.of("target", "benchmarks", "charts", "images-fixture");
        deleteDirectory(outputDir);

        List<Path> generated = BenchmarkMetricsPlotGenerator.run(
                new BenchmarkMetricsPlotGenerator.BenchmarkMetricsPlotConfig(
                        fixture("core-results-empty.json"),
                        fixture("core-thresholds-empty.json"),
                        fixture("metrics-results.json"),
                        fixture("metrics-thresholds.json"),
                        outputDir,
                        Path.of("target", "benchmarks", "charts", "metrics-normalized-fixture.csv"),
                        outputDir.resolve("INDEX.txt"),
                        1.75d));

        assertFalse(generated.isEmpty());
        assertTrue(Files.exists(outputDir.resolve("delta-overview.png")));
        assertTrue(Files.exists(outputDir.resolve("INDEX.txt")));
        assertTrue(Files.exists(Path.of("target", "benchmarks", "charts", "metrics-normalized-fixture.csv")));
    }

    private static Path fixture(String name) {
        return Path.of("..", "src", "test", "resources", "fixtures", "benchmark", name);
    }

    private static void deleteDirectory(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walk(dir)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
    }
}

