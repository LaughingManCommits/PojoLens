package laughing.man.commits.benchmark;

import laughing.man.commits.testing.FluentSqlLikeParity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;


public class BenchmarkMetricQueriesParityTest {

    @Test
    public void fluentAndSqlLikeShouldReturnSameRowsForChartTypeAndSizeFilter() throws Exception {
        List<BenchmarkMetricRow> rows = loadFixtureRows();
        List<BenchmarkMetricRow> fluent = BenchmarkMetricQueries.fluentByChartTypeAndMinSize(rows, "BAR", 1000);
        List<BenchmarkMetricRow> sqlLike = BenchmarkMetricQueries.sqlLikeByChartTypeAndMinSize(rows, "BAR", 1000);
        FluentSqlLikeParity.assertOrderedEquals(fluent, sqlLike, row -> row.benchmarkKey);
    }

    @Test
    public void fluentAndSqlLikeShouldReturnSameFailures() throws Exception {
        List<BenchmarkMetricRow> rows = loadFixtureRows();
        List<BenchmarkMetricRow> fluent = BenchmarkMetricQueries.fluentFailures(rows, 10);
        List<BenchmarkMetricRow> sqlLike = BenchmarkMetricQueries.sqlLikeFailures(rows, 10);
        FluentSqlLikeParity.assertOrderedEquals(fluent, sqlLike, row -> row.benchmarkKey);
    }

    @Test
    public void fluentAndSqlLikeShouldReturnSameFamilyAndStageRows() throws Exception {
        List<BenchmarkMetricRow> rows = loadFixtureRows();
        List<BenchmarkMetricRow> fluent = BenchmarkMetricQueries.fluentByFamilyAndStage(rows, "FLUENT", "MAPPING");
        List<BenchmarkMetricRow> sqlLike = BenchmarkMetricQueries.sqlLikeByFamilyAndStage(rows, "FLUENT", "MAPPING");
        FluentSqlLikeParity.assertOrderedEquals(fluent, sqlLike, row -> row.benchmarkKey);
    }

    private static List<BenchmarkMetricRow> loadFixtureRows() throws Exception {
        return BenchmarkMetricLoader.load(
                Path.of("..", "src", "test", "resources", "fixtures", "benchmark", "metrics-results.json"),
                Path.of("..", "src", "test", "resources", "fixtures", "benchmark", "metrics-thresholds.json"),
                "chart-suite");
    }
}
