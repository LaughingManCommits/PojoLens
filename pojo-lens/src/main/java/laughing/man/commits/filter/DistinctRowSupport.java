package laughing.man.commits.filter;

import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.util.GroupKeyUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SQL {@code SELECT DISTINCT} over output rows: keeps the first row for each set of output
 * values, in input (ORDER BY) order. Values compare like {@code GROUP BY} keys, so
 * {@code null} and {@code ''} stay apart and date/time values compare exactly.
 */
final class DistinctRowSupport {

    private DistinctRowSupport() {
    }

    static List<QueryRow> distinct(List<QueryRow> rows) {
        if (rows == null || rows.size() < 2) {
            return rows;
        }
        Set<QueryKey> seen = new HashSet<>();
        List<QueryRow> kept = new ArrayList<>();
        for (QueryRow row : rows) {
            if (row == null) {
                continue;
            }
            int fieldCount = row.getFieldCount();
            Object[] key = new Object[fieldCount];
            for (int i = 0; i < fieldCount; i++) {
                key[i] = GroupKeyUtil.groupKey(row.getValueAt(i), null);
            }
            if (seen.add(new QueryKey(key, fieldCount))) {
                kept.add(row);
            }
        }
        return kept;
    }
}
