package laughing.man.commits.csv;

import java.util.Objects;

/**
 * Load-time CSV failure that carries structured diagnostics.
 */
public final class CsvLoadException extends IllegalArgumentException {

    private final CsvLoadReport report;

    public CsvLoadException(String message, CsvLoadReport report) {
        super(message);
        this.report = Objects.requireNonNull(report, "report must not be null");
    }

    public CsvLoadException(String message, CsvLoadReport report, Throwable cause) {
        super(message, cause);
        this.report = Objects.requireNonNull(report, "report must not be null");
    }

    public CsvLoadReport report() {
        return report;
    }
}
