package laughing.man.commits.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates benchmark performance plots from JMH outputs.
 */
public final class BenchmarkMetricsPlotGenerator {

    private static final String DEFAULT_CORE_RESULTS = "target/benchmarks.json";
    private static final String DEFAULT_CORE_THRESHOLDS = "benchmarks/thresholds.json";
    private static final String DEFAULT_CHART_RESULTS = "target/benchmarks/charts/chart-benchmarks.json";
    private static final String DEFAULT_CHART_THRESHOLDS = "benchmarks/chart-thresholds.json";
    private static final String DEFAULT_OUTPUT_DIR = "target/benchmarks/charts/images";
    private static final String DEFAULT_NORMALIZED_CSV = "target/benchmarks/charts/metrics-normalized.csv";
    private static final String DEFAULT_INDEX = "target/benchmarks/charts/images/INDEX.txt";

    private BenchmarkMetricsPlotGenerator() {
    }

    public static void main(String[] args) throws Exception {
        BenchmarkMetricsPlotConfig config = BenchmarkMetricsPlotConfig.fromArgs(args);
        run(config);
    }

    public static List<Path> run(BenchmarkMetricsPlotConfig config) throws IOException {
        List<BenchmarkMetricRow> coreRows = BenchmarkMetricLoader.load(
                config.coreResultsPath, config.coreThresholdsPath, "core-suite");
        List<BenchmarkMetricRow> chartRows = BenchmarkMetricLoader.load(
                config.chartResultsPath, config.chartThresholdsPath, "chart-suite");
        List<BenchmarkMetricRow> allRows = BenchmarkMetricLoader.combine(coreRows, chartRows);

        BenchmarkMetricCsvExporter.write(allRows, config.normalizedCsvPath);
        BenchmarkMetricsPlotter plotter = new BenchmarkMetricsPlotter();
        List<Path> generated = plotter.renderAll(allRows, config.outputDir, config.maxParityRatio);
        writeIndex(config.indexPath, generated, allRows.size());
        return generated;
    }

    private static void writeIndex(Path indexPath, List<Path> generated, int rowCount) throws IOException {
        Path parent = indexPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> lines = new ArrayList<>();
        lines.add("benchmark_plot_index");
        lines.add("row_count=" + rowCount);
        lines.add("image_count=" + generated.size());
        lines.add("images:");
        for (Path path : generated) {
            lines.add("- " + path.toString().replace('\\', '/'));
        }
        Files.write(indexPath, lines);
    }

    public static final class BenchmarkMetricsPlotConfig {
        private final Path coreResultsPath;
        private final Path coreThresholdsPath;
        private final Path chartResultsPath;
        private final Path chartThresholdsPath;
        private final Path outputDir;
        private final Path normalizedCsvPath;
        private final Path indexPath;
        private final double maxParityRatio;

        public BenchmarkMetricsPlotConfig(Path coreResultsPath,
                                          Path coreThresholdsPath,
                                          Path chartResultsPath,
                                          Path chartThresholdsPath,
                                          Path outputDir,
                                          Path normalizedCsvPath,
                                          Path indexPath,
                                          double maxParityRatio) {
            this.coreResultsPath = coreResultsPath;
            this.coreThresholdsPath = coreThresholdsPath;
            this.chartResultsPath = chartResultsPath;
            this.chartThresholdsPath = chartThresholdsPath;
            this.outputDir = outputDir;
            this.normalizedCsvPath = normalizedCsvPath;
            this.indexPath = indexPath;
            this.maxParityRatio = maxParityRatio;
        }

        static BenchmarkMetricsPlotConfig fromArgs(String[] args) {
            Path coreResults = args.length > 0 ? Paths.get(args[0]) : Paths.get(DEFAULT_CORE_RESULTS);
            Path coreThresholds = args.length > 1 ? Paths.get(args[1]) : Paths.get(DEFAULT_CORE_THRESHOLDS);
            Path chartResults = args.length > 2 ? Paths.get(args[2]) : Paths.get(DEFAULT_CHART_RESULTS);
            Path chartThresholds = args.length > 3 ? Paths.get(args[3]) : Paths.get(DEFAULT_CHART_THRESHOLDS);
            Path outputDir = args.length > 4 ? Paths.get(args[4]) : Paths.get(DEFAULT_OUTPUT_DIR);
            double maxParityRatio = args.length > 5 ? Double.parseDouble(args[5]) : 1.75d;
            return new BenchmarkMetricsPlotConfig(
                    coreResults,
                    coreThresholds,
                    chartResults,
                    chartThresholds,
                    outputDir,
                    Paths.get(DEFAULT_NORMALIZED_CSV),
                    Paths.get(DEFAULT_INDEX),
                    maxParityRatio);
        }
    }
}

