package laughing.man.commits.builder;

import java.util.Objects;

/**
 * Immutable descriptor for the supported ROWS window frames.
 */
public final class QueryWindowFrame {

    private final Integer precedingRows;
    private final boolean unboundedPreceding;
    private final boolean unboundedFollowing;

    private QueryWindowFrame(Integer precedingRows, boolean unboundedPreceding, boolean unboundedFollowing) {
        this.precedingRows = precedingRows;
        this.unboundedPreceding = unboundedPreceding;
        this.unboundedFollowing = unboundedFollowing;
    }

    public static QueryWindowFrame running() {
        return new QueryWindowFrame(null, true, false);
    }

    public static QueryWindowFrame unboundedPrecedingToCurrentRow() {
        return running();
    }

    public static QueryWindowFrame rowsPrecedingToCurrentRow(int precedingRows) {
        if (precedingRows < 0) {
            throw new IllegalArgumentException("precedingRows must be >= 0");
        }
        return new QueryWindowFrame(precedingRows, false, false);
    }

    public static QueryWindowFrame fullPartition() {
        return new QueryWindowFrame(null, true, true);
    }

    public boolean isRunning() {
        return unboundedPreceding && !unboundedFollowing;
    }

    public boolean boundedPreceding() {
        return precedingRows != null;
    }

    public int precedingRows() {
        if (precedingRows == null) {
            throw new IllegalStateException("Frame is not bounded by preceding rows");
        }
        return precedingRows;
    }

    public boolean isFullPartition() {
        return unboundedPreceding && unboundedFollowing;
    }

    public String sqlExpression() {
        if (isFullPartition()) {
            return "ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING";
        }
        if (isRunning()) {
            return "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW";
        }
        return "ROWS BETWEEN " + precedingRows + " PRECEDING AND CURRENT ROW";
    }

    public String explainToken() {
        return sqlExpression();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QueryWindowFrame that)) {
            return false;
        }
        return unboundedPreceding == that.unboundedPreceding
                && unboundedFollowing == that.unboundedFollowing
                && Objects.equals(precedingRows, that.precedingRows);
    }

    @Override
    public int hashCode() {
        return Objects.hash(precedingRows, unboundedPreceding, unboundedFollowing);
    }

    @Override
    public String toString() {
        return sqlExpression();
    }
}
