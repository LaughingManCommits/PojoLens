package laughing.man.commits.filter;

import java.util.Arrays;
import java.util.List;

final class QueryKey {

    private static final Object[] EMPTY_VALUES = new Object[0];
    private static final int ESTIMATED_CHARS_PER_KEY = 8;
    private final Object[] values;
    private int hashCode;

    QueryKey(List<?> values) {
        if (values == null || values.isEmpty()) {
            this.values = EMPTY_VALUES;
        } else {
            this.values = values.toArray();
        }
        this.hashCode = Arrays.hashCode(this.values);
    }

    QueryKey(Object[] values, int size) {
        if (values == null || size <= 0) {
            this.values = EMPTY_VALUES;
        } else {
            this.values = Arrays.copyOf(values, size);
        }
        this.hashCode = Arrays.hashCode(this.values);
    }

    /**
     * Creates a transient lookup key that shares {@code sharedBuffer} directly
     * without copying.  Only safe for map {@code get}/{@code containsKey}
     * operations — never put this key into a map.  Call {@link #refresh()}
     * after mutating the shared buffer before each lookup.
     */
    static QueryKey forMutableLookup(Object[] sharedBuffer, int size) {
        return new QueryKey(sharedBuffer, size, false);
    }

    private QueryKey(Object[] sharedBuffer, int size, boolean unused) {
        this.values = (sharedBuffer != null && size > 0) ? sharedBuffer : EMPTY_VALUES;
        this.hashCode = Arrays.hashCode(this.values);
    }

    /**
     * Recomputes the cached hash code from the shared buffer.
     * Must be called after mutating the buffer and before each map lookup.
     */
    void refresh() {
        this.hashCode = Arrays.hashCode(values);
    }

    boolean isEmpty() {
        return values.length == 0;
    }

    String toExternalKey() {
        if (values.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(values.length * ESTIMATED_CHARS_PER_KEY);
        for (Object value : values) {
            // Escape the separator so ("x,y","z") and ("x","y,z") stay distinct keys.
            sb.append(String.valueOf(value).replace("\\", "\\\\").replace(",", "\\,")).append(",");
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

