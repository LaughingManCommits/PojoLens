package laughing.man.commits.files;

import java.util.Objects;

/**
 * Load-time JSON or JSONL failure that carries structured diagnostics.
 */
public final class JsonLoadException extends IllegalArgumentException {

    private final transient JsonLoadReport report;

    public JsonLoadException(String message, JsonLoadReport report) {
        super(message);
        this.report = Objects.requireNonNull(report, "report must not be null");
    }

    public JsonLoadException(String message, JsonLoadReport report, Throwable cause) {
        super(message, cause);
        this.report = Objects.requireNonNull(report, "report must not be null");
    }

    public JsonLoadReport report() {
        return report;
    }
}
