package laughing.man.commits.dsl;

import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.internal.builder.QueryWindowFrame;
import laughing.man.commits.sqllike.PlanPreviewOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Structural description of a configured typed window output.
 */
public final class TypedPlanWindow {

    private static final int DEFAULT_OFFSET = 1;

    private final WindowFunction function;
    private final String valueField;
    private final boolean countAll;
    private final String alias;
    private final List<String> partitionFields;
    private final List<PlanPreviewOrder> orderFields;
    private final QueryWindowFrame frame;
    private final int offset;
    private final Object defaultValue;

    public TypedPlanWindow(WindowFunction function,
                           String valueField,
                           boolean countAll,
                           String alias,
                           List<String> partitionFields,
                           List<PlanPreviewOrder> orderFields,
                           QueryWindowFrame frame) {
        this(function, valueField, countAll, alias, partitionFields, orderFields, frame, DEFAULT_OFFSET, null);
    }

    /**
     * @param offset       {@code LAG}/{@code LEAD} offset; {@code 1} for other functions
     * @param defaultValue {@code LAG}/{@code LEAD} default, or {@code null}
     */
    public TypedPlanWindow(WindowFunction function,
                           String valueField,
                           boolean countAll,
                           String alias,
                           List<String> partitionFields,
                           List<PlanPreviewOrder> orderFields,
                           QueryWindowFrame frame,
                           int offset,
                           Object defaultValue) {
        this.offset = offset;
        this.defaultValue = defaultValue;
        this.function = Objects.requireNonNull(function, "function must not be null");
        this.valueField = valueField;
        this.countAll = countAll;
        this.alias = Objects.requireNonNull(alias, "alias must not be null");
        this.partitionFields = List.copyOf(Objects.requireNonNull(partitionFields, "partitionFields must not be null"));
        this.orderFields = List.copyOf(new ArrayList<>(Objects.requireNonNull(orderFields, "orderFields must not be null")));
        this.frame = Objects.requireNonNull(frame, "frame must not be null");
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

    public String alias() {
        return alias;
    }

    public List<String> partitionFields() {
        return partitionFields;
    }

    public List<PlanPreviewOrder> orderFields() {
        return orderFields;
    }

    /**
     * The ROWS frame; offset windows ({@code LAG}/{@code LEAD}) report the default running
     * frame, which they ignore.
     */
    public QueryWindowFrame frame() {
        return frame;
    }

    /**
     * Rows between the current row and the row {@code LAG}/{@code LEAD} reads; {@code 1}
     * for other functions.
     */
    public int offset() {
        return offset;
    }

    /**
     * The {@code LAG}/{@code LEAD} value used outside the partition, or {@code null}.
     */
    public Object defaultValue() {
        return defaultValue;
    }
}
