package laughing.man.commits.telemetry;

/**
 * Listener for low-overhead query telemetry events.
 */
@FunctionalInterface
public interface QueryTelemetryListener {

    void onTelemetry(QueryTelemetryEvent event);
}

