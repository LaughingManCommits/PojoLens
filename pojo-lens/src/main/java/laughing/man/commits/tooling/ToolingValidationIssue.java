package laughing.man.commits.tooling;

import java.util.Objects;

/**
 * Machine-readable build-time validation finding.
 */
public final class ToolingValidationIssue {

    private final ToolingValidationSeverity severity;
    private final String code;
    private final String message;

    public ToolingValidationIssue(ToolingValidationSeverity severity, String code, String message) {
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
    }

    public ToolingValidationSeverity severity() {
        return severity;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
