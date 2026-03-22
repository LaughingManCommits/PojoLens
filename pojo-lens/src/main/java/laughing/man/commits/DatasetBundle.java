package laughing.man.commits;

import laughing.man.commits.sqllike.JoinBindings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable execution bundle that captures a primary dataset and optional named
 * secondary datasets for repeated SQL-like/report/chart execution.
 */
public final class DatasetBundle {

    private final List<?> primaryRows;
    private final JoinBindings joinBindings;

    private DatasetBundle(List<?> primaryRows, JoinBindings joinBindings) {
        this.primaryRows = snapshotList(primaryRows, "primaryRows");
        this.joinBindings = snapshotJoinBindings(joinBindings);
    }

    public static DatasetBundle of(List<?> primaryRows) {
        return new DatasetBundle(primaryRows, JoinBindings.empty());
    }

    public static DatasetBundle of(List<?> primaryRows, Map<String, List<?>> joinSources) {
        Objects.requireNonNull(joinSources, "joinSources must not be null");
        return new DatasetBundle(primaryRows, JoinBindings.from(joinSources));
    }

    public static DatasetBundle of(List<?> primaryRows, JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return new DatasetBundle(primaryRows, joinBindings);
    }

    public static Builder builder(List<?> primaryRows) {
        return new Builder(primaryRows);
    }

    public List<?> primaryRows() {
        return primaryRows;
    }

    public Map<String, List<?>> joinSources() {
        return joinBindings.asMap();
    }

    public JoinBindings joinBindings() {
        return joinBindings;
    }

    public boolean hasJoinSources() {
        return !joinBindings.isEmpty();
    }

    public int joinSourceCount() {
        return joinBindings.size();
    }

    private static List<?> snapshotList(List<?> rows, String label) {
        Objects.requireNonNull(rows, label + " must not be null");
        return Collections.unmodifiableList(new ArrayList<>(rows));
    }

    private static JoinBindings snapshotJoinBindings(JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        if (joinBindings.isEmpty()) {
            return JoinBindings.empty();
        }
        JoinBindings.Builder builder = JoinBindings.builder();
        for (Map.Entry<String, List<?>> entry : joinBindings.asMap().entrySet()) {
            builder.add(entry.getKey(), snapshotList(entry.getValue(), "rows"));
        }
        return builder.build();
    }

    public static final class Builder {
        private final List<?> primaryRows;
        private final JoinBindings.Builder joinBindings = JoinBindings.builder();

        private Builder(List<?> primaryRows) {
            this.primaryRows = snapshotList(primaryRows, "primaryRows");
        }

        public Builder add(String sourceName, List<?> rows) {
            joinBindings.add(sourceName, snapshotList(rows, "rows"));
            return this;
        }

        public Builder addAll(Map<String, List<?>> joinSources) {
            Objects.requireNonNull(joinSources, "joinSources must not be null");
            for (Map.Entry<String, List<?>> entry : joinSources.entrySet()) {
                add(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public Builder addAll(JoinBindings joinBindings) {
            Objects.requireNonNull(joinBindings, "joinBindings must not be null");
            return addAll(joinBindings.asMap());
        }

        public DatasetBundle build() {
            return new DatasetBundle(primaryRows, joinBindings.build());
        }
    }
}

