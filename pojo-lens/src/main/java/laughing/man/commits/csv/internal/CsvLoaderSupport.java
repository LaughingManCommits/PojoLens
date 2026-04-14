package laughing.man.commits.csv.internal;

import laughing.man.commits.csv.CsvCoercionPolicy;
import laughing.man.commits.csv.CsvLoadException;
import laughing.man.commits.csv.CsvLoadReport;
import laughing.man.commits.csv.CsvLoadResult;
import laughing.man.commits.csv.CsvOptions;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.StringUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Internal typed CSV loader that keeps the CSV surface as a boundary adapter.
 */
public final class CsvLoaderSupport {

    private CsvLoaderSupport() {
    }

    public static <T> List<T> read(Path path, Class<T> rowType, CsvOptions options) {
        return readWithReport(path, rowType, options).rows();
    }

    public static <T> CsvLoadResult<T> readWithReport(Path path, Class<T> rowType, CsvOptions options) {
        long started = System.nanoTime();
        CsvLoadReportState reportState = new CsvLoadReportState(path, rowType, options);
        validatePreconditions(path, rowType, options, reportState, started);

        return readWithReportValidated(path, rowType, options, reportState, started);
    }

    private static <T> CsvLoadResult<T> readWithReportValidated(Path path,
                                                                Class<T> rowType,
                                                                CsvOptions options,
                                                                CsvLoadReportState reportState,
                                                                long started) {
        try {
            LinkedHashMap<String, CsvColumnBinding> bindingsByName = resolveBindings(rowType);
            if (bindingsByName.isEmpty()) {
                throw new CsvLoadFailure(
                        "schema",
                        null,
                        null,
                        "CSV row type " + rowType.getSimpleName() + " exposes no bindable fields",
                        List.of(),
                        List.of()
                );
            }

            List<CsvRecord> records = parseRecords(path, options, reportState);
            if (records.isEmpty()) {
                return new CsvLoadResult<>(List.of(), reportState.success(System.nanoTime() - started));
            }

            List<String> schema;
            List<CsvColumnBinding> bindings;
            int dataStartIndex = 0;

            if (options.header()) {
                CsvRecord header = records.get(0);
                schema = resolveHeaderSchema(header, bindingsByName, rowType);
                bindings = bindingsForSchema(schema, bindingsByName);
                dataStartIndex = 1;
            } else {
                schema = List.copyOf(bindingsByName.keySet());
                bindings = List.copyOf(bindingsByName.values());
            }
            reportState.resolvedSchema(schema);

            if (schema.isEmpty() || dataStartIndex >= records.size()) {
                return new CsvLoadResult<>(List.of(), reportState.success(System.nanoTime() - started));
            }

            CsvCoercionPolicy coercionPolicy = options.coercionPolicy();
            ArrayList<Object[]> typedRows = new ArrayList<>(Math.max(0, records.size() - dataStartIndex));
            for (int i = dataStartIndex; i < records.size(); i++) {
                CsvRecord record = records.get(i);
                validateColumnCount(record, schema.size());
                Object[] values = new Object[schema.size()];
                for (int columnIndex = 0; columnIndex < schema.size(); columnIndex++) {
                    CsvColumnBinding binding = bindings.get(columnIndex);
                    values[columnIndex] = coerce(record, binding, record.values().get(columnIndex), coercionPolicy);
                }
                typedRows.add(values);
                reportState.loadedRowCount(typedRows.size());
            }

            List<T> rows = typedRows.isEmpty() ? List.of() : ReflectionUtil.toClassList(rowType, typedRows, schema);
            return new CsvLoadResult<>(rows, reportState.success(System.nanoTime() - started));
        } catch (CsvLoadFailure failure) {
            CsvLoadReport report = reportState.failure(failure, System.nanoTime() - started);
            throw new CsvLoadException(failure.getMessage(), report, failure.getCause());
        } catch (UncheckedIOException ex) {
            CsvLoadReport report = reportState.failure(
                    new CsvLoadFailure("parse", null, null, ex.getMessage(), List.of(), List.of(), ex),
                    System.nanoTime() - started
            );
            throw new CsvLoadException(ex.getMessage(), report, ex);
        } catch (RuntimeException ex) {
            CsvLoadReport report = reportState.failure(
                    new CsvLoadFailure("materialize", null, null, ex.getMessage(), List.of(), List.of(), ex),
                    System.nanoTime() - started
            );
            throw new CsvLoadException(ex.getMessage(), report, ex);
        }
    }

    private static void validatePreconditions(Path path,
                                              Class<?> rowType,
                                              CsvOptions options,
                                              CsvLoadReportState reportState,
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

    private static CsvLoadException preflightFailure(CsvLoadReportState reportState,
                                                     long started,
                                                     String message) {
        CsvLoadReport report = reportState.failure(
                new CsvLoadFailure("preflight", null, null, message, List.of(), List.of()),
                System.nanoTime() - started
        );
        return new CsvLoadException(message, report);
    }

    private static List<CsvRecord> parseRecords(Path path, CsvOptions options, CsvLoadReportState reportState) {
        ArrayList<CsvRecord> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            boolean firstRecord = true;
            int lineNumber = 0;
            int recordStartLine = 0;
            ArrayList<String> values = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            boolean expectingDelimiter = false;
            boolean recordOpen = false;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (!recordOpen) {
                    recordOpen = true;
                    recordStartLine = lineNumber;
                } else if (inQuotes) {
                    current.append('\n');
                }

                for (int i = 0; i < line.length(); i++) {
                    char currentChar = line.charAt(i);
                    if (inQuotes) {
                        if (currentChar == '"') {
                            if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                                current.append('"');
                                i++;
                            } else {
                                inQuotes = false;
                                expectingDelimiter = true;
                            }
                        } else {
                            current.append(currentChar);
                        }
                        continue;
                    }

                    if (expectingDelimiter) {
                        if (currentChar == options.delimiter()) {
                            values.add(current.toString());
                            current.setLength(0);
                            expectingDelimiter = false;
                            continue;
                        }
                        if (Character.isWhitespace(currentChar)) {
                            current.append(currentChar);
                            continue;
                        }
                        throw new CsvLoadFailure(
                                "parse",
                                recordStartLine,
                                null,
                                "CSV row " + recordStartLine + " has invalid characters after closing quote",
                                List.of(),
                                List.of()
                        );
                    }

                    if (currentChar == options.delimiter()) {
                        values.add(current.toString());
                        current.setLength(0);
                        continue;
                    }
                    if (currentChar == '"') {
                        if (current.length() != 0) {
                            throw new CsvLoadFailure(
                                    "parse",
                                    recordStartLine,
                                    null,
                                    "CSV row " + recordStartLine + " has an unexpected quote inside an unquoted field",
                                    List.of(),
                                    List.of()
                            );
                        }
                        inQuotes = true;
                        continue;
                    }
                    current.append(currentChar);
                }

                if (inQuotes) {
                    continue;
                }

                CsvRecord record = finalizeRecord(recordStartLine, values, current, options, firstRecord);
                if (record != null) {
                    records.add(record);
                    reportState.logicalRecordCount(records.size());
                    firstRecord = false;
                }
                values = new ArrayList<>();
                current = new StringBuilder();
                expectingDelimiter = false;
                recordOpen = false;
            }
            if (recordOpen && inQuotes) {
                throw new CsvLoadFailure(
                        "parse",
                        recordStartLine,
                        null,
                        "CSV row " + recordStartLine + " has an unmatched quote",
                        List.of(),
                        List.of()
                );
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read CSV file '" + path + "'", ex);
        }
        return List.copyOf(records);
    }

    private static CsvRecord finalizeRecord(int lineNumber,
                                            List<String> values,
                                            StringBuilder current,
                                            CsvOptions options,
                                            boolean firstRecord) {
        ArrayList<String> normalized = new ArrayList<>(values.size() + 1);
        values.add(current.toString());
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (firstRecord && i == 0) {
                value = stripBom(value);
            }
            normalized.add(options.trim() ? value.trim() : value);
        }
        if (options.skipEmptyLines() && isBlankRecord(normalized)) {
            return null;
        }
        return new CsvRecord(lineNumber, List.copyOf(normalized));
    }

    private static LinkedHashMap<String, CsvColumnBinding> resolveBindings(Class<?> rowType) {
        LinkedHashSet<String> fieldNames = new LinkedHashSet<>(ReflectionUtil.collectQueryableFieldNames(rowType));
        LinkedHashMap<String, CsvColumnBinding> bindings = new LinkedHashMap<>(fieldNames.size());
        for (String fieldName : fieldNames) {
            CsvColumnBinding binding = resolveBinding(rowType, fieldName, fieldName);
            if (binding != null) {
                bindings.put(fieldName, binding);
            }
        }
        return bindings;
    }

    private static CsvColumnBinding resolveBinding(Class<?> rowType, String fieldName, String columnName) {
        String[] parts = fieldName.split("\\.");
        Class<?> currentType = rowType;
        Class<?> wrappedLeafType = null;
        boolean primitive = false;

        for (String part : parts) {
            Field field = findField(currentType, part);
            if (field == null) {
                return null;
            }
            primitive = field.getType().isPrimitive();
            wrappedLeafType = wrapPrimitive(field.getType());
            currentType = wrappedLeafType;
        }

        if (wrappedLeafType == null) {
            return null;
        }
        return new CsvColumnBinding(fieldName, columnName, wrappedLeafType, primitive);
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static List<String> resolveHeaderSchema(CsvRecord header,
                                                    Map<String, CsvColumnBinding> bindingsByName,
                                                    Class<?> rowType) {
        ArrayList<String> schema = new ArrayList<>(header.values().size());
        LinkedHashSet<String> seen = new LinkedHashSet<>(header.values().size());
        for (String headerName : header.values()) {
            if (StringUtil.isNullOrBlank(headerName)) {
                throw new CsvLoadFailure(
                        "header",
                        header.lineNumber(),
                        null,
                        "CSV header columns must not be blank",
                        List.of(),
                        List.of()
                );
            }
            if (!seen.add(headerName)) {
                throw new CsvLoadFailure(
                        "header",
                        header.lineNumber(),
                        headerName,
                        "CSV header column '" + headerName + "' is duplicated",
                        List.of(headerName),
                        List.of()
                );
            }
            if (!bindingsByName.containsKey(headerName)) {
                throw new CsvLoadFailure(
                        "header",
                        header.lineNumber(),
                        headerName,
                        "CSV header column '" + headerName + "' does not map to " + rowType.getSimpleName(),
                        List.of(headerName),
                        List.of()
                );
            }
            schema.add(headerName);
        }
        ArrayList<String> missingRequired = new ArrayList<>();
        for (CsvColumnBinding binding : bindingsByName.values()) {
            if (binding.primitive() && !seen.contains(binding.columnName())) {
                missingRequired.add(binding.columnName());
            }
        }
        if (!missingRequired.isEmpty()) {
            throw new CsvLoadFailure(
                    "header",
                    header.lineNumber(),
                    null,
                    "CSV header for " + rowType.getSimpleName()
                            + " is missing required columns: " + String.join(", ", missingRequired),
                    List.of(),
                    missingRequired
            );
        }
        return List.copyOf(schema);
    }

    private static List<CsvColumnBinding> bindingsForSchema(List<String> schema,
                                                            Map<String, CsvColumnBinding> bindingsByName) {
        ArrayList<CsvColumnBinding> bindings = new ArrayList<>(schema.size());
        for (String fieldName : schema) {
            CsvColumnBinding binding = bindingsByName.get(fieldName);
            if (binding == null) {
                throw new CsvLoadFailure(
                        "header",
                        null,
                        fieldName,
                        "CSV schema field '" + fieldName + "' is not queryable",
                        List.of(fieldName),
                        List.of()
                );
            }
            bindings.add(new CsvColumnBinding(binding.fieldName(), fieldName, binding.valueType(), binding.primitive()));
        }
        return List.copyOf(bindings);
    }

    private static void validateColumnCount(CsvRecord record, int expectedColumns) {
        if (record.values().size() != expectedColumns) {
            throw new CsvLoadFailure(
                    "parse",
                    record.lineNumber(),
                    null,
                    "CSV row " + record.lineNumber() + " has " + record.values().size()
                            + " columns; expected " + expectedColumns,
                    List.of(),
                    List.of()
            );
        }
    }

    private static Object coerce(CsvRecord record,
                                 CsvColumnBinding binding,
                                 String rawValue,
                                 CsvCoercionPolicy coercionPolicy) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue.isEmpty()) {
            if (binding.valueType() == String.class) {
                return coercionPolicy.blankStringAsNull() ? null : rawValue;
            }
            if (binding.primitive()) {
                throw new CsvLoadFailure(
                        "coerce",
                        record.lineNumber(),
                        binding.columnName(),
                        "CSV row " + record.lineNumber() + " column " + binding.columnName()
                                + ": blank value is not allowed for primitive targets",
                        List.of(),
                        List.of()
                );
            }
            return null;
        }
        if (coercionPolicy.isNullToken(rawValue)) {
            if (binding.primitive()) {
                throw new CsvLoadFailure(
                        "coerce",
                        record.lineNumber(),
                        binding.columnName(),
                        "CSV row " + record.lineNumber() + " column " + binding.columnName()
                                + ": null value is not allowed for primitive targets",
                        List.of(),
                        List.of()
                );
            }
            return null;
        }

        Class<?> targetType = binding.valueType();
        try {
            if (targetType == String.class) {
                return rawValue;
            }
            if (targetType == Integer.class) {
                return Integer.valueOf(normalizeNumericValue(rawValue, coercionPolicy));
            }
            if (targetType == Long.class) {
                return Long.valueOf(normalizeNumericValue(rawValue, coercionPolicy));
            }
            if (targetType == Double.class) {
                return Double.valueOf(normalizeNumericValue(rawValue, coercionPolicy));
            }
            if (targetType == Float.class) {
                return Float.valueOf(normalizeNumericValue(rawValue, coercionPolicy));
            }
            if (targetType == Short.class) {
                return Short.valueOf(normalizeNumericValue(rawValue, coercionPolicy));
            }
            if (targetType == Byte.class) {
                return Byte.valueOf(normalizeNumericValue(rawValue, coercionPolicy));
            }
            if (targetType == Boolean.class) {
                Boolean parsed = StringUtil.parseBoolStrict(rawValue);
                if (parsed == null) {
                    throw new IllegalArgumentException("Invalid boolean");
                }
                return parsed;
            }
            if (targetType == Character.class) {
                if (rawValue.length() != 1) {
                    throw new IllegalArgumentException("Invalid character");
                }
                return rawValue.charAt(0);
            }
            if (targetType == Instant.class) {
                return parseInstant(rawValue, coercionPolicy);
            }
            if (targetType == LocalDate.class) {
                return parseLocalDate(rawValue, coercionPolicy);
            }
            if (targetType == LocalDateTime.class) {
                return parseLocalDateTime(rawValue, coercionPolicy);
            }
            if (targetType == OffsetDateTime.class) {
                return OffsetDateTime.parse(rawValue);
            }
            if (targetType == ZonedDateTime.class) {
                return ZonedDateTime.parse(rawValue);
            }
            if (targetType == Date.class) {
                return Date.from(parseDateInstant(rawValue, coercionPolicy));
            }
            if (targetType.isEnum()) {
                return parseEnumValue(targetType, rawValue, coercionPolicy);
            }
        } catch (CsvLoadFailure failure) {
            throw failure;
        } catch (RuntimeException ex) {
            throw parseError(record, binding, rawValue, targetType, ex);
        }

        throw new CsvLoadFailure(
                "coerce",
                record.lineNumber(),
                binding.columnName(),
                "CSV column '" + binding.columnName() + "' maps to unsupported type " + targetType.getSimpleName(),
                List.of(),
                List.of()
        );
    }

    private static String normalizeNumericValue(String rawValue, CsvCoercionPolicy coercionPolicy) {
        if (!coercionPolicy.numericNormalizationEnabled()) {
            return rawValue;
        }
        String normalized = rawValue.replace(String.valueOf(coercionPolicy.groupingSeparator()), "");
        if (coercionPolicy.decimalSeparator() != '.') {
            normalized = normalized.replace(coercionPolicy.decimalSeparator(), '.');
        }
        return normalized;
    }

    private static LocalDate parseLocalDate(String rawValue, CsvCoercionPolicy coercionPolicy) {
        try {
            return LocalDate.parse(rawValue);
        } catch (DateTimeParseException isoFailure) {
            for (var formatter : coercionPolicy.dateFormatters()) {
                try {
                    return LocalDate.parse(rawValue, formatter);
                } catch (DateTimeParseException ignored) {
                }
            }
            throw isoFailure;
        }
    }

    private static LocalDateTime parseLocalDateTime(String rawValue, CsvCoercionPolicy coercionPolicy) {
        try {
            return LocalDateTime.parse(rawValue);
        } catch (DateTimeParseException isoFailure) {
            for (var formatter : coercionPolicy.dateTimeFormatters()) {
                try {
                    return LocalDateTime.parse(rawValue, formatter);
                } catch (DateTimeParseException ignored) {
                }
            }
            throw isoFailure;
        }
    }

    private static Instant parseInstant(String rawValue, CsvCoercionPolicy coercionPolicy) {
        try {
            return Instant.parse(rawValue);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(rawValue).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        for (var formatter : coercionPolicy.dateTimeFormatters()) {
            try {
                return LocalDateTime.parse(rawValue, formatter).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }
        for (var formatter : coercionPolicy.dateFormatters()) {
            try {
                return LocalDate.parse(rawValue, formatter).atStartOfDay(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }
        return OffsetDateTime.parse(rawValue).toInstant();
    }

    private static Instant parseDateInstant(String rawValue, CsvCoercionPolicy coercionPolicy) {
        try {
            return Instant.parse(rawValue);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(rawValue).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(rawValue).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return parseLocalDateTime(rawValue, coercionPolicy).atZone(ZoneId.systemDefault()).toInstant();
        } catch (RuntimeException ignored) {
        }
        return parseLocalDate(rawValue, coercionPolicy).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private static Object parseEnumValue(Class<?> targetType, String rawValue, CsvCoercionPolicy coercionPolicy) {
        @SuppressWarnings("rawtypes")
        Class<? extends Enum> enumType = targetType.asSubclass(Enum.class);
        if (!coercionPolicy.enumCaseInsensitive()) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Enum<?> parsed = Enum.valueOf(enumType, rawValue);
            return parsed;
        }
        Object[] constants = enumType.getEnumConstants();
        for (Object constant : constants) {
            Enum<?> candidate = (Enum<?>) constant;
            if (candidate.name().equalsIgnoreCase(rawValue)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Invalid enum constant");
    }

    private static CsvLoadFailure parseError(CsvRecord record,
                                             CsvColumnBinding binding,
                                             String rawValue,
                                             Class<?> targetType,
                                             RuntimeException cause) {
        return new CsvLoadFailure(
                "coerce",
                record.lineNumber(),
                binding.columnName(),
                "CSV row " + record.lineNumber() + " column " + binding.columnName()
                        + ": cannot parse '" + rawValue + "' as " + targetType.getSimpleName(),
                List.of(),
                List.of(),
                cause
        );
    }

    private static boolean isBlankRecord(List<String> values) {
        if (values.isEmpty()) {
            return true;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String stripBom(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static final class CsvLoadReportState {
        private final Path path;
        private final Class<?> rowType;
        private final CsvOptions options;
        private List<String> resolvedSchema = List.of();
        private List<String> rejectedColumns = List.of();
        private List<String> missingColumns = List.of();
        private int logicalRecordCount;
        private int dataRecordCount;
        private int loadedRowCount;

        private CsvLoadReportState(Path path, Class<?> rowType, CsvOptions options) {
            this.path = path;
            this.rowType = rowType;
            this.options = options;
        }

        private void resolvedSchema(List<String> value) {
            this.resolvedSchema = List.copyOf(value == null ? List.of() : value);
        }

        private void rejectedColumns(List<String> value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            LinkedHashSet<String> merged = new LinkedHashSet<>(rejectedColumns);
            merged.addAll(value);
            this.rejectedColumns = List.copyOf(merged);
        }

        private void missingColumns(List<String> value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            LinkedHashSet<String> merged = new LinkedHashSet<>(missingColumns);
            merged.addAll(value);
            this.missingColumns = List.copyOf(merged);
        }

        private void logicalRecordCount(int value) {
            this.logicalRecordCount = value;
            this.dataRecordCount = options != null && options.header() ? Math.max(0, value - 1) : value;
        }

        private void loadedRowCount(int value) {
            this.loadedRowCount = value;
        }

        private CsvLoadReport success(long durationNanos) {
            return new CsvLoadReport(
                    path,
                    rowType,
                    options,
                    resolvedSchema,
                    rejectedColumns,
                    missingColumns,
                    logicalRecordCount,
                    dataRecordCount,
                    loadedRowCount,
                    true,
                    null,
                    null,
                    null,
                    null,
                    durationNanos
            );
        }

        private CsvLoadReport failure(CsvLoadFailure failure, long durationNanos) {
            rejectedColumns(failure.rejectedColumns());
            missingColumns(failure.missingColumns());
            return new CsvLoadReport(
                    path,
                    rowType,
                    options,
                    resolvedSchema,
                    rejectedColumns,
                    missingColumns,
                    logicalRecordCount,
                    dataRecordCount,
                    loadedRowCount,
                    false,
                    failure.stage(),
                    failure.rowNumber(),
                    failure.columnName(),
                    failure.getMessage(),
                    durationNanos
            );
        }
    }

    private static final class CsvLoadFailure extends RuntimeException {
        private final String stage;
        private final Integer rowNumber;
        private final String columnName;
        private final List<String> rejectedColumns;
        private final List<String> missingColumns;

        private CsvLoadFailure(String stage,
                               Integer rowNumber,
                               String columnName,
                               String message,
                               List<String> rejectedColumns,
                               List<String> missingColumns) {
            super(message);
            this.stage = stage;
            this.rowNumber = rowNumber;
            this.columnName = columnName;
            this.rejectedColumns = List.copyOf(rejectedColumns == null ? List.of() : rejectedColumns);
            this.missingColumns = List.copyOf(missingColumns == null ? List.of() : missingColumns);
        }

        private CsvLoadFailure(String stage,
                               Integer rowNumber,
                               String columnName,
                               String message,
                               List<String> rejectedColumns,
                               List<String> missingColumns,
                               Throwable cause) {
            super(message, cause);
            this.stage = stage;
            this.rowNumber = rowNumber;
            this.columnName = columnName;
            this.rejectedColumns = List.copyOf(rejectedColumns == null ? List.of() : rejectedColumns);
            this.missingColumns = List.copyOf(missingColumns == null ? List.of() : missingColumns);
        }

        private String stage() {
            return stage;
        }

        private Integer rowNumber() {
            return rowNumber;
        }

        private String columnName() {
            return columnName;
        }

        private List<String> rejectedColumns() {
            return rejectedColumns;
        }

        private List<String> missingColumns() {
            return missingColumns;
        }
    }

    private record CsvRecord(int lineNumber, List<String> values) {
    }

    private record CsvColumnBinding(String fieldName, String columnName, Class<?> valueType, boolean primitive) {
    }
}
