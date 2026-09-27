package laughing.man.commits.filter;

import laughing.man.commits.util.GroupKeyUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Accumulates {@code COUNT(DISTINCT field)}: distinct non-null values, compared like
 * {@code GROUP BY} keys (so {@code ''} counts, and date/time values compare exactly).
 */
final class DistinctValueCounter {

    private final Set<Object> keys = new HashSet<>();

    void add(Object value) {
        if (value != null) {
            keys.add(GroupKeyUtil.groupKey(value, null));
        }
    }

    long count() {
        return keys.size();
    }
}
