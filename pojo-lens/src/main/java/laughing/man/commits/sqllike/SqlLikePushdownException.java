package laughing.man.commits.sqllike;

/**
 * Unchecked boundary exception for host-adapter pushdown integration failures.
 */
public class SqlLikePushdownException extends RuntimeException {

    public SqlLikePushdownException(String message) {
        super(message);
    }

    public SqlLikePushdownException(String message, Throwable cause) {
        super(message, cause);
    }
}
