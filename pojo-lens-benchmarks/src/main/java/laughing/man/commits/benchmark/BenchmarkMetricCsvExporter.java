package laughing.man.commits.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Writes normalized benchmark rows to CSV.
 */
public final class BenchmarkMetricCsvExporter {

    private BenchmarkMetricCsvExporter() {
    }

    public static void write(List<BenchmarkMetricRow> rows, Path outputCsv) throws IOException {
        Path parent = outputCsv.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<BenchmarkMetricRow> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(r -> r.benchmarkKey));

        List<String> lines = new ArrayList<>();
        lines.add("benchmark_key,benchmark_name,method_name,source,family,benchmark_category,chart_type,metric_stage,size,score,threshold,delta,ratio_to_threshold,status,pass");
        for (BenchmarkMetricRow row : sorted) {
            lines.add(String.format(Locale.ROOT,
                    "%s,%s,%s,%s,%s,%s,%s,%s,%d,%.6f,%.6f,%.6f,%.6f,%s,%s",
                    csv(row.benchmarkKey),
                    csv(row.benchmarkName),
                    csv(row.methodName),
                    csv(row.source),
                    csv(row.family),
                    csv(row.benchmarkCategory),
                    csv(row.chartType),
                    csv(row.metricStage),
                    row.size,
                    row.score,
                    row.threshold,
                    row.delta,
                    row.ratioToThreshold,
                    csv(row.status),
                    row.pass));
        }
        Files.write(outputCsv, lines);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

