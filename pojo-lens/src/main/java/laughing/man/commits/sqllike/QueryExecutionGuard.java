package laughing.man.commits.sqllike;

import java.util.Objects;

/**
 * Configurable execution guard that bounds query complexity, rows scanned,
 * rows returned, execution duration, and supports cooperative cancellation.
 *
 * <p>Attach a guard to a query with
 * {@link SqlLikeQuery#executionGuard(QueryExecutionGuard)} or
 * {@link laughing.man.commits.natural.NaturalQuery#executionGuard(QueryExecutionGuard)}.
 * When a bound guard blocks execution, a {@link QueryExecutionGuardException}
 * is thrown carrying the full {@link QueryGuardOutcome} for audit purposes.
 *
 * <h3>Security boundary</h3>
 * <p>This guard provides bounded execution governance — it limits what queries
 * are allowed to run and how large their inputs/outputs may be. Authentication,
 * RBAC, and tenant-level authorization remain host-application responsibilities
 * and are not in scope for this contract. Pair it with
 * {@link QueryExposurePolicy} to restrict which fields and sources are visible
 * to user-authored queries.
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * AtomicBoolean cancel = new AtomicBoolean();
 * QueryExecutionGuard guard = QueryExecutionGuard.builder()
 *     .maxRowsScanned(10_000)
 *     .maxRowsReturned(500)
 *     .maxComplexityScore(8)
 *     .maxDurationMillis(2_000)
 *     .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
 *     .build();
 *
 * try {
 *     List<Row> rows = PojoLensSql.parse(userQuery)
 *         .executionGuard(guard)
 *         .filter(snapshot, Row.class);
 * } catch (QueryExecutionGuardException ex) {
 *     log.warn("Query blocked: {}", ex.outcome().auditMetadata());
 *     // surface ex.outcome().blockReason() to the user
 * }
 * }</pre>
 */
public final class QueryExecutionGuard {

    private static final QueryExecutionGuard UNRESTRICTED =
            new QueryExecutionGuard(-1, -1, -1, -1L, null);

    private final int maxRowsScanned;
    private final int maxRowsReturned;
    private final int maxComplexityScore;
    private final long maxDurationMillis;
    private final QueryCancellationToken cancellationToken;

    private QueryExecutionGuard(int maxRowsScanned,
                                int maxRowsReturned,
                                int maxComplexityScore,
                                long maxDurationMillis,
                                QueryCancellationToken cancellationToken) {
        this.maxRowsScanned = maxRowsScanned;
        this.maxRowsReturned = maxRowsReturned;
        this.maxComplexityScore = maxComplexityScore;
        this.maxDurationMillis = maxDurationMillis;
        this.cancellationToken = cancellationToken;
    }

    /**
     * Returns an unrestricted guard that never blocks execution.
     *
     * @return unrestricted guard
     */
    public static QueryExecutionGuard unrestricted() {
        return UNRESTRICTED;
    }

    /**
     * Starts a guard builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns true when no limits are set, no cancellation token is attached,
     * and every query is allowed unconditionally.
     *
     * @return true when unrestricted
     */
    public boolean isUnrestricted() {
        return maxRowsScanned < 0 && maxRowsReturned < 0
                && maxComplexityScore < 0 && maxDurationMillis < 0
                && cancellationToken == null;
    }

    /**
     * Returns true when this guard has pre-execution limits (rows scanned or
     * complexity score) that require a plan preview to evaluate.
     *
     * @return true when pre-execution limits are configured
     */
    public boolean hasPreExecutionLimits() {
        return maxRowsScanned >= 0 || maxComplexityScore >= 0;
    }

    /** Maximum allowed input rows scanned; {@code -1} means unlimited. */
    public int maxRowsScanned() {
        return maxRowsScanned;
    }

    /** Maximum allowed output rows returned; {@code -1} means unlimited. */
    public int maxRowsReturned() {
        return maxRowsReturned;
    }

    /** Maximum allowed complexity score; {@code -1} means unlimited. */
    public int maxComplexityScore() {
        return maxComplexityScore;
    }

    /**
     * Maximum allowed wall-clock duration in milliseconds; {@code -1} means
     * unlimited. Checked post-execution — the work has already completed, but
     * the exception signals to the caller that their deadline was exceeded.
     */
    public long maxDurationMillis() {
        return maxDurationMillis;
    }

    /**
     * Returns the cooperative cancellation token, or {@code null} when none is set.
     *
     * @return cancellation token or null
     */
    public QueryCancellationToken cancellationToken() {
        return cancellationToken;
    }

    /**
     * Checks complexity and input-row budget before query execution.
     *
     * @param preview     plan preview for the query being assessed
     * @param rowsScanned number of input rows that will be scanned
     * @return allowed or blocked outcome with audit metadata
     */
    public QueryGuardOutcome checkPreExecution(SqlLikePlanPreview preview, int rowsScanned) {
        Objects.requireNonNull(preview, "preview must not be null");
        QueryComplexitySummary summary = QueryComplexitySummary.from(preview);
        if (maxRowsScanned >= 0 && rowsScanned > maxRowsScanned) {
            return QueryGuardOutcome.blocked(
                    "GUARD_ROWS_SCANNED_EXCEEDED",
                    "Query would scan " + rowsScanned + " rows, limit is " + maxRowsScanned,
                    summary);
        }
        if (maxComplexityScore >= 0 && summary.estimatedComplexityScore() > maxComplexityScore) {
            return QueryGuardOutcome.blocked(
                    "GUARD_COMPLEXITY_EXCEEDED",
                    "Query complexity score " + summary.estimatedComplexityScore()
                            + " exceeds limit " + maxComplexityScore,
                    summary);
        }
        return QueryGuardOutcome.allowed(summary);
    }

    /**
     * Checks output-row budget and wall-clock duration after query execution.
     *
     * @param rowsReturned   number of rows produced by the query
     * @param durationMillis wall-clock time the query took in milliseconds
     * @return allowed or blocked outcome with audit metadata
     */
    public QueryGuardOutcome checkPostExecution(int rowsReturned, long durationMillis) {
        if (maxRowsReturned >= 0 && rowsReturned > maxRowsReturned) {
            return QueryGuardOutcome.blocked(
                    "GUARD_ROWS_RETURNED_EXCEEDED",
                    "Query returned " + rowsReturned + " rows, limit is " + maxRowsReturned,
                    null);
        }
        if (maxDurationMillis >= 0 && durationMillis > maxDurationMillis) {
            return QueryGuardOutcome.blocked(
                    "GUARD_DURATION_EXCEEDED",
                    "Query took " + durationMillis + " ms, limit is " + maxDurationMillis + " ms",
                    null);
        }
        return QueryGuardOutcome.allowed(null);
    }

    /**
     * Checks whether the attached cancellation token has fired.
     *
     * <p>When the token is cancelled, returns a {@link QueryGuardOutcome} with
     * block code {@code GUARD_CANCELLED} and the deterministic
     * {@code rowsReturnedBeforeAbort} count. When no token is attached or the
     * token has not fired, returns an allowed outcome.
     *
     * @param rowsReturnedSoFar rows already yielded to the caller at the time of this check
     * @return allowed outcome or cancelled outcome with abort metadata
     */
    public QueryGuardOutcome checkCancellation(int rowsReturnedSoFar) {
        if (rowsReturnedSoFar < 0) {
            throw new IllegalArgumentException(
                    "rowsReturnedSoFar must be >= 0, got " + rowsReturnedSoFar);
        }
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return QueryGuardOutcome.cancelled(
                    "GUARD_CANCELLED",
                    "Query execution was cancelled by the caller",
                    rowsReturnedSoFar,
                    null);
        }
        return QueryGuardOutcome.allowed(null);
    }

    /**
     * Builder for {@link QueryExecutionGuard}.
     * All limits default to unrestricted ({@code -1}); no cancellation token by default.
     */
    public static final class Builder {

        private int maxRowsScanned = -1;
        private int maxRowsReturned = -1;
        private int maxComplexityScore = -1;
        private long maxDurationMillis = -1L;
        private QueryCancellationToken cancellationToken = null;

        private Builder() {
        }

        /**
         * Limits the number of input rows the query may scan.
         *
         * @param limit maximum rows scanned; must be &gt;= 0
         * @return this builder
         */
        public Builder maxRowsScanned(int limit) {
            if (limit < 0) {
                throw new IllegalArgumentException("maxRowsScanned must be >= 0, got " + limit);
            }
            this.maxRowsScanned = limit;
            return this;
        }

        /**
         * Limits the number of output rows the query may return.
         *
         * @param limit maximum rows returned; must be &gt;= 0
         * @return this builder
         */
        public Builder maxRowsReturned(int limit) {
            if (limit < 0) {
                throw new IllegalArgumentException("maxRowsReturned must be >= 0, got " + limit);
            }
            this.maxRowsReturned = limit;
            return this;
        }

        /**
         * Limits the query's estimated complexity score.
         * See {@link QueryComplexitySummary#estimatedComplexityScore()} for
         * scoring details.
         *
         * @param limit maximum complexity score; must be &gt;= 0
         * @return this builder
         */
        public Builder maxComplexityScore(int limit) {
            if (limit < 0) {
                throw new IllegalArgumentException("maxComplexityScore must be >= 0, got " + limit);
            }
            this.maxComplexityScore = limit;
            return this;
        }

        /**
         * Limits wall-clock execution time. The check is applied post-execution;
         * the exception signals that the duration budget was exceeded.
         *
         * @param limit maximum duration in milliseconds; must be &gt;= 0
         * @return this builder
         */
        public Builder maxDurationMillis(long limit) {
            if (limit < 0) {
                throw new IllegalArgumentException("maxDurationMillis must be >= 0, got " + limit);
            }
            this.maxDurationMillis = limit;
            return this;
        }

        /**
         * Attaches a cooperative cancellation token. When the token fires,
         * a {@link QueryExecutionGuardException} is thrown with block code
         * {@code GUARD_CANCELLED} and the count of rows already returned.
         *
         * @param token cancellation signal; must not be null
         * @return this builder
         */
        public Builder cancellationToken(QueryCancellationToken token) {
            this.cancellationToken = Objects.requireNonNull(token, "token must not be null");
            return this;
        }

        /**
         * Builds the guard.
         *
         * @return configured execution guard
         */
        public QueryExecutionGuard build() {
            return new QueryExecutionGuard(maxRowsScanned, maxRowsReturned,
                    maxComplexityScore, maxDurationMillis, cancellationToken);
        }
    }
}
