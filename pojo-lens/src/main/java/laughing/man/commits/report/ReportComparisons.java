package laughing.man.commits.report;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.util.ReflectionUtil;

import java.util.List;
import java.util.Objects;

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
     * <p>Supported metrics: {@code COUNT}, {@code SUM}, {@code AVG},
     * {@code MIN}, {@code MAX}.
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

    private static <T> double numericFieldValue(T row, String field) {
        Object raw;
        try {
            raw = ReflectionUtil.getFieldValue(row, field);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "ReportComparisons: cannot read field '" + field + "' from "
                            + row.getClass().getSimpleName(), ex);
        }
        return switch (raw) {
            case null -> 0d;
            case Number number -> number.doubleValue();
            default -> throw new IllegalArgumentException(
                    "ReportComparisons: field '" + field + "' is not numeric");
        };
    }
}
