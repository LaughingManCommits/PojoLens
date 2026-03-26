package laughing.man.commits;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.snapshot.SnapshotComparison;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikeCursor;
import laughing.man.commits.sqllike.SqlLikeQuery;

/**
 * Entry point for builder-based and SQL-like PojoLens APIs.
 */
public final class PojoLens {

    private PojoLens() {
    }

    public static final String SDF = EngineDefaults.SDF;
    public static final String EMPTY_GROUPING = EngineDefaults.EMPTY_GROUPING;

    public static PojoLensRuntime newRuntime() {
        return new PojoLensRuntime();
    }

    public static PojoLensRuntime newRuntime(PojoLensRuntimePreset preset) {
        return PojoLensRuntime.ofPreset(preset);
    }

    /**
     * Creates a keyset cursor builder for SQL-like keyset pagination.
     *
     * @return keyset cursor builder
     */
    public static SqlLikeCursor.Builder newKeysetCursorBuilder() {
        return SqlLikeCursor.builder();
    }

    /**
     * Decodes a previously issued keyset cursor token.
     *
     * @param token keyset cursor token
     * @return decoded keyset cursor
     */
    public static SqlLikeCursor parseKeysetCursor(String token) {
        return SqlLikeCursor.fromToken(token);
    }

    public static DatasetBundle bundle(List<?> primaryRows) {
        return DatasetBundle.of(primaryRows);
    }

    public static DatasetBundle bundle(List<?> primaryRows, Map<String, List<?>> joinSources) {
        return DatasetBundle.of(primaryRows, joinSources);
    }

    public static DatasetBundle bundle(List<?> primaryRows, JoinBindings joinBindings) {
        return DatasetBundle.of(primaryRows, joinBindings);
    }

    public static <T> SnapshotComparison.Builder<T> compareSnapshots(List<T> currentRows, List<T> previousRows) {
        return SnapshotComparison.builder(currentRows, previousRows);
    }

    public static <T> ReportDefinition<T> report(SqlLikeQuery query, Class<T> projectionClass) {
        return ReportDefinition.sql(query, projectionClass);
    }

    public static <T> ReportDefinition<T> report(SqlLikeQuery query, Class<T> projectionClass, ChartSpec chartSpec) {
        return ReportDefinition.sql(query, projectionClass, chartSpec);
    }

    public static <T> ReportDefinition<T> report(Class<T> projectionClass, Consumer<QueryBuilder> configurer) {
        return ReportDefinition.fluent(projectionClass, configurer);
    }

    public static <T> ReportDefinition<T> report(Class<T> projectionClass,
                                                 Consumer<QueryBuilder> configurer,
                                                 ChartSpec chartSpec) {
        return ReportDefinition.fluent(projectionClass, configurer, chartSpec);
    }

}
