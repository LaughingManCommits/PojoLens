package laughing.man.commits.telemetry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable telemetry event emitted for a query stage.
 */
public final class QueryTelemetryEvent {

    private final QueryTelemetryStage stage;
    private final String queryType;
    private final String source;
    private final long durationNanos;
    private final Integer rowCountBefore;
    private final Integer rowCountAfter;
    private final Map<String, Object> metadata;

    public QueryTelemetryEvent(QueryTelemetryStage stage,
                               String queryType,
                               String source,
                               long durationNanos,
                               Integer rowCountBefore,
                               Integer rowCountAfter,
                               Map<String, Object> metadata) {
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
        this.queryType = Objects.requireNonNull(queryType, "queryType must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.durationNanos = durationNanos;
        this.rowCountBefore = rowCountBefore;
        this.rowCountAfter = rowCountAfter;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(
                metadata == null ? Collections.emptyMap() : metadata
        ));
    }

    public QueryTelemetryStage stage() {
        return stage;
    }

    public String queryType() {
        return queryType;
    }

    public String source() {
        return source;
    }

    public long durationNanos() {
        return durationNanos;
    }

    public Integer rowCountBefore() {
        return rowCountBefore;
    }

    public Integer rowCountAfter() {
        return rowCountAfter;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }
}

