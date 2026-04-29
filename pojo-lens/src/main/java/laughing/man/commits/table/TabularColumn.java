package laughing.man.commits.table;

import laughing.man.commits.util.StringUtil;

import java.util.Objects;

/**
 * Immutable column metadata for tabular query results.
 */
public final class TabularColumn {

    private final String name;
    private final String label;
    private final Class<?> type;
    private final int order;
    private final String formatHint;

    private TabularColumn(String name, String label, Class<?> type, int order, String formatHint) {
        this.name = StringUtil.requireNonBlank(name, "name");
        this.label = StringUtil.requireNonBlank(label, "label");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.order = order;
        this.formatHint = formatHint;
    }

    public static TabularColumn of(String name, String label, Class<?> type, int order, String formatHint) {
        return new TabularColumn(name, label, type, order, formatHint);
    }

    public String name() {
        return name;
    }

    public String label() {
        return label;
    }

    public Class<?> type() {
        return type;
    }

    public int order() {
        return order;
    }

    public String formatHint() {
        return formatHint;
    }

    public String typeName() {
        return type.getSimpleName();
    }

}

