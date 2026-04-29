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
        this.values = values == null ? new Object[0] : values.clone();
        this.schema = schema == null ? List.of() : new ArrayList<>(schema);
    }

    @Override
    public Object getValueAt(int index) {
        if (index < 0) {
            return null;
        }
        if (index < values.length) {
            return values[index];
        }
        // Fall back to the cached field list for indices beyond the original values array
        // (e.g. computed fields appended after row construction).
        List<? extends QueryField> cached = super.getFields();
        if (cached != null && index < cached.size()) {
            return cached.get(index).getValue();
        }
        return null;
    }

    @Override
    public int getFieldCount() {
        List<? extends QueryField> cached = super.getFields();
        return cached != null ? cached.size() : values.length;
    }

    @Override
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "RawQueryRow owns a cloned schema list and exposes it read-only for internal schema checks."
    )
    public List<String> getFieldNames() {
        List<? extends QueryField> cached = super.getFields();
        if (cached == null || cached.isEmpty()) {
            return schema;
        }
        List<String> names = new ArrayList<>(cached.size());
        for (QueryField field : cached) {
            names.add(field == null || field.getFieldName() == null ? "" : field.getFieldName());
        }
        return List.copyOf(names);
    }

    /**
     * Returns the cached schema names without materializing query fields.
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "RawQueryRow owns a cloned schema list and exposes it read-only for internal schema checks."
    )
    public List<String> getSchema() {
        return schema;
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
        return super.getFields();
    }
}
