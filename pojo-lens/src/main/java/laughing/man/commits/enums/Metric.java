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
    MAX;

    public boolean requiresNumericField() {
        return this == SUM || this == AVG || this == MIN || this == MAX;
    }

    public String expressionFor(String field) {
        if (this == COUNT) {
            return "count(*)";
        }
        return name().toLowerCase(java.util.Locale.ROOT) + "(" + StringUtil.requireNonBlank(field, "metricField") + ")";
    }
}

