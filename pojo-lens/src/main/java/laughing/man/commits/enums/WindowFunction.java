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
    MAX;

    public boolean isRankFunction() {
        return this == ROW_NUMBER || this == RANK || this == DENSE_RANK;
    }

    public boolean isAggregateFunction() {
        return !isRankFunction();
    }

    public boolean supportsCountAll() {
        return this == COUNT;
    }

    public boolean requiresNumericField() {
        return this == SUM || this == AVG || this == MIN || this == MAX;
    }
}
