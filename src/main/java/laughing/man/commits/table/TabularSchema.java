package laughing.man.commits.table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable ordered schema metadata for tabular query outputs.
 */
public final class TabularSchema {

    private final Class<?> rowType;
    private final List<TabularColumn> columns;
    private final Map<String, TabularColumn> columnsByName;

    private TabularSchema(Class<?> rowType, List<TabularColumn> columns) {
        this.rowType = Objects.requireNonNull(rowType, "rowType must not be null");
        Objects.requireNonNull(columns, "columns must not be null");
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        LinkedHashMap<String, TabularColumn> byName = new LinkedHashMap<>();
        for (TabularColumn column : columns) {
            if (byName.putIfAbsent(column.name(), column) != null) {
                throw new IllegalArgumentException("Duplicate schema column '" + column.name() + "'");
            }
        }
        this.columnsByName = Collections.unmodifiableMap(byName);
    }

    public static TabularSchema of(Class<?> rowType, List<TabularColumn> columns) {
        return new TabularSchema(rowType, columns);
    }

    public Class<?> rowType() {
        return rowType;
    }

    public List<TabularColumn> columns() {
        return columns;
    }

    public int size() {
        return columns.size();
    }

    public List<String> names() {
        ArrayList<String> names = new ArrayList<>(columns.size());
        for (TabularColumn column : columns) {
            names.add(column.name());
        }
        return Collections.unmodifiableList(names);
    }

    public TabularColumn column(String name) {
        return columnsByName.get(name);
    }
}

