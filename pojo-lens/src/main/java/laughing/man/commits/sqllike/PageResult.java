package laughing.man.commits.sqllike;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Paged query result with visible rows, total row count, overflow indicator,
 * and an optional keyset cursor for the next page.
 * <p>
 * Obtain a {@code PageResult} by calling {@link SqlLikeQuery#filterPage} on a
 * SQL-like query that has a static {@code LIMIT} clause and at least one
 * {@code ORDER BY} field. The helper computes the total row count without
 * pagination, executes with {@code limit + 1} lookahead, and trims the extra
 * row from the returned list. Typed and natural query surfaces also return
 * {@code PageResult} from their page helpers.
 * <p>
 * Usage:
 * <pre>{@code
 * PageResult<Employee> page = PojoLensSql
 *     .parse("where active = true order by salary desc, id desc limit 20")
 *     .filterPage(source, Employee.class);
 *
 * List<Employee> rows      = page.rows();
 * long           total     = page.totalRows();
 * boolean        more      = page.hasMore();
 * Optional<SqlLikeCursor> next = page.nextCursor();
 *
 * // Apply cursor to get the next page
 * if (next.isPresent()) {
 *     PageResult<Employee> nextPage = PojoLensSql
 *         .parse("where active = true order by salary desc, id desc limit 20")
 *         .keysetAfter(next.get())
 *         .filterPage(source, Employee.class);
 * }
 * }</pre>
 *
 * @param <T> row type
 */
public final class PageResult<T> {

    private final List<T> rows;
    private final long totalRows;
    private final boolean hasMore;
    private final SqlLikeCursor nextCursor;

    PageResult(List<T> rows, boolean hasMore, SqlLikeCursor nextCursor) {
        this(rows, rows == null ? 0L : rows.size(), hasMore, nextCursor);
    }

    PageResult(List<T> rows, long totalRows, boolean hasMore, SqlLikeCursor nextCursor) {
        this.rows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));
        if (totalRows < 0L) {
            throw new IllegalArgumentException("totalRows must be >= 0");
        }
        this.totalRows = totalRows;
        this.hasMore = hasMore;
        this.nextCursor = nextCursor;
    }

    /**
     * Creates a page result without a keyset cursor.
     *
     * @param rows      visible rows for this page
     * @param totalRows total rows matching the query before pagination
     * @param hasMore   true when more rows are available after this page
     * @param <T>       row type
     * @return page result
     */
    public static <T> PageResult<T> of(List<T> rows, long totalRows, boolean hasMore) {
        return new PageResult<>(rows, totalRows, hasMore, null);
    }

    /**
     * Returns the visible rows for this page, up to the query {@code LIMIT}.
     *
     * @return visible rows
     */
    public List<T> rows() {
        return rows;
    }

    /**
     * Returns the total number of rows matching the query before pagination is
     * applied. For cursor-based SQL-like pages, this count reflects the current
     * cursor boundary when {@code keysetAfter(...)} or {@code keysetBefore(...)}
     * is part of the query.
     *
     * @return total rows before pagination
     */
    public long totalRows() {
        return totalRows;
    }

    /**
     * Returns {@code true} when at least one row exists beyond this page.
     *
     * @return true when more rows are available
     */
    public boolean hasMore() {
        return hasMore;
    }

    /**
     * Returns the keyset cursor positioned at the last visible row, or empty
     * when no more rows are available.
     * <p>
     * Pass the cursor to {@link SqlLikeQuery#keysetAfter} on the same query to
     * fetch the next page. The cursor contains one entry per {@code ORDER BY}
     * field, taken from the last row on this page.
     *
     * @return next-page cursor, or empty when this is the last page
     */
    public Optional<SqlLikeCursor> nextCursor() {
        return Optional.ofNullable(nextCursor);
    }
}
