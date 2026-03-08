package laughing.man.commits.benchmark;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.style.Styler;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders benchmark metric charts using XChart.
 */
public final class BenchmarkMetricsPlotter {

    private static final List<String> CHART_TYPES = Arrays.asList("BAR", "LINE", "PIE", "AREA", "SCATTER");

    public List<Path> renderAll(List<BenchmarkMetricRow> rows, Path outputDir, double maxParityRatio) throws IOException {
        Files.createDirectories(outputDir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir, "*.png")) {
            for (Path path : stream) {
                Files.deleteIfExists(path);
            }
        }
        List<Path> outputs = new ArrayList<>();
        outputs.addAll(renderLatencyByChartType(rows, outputDir));
        outputs.addAll(renderParityRatioByChartType(rows, outputDir, maxParityRatio));
        outputs.add(renderDeltaOverview(rows, outputDir));
        return outputs;
    }

    private List<Path> renderLatencyByChartType(List<BenchmarkMetricRow> rows, Path outputDir) throws IOException {
        List<Path> outputs = new ArrayList<>();
        List<BenchmarkMetricRow> mappingRows = mappingRows(rows);
        for (String chartType : CHART_TYPES) {
            List<BenchmarkMetricRow> chartRows = new ArrayList<>();
            for (BenchmarkMetricRow row : mappingRows) {
                if (chartType.equals(row.chartType)) {
                    chartRows.add(row);
                }
            }
            if (chartRows.isEmpty()) {
                continue;
            }

            XYChart chart = new XYChartBuilder()
                    .title(chartType + " Mapping Latency")
                    .xAxisTitle("Dataset Size")
                    .yAxisTitle("ms/op")
                    .width(1100)
                    .height(700)
                    .build();
            chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideE);
            chart.getStyler().setMarkerSize(8);

            addSeries(chart, "fluent", chartRows, "FLUENT", false);
            addSeries(chart, "sqlLike", chartRows, "SQLLIKE", false);
            addSeries(chart, "fluent-threshold", chartRows, "FLUENT", true);
            addSeries(chart, "sqlLike-threshold", chartRows, "SQLLIKE", true);

            outputs.add(saveChart(chart, outputDir, "latency-" + chartType.toLowerCase(Locale.ROOT)));
        }
        return outputs;
    }

    private List<Path> renderParityRatioByChartType(List<BenchmarkMetricRow> rows,
                                                    Path outputDir,
                                                    double maxParityRatio) throws IOException {
        List<Path> outputs = new ArrayList<>();
        List<BenchmarkMetricRow> fluentRows = mappingRowsByFamily(rows, "FLUENT");
        List<BenchmarkMetricRow> sqlRows = mappingRowsByFamily(rows, "SQLLIKE");

        for (String chartType : CHART_TYPES) {
            Map<Integer, Double> fluentBySize = scoreBySize(fluentRows, chartType);
            Map<Integer, Double> sqlBySize = scoreBySize(sqlRows, chartType);
            List<Integer> sizes = new ArrayList<>(fluentBySize.keySet());
            sizes.removeIf(size -> !sqlBySize.containsKey(size));
            sizes.sort(Comparator.naturalOrder());
            if (sizes.isEmpty()) {
                continue;
            }

            List<Integer> x = new ArrayList<>();
            List<Double> ratio = new ArrayList<>();
            List<Double> max = new ArrayList<>();
            for (Integer size : sizes) {
                double fluent = fluentBySize.get(size);
                double sql = sqlBySize.get(size);
                if (fluent <= 0d) {
                    continue;
                }
                x.add(size);
                ratio.add(sql / fluent);
                max.add(maxParityRatio);
            }
            if (x.isEmpty()) {
                continue;
            }

            XYChart chart = new XYChartBuilder()
                    .title(chartType + " SQLLike/Fluent Ratio")
                    .xAxisTitle("Dataset Size")
                    .yAxisTitle("ratio")
                    .width(1100)
                    .height(700)
                    .build();
            chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideE);
            chart.addSeries("ratio", x, ratio).setLineColor(new Color(0x1f, 0x77, 0xb4));
            chart.addSeries("max-ratio", x, max).setLineColor(new Color(0xd6, 0x27, 0x28));

            outputs.add(saveChart(chart, outputDir, "ratio-" + chartType.toLowerCase(Locale.ROOT)));
        }
        return outputs;
    }

    private static List<BenchmarkMetricRow> mappingRows(List<BenchmarkMetricRow> rows) {
        List<BenchmarkMetricRow> filtered = new ArrayList<>();
        for (BenchmarkMetricRow row : rows) {
            if (!"MAPPING".equals(row.metricStage)) {
                continue;
            }
            if (!"FLUENT".equals(row.family) && !"SQLLIKE".equals(row.family)) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    private static List<BenchmarkMetricRow> mappingRowsByFamily(List<BenchmarkMetricRow> rows, String family) {
        List<BenchmarkMetricRow> filtered = new ArrayList<>();
        for (BenchmarkMetricRow row : rows) {
            if ("MAPPING".equals(row.metricStage) && family.equals(row.family)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private Path renderDeltaOverview(List<BenchmarkMetricRow> rows, Path outputDir) throws IOException {
        List<BenchmarkMetricRow> mappingRows = new ArrayList<>();
        for (BenchmarkMetricRow row : rows) {
            if (!"MAPPING".equals(row.metricStage)) {
                continue;
            }
            if (!"FLUENT".equals(row.family) && !"SQLLIKE".equals(row.family)) {
                continue;
            }
            mappingRows.add(row);
        }
        mappingRows.sort(Comparator.comparing((BenchmarkMetricRow r) -> r.chartType).thenComparingInt(r -> r.size));

        List<String> labels = new ArrayList<>();
        List<Double> passValues = new ArrayList<>();
        List<Double> failValues = new ArrayList<>();
        for (BenchmarkMetricRow row : mappingRows) {
            labels.add(row.chartType + "-" + row.size + "-" + row.family.toLowerCase(Locale.ROOT));
            if (row.pass) {
                passValues.add(row.delta);
                failValues.add(0d);
            } else {
                passValues.add(0d);
                failValues.add(row.delta);
            }
        }

        CategoryChart chart = new CategoryChartBuilder()
                .title("Threshold Delta Overview (mapping)")
                .xAxisTitle("Metric")
                .yAxisTitle("delta (score - threshold)")
                .width(1300)
                .height(750)
                .build();
        chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideE);
        chart.addSeries("pass", labels, passValues).setFillColor(new Color(0x2c, 0xa0, 0x2c));
        chart.addSeries("fail", labels, failValues).setFillColor(new Color(0xd6, 0x27, 0x28));

        return saveChart(chart, outputDir, "delta-overview");
    }

    private static void addSeries(XYChart chart,
                                  String seriesLabel,
                                  List<BenchmarkMetricRow> rows,
                                  String family,
                                  boolean threshold) {
        List<Integer> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();
        rows.stream()
                .filter(r -> family.equals(r.family))
                .sorted(Comparator.comparingInt(r -> r.size))
                .forEach(r -> {
                    x.add(r.size);
                    y.add(threshold ? r.threshold : r.score);
                });
        if (x.isEmpty()) {
            return;
        }
        chart.addSeries(seriesLabel, x, y);
    }

    private static Map<Integer, Double> scoreBySize(List<BenchmarkMetricRow> rows, String chartType) {
        Map<Integer, Double> bySize = new LinkedHashMap<>();
        for (BenchmarkMetricRow row : rows) {
            if (!chartType.equals(row.chartType)) {
                continue;
            }
            bySize.put(row.size, row.score);
        }
        return bySize;
    }

    private static Path saveChart(Chart<?, ?> chart, Path outputDir, String fileBase) throws IOException {
        Path base = outputDir.resolve(fileBase);
        Path lower = Paths.get(base.toString() + ".png");
        Path upper = Paths.get(base.toString() + ".PNG");
        Files.deleteIfExists(lower);
        Files.deleteIfExists(upper);
        try {
            BitmapEncoder.saveBitmap(chart, base.toString(), BitmapEncoder.BitmapFormat.PNG);
        } catch (Exception e) {
            throw new IOException("Failed to save chart: " + fileBase, e);
        }
        if (Files.exists(lower)) {
            return lower;
        }
        return upper;
    }
}

