package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.enums.Join;
import laughing.man.commits.util.ObjectUtil;
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
        if (!builder.getJoinClasses().isEmpty()) {
            SortedSet<Integer> orderKeys = new TreeSet<>(builder.getJoinClasses().keySet());
            for (int joinID : orderKeys) {
                List<QueryRow> joinChildClasses = builder.getJoinClasses().get(joinID);
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

                int parentFieldIndex = findFieldIndex(parentLoop.get(0).getFields(), joinParentField);
                int childFieldIndex = findFieldIndex(childLoop.get(0).getFields(), joinChildFieldName);
                if (parentFieldIndex < 0 || childFieldIndex < 0) {
                    continue;
                }

                Map<String, List<QueryRow>> childIndex = buildFieldIndex(childLoop, childFieldIndex);
                List<? extends QueryField> childTemplateFields = childLoop.get(0).getFields();
                List<QueryRow> joinedRows = new ArrayList<>(parentLoop.size());

                for (QueryRow parentClass : parentLoop) {
                    List<? extends QueryField> parentFields = parentClass.getFields();
                    if (parentFields == null || parentFieldIndex >= parentFields.size()) {
                        continue;
                    }
                    String parentValue = ObjectUtil.castToString(parentFields.get(parentFieldIndex).getValue());
                    List<QueryRow> matchingChildren = childIndex.get(parentValue);
                    if (matchingChildren != null && !matchingChildren.isEmpty()) {
                        for (QueryRow childClass : matchingChildren) {
                            joinedRows.add(buildJoinedRow(parentClass, parentFields, childClass.getFields(), false));
                        }
                    } else if (!Join.INNER_JOIN.equals(joinMethod)) {
                        joinedRows.add(buildJoinedRow(parentClass, parentFields, childTemplateFields, true));
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
                                    List<? extends QueryField> parentFields,
                                    List<? extends QueryField> childFields,
                                    boolean nullChildValues) {
        QueryRow row = new QueryRow();
        row.setRowType(parentClass.getRowType());
        row.setFields(mergeFields(parentFields, childFields, nullChildValues));
        return row;
    }

    private Map<String, List<QueryRow>> buildFieldIndex(List<QueryRow> classes, int fieldIndex) {
        Map<String, List<QueryRow>> index = new HashMap<>(expectedMapSize(classes.size()));
        for (QueryRow row : classes) {
            List<? extends QueryField> fields = row.getFields();
            if (fields == null) {
                continue;
            }
            if (fieldIndex >= fields.size()) {
                continue;
            }
            QueryField field = fields.get(fieldIndex);
            String key = ObjectUtil.castToString(field.getValue());
            index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        return index;
    }

    private int findFieldIndex(List<? extends QueryField> fields, String fieldName) {
        for (int i = 0; i < fields.size(); i++) {
            if (fieldName.equals(fields.get(i).getFieldName())) {
                return i;
            }
        }
        return -1;
    }

    private List<QueryField> mergeFields(List<? extends QueryField> parentFields,
                                          List<? extends QueryField> childFields,
                                          boolean nullChildValues) {
        int parentSize = parentFields == null ? 0 : parentFields.size();
        int childSize = childFields == null ? 0 : childFields.size();
        List<QueryField> merged = new ArrayList<>(parentSize + childSize);
        HashSet<String> names = new HashSet<>(Math.max(16, parentSize + childSize));

        if (parentFields != null) {
            for (QueryField parent : parentFields) {
                QueryField copy = new QueryField();
                copy.setFieldName(parent.getFieldName());
                copy.setValue(parent.getValue());
                merged.add(copy);
                names.add(copy.getFieldName());
            }
        }

        if (childFields != null) {
            for (QueryField child : childFields) {
                String fieldName = child.getFieldName();
                if (names.contains(fieldName)) {
                    fieldName = uniqueChildName(fieldName, names);
                }
                QueryField copy = new QueryField();
                copy.setFieldName(fieldName);
                copy.setValue(nullChildValues ? null : child.getValue());
                merged.add(copy);
                names.add(fieldName);
            }
        }
        return merged;
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

    private int expectedMapSize(int sourceSize) {
        if (sourceSize <= 0) {
            return 16;
        }
        return (int) ((sourceSize / 0.75f) + 1.0f);
    }
}

