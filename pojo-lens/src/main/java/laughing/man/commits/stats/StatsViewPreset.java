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
 * Specialized table-first reusable wrapper built from a SQL-like stats query.
 *
 * <p>Use {@link ReportDefinition} when the reusable contract should be the row
 * query itself instead of the table/totals workflow.
 */
public final class StatsViewPreset<T> {

    private final SqlLikeQuery query;
    private final SqlLikeQuery totalsQuery;
    private final Class<T> projectionClass;

    StatsViewPreset(SqlLikeQuery query, SqlLikeQuery totalsQuery, Class<T> projectionClass) {
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.totalsQuery = totalsQuery;
        this.projectionClass = Objects.requireNonNull(projectionClass, "projectionClass must not be null");
    }

    public String source() {
        return query.source();
    }

    public SqlLikeQuery query() {
        return query;
    }

    public boolean hasTotals() {
        return totalsQuery != null;
    }

    public Class<T> projectionClass() {
        return projectionClass;
    }

    public TabularSchema schema() {
        return query.schema(projectionClass);
    }

    /**
     * Exports the reusable row query as the general report contract.
     * Totals remain part of the stats-preset/table workflow and are not carried
     * into the returned report definition.
     */
    public ReportDefinition<T> reportDefinition() {
        return ReportDefinition.sql(query, projectionClass);
    }

    public List<T> rows(List<?> sourceRows) {
        return rows(sourceRows, Collections.emptyMap());
    }

    public List<T> rows(List<?> sourceRows, Map<String, List<?>> joinSources) {
        return query.filter(sourceRows, joinSources, projectionClass);
    }

    public List<T> rows(List<?> sourceRows, JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return query.filter(sourceRows, joinBindings, projectionClass);
    }

    public List<T> rows(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return query.filter(datasetBundle, projectionClass);
    }

    public Map<String, Object> totals(List<?> sourceRows) {
        return totals(sourceRows, Collections.emptyMap());
    }

    public Map<String, Object> totals(List<?> sourceRows, Map<String, List<?>> joinSources) {
        if (totalsQuery == null) {
            return Collections.emptyMap();
        }
        List<T> totalRows = totalsQuery.filter(sourceRows, joinSources, projectionClass);
        if (totalRows.isEmpty()) {
            return Collections.emptyMap();
        }
        return TabularRows.firstRowAsMap(totalRows, totalsQuery.schema(projectionClass));
    }

    public Map<String, Object> totals(List<?> sourceRows, JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return totals(sourceRows, joinBindings.asMap());
    }

    public Map<String, Object> totals(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return totals(datasetBundle.primaryRows(), datasetBundle.joinSources());
    }

    public StatsTable<T> table(List<?> sourceRows) {
        return table(sourceRows, Collections.emptyMap());
    }

    public StatsTable<T> table(List<?> sourceRows, Map<String, List<?>> joinSources) {
        return StatsTable.of(rows(sourceRows, joinSources), totals(sourceRows, joinSources), schema());
    }

    public StatsTable<T> table(List<?> sourceRows, JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return table(sourceRows, joinBindings.asMap());
    }

    public StatsTable<T> table(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return table(datasetBundle.primaryRows(), datasetBundle.joinSources());
    }

    public StatsTablePayload tablePayload(List<?> sourceRows) {
        return table(sourceRows).payload();
    }

    public StatsTablePayload tablePayload(List<?> sourceRows, Map<String, List<?>> joinSources) {
        return table(sourceRows, joinSources).payload();
    }

    public StatsTablePayload tablePayload(List<?> sourceRows, JoinBindings joinBindings) {
        return table(sourceRows, joinBindings).payload();
    }

    public StatsTablePayload tablePayload(DatasetBundle datasetBundle) {
        return table(datasetBundle).payload();
    }
}
