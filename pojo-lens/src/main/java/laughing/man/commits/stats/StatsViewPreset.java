package laughing.man.commits.stats;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.table.TabularRows;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Advanced table-first convenience wrapper built from a SQL-like stats query.
 *
 * <p>Prefer {@link ReportDefinition} for new reusable row/query workflows.
 * This type remains public when totals and table payload helpers are the
 * primary contract.
 */
public final class StatsViewPreset<T> {

    private final SqlLikeQuery query;
    private final SqlLikeQuery totalsQuery;
    private final Class<T> projectionClass;
    private final ReportDefinition<T> reportDefinition;

    StatsViewPreset(SqlLikeQuery query, SqlLikeQuery totalsQuery, Class<T> projectionClass) {
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.totalsQuery = totalsQuery;
        this.projectionClass = Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        this.reportDefinition = ReportDefinition.sql(this.query, this.projectionClass);
    }

    public String source() {
        return reportDefinition.source();
    }

    public SqlLikeQuery query() {
        return query.copy();
    }

    public boolean hasTotals() {
        return totalsQuery != null;
    }

    public Class<T> projectionClass() {
        return projectionClass;
    }

    public TabularSchema schema() {
        return reportDefinition.schema();
    }

    /**
     * Exports the reusable row query as the general report contract.
     * Totals remain part of the stats-preset/table workflow and are not carried
     * into the returned report definition.
     */
    public ReportDefinition<T> reportDefinition() {
        return reportDefinition;
    }

    public List<T> rows(List<?> sourceRows) {
        Objects.requireNonNull(sourceRows, "pojos must not be null");
        return reportDefinition.rows(sourceRows);
    }

    public List<T> rows(List<?> sourceRows, JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        Objects.requireNonNull(sourceRows, "pojos must not be null");
        return reportDefinition.rows(sourceRows, joinBindings);
    }

    public List<T> rows(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return reportDefinition.rows(datasetBundle);
    }

    public Map<String, Object> totals(List<?> sourceRows) {
        if (totalsQuery == null) {
            return Collections.emptyMap();
        }
        List<T> totalRows = totalsQuery.filter(sourceRows, projectionClass);
        if (totalRows.isEmpty()) {
            return Collections.emptyMap();
        }
        return TabularRows.firstRowAsMap(totalRows, totalsQuery.schema(projectionClass));
    }

    public Map<String, Object> totals(List<?> sourceRows, JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        if (totalsQuery == null) {
            return Collections.emptyMap();
        }
        List<T> totalRows = totalsQuery.filter(sourceRows, joinBindings, projectionClass);
        if (totalRows.isEmpty()) {
            return Collections.emptyMap();
        }
        return TabularRows.firstRowAsMap(totalRows, totalsQuery.schema(projectionClass));
    }

    public Map<String, Object> totals(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return totals(datasetBundle.primaryRows(), datasetBundle.joinBindings());
    }

    public StatsTable<T> table(List<?> sourceRows) {
        return StatsTable.of(rows(sourceRows), totals(sourceRows), schema());
    }

    public StatsTable<T> table(List<?> sourceRows, JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return StatsTable.of(rows(sourceRows, joinBindings), totals(sourceRows, joinBindings), schema());
    }

    public StatsTable<T> table(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return table(datasetBundle.primaryRows(), datasetBundle.joinBindings());
    }

    public StatsTablePayload tablePayload(List<?> sourceRows) {
        return table(sourceRows).payload();
    }

    public StatsTablePayload tablePayload(List<?> sourceRows, JoinBindings joinBindings) {
        return table(sourceRows, joinBindings).payload();
    }

    public StatsTablePayload tablePayload(DatasetBundle datasetBundle) {
        return table(datasetBundle).payload();
    }
}
