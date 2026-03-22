package laughing.man.commits.domain;

import java.util.List;

/**
 * Internal row container used by the query pipeline.
 */
public class QueryRow {

    private String rowId;
    private String rowType;
    private List<? extends QueryField> fields;

    public String getRowId() {
        return rowId;
    }

    public void setRowId(String rowId) {
        this.rowId = rowId;
    }

    public String getRowType() {
        return rowType;
    }

    public void setRowType(String rowType) {
        this.rowType = rowType;
    }

    public List<? extends QueryField> getFields() {
        return fields;
    }

    public void setFields(List<? extends QueryField> fields) {
        this.fields = fields;
    }

    /**
     * Returns the value of the field at {@code index}, or {@code null} if the
     * index is out of bounds or the field value is null.
     * <p>
     * Subclasses may override this to avoid materializing the full
     * {@link #getFields()} list when only indexed access is needed.
     */
    public Object getValueAt(int index) {
        List<? extends QueryField> f = getFields();
        if (f == null || index < 0 || index >= f.size()) {
            return null;
        }
        return f.get(index).getValue();
    }

    /**
     * Returns the number of fields in this row, or {@code 0} if none.
     * <p>
     * Subclasses may override this to avoid materializing the full
     * {@link #getFields()} list.
     */
    public int getFieldCount() {
        List<? extends QueryField> f = getFields();
        return f == null ? 0 : f.size();
    }
}

