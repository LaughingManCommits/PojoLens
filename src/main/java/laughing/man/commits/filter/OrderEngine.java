package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.util.ObjectUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static laughing.man.commits.EngineDefaults.SDF;

final class OrderEngine {

    private final FilterQueryBuilder builder;

    OrderEngine(FilterQueryBuilder builder) {
        this.builder = builder;
    }

    List<QueryRow> orderByFields(List<QueryRow> rows, Sort sortMethod, FilterExecutionPlan plan) {
        if (sortMethod == null || rows == null || rows.isEmpty()) {
            return rows;
        }

        SortedSet<Integer> orderKeys = new TreeSet<>(builder.getOrderFields().keySet());
        List<OrderColumn> columns = resolveOrderColumns(orderKeys, plan);
        List<QueryRow> results = new ArrayList<>(rows);
        results.sort((left, right) -> compareByColumns(left, right, columns, sortMethod));
        return results;
    }

    private int compareByColumns(QueryRow left,
                                 QueryRow right,
                                 List<OrderColumn> columns,
                                 Sort sortMethod) {
        List<? extends QueryField> leftFields = left.getFields();
        List<? extends QueryField> rightFields = right.getFields();
        for (OrderColumn column : columns) {
            Object leftValue = column.fieldIndex < leftFields.size()
                    ? leftFields.get(column.fieldIndex).getValue()
                    : null;
            Object rightValue = column.fieldIndex < rightFields.size()
                    ? rightFields.get(column.fieldIndex).getValue()
                    : null;
            int cmp = compareValues(leftValue, rightValue, column.dateFormat);
            if (cmp != 0) {
                return Sort.DESC.equals(sortMethod) ? -cmp : cmp;
            }
        }
        return 0;
    }

    private int compareValues(Object leftValue, Object rightValue, String dateFormat) {
        if (leftValue == null && rightValue == null) {
            return 0;
        }
        if (leftValue == null) {
            return -1;
        }
        if (rightValue == null) {
            return 1;
        }
        if (leftValue instanceof Number && rightValue instanceof Number) {
            return Double.compare(((Number) leftValue).doubleValue(), ((Number) rightValue).doubleValue());
        }
        if (leftValue instanceof java.util.Date && rightValue instanceof java.util.Date) {
            return ((java.util.Date) leftValue).compareTo((java.util.Date) rightValue);
        }
        if (leftValue instanceof Boolean && rightValue instanceof Boolean) {
            return Boolean.compare((Boolean) leftValue, (Boolean) rightValue);
        }
        if (leftValue instanceof Comparable && leftValue.getClass().isInstance(rightValue)) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            int cmp = ((Comparable) leftValue).compareTo(rightValue);
            return cmp;
        }
        String leftText = ObjectUtil.castToString(leftValue, dateFormat);
        String rightText = ObjectUtil.castToString(rightValue, dateFormat);
        if (leftText == null && rightText == null) {
            return 0;
        }
        if (leftText == null) {
            return -1;
        }
        if (rightText == null) {
            return 1;
        }
        return leftText.compareTo(rightText);
    }

    private List<OrderColumn> resolveOrderColumns(SortedSet<Integer> orderKeys, FilterExecutionPlan plan) {
        List<OrderColumn> columns = new ArrayList<>();
        for (int orderKey : orderKeys) {
            String fieldName = builder.getOrderFields().get(orderKey);
            int index = plan.findFieldIndexIgnoreCase(fieldName);
            if (index < 0) {
                continue;
            }
            String dateFormat = SDF;
            String uniqueKey = ObjectUtil.castToString(orderKey);
            if (builder.getFilterDateFormats().containsKey(uniqueKey)) {
                dateFormat = builder.getFilterDateFormats().get(uniqueKey);
            }
            columns.add(new OrderColumn(index, dateFormat));
        }
        return columns;
    }

    private static final class OrderColumn {
        private final int fieldIndex;
        private final String dateFormat;

        private OrderColumn(int fieldIndex, String dateFormat) {
            this.fieldIndex = fieldIndex;
            this.dateFormat = dateFormat;
        }
    }
}

