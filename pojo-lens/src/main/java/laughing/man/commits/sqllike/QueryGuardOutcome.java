package laughing.man.commits.sqllike;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, audit-friendly result produced by a {@link QueryExecutionGuard} check.
 *
 * <p>When {@link #blocked()} is true, the host application should log or emit
 * {@link #auditMetadata()} and surface the {@link #blockReason()} to
 * operations tooling. The query was not executed (pre-execution block) or its
 * result should not be consumed (post-execution block).
 *
 * <p>{@link #complexitySummary()} is populated for pre-execution checks and
 * may be {@code null} for post-execution row-count or duration checks.
 *
 * <p>For cancellation outcomes (block code {@code GUARD_CANCELLED}),
 * {@link #rowsReturnedBeforeAbort()} carries the exact number of rows that
 * were already returned to the caller before the query was aborted. This
 * provides deterministic aborted-query metadata.
 */
public final class QueryGuardOutcome {

    private final boolean allowed;
    private final String blockCode;
    private final String blockReason;
    private final QueryComplexitySummary complexitySummary;
    private final Integer rowsReturnedBeforeAbort;

    private QueryGuardOutcome(boolean allowed,
                              String blockCode,
                              String blockReason,
                              QueryComplexitySummary complexitySummary,
                              Integer rowsReturnedBeforeAbort) {
        this.allowed = allowed;
        this.blockCode = blockCode;
        this.blockReason = blockReason;
        this.complexitySummary = complexitySummary;
        this.rowsReturnedBeforeAbort = rowsReturnedBeforeAbort;
    }

    /**
     * Returns an allowed outcome carrying the pre-execution complexity summary.
     *
     * @param summary complexity summary; may be {@code null} for post-execution outcomes
     * @return allowed outcome
     */
    public static QueryGuardOutcome allowed(QueryComplexitySummary summary) {
        return new QueryGuardOutcome(true, null, null, summary, null);
    }

    /**
     * Returns a blocked outcome with a machine-readable code and human-readable reason.
     *
     * @param blockCode   machine-readable block code (e.g. {@code "GUARD_ROWS_SCANNED_EXCEEDED"})
     * @param blockReason human-readable description of why the query was blocked
     * @param summary     complexity summary; may be {@code null} for post-execution blocks
     * @return blocked outcome
     */
    public static QueryGuardOutcome blocked(String blockCode,
                                            String blockReason,
                                            QueryComplexitySummary summary) {
        Objects.requireNonNull(blockCode, "blockCode must not be null");
        Objects.requireNonNull(blockReason, "blockReason must not be null");
        return new QueryGuardOutcome(false, blockCode, blockReason, summary, null);
    }

    /**
     * Returns a cancelled outcome carrying deterministic abort metadata.
     *
     * <p>Use this factory when a {@link QueryCancellationToken} fires mid-execution.
     * The {@code rowsReturnedBeforeAbort} value is the exact number of rows the
     * caller received before the query was stopped.
     *
     * @param blockCode              machine-readable block code (e.g. {@code "GUARD_CANCELLED"})
     * @param blockReason            human-readable cancellation reason
     * @param rowsReturnedBeforeAbort rows already yielded to the caller; must be &gt;= 0
     * @param summary                complexity summary; may be {@code null}
     * @return cancelled outcome
     */
    public static QueryGuardOutcome cancelled(String blockCode,
                                              String blockReason,
                                              int rowsReturnedBeforeAbort,
                                              QueryComplexitySummary summary) {
        Objects.requireNonNull(blockCode, "blockCode must not be null");
        Objects.requireNonNull(blockReason, "blockReason must not be null");
        if (rowsReturnedBeforeAbort < 0) {
            throw new IllegalArgumentException(
                    "rowsReturnedBeforeAbort must be >= 0, got " + rowsReturnedBeforeAbort);
        }
        return new QueryGuardOutcome(false, blockCode, blockReason, summary, rowsReturnedBeforeAbort);
    }

    /** Returns true when the query was allowed to proceed. */
    public boolean allowed() {
        return allowed;
    }

    /** Returns true when the query was blocked by the guard. */
    public boolean blocked() {
        return !allowed;
    }

    /**
     * Returns the machine-readable block code, or {@code null} when allowed.
     * Known codes: {@code GUARD_ROWS_SCANNED_EXCEEDED}, {@code GUARD_COMPLEXITY_EXCEEDED},
     * {@code GUARD_ROWS_RETURNED_EXCEEDED}, {@code GUARD_DURATION_EXCEEDED},
     * {@code GUARD_CANCELLED}.
     */
    public String blockCode() {
        return blockCode;
    }

    /**
     * Returns the human-readable block reason, or {@code null} when allowed.
     * Suitable for logging and operations tooling.
     */
    public String blockReason() {
        return blockReason;
    }

    /**
     * Returns the pre-execution complexity summary, or {@code null} for
     * post-execution outcomes that did not assess complexity.
     */
    public QueryComplexitySummary complexitySummary() {
        return complexitySummary;
    }

    /**
     * Returns the number of rows already returned to the caller before the query
     * was aborted via a {@link QueryCancellationToken}, or {@code null} when the
     * outcome is not a cancellation (i.e. a pre-execution block or a post-execution
     * limit exceeded).
     *
     * <p>This value is deterministic: it reflects exactly what the caller received,
     * making it safe to use in audit logs and partial-result tracking.
     */
    public Integer rowsReturnedBeforeAbort() {
        return rowsReturnedBeforeAbort;
    }

    /**
     * Returns a structured audit metadata map suitable for telemetry emission
     * and structured logging.
     *
     * @return unmodifiable audit metadata map
     */
    public Map<String, Object> auditMetadata() {
        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        meta.put("guardAllowed", allowed);
        if (blockCode != null) {
            meta.put("guardBlockCode", blockCode);
        }
        if (blockReason != null) {
            meta.put("guardBlockReason", blockReason);
        }
        if (rowsReturnedBeforeAbort != null) {
            meta.put("rowsReturnedBeforeAbort", rowsReturnedBeforeAbort);
        }
        if (complexitySummary != null) {
            meta.put("complexityScore", complexitySummary.estimatedComplexityScore());
            meta.put("filterCount", complexitySummary.filterCount());
            meta.put("joinCount", complexitySummary.joinCount());
        }
        return Collections.unmodifiableMap(meta);
    }
}
