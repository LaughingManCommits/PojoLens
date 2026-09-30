package laughing.man.commits.filter;

import laughing.man.commits.internal.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.RawQueryRow;
import laughing.man.commits.enums.Join;
import laughing.man.commits.internal.JoinFieldNames;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.QueryFieldLookupUtil;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

final class JoinEngine {

    private static final int DEFAULT_MAP_CAPACITY = 16;

    private final FilterQueryBuilder builder;

    JoinEngine(FilterQueryBuilder builder) {
        this.builder = builder;
    }

    <T> List<QueryRow> join(List<T> bean) {
        List<QueryRow> rows = toRows(bean);
        Map<Integer, List<QueryRow>> joinClasses = builder.getJoinClassesForExecution();
        if (!joinClasses.isEmpty()) {
            SortedSet<Integer> orderKeys = new TreeSet<>(joinClasses.keySet());
            for (int joinID : orderKeys) {
                List<QueryRow> joinChildClasses = joinClasses.get(joinID);
                Join joinMethod = builder.getJoinMethods().get(joinID);
                if (joinChildClasses == null || joinChildClasses.isEmpty() || rows.isEmpty()) {
                    if (Join.INNER_JOIN.equals(joinMethod)) {
                        // Nothing can match: an inner join over an empty side yields no rows.
                        rows = new ArrayList<>();
                    }
                    continue;
                }

                String rootField = builder.getJoinParentFields().get(joinID);
                String joinedField = builder.getJoinChildFields().get(joinID);
                int rootFieldIndex = QueryFieldLookupUtil.findFieldIndex(rows.get(0).getFields(), rootField);
                int joinedFieldIndex = QueryFieldLookupUtil.findFieldIndex(joinChildClasses.get(0).getFields(), joinedField);
                if (rootFieldIndex < 0 || joinedFieldIndex < 0) {
                    continue;
                }

                List<QueryRow> joinedRows;
                if (Join.RIGHT_JOIN.equals(joinMethod)) {
                    // RIGHT JOIN drives from the joined rows: their columns come first and keep
                    // their names; colliding existing columns take the child_ prefix.
                    MergePlan mergePlan = buildMergePlan(joinChildClasses.get(0).getFields(), rows.get(0).getFields());
                    joinedRows = leftOrInnerJoin(joinChildClasses, joinedFieldIndex, rows, rootFieldIndex, mergePlan, false);
                } else {
                    MergePlan mergePlan = buildMergePlan(rows.get(0).getFields(), joinChildClasses.get(0).getFields());
                    joinedRows = leftOrInnerJoin(rows, rootFieldIndex, joinChildClasses, joinedFieldIndex, mergePlan,
                            Join.INNER_JOIN.equals(joinMethod));
                }

                rows = joinedRows;
            }
        }
        return rows;
    }

    private List<QueryRow> leftOrInnerJoin(List<QueryRow> drivingRows,
                                           int drivingFieldIndex,
                                           List<QueryRow> joinRows,
                                           int joinedFieldIndex,
                                           MergePlan mergePlan,
                                           boolean inner) {
        Map<Object, List<QueryRow>> joinIndex = buildFieldIndex(joinRows, joinedFieldIndex);
        List<QueryRow> joinedRows = new ArrayList<>(drivingRows.size());
        for (QueryRow drivingRow : drivingRows) {
            if (drivingRow == null || drivingRow.getFieldCount() <= drivingFieldIndex) {
                continue;
            }
            Object key = JoinKeys.normalize(drivingRow.getValueAt(drivingFieldIndex));
            List<QueryRow> matches = key == null ? null : joinIndex.get(key);
            if (matches != null && !matches.isEmpty()) {
                for (QueryRow match : matches) {
                    joinedRows.add(buildJoinedRow(drivingRow, match, mergePlan, false));
                }
            } else if (!inner) {
                joinedRows.add(buildJoinedRow(drivingRow, null, mergePlan, true));
            }
        }
        return joinedRows;
    }

    private <T> List<QueryRow> toRows(List<T> bean) {
        List<QueryRow> rows = new ArrayList<>();
        if (bean == null || bean.isEmpty()) {
            return rows;
        }
        Object first = null;
        for (Object item : bean) {
            if (item != null) {
                first = item;
                break;
            }
        }
        if (first instanceof QueryRow) {
            for (Object item : bean) {
                if (item instanceof QueryRow queryRow) {
                    rows.add(queryRow);
                }
            }
            return rows;
        }
        return ReflectionUtil.toDomainRows(bean);
    }

    private QueryRow buildJoinedRow(QueryRow parentClass,
                                    QueryRow childRow,
                                    MergePlan mergePlan,
                                    boolean nullChildValues) {
        List<String> schema = mergePlan.schema();
        Object[] values = new Object[schema.size()];
        int parentSize = mergePlan.parentSize();
        for (int i = 0; i < parentSize; i++) {
            values[i] = parentClass.getValueAt(i);
        }
        if (!nullChildValues && childRow != null) {
            MergeSlot[] childSlots = mergePlan.childSlots();
            for (int i = 0; i < childSlots.length; i++) {
                values[parentSize + i] = childRow.getValueAt(i);
            }
        }
        RawQueryRow row = new RawQueryRow(values, schema);
        row.setRowType(parentClass.getRowType());
        return row;
    }

    private Map<Object, List<QueryRow>> buildFieldIndex(List<QueryRow> classes, int fieldIndex) {
        Map<Object, List<QueryRow>> index = new HashMap<>(CollectionUtil.expectedMapCapacity(classes.size()));
        for (QueryRow row : classes) {
            if (row == null) {
                continue;
            }
            Object key = JoinKeys.normalize(row.getValueAt(fieldIndex));
            if (key == null) {
                continue;
            }
            index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        return index;
    }

    private MergePlan buildMergePlan(List<? extends QueryField> parentFields,
                                     List<? extends QueryField> childFields) {
        int parentSize = parentFields == null ? 0 : parentFields.size();
        int childSize = childFields == null ? 0 : childFields.size();
        HashSet<String> usedNames = new HashSet<>(Math.max(DEFAULT_MAP_CAPACITY, parentSize + childSize));
        MergeSlot[] childSlots = new MergeSlot[childSize];
        List<String> schema = new ArrayList<>(parentSize + childSize);

        if (parentFields != null) {
            for (QueryField parent : parentFields) {
                String name = parent.getFieldName();
                usedNames.add(name);
                schema.add(name);
            }
        }

        if (childFields != null) {
            for (int i = 0; i < childFields.size(); i++) {
                QueryField child = childFields.get(i);
                String fieldName = child.getFieldName();
                if (usedNames.contains(fieldName)) {
                    fieldName = JoinFieldNames.uniqueChildName(fieldName, usedNames);
                }
                usedNames.add(fieldName);
                childSlots[i] = new MergeSlot(fieldName, !fieldName.equals(child.getFieldName()));
                schema.add(fieldName);
            }
        }
        return new MergePlan(parentSize, childSlots, List.copyOf(schema));
    }

    private record MergePlan(int parentSize, MergeSlot[] childSlots, List<String> schema) {
    }

    private record MergeSlot(String fieldName, boolean renamed) {
    }
}

