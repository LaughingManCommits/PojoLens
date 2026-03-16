package laughing.man.commits.util;

import java.util.ArrayList;
import java.util.List;

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
}
