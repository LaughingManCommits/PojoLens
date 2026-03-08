package laughing.man.commits.snapshot;

import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Deterministic keyed comparison between two in-memory snapshots.
 *
 * @param <T> row type
 * @param <K> key type
 */
public final class SnapshotComparison<T, K> {

    private final List<SnapshotDeltaRow<T, K>> rows;
    private final SnapshotComparisonSummary summary;

    private SnapshotComparison(List<SnapshotDeltaRow<T, K>> rows, SnapshotComparisonSummary summary) {
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.summary = summary;
    }

    public static <T> Builder<T> builder(List<T> currentRows, List<T> previousRows) {
        return new Builder<>(currentRows, previousRows);
    }

    public List<SnapshotDeltaRow<T, K>> rows() {
        return rows;
    }

    public List<SnapshotDeltaRow<T, K>> added() {
        return filterByType("ADDED");
    }

    public List<SnapshotDeltaRow<T, K>> removed() {
        return filterByType("REMOVED");
    }

    public List<SnapshotDeltaRow<T, K>> changed() {
        return filterByType("CHANGED");
    }

    public List<SnapshotDeltaRow<T, K>> unchanged() {
        return filterByType("UNCHANGED");
    }

    public SnapshotComparisonSummary summary() {
        return summary;
    }

    private List<SnapshotDeltaRow<T, K>> filterByType(String type) {
        ArrayList<SnapshotDeltaRow<T, K>> filtered = new ArrayList<>();
        for (SnapshotDeltaRow<T, K> row : rows) {
            if (type.equals(row.changeType)) {
                filtered.add(row);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    public static final class Builder<T> {
        private final List<T> currentRows;
        private final List<T> previousRows;
        private boolean allowNullKeys;

        private Builder(List<T> currentRows, List<T> previousRows) {
            this.currentRows = snapshotRows(currentRows, "currentRows");
            this.previousRows = snapshotRows(previousRows, "previousRows");
        }

        public Builder<T> allowNullKeys() {
            return allowNullKeys(true);
        }

        public Builder<T> allowNullKeys(boolean allowNullKeys) {
            this.allowNullKeys = allowNullKeys;
            return this;
        }

        public <K> SnapshotComparison<T, K> byKey(Function<T, K> keySelector) {
            Objects.requireNonNull(keySelector, "keySelector must not be null");
            LinkedHashMap<K, T> currentByKey = indexRows(currentRows, keySelector, allowNullKeys, "current");
            LinkedHashMap<K, T> previousByKey = indexRows(previousRows, keySelector, allowNullKeys, "previous");
            List<String> compareFields = resolveCompareFields(currentRows, previousRows);
            LinkedHashSet<K> emittedKeys = new LinkedHashSet<>();
            ArrayList<SnapshotDeltaRow<T, K>> rows = new ArrayList<>(currentByKey.size() + previousByKey.size());

            int addedCount = 0;
            int removedCount = 0;
            int changedCount = 0;
            int unchangedCount = 0;

            for (Map.Entry<K, T> entry : currentByKey.entrySet()) {
                K key = entry.getKey();
                T current = entry.getValue();
                T previous = previousByKey.get(key);
                emittedKeys.add(key);

                SnapshotDeltaRow<T, K> row;
                if (previous == null) {
                    row = SnapshotDeltaRow.added(key, current);
                    addedCount++;
                } else {
                    List<String> changedFields = detectChangedFields(current, previous, compareFields);
                    if (changedFields.isEmpty()) {
                        row = SnapshotDeltaRow.unchanged(key, current, previous);
                        unchangedCount++;
                    } else {
                        row = SnapshotDeltaRow.changed(key, current, previous, changedFields);
                        changedCount++;
                    }
                }
                rows.add(row);
            }

            for (Map.Entry<K, T> entry : previousByKey.entrySet()) {
                if (emittedKeys.contains(entry.getKey())) {
                    continue;
                }
                rows.add(SnapshotDeltaRow.removed(entry.getKey(), entry.getValue()));
                removedCount++;
            }

            SnapshotComparisonSummary summary = new SnapshotComparisonSummary(
                    currentByKey.size(),
                    previousByKey.size(),
                    addedCount,
                    removedCount,
                    changedCount,
                    unchangedCount
            );
            return new SnapshotComparison<>(rows, summary);
        }

        private static <T> List<T> snapshotRows(List<T> rows, String label) {
            Objects.requireNonNull(rows, label + " must not be null");
            return Collections.unmodifiableList(new ArrayList<>(rows));
        }

        private static <T, K> LinkedHashMap<K, T> indexRows(List<T> rows,
                                                            Function<T, K> keySelector,
                                                            boolean allowNullKeys,
                                                            String label) {
            LinkedHashMap<K, T> indexed = new LinkedHashMap<>();
            for (T row : rows) {
                if (row == null) {
                    throw new IllegalArgumentException(label + " snapshot must not contain null rows");
                }
                K key = keySelector.apply(row);
                if (key == null && !allowNullKeys) {
                    throw new IllegalArgumentException(label + " snapshot contains null key");
                }
                if (indexed.containsKey(key)) {
                    throw new IllegalArgumentException("Duplicate snapshot key '" + key + "' in " + label + " snapshot");
                }
                indexed.put(key, row);
            }
            return indexed;
        }

        private static <T> List<String> resolveCompareFields(List<T> currentRows, List<T> previousRows) {
            Class<?> sampleType = null;
            for (T row : currentRows) {
                if (row != null) {
                    sampleType = row.getClass();
                    break;
                }
            }
            if (sampleType == null) {
                for (T row : previousRows) {
                    if (row != null) {
                        sampleType = row.getClass();
                        break;
                    }
                }
            }
            if (sampleType == null) {
                return List.of();
            }
            return ReflectionUtil.collectQueryableFieldNames(sampleType);
        }

        private static List<String> detectChangedFields(Object current, Object previous, List<String> compareFields) {
            if (compareFields.isEmpty()) {
                return Objects.equals(current, previous) ? List.of() : List.of("__row__");
            }
            ArrayList<String> changedFields = new ArrayList<>();
            for (String field : compareFields) {
                Object currentValue = getValue(current, field);
                Object previousValue = getValue(previous, field);
                if (!Objects.equals(currentValue, previousValue)) {
                    changedFields.add(field);
                }
            }
            return Collections.unmodifiableList(changedFields);
        }

        private static Object getValue(Object row, String field) {
            try {
                return ReflectionUtil.getFieldValue(row, field);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to compare snapshot field '" + field + "'", ex);
            }
        }
    }
}

