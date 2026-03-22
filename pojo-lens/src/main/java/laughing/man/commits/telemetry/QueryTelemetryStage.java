package laughing.man.commits.telemetry;

/**
 * High-level execution stages exposed through telemetry hooks.
 */
public enum QueryTelemetryStage {
    PARSE,
    BIND,
    FILTER,
    AGGREGATE,
    ORDER,
    CHART
}

