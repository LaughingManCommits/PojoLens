package laughing.man.commits.util;

import laughing.man.commits.domain.QueryField;

import java.util.List;

/**
 * Shared exact-name lookup helpers for {@link QueryField} lists.
 */
public final class QueryFieldLookupUtil {

    private QueryFieldLookupUtil() {
    }

    public static int findFieldIndex(List<? extends QueryField> fields, String fieldName) {
        if (fields == null || fieldName == null) {
            return -1;
        }
        for (int i = 0; i < fields.size(); i++) {
            QueryField field = fields.get(i);
            if (field != null && fieldName.equals(field.getFieldName())) {
                return i;
            }
        }
        return -1;
    }

    public static boolean containsField(List<? extends QueryField> fields, String fieldName) {
        return findFieldIndex(fields, fieldName) >= 0;
    }

    public static Object findFieldValue(List<? extends QueryField> fields, String fieldName) {
        return findFieldValue(fields, fieldName, -1);
    }

    public static Object findFieldValue(List<? extends QueryField> fields, String fieldName, int preferredIndex) {
        if (fields == null || fields.isEmpty() || fieldName == null) {
            return null;
        }
        if (preferredIndex >= 0 && preferredIndex < fields.size()) {
            QueryField indexedField = fields.get(preferredIndex);
            if (indexedField != null && fieldName.equals(indexedField.getFieldName())) {
                return indexedField.getValue();
            }
        }
        int fieldIndex = findFieldIndex(fields, fieldName);
        if (fieldIndex < 0) {
            return null;
        }
        QueryField field = fields.get(fieldIndex);
        return field == null ? null : field.getValue();
    }
}
