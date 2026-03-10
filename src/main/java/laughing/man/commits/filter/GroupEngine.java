package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.StringUtil;
import laughing.man.commits.util.TimeBucketUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.EngineDefaults.EMPTY_GROUPING;

final class GroupEngine {

    private static final String NULL_GROUP_KEY = "<NULL>";
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
            Map<QueryKey, List<QueryRow>> grouped = new LinkedHashMap<>(expectedMapSize(rows.size()));

            for (int index = 0; index < rows.size(); index++) {
                QueryRow row = rows.get(index);
                List<? extends QueryField> allFields = row.getFields();
                String[] groupParts = new String[columnCount];
                for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                    FilterExecutionPlan.GroupColumn column = columns.get(columnIndex);
                    if (column.fieldIndex() >= allFields.size()) {
                        groupParts[columnIndex] = NULL_GROUP_KEY;
                        continue;
                    }
                    Object raw = allFields.get(column.fieldIndex()).getValue();
                    Object projected = column.timeBucket() == null ? raw : TimeBucketUtil.bucketValue(raw, column.timeBucket());
                    String groupedValue = ObjectUtil.castToString(projected, column.dateFormat());
                    groupParts[columnIndex] = StringUtil.isNull(groupedValue) ? NULL_GROUP_KEY : groupedValue;
                }
                QueryKey groupKey = new QueryKey(groupParts, columnCount);

                List<QueryRow> groupedRows = grouped.computeIfAbsent(groupKey, ignored -> new ArrayList<>(4));

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

    private int expectedMapSize(int sourceSize) {
        if (sourceSize <= 0) {
            return 16;
        }
        return (int) ((sourceSize / 0.75f) + 1.0f);
    }
}

