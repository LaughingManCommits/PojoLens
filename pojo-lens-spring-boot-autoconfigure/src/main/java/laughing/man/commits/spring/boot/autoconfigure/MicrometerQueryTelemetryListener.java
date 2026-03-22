package laughing.man.commits.spring.boot.autoconfigure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import laughing.man.commits.telemetry.QueryTelemetryEvent;
import laughing.man.commits.telemetry.QueryTelemetryListener;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer bridge for PojoLens telemetry with low-cardinality tags.
 */
final class MicrometerQueryTelemetryListener implements QueryTelemetryListener {

    private static final String EVENTS_METRIC = "pojolens.query.events";
    private static final String DURATION_METRIC = "pojolens.query.duration";

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<MetricKey, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<MetricKey, Timer> timers = new ConcurrentHashMap<>();

    MicrometerQueryTelemetryListener(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @Override
    public void onTelemetry(QueryTelemetryEvent event) {
        MetricKey key = MetricKey.from(event);
        counter(key).increment();
        timer(key).record(Math.max(0L, event.durationNanos()), TimeUnit.NANOSECONDS);
    }

    private Counter counter(MetricKey key) {
        return counters.computeIfAbsent(key, this::newCounter);
    }

    private Timer timer(MetricKey key) {
        return timers.computeIfAbsent(key, this::newTimer);
    }

    private Counter newCounter(MetricKey key) {
        return Counter.builder(EVENTS_METRIC)
                .description("Total number of PojoLens telemetry events")
                .tag("stage", key.stage())
                .tag("query_type", key.queryType())
                .register(meterRegistry);
    }

    private Timer newTimer(MetricKey key) {
        return Timer.builder(DURATION_METRIC)
                .description("PojoLens stage execution duration")
                .tag("stage", key.stage())
                .tag("query_type", key.queryType())
                .register(meterRegistry);
    }

    private record MetricKey(String stage, String queryType) {
        static MetricKey from(QueryTelemetryEvent event) {
            String stage = event.stage().name().toLowerCase(Locale.ROOT);
            String queryType = normalizeQueryType(event.queryType());
            return new MetricKey(stage, queryType);
        }

        private static String normalizeQueryType(String queryType) {
            if (queryType == null || queryType.isBlank()) {
                return "unknown";
            }
            return queryType.toLowerCase(Locale.ROOT);
        }
    }
}
