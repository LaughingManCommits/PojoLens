package laughing.man.commits.filter;

import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared cold-path adapters for materializing internal array rows as {@link QueryRow}s.
 */
final class QueryRowAdapterSupport {

    private QueryRowAdapterSupport() {
    }

    static List<QueryRow> toQueryRows(List<String> schemaFields, List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>(0);
        }

        ArrayList<QueryRow> queryRows = new ArrayList<>(rows.size());
        for (Object[] values : rows) {
            ArrayList<QueryField> fields = new ArrayList<>(schemaFields.size());
            for (int i = 0; i < schemaFields.size(); i++) {
                QueryField field = new QueryField();
                field.setFieldName(schemaFields.get(i));
                field.setValue(values != null && i < values.length ? values[i] : null);
                fields.add(field);
            }
            QueryRow row = new QueryRow();
            row.setFields(fields);
            queryRows.add(row);
        }
        return queryRows;
    }
}
