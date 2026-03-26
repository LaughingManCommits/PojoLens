package laughing.man.commits.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

public final class BenchmarkThresholdChecker {

    private static final Pattern THRESHOLD_ENTRY_PATTERN =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)");
    private static final Pattern RESULT_ENTRY_PATTERN =
            Pattern.compile("\"benchmark\"\\s*:\\s*\"([^\"]+)\".*?\"params\"\\s*:\\s*\\{(.*?)\\}.*?\"primaryMetric\"\\s*:\\s*\\{.*?\"score\"\\s*:\\s*([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)",
                    Pattern.DOTALL);
    private static final Pattern PARAM_ENTRY_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    private BenchmarkThresholdChecker() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 4) {
            System.out.println("usage: BenchmarkThresholdChecker <results.json> <thresholds.json> [report.csv] [--strict]");
            System.exit(2);
        }

        Path resultsPath = Paths.get(args[0]);
        Path thresholdsPath = Paths.get(args[1]);
        Path reportPath = null;
        boolean strict = false;
        for (int i = 2; i < args.length; i++) {
            if ("--strict".equals(args[i])) {
                strict = true;
            } else if (reportPath == null) {
                reportPath = Paths.get(args[i]);
            } else {
                System.out.println("Unexpected argument: " + args[i]);
                System.exit(2);
            }
        }

        String resultsJson = readUtf8(resultsPath);
        String thresholdsJson = readUtf8(thresholdsPath);

        Map<String, Double> thresholds = parseThresholds(thresholdsJson);
        Map<String, Double> results = parseResults(resultsJson);
        CheckOutcome outcome = evaluate(results, thresholds, strict);

        List<String> reportLines = buildReportLines(results, thresholds, outcome.thresholdsWithoutResults);
        if (reportPath != null) {
            writeUtf8(reportPath, String.join(System.lineSeparator(), reportLines) + System.lineSeparator());
        }

        if (!outcome.missingThresholds.isEmpty()) {
            System.out.println("Missing threshold entries for executed benchmarks:");
            for (String key : outcome.missingThresholds) {
                System.out.println("- " + key);
            }
        }
        if (!outcome.failures.isEmpty()) {
            System.out.println("Benchmark threshold failures:");
            for (Failure failure : outcome.failures) {
                System.out.printf(Locale.ROOT, "- %s: %.3f > %.3f%n", failure.key, failure.score, failure.limit);
            }
        }
        if (strict && !outcome.thresholdsWithoutResults.isEmpty()) {
            System.out.println("Strict mode: threshold keys without benchmark results:");
            for (String key : outcome.thresholdsWithoutResults) {
                System.out.println("- " + key);
            }
        }

        if (outcome.hasFailures()) {
            System.exit(1);
        }

        System.out.println("Benchmark thresholds satisfied.");
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

    private static Map<String, Double> parseResults(String json) {
        Map<String, Double> results = new LinkedHashMap<>();
        Matcher matcher = RESULT_ENTRY_PATTERN.matcher(json);
        while (matcher.find()) {
            String benchmark = matcher.group(1);
            String paramsBlob = matcher.group(2);
            String scoreText = matcher.group(3);
            double score = Double.parseDouble(scoreText);

            Map<String, String> params = parseParams(paramsBlob);
            String key = benchmark;
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

    private static List<String> buildReportLines(Map<String, Double> results,
                                                 Map<String, Double> thresholds,
                                                 List<String> thresholdsWithoutResults) {
        List<String> keys = new ArrayList<>(results.keySet());
        Collections.sort(keys);
        List<String> lines = new ArrayList<>();
        lines.add("benchmark_key,score,limit,delta,status");
        for (String key : keys) {
            double score = results.get(key);
            Double limit = thresholds.get(key);
            if (limit == null) {
                lines.add(String.format(Locale.ROOT, "%s,%.3f,,,%s", key, score, "missing-threshold"));
                continue;
            }
            double delta = score - limit;
            String status = delta > 0 ? "fail" : "pass";
            lines.add(String.format(Locale.ROOT, "%s,%.3f,%.3f,%.3f,%s", key, score, limit, delta, status));
        }
        List<String> unusedThresholds = new ArrayList<>(thresholdsWithoutResults);
        Collections.sort(unusedThresholds);
        for (String key : unusedThresholds) {
            lines.add(String.format(Locale.ROOT, "%s,,%.3f,,%s", key, thresholds.get(key), "threshold-without-result"));
        }
        return lines;
    }

    private static CheckOutcome evaluate(Map<String, Double> results,
                                         Map<String, Double> thresholds,
                                         boolean strict) {
        List<Failure> failures = new ArrayList<>();
        List<String> missingThresholds = new ArrayList<>();
        List<String> thresholdsWithoutResults = new ArrayList<>();

        for (Map.Entry<String, Double> result : results.entrySet()) {
            Double limit = thresholds.get(result.getKey());
            if (limit == null) {
                missingThresholds.add(result.getKey());
                continue;
            }
            if (result.getValue() > limit) {
                failures.add(new Failure(result.getKey(), result.getValue(), limit));
            }
        }
        for (String thresholdKey : thresholds.keySet()) {
            if (!results.containsKey(thresholdKey)) {
                thresholdsWithoutResults.add(thresholdKey);
            }
        }

        failures.sort(Comparator.comparing(f -> f.key));
        Collections.sort(missingThresholds);
        Collections.sort(thresholdsWithoutResults);

        return new CheckOutcome(failures, missingThresholds, thresholdsWithoutResults, strict);
    }

    private static Map<String, String> parseParams(String paramsBlob) {
        Map<String, String> params = new HashMap<>();
        Matcher matcher = PARAM_ENTRY_PATTERN.matcher(paramsBlob);
        while (matcher.find()) {
            params.put(matcher.group(1), matcher.group(2));
        }
        return params;
    }

    private static final class Failure {
        private final String key;
        private final double score;
        private final double limit;

        private Failure(String key, double score, double limit) {
            this.key = key;
            this.score = score;
            this.limit = limit;
        }
    }

    private static final class CheckOutcome {
        private final List<Failure> failures;
        private final List<String> missingThresholds;
        private final List<String> thresholdsWithoutResults;
        private final boolean strict;

        private CheckOutcome(List<Failure> failures,
                             List<String> missingThresholds,
                             List<String> thresholdsWithoutResults,
                             boolean strict) {
            this.failures = failures;
            this.missingThresholds = missingThresholds;
            this.thresholdsWithoutResults = thresholdsWithoutResults;
            this.strict = strict;
        }

        private boolean hasFailures() {
            return !failures.isEmpty()
                    || !missingThresholds.isEmpty()
                    || (strict && !thresholdsWithoutResults.isEmpty());
        }
    }
}

