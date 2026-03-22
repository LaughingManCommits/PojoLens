package laughing.man.commits.spring.boot.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.PojoLensRuntimePreset;
import laughing.man.commits.telemetry.QueryTelemetryEvent;
import laughing.man.commits.telemetry.QueryTelemetryListener;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PojoLensSpringBootAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PojoLensSpringBootAutoConfiguration.class));

    @Test
    void shouldAutoConfigureRuntimeWithProdDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PojoLensRuntime.class);
            PojoLensRuntime runtime = context.getBean(PojoLensRuntime.class);

            assertThat(runtime.isStrictParameterTypes()).isFalse();
            assertThat(runtime.isLintMode()).isFalse();
            assertThat(runtime.sqlLikeCache().isEnabled()).isTrue();
            assertThat(runtime.statsPlanCache().isEnabled()).isTrue();
        });
    }

    @Test
    void shouldApplyRuntimePropertyOverrides() {
        contextRunner
                .withPropertyValues(
                        "pojo-lens.preset=DEV",
                        "pojo-lens.strict-parameter-types=false",
                        "pojo-lens.lint-mode=false",
                        "pojo-lens.sql-like-cache.enabled=false",
                        "pojo-lens.sql-like-cache.stats-enabled=false",
                        "pojo-lens.sql-like-cache.max-entries=64",
                        "pojo-lens.sql-like-cache.max-weight=4096",
                        "pojo-lens.sql-like-cache.expire-after-write-millis=1234",
                        "pojo-lens.stats-plan-cache.enabled=false",
                        "pojo-lens.stats-plan-cache.stats-enabled=false",
                        "pojo-lens.stats-plan-cache.max-entries=32",
                        "pojo-lens.stats-plan-cache.max-weight=2048",
                        "pojo-lens.stats-plan-cache.expire-after-write-millis=4321")
                .run(context -> {
                    PojoLensRuntime runtime = context.getBean(PojoLensRuntime.class);

                    assertThat(runtime.isStrictParameterTypes()).isFalse();
                    assertThat(runtime.isLintMode()).isFalse();

                    assertThat(runtime.sqlLikeCache().isEnabled()).isFalse();
                    assertThat(runtime.sqlLikeCache().isStatsEnabled()).isFalse();
                    assertThat(runtime.sqlLikeCache().getMaxEntries()).isEqualTo(64);
                    assertThat(runtime.sqlLikeCache().getMaxWeight()).isEqualTo(4096L);
                    assertThat(runtime.sqlLikeCache().getExpireAfterWriteMillis()).isEqualTo(1234L);

                    assertThat(runtime.statsPlanCache().isEnabled()).isFalse();
                    assertThat(runtime.statsPlanCache().isStatsEnabled()).isFalse();
                    assertThat(runtime.statsPlanCache().maxEntries()).isEqualTo(32);
                    assertThat(runtime.statsPlanCache().maxWeight()).isEqualTo(2048L);
                    assertThat(runtime.statsPlanCache().expireAfterWriteMillis()).isEqualTo(4321L);
                });
    }

    @Test
    void shouldBackOffWhenRuntimeBeanAlreadyProvided() {
        contextRunner
                .withUserConfiguration(CustomRuntimeConfiguration.class)
                .run(context -> {
                    PojoLensRuntime runtime = context.getBean(PojoLensRuntime.class);
                    assertThat(runtime.isStrictParameterTypes()).isTrue();
                    assertThat(runtime.isLintMode()).isTrue();
                });
    }

    @Test
    void shouldWireMicrometerTelemetryListenerWhenMeterRegistryPresent() {
        contextRunner
                .withUserConfiguration(MeterRegistryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(QueryTelemetryListener.class);

                    QueryTelemetryListener listener = context.getBean(QueryTelemetryListener.class);
                    PojoLensRuntime runtime = context.getBean(PojoLensRuntime.class);
                    MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);

                    assertThat(runtime.getTelemetryListener()).isSameAs(listener);

                    listener.onTelemetry(new QueryTelemetryEvent(
                            QueryTelemetryStage.PARSE,
                            "sql-like",
                            "select * where id = :id",
                            2_500L,
                            null,
                            null,
                            Map.of("sample", "value")
                    ));

                    assertThat(meterRegistry.get("pojolens.query.events")
                            .tags("stage", "parse", "query_type", "sql-like")
                            .counter()
                            .count())
                            .isEqualTo(1.0d);
                });
    }

    @Test
    void shouldDisableMicrometerTelemetryListenerWhenConfigured() {
        contextRunner
                .withUserConfiguration(MeterRegistryConfiguration.class)
                .withPropertyValues("pojo-lens.telemetry.micrometer.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(QueryTelemetryListener.class);
                    assertThat(context.getBean(PojoLensRuntime.class).getTelemetryListener()).isNull();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRuntimeConfiguration {

        @Bean
        PojoLensRuntime pojoLensRuntime() {
            return new PojoLensRuntime().applyPreset(PojoLensRuntimePreset.TEST);
        }
    }
}
