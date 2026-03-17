package laughing.man.commits;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.snapshot.SnapshotComparison;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.SqlLikeTemplate;

/**
 * Entry point for builder-based and SQL-like PojoLens APIs.
 */
public class PojoLens {

    private PojoLens() {
    }

    public static final String SDF = EngineDefaults.SDF;
    public static final String EMPTY_GROUPING = EngineDefaults.EMPTY_GROUPING;

    public static QueryBuilder newQueryBuilder(List<?> pojos) {
        return PojoLensCore.newQueryBuilder(pojos);
    }

    public static PojoLensRuntime newRuntime() {
        return new PojoLensRuntime();
    }

    public static PojoLensRuntime newRuntime(PojoLensRuntimePreset preset) {
        return PojoLensRuntime.ofPreset(preset);
    }

    /**
     * Parses SQL-like query text into an executable query contract.
     *
     * @param sqlLikeQuery SQL-like query text
     * @return parsed SQL-like query contract
     */
    public static SqlLikeQuery parse(String sqlLikeQuery) {
        return PojoLensSql.parse(sqlLikeQuery);
    }

    /**
     * Creates a reusable SQL-like template with explicit parameter schema.
     *
     * @param sqlLikeQuery SQL-like query text
     * @param expectedParams expected parameter names
     * @return SQL-like template
     */
    public static SqlLikeTemplate template(String sqlLikeQuery, String... expectedParams) {
        return PojoLensSql.template(sqlLikeQuery, expectedParams);
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

    public static void setSqlLikeCacheEnabled(boolean enabled) {
        PojoLensSql.setSqlLikeCacheEnabled(enabled);
    }

    public static boolean isSqlLikeCacheEnabled() {
        return PojoLensSql.isSqlLikeCacheEnabled();
    }

    public static void setSqlLikeCacheMaxEntries(int maxEntries) {
        PojoLensSql.setSqlLikeCacheMaxEntries(maxEntries);
    }

    public static int getSqlLikeCacheMaxEntries() {
        return PojoLensSql.getSqlLikeCacheMaxEntries();
    }

    public static void setSqlLikeCacheMaxWeight(long maxWeight) {
        PojoLensSql.setSqlLikeCacheMaxWeight(maxWeight);
    }

    public static long getSqlLikeCacheMaxWeight() {
        return PojoLensSql.getSqlLikeCacheMaxWeight();
    }

    public static void setSqlLikeCacheExpireAfterWriteMillis(long expireAfterWriteMillis) {
        PojoLensSql.setSqlLikeCacheExpireAfterWriteMillis(expireAfterWriteMillis);
    }

    public static long getSqlLikeCacheExpireAfterWriteMillis() {
        return PojoLensSql.getSqlLikeCacheExpireAfterWriteMillis();
    }

    public static void setSqlLikeCacheStatsEnabled(boolean statsEnabled) {
        PojoLensSql.setSqlLikeCacheStatsEnabled(statsEnabled);
    }

    public static boolean isSqlLikeCacheStatsEnabled() {
        return PojoLensSql.isSqlLikeCacheStatsEnabled();
    }

    public static void clearSqlLikeCache() {
        PojoLensSql.clearSqlLikeCache();
    }

    public static void resetSqlLikeCacheStats() {
        PojoLensSql.resetSqlLikeCacheStats();
    }

    public static long getSqlLikeCacheHits() {
        return PojoLensSql.getSqlLikeCacheHits();
    }

    public static long getSqlLikeCacheMisses() {
        return PojoLensSql.getSqlLikeCacheMisses();
    }

    public static int getSqlLikeCacheSize() {
        return PojoLensSql.getSqlLikeCacheSize();
    }

    public static long getSqlLikeCacheEvictions() {
        return PojoLensSql.getSqlLikeCacheEvictions();
    }

    public static Map<String, Object> getSqlLikeCacheSnapshot() {
        return PojoLensSql.getSqlLikeCacheSnapshot();
    }

    public static void setStatsPlanCacheEnabled(boolean enabled) {
        PojoLensCore.setStatsPlanCacheEnabled(enabled);
    }

    public static boolean isStatsPlanCacheEnabled() {
        return PojoLensCore.isStatsPlanCacheEnabled();
    }

    public static void setStatsPlanCacheMaxEntries(int maxEntries) {
        PojoLensCore.setStatsPlanCacheMaxEntries(maxEntries);
    }

    public static int getStatsPlanCacheMaxEntries() {
        return PojoLensCore.getStatsPlanCacheMaxEntries();
    }

    public static void setStatsPlanCacheMaxWeight(long maxWeight) {
        PojoLensCore.setStatsPlanCacheMaxWeight(maxWeight);
    }

    public static long getStatsPlanCacheMaxWeight() {
        return PojoLensCore.getStatsPlanCacheMaxWeight();
    }

    public static void setStatsPlanCacheExpireAfterWriteMillis(long expireAfterWriteMillis) {
        PojoLensCore.setStatsPlanCacheExpireAfterWriteMillis(expireAfterWriteMillis);
    }

    public static long getStatsPlanCacheExpireAfterWriteMillis() {
        return PojoLensCore.getStatsPlanCacheExpireAfterWriteMillis();
    }

    public static void setStatsPlanCacheStatsEnabled(boolean statsEnabled) {
        PojoLensCore.setStatsPlanCacheStatsEnabled(statsEnabled);
    }

    public static boolean isStatsPlanCacheStatsEnabled() {
        return PojoLensCore.isStatsPlanCacheStatsEnabled();
    }

    public static void clearStatsPlanCache() {
        PojoLensCore.clearStatsPlanCache();
    }

    public static void resetStatsPlanCacheStats() {
        PojoLensCore.resetStatsPlanCacheStats();
    }

    public static long getStatsPlanCacheHits() {
        return PojoLensCore.getStatsPlanCacheHits();
    }

    public static long getStatsPlanCacheMisses() {
        return PojoLensCore.getStatsPlanCacheMisses();
    }

    public static int getStatsPlanCacheSize() {
        return PojoLensCore.getStatsPlanCacheSize();
    }

    public static long getStatsPlanCacheEvictions() {
        return PojoLensCore.getStatsPlanCacheEvictions();
    }

    public static Map<String, Object> getStatsPlanCacheSnapshot() {
        return PojoLensCore.getStatsPlanCacheSnapshot();
    }

    /**
     * Maps rows to chart-data payload using the provided chart specification.
     *
     * @param rows input rows
     * @param spec chart mapping spec
     * @param <T> row type
     * @return chart payload
     */
    public static <T> ChartData toChartData(List<T> rows, ChartSpec spec) {
        return PojoLensChart.toChartData(rows, spec);
    }

}

