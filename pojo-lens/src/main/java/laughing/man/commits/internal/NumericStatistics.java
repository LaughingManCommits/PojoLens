package laughing.man.commits.internal;

import laughing.man.commits.enums.Metric;

import java.util.Arrays;

/**
 * Accumulates one statistical aggregate over non-null numbers: {@code MEDIAN},
 * {@code PERCENTILE}, {@code STDDEV}/{@code STDDEV_POP}, and {@code VARIANCE}/{@code VAR_POP}.
 *
 * <p>Values are read as {@code double}, and every result is a {@code Double} ({@code null}
 * without input). Percentiles use linear interpolation between the closest ranks, like SQL
 * {@code percentile_cont}: {@code MEDIAN} is the 0.5 percentile, so the median of
 * {@code 1, 2} is {@code 1.5}. {@code STDDEV}/{@code VARIANCE} are the sample statistics
 * ({@code n - 1}; {@code null} for a single value, like PostgreSQL), and the {@code _POP}
 * forms divide by {@code n}. Variance uses Welford's streaming update, so only
 * percentiles keep the values.</p>
 */
public final class NumericStatistics {

    private final Metric metric;
    private final double percentile;
    private double[] values;
    private int size;
    private long count;
    private double mean;
    private double squaredDistance;

    private NumericStatistics(Metric metric, double percentile) {
        this.metric = metric;
        this.percentile = percentile;
        this.values = keepsValues(metric) ? new double[16] : null;
    }

    /**
     * @param argument percentile fraction for {@link Metric#PERCENTILE}; ignored otherwise
     */
    public static NumericStatistics of(Metric metric, Double argument) {
        if (!isStatistical(metric)) {
            throw new IllegalArgumentException("Not a statistical aggregate: " + metric);
        }
        double percentile = metric == Metric.MEDIAN ? 0.5 : metric == Metric.PERCENTILE
                ? requirePercentile(argument) : 0d;
        return new NumericStatistics(metric, percentile);
    }

    public static boolean isStatistical(Metric metric) {
        return switch (metric) {
            case MEDIAN, PERCENTILE, STDDEV, STDDEV_POP, VARIANCE, VAR_POP -> true;
            default -> false;
        };
    }

    /**
     * Validates a {@code PERCENTILE} fraction: a number from 0 to 1 ({@code 0.9} is the
     * 90th percentile).
     */
    public static double requirePercentile(Double percentile) {
        if (percentile == null || percentile.isNaN() || percentile < 0d || percentile > 1d) {
            throw new IllegalArgumentException(
                    "PERCENTILE needs a fraction from 0 to 1 (0.9 is the 90th percentile), got: " + percentile);
        }
        return percentile;
    }

    public void add(Number value) {
        double number = value.doubleValue();
        count++;
        double delta = number - mean;
        mean += delta / count;
        squaredDistance += delta * (number - mean);
        if (values != null) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = number;
        }
    }

    public Double result() {
        if (count == 0) {
            return null;
        }
        return switch (metric) {
            case MEDIAN, PERCENTILE -> interpolatedPercentile();
            case VARIANCE -> sampleVariance();
            case VAR_POP -> squaredDistance / count;
            case STDDEV -> {
                Double variance = sampleVariance();
                yield variance == null ? null : Math.sqrt(variance);
            }
            case STDDEV_POP -> Math.sqrt(squaredDistance / count);
            default -> throw new IllegalStateException("Not a statistical aggregate: " + metric);
        };
    }

    private Double sampleVariance() {
        return count < 2 ? null : squaredDistance / (count - 1);
    }

    private double interpolatedPercentile() {
        double[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        double rank = percentile * (size - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (rank - lower);
    }

    private static boolean keepsValues(Metric metric) {
        return metric == Metric.MEDIAN || metric == Metric.PERCENTILE;
    }
}
