package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.util.ObjectUtil;

import java.util.ArrayList;
import java.util.List;

final class OrderEngine {
    OrderEngine(FilterQueryBuilder builder) {
    }

    List<QueryRow> orderByFields(List<QueryRow> rows, Sort sortMethod, FilterExecutionPlan plan) {
        if (sortMethod == null || rows == null || rows.isEmpty()) {
            return rows;
        }

        List<FilterExecutionPlan.OrderColumn> columns = plan.getOrderColumns();
        List<QueryRow> results = new ArrayList<>(rows);
        results.sort((left, right) -> compareByColumns(left, right, columns, sortMethod));
        return results;
    }

    private int compareByColumns(QueryRow left,
                                 QueryRow right,
                                 List<FilterExecutionPlan.OrderColumn> columns,
                                 Sort sortMethod) {
        for (FilterExecutionPlan.OrderColumn column : columns) {
            Object leftValue = left.getValueAt(column.fieldIndex());
            Object rightValue = right.getValueAt(column.fieldIndex());
            int cmp = compareValues(leftValue, rightValue, column.dateFormat());
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
}

