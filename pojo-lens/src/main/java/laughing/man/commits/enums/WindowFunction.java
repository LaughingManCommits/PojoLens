package laughing.man.commits.enums;

/**
 * Window functions supported by fluent and SQL-like window pipelines.
 */
public enum WindowFunction {
    ROW_NUMBER,
    RANK,
    DENSE_RANK,
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX,
    LAG,
    LEAD;

    /**
     * The function with this name, ignoring case and surrounding whitespace, or
     * {@code null} when no function matches.
     */
    public static WindowFunction fromName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        for (WindowFunction function : values()) {
            if (function.name().equalsIgnoreCase(trimmed)) {
                return function;
            }
        }
        return null;
    }

    public boolean isRankFunction() {
        return this == ROW_NUMBER || this == RANK || this == DENSE_RANK;
    }

    /**
     * {@code LAG}/{@code LEAD}: read the value field from a row a fixed number of
     * positions before or after the current row in its partition.
     */
    public boolean isOffsetFunction() {
        return this == LAG || this == LEAD;
    }

    public boolean isAggregateFunction() {
        return !isRankFunction() && !isOffsetFunction();
    }

    public boolean supportsCountAll() {
        return this == COUNT;
    }

    public boolean requiresNumericField() {
        return this == SUM || this == AVG || this == MIN || this == MAX;
    }
}
