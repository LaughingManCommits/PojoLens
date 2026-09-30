package laughing.man.commits.enums;

import laughing.man.commits.util.StringUtil;

/**
 * Aggregation metrics supported by fluent query builders.
 */
public enum Metric {

    /** Row count. */
    COUNT,
    /** Numeric sum. */
    SUM,
    /** Numeric average. */
    AVG,
    /** Numeric minimum. */
    MIN,
    /** Numeric maximum. */
    MAX,
    /**
     * Number of distinct non-null values ({@code COUNT(DISTINCT field)}). Values compare like
     * {@code GROUP BY} keys.
     */
    COUNT_DISTINCT,
    /** Median ({@code 0.5} percentile, linear interpolation); a {@code Double}. */
    MEDIAN,
    /**
     * Percentile with linear interpolation (SQL {@code percentile_cont}); needs a fraction
     * from 0 to 1 as its argument. A {@code Double}.
     */
    PERCENTILE,
    /** Sample standard deviation ({@code n - 1}); {@code null} for one value. A {@code Double}. */
    STDDEV,
    /** Population standard deviation ({@code n}). A {@code Double}. */
    STDDEV_POP,
    /** Sample variance ({@code n - 1}); {@code null} for one value. A {@code Double}. */
    VARIANCE,
    /** Population variance ({@code n}). A {@code Double}. */
    VAR_POP;

    public boolean requiresNumericField() {
        return this != COUNT && this != COUNT_DISTINCT;
    }

    /**
     * True for {@link #PERCENTILE}, which needs a percentile fraction.
     */
    public boolean requiresArgument() {
        return this == PERCENTILE;
    }

    public String expressionFor(String field) {
        if (this == COUNT) {
            return "count(*)";
        }
        if (this == COUNT_DISTINCT) {
            return "count(distinct " + StringUtil.requireNonBlank(field, "metricField") + ")";
        }
        return name().toLowerCase(java.util.Locale.ROOT) + "(" + StringUtil.requireNonBlank(field, "metricField") + ")";
    }
}

