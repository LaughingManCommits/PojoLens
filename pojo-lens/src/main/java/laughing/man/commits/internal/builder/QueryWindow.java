package laughing.man.commits.internal.builder;

import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable fluent window-definition descriptor.
 */
public final class QueryWindow {

    private final String alias;
    private final WindowFunction function;
    private final String valueField;
    private final boolean countAll;
    private final List<String> partitionFields;
    private final List<QueryWindowOrder> orderFields;
    private final QueryWindowFrame frame;

    private QueryWindow(String alias,
                        WindowFunction function,
                        String valueField,
                        boolean countAll,
                        List<String> partitionFields,
                        List<QueryWindowOrder> orderFields,
                        QueryWindowFrame frame) {
        this.alias = alias;
        this.function = function;
        this.valueField = valueField;
        this.countAll = countAll;
        this.partitionFields = List.copyOf(partitionFields);
        this.orderFields = List.copyOf(orderFields);
        this.frame = frame;
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
        if (alias == null || StringUtil.isNull(alias.trim())) {
            throw new IllegalArgumentException("alias is required");
        }
        if (function == null) {
            throw new IllegalArgumentException("function is required");
        }
        QueryWindowFrame normalizedFrame = frame == null ? QueryWindowFrame.running() : frame;
        String normalizedValueField = valueField == null || StringUtil.isNull(valueField.trim())
                ? null
                : valueField.trim();
        if (function.isRankFunction()) {
            if (normalizedValueField != null || countAll) {
                throw new IllegalArgumentException("Rank window functions do not accept value field arguments");
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
                if (field == null || StringUtil.isNull(field.trim())) {
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
                normalizedFrame
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
}
