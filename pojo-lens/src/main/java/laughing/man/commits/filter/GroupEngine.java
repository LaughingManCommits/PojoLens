package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.GroupKeyUtil;
import laughing.man.commits.util.TimeBucketUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.EngineDefaults.EMPTY_GROUPING;

final class GroupEngine {

    private static final int INITIAL_GROUP_LIST_CAPACITY = 4;

    private final FilterQueryBuilder builder;

    GroupEngine(FilterQueryBuilder builder) {
        this.builder = builder;
    }

    Map<String, List<QueryRow>> groupByFields(List<QueryRow> rows,
                                                 List<QueryRow> displayFields,
                                                 FilterExecutionPlan plan) {
        Map<String, List<QueryRow>> results = new LinkedHashMap<>();
        if (!builder.getGroupFields().isEmpty() && rows != null && !rows.isEmpty()) {
            List<FilterExecutionPlan.GroupColumn> columns = plan.getGroupColumns();
            int columnCount = columns.size();
            Map<QueryKey, List<QueryRow>> grouped =
                    new LinkedHashMap<>(CollectionUtil.expectedMapCapacity(rows.size()));

            String[] groupParts = new String[columnCount];
            QueryKey lookupKey = QueryKey.forMutableLookup(groupParts, columnCount);
            for (int index = 0; index < rows.size(); index++) {
                QueryRow row = rows.get(index);
                for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                    FilterExecutionPlan.GroupColumn column = columns.get(columnIndex);
                    Object raw = row.getValueAt(column.fieldIndex());
                    Object projected = column.timeBucket() == null ? raw : TimeBucketUtil.bucketValue(raw, column.timeBucket());
                    groupParts[columnIndex] = GroupKeyUtil.toGroupKeyValue(projected, column.dateFormat());
                }
                lookupKey.refresh();
                List<QueryRow> groupedRows = grouped.get(lookupKey);
                if (groupedRows == null) {
                    groupedRows = new ArrayList<>(INITIAL_GROUP_LIST_CAPACITY);
                    grouped.put(new QueryKey(groupParts, columnCount), groupedRows);
                }

                if (displayFields == null || index >= displayFields.size()) {
                    continue;
                }
                groupedRows.add(displayFields.get(index));
            }

            for (Map.Entry<QueryKey, List<QueryRow>> entry : grouped.entrySet()) {
                results.put(entry.getKey().toExternalKey(), entry.getValue());
            }
        } else {
            List<QueryRow> keys = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
            results.put(EMPTY_GROUPING, keys);
        }

        return results;
    }
}

