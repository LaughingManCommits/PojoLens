package laughing.man.commits.internal.builder;

import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable fluent window-definition descriptor.
 */
public final class QueryWindow {

    private static final int DEFAULT_OFFSET = 1;

    private final String alias;
    private final WindowFunction function;
    private final String valueField;
    private final boolean countAll;
    private final List<String> partitionFields;
    private final List<QueryWindowOrder> orderFields;
    private final QueryWindowFrame frame;
    private final int offset;
    private final Object defaultValue;

    private QueryWindow(String alias,
                        WindowFunction function,
                        String valueField,
                        boolean countAll,
                        List<String> partitionFields,
                        List<QueryWindowOrder> orderFields,
                        QueryWindowFrame frame,
                        int offset,
                        Object defaultValue) {
        this.alias = alias;
        this.function = function;
        this.valueField = valueField;
        this.countAll = countAll;
        this.partitionFields = List.copyOf(partitionFields);
        this.orderFields = List.copyOf(orderFields);
        this.frame = frame;
        this.offset = offset;
        this.defaultValue = defaultValue;
    }

    public static QueryWindow of(String alias,
                                 WindowFunction function,
                                 List<String> partitionFields,
                                 List<QueryWindowOrder> orderFields) {
        return of(alias, function, null, false, partitionFields, orderFields);
    }

    public static QueryWindow of(String alias,
                                 WindowFunction function,
                                 String valueField,
                                 boolean countAll,
                                 List<String> partitionFields,
                                 List<QueryWindowOrder> orderFields) {
        return of(alias, function, valueField, countAll, partitionFields, orderFields, QueryWindowFrame.running());
    }

    public static QueryWindow of(String alias,
                                 WindowFunction function,
                                 String valueField,
                                 boolean countAll,
                                 List<String> partitionFields,
                                 List<QueryWindowOrder> orderFields,
                                 QueryWindowFrame frame) {
        return create(alias, function, valueField, countAll, partitionFields, orderFields, frame,
                DEFAULT_OFFSET, null);
    }

    /**
     * {@code LAG}/{@code LEAD} window: the value field read {@code offset} rows before
     * ({@code LAG}) or after ({@code LEAD}) the current row within its partition, or
     * {@code defaultValue} when that row is outside the partition.
     */
    public static QueryWindow offset(String alias,
                                     WindowFunction function,
                                     String valueField,
                                     int offset,
                                     Object defaultValue,
                                     List<String> partitionFields,
                                     List<QueryWindowOrder> orderFields) {
        if (function != null && !function.isOffsetFunction()) {
            throw new IllegalArgumentException(function + " is not an offset window function");
        }
        return create(alias, function, valueField, false, partitionFields, orderFields,
                QueryWindowFrame.running(), offset, defaultValue);
    }

    private static QueryWindow create(String alias,
                                      WindowFunction function,
                                      String valueField,
                                      boolean countAll,
                                      List<String> partitionFields,
                                      List<QueryWindowOrder> orderFields,
                                      QueryWindowFrame frame,
                                      int offset,
                                      Object defaultValue) {
        if (StringUtil.isNullOrBlank(alias)) {
            throw new IllegalArgumentException("alias is required");
        }
        if (function == null) {
            throw new IllegalArgumentException("function is required");
        }
        QueryWindowFrame normalizedFrame = frame == null ? QueryWindowFrame.running() : frame;
        String normalizedValueField = StringUtil.isNullOrBlank(valueField)
                ? null
                : valueField.trim();
        if (function.isRankFunction()) {
            if (normalizedValueField != null || countAll) {
                throw new IllegalArgumentException("Rank window functions do not accept value field arguments");
            }
        } else if (function.isOffsetFunction()) {
            if (countAll || normalizedValueField == null) {
                throw new IllegalArgumentException("Window value field is required for " + function);
            }
            if (offset < 0) {
                throw new IllegalArgumentException(function + " offset must be >= 0 but was " + offset
                        + "; use " + (function == WindowFunction.LAG ? "LEAD" : "LAG") + " to look the other way");
            }
            if (!normalizedFrame.isRunning()) {
                throw new IllegalArgumentException(function + " does not accept a window frame");
            }
        } else {
            if (countAll && !function.supportsCountAll()) {
                throw new IllegalArgumentException(function + " does not support '*' window argument");
            }
            if (countAll && normalizedValueField != null) {
                throw new IllegalArgumentException("COUNT(*) window cannot define an explicit value field");
            }
            if (!countAll && normalizedValueField == null) {
                throw new IllegalArgumentException("Window value field is required for " + function);
            }
        }
        if (orderFields == null || orderFields.isEmpty()) {
            throw new IllegalArgumentException("window ORDER BY fields are required");
        }
        ArrayList<String> normalizedPartitions = new ArrayList<>();
        if (partitionFields != null) {
            for (String field : partitionFields) {
                if (StringUtil.isNullOrBlank(field)) {
                    continue;
                }
                normalizedPartitions.add(field.trim());
            }
        }
        ArrayList<QueryWindowOrder> normalizedOrders = new ArrayList<>(orderFields.size());
        for (QueryWindowOrder order : orderFields) {
            if (order == null) {
                continue;
            }
            normalizedOrders.add(order);
        }
        if (normalizedOrders.isEmpty()) {
            throw new IllegalArgumentException("window ORDER BY fields are required");
        }
        return new QueryWindow(
                alias.trim(),
                function,
                normalizedValueField,
                countAll,
                normalizedPartitions,
                normalizedOrders,
                normalizedFrame,
                function.isOffsetFunction() ? offset : DEFAULT_OFFSET,
                function.isOffsetFunction() ? defaultValue : null
        );
    }

    public String alias() {
        return alias;
    }

    public WindowFunction function() {
        return function;
    }

    public String valueField() {
        return valueField;
    }

    public boolean countAll() {
        return countAll;
    }

    public List<String> partitionFields() {
        return partitionFields;
    }

    public List<QueryWindowOrder> orderFields() {
        return orderFields;
    }

    public QueryWindowFrame frame() {
        return frame;
    }

    /**
     * Rows between the current row and the row an offset function reads; {@code 1}
     * for every other function.
     */
    public int offset() {
        return offset;
    }

    /**
     * Value an offset function returns when the offset row is outside the partition;
     * {@code null} for every other function.
     */
    public Object defaultValue() {
        return defaultValue;
    }
}
