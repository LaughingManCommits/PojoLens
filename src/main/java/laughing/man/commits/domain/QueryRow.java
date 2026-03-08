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
}

