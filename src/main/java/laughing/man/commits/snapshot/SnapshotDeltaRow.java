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

    private K key;
    private String keyText;
    private String changeType;
    private boolean added;
    private boolean removed;
    private boolean changed;
    private boolean unchanged;
    private boolean currentPresent;
    private boolean previousPresent;
    private int changedFieldCount;
    private String changedFieldSummary;
    private List<String> changedFields;
    private T current;
    private T previous;

    public SnapshotDeltaRow() {
    }

    public K getKey() {
        return key;
    }

    public String getKeyText() {
        return keyText;
    }

    public String getChangeType() {
        return changeType;
    }

    public boolean isAdded() {
        return added;
    }

    public boolean isRemoved() {
        return removed;
    }

    public boolean isChanged() {
        return changed;
    }

    public boolean isUnchanged() {
        return unchanged;
    }

    public boolean isCurrentPresent() {
        return currentPresent;
    }

    public boolean isPreviousPresent() {
        return previousPresent;
    }

    public int getChangedFieldCount() {
        return changedFieldCount;
    }

    public String getChangedFieldSummary() {
        return changedFieldSummary;
    }

    public List<String> getChangedFields() {
        return changedFields;
    }

    public T getCurrent() {
        return current;
    }

    public T getPrevious() {
        return previous;
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
