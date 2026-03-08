package laughing.man.commits.filter;

import java.util.Arrays;
import java.util.List;

final class QueryKey {

    private static final String[] EMPTY_VALUES = new String[0];
    private final String[] values;
    private final int hashCode;

    QueryKey(List<String> values) {
        if (values == null || values.isEmpty()) {
            this.values = EMPTY_VALUES;
        } else {
            this.values = values.toArray(new String[0]);
        }
        this.hashCode = Arrays.hashCode(this.values);
    }

    QueryKey(String[] values, int size) {
        if (values == null || size <= 0) {
            this.values = EMPTY_VALUES;
        } else {
            this.values = Arrays.copyOf(values, size);
        }
        this.hashCode = Arrays.hashCode(this.values);
    }

    boolean isEmpty() {
        return values.length == 0;
    }

    String toExternalKey() {
        if (values.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(values.length * 8);
        for (String value : values) {
            sb.append(value).append(",");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueryKey)) {
            return false;
        }
        QueryKey queryKey = (QueryKey) o;
        return Arrays.equals(values, queryKey.values);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}

