package laughing.man.commits.sqllike;

import java.util.Objects;

/**
 * Thrown when a {@link QueryExecutionGuard} blocks query execution.
 *
 * <p>The guard may block before execution (input-row or complexity budget
 * exceeded) or after execution (output-row or duration budget exceeded).
 * In both cases this exception carries the full {@link QueryGuardOutcome}
 * for audit purposes.
 *
 * <p>Callers should treat this as an expected operational signal, not an
 * internal error:
 * <pre>{@code
 * try {
 *     List<Row> rows = query.executionGuard(guard).filter(snapshot, Row.class);
 * } catch (QueryExecutionGuardException ex) {
 *     QueryGuardOutcome outcome = ex.outcome();
 *     log.warn("Query blocked [{}]: {}", outcome.blockCode(), outcome.blockReason());
 *     // do not expose raw reason to end users without sanitization
 * }
 * }</pre>
 */
public final class QueryExecutionGuardException extends RuntimeException {

    private final QueryGuardOutcome outcome;

    QueryExecutionGuardException(QueryGuardOutcome outcome) {
        super(Objects.requireNonNull(outcome, "outcome must not be null").blockReason());
        this.outcome = outcome;
    }

    /**
     * Returns the full guard outcome including block code, reason, and
     * audit metadata.
     *
     * @return guard outcome
     */
    public QueryGuardOutcome outcome() {
        return outcome;
    }
}
