package laughing.man.commits.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared collection helpers for non-hot setup and adaptation paths.
 */
public final class CollectionUtil {

    private static final int DEFAULT_MAP_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

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
        return applyOffsetAndLimit(rows, null, limit);
    }

    public static <T> List<T> applyOffsetAndLimit(List<T> rows, Integer offset, Integer limit) {
        if (rows == null) {
            return null;
        }
        int rowCount = rows.size();
        if (rowCount == 0) {
            return rows;
        }

        int normalizedOffset = offset == null ? 0 : Math.max(0, offset);
        if (normalizedOffset >= rowCount) {
            return new ArrayList<>();
        }
        if (limit != null && limit <= 0) {
            return new ArrayList<>();
        }

        int start = normalizedOffset;
        int endExclusive = rowCount;
        if (limit != null) {
            long end = (long) start + limit;
            endExclusive = (int) Math.min(rowCount, end);
        }
        if (start == 0 && endExclusive == rowCount) {
            return rows;
        }
        return new ArrayList<>(rows.subList(start, endExclusive));
    }

    public static Integer pagingWindow(Integer offset, Integer limit) {
        if (limit == null) {
            return null;
        }
        int normalizedOffset = offset == null ? 0 : Math.max(0, offset);
        long window = (long) normalizedOffset + limit;
        if (window > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) window;
    }

    public static int expectedMapCapacity(int sourceSize) {
        if (sourceSize <= 0) {
            return DEFAULT_MAP_CAPACITY;
        }
        return (int) ((sourceSize / DEFAULT_LOAD_FACTOR) + 1.0f);
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
