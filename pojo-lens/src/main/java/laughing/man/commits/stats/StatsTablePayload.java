package laughing.man.commits.stats;

import laughing.man.commits.table.TabularSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JSON-friendly payload for dashboard/table consumers that need schema, rows,
 * and totals together.
 */
public final class StatsTablePayload {

    private final TabularSchema schema;
    private final List<Map<String, Object>> rows;
    private final Map<String, Object> totals;

    private StatsTablePayload(TabularSchema schema,
                              List<Map<String, Object>> rows,
                              Map<String, Object> totals) {
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(totals, "totals must not be null");
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.totals = Collections.unmodifiableMap(new LinkedHashMap<>(totals));
    }

    public static StatsTablePayload of(TabularSchema schema,
                                       List<Map<String, Object>> rows,
                                       Map<String, Object> totals) {
        return new StatsTablePayload(schema, rows, totals);
    }

    public TabularSchema schema() {
        return schema;
    }

    public List<String> columns() {
        return schema.names();
    }

    public List<Map<String, Object>> rows() {
        return rows;
    }

    public Map<String, Object> totals() {
        return totals;
    }

    public boolean hasTotals() {
        return !totals.isEmpty();
    }
}
