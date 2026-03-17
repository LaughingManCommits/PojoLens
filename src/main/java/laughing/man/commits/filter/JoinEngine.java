package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.RawQueryRow;
import laughing.man.commits.enums.Join;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.ObjectUtil;
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
                if (joinChildClasses == null || joinChildClasses.isEmpty()) {
                    continue;
                }

                String joinChildFieldName = null;
                String joinParentField = null;
                List<QueryRow> parentLoop = List.of();
                List<QueryRow> childLoop = List.of();
                if (Join.INNER_JOIN.equals(joinMethod) || Join.LEFT_JOIN.equals(joinMethod)) {
                    parentLoop = rows;
                    childLoop = joinChildClasses;
                    joinChildFieldName = builder.getJoinChildFields().get(joinID);
                    joinParentField = builder.getJoinParentFields().get(joinID);
                } else if (Join.RIGHT_JOIN.equals(joinMethod)) {
                    joinChildFieldName = builder.getJoinParentFields().get(joinID);
                    joinParentField = builder.getJoinChildFields().get(joinID);
                    parentLoop = joinChildClasses;
                    childLoop = rows;
                }

                if (parentLoop.isEmpty() || childLoop.isEmpty()) {
                    continue;
                }

                int parentFieldIndex = QueryFieldLookupUtil.findFieldIndex(parentLoop.get(0).getFields(), joinParentField);
                int childFieldIndex = QueryFieldLookupUtil.findFieldIndex(childLoop.get(0).getFields(), joinChildFieldName);
                if (parentFieldIndex < 0 || childFieldIndex < 0) {
                    continue;
                }

                Map<String, List<QueryRow>> childIndex = buildFieldIndex(childLoop, childFieldIndex);
                MergePlan mergePlan = buildMergePlan(parentLoop.get(0).getFields(), childLoop.get(0).getFields());
                List<QueryRow> joinedRows = new ArrayList<>(parentLoop.size());

                for (QueryRow parentClass : parentLoop) {
                    if (parentClass == null || parentClass.getFieldCount() <= parentFieldIndex) {
                        continue;
                    }
                    String parentValue = ObjectUtil.castToString(parentClass.getValueAt(parentFieldIndex));
                    List<QueryRow> matchingChildren = childIndex.get(parentValue);
                    if (matchingChildren != null && !matchingChildren.isEmpty()) {
                        for (QueryRow childClass : matchingChildren) {
                            joinedRows.add(buildJoinedRow(parentClass, childClass, mergePlan, false));
                        }
                    } else if (!Join.INNER_JOIN.equals(joinMethod)) {
                        joinedRows.add(buildJoinedRow(parentClass, null, mergePlan, true));
                    }
                }

                rows = joinedRows;
            }
        }
        return rows;
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

    private Map<String, List<QueryRow>> buildFieldIndex(List<QueryRow> classes, int fieldIndex) {
        Map<String, List<QueryRow>> index = new HashMap<>(CollectionUtil.expectedMapCapacity(classes.size()));
        for (QueryRow row : classes) {
            if (row == null) {
                continue;
            }
            String key = ObjectUtil.castToString(row.getValueAt(fieldIndex));
            index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        return index;
    }

    private MergePlan buildMergePlan(List<? extends QueryField> parentFields,
                                     List<? extends QueryField> childFields) {
        int parentSize = parentFields == null ? 0 : parentFields.size();
        int childSize = childFields == null ? 0 : childFields.size();
        HashSet<String> usedNames = new HashSet<>(Math.max(16, parentSize + childSize));
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
                    fieldName = uniqueChildName(fieldName, usedNames);
                }
                usedNames.add(fieldName);
                childSlots[i] = new MergeSlot(fieldName, !fieldName.equals(child.getFieldName()));
                schema.add(fieldName);
            }
        }
        return new MergePlan(parentSize, childSlots, List.copyOf(schema));
    }

    private String uniqueChildName(String baseName, HashSet<String> existing) {
        String candidate = "child_" + baseName;
        int index = 1;
        while (existing.contains(candidate)) {
            candidate = "child_" + baseName + "_" + index;
            index++;
        }
        return candidate;
    }
    private record MergePlan(int parentSize, MergeSlot[] childSlots, List<String> schema) {
    }

    private record MergeSlot(String fieldName, boolean renamed) {
    }
}

