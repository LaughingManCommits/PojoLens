package laughing.man.commits.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact {@link QueryRow} backed by a plain {@code Object[]} with a shared schema list.
 * <p>
 * Internal engine use: field values are accessed in O(1) via {@link #getValueAt(int)}
 * without materializing {@link QueryField} wrapper objects.  The full {@link #getFields()}
 * view is built lazily on first access and cached thereafter.
 */
public final class RawQueryRow extends QueryRow {

    private final Object[] values;
    private final List<String> schema;

    public RawQueryRow(Object[] values, List<String> schema) {
        this.values = values;
        this.schema = schema;
    }

    @Override
    public Object getValueAt(int index) {
        return (index >= 0 && index < values.length) ? values[index] : null;
    }

    @Override
    public int getFieldCount() {
        return values.length;
    }

    @Override
    public List<? extends QueryField> getFields() {
        List<? extends QueryField> cached = super.getFields();
        if (cached != null) {
            return cached;
        }
        List<QueryField> fields = new ArrayList<>(schema.size());
        for (int i = 0; i < schema.size(); i++) {
            QueryField f = new QueryField();
            f.setFieldName(schema.get(i));
            f.setValue(i < values.length ? values[i] : null);
            fields.add(f);
        }
        setFields(fields);
        return fields;
    }
}
