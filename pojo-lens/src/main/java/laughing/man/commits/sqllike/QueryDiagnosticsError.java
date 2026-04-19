package laughing.man.commits.sqllike;

import java.util.Objects;

/**
 * A validation or parse error found during SQL-like query diagnostics.
 */
public final class QueryDiagnosticsError {

    private final String code;
    private final String message;

    public QueryDiagnosticsError(String code, String message) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
