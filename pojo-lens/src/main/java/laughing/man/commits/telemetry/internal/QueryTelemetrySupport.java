package laughing.man.commits.telemetry.internal;

import laughing.man.commits.telemetry.QueryTelemetryEvent;
import laughing.man.commits.telemetry.QueryTelemetryListener;
import laughing.man.commits.telemetry.QueryTelemetryStage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal helpers for cheap conditional telemetry emission.
 */
public final class QueryTelemetrySupport {

    private QueryTelemetrySupport() {
    }

    public static long start(QueryTelemetryListener listener) {
        return listener == null ? 0L : System.nanoTime();
    }

    public static void emit(QueryTelemetryListener listener,
                            QueryTelemetryStage stage,
                            String queryType,
                            String source,
                            long startedNanos,
                            Integer rowCountBefore,
                            Integer rowCountAfter,
                            Map<String, Object> metadata) {
        if (listener == null) {
            return;
        }
        listener.onTelemetry(new QueryTelemetryEvent(
                stage,
                queryType,
                source,
                Math.max(0L, System.nanoTime() - startedNanos),
                rowCountBefore,
                rowCountAfter,
                metadata
        ));
    }

    public static Map<String, Object> metadata(Object... entries) {
        if (entries == null || entries.length == 0) {
            return Collections.emptyMap();
        }
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Telemetry metadata entries must be key/value pairs");
        }
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            metadata.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return metadata;
    }
}

