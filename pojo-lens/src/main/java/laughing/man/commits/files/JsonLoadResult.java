package laughing.man.commits.files;

import java.util.List;
import java.util.Objects;

/**
 * Rows plus diagnostics for an explicit JSON or JSONL load call.
 */
public final class JsonLoadResult<T> {

    private final List<T> rows;
    private final JsonLoadReport report;

    public JsonLoadResult(List<T> rows, JsonLoadReport report) {
        this.rows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));
        this.report = Objects.requireNonNull(report, "report must not be null");
    }

    public List<T> rows() {
        return rows;
    }

    public JsonLoadReport report() {
        return report;
    }
}
