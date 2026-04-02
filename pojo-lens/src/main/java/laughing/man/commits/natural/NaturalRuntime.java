package laughing.man.commits.natural;

import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import laughing.man.commits.telemetry.internal.QueryTelemetrySupport;

import java.util.Objects;

/**
 * Runtime-scoped entry point for controlled plain-English queries.
 */
public final class NaturalRuntime {

    private final PojoLensRuntime runtime;

    public NaturalRuntime(PojoLensRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
    }

    public NaturalQuery parse(String source) {
        long parseStarted = QueryTelemetrySupport.start(runtime.getTelemetryListener());
        NaturalQuery query = NaturalQuery.of(source)
                .strictParameterTypes(runtime.isStrictParameterTypes())
                .lintMode(runtime.isLintMode())
                .computedFields(runtime.getComputedFieldRegistry())
                .vocabulary(runtime.getNaturalVocabulary())
                .executionPlanCache(runtime.statsPlanCache())
                .telemetry(runtime.getTelemetryListener());
        QueryTelemetrySupport.emit(
                runtime.getTelemetryListener(),
                QueryTelemetryStage.PARSE,
                "natural",
                query.source(),
                parseStarted,
                null,
                null,
                QueryTelemetrySupport.metadata(
                        "strictParameterTypes", runtime.isStrictParameterTypes(),
                        "lintMode", runtime.isLintMode()
                )
        );
        return query;
    }
}
