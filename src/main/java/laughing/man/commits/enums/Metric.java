package laughing.man.commits.enums;

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
}

