package laughing.man.commits.csv.internal;

import laughing.man.commits.csv.CsvCoercionPolicy;
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
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (rowType == null) {
            throw new IllegalArgumentException("rowType must not be null");
        }
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("path must point to an existing file");
        }

        LinkedHashMap<String, CsvColumnBinding> bindingsByName = resolveBindings(rowType);
        if (bindingsByName.isEmpty()) {
            return List.of();
        }

        List<CsvRecord> records = parseRecords(path, options);
        if (records.isEmpty()) {
            return List.of();
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

        if (schema.isEmpty() || dataStartIndex >= records.size()) {
            return List.of();
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
        }

        return typedRows.isEmpty() ? List.of() : ReflectionUtil.toClassList(rowType, typedRows, schema);
    }

    private static List<CsvRecord> parseRecords(Path path, CsvOptions options) {
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
                        throw new IllegalArgumentException(
                                "CSV row " + recordStartLine + " has invalid characters after closing quote"
                        );
                    }

                    if (currentChar == options.delimiter()) {
                        values.add(current.toString());
                        current.setLength(0);
                        continue;
                    }
                    if (currentChar == '"') {
                        if (current.length() != 0) {
                            throw new IllegalArgumentException(
                                    "CSV row " + recordStartLine + " has an unexpected quote inside an unquoted field"
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
                    firstRecord = false;
                }
                values = new ArrayList<>();
                current = new StringBuilder();
                expectingDelimiter = false;
                recordOpen = false;
            }
            if (recordOpen && inQuotes) {
                throw new IllegalArgumentException("CSV row " + recordStartLine + " has an unmatched quote");
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
        collectEnumFieldNames(rowType, "", fieldNames, new LinkedHashSet<>());
        LinkedHashMap<String, CsvColumnBinding> bindings = new LinkedHashMap<>(fieldNames.size());
        for (String fieldName : fieldNames) {
            CsvColumnBinding binding = resolveBinding(rowType, fieldName, fieldName);
            if (binding != null) {
                bindings.put(fieldName, binding);
            }
        }
        return bindings;
    }

    private static void collectEnumFieldNames(Class<?> type,
                                              String prefix,
                                              LinkedHashSet<String> fieldNames,
                                              LinkedHashSet<Class<?>> pathTypes) {
        if (type == null || !pathTypes.add(type)) {
            return;
        }
        for (Field field : mutableFields(type)) {
            Class<?> fieldType = field.getType();
            String qualifiedName = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();
            if (fieldType.isEnum()) {
                fieldNames.add(qualifiedName);
                continue;
            }
            if (isNestedPojoType(fieldType)) {
                collectEnumFieldNames(fieldType, qualifiedName, fieldNames, pathTypes);
            }
        }
        pathTypes.remove(type);
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

    private static List<Field> mutableFields(Class<?> type) {
        ArrayList<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            fields.addAll(ReflectionUtil.getFields(current));
            current = current.getSuperclass();
        }
        return List.copyOf(fields);
    }

    private static boolean isNestedPojoType(Class<?> type) {
        if (type == null || ReflectionUtil.isSimpleType(type) || type.isEnum() || type.isArray()) {
            return false;
        }
        Package pkg = type.getPackage();
        if (pkg == null) {
            return true;
        }
        String packageName = pkg.getName();
        return !packageName.startsWith("java.")
                && !packageName.startsWith("javax.")
                && !packageName.startsWith("jdk.");
    }

    private static List<String> resolveHeaderSchema(CsvRecord header,
                                                    Map<String, CsvColumnBinding> bindingsByName,
                                                    Class<?> rowType) {
        ArrayList<String> schema = new ArrayList<>(header.values().size());
        LinkedHashSet<String> seen = new LinkedHashSet<>(header.values().size());
        for (String headerName : header.values()) {
            if (StringUtil.isNullOrBlank(headerName)) {
                throw new IllegalArgumentException("CSV header columns must not be blank");
            }
            if (!seen.add(headerName)) {
                throw new IllegalArgumentException("CSV header column '" + headerName + "' is duplicated");
            }
            if (!bindingsByName.containsKey(headerName)) {
                throw new IllegalArgumentException(
                        "CSV header column '" + headerName + "' does not map to " + rowType.getSimpleName()
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
            throw new IllegalArgumentException(
                    "CSV header for " + rowType.getSimpleName()
                            + " is missing required columns: " + String.join(", ", missingRequired)
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
                throw new IllegalArgumentException("CSV schema field '" + fieldName + "' is not queryable");
            }
            bindings.add(new CsvColumnBinding(binding.fieldName(), fieldName, binding.valueType(), binding.primitive()));
        }
        return List.copyOf(bindings);
    }

    private static void validateColumnCount(CsvRecord record, int expectedColumns) {
        if (record.values().size() != expectedColumns) {
            throw new IllegalArgumentException(
                    "CSV row " + record.lineNumber() + " has " + record.values().size()
                            + " columns; expected " + expectedColumns
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
                throw new IllegalArgumentException(
                        "CSV row " + record.lineNumber() + " column " + binding.columnName()
                                + ": blank value is not allowed for primitive targets"
                );
            }
            return null;
        }
        if (coercionPolicy.isNullToken(rawValue)) {
            if (binding.primitive()) {
                throw new IllegalArgumentException(
                        "CSV row " + record.lineNumber() + " column " + binding.columnName()
                                + ": null value is not allowed for primitive targets"
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
        } catch (RuntimeException ex) {
            throw parseError(record, binding, rawValue, targetType, ex);
        }

        throw new IllegalArgumentException(
                "CSV column '" + binding.columnName() + "' maps to unsupported type " + targetType.getSimpleName()
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

    private static IllegalArgumentException parseError(CsvRecord record,
                                                       CsvColumnBinding binding,
                                                       String rawValue,
                                                       Class<?> targetType,
                                                       RuntimeException cause) {
        return new IllegalArgumentException(
                "CSV row " + record.lineNumber() + " column " + binding.columnName()
                        + ": cannot parse '" + rawValue + "' as " + targetType.getSimpleName(),
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

    private record CsvRecord(int lineNumber, List<String> values) {
    }

    private record CsvColumnBinding(String fieldName, String columnName, Class<?> valueType, boolean primitive) {
    }
}
