package laughing.man.commits.report;

import laughing.man.commits.PojoLensCore;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartResultMapper;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chartjs.ChartJsAdapter;
import laughing.man.commits.chartjs.ChartJsPayload;
import laughing.man.commits.natural.NaturalQuery;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikeQuery;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * General reusable row-query contract for repeated execution over different
 * in-memory dataset snapshots.
 *
 * <p>SQL-like, fluent, and natural queries can all be promoted into this
 * abstraction. Specialized workflow helpers such as {@code ChartQueryPreset}
 * and {@code StatsViewPreset} can bridge into it when a more general reusable
 * contract is needed.
 *
 * @param <T> projection row type
 */
public final class ReportDefinition<T> {

    private final String source;
    private final Class<T> projectionClass;
    private final ChartSpec chartSpec;
    private final TabularSchema schema;
    private final ReportExecutor<T> executor;
    private final boolean supportsJoinSources;

    private ReportDefinition(String source,
                             Class<T> projectionClass,
                             ChartSpec chartSpec,
                             TabularSchema schema,
                             ReportExecutor<T> executor,
                             boolean supportsJoinSources) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.projectionClass = Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        this.chartSpec = chartSpec;
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.supportsJoinSources = supportsJoinSources;
    }

    public static <T> ReportDefinition<T> sql(SqlLikeQuery query, Class<T> projectionClass) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        return new ReportDefinition<>(
                query.source(),
                projectionClass,
                null,
                query.schema(projectionClass),
                (sourceRows, joinBindings) -> query.filter(sourceRows, joinBindings, projectionClass),
                true
        );
    }

    public static <T> ReportDefinition<T> sql(SqlLikeQuery query, Class<T> projectionClass, ChartSpec chartSpec) {
        return sql(query, projectionClass).withChartSpec(chartSpec);
    }

    public static <T> ReportDefinition<T> natural(NaturalQuery query, Class<T> projectionClass) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        return new ReportDefinition<>(
                query.source(),
                projectionClass,
                null,
                query.schema(projectionClass),
                (sourceRows, joinBindings) -> query.filter(sourceRows, joinBindings, projectionClass),
                true
        );
    }

    public static <T> ReportDefinition<T> natural(NaturalQuery query, Class<T> projectionClass, ChartSpec chartSpec) {
        return natural(query, projectionClass).withChartSpec(chartSpec);
    }

    public static <T> ReportDefinition<T> fluent(Class<T> projectionClass, Consumer<QueryBuilder> configurer) {
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        Objects.requireNonNull(configurer, "configurer must not be null");
        return new ReportDefinition<>(
                "fluent",
                projectionClass,
                null,
                deriveFluentSchema(projectionClass, configurer),
                (sourceRows, joinBindings) -> {
                    if (!joinBindings.isEmpty()) {
                        throw new IllegalArgumentException(
                                "joinBindings are not supported for fluent report definitions; "
                                        + "capture joined rows in the fluent configurer or use a SQL-like report definition"
                        );
                    }
                    QueryBuilder builder = PojoLensCore.newQueryBuilder(sourceRows);
                    configurer.accept(builder);
                    return builder.initFilter().filter(projectionClass);
                },
                false
        );
    }

    public static <T> ReportDefinition<T> fluent(Class<T> projectionClass,
                                                 Consumer<QueryBuilder> configurer,
                                                 ChartSpec chartSpec) {
        return fluent(projectionClass, configurer).withChartSpec(chartSpec);
    }

    public String source() {
        return source;
    }

    public Class<T> projectionClass() {
        return projectionClass;
    }

    public ChartSpec chartSpec() {
        return chartSpec;
    }

    public TabularSchema schema() {
        return schema;
    }

    public boolean supportsJoinSources() {
        return supportsJoinSources;
    }

    public ReportDefinition<T> withChartSpec(ChartSpec spec) {
        return new ReportDefinition<>(
                source,
                projectionClass,
                Objects.requireNonNull(spec, "chartSpec must not be null"),
                schema,
                executor,
                supportsJoinSources
        );
    }

    public ReportDefinition<T> mapChartSpec(UnaryOperator<ChartSpec> updater) {
        Objects.requireNonNull(updater, "updater must not be null");
        return withChartSpec(Objects.requireNonNull(
                updater.apply(requireChartSpec()),
                "updated chartSpec must not be null"
        ));
    }

    public ReportDefinition<T> withSchema(TabularSchema value) {
        return new ReportDefinition<>(
                source,
                projectionClass,
                chartSpec,
                Objects.requireNonNull(value, "schema must not be null"),
                executor,
                supportsJoinSources
        );
    }

    public List<T> rows(List<?> sourceRows) {
        Objects.requireNonNull(sourceRows, "sourceRows must not be null");
        return executor.rows(sourceRows, JoinBindings.empty());
    }

    public List<T> rows(List<?> sourceRows, JoinBindings joinBindings) {
        Objects.requireNonNull(sourceRows, "sourceRows must not be null");
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return executor.rows(sourceRows, joinBindings);
    }

    public List<T> rows(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return rows(datasetBundle.primaryRows(), datasetBundle.joinBindings());
    }

    public ChartData chart(List<?> sourceRows) {
        return ChartResultMapper.toChartData(rows(sourceRows), requireChartSpec());
    }

    public ChartData chart(List<?> sourceRows, JoinBindings joinBindings) {
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return ChartResultMapper.toChartData(rows(sourceRows, joinBindings), requireChartSpec());
    }

    public ChartData chart(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return chart(datasetBundle.primaryRows(), datasetBundle.joinBindings());
    }

    public ChartJsPayload chartJs(List<?> sourceRows) {
        return ChartJsAdapter.toPayload(chart(sourceRows));
    }

    public ChartJsPayload chartJs(List<?> sourceRows, JoinBindings joinBindings) {
        return ChartJsAdapter.toPayload(chart(sourceRows, joinBindings));
    }

    public ChartJsPayload chartJs(DatasetBundle datasetBundle) {
        return ChartJsAdapter.toPayload(chart(datasetBundle));
    }

    private ChartSpec requireChartSpec() {
        if (chartSpec == null) {
            throw new IllegalStateException("Report definition has no chartSpec configured");
        }
        return chartSpec;
    }

    @FunctionalInterface
    private interface ReportExecutor<T> {
        List<T> rows(List<?> sourceRows, JoinBindings joinBindings);
    }

    private static <T> TabularSchema deriveFluentSchema(Class<T> projectionClass, Consumer<QueryBuilder> configurer) {
        QueryBuilder schemaBuilder = PojoLensCore.newQueryBuilder(Collections.emptyList());
        configurer.accept(schemaBuilder);
        return schemaBuilder.schema(projectionClass);
    }
}

