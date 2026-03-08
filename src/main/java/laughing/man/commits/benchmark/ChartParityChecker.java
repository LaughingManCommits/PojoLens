package laughing.man.commits.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares SQL-like chart benchmark methods against fluent baselines.
 */
public final class ChartParityChecker {

    private static final Pattern RESULT_ENTRY_PATTERN =
            Pattern.compile("\"benchmark\"\\s*:\\s*\"([^\"]+)\".*?\"params\"\\s*:\\s*\\{(.*?)\\}.*?\"primaryMetric\"\\s*:\\s*\\{.*?\"score\"\\s*:\\s*([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)",
                    Pattern.DOTALL);
    private static final Pattern PARAM_ENTRY_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
    private static final String BENCH_CLASS = "laughing.man.commits.benchmark.ChartVisualizationJmhBenchmark.";

    private static final Map<String, String> PARITY_METHOD_PAIRS;

    static {
        Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("fluentBarMapping", "sqlLikeBarMapping");
        pairs.put("fluentLineMapping", "sqlLikeLineMapping");
        pairs.put("fluentPieMapping", "sqlLikePieMapping");
        pairs.put("fluentAreaMapping", "sqlLikeAreaMapping");
        pairs.put("fluentScatterMapping", "sqlLikeScatterMapping");
        PARITY_METHOD_PAIRS = Collections.unmodifiableMap(pairs);
    }

    private ChartParityChecker() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 3) {
            System.out.println("usage: ChartParityChecker <results.json> [report.csv] [max-ratio]");
            System.exit(2);
        }
        Path resultsPath = Paths.get(args[0]);
        Path reportPath = args.length >= 2 ? Paths.get(args[1]) : null;
        double maxRatio = args.length == 3 ? Double.parseDouble(args[2]) : 1.75d;

        Map<String, Double> results = parseResults(readUtf8(resultsPath));
        List<ParityRow> rows = evaluate(rowsBySize(results), maxRatio);

        if (reportPath != null) {
            writeUtf8(reportPath, toCsv(rows));
        }

        boolean failed = false;
        for (ParityRow row : rows) {
            if (!row.pass) {
                failed = true;
                System.out.printf(Locale.ROOT,
                        "Chart parity failure [%s size=%s]: sql/fluent ratio %.3f > %.3f%n",
                        row.chartType, row.size, row.ratio, row.maxRatio);
            }
        }
        if (failed) {
            System.exit(1);
        }
        System.out.printf(Locale.ROOT, "Chart parity satisfied (max-ratio=%.3f).%n", maxRatio);
    }

    private static String readUtf8(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }
        return Files.readString(path);
    }

    private static void writeUtf8(Path path, String content) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, content);
    }

    private static Map<String, Double> parseResults(String json) {
        Map<String, Double> results = new LinkedHashMap<>();
        Matcher matcher = RESULT_ENTRY_PATTERN.matcher(json);
        while (matcher.find()) {
            String benchmark = matcher.group(1);
            String paramsBlob = matcher.group(2);
            double score = Double.parseDouble(matcher.group(3));

            String key = benchmark;
            Map<String, String> params = parseParams(paramsBlob);
            if (!params.isEmpty()) {
                List<String> names = new ArrayList<>(params.keySet());
                Collections.sort(names);
                StringBuilder sb = new StringBuilder(benchmark).append("|");
                for (int i = 0; i < names.size(); i++) {
                    if (i > 0) {
                        sb.append(",");
                    }
                    String name = names.get(i);
                    sb.append(name).append("=").append(params.get(name));
                }
                key = sb.toString();
            }
            results.put(key, score);
        }
        return results;
    }

    private static Map<String, Map<String, Double>> rowsBySize(Map<String, Double> results) {
        Map<String, Map<String, Double>> bySize = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : results.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(BENCH_CLASS)) {
                continue;
            }
            int pipeIndex = key.indexOf('|');
            if (pipeIndex < 0) {
                continue;
            }
            String method = key.substring(BENCH_CLASS.length(), pipeIndex);
            String params = key.substring(pipeIndex + 1);
            String size = sizeParam(params);
            if (size == null) {
                continue;
            }
            bySize.computeIfAbsent(size, ignored -> new LinkedHashMap<>()).put(method, entry.getValue());
        }
        return bySize;
    }

    private static List<ParityRow> evaluate(Map<String, Map<String, Double>> bySize, double maxRatio) {
        List<ParityRow> rows = new ArrayList<>();
        List<String> sizes = new ArrayList<>(bySize.keySet());
        Collections.sort(sizes);
        for (String size : sizes) {
            Map<String, Double> sizeMetrics = bySize.get(size);
            for (Map.Entry<String, String> pair : PARITY_METHOD_PAIRS.entrySet()) {
                String fluent = pair.getKey();
                String sqlLike = pair.getValue();
                Double fluentScore = sizeMetrics.get(fluent);
                Double sqlScore = sizeMetrics.get(sqlLike);
                if (fluentScore == null || sqlScore == null || fluentScore <= 0d) {
                    rows.add(new ParityRow(chartTypeFor(fluent), size, fluentScore, sqlScore, Double.NaN, maxRatio, false));
                    continue;
                }
                double ratio = sqlScore / fluentScore;
                rows.add(new ParityRow(chartTypeFor(fluent), size, fluentScore, sqlScore, ratio, maxRatio, ratio <= maxRatio));
            }
        }
        return rows;
    }

    private static String chartTypeFor(String fluentMethodName) {
        String name = fluentMethodName.replace("fluent", "").replace("Mapping", "");
        return name.toUpperCase(Locale.ROOT);
    }

    private static String sizeParam(String params) {
        String[] entries = params.split(",");
        for (String entry : entries) {
            String[] kv = entry.split("=", 2);
            if (kv.length == 2 && "size".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }

    private static Map<String, String> parseParams(String paramsBlob) {
        Map<String, String> params = new LinkedHashMap<>();
        Matcher matcher = PARAM_ENTRY_PATTERN.matcher(paramsBlob);
        while (matcher.find()) {
            params.put(matcher.group(1), matcher.group(2));
        }
        return params;
    }

    private static String toCsv(List<ParityRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("chart_type,size,fluent_ms,sql_like_ms,ratio,max_ratio,status").append(System.lineSeparator());
        for (ParityRow row : rows) {
            sb.append(row.chartType).append(",")
                    .append(row.size).append(",")
                    .append(formatDouble(row.fluentScore)).append(",")
                    .append(formatDouble(row.sqlLikeScore)).append(",")
                    .append(formatDouble(row.ratio)).append(",")
                    .append(formatDouble(row.maxRatio)).append(",")
                    .append(row.pass ? "pass" : "fail")
                    .append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static String formatDouble(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return "";
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static final class ParityRow {
        private final String chartType;
        private final String size;
        private final Double fluentScore;
        private final Double sqlLikeScore;
        private final Double ratio;
        private final Double maxRatio;
        private final boolean pass;

        private ParityRow(String chartType,
                          String size,
                          Double fluentScore,
                          Double sqlLikeScore,
                          Double ratio,
                          Double maxRatio,
                          boolean pass) {
            this.chartType = chartType;
            this.size = size;
            this.fluentScore = fluentScore;
            this.sqlLikeScore = sqlLikeScore;
            this.ratio = ratio;
            this.maxRatio = maxRatio;
            this.pass = pass;
        }
    }
}

