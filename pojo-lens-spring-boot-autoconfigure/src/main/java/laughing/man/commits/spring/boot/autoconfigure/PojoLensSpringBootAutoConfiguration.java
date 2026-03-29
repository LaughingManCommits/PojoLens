package laughing.man.commits.spring.boot.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.PojoLensRuntimePreset;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.filter.FilterExecutionPlanCacheStore;
import laughing.man.commits.sqllike.internal.cache.SqlLikeQueryCache;
import laughing.man.commits.telemetry.QueryTelemetryListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for PojoLens runtime and optional telemetry wiring.
 */
@AutoConfiguration
@ConditionalOnClass(PojoLensRuntime.class)
@EnableConfigurationProperties(PojoLensProperties.class)
public class PojoLensSpringBootAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PojoLensRuntime pojoLensRuntime(PojoLensProperties properties,
                                           ObjectProvider<QueryTelemetryListener> telemetryListenerProvider,
                                           ObjectProvider<ComputedFieldRegistry> computedFieldRegistryProvider) {
        PojoLensRuntimePreset preset = properties.getPreset() == null
                ? PojoLensRuntimePreset.PROD
                : properties.getPreset();

        PojoLensRuntime runtime = PojoLensRuntime.ofPreset(preset);

        if (properties.getStrictParameterTypes() != null) {
            runtime.setStrictParameterTypes(properties.getStrictParameterTypes());
        }
        if (properties.getLintMode() != null) {
            runtime.setLintMode(properties.getLintMode());
        }

        applySqlLikeCache(runtime.sqlLikeCache(), properties.getSqlLikeCache());
        applyStatsPlanCache(runtime.statsPlanCache(), properties.getStatsPlanCache());

        QueryTelemetryListener telemetryListener = telemetryListenerProvider.getIfAvailable();
        if (telemetryListener != null) {
            runtime.setTelemetryListener(telemetryListener);
        }

        ComputedFieldRegistry computedFieldRegistry = computedFieldRegistryProvider.getIfAvailable();
        if (computedFieldRegistry != null) {
            runtime.setComputedFieldRegistry(computedFieldRegistry);
        }

        return runtime;
    }

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(QueryTelemetryListener.class)
    @ConditionalOnProperty(prefix = "pojo-lens.telemetry.micrometer",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public QueryTelemetryListener pojoLensMicrometerTelemetryListener(MeterRegistry meterRegistry) {
        return new MicrometerQueryTelemetryListener(meterRegistry);
    }

    private static void applySqlLikeCache(SqlLikeQueryCache cache, PojoLensProperties.CacheProperties properties) {
        if (properties.getEnabled() != null) {
            cache.setEnabled(properties.getEnabled());
        }
        if (properties.getStatsEnabled() != null) {
            cache.setStatsEnabled(properties.getStatsEnabled());
        }
        if (properties.getMaxEntries() != null) {
            cache.setMaxEntries(properties.getMaxEntries());
        }
        if (properties.getMaxWeight() != null) {
            cache.setMaxWeight(properties.getMaxWeight());
        }
        if (properties.getExpireAfterWriteMillis() != null) {
            cache.setExpireAfterWriteMillis(properties.getExpireAfterWriteMillis());
        }
    }

    private static void applyStatsPlanCache(FilterExecutionPlanCacheStore cache,
                                            PojoLensProperties.CacheProperties properties) {
        if (properties.getEnabled() != null) {
            cache.setEnabled(properties.getEnabled());
        }
        if (properties.getStatsEnabled() != null) {
            cache.setStatsEnabled(properties.getStatsEnabled());
        }
        if (properties.getMaxEntries() != null) {
            cache.setMaxEntries(properties.getMaxEntries());
        }
        if (properties.getMaxWeight() != null) {
            cache.setMaxWeight(properties.getMaxWeight());
        }
        if (properties.getExpireAfterWriteMillis() != null) {
            cache.setExpireAfterWriteMillis(properties.getExpireAfterWriteMillis());
        }
    }
}
