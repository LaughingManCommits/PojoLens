package laughing.man.commits.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Queryable diff row produced by {@link SnapshotComparison}.
 *
 * @param <T> row type
 * @param <K> key type
 */
public final class SnapshotDeltaRow<T, K> {

    public K key;
    public String keyText;
    public String changeType;
    public boolean added;
    public boolean removed;
    public boolean changed;
    public boolean unchanged;
    public boolean currentPresent;
    public boolean previousPresent;
    public int changedFieldCount;
    public String changedFieldSummary;
    public List<String> changedFields;
    public T current;
    public T previous;

    public SnapshotDeltaRow() {
    }

    static <T, K> SnapshotDeltaRow<T, K> added(K key, T current) {
        return row(key, "ADDED", true, false, false, false, List.of(), current, null);
    }

    static <T, K> SnapshotDeltaRow<T, K> removed(K key, T previous) {
        return row(key, "REMOVED", false, true, false, false, List.of(), null, previous);
    }

    static <T, K> SnapshotDeltaRow<T, K> changed(K key, T current, T previous, List<String> changedFields) {
        return row(key, "CHANGED", false, false, true, false, changedFields, current, previous);
    }

    static <T, K> SnapshotDeltaRow<T, K> unchanged(K key, T current, T previous) {
        return row(key, "UNCHANGED", false, false, false, true, List.of(), current, previous);
    }

    private static <T, K> SnapshotDeltaRow<T, K> row(K key,
                                                      String changeType,
                                                      boolean added,
                                                      boolean removed,
                                                      boolean changed,
                                                      boolean unchanged,
                                                      List<String> changedFields,
                                                      T current,
                                                      T previous) {
        SnapshotDeltaRow<T, K> row = new SnapshotDeltaRow<>();
        row.key = key;
        row.keyText = String.valueOf(key);
        row.changeType = changeType;
        row.added = added;
        row.removed = removed;
        row.changed = changed;
        row.unchanged = unchanged;
        row.currentPresent = current != null;
        row.previousPresent = previous != null;
        row.changedFields = Collections.unmodifiableList(new ArrayList<>(changedFields));
        row.changedFieldCount = changedFields.size();
        row.changedFieldSummary = changedFields.isEmpty() ? "" : String.join(",", changedFields);
        row.current = current;
        row.previous = previous;
        return row;
    }
}

