package laughing.man.commits.tooling;

import java.util.List;
import java.util.Objects;

/**
 * Build-time validation result for a saved-report catalog.
 */
public final class SavedReportCatalogValidationResult {

    private final boolean valid;
    private final int reportCount;
    private final List<ToolingValidationIssue> issues;
    private final List<SavedReportValidationResult> reports;

    public SavedReportCatalogValidationResult(boolean valid,
                                              int reportCount,
                                              List<ToolingValidationIssue> issues,
                                              List<SavedReportValidationResult> reports) {
        this.valid = valid;
        this.reportCount = reportCount;
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues must not be null"));
        this.reports = List.copyOf(Objects.requireNonNull(reports, "reports must not be null"));
    }

    public boolean valid() {
        return valid;
    }

    public int reportCount() {
        return reportCount;
    }

    public List<ToolingValidationIssue> issues() {
        return issues;
    }

    public List<SavedReportValidationResult> reports() {
        return reports;
    }
}
