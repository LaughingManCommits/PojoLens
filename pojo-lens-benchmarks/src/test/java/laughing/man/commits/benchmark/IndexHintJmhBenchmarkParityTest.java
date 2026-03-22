package laughing.man.commits.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IndexHintJmhBenchmarkParityTest {

    @Test
    public void indexedAndScanBenchmarkPathsShouldMatchAt1k() {
        assertParity(1000);
    }

    @Test
    public void indexedAndScanBenchmarkPathsShouldMatchAt10k() {
        assertParity(10000);
    }

    private static void assertParity(int size) {
        IndexHintJmhBenchmark benchmark = new IndexHintJmhBenchmark();
        benchmark.size = size;
        benchmark.setup();

        long scanChecksum = benchmark.fluentFilterScan();
        long indexedChecksum = benchmark.fluentFilterIndexed();
        assertEquals(scanChecksum, indexedChecksum);
    }
}
