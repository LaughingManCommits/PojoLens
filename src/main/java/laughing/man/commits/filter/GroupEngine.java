package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryTimeBucket;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.StringUtil;
import laughing.man.commits.util.TimeBucketUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static laughing.man.commits.EngineDefaults.EMPTY_GROUPING;
import static laughing.man.commits.EngineDefaults.SDF;

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
            SortedSet<Integer> sortedKeys = new TreeSet<>(builder.getGroupFields().keySet());
            List<GroupColumn> columns = resolveGroupColumns(sortedKeys, plan);
            int columnCount = columns.size();
            Map<QueryKey, List<QueryRow>> grouped = new LinkedHashMap<>(expectedMapSize(rows.size()));

            for (int index = 0; index < rows.size(); index++) {
                QueryRow row = rows.get(index);
                String uniqueID = row.getRowId();
                List<? extends QueryField> allFields = row.getFields();
                String[] groupParts = new String[columnCount];
                for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                    GroupColumn column = columns.get(columnIndex);
                    if (column.fieldIndex >= allFields.size()) {
                        groupParts[columnIndex] = NULL_GROUP_KEY;
                        continue;
                    }
                    Object raw = allFields.get(column.fieldIndex).getValue();
                    Object projected = column.timeBucket == null ? raw : TimeBucketUtil.bucketValue(raw, column.timeBucket);
                    String groupedValue = ObjectUtil.castToString(projected, column.dateFormat);
                    groupParts[columnIndex] = StringUtil.isNull(groupedValue) ? NULL_GROUP_KEY : groupedValue;
                }
                QueryKey groupKey = new QueryKey(groupParts, columnCount);

                List<QueryRow> groupedRows = grouped.computeIfAbsent(groupKey, ignored -> new ArrayList<>(4));

                if (displayFields == null || index >= displayFields.size()) {
                    continue;
                }
                QueryRow displayClass = displayFields.get(index);
                String displayID = displayClass.getRowId();

                if (displayID.equals(uniqueID)) {
                    groupedRows.add(displayClass);
                }

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

    private List<GroupColumn> resolveGroupColumns(SortedSet<Integer> sortedKeys, FilterExecutionPlan plan) {
        List<GroupColumn> columns = new ArrayList<>();
        for (int groupedKey : sortedKeys) {
            String groupedFieldName = builder.getGroupFields().get(groupedKey);
            int index = plan.findFieldIndex(groupedFieldName);
            String dateFormat = SDF;
            String uniqueKey = ObjectUtil.castToString(groupedKey);
            if (builder.getFilterDateFormats().containsKey(uniqueKey)) {
                dateFormat = builder.getFilterDateFormats().get(uniqueKey);
            }
            QueryTimeBucket bucket = builder.getTimeBuckets().get(groupedFieldName);
            if (bucket != null) {
                int dateFieldIndex = plan.findFieldIndex(bucket.getDateField());
                if (dateFieldIndex >= 0) {
                    columns.add(new GroupColumn(dateFieldIndex, dateFormat, bucket.getPreset()));
                }
                continue;
            }
            if (index >= 0) {
                columns.add(new GroupColumn(index, dateFormat, null));
            }
        }
        return columns;
    }

    private static final class GroupColumn {
        private final int fieldIndex;
        private final String dateFormat;
        private final TimeBucketPreset timeBucket;

        private GroupColumn(int fieldIndex, String dateFormat, TimeBucketPreset timeBucket) {
            this.fieldIndex = fieldIndex;
            this.dateFormat = dateFormat;
            this.timeBucket = timeBucket;
        }
    }
}

