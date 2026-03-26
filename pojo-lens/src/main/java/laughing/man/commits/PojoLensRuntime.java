package laughing.man.commits;

import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.filter.FilterExecutionPlanCacheStore;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.SqlLikeTemplate;
import laughing.man.commits.sqllike.internal.cache.SqlLikeQueryCache;
import laughing.man.commits.telemetry.QueryTelemetryListener;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import laughing.man.commits.telemetry.internal.QueryTelemetrySupport;

import java.util.List;

/**
 * Instance-scoped runtime for DI and multi-tenant cache isolation.
 */
public final class PojoLensRuntime {

    private static final int DEV_SQL_LIKE_MAX_ENTRIES = 256;
    private static final int DEV_STATS_PLAN_MAX_ENTRIES = 512;
    private static final int PROD_SQL_LIKE_MAX_ENTRIES = 1024;
    private static final int PROD_STATS_PLAN_MAX_ENTRIES = 1024;
    private static final long PROD_EXPIRE_AFTER_WRITE_MILLIS = 300_000L;

    private final SqlLikeQueryCache sqlLikeCache;
    private final FilterExecutionPlanCacheStore statsPlanCache;
    private volatile boolean strictParameterTypes;
    private volatile boolean lintMode;
    private volatile QueryTelemetryListener telemetryListener;
    private volatile ComputedFieldRegistry computedFieldRegistry = ComputedFieldRegistry.empty();

    public PojoLensRuntime() {
        this(new SqlLikeQueryCache(), new FilterExecutionPlanCacheStore());
    }

    public PojoLensRuntime(SqlLikeQueryCache sqlLikeCache, FilterExecutionPlanCacheStore statsPlanCache) {
        if (sqlLikeCache == null) {
            throw new IllegalArgumentException("sqlLikeCache must not be null");
        }
        if (statsPlanCache == null) {
            throw new IllegalArgumentException("statsPlanCache must not be null");
        }
        sqlLikeCache.setExecutionPlanCacheStore(statsPlanCache);
        this.sqlLikeCache = sqlLikeCache;
        this.statsPlanCache = statsPlanCache;
    }

    public static PojoLensRuntime ofPreset(PojoLensRuntimePreset preset) {
        return new PojoLensRuntime().applyPreset(preset);
    }

    public QueryBuilder newQueryBuilder(List<?> pojos) {
        return PojoLensCore.newQueryBuilder(pojos, statsPlanCache)
                .computedFields(computedFieldRegistry)
                .telemetry(telemetryListener);
    }

    public SqlLikeQuery parse(String sqlLikeQuery) {
        long parseStarted = QueryTelemetrySupport.start(telemetryListener);
        SqlLikeQuery query = sqlLikeCache.parse(sqlLikeQuery)
                .strictParameterTypes(strictParameterTypes)
                .lintMode(lintMode)
                .computedFields(computedFieldRegistry)
                .telemetry(telemetryListener);
        QueryTelemetrySupport.emit(
                telemetryListener,
                QueryTelemetryStage.PARSE,
                "sql-like",
                query.source(),
                parseStarted,
                null,
                null,
                QueryTelemetrySupport.metadata(
                        "strictParameterTypes", strictParameterTypes,
                        "lintMode", lintMode
                )
        );
        return query;
    }

    public SqlLikeTemplate template(String sqlLikeQuery, String... expectedParams) {
        return SqlLikeTemplate.of(parse(sqlLikeQuery), expectedParams);
    }

    public SqlLikeQueryCache sqlLikeCache() {
        return sqlLikeCache;
    }

    public FilterExecutionPlanCacheStore statsPlanCache() {
        return statsPlanCache;
    }

    public void setStrictParameterTypes(boolean strictParameterTypes) {
        this.strictParameterTypes = strictParameterTypes;
    }

    public boolean isStrictParameterTypes() {
        return strictParameterTypes;
    }

    public void setLintMode(boolean lintMode) {
        this.lintMode = lintMode;
    }

    public boolean isLintMode() {
        return lintMode;
    }

    public void setTelemetryListener(QueryTelemetryListener telemetryListener) {
        this.telemetryListener = telemetryListener;
    }

    public QueryTelemetryListener getTelemetryListener() {
        return telemetryListener;
    }

    public void setComputedFieldRegistry(ComputedFieldRegistry computedFieldRegistry) {
        if (computedFieldRegistry == null) {
            throw new IllegalArgumentException("computedFieldRegistry must not be null");
        }
        this.computedFieldRegistry = computedFieldRegistry;
    }

    public ComputedFieldRegistry getComputedFieldRegistry() {
        return computedFieldRegistry;
    }

    public PojoLensRuntime applyPreset(PojoLensRuntimePreset preset) {
        if (preset == null) {
            throw new IllegalArgumentException("preset must not be null");
        }
        switch (preset) {
            case DEV -> applyDevPreset();
            case PROD -> applyProdPreset();
            case TEST -> applyTestPreset();
            default -> throw new IllegalArgumentException("Unsupported preset: " + preset);
        }
        clearCachesAndResetStats();
        return this;
    }

    private void applyDevPreset() {
        sqlLikeCache.setEnabled(true);
        sqlLikeCache.setStatsEnabled(true);
        sqlLikeCache.setMaxEntries(DEV_SQL_LIKE_MAX_ENTRIES);
        sqlLikeCache.setMaxWeight(0L);
        sqlLikeCache.setExpireAfterWriteMillis(0L);

        statsPlanCache.setEnabled(true);
        statsPlanCache.setStatsEnabled(true);
        statsPlanCache.setMaxEntries(DEV_STATS_PLAN_MAX_ENTRIES);
        statsPlanCache.setMaxWeight(0L);
        statsPlanCache.setExpireAfterWriteMillis(0L);

        strictParameterTypes = true;
        lintMode = true;
    }

    private void applyProdPreset() {
        sqlLikeCache.setEnabled(true);
        sqlLikeCache.setStatsEnabled(false);
        sqlLikeCache.setMaxEntries(PROD_SQL_LIKE_MAX_ENTRIES);
        sqlLikeCache.setMaxWeight(0L);
        sqlLikeCache.setExpireAfterWriteMillis(PROD_EXPIRE_AFTER_WRITE_MILLIS);

        statsPlanCache.setEnabled(true);
        statsPlanCache.setStatsEnabled(false);
        statsPlanCache.setMaxEntries(PROD_STATS_PLAN_MAX_ENTRIES);
        statsPlanCache.setMaxWeight(0L);
        statsPlanCache.setExpireAfterWriteMillis(PROD_EXPIRE_AFTER_WRITE_MILLIS);

        strictParameterTypes = false;
        lintMode = false;
    }

    private void applyTestPreset() {
        sqlLikeCache.setEnabled(false);
        sqlLikeCache.setStatsEnabled(false);
        sqlLikeCache.setMaxEntries(DEV_SQL_LIKE_MAX_ENTRIES);
        sqlLikeCache.setMaxWeight(0L);
        sqlLikeCache.setExpireAfterWriteMillis(0L);

        statsPlanCache.setEnabled(false);
        statsPlanCache.setStatsEnabled(false);
        statsPlanCache.setMaxEntries(DEV_STATS_PLAN_MAX_ENTRIES);
        statsPlanCache.setMaxWeight(0L);
        statsPlanCache.setExpireAfterWriteMillis(0L);

        strictParameterTypes = true;
        lintMode = true;
    }

    private void clearCachesAndResetStats() {
        sqlLikeCache.clear();
        sqlLikeCache.resetStats();
        statsPlanCache.clear();
        statsPlanCache.resetStats();
    }
}

