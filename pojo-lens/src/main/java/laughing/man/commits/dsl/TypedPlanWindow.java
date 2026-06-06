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

    private final WindowFunction function;
    private final String valueField;
    private final boolean countAll;
    private final String alias;
    private final List<String> partitionFields;
    private final List<PlanPreviewOrder> orderFields;
    private final QueryWindowFrame frame;

    public TypedPlanWindow(WindowFunction function,
                           String valueField,
                           boolean countAll,
                           String alias,
                           List<String> partitionFields,
                           List<PlanPreviewOrder> orderFields,
                           QueryWindowFrame frame) {
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

    public QueryWindowFrame frame() {
        return frame;
    }
}
