package laughing.man.commits.sqllike.internal.window;

import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.RawQueryRow;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.SchemaIndexUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal window-function executor for SQL-like rank functions.
 */
public final class SqlLikeWindowSupport {

    private SqlLikeWindowSupport() {
    }

    public static List<QueryRow> applyWindowFunctions(List<QueryRow> rows, SelectAst select) {
        if (rows == null || rows.isEmpty() || select == null || !select.hasWindowFields()) {
            return rows;
        }
        List<String> sourceSchema = SchemaIndexUtil.firstQueryRowFieldNames(rows);
        if (sourceSchema.isEmpty()) {
            return rows;
        }
        Map<String, Integer> sourceFieldIndexes = SchemaIndexUtil.indexFieldNames(sourceSchema);
        ArrayList<SelectFieldAst> windowFields = new ArrayList<>();
        for (SelectFieldAst field : select.fields()) {
            if (field.windowField()) {
                windowFields.add(field);
            }
        }
        if (windowFields.isEmpty()) {
            return rows;
        }

        Object[][] windowValues = new Object[windowFields.size()][rows.size()];
        for (int i = 0; i < windowFields.size(); i++) {
            windowValues[i] = evaluateWindowField(rows, windowFields.get(i), sourceFieldIndexes);
        }

        ArrayList<String> outputSchema = new ArrayList<>(sourceSchema);
        for (SelectFieldAst windowField : windowFields) {
            outputSchema.add(windowField.outputName());
        }

        ArrayList<QueryRow> output = new ArrayList<>(rows.size());
        int baseFieldCount = sourceSchema.size();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            QueryRow sourceRow = rows.get(rowIndex);
            Object[] values = new Object[outputSchema.size()];
            for (int fieldIndex = 0; fieldIndex < baseFieldCount; fieldIndex++) {
                values[fieldIndex] = sourceRow == null ? null : sourceRow.getValueAt(fieldIndex);
            }
            for (int windowIndex = 0; windowIndex < windowFields.size(); windowIndex++) {
                values[baseFieldCount + windowIndex] = windowValues[windowIndex][rowIndex];
            }
            RawQueryRow projected = new RawQueryRow(values, outputSchema);
            if (sourceRow != null) {
                projected.setRowId(sourceRow.getRowId());
                projected.setRowType(sourceRow.getRowType());
            }
            output.add(projected);
        }
        return output;
    }

    private static Object[] evaluateWindowField(List<QueryRow> rows,
                                                SelectFieldAst windowField,
                                                Map<String, Integer> sourceFieldIndexes) {
        int[] partitionIndexes = resolveIndexes(windowField.windowPartitionFields(), sourceFieldIndexes);
        int[] orderIndexes = resolveOrderIndexes(windowField.windowOrderFields(), sourceFieldIndexes);
        Sort[] orderSorts = resolveOrderSorts(windowField.windowOrderFields());

        Map<PartitionKey, List<Integer>> partitions = new LinkedHashMap<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            QueryRow row = rows.get(rowIndex);
            partitions.computeIfAbsent(partitionKey(row, partitionIndexes), ignored -> new ArrayList<>())
                    .add(rowIndex);
        }

        Object[] values = new Object[rows.size()];
        String function = windowField.windowFunction();
        for (List<Integer> partitionRows : partitions.values()) {
            partitionRows.sort((left, right) -> compareRowIndexes(rows, left, right, orderIndexes, orderSorts));
            long rank = 1L;
            long denseRank = 1L;
            for (int position = 0; position < partitionRows.size(); position++) {
                int rowIndex = partitionRows.get(position);
                if (position > 0) {
                    int previousIndex = partitionRows.get(position - 1);
                    boolean tie = compareOrderValues(rows, rowIndex, previousIndex, orderIndexes, orderSorts) == 0;
                    if (!tie) {
                        rank = position + 1L;
                        denseRank++;
                    }
                }
                values[rowIndex] = switch (function) {
                    case "ROW_NUMBER" -> position + 1L;
                    case "RANK" -> rank;
                    case "DENSE_RANK" -> denseRank;
                    default -> throw new IllegalArgumentException("Unsupported window function '" + function + "'");
                };
            }
        }
        return values;
    }

    private static int compareRowIndexes(List<QueryRow> rows,
                                         int leftIndex,
                                         int rightIndex,
                                         int[] orderIndexes,
                                         Sort[] orderSorts) {
        int compare = compareOrderValues(rows, leftIndex, rightIndex, orderIndexes, orderSorts);
        if (compare != 0) {
            return compare;
        }
        return Integer.compare(leftIndex, rightIndex);
    }

    private static int compareOrderValues(List<QueryRow> rows,
                                          int leftIndex,
                                          int rightIndex,
                                          int[] orderIndexes,
                                          Sort[] orderSorts) {
        QueryRow left = rows.get(leftIndex);
        QueryRow right = rows.get(rightIndex);
        for (int i = 0; i < orderIndexes.length; i++) {
            Object leftValue = left == null ? null : left.getValueAt(orderIndexes[i]);
            Object rightValue = right == null ? null : right.getValueAt(orderIndexes[i]);
            int compare = compareValues(leftValue, rightValue);
            if (compare == 0) {
                continue;
            }
            if (orderSorts[i] == Sort.DESC) {
                compare = -compare;
            }
            return compare;
        }
        return 0;
    }

    private static int compareValues(Object leftValue, Object rightValue) {
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
        if (leftValue instanceof Date && rightValue instanceof Date) {
            return ((Date) leftValue).compareTo((Date) rightValue);
        }
        if (leftValue instanceof Boolean && rightValue instanceof Boolean) {
            return Boolean.compare((Boolean) leftValue, (Boolean) rightValue);
        }
        if (leftValue instanceof Comparable<?> comparable && leftValue.getClass().isInstance(rightValue)) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            int compare = ((Comparable) comparable).compareTo(rightValue);
            return compare;
        }
        String leftText = ObjectUtil.castToString(leftValue);
        String rightText = ObjectUtil.castToString(rightValue);
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

    private static int[] resolveIndexes(List<String> fieldNames, Map<String, Integer> sourceFieldIndexes) {
        int[] indexes = new int[fieldNames.size()];
        for (int i = 0; i < fieldNames.size(); i++) {
            indexes[i] = resolveIndex(fieldNames.get(i), sourceFieldIndexes);
        }
        return indexes;
    }

    private static int[] resolveOrderIndexes(List<OrderAst> orders, Map<String, Integer> sourceFieldIndexes) {
        int[] indexes = new int[orders.size()];
        for (int i = 0; i < orders.size(); i++) {
            indexes[i] = resolveIndex(orders.get(i).field(), sourceFieldIndexes);
        }
        return indexes;
    }

    private static Sort[] resolveOrderSorts(List<OrderAst> orders) {
        Sort[] sorts = new Sort[orders.size()];
        for (int i = 0; i < orders.size(); i++) {
            sorts[i] = orders.get(i).sort();
        }
        return sorts;
    }

    private static int resolveIndex(String fieldName, Map<String, Integer> sourceFieldIndexes) {
        int index = SchemaIndexUtil.findFieldIndex(sourceFieldIndexes, fieldName);
        if (index >= 0) {
            return index;
        }
        throw new IllegalArgumentException("Unknown window field '" + fieldName + "'");
    }

    private static PartitionKey partitionKey(QueryRow row, int[] partitionIndexes) {
        if (partitionIndexes.length == 0) {
            return PartitionKey.EMPTY;
        }
        ArrayList<Object> values = new ArrayList<>(partitionIndexes.length);
        for (int partitionIndex : partitionIndexes) {
            values.add(row == null ? null : row.getValueAt(partitionIndex));
        }
        return new PartitionKey(List.copyOf(values));
    }

    private record PartitionKey(List<Object> values) {
        private static final PartitionKey EMPTY = new PartitionKey(List.of());
    }
}
