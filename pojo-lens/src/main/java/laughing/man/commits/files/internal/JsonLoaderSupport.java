package laughing.man.commits.files.internal;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import laughing.man.commits.files.JsonLoadException;
import laughing.man.commits.files.JsonLoadReport;
import laughing.man.commits.files.JsonLoadResult;
import laughing.man.commits.files.JsonOptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Internal typed JSON and JSONL loader that keeps the file surface as a boundary adapter.
 */
public final class JsonLoaderSupport {

    private JsonLoaderSupport() {
    }

    public static <T> List<T> readJson(Path path, Class<T> rowType, JsonOptions options) {
        return readJsonWithReport(path, rowType, options).rows();
    }

    public static <T> JsonLoadResult<T> readJsonWithReport(Path path, Class<T> rowType, JsonOptions options) {
        return readWithReport(path, rowType, options, InputMode.JSON);
    }

    public static <T> List<T> readJsonLines(Path path, Class<T> rowType, JsonOptions options) {
        return readJsonLinesWithReport(path, rowType, options).rows();
    }

    public static <T> JsonLoadResult<T> readJsonLinesWithReport(Path path, Class<T> rowType, JsonOptions options) {
        return readWithReport(path, rowType, options, InputMode.JSONL);
    }

    private static <T> JsonLoadResult<T> readWithReport(Path path,
                                                        Class<T> rowType,
                                                        JsonOptions options,
                                                        InputMode inputMode) {
        long started = System.nanoTime();
        JsonLoadReportState reportState = new JsonLoadReportState(path, rowType, options);
        validatePreconditions(path, rowType, options, reportState, started);
        return readWithReportValidated(path, rowType, options, inputMode, reportState, started);
    }

    private static <T> JsonLoadResult<T> readWithReportValidated(Path path,
                                                                 Class<T> rowType,
                                                                 JsonOptions options,
                                                                 InputMode inputMode,
                                                                 JsonLoadReportState reportState,
                                                                 long started) {
        try {
            FileLoadSupport.RowSchema rowSchema = FileLoadSupport.rowSchema(rowType);
            if (rowSchema.fieldNames().isEmpty()) {
                throw new JsonLoadFailure(
                        "schema",
                        null,
                        null,
                        inputMode.label() + " row type " + rowType.getSimpleName() + " exposes no bindable fields",
                        List.of(),
                        List.of()
                );
            }

            ObjectMapper mapper = mapper(options);
            List<JsonRecord> records = inputMode == InputMode.JSON
                    ? parseJsonRecords(path, options, mapper, reportState)
                    : parseJsonLinesRecords(path, options, mapper, reportState);
            if (records.isEmpty()) {
                return new JsonLoadResult<>(List.of(), reportState.success(System.nanoTime() - started));
            }

            ArrayList<T> rows = new ArrayList<>(records.size());
            for (JsonRecord record : records) {
                RecordShape shape = resolveRecordShape(record, rowSchema, options, inputMode);
                reportState.resolvedSchema(shape.acceptedFields());
                T row = materializeRow(mapper, record, rowType, inputMode);
                rows.add(row);
                reportState.loadedRowCount(rows.size());
            }
            return new JsonLoadResult<>(rows, reportState.success(System.nanoTime() - started));
        } catch (JsonLoadFailure failure) {
            JsonLoadReport report = reportState.failure(failure, System.nanoTime() - started);
            throw new JsonLoadException(failure.getMessage(), report, failure.getCause());
        } catch (UncheckedIOException ex) {
            JsonLoadReport report = reportState.failure(
                    new JsonLoadFailure("parse", null, null, ex.getMessage(), List.of(), List.of(), ex),
                    System.nanoTime() - started
            );
            throw new JsonLoadException(ex.getMessage(), report, ex);
        } catch (RuntimeException ex) {
            JsonLoadReport report = reportState.failure(
                    new JsonLoadFailure("materialize", null, null, ex.getMessage(), List.of(), List.of(), ex),
                    System.nanoTime() - started
            );
            throw new JsonLoadException(ex.getMessage(), report, ex);
        }
    }

    private static void validatePreconditions(Path path,
                                              Class<?> rowType,
                                              JsonOptions options,
                                              JsonLoadReportState reportState,
                                              long started) {
        if (path == null) {
            throw preflightFailure(reportState, started, "path must not be null");
        }
        if (rowType == null) {
            throw preflightFailure(reportState, started, "rowType must not be null");
        }
        if (options == null) {
            throw preflightFailure(reportState, started, "options must not be null");
        }
        if (!Files.isRegularFile(path)) {
            throw preflightFailure(reportState, started, "path must point to an existing file");
        }
    }

    private static JsonLoadException preflightFailure(JsonLoadReportState reportState,
                                                      long started,
                                                      String message) {
        JsonLoadReport report = reportState.failure(
                new JsonLoadFailure("preflight", null, null, message, List.of(), List.of()),
                System.nanoTime() - started
        );
        return new JsonLoadException(message, report);
    }

    private static List<JsonRecord> parseJsonRecords(Path path,
                                                     JsonOptions options,
                                                     ObjectMapper mapper,
                                                     JsonLoadReportState reportState) {
        JsonNode root;
        try {
            root = mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read JSON file '" + path + "'", ex);
        }
        if (root == null || root.isNull()) {
            return List.of();
        }
        ArrayList<JsonRecord> records = new ArrayList<>();
        if (root.isArray()) {
            int index = 0;
            for (JsonNode element : root) {
                index++;
                if (!element.isObject()) {
                    throw new JsonLoadFailure(
                            "parse",
                            index,
                            null,
                            "JSON row " + index + " must be a JSON object",
                            List.of(),
                            List.of()
                    );
                }
                records.add(new JsonRecord(index, element));
            }
            reportState.logicalRecordCount(records.size());
            return List.copyOf(records);
        }
        if (root.isObject()) {
            if (!options.allowSingleObject()) {
                throw new JsonLoadFailure(
                        "parse",
                        1,
                        null,
                        "JSON root must be an array when allowSingleObject is disabled",
                        List.of(),
                        List.of()
                );
            }
            records.add(new JsonRecord(1, root));
            reportState.logicalRecordCount(1);
            return List.copyOf(records);
        }
        throw new JsonLoadFailure(
                "parse",
                1,
                null,
                "JSON root must be an object or array of objects",
                List.of(),
                List.of()
        );
    }

    private static List<JsonRecord> parseJsonLinesRecords(Path path,
                                                          JsonOptions options,
                                                          ObjectMapper mapper,
                                                          JsonLoadReportState reportState) {
        ArrayList<JsonRecord> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    if (options.skipEmptyLines()) {
                        continue;
                    }
                    throw new JsonLoadFailure(
                            "parse",
                            lineNumber,
                            null,
                            "JSONL line " + lineNumber + " must not be blank",
                            List.of(),
                            List.of()
                    );
                }
                JsonNode node = parseJsonLine(line, lineNumber, mapper);
                if (!node.isObject()) {
                    throw new JsonLoadFailure(
                            "parse",
                            lineNumber,
                            null,
                            "JSONL line " + lineNumber + " must contain a JSON object",
                            List.of(),
                            List.of()
                    );
                }
                records.add(new JsonRecord(lineNumber, node));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read JSONL file '" + path + "'", ex);
        }
        reportState.logicalRecordCount(records.size());
        return List.copyOf(records);
    }

    private static JsonNode parseJsonLine(String line, int lineNumber, ObjectMapper mapper) {
        try {
            return mapper.readTree(line);
        } catch (JsonProcessingException ex) {
            throw parseFailure("JSONL", lineNumber, ex);
        }
    }

    private static RecordShape resolveRecordShape(JsonRecord record,
                                                  FileLoadSupport.RowSchema rowSchema,
                                                  JsonOptions options,
                                                  InputMode inputMode) {
        LinkedHashSet<String> flattenedFields = new LinkedHashSet<>();
        flattenFieldNames(null, record.node(), flattenedFields);

        ArrayList<String> acceptedFields = new ArrayList<>();
        ArrayList<String> rejectedFields = new ArrayList<>();
        Set<String> knownFields = Set.copyOf(rowSchema.fieldNames());
        for (String fieldName : flattenedFields) {
            if (knownFields.contains(fieldName)) {
                acceptedFields.add(fieldName);
            } else {
                rejectedFields.add(fieldName);
            }
        }
        if (!rejectedFields.isEmpty() && options.failOnUnknownProperties()) {
            throw new JsonLoadFailure(
                    "schema",
                    record.rowNumber(),
                    rejectedFields.get(0),
                    inputMode.label() + " row " + record.rowNumber()
                            + " contains unmapped fields: " + String.join(", ", rejectedFields),
                    rejectedFields,
                    List.of()
            );
        }

        ArrayList<String> missingFields = new ArrayList<>();
        for (String primitiveField : rowSchema.primitiveFieldNames()) {
            if (!flattenedFields.contains(primitiveField)) {
                missingFields.add(primitiveField);
            }
        }
        if (!missingFields.isEmpty()) {
            throw new JsonLoadFailure(
                    "schema",
                    record.rowNumber(),
                    null,
                    inputMode.label() + " row " + record.rowNumber()
                            + " is missing required fields: " + String.join(", ", missingFields),
                    List.of(),
                    missingFields
            );
        }
        return new RecordShape(acceptedFields);
    }

    private static <T> T materializeRow(ObjectMapper mapper,
                                        JsonRecord record,
                                        Class<T> rowType,
                                        InputMode inputMode) {
        try {
            return mapper.treeToValue(record.node(), rowType);
        } catch (JsonProcessingException ex) {
            throw materializeFailure(inputMode, record.rowNumber(), ex);
        }
    }

    private static ObjectMapper mapper(JsonOptions options) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, options.failOnUnknownProperties());
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, options.enumCaseInsensitive());
        return mapper;
    }

    private static void flattenFieldNames(String prefix, JsonNode node, LinkedHashSet<String> target) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> flattenFieldNames(qualify(prefix, entry.getKey()), entry.getValue(), target));
            return;
        }
        if (prefix != null && !prefix.isBlank()) {
            target.add(prefix);
        }
    }

    private static String qualify(String prefix, String fieldName) {
        return (prefix == null || prefix.isEmpty()) ? fieldName : prefix + '.' + fieldName;
    }

    private static JsonLoadFailure parseFailure(String formatLabel, Integer rowNumber, JsonProcessingException ex) {
        JsonLocation location = ex.getLocation();
        Integer failureRowNumber = rowNumber;
        if (failureRowNumber == null && location != null && location.getLineNr() > 0) {
            failureRowNumber = location.getLineNr();
        }
        return new JsonLoadFailure(
                "parse",
                failureRowNumber,
                null,
                formatLabel + " parse failure: " + ex.getOriginalMessage(),
                List.of(),
                List.of(),
                ex
        );
    }

    private static JsonLoadFailure materializeFailure(InputMode inputMode,
                                                      int rowNumber,
                                                      JsonProcessingException ex) {
        String failureField = null;
        if (ex instanceof JsonMappingException mappingException) {
            failureField = extractFieldPath(mappingException);
        }
        return new JsonLoadFailure(
                "materialize",
                rowNumber,
                failureField,
                inputMode.label() + " row " + rowNumber
                        + (failureField == null ? "" : " field " + failureField)
                        + ": " + ex.getOriginalMessage(),
                List.of(),
                List.of(),
                ex
        );
    }

    private static String extractFieldPath(JsonMappingException exception) {
        if (exception.getPath() == null || exception.getPath().isEmpty()) {
            return null;
        }
        ArrayList<String> path = new ArrayList<>(exception.getPath().size());
        for (JsonMappingException.Reference reference : exception.getPath()) {
            if (reference.getFieldName() != null) {
                path.add(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.add("[" + reference.getIndex() + "]");
            }
        }
        if (path.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String part : path) {
            if (part.startsWith("[")) {
                builder.append(part);
                continue;
            }
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(part);
        }
        return builder.toString();
    }

    private enum InputMode {
        JSON("JSON"),
        JSONL("JSONL");

        private final String label;

        InputMode(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private record JsonRecord(int rowNumber, JsonNode node) {
    }

    private record RecordShape(List<String> acceptedFields) {
        private RecordShape(List<String> acceptedFields) {
            this.acceptedFields = List.copyOf(acceptedFields == null ? List.of() : acceptedFields);
        }
    }

    private static final class JsonLoadReportState {
        private final Path path;
        private final Class<?> rowType;
        private final JsonOptions options;
        private List<String> resolvedSchema = List.of();
        private List<String> rejectedFields = List.of();
        private List<String> missingFields = List.of();
        private int logicalRecordCount;
        private int loadedRowCount;

        private JsonLoadReportState(Path path, Class<?> rowType, JsonOptions options) {
            this.path = path;
            this.rowType = rowType;
            this.options = options;
        }

        private void resolvedSchema(List<String> value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            LinkedHashSet<String> merged = new LinkedHashSet<>(resolvedSchema);
            merged.addAll(value);
            this.resolvedSchema = List.copyOf(merged);
        }

        private void rejectedFields(List<String> value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            LinkedHashSet<String> merged = new LinkedHashSet<>(rejectedFields);
            merged.addAll(value);
            this.rejectedFields = List.copyOf(merged);
        }

        private void missingFields(List<String> value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            LinkedHashSet<String> merged = new LinkedHashSet<>(missingFields);
            merged.addAll(value);
            this.missingFields = List.copyOf(merged);
        }

        private void logicalRecordCount(int value) {
            this.logicalRecordCount = value;
        }

        private void loadedRowCount(int value) {
            this.loadedRowCount = value;
        }

        private JsonLoadReport success(long durationNanos) {
            return new JsonLoadReport(
                    path,
                    rowType,
                    options,
                    resolvedSchema,
                    rejectedFields,
                    missingFields,
                    logicalRecordCount,
                    loadedRowCount,
                    true,
                    null,
                    null,
                    null,
                    null,
                    durationNanos
            );
        }

        private JsonLoadReport failure(JsonLoadFailure failure, long durationNanos) {
            rejectedFields(failure.rejectedFields());
            missingFields(failure.missingFields());
            return new JsonLoadReport(
                    path,
                    rowType,
                    options,
                    resolvedSchema,
                    rejectedFields,
                    missingFields,
                    logicalRecordCount,
                    loadedRowCount,
                    false,
                    failure.stage(),
                    failure.rowNumber(),
                    failure.fieldName(),
                    failure.getMessage(),
                    durationNanos
            );
        }
    }

    private static final class JsonLoadFailure extends RuntimeException {
        private final String stage;
        private final Integer rowNumber;
        private final String fieldName;
        private final List<String> rejectedFields;
        private final List<String> missingFields;

        private JsonLoadFailure(String stage,
                                Integer rowNumber,
                                String fieldName,
                                String message,
                                List<String> rejectedFields,
                                List<String> missingFields) {
            super(message);
            this.stage = stage;
            this.rowNumber = rowNumber;
            this.fieldName = fieldName;
            this.rejectedFields = List.copyOf(rejectedFields == null ? List.of() : rejectedFields);
            this.missingFields = List.copyOf(missingFields == null ? List.of() : missingFields);
        }

        private JsonLoadFailure(String stage,
                                Integer rowNumber,
                                String fieldName,
                                String message,
                                List<String> rejectedFields,
                                List<String> missingFields,
                                Throwable cause) {
            super(message, cause);
            this.stage = stage;
            this.rowNumber = rowNumber;
            this.fieldName = fieldName;
            this.rejectedFields = List.copyOf(rejectedFields == null ? List.of() : rejectedFields);
            this.missingFields = List.copyOf(missingFields == null ? List.of() : missingFields);
        }

        private String stage() {
            return stage;
        }

        private Integer rowNumber() {
            return rowNumber;
        }

        private String fieldName() {
            return fieldName;
        }

        private List<String> rejectedFields() {
            return rejectedFields;
        }

        private List<String> missingFields() {
            return missingFields;
        }
    }
}
