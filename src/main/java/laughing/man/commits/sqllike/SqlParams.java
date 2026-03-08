package laughing.man.commits.sqllike;

import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed SQL-like named parameter container.
 */
public final class SqlParams {

    private static final SqlParams EMPTY = new SqlParams(Collections.emptyMap());

    private final Map<String, Object> values;

    private SqlParams(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static SqlParams empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int size() {
        return values.size();
    }

    public static final class Builder {

        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder put(String name, Object value) {
            String normalizedName = normalizeName(name);
            values.put(normalizedName, value);
            return this;
        }

        public Builder putAll(Map<String, ?> parameters) {
            if (parameters == null) {
                throw new IllegalArgumentException("parameters must not be null");
            }
            for (Map.Entry<String, ?> entry : parameters.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public SqlParams build() {
            if (values.isEmpty()) {
                return EMPTY;
            }
            return new SqlParams(values);
        }

        private static String normalizeName(String name) {
            if (name == null || name.isBlank()) {
                throw SqlLikeErrors.argument(SqlLikeErrorCodes.PARAM_NAME_INVALID,
                        "Parameter name must not be null/blank");
            }
            return name;
        }
    }
}

