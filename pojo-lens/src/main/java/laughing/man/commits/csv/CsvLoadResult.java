package laughing.man.commits.csv;

import java.util.List;
import java.util.Objects;

/**
 * Rows plus diagnostics for an explicit CSV load call.
 */
public final class CsvLoadResult<T> {

    private final List<T> rows;
    private final CsvLoadReport report;

    public CsvLoadResult(List<T> rows, CsvLoadReport report) {
        this.rows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));
        this.report = Objects.requireNonNull(report, "report must not be null");
    }

    public List<T> rows() {
        return rows;
    }

    public CsvLoadReport report() {
        return report;
    }
}
