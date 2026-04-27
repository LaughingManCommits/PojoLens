package laughing.man.commits.sqllike;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Materialized rows and audit metadata returned by a host pushdown adapter.
 *
 * @param <T> row type returned to PojoLens
 */
public final class SqlLikePushdownResult<T> {

    private final List<T> rows;
    private final List<String> pushedStages;
    private final int sourceRowCount;
    private final Map<String, Object> metadata;

    public SqlLikePushdownResult(List<T> rows,
                                 Collection<String> pushedStages,
                                 int sourceRowCount,
                                 Map<String, ?> metadata) {
        this.rows = new java.util.ArrayList<>(Objects.requireNonNull(rows, "rows must not be null"));
        this.pushedStages = new java.util.ArrayList<>(Objects.requireNonNull(pushedStages,
                "pushedStages must not be null"));
        this.sourceRowCount = sourceRowCount;
        this.metadata = copyMetadata(metadata);
    }

    public static <T> SqlLikePushdownResult<T> of(List<T> rows) {
        return new SqlLikePushdownResult<>(rows, List.of(), -1, Map.of());
    }

    public static <T> SqlLikePushdownResult<T> of(List<T> rows, Collection<String> pushedStages) {
        return new SqlLikePushdownResult<>(rows, pushedStages, -1, Map.of());
    }

    public static <T> SqlLikePushdownResult<T> of(List<T> rows,
                                                  Collection<String> pushedStages,
                                                  int sourceRowCount,
                                                  Map<String, ?> metadata) {
        return new SqlLikePushdownResult<>(rows, pushedStages, sourceRowCount, metadata);
    }

    /**
     * Returns materialized rows that PojoLens will finish in memory.
     *
     * @return rows
     */
    public List<T> rows() {
        return java.util.Collections.unmodifiableList(rows);
    }

    /**
     * Returns stages the adapter actually performed.
     *
     * @return pushed stage names
     */
    public List<String> pushedStages() {
        return java.util.Collections.unmodifiableList(pushedStages);
    }

    /**
     * Returns the adapter-observed source row count, or {@code -1} when unknown.
     *
     * @return source row count, or {@code -1}
     */
    public int sourceRowCount() {
        return sourceRowCount;
    }

    /**
     * Returns adapter-supplied audit metadata.
     *
     * @return immutable metadata
     */
    public Map<String, Object> metadata() {
        return java.util.Collections.unmodifiableMap(metadata);
    }

    private static Map<String, Object> copyMetadata(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : metadata.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }
}
