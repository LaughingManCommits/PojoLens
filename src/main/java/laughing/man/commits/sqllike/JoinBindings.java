package laughing.man.commits.sqllike;

import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed SQL-like JOIN source bindings.
 */
public final class JoinBindings {

    private static final JoinBindings EMPTY = new JoinBindings(Collections.emptyMap());

    private final Map<String, List<?>> bindings;

    private JoinBindings(Map<String, List<?>> bindings) {
        this.bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }

    public static JoinBindings empty() {
        return EMPTY;
    }

    public static JoinBindings of(String sourceName, List<?> rows) {
        return builder().add(sourceName, rows).build();
    }

    public static JoinBindings from(Map<String, List<?>> bindings) {
        Objects.requireNonNull(bindings, "bindings must not be null");
        Builder builder = builder();
        for (Map.Entry<String, List<?>> entry : bindings.entrySet()) {
            builder.add(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, List<?>> asMap() {
        return bindings;
    }

    public boolean isEmpty() {
        return bindings.isEmpty();
    }

    public int size() {
        return bindings.size();
    }

    public static final class Builder {
        private final LinkedHashMap<String, List<?>> bindings = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder add(String sourceName, List<?> rows) {
            String normalizedSource = normalizeSource(sourceName);
            Objects.requireNonNull(rows, "rows must not be null");
            if (bindings.containsKey(normalizedSource)) {
                throw SqlLikeErrors.argument(SqlLikeErrorCodes.JOIN_DUPLICATE_SOURCE_BINDING,
                        "Duplicate JOIN source binding for '" + normalizedSource + "'");
            }
            bindings.put(normalizedSource, rows);
            return this;
        }

        public JoinBindings build() {
            if (bindings.isEmpty()) {
                return EMPTY;
            }
            return new JoinBindings(bindings);
        }

        private static String normalizeSource(String sourceName) {
            if (sourceName == null || sourceName.isBlank()) {
                throw SqlLikeErrors.argument(SqlLikeErrorCodes.JOIN_SOURCE_NAME_INVALID,
                        "sourceName must not be null/blank");
            }
            return sourceName;
        }
    }
}

