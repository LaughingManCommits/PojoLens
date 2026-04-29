package laughing.man.commits.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlLikePushdownBenchmarkParityTest {

    @Test
    void pushedCandidateMatchesPureInMemoryCandidate() {
        SqlLikePipelineJmhBenchmark benchmark = benchmark();

        List<BenchmarkFoo> inMemory = benchmark.pureInMemoryPushdownCandidate();
        List<BenchmarkFoo> pushed = benchmark.pushedFirstPhaseCandidate();

        assertEquals(inMemory.size(), pushed.size());
        assertEquals(
                inMemory.stream().map(SqlLikePushdownBenchmarkParityTest::fooKey).toList(),
                pushed.stream().map(SqlLikePushdownBenchmarkParityTest::fooKey).toList()
        );
    }

    @Test
    void splitCandidateMatchesPureInMemoryCandidate() {
        SqlLikePipelineJmhBenchmark benchmark = benchmark();

        List<SqlLikePipelineJmhBenchmark.BenchmarkGroupRow> inMemory =
                benchmark.pureInMemorySplitCandidate();
        List<SqlLikePipelineJmhBenchmark.BenchmarkGroupRow> split =
                benchmark.splitPushdownCandidate();

        assertEquals(
                inMemory.stream().map(SqlLikePushdownBenchmarkParityTest::groupKey).toList(),
                split.stream().map(SqlLikePushdownBenchmarkParityTest::groupKey).toList()
        );
    }

    private static SqlLikePipelineJmhBenchmark benchmark() {
        SqlLikePipelineJmhBenchmark benchmark = new SqlLikePipelineJmhBenchmark();
        benchmark.size = 1000;
        benchmark.setup();
        return benchmark;
    }

    private static String fooKey(BenchmarkFoo row) {
        return row.getStringField() + ':' + row.getIntegerField();
    }

    private static String groupKey(SqlLikePipelineJmhBenchmark.BenchmarkGroupRow row) {
        return row.stringField + ':' + row.total;
    }
}
