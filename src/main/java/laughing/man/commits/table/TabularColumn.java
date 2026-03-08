package laughing.man.commits.table;

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
        this.name = requireText(name, "name");
        this.label = requireText(label, "label");
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

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be null/blank");
        }
        return value;
    }
}

