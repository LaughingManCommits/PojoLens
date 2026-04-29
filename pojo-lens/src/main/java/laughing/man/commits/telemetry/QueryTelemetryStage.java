package laughing.man.commits.telemetry;

/**
 * High-level execution stages exposed through telemetry hooks.
 */
public enum QueryTelemetryStage {
    PARSE,
    BIND,
    PUSHDOWN,
    FILTER,
    AGGREGATE,
    ORDER,
    CHART,
    GUARD_REJECTED
}

