package laughing.man.commits.files;

import java.nio.file.Path;
import java.util.List;

/**
 * Immutable diagnostics for a single JSON or JSONL load attempt.
 */
public final class JsonLoadReport {

    private final Path path;
    private final Class<?> rowType;
    private final JsonOptions options;
    private final List<String> resolvedSchema;
    private final List<String> rejectedFields;
    private final List<String> missingFields;
    private final int logicalRecordCount;
    private final int loadedRowCount;
    private final boolean success;
    private final String failureStage;
    private final Integer failureRowNumber;
    private final String failureField;
    private final String failureMessage;
    private final long durationNanos;

    public JsonLoadReport(Path path,
                          Class<?> rowType,
                          JsonOptions options,
                          List<String> resolvedSchema,
                          List<String> rejectedFields,
                          List<String> missingFields,
                          int logicalRecordCount,
                          int loadedRowCount,
                          boolean success,
                          String failureStage,
                          Integer failureRowNumber,
                          String failureField,
                          String failureMessage,
                          long durationNanos) {
        this.path = path;
        this.rowType = rowType;
        this.options = options;
        this.resolvedSchema = List.copyOf(resolvedSchema == null ? List.of() : resolvedSchema);
        this.rejectedFields = List.copyOf(rejectedFields == null ? List.of() : rejectedFields);
        this.missingFields = List.copyOf(missingFields == null ? List.of() : missingFields);
        this.logicalRecordCount = logicalRecordCount;
        this.loadedRowCount = loadedRowCount;
        this.success = success;
        this.failureStage = failureStage;
        this.failureRowNumber = failureRowNumber;
        this.failureField = failureField;
        this.failureMessage = failureMessage;
        this.durationNanos = durationNanos;
    }

    public Path path() {
        return path;
    }

    public Class<?> rowType() {
        return rowType;
    }

    public JsonOptions options() {
        return options;
    }

    public List<String> resolvedSchema() {
        return resolvedSchema;
    }

    public List<String> rejectedFields() {
        return rejectedFields;
    }

    public List<String> missingFields() {
        return missingFields;
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

    public String failureField() {
        return failureField;
    }

    public String failureMessage() {
        return failureMessage;
    }

    public long durationNanos() {
        return durationNanos;
    }
}
