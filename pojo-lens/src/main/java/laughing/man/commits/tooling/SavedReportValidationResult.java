package laughing.man.commits.tooling;

import laughing.man.commits.report.SavedReportKind;
import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.SqlLikePlanPreview;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Build-time validation result for one saved report or config-owned query contract.
 */
public final class SavedReportValidationResult {

    private final String reportId;
    private final String reportName;
    private final SavedReportKind kind;
    private final String source;
    private final Map<String, Object> defaultParams;
    private final List<String> requiredParams;
    private final SqlLikePlanPreview planPreview;
    private final QueryDiagnostics diagnostics;
    private final List<ToolingValidationIssue> issues;
    private final boolean valid;

    public SavedReportValidationResult(String reportId,
                                       String reportName,
                                       SavedReportKind kind,
                                       String source,
                                       Map<String, Object> defaultParams,
                                       List<String> requiredParams,
                                       SqlLikePlanPreview planPreview,
                                       QueryDiagnostics diagnostics,
                                       List<ToolingValidationIssue> issues,
                                       boolean valid) {
        this.reportId = Objects.requireNonNull(reportId, "reportId must not be null");
        this.reportName = Objects.requireNonNull(reportName, "reportName must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.defaultParams = Map.copyOf(Objects.requireNonNull(defaultParams, "defaultParams must not be null"));
        this.requiredParams = List.copyOf(Objects.requireNonNull(requiredParams, "requiredParams must not be null"));
        this.planPreview = Objects.requireNonNull(planPreview, "planPreview must not be null");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues must not be null"));
        this.valid = valid;
    }

    public String reportId() {
        return reportId;
    }

    public String reportName() {
        return reportName;
    }

    public SavedReportKind kind() {
        return kind;
    }

    public String source() {
        return source;
    }

    public Map<String, Object> defaultParams() {
        return defaultParams;
    }

    public List<String> requiredParams() {
        return requiredParams;
    }

    public SqlLikePlanPreview planPreview() {
        return planPreview;
    }

    public QueryDiagnostics diagnostics() {
        return diagnostics;
    }

    public List<ToolingValidationIssue> issues() {
        return issues;
    }

    public boolean valid() {
        return valid;
    }
}
