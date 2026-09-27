package laughing.man.commits.report;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.internal.NumericStatistics;
import laughing.man.commits.util.GroupKeyUtil;
import laughing.man.commits.util.ReflectionUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Factory for {@link PeriodComparison} values.
 *
 * <p>Use the numeric overloads when metric aggregation is already done,
 * and the row-based overloads when you have raw POJO lists.
 */
public final class ReportComparisons {

    private ReportComparisons() {
    }

    public static PeriodComparison of(double currentValue, double previousValue) {
        return new PeriodComparison(currentValue, previousValue);
    }

    public static PeriodComparison of(long currentValue, long previousValue) {
        return new PeriodComparison(currentValue, previousValue);
    }

    /**
     * Applies {@code metric} over {@code field} on both row lists and returns
     * the resulting {@link PeriodComparison}.
     *
     * <p>Supported metrics: {@code COUNT}, {@code COUNT_DISTINCT} (distinct non-null
     * values of {@code field}), {@code SUM}, {@code AVG}, {@code MIN}, {@code MAX},
     * {@code MEDIAN}, {@code STDDEV}, {@code STDDEV_POP}, {@code VARIANCE}, {@code VAR_POP}.
     */
    public static <T> PeriodComparison compare(List<T> currentRows,
                                               List<T> previousRows,
                                               String field,
                                               Metric metric) {
        Objects.requireNonNull(metric, "metric must not be null");
        Objects.requireNonNull(field, "field must not be null");
        double current = aggregate(currentRows, field, metric);
        double previous = aggregate(previousRows, field, metric);
        return new PeriodComparison(current, previous);
    }

    /**
     * Shorthand for {@link Metric#COUNT} comparisons where no field is needed.
     */
    public static <T> PeriodComparison compareCount(List<T> currentRows, List<T> previousRows) {
        double current = currentRows == null ? 0 : currentRows.size();
        double previous = previousRows == null ? 0 : previousRows.size();
        return new PeriodComparison(current, previous);
    }

    private static <T> double aggregate(List<T> rows, String field, Metric metric) {
        if (rows == null || rows.isEmpty()) {
            return 0d;
        }
        if (metric == Metric.COUNT) {
            return rows.size();
        }
        if (metric == Metric.COUNT_DISTINCT) {
            return distinctCount(rows, field);
        }
        if (NumericStatistics.isStatistical(metric)) {
            return statistic(rows, field, metric);
        }
        double accumulator = metric == Metric.MIN ? Double.MAX_VALUE
                : metric == Metric.MAX ? -Double.MAX_VALUE : 0d;
        int count = 0;
        for (T row : rows) {
            if (row == null) {
                continue;
            }
            double value = numericFieldValue(row, field);
            accumulator = switch (metric) {
                case SUM, AVG -> accumulator + value;
                case MIN -> Math.min(accumulator, value);
                case MAX -> Math.max(accumulator, value);
                default -> accumulator;
            };
            count++;
        }
        if (metric == Metric.AVG) {
            return count == 0 ? 0d : accumulator / count;
        }
        if ((metric == Metric.MIN || metric == Metric.MAX) && count == 0) {
            return 0d;
        }
        return accumulator;
    }

    /**
     * MEDIAN, STDDEV, STDDEV_POP, VARIANCE, VAR_POP over non-null values; 0 without a
     * result (no values, or sample statistics over one value).
     */
    private static <T> double statistic(List<T> rows, String field, Metric metric) {
        if (metric.requiresArgument()) {
            throw new IllegalArgumentException(
                    "ReportComparisons does not support PERCENTILE; compare percentiles computed by a query");
        }
        NumericStatistics statistics = NumericStatistics.of(metric, null);
        for (T row : rows) {
            Object raw = row == null ? null : fieldValue(row, field);
            if (raw != null) {
                statistics.add(numericFieldValue(row, field));
            }
        }
        Double result = statistics.result();
        return result == null ? 0d : result;
    }

    private static <T> double distinctCount(List<T> rows, String field) {
        Set<Object> keys = new HashSet<>();
        for (T row : rows) {
            Object raw = row == null ? null : fieldValue(row, field);
            if (raw != null) {
                keys.add(GroupKeyUtil.groupKey(raw, null));
            }
        }
        return keys.size();
    }

    private static <T> Object fieldValue(T row, String field) {
        try {
            return ReflectionUtil.getFieldValue(row, field);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "ReportComparisons: cannot read field '" + field + "' from "
                            + row.getClass().getSimpleName(), ex);
        }
    }

    private static <T> double numericFieldValue(T row, String field) {
        Object raw = fieldValue(row, field);
        return switch (raw) {
            case null -> 0d;
            case Number number -> number.doubleValue();
            default -> throw new IllegalArgumentException(
                    "ReportComparisons: field '" + field + "' is not numeric");
        };
    }
}
