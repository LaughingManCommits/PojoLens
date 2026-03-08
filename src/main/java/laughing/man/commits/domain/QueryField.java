package laughing.man.commits.domain;

/**
 * Internal field/value pair used by {@link QueryRow}.
 */
public class QueryField {

    private String fieldName;
    private Object value;

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}

