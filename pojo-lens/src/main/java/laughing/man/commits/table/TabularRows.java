package laughing.man.commits.table;

import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.SchemaIndexUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility for turning tabular/query rows into JSON-friendly ordered maps.
 */
public final class TabularRows {

    private TabularRows() {
    }

    public static List<Map<String, Object>> toMaps(List<?> rows, TabularSchema schema) {
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        List<String> columns = schema.names();
        for (Object row : rows) {
            mapped.add(toMap(row, columns));
        }
        return Collections.unmodifiableList(mapped);
    }

    public static Map<String, Object> firstRowAsMap(List<?> rows, TabularSchema schema) {
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(schema, "schema must not be null");
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }
        return toMap(rows.get(0), schema.names());
    }

    private static Map<String, Object> toMap(Object row, List<String> columns) {
        if (row == null || columns.isEmpty()) {
            return Collections.emptyMap();
        }
        if (row instanceof QueryRow queryRow) {
            return queryRowToMap(queryRow, columns);
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (String column : columns) {
            values.put(column, fieldValue(row, column));
        }
        return Collections.unmodifiableMap(values);
    }

    private static Map<String, Object> queryRowToMap(QueryRow row, List<String> columns) {
        Map<String, Integer> indexes = SchemaIndexUtil.indexQueryFields(row.getFields());
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (String column : columns) {
            int index = SchemaIndexUtil.findFieldIndex(indexes, column);
            values.put(column, index < 0 ? null : row.getValueAt(index));
        }
        return Collections.unmodifiableMap(values);
    }

    private static Object fieldValue(Object row, String fieldName) {
        try {
            return ReflectionUtil.getFieldValue(row, fieldName);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read tabular field '" + fieldName + "'", ex);
        }
    }
}
