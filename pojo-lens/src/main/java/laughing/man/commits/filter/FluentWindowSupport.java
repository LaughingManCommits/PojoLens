package laughing.man.commits.filter;

import laughing.man.commits.internal.builder.QueryWindow;
import laughing.man.commits.internal.builder.QueryWindowOrder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.RawQueryRow;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.SchemaIndexUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal window-function executor for fluent rank-window pipelines.
 */
final class FluentWindowSupport {

    private FluentWindowSupport() {
    }

    static List<QueryRow> apply(List<QueryRow> rows, List<QueryWindow> windows) {
        if (rows == null || rows.isEmpty() || windows == null || windows.isEmpty()) {
            return rows;
        }
        List<String> sourceSchema = SchemaIndexUtil.firstQueryRowFieldNames(rows);
        if (sourceSchema.isEmpty()) {
            return rows;
        }
        Map<String, Integer> sourceFieldIndexes = SchemaIndexUtil.indexFieldNames(sourceSchema);

        ArrayList<String> outputSchema = new ArrayList<>(sourceSchema);
        for (QueryWindow window : windows) {
            outputSchema.add(window.alias());
        }

        ArrayList<QueryRow> output = new ArrayList<>(rows.size());
        Object[][] outputValues = new Object[rows.size()][];
        int baseFieldCount = sourceSchema.size();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            QueryRow sourceRow = rows.get(rowIndex);
            Object[] values = new Object[outputSchema.size()];
            outputValues[rowIndex] = values;
            for (int fieldIndex = 0; fieldIndex < baseFieldCount; fieldIndex++) {
                values[fieldIndex] = sourceRow == null ? null : sourceRow.getValueAt(fieldIndex);
            }
            RawQueryRow projected = new RawQueryRow(values, outputSchema);
            if (sourceRow != null) {
                projected.setRowId(sourceRow.getRowId());
                projected.setRowType(sourceRow.getRowType());
            }
            output.add(projected);
        }

        for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
            evaluateWindow(rows, windows.get(windowIndex), sourceFieldIndexes, outputValues, baseFieldCount + windowIndex);
        }
        return output;
    }

    private static void evaluateWindow(List<QueryRow> rows,
                                       QueryWindow window,
                                       Map<String, Integer> sourceFieldIndexes,
                                       Object[][] outputValues,
                                       int targetFieldIndex) {
        int[] partitionIndexes = resolveIndexes(window.partitionFields(), sourceFieldIndexes);
        int[] orderIndexes = resolveOrderIndexes(window.orderFields(), sourceFieldIndexes);
        Sort[] orderSorts = resolveOrderSorts(window.orderFields());
        int valueIndex = resolveValueIndex(window, sourceFieldIndexes);

        Map<Object, List<Integer>> partitions = new LinkedHashMap<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            QueryRow row = rows.get(rowIndex);
            partitions.computeIfAbsent(partitionKey(row, partitionIndexes), ignored -> new ArrayList<>())
                    .add(rowIndex);
        }

        for (List<Integer> partitionRows : partitions.values()) {
            partitionRows.sort((left, right) -> compareRowIndexes(rows, left, right, orderIndexes, orderSorts));
            if (window.function().isRankFunction()) {
                assignRankValues(rows, window, partitionRows, orderIndexes, orderSorts, outputValues, targetFieldIndex);
            } else {
                assignAggregateValues(rows, window, partitionRows, valueIndex, outputValues, targetFieldIndex);
            }
        }
    }

    private static void assignRankValues(List<QueryRow> rows,
                                         QueryWindow window,
                                         List<Integer> partitionRows,
                                         int[] orderIndexes,
                                         Sort[] orderSorts,
                                         Object[][] outputValues,
                                         int targetFieldIndex) {
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
            outputValues[rowIndex][targetFieldIndex] = switch (window.function()) {
                case ROW_NUMBER -> position + 1L;
                case RANK -> rank;
                case DENSE_RANK -> denseRank;
                default -> throw new IllegalArgumentException(
                        "Unsupported rank window function '" + window.function() + "'");
            };
        }
    }

    private static void assignAggregateValues(List<QueryRow> rows,
                                              QueryWindow window,
                                              List<Integer> partitionRows,
                                              int valueIndex,
                                              Object[][] outputValues,
                                              int targetFieldIndex) {
        AggregateWindowAccumulator accumulator = new AggregateWindowAccumulator(rows, window, valueIndex);
        if (window.frame().isFullPartition()) {
            for (int rowIndex : partitionRows) {
                accumulator.add(rowIndex);
            }
            Object frameValue = accumulator.value();
            for (int rowIndex : partitionRows) {
                outputValues[rowIndex][targetFieldIndex] = frameValue;
            }
            return;
        }

        int frameStartPosition = 0;
        for (int position = 0; position < partitionRows.size(); position++) {
            int rowIndex = partitionRows.get(position);
            accumulator.add(rowIndex);
            if (window.frame().boundedPreceding()) {
                int firstAllowedPosition = position - window.frame().precedingRows();
                while (frameStartPosition < firstAllowedPosition) {
                    accumulator.remove();
                    frameStartPosition++;
                }
            }
            outputValues[rowIndex][targetFieldIndex] = accumulator.value();
        }
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

    private static int[] resolveOrderIndexes(List<QueryWindowOrder> orders, Map<String, Integer> sourceFieldIndexes) {
        int[] indexes = new int[orders.size()];
        for (int i = 0; i < orders.size(); i++) {
            indexes[i] = resolveIndex(orders.get(i).field(), sourceFieldIndexes);
        }
        return indexes;
    }

    private static Sort[] resolveOrderSorts(List<QueryWindowOrder> orders) {
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

    private static int resolveValueIndex(QueryWindow window, Map<String, Integer> sourceFieldIndexes) {
        if (window.function().isRankFunction() || window.countAll()) {
            return -1;
        }
        return resolveIndex(window.valueField(), sourceFieldIndexes);
    }

    private static Object partitionKey(QueryRow row, int[] partitionIndexes) {
        return switch (partitionIndexes.length) {
            case 0 -> PartitionKey.EMPTY;
            case 1 -> row == null ? null : row.getValueAt(partitionIndexes[0]);
            case 2 -> new PairPartitionKey(
                    row == null ? null : row.getValueAt(partitionIndexes[0]),
                    row == null ? null : row.getValueAt(partitionIndexes[1])
            );
            default -> multiPartitionKey(row, partitionIndexes);
        };
    }

    private static MultiPartitionKey multiPartitionKey(QueryRow row, int[] partitionIndexes) {
        ArrayList<Object> values = new ArrayList<>(partitionIndexes.length);
        for (int partitionIndex : partitionIndexes) {
            values.add(row == null ? null : row.getValueAt(partitionIndex));
        }
        return new MultiPartitionKey(List.copyOf(values));
    }

    private enum PartitionKey {
        EMPTY
    }

    private record PairPartitionKey(Object first, Object second) {
    }

    private record MultiPartitionKey(List<Object> values) {
    }

    private static final class AggregateWindowAccumulator {
        private final List<QueryRow> rows;
        private final QueryWindow window;
        private final int valueIndex;
        private final boolean trackEntries;
        private final ArrayDeque<FrameEntry> entries = new ArrayDeque<>();
        private long rowCount;
        private long nonNullCount;
        private double sum;
        private int fractionalCount;
        private boolean minDirty;
        private boolean maxDirty;
        private Number minValue;
        private double minDouble;
        private Number maxValue;
        private double maxDouble;

        private AggregateWindowAccumulator(List<QueryRow> rows, QueryWindow window, int valueIndex) {
            this.rows = rows;
            this.window = window;
            this.valueIndex = valueIndex;
            this.trackEntries = window.frame().boundedPreceding();
        }

        private void add(int rowIndex) {
            if (window.countAll()) {
                rowCount++;
                return;
            }
            QueryRow row = rows.get(rowIndex);
            Object raw = row == null ? null : row.getValueAt(valueIndex);
            if (raw == null) {
                if (trackEntries) {
                    entries.addLast(FrameEntry.nullValue());
                }
                return;
            }
            if (!(raw instanceof Number number)) {
                throw new IllegalArgumentException(
                        "Window function " + window.function() + " requires numeric field: " + window.valueField());
            }
            FrameEntry entry = FrameEntry.of(number);
            if (trackEntries) {
                entries.addLast(entry);
            }
            nonNullCount++;
            sum += entry.asDouble();
            if (entry.fractional()) {
                fractionalCount++;
            }
            if (minValue == null || entry.asDouble() < minDouble) {
                minValue = number;
                minDouble = entry.asDouble();
                minDirty = false;
            }
            if (maxValue == null || entry.asDouble() > maxDouble) {
                maxValue = number;
                maxDouble = entry.asDouble();
                maxDirty = false;
            }
        }

        private void remove() {
            if (window.countAll()) {
                rowCount--;
                return;
            }
            FrameEntry entry = entries.removeFirst();
            if (entry.number() == null) {
                return;
            }
            nonNullCount--;
            sum -= entry.asDouble();
            if (entry.fractional()) {
                fractionalCount--;
            }
            if (entry.number() == minValue) {
                minDirty = true;
            }
            if (entry.number() == maxValue) {
                maxDirty = true;
            }
        }

        private Object value() {
            return switch (window.function()) {
                case COUNT -> window.countAll() ? rowCount : nonNullCount;
                case SUM -> nonNullCount == 0 ? null : fractionalCount > 0 ? sum : (long) sum;
                case AVG -> nonNullCount == 0 ? null : sum / nonNullCount;
                case MIN -> min();
                case MAX -> max();
                default -> throw new IllegalArgumentException(
                        "Unsupported aggregate window function '" + window.function() + "'");
            };
        }

        private Number min() {
            if (nonNullCount == 0) {
                return null;
            }
            if (minDirty) {
                recomputeMin();
            }
            return minValue;
        }

        private Number max() {
            if (nonNullCount == 0) {
                return null;
            }
            if (maxDirty) {
                recomputeMax();
            }
            return maxValue;
        }

        private void recomputeMin() {
            minValue = null;
            for (FrameEntry entry : entries) {
                if (entry.number() == null) {
                    continue;
                }
                if (minValue == null || entry.asDouble() < minDouble) {
                    minValue = entry.number();
                    minDouble = entry.asDouble();
                }
            }
            minDirty = false;
        }

        private void recomputeMax() {
            maxValue = null;
            for (FrameEntry entry : entries) {
                if (entry.number() == null) {
                    continue;
                }
                if (maxValue == null || entry.asDouble() > maxDouble) {
                    maxValue = entry.number();
                    maxDouble = entry.asDouble();
                }
            }
            maxDirty = false;
        }
    }

    private record FrameEntry(Number number, double asDouble, boolean fractional) {
        private static FrameEntry nullValue() {
            return new FrameEntry(null, 0D, false);
        }

        private static FrameEntry of(Number number) {
            return new FrameEntry(
                    number,
                    number.doubleValue(),
                    number instanceof Float || number instanceof Double
            );
        }
    }
}
