package laughing.man.commits.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads JMH result JSON and threshold JSON into normalized benchmark metric rows.
 */
public final class BenchmarkMetricLoader {

    private static final Pattern THRESHOLD_ENTRY_PATTERN =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)");
    private static final Pattern RESULT_ENTRY_PATTERN =
            Pattern.compile("\"benchmark\"\\s*:\\s*\"([^\"]+)\".*?\"params\"\\s*:\\s*\\{(.*?)\\}.*?\"primaryMetric\"\\s*:\\s*\\{.*?\"score\"\\s*:\\s*([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)",
                    Pattern.DOTALL);
    private static final Pattern PARAM_ENTRY_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    private BenchmarkMetricLoader() {
    }

    public static List<BenchmarkMetricRow> load(Path resultsPath, Path thresholdsPath, String source) throws IOException {
        String resultsJson = readUtf8(resultsPath);
        String thresholdsJson = readUtf8(thresholdsPath);
        Map<String, Double> thresholds = parseThresholds(thresholdsJson);
        Map<String, ResultMetric> results = parseResults(resultsJson);
        return toRows(results, thresholds, source);
    }

    public static List<BenchmarkMetricRow> combine(List<BenchmarkMetricRow> first, List<BenchmarkMetricRow> second) {
        List<BenchmarkMetricRow> all = new ArrayList<>();
        if (first != null) {
            all.addAll(first);
        }
        if (second != null) {
            all.addAll(second);
        }
        all.sort(Comparator.comparing(r -> r.benchmarkKey));
        return all;
    }

    private static String readUtf8(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }
        return Files.readString(path);
    }

    private static Map<String, Double> parseThresholds(String json) {
        Map<String, Double> thresholds = new HashMap<>();
        Matcher matcher = THRESHOLD_ENTRY_PATTERN.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            double value = Double.parseDouble(matcher.group(2));
            thresholds.put(key, value);
        }
        return thresholds;
    }

    private static Map<String, ResultMetric> parseResults(String json) {
        Map<String, ResultMetric> results = new LinkedHashMap<>();
        Matcher matcher = RESULT_ENTRY_PATTERN.matcher(json);
        while (matcher.find()) {
            String benchmarkName = matcher.group(1);
            String paramsBlob = matcher.group(2);
            double score = Double.parseDouble(matcher.group(3));

            Map<String, String> params = parseParams(paramsBlob);
            String key = benchmarkName;
            if (!params.isEmpty()) {
                List<String> names = new ArrayList<>(params.keySet());
                Collections.sort(names);
                StringBuilder sb = new StringBuilder(benchmarkName).append("|");
                for (int i = 0; i < names.size(); i++) {
                    if (i > 0) {
                        sb.append(",");
                    }
                    String name = names.get(i);
                    sb.append(name).append("=").append(params.get(name));
                }
                key = sb.toString();
            }
            results.put(key, new ResultMetric(benchmarkName, score, params));
        }
        return results;
    }

    private static Map<String, String> parseParams(String paramsBlob) {
        Map<String, String> params = new HashMap<>();
        Matcher matcher = PARAM_ENTRY_PATTERN.matcher(paramsBlob);
        while (matcher.find()) {
            params.put(matcher.group(1), matcher.group(2));
        }
        return params;
    }

    private static List<BenchmarkMetricRow> toRows(Map<String, ResultMetric> results,
                                                   Map<String, Double> thresholds,
                                                   String source) {
        List<BenchmarkMetricRow> rows = new ArrayList<>();
        List<String> keys = new ArrayList<>(results.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            ResultMetric metric = results.get(key);
            BenchmarkMetricRow row = new BenchmarkMetricRow();
            row.benchmarkKey = key;
            row.benchmarkName = metric.benchmarkName;
            row.methodName = methodName(metric.benchmarkName);
            row.source = source == null ? "unknown" : source;
            row.size = parseInt(metric.params.get("size"), -1);
            row.score = metric.score;
            row.unit = "ms/op";
            row.family = family(row.methodName);
            row.benchmarkCategory = benchmarkCategory(row.methodName);
            row.chartType = chartType(row.methodName);
            row.metricStage = metricStage(row.methodName);

            Double threshold = thresholds.get(key);
            row.thresholdPresent = threshold != null;
            if (threshold == null) {
                row.threshold = Double.NaN;
                row.delta = Double.NaN;
                row.ratioToThreshold = Double.NaN;
                row.pass = false;
                row.status = "MISSING_THRESHOLD";
            } else {
                row.threshold = threshold;
                row.delta = row.score - threshold;
                row.ratioToThreshold = threshold <= 0d ? Double.NaN : row.score / threshold;
                row.pass = row.score <= threshold;
                row.status = row.pass ? "PASS" : "FAIL";
            }
            rows.add(row);
        }
        return rows;
    }

    private static String methodName(String benchmarkName) {
        if (benchmarkName == null) {
            return "unknown";
        }
        int index = benchmarkName.lastIndexOf('.');
        return index < 0 ? benchmarkName : benchmarkName.substring(index + 1);
    }

    private static String family(String methodName) {
        if (methodName == null) {
            return "OTHER";
        }
        String n = methodName.toLowerCase(Locale.ROOT);
        if (n.startsWith("streams")) {
            return "STREAMS";
        }
        if (n.startsWith("manual")) {
            return "MANUAL";
        }
        if (n.startsWith("fluent")) {
            return "FLUENT";
        }
        if (n.startsWith("csv")) {
            return "CSV";
        }
        if (n.startsWith("sqllike") || n.contains("sqllike")) {
            return "SQLLIKE";
        }
        return "OTHER";
    }

    private static String benchmarkCategory(String methodName) {
        if (methodName == null) {
            return "OTHER";
        }
        String n = methodName.toLowerCase(Locale.ROOT);
        if (n.contains("cache")) {
            return "CACHE";
        }
        if (n.contains("csv")) {
            return "LOAD";
        }
        if (n.contains("parseonly")) {
            return "PARSE";
        }
        if (n.contains("explain")) {
            return "EXPLAIN";
        }
        if (n.contains("join")) {
            return "JOIN";
        }
        if (n.contains("payloadjsonexport") || n.contains("mapping") || n.contains("tochart")) {
            return "CHART";
        }
        if (n.contains("timebucket") || n.contains("bucket")) {
            return "TIME_BUCKET";
        }
        if (n.contains("group")) {
            return "GROUP";
        }
        if (n.contains("filter")) {
            return "FILTER";
        }
        return "OTHER";
    }

    private static String chartType(String methodName) {
        if (methodName == null) {
            return "UNKNOWN";
        }
        String n = methodName.toLowerCase(Locale.ROOT);
        if (n.contains("bar")) {
            return "BAR";
        }
        if (n.contains("line")) {
            return "LINE";
        }
        if (n.contains("pie")) {
            return "PIE";
        }
        if (n.contains("area")) {
            return "AREA";
        }
        if (n.contains("scatter")) {
            return "SCATTER";
        }
        return "UNKNOWN";
    }

    private static String metricStage(String methodName) {
        if (methodName == null) {
            return "OTHER";
        }
        String n = methodName.toLowerCase(Locale.ROOT);
        if (n.contains("payloadjsonexport")) {
            return "EXPORT";
        }
        if (n.contains("csv")) {
            return "LOAD_PIPELINE";
        }
        if (n.contains("mapping")) {
            return "MAPPING";
        }
        if (n.contains("chart")) {
            return "CHART_PIPELINE";
        }
        return "OTHER";
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class ResultMetric {
        private final String benchmarkName;
        private final double score;
        private final Map<String, String> params;

        private ResultMetric(String benchmarkName, double score, Map<String, String> params) {
            this.benchmarkName = benchmarkName;
            this.score = score;
            this.params = params;
        }
    }
}

