package laughing.man.commits.sqllike;

import java.util.Objects;

/**
 * Non-blocking SQL-like best-practice warning.
 */
public final class SqlLikeLintWarning {

    private final String code;
    private final String message;

    public SqlLikeLintWarning(String code, String message) {
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

