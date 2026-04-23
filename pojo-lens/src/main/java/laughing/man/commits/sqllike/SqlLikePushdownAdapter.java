package laughing.man.commits.sqllike;

/**
 * Host-owned bridge for advisory SQL-like pushdown.
 * <p>
 * PojoLens builds the request and executes the in-memory completion step. The
 * adapter owns database access, SQL rendering, authorization, and any
 * framework-specific integration.
 */
@FunctionalInterface
public interface SqlLikePushdownAdapter {

    /**
     * Fetches rows for the pushable first phase.
     *
     * @param request pushdown request metadata
     * @param rowClass class of rows returned to PojoLens
     * @param <T> row type returned to PojoLens
     * @return materialized pushed rows
     */
    <T> SqlLikePushdownResult<T> fetch(SqlLikePushdownRequest request, Class<T> rowClass);
}
