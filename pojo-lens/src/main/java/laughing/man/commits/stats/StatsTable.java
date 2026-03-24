package laughing.man.commits.stats;

import laughing.man.commits.table.TabularSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable table payload for stats presets.
 *
 * @param <T> typed row projection
 */
public final class StatsTable<T> {

    private final List<T> rows;
    private final Map<String, Object> totals;
    private final TabularSchema schema;

    private StatsTable(List<T> rows, Map<String, Object> totals, TabularSchema schema) {
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(totals, "totals must not be null");
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.totals = Collections.unmodifiableMap(new LinkedHashMap<>(totals));
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
    }

    public static <T> StatsTable<T> of(List<T> rows, Map<String, Object> totals, TabularSchema schema) {
        return new StatsTable<>(rows, totals, schema);
    }

    public List<T> rows() {
        return rows;
    }

    public Map<String, Object> totals() {
        return totals;
    }

    public boolean hasTotals() {
        return !totals.isEmpty();
    }

    public TabularSchema schema() {
        return schema;
    }
}
