package laughing.man.commits.chart;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.chartjs.ChartJsPayload;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.table.TabularSchema;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

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
    private final ReportDefinition<T> reportDefinition;

    ChartQueryPreset(SqlLikeQuery query, Class<T> projectionClass, ChartSpec chartSpec) {
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.projectionClass = Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        this.chartSpec = Objects.requireNonNull(chartSpec, "chartSpec must not be null");
        this.reportDefinition = ReportDefinition.sql(this.query, this.projectionClass, this.chartSpec);
    }

    public String source() {
        return reportDefinition.source();
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

    public ChartQueryPreset<T> withChartSpec(ChartSpec spec) {
        return new ChartQueryPreset<>(
                query,
                projectionClass,
                Objects.requireNonNull(spec, "chartSpec must not be null")
        );
    }

    public ChartQueryPreset<T> mapChartSpec(UnaryOperator<ChartSpec> updater) {
        Objects.requireNonNull(updater, "updater must not be null");
        return withChartSpec(Objects.requireNonNull(
                updater.apply(chartSpec),
                "updated chartSpec must not be null"
        ));
    }

    public TabularSchema schema() {
        return reportDefinition.schema();
    }

    /**
     * Promotes this chart-first preset to the general reusable report contract
     * while preserving the configured chart specification.
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

    public ChartData chart(List<?> sourceRows) {
        Objects.requireNonNull(sourceRows, "pojos must not be null");
        return reportDefinition.chart(sourceRows);
    }

    public ChartData chart(List<?> sourceRows, JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        Objects.requireNonNull(sourceRows, "pojos must not be null");
        return reportDefinition.chart(sourceRows, joinBindings);
    }

    public ChartData chart(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return reportDefinition.chart(datasetBundle);
    }

    public ChartJsPayload chartJs(List<?> sourceRows) {
        Objects.requireNonNull(sourceRows, "pojos must not be null");
        return reportDefinition.chartJs(sourceRows);
    }

    public ChartJsPayload chartJs(List<?> sourceRows, JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        Objects.requireNonNull(sourceRows, "pojos must not be null");
        return reportDefinition.chartJs(sourceRows, joinBindings);
    }

    public ChartJsPayload chartJs(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return reportDefinition.chartJs(datasetBundle);
    }
}

