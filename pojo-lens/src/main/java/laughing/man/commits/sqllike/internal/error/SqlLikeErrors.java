package laughing.man.commits.sqllike.internal.error;

/**
 * Deterministic SQL-like exception message formatter.
 */
public final class SqlLikeErrors {

    private SqlLikeErrors() {
    }

    public static IllegalArgumentException argument(String code, String message) {
        return new IllegalArgumentException(format(code, message));
    }

    public static IllegalStateException state(String code, String message, Throwable cause) {
        return new IllegalStateException(format(code, message), cause);
    }

    public static String format(String code, String message) {
        return code + ": " + message + " Troubleshooting: " + SqlLikeErrorCodes.troubleshootingLink(code);
    }
}

