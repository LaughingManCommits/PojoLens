package laughing.man.commits.filter;

import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.RawQueryRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared cold-path adapters for materializing internal array rows as {@link QueryRow}s.
 */
final class QueryRowAdapterSupport {

    private QueryRowAdapterSupport() {
    }

    static List<QueryRow> toQueryRows(List<String> schemaFields, List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>(0);
        }

        ArrayList<QueryRow> queryRows = new ArrayList<>(rows.size());
        for (Object[] values : rows) {
            queryRows.add(new RawQueryRow(values != null ? values : new Object[0], schemaFields));
        }
        return queryRows;
    }
}
