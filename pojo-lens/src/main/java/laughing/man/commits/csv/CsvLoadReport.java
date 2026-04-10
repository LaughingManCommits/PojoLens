package laughing.man.commits.csv;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Immutable diagnostics for a single CSV load attempt.
 */
public final class CsvLoadReport {

    private final Path path;
    private final Class<?> rowType;
    private final CsvOptions options;
    private final List<String> resolvedSchema;
    private final List<String> rejectedColumns;
    private final int logicalRecordCount;
    private final int loadedRowCount;
    private final boolean success;
    private final String failureStage;
    private final Integer failureRowNumber;
    private final String failureColumn;
    private final String failureMessage;
    private final long durationNanos;

    public CsvLoadReport(Path path,
                         Class<?> rowType,
                         CsvOptions options,
                         List<String> resolvedSchema,
                         List<String> rejectedColumns,
                         int logicalRecordCount,
                         int loadedRowCount,
                         boolean success,
                         String failureStage,
                         Integer failureRowNumber,
                         String failureColumn,
                         String failureMessage,
                         long durationNanos) {
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.rowType = Objects.requireNonNull(rowType, "rowType must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.resolvedSchema = List.copyOf(resolvedSchema == null ? List.of() : resolvedSchema);
        this.rejectedColumns = List.copyOf(rejectedColumns == null ? List.of() : rejectedColumns);
        this.logicalRecordCount = logicalRecordCount;
        this.loadedRowCount = loadedRowCount;
        this.success = success;
        this.failureStage = failureStage;
        this.failureRowNumber = failureRowNumber;
        this.failureColumn = failureColumn;
        this.failureMessage = failureMessage;
        this.durationNanos = durationNanos;
    }

    public Path path() {
        return path;
    }

    public Class<?> rowType() {
        return rowType;
    }

    public CsvOptions options() {
        return options;
    }

    public List<String> resolvedSchema() {
        return resolvedSchema;
    }

    public List<String> rejectedColumns() {
        return rejectedColumns;
    }

    public int logicalRecordCount() {
        return logicalRecordCount;
    }

    public int loadedRowCount() {
        return loadedRowCount;
    }

    public boolean success() {
        return success;
    }

    public String failureStage() {
        return failureStage;
    }

    public Integer failureRowNumber() {
        return failureRowNumber;
    }

    public String failureColumn() {
        return failureColumn;
    }

    public String failureMessage() {
        return failureMessage;
    }

    public long durationNanos() {
        return durationNanos;
    }
}
