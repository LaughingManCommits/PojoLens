package laughing.man.commits.benchmark;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BenchmarkMetricLoaderTest {

    @Test
    public void shouldLoadMetricRowsWithDerivedFieldsAndThresholdStatus() throws Exception {
        List<BenchmarkMetricRow> rows = BenchmarkMetricLoader.load(
                fixture("metrics-results.json"),
                fixture("metrics-thresholds.json"),
                "chart-suite");

        assertEquals(7, rows.size());
        BenchmarkMetricRow first = rows.get(0);
        assertNotNull(first.benchmarkKey);
        assertNotNull(first.methodName);
        assertNotNull(first.family);
        assertNotNull(first.benchmarkCategory);
        assertNotNull(first.metricStage);

        BenchmarkMetricRow failed = findByMethod(rows, "sqlLikeBarMapping");
        assertEquals("SQLLIKE", failed.family);
        assertEquals("BAR", failed.chartType);
        assertEquals("MAPPING", failed.metricStage);
        assertEquals("FAIL", failed.status);
        assertFalse(failed.pass);
        assertTrue(failed.delta > 0d);

        BenchmarkMetricRow passed = findByMethod(rows, "fluentLineMapping");
        assertEquals("PASS", passed.status);
        assertTrue(passed.pass);

        BenchmarkMetricRow export = findByMethod(rows, "scatterPayloadJsonExport");
        assertEquals("EXPORT", export.metricStage);

        BenchmarkMetricRow streams = findByMethod(rows, "streamsFilterProjection");
        assertEquals("STREAMS", streams.family);
        assertEquals("FILTER", streams.benchmarkCategory);
        assertEquals("PASS", streams.status);
    }

    private static BenchmarkMetricRow findByMethod(List<BenchmarkMetricRow> rows, String methodName) {
        for (BenchmarkMetricRow row : rows) {
            if (methodName.equals(row.methodName)) {
                return row;
            }
        }
        throw new IllegalStateException("Missing row for method: " + methodName);
    }

    private static Path fixture(String name) {
        return Path.of("src", "test", "resources", "fixtures", "benchmark", name);
    }
}

