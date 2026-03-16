package laughing.man.commits.util;

import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared exact-name schema index helpers for cold-path planning work.
 */
public final class SchemaIndexUtil {

    private SchemaIndexUtil() {
    }

    public static Map<String, Integer> indexFieldNames(List<String> fieldNames) {
        LinkedHashMap<String, Integer> fieldIndexes = new LinkedHashMap<>(
                CollectionUtil.expectedMapCapacity(fieldNames == null ? 0 : fieldNames.size())
        );
        if (fieldNames == null) {
            return fieldIndexes;
        }
        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            fieldIndexes.putIfAbsent(fieldName, i);
        }
        return fieldIndexes;
    }

    public static Map<String, Integer> indexQueryFields(List<? extends QueryField> fields) {
        LinkedHashMap<String, Integer> fieldIndexes = new LinkedHashMap<>(
                CollectionUtil.expectedMapCapacity(fields == null ? 0 : fields.size())
        );
        if (fields == null) {
            return fieldIndexes;
        }
        for (int i = 0; i < fields.size(); i++) {
            QueryField field = fields.get(i);
            if (field == null || field.getFieldName() == null || field.getFieldName().isBlank()) {
                continue;
            }
            fieldIndexes.putIfAbsent(field.getFieldName(), i);
        }
        return fieldIndexes;
    }

    public static List<String> queryFieldNames(List<? extends QueryField> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        ArrayList<String> names = new ArrayList<>(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            QueryField field = fields.get(i);
            names.add(field == null || field.getFieldName() == null ? "" : field.getFieldName());
        }
        return List.copyOf(names);
    }

    public static List<String> firstQueryRowFieldNames(List<QueryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        for (int i = 0; i < rows.size(); i++) {
            QueryRow row = rows.get(i);
            if (row == null || row.getFields() == null || row.getFields().isEmpty()) {
                continue;
            }
            return queryFieldNames(row.getFields());
        }
        return List.of();
    }

    public static int findFieldIndex(Map<String, Integer> fieldIndexes, String fieldName) {
        if (fieldIndexes == null || fieldName == null) {
            return -1;
        }
        Integer index = fieldIndexes.get(fieldName);
        return index == null ? -1 : index;
    }
}
