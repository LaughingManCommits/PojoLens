package laughing.man.commits.tooling;

import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.report.SavedReport;
import laughing.man.commits.report.SavedReportKind;
import laughing.man.commits.sqllike.PlanPreviewField;
import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.QueryDiagnosticsError;
import laughing.man.commits.sqllike.SqlLikeLintWarning;
import laughing.man.commits.sqllike.SqlLikePlanPreview;
import laughing.man.commits.sqllike.parser.SqlLikeParseException;
import laughing.man.commits.table.TabularSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static validator for saved-report catalogs and config-owned query contracts.
 */
public final class SavedReportCatalogValidator {

    private static final String DUPLICATE_REPORT_ID = "PLT-SAVED-001";
    private static final String UNKNOWN_DEFAULT_PARAM = "PLT-SAVED-002";
    private static final String MISSING_DEFAULT_PARAM = "PLT-SAVED-003";
    private static final String CHART_FIELD_NOT_OUTPUT = "PLT-SAVED-004";
    private static final String SCHEMA_FIELD_NOT_OUTPUT = "PLT-SAVED-005";
    private static final String WILDCARD_CHART_VALIDATION_SKIPPED = "PLT-SAVED-006";
    private static final String WILDCARD_SCHEMA_VALIDATION_SKIPPED = "PLT-SAVED-007";
    private static final String RAW_QUERY_INVALID = "PLT-SAVED-008";

    private SavedReportCatalogValidator() {
    }

    public static SavedReportCatalogValidationResult validate(Collection<SavedReport> reports) {
        if (reports == null) {
            throw new IllegalArgumentException("reports must not be null");
        }
        List<SavedReport> orderedReports = List.copyOf(reports);
        LinkedHashSet<String> duplicateIds = duplicateIds(orderedReports);

        ArrayList<ToolingValidationIssue> catalogIssues = new ArrayList<>();
        for (String duplicateId : duplicateIds) {
            catalogIssues.add(error(
                    DUPLICATE_REPORT_ID,
                    "Saved report id '" + duplicateId + "' is duplicated in the catalog"
            ));
        }

        ArrayList<SavedReportValidationResult> results = new ArrayList<>(orderedReports.size());
        for (SavedReport report : orderedReports) {
            results.add(validateInternal(report, duplicateIds));
        }

        boolean valid = containsNoErrors(catalogIssues) && results.stream().allMatch(SavedReportValidationResult::valid);
        return new SavedReportCatalogValidationResult(valid, orderedReports.size(), catalogIssues, results);
    }

    public static SavedReportValidationResult validate(SavedReport report) {
        return validateInternal(report, Set.of());
    }

    public static SavedReportValidationResult validateSqlLike(String id, String name, String queryText) {
        try {
            return validate(SavedReport.sqlLike(id, name, queryText));
        } catch (IllegalArgumentException ex) {
            return invalidRawQueryResult(id, name, SavedReportKind.SQL_LIKE, queryText, ex);
        }
    }

    public static SavedReportValidationResult validateNatural(String id, String name, String queryText) {
        try {
            return validate(SavedReport.natural(id, name, queryText));
        } catch (IllegalArgumentException ex) {
            return invalidRawQueryResult(id, name, SavedReportKind.NATURAL, queryText, ex);
        }
    }

    private static SavedReportValidationResult validateInternal(SavedReport report, Set<String> duplicateIds) {
        if (report == null) {
            throw new IllegalArgumentException("report must not be null");
        }

        QueryDiagnostics diagnostics = report.diagnostics();
        SqlLikePlanPreview planPreview = report.planPreview();
        ArrayList<ToolingValidationIssue> issues = new ArrayList<>();

        if (duplicateIds.contains(report.id())) {
            issues.add(error(
                    DUPLICATE_REPORT_ID,
                    "Saved report id '" + report.id() + "' is duplicated in the catalog"
            ));
        }

        for (QueryDiagnosticsError diagnosticsError : diagnostics.errors()) {
            issues.add(error(diagnosticsError.code(), diagnosticsError.message()));
        }
        for (SqlLikeLintWarning lintWarning : diagnostics.lintWarnings()) {
            issues.add(warning(lintWarning.code(), lintWarning.message()));
        }

        validateDefaultParams(report, diagnostics.requiredParams(), issues);
        validateChartSpec(report.chartSpec(), planPreview, issues);
        validateSchema(report.schema(), planPreview, issues);

        boolean valid = containsNoErrors(issues);
        return new SavedReportValidationResult(
                report.id(),
                report.name(),
                report.kind(),
                report.source(),
                report.defaultParams(),
                diagnostics.requiredParams(),
                planPreview,
                diagnostics,
                issues,
                valid
        );
    }

    private static void validateDefaultParams(SavedReport report,
                                              List<String> requiredParams,
                                              List<ToolingValidationIssue> issues) {
        LinkedHashSet<String> required = new LinkedHashSet<>(requiredParams);
        LinkedHashSet<String> provided = new LinkedHashSet<>(report.defaultParams().keySet());

        for (String providedParam : provided) {
            if (!required.contains(providedParam)) {
                issues.add(error(
                        UNKNOWN_DEFAULT_PARAM,
                        "Saved report '" + report.id() + "' defines default param '" + providedParam
                                + "' that is not required by the query"
                ));
            }
        }

        for (String requiredParam : required) {
            if (!provided.contains(requiredParam)) {
                issues.add(warning(
                        MISSING_DEFAULT_PARAM,
                        "Saved report '" + report.id() + "' requires param '" + requiredParam
                                + "' without a default value"
                ));
            }
        }
    }

    private static void validateChartSpec(ChartSpec chartSpec,
                                          SqlLikePlanPreview planPreview,
                                          List<ToolingValidationIssue> issues) {
        if (chartSpec == null) {
            return;
        }
        if (planPreview.isWildcard()) {
            issues.add(warning(
                    WILDCARD_CHART_VALIDATION_SKIPPED,
                    "Chart spec fields cannot be statically validated against wildcard output"
            ));
            return;
        }

        Set<String> outputFields = outputFields(planPreview);
        requireOutputField(chartSpec.xField(), outputFields, CHART_FIELD_NOT_OUTPUT, "chart xField", issues);
        requireOutputField(chartSpec.yField(), outputFields, CHART_FIELD_NOT_OUTPUT, "chart yField", issues);
        if (chartSpec.seriesField() != null && !chartSpec.seriesField().isBlank()) {
            requireOutputField(chartSpec.seriesField(), outputFields, CHART_FIELD_NOT_OUTPUT,
                    "chart seriesField", issues);
        }
    }

    private static void validateSchema(TabularSchema schema,
                                       SqlLikePlanPreview planPreview,
                                       List<ToolingValidationIssue> issues) {
        if (schema == null) {
            return;
        }
        if (planPreview.isWildcard()) {
            issues.add(warning(
                    WILDCARD_SCHEMA_VALIDATION_SKIPPED,
                    "Schema columns cannot be statically validated against wildcard output"
            ));
            return;
        }

        Set<String> outputFields = outputFields(planPreview);
        for (String columnName : schema.names()) {
            requireOutputField(columnName, outputFields, SCHEMA_FIELD_NOT_OUTPUT, "schema column", issues);
        }
    }

    private static Set<String> outputFields(SqlLikePlanPreview planPreview) {
        LinkedHashSet<String> outputFields = new LinkedHashSet<>();
        for (PlanPreviewField field : planPreview.selectFields()) {
            outputFields.add(field.outputName());
        }
        return outputFields;
    }

    private static void requireOutputField(String fieldName,
                                           Set<String> outputFields,
                                           String code,
                                           String subject,
                                           List<ToolingValidationIssue> issues) {
        if (!outputFields.contains(fieldName)) {
            issues.add(error(code, "Saved report " + subject + " '" + fieldName + "' is not part of the query output"));
        }
    }

    private static LinkedHashSet<String> duplicateIds(List<SavedReport> reports) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (SavedReport report : reports) {
            if (report == null) {
                throw new IllegalArgumentException("reports must not contain null entries");
            }
            counts.merge(report.id(), 1, Integer::sum);
        }

        LinkedHashSet<String> duplicates = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 1) {
                duplicates.add(entry.getKey());
            }
        }
        return duplicates;
    }

    private static boolean containsNoErrors(List<ToolingValidationIssue> issues) {
        return issues.stream().noneMatch(issue -> issue.severity() == ToolingValidationSeverity.ERROR);
    }

    private static ToolingValidationIssue error(String code, String message) {
        return new ToolingValidationIssue(ToolingValidationSeverity.ERROR, code, message);
    }

    private static ToolingValidationIssue warning(String code, String message) {
        return new ToolingValidationIssue(ToolingValidationSeverity.WARNING, code, message);
    }

    private static SavedReportValidationResult invalidRawQueryResult(String id,
                                                                     String name,
                                                                     SavedReportKind kind,
                                                                     String queryText,
                                                                     IllegalArgumentException ex) {
        ToolingValidationIssue issue = error(extractIssueCode(kind, ex), ex.getMessage());
        QueryDiagnostics diagnostics = new QueryDiagnostics(
                false,
                List.of(new QueryDiagnosticsError(issue.code(), issue.message())),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                false
        );
        SqlLikePlanPreview planPreview = new SqlLikePlanPreview(
                normalizedSource(queryText),
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                false
        );
        return new SavedReportValidationResult(
                safeText(id),
                safeText(name),
                kind,
                normalizedSource(queryText),
                Collections.emptyMap(),
                Collections.emptyList(),
                planPreview,
                diagnostics,
                List.of(issue),
                false
        );
    }

    private static String extractIssueCode(SavedReportKind kind, IllegalArgumentException ex) {
        if (kind == SavedReportKind.SQL_LIKE) {
            if (ex instanceof SqlLikeParseException parseException) {
                return parseException.code();
            }
            String message = ex.getMessage();
            if (message != null) {
                int separator = message.indexOf(':');
                if (separator > 0) {
                    return message.substring(0, separator).trim();
                }
            }
        }
        return RAW_QUERY_INVALID;
    }

    private static String normalizedSource(String queryText) {
        return queryText == null ? "" : queryText.trim();
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }
}
