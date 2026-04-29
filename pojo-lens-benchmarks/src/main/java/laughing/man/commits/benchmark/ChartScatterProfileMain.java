package laughing.man.commits.benchmark;

import laughing.man.commits.chart.ChartData;

/**
 * Small profiling harness that runs scatter chart mapping inside the recorded
 * JVM so JFR attaches to the actual hot path instead of the outer JMH driver.
 */
public final class ChartScatterProfileMain {

    private ChartScatterProfileMain() {
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "sqlLike";
        int size = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 200;

        ChartVisualizationJmhBenchmark benchmark = new ChartVisualizationJmhBenchmark();
        benchmark.size = size;
        benchmark.setup();

        long checksum = 0L;
        long started = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            if ("fluent".equals(mode)) {
                benchmark.resetFluentExecutionCaches();
            }
            ChartData data = switch (mode) {
                case "fluent" -> benchmark.fluentScatterMapping();
                case "sqlLike" -> benchmark.sqlLikeScatterMapping();
                case "sqlLikeBound" -> benchmark.sqlLikeBoundScatterMapping();
                default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
            };
            checksum += pointCount(data);
        }
        long durationNanos = System.nanoTime() - started;
        double avgMillis = durationNanos / 1_000_000.0 / iterations;
        System.out.println("mode=" + mode
                + " size=" + size
                + " iterations=" + iterations
                + " checksum=" + checksum
                + " avgMs=" + avgMillis);
    }

    private static int pointCount(ChartData data) {
        if (data == null || data.getDatasets() == null || data.getDatasets().isEmpty()) {
            return 0;
        }
        if (data.getDatasets().get(0).getValues() != null) {
            return data.getDatasets().get(0).getValues().size();
        }
        return 0;
    }
}
