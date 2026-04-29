package laughing.man.commits.tooling;

import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.report.SavedReport;
import laughing.man.commits.report.SavedReportKind;
import laughing.man.commits.table.TabularColumn;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCountRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SavedReportCatalogValidatorTest {

    private static final String GROUPED_SQL =
            "select department, count(*) as total group by department order by department asc";

    @Test
    public void validateShouldAcceptValidSavedReport() {
        SavedReport report = SavedReport.sqlLike("dept-count", "Department count", GROUPED_SQL)
                .withChartSpec(ChartSpec.of(ChartType.BAR, "department", "total"))
                .withSchema(validDepartmentCountSchema());

        SavedReportValidationResult result = SavedReportCatalogValidator.validate(report);

        assertTrue(result.valid());
        assertTrue(result.issues().isEmpty());
        assertEquals(List.of("department"), result.planPreview().groupByFields());
        assertEquals(List.of("department", "total"), result.diagnostics().outputFields());
    }

    @Test
    public void validateCatalogShouldFlagDuplicateIdsAndParameterIssues() {
        SavedReport sql = SavedReport.sqlLike("dup", "Active employees",
                        "where active = :active order by salary desc")
                .withDefaultParam("unused", true);
        SavedReport natural = SavedReport.natural("dup", "Active employees natural",
                "show employees where active is :active sort by salary descending");

        SavedReportCatalogValidationResult result = SavedReportCatalogValidator.validate(List.of(sql, natural));

        assertFalse(result.valid());
        assertTrue(issueCodes(result.issues()).contains("PLT-SAVED-001"));
        assertTrue(issueCodes(result.reports().get(0).issues()).contains("PLT-SAVED-001"));
        assertTrue(issueCodes(result.reports().get(0).issues()).contains("PLT-SAVED-002"));
        assertTrue(issueCodes(result.reports().get(0).issues()).contains("PLT-SAVED-003"));
        assertTrue(issueCodes(result.reports().get(1).issues()).contains("PLT-SAVED-001"));
        assertTrue(issueCodes(result.reports().get(1).issues()).contains("PLT-SAVED-003"));
    }

    @Test
    public void validateShouldFlagChartAndSchemaMismatch() {
        SavedReport report = SavedReport.sqlLike("broken-output", "Broken output", GROUPED_SQL)
                .withChartSpec(ChartSpec.of(ChartType.BAR, "department", "payroll"))
                .withSchema(TabularSchema.of(
                        DepartmentCountRow.class,
                        List.of(
                                TabularColumn.of("department", "Department", String.class, 0, null),
                                TabularColumn.of("payroll", "Payroll", Long.class, 1, null)
                        )
                ));

        SavedReportValidationResult result = SavedReportCatalogValidator.validate(report);

        assertFalse(result.valid());
        assertTrue(issueCodes(result.issues()).contains("PLT-SAVED-004"));
        assertTrue(issueCodes(result.issues()).contains("PLT-SAVED-005"));
    }

    @Test
    public void validateShouldWarnWhenWildcardPreventsStaticOutputChecks() {
        SavedReport report = SavedReport.sqlLike("wildcard", "Wildcard output", "select * from Employee")
                .withChartSpec(ChartSpec.of(ChartType.BAR, "department", "salary"))
                .withSchema(TabularSchema.of(
                        DepartmentCountRow.class,
                        List.of(TabularColumn.of("department", "Department", String.class, 0, null))
                ));

        SavedReportValidationResult result = SavedReportCatalogValidator.validate(report);

        assertTrue(result.valid());
        assertTrue(issueCodes(result.issues()).contains("PLT-SAVED-006"));
        assertTrue(issueCodes(result.issues()).contains("PLT-SAVED-007"));
    }

    @Test
    public void validateSqlLikeShouldReturnStructuredParseFailure() {
        SavedReportValidationResult result =
                SavedReportCatalogValidator.validateSqlLike("bad-sql", "Bad SQL", "select from Employee");

        assertFalse(result.valid());
        assertEquals(SavedReportKind.SQL_LIKE, result.kind());
        assertTrue(issueCodes(result.issues()).contains("EQ-SQL-PAR-001"));
        assertTrue(result.diagnostics().errors().stream().anyMatch(error -> error.code().equals("EQ-SQL-PAR-001")));
    }

    @Test
    public void validateNaturalShouldReturnStructuredParseFailure() {
        SavedReportValidationResult result =
                SavedReportCatalogValidator.validateNatural("bad-natural", "Bad natural", "");

        assertFalse(result.valid());
        assertEquals(SavedReportKind.NATURAL, result.kind());
        assertTrue(issueCodes(result.issues()).contains("PLT-SAVED-008"));
    }

    private static TabularSchema validDepartmentCountSchema() {
        return TabularSchema.of(
                DepartmentCountRow.class,
                List.of(
                        TabularColumn.of("department", "Department", String.class, 0, null),
                        TabularColumn.of("total", "Total", Long.class, 1, "metric:COUNT")
                )
        );
    }

    private static Set<String> issueCodes(List<ToolingValidationIssue> issues) {
        return issues.stream().map(ToolingValidationIssue::code).collect(Collectors.toSet());
    }
}
