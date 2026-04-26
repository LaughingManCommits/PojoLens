package laughing.man.commits.sqllike;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation signal for query execution.
 *
 * <p>Attach a token to an execution guard via
 * {@link QueryExecutionGuard.Builder#cancellationToken(QueryCancellationToken)}.
 * The library polls {@link #isCancelled()} at the start of execution and between
 * rows during lazy (stream/iterator) execution. When the token fires, a
 * {@link QueryExecutionGuardException} is thrown with block code
 * {@code GUARD_CANCELLED} and the number of rows already returned to the caller.
 *
 * <p>The token is polled cooperatively — it does not interrupt running computation
 * mid-row, only between rows and at execution start.
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * AtomicBoolean cancel = new AtomicBoolean();
 * QueryExecutionGuard guard = QueryExecutionGuard.builder()
 *     .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
 *     .build();
 *
 * // In another thread or on HTTP request abort:
 * cancel.set(true);
 *
 * try {
 *     PojoLensSql.parse("select * from employees")
 *         .executionGuard(guard)
 *         .stream(snapshot, Employee.class)
 *         .forEach(e -> process(e));
 * } catch (QueryExecutionGuardException ex) {
 *     Integer abortedAfter = ex.outcome().rowsReturnedBeforeAbort();
 *     log.info("Cancelled after {} rows", abortedAfter);
 * }
 * }</pre>
 */
@FunctionalInterface
public interface QueryCancellationToken {

    /**
     * Returns {@code true} when the caller has requested query cancellation.
     *
     * <p>Implementations must be thread-safe — this method may be called from
     * any thread. Keep it fast; it is polled between every returned row.
     *
     * @return true when cancellation is requested
     */
    boolean isCancelled();

    /**
     * Returns a token backed by an {@link AtomicBoolean} flag.
     * Set the flag to {@code true} to request cancellation.
     *
     * @param flag cancellation flag; must not be null
     * @return token that mirrors the flag
     */
    static QueryCancellationToken ofAtomic(AtomicBoolean flag) {
        Objects.requireNonNull(flag, "flag must not be null");
        return flag::get;
    }

    /**
     * Returns a token that cancels when the given thread is interrupted.
     * Useful for tying query lifetime to a request thread, including a virtual
     * request thread in a Spring-style boundary.
     *
     * <p>This token only observes the interrupt state of the specific thread
     * instance passed here. If a host hands execution across threads or drives
     * cancellation through another request-scoped signal, prefer
     * {@link #ofAtomic(AtomicBoolean)}.
     *
     * @param thread target thread; must not be null
     * @return token backed by thread interrupt status
     */
    static QueryCancellationToken ofThread(Thread thread) {
        Objects.requireNonNull(thread, "thread must not be null");
        return thread::isInterrupted;
    }
}
