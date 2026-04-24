package laughing.man.commits.sqllike;

import laughing.man.commits.util.ReflectionUtil;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * JDBC {@link ResultSet} materialization helper for host-owned pushdown bridges.
 */
public final class SqlLikeResultSetAdapter {

    private SqlLikeResultSetAdapter() {
    }

    /**
     * Reads all rows from the current {@link ResultSet} into mutable PojoLens
     * projection objects using column labels as field names. Labels may match
     * queryable field names exactly or via simple JDBC-style normalization
     * (`snake_case`, `kebab-case`, spaced labels, and case differences map to
     * camelCase field names).
     *
     * @param resultSet result set positioned before the first row
     * @param rowClass destination row class
     * @param <T> row type
     * @return materialized rows
     */
    public static <T> List<T> read(ResultSet resultSet, Class<T> rowClass) {
        Objects.requireNonNull(resultSet, "resultSet must not be null");
        Objects.requireNonNull(rowClass, "rowClass must not be null");
        try {
            List<String> schema = schema(resultSet.getMetaData(), rowClass);
            Map<String, Class<?>> fieldTypes = ReflectionUtil.collectQueryableFieldTypes(rowClass);
            List<Object[]> rows = rows(resultSet, schema, fieldTypes);
            return ReflectionUtil.toClassList(rowClass, rows, schema);
        } catch (SQLException ex) {
            throw new SqlLikePushdownException("Failed to read pushed ResultSet rows", ex);
        }
    }

    /**
     * Reads all rows and wraps them as a pushdown result.
     *
     * @param resultSet result set positioned before the first row
     * @param rowClass destination row class
     * @param pushedStages stages performed by the host adapter
     * @param <T> row type
     * @return pushdown result
     */
    public static <T> SqlLikePushdownResult<T> readPushed(ResultSet resultSet,
                                                          Class<T> rowClass,
                                                          Collection<String> pushedStages) {
        List<T> rows = read(resultSet, rowClass);
        return SqlLikePushdownResult.of(
                rows,
                pushedStages == null ? List.of() : pushedStages,
                -1,
                Map.of("adapter", "resultSet")
        );
    }

    private static List<String> schema(ResultSetMetaData metadata, Class<?> rowClass) throws SQLException {
        Objects.requireNonNull(metadata, "metadata must not be null");
        int columnCount = metadata.getColumnCount();
        Set<String> fieldNames = new LinkedHashSet<>(ReflectionUtil.collectQueryableFieldNames(rowClass));
        Map<String, String> normalizedFieldNames = normalizedFieldNames(fieldNames, rowClass);
        LinkedHashSet<String> seenLabels = new LinkedHashSet<>();
        List<String> schema = new ArrayList<>(columnCount);
        for (int column = 1; column <= columnCount; column++) {
            String label = columnLabel(metadata, column);
            if (!seenLabels.add(label)) {
                throw new SqlLikePushdownException("Duplicate ResultSet column label '" + label + "'");
            }
            String fieldName = resolveFieldName(label, fieldNames, normalizedFieldNames);
            if (fieldName == null) {
                throw new SqlLikePushdownException("ResultSet column label '" + label
                        + "' does not match a queryable field on " + rowClass.getSimpleName());
            }
            schema.add(fieldName);
        }
        return schema;
    }

    private static Map<String, String> normalizedFieldNames(Set<String> fieldNames, Class<?> rowClass) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (String fieldName : fieldNames) {
            String key = normalizeLabel(fieldName);
            String existing = normalized.putIfAbsent(key, fieldName);
            if (existing != null && !existing.equals(fieldName)) {
                throw new SqlLikePushdownException("Normalized ResultSet field mapping is ambiguous for "
                        + rowClass.getSimpleName() + ": '" + existing + "' and '" + fieldName + "'");
            }
        }
        return normalized;
    }

    private static String resolveFieldName(String label,
                                           Set<String> fieldNames,
                                           Map<String, String> normalizedFieldNames) {
        if (fieldNames.contains(label)) {
            return label;
        }
        return normalizedFieldNames.get(normalizeLabel(label));
    }

    private static String columnLabel(ResultSetMetaData metadata, int column) throws SQLException {
        String label = metadata.getColumnLabel(column);
        if (label == null || label.isBlank()) {
            label = metadata.getColumnName(column);
        }
        if (label == null || label.isBlank()) {
            throw new SqlLikePushdownException("ResultSet column " + column + " has no column label");
        }
        return label.trim();
    }

    private static String normalizeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isLetterOrDigit(current)) {
                normalized.append(Character.toLowerCase(current));
            }
        }
        return normalized.toString();
    }

    private static List<Object[]> rows(ResultSet resultSet,
                                       List<String> schema,
                                       Map<String, Class<?>> fieldTypes) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        while (resultSet.next()) {
            Object[] values = new Object[schema.size()];
            for (int column = 1; column <= schema.size(); column++) {
                String fieldName = schema.get(column - 1);
                values[column - 1] = readJdbcValue(resultSet, column, fieldTypes.get(fieldName));
            }
            rows.add(values);
        }
        return rows;
    }

    private static Object readJdbcValue(ResultSet resultSet, int column, Class<?> targetType) throws SQLException {
        Object rawValue = resultSet.getObject(column);
        if (rawValue == null || targetType == null) {
            return rawValue;
        }

        Class<?> wrappedTargetType = wrapPrimitive(targetType);
        if (wrappedTargetType.isInstance(rawValue)) {
            return rawValue;
        }

        if (wrappedTargetType == LocalDateTime.class) {
            if (rawValue instanceof Timestamp timestamp) {
                return timestamp.toLocalDateTime();
            }
            if (rawValue instanceof Date date) {
                return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
            }
        }
        if (wrappedTargetType == LocalDate.class) {
            if (rawValue instanceof java.sql.Date date) {
                return date.toLocalDate();
            }
            if (rawValue instanceof Timestamp timestamp) {
                return timestamp.toLocalDateTime().toLocalDate();
            }
            if (rawValue instanceof Date date) {
                return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
            }
        }
        if (wrappedTargetType == Instant.class) {
            if (rawValue instanceof Timestamp timestamp) {
                return timestamp.toInstant();
            }
            if (rawValue instanceof Date date) {
                return date.toInstant();
            }
        }
        if (wrappedTargetType == OffsetDateTime.class) {
            if (rawValue instanceof Timestamp timestamp) {
                return timestamp.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
            }
            if (rawValue instanceof Date date) {
                return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
            }
        }
        if (wrappedTargetType == ZonedDateTime.class) {
            if (rawValue instanceof Timestamp timestamp) {
                return timestamp.toInstant().atZone(ZoneId.systemDefault());
            }
            if (rawValue instanceof Date date) {
                return date.toInstant().atZone(ZoneId.systemDefault());
            }
        }
        if (wrappedTargetType.isEnum() && rawValue instanceof String value) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Enum<?> enumValue = Enum.valueOf((Class<? extends Enum>) wrappedTargetType.asSubclass(Enum.class), value);
            return enumValue;
        }

        return rawValue;
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
}
