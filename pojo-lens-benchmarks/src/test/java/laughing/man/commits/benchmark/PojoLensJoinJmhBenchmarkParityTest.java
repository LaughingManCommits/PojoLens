package laughing.man.commits.benchmark;

import laughing.man.commits.testing.FluentSqlLikeParity;
import org.junit.jupiter.api.Test;

import java.util.List;

public class PojoLensJoinJmhBenchmarkParityTest {

    @Test
    public void computedFieldJoinBaselineShouldMatchPojoLensAt1k() throws Exception {
        assertComputedFieldJoinParity(1000);
    }

    @Test
    public void computedFieldJoinBaselineShouldMatchPojoLensAt10k() throws Exception {
        assertComputedFieldJoinParity(10000);
    }

    private static void assertComputedFieldJoinParity(int size) throws Exception {
        PojoLensJoinJmhBenchmark benchmark = new PojoLensJoinJmhBenchmark();
        benchmark.size = size;
        benchmark.setup();

        List<PojoLensJoinJmhBenchmark.ComputedJoinProjection> pojoLensRows = benchmark.pojoLensJoinLeftComputedField();
        List<PojoLensJoinJmhBenchmark.ComputedJoinProjection> manualRows = benchmark.manualHashJoinLeftComputedField();

        FluentSqlLikeParity.assertOrderedEquals(
                pojoLensRows,
                manualRows,
                row -> row.name + ":" + row.totalComp
        );
    }
}
