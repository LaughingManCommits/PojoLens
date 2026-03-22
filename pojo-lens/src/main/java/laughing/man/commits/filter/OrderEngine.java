package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.util.ObjectUtil;

import java.util.ArrayList;
import java.util.List;

final class OrderEngine {
    private static final int TOP_K_MIN_INPUT_ROWS = 64;
    private static final int TOP_K_MAX_LIMIT = 512;

    OrderEngine(FilterQueryBuilder builder) {
    }

    List<QueryRow> orderByFields(List<QueryRow> rows, Sort sortMethod, FilterExecutionPlan plan) {
        return orderByFields(rows, sortMethod, plan, null);
    }

    List<QueryRow> orderByFields(List<QueryRow> rows,
                                 Sort sortMethod,
                                 FilterExecutionPlan plan,
                                 Integer limit) {
        if (sortMethod == null || rows == null || rows.isEmpty()) {
            return rows;
        }

        List<FilterExecutionPlan.OrderColumn> columns = plan.getOrderColumns();
        if (columns.isEmpty()) {
            return new ArrayList<>(rows);
        }
        int topKLimit = normalizedTopKLimit(limit, rows.size());
        if (shouldUseTopK(topKLimit, rows.size())) {
            return topKOrderedRows(rows, columns, sortMethod, topKLimit);
        }

        List<QueryRow> results = new ArrayList<>(rows);
        results.sort((left, right) -> compareByColumns(left, right, columns, sortMethod));
        return results;
    }

    private int normalizedTopKLimit(Integer limit, int rowCount) {
        if (limit == null || limit <= 0 || rowCount <= 0 || limit >= rowCount) {
            return -1;
        }
        return limit;
    }

    private boolean shouldUseTopK(int limit, int rowCount) {
        return limit > 0
                && rowCount >= TOP_K_MIN_INPUT_ROWS
                && limit <= TOP_K_MAX_LIMIT
                && limit * 4 <= rowCount;
    }

    private List<QueryRow> topKOrderedRows(List<QueryRow> rows,
                                           List<FilterExecutionPlan.OrderColumn> columns,
                                           Sort sortMethod,
                                           int limit) {
        int[] heap = new int[limit];
        int size = 0;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            if (size < limit) {
                heap[size] = rowIndex;
                siftUpWorst(heap, size, rows, columns, sortMethod);
                size++;
                continue;
            }
            if (compareRowIndexes(rowIndex, heap[0], rows, columns, sortMethod) < 0) {
                heap[0] = rowIndex;
                siftDownWorst(heap, 0, size, rows, columns, sortMethod);
            }
        }

        int[] orderedIndexes = drainHeapBestFirst(heap, size, rows, columns, sortMethod);

        List<QueryRow> results = new ArrayList<>(size);
        for (int i = 0; i < orderedIndexes.length; i++) {
            results.add(rows.get(orderedIndexes[i]));
        }
        return results;
    }

    private int[] drainHeapBestFirst(int[] heap,
                                     int size,
                                     List<QueryRow> rows,
                                     List<FilterExecutionPlan.OrderColumn> columns,
                                     Sort sortMethod) {
        int[] ordered = new int[size];
        int heapSize = size;
        for (int target = size - 1; target >= 0; target--) {
            ordered[target] = heap[0];
            heapSize--;
            if (heapSize == 0) {
                break;
            }
            heap[0] = heap[heapSize];
            siftDownWorst(heap, 0, heapSize, rows, columns, sortMethod);
        }
        return ordered;
    }

    private void siftUpWorst(int[] heap,
                             int index,
                             List<QueryRow> rows,
                             List<FilterExecutionPlan.OrderColumn> columns,
                             Sort sortMethod) {
        int current = index;
        while (current > 0) {
            int parent = (current - 1) >>> 1;
            if (compareRowIndexes(heap[current], heap[parent], rows, columns, sortMethod) <= 0) {
                break;
            }
            swap(heap, current, parent);
            current = parent;
        }
    }

    private void siftDownWorst(int[] heap,
                               int index,
                               int size,
                               List<QueryRow> rows,
                               List<FilterExecutionPlan.OrderColumn> columns,
                               Sort sortMethod) {
        int current = index;
        while (true) {
            int left = (current << 1) + 1;
            if (left >= size) {
                return;
            }
            int right = left + 1;
            int worstChild = left;
            if (right < size && compareRowIndexes(heap[right], heap[left], rows, columns, sortMethod) > 0) {
                worstChild = right;
            }
            if (compareRowIndexes(heap[worstChild], heap[current], rows, columns, sortMethod) <= 0) {
                return;
            }
            swap(heap, current, worstChild);
            current = worstChild;
        }
    }

    private int compareRowIndexes(int leftIndex,
                                  int rightIndex,
                                  List<QueryRow> rows,
                                  List<FilterExecutionPlan.OrderColumn> columns,
                                  Sort sortMethod) {
        int cmp = compareByColumns(rows.get(leftIndex), rows.get(rightIndex), columns, sortMethod);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(leftIndex, rightIndex);
    }

    private void swap(int[] array, int left, int right) {
        int temp = array[left];
        array[left] = array[right];
        array[right] = temp;
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
