package laughing.man.commits.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared collection helpers for non-hot setup and adaptation paths.
 */
public final class CollectionUtil {

    private CollectionUtil() {
    }

    public static <T> T firstNonNull(Iterable<? extends T> values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static <T> List<T> applyLimit(List<T> rows, Integer limit) {
        if (rows == null || limit == null || limit >= rows.size()) {
            return rows;
        }
        if (limit <= 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(rows.subList(0, limit));
    }

    public static int expectedMapCapacity(int sourceSize) {
        if (sourceSize <= 0) {
            return 16;
        }
        return (int) ((sourceSize / 0.75f) + 1.0f);
    }

    public static <K extends Comparable<? super K>, V> List<Map.Entry<K, V>> sortedEntriesByKey(Map<K, V> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        ArrayList<Map.Entry<K, V>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        return entries;
    }
}
