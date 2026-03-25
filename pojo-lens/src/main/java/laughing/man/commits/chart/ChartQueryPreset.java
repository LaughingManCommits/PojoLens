package laughing.man.commits.chart;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.table.TabularSchema;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Specialized chart-first reusable wrapper built from a SQL-like preset query.
 *
 * <p>Use {@link ReportDefinition} when the same query should be carried as a
 * more general reusable row/chart contract rather than a chart-first preset.
 */
public final class ChartQueryPreset<T> {

    private final SqlLikeQuery query;
    private final Class<T> projectionClass;
    private final ChartSpec chartSpec;

    ChartQueryPreset(SqlLikeQuery query, Class<T> projectionClass, ChartSpec chartSpec) {
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.projectionClass = Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        this.chartSpec = Objects.requireNonNull(chartSpec, "chartSpec must not be null");
    }

    public String source() {
        return query.source();
    }

    public SqlLikeQuery query() {
        return query;
    }

    public Class<T> projectionClass() {
        return projectionClass;
    }

    public ChartSpec chartSpec() {
        return chartSpec;
    }

    public TabularSchema schema() {
        return query.schema(projectionClass);
    }

    /**
     * Promotes this chart-first preset to the general reusable report contract
     * while preserving the configured chart specification.
     */
    public ReportDefinition<T> reportDefinition() {
        return ReportDefinition.sql(query, projectionClass, chartSpec);
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

    public ChartData chart(List<?> sourceRows) {
        return chart(sourceRows, Collections.emptyMap());
    }

    public ChartData chart(List<?> sourceRows, Map<String, List<?>> joinSources) {
        return query.chart(sourceRows, joinSources, projectionClass, chartSpec);
    }

    public ChartData chart(List<?> sourceRows, JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return query.chart(sourceRows, joinBindings, projectionClass, chartSpec);
    }

    public ChartData chart(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return query.chart(datasetBundle, projectionClass, chartSpec);
    }
}

