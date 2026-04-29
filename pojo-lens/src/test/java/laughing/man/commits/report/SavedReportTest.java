package laughing.man.commits.report;

import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.natural.NaturalQuery;
import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.SqlLikePlanPreview;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.table.TabularColumn;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCountRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SavedReportTest {

    private static final String SQL_QUERY =
            "select department, count(*) as total group by department order by department asc";
    private static final String NATURAL_QUERY =
            "show department, count of employees as total group by department sort by department ascending";

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    @Test
    public void sqlLikeFactoryShouldPopulateFields() {
        SavedReport report = SavedReport.sqlLike("r1", "Dept count", SQL_QUERY);

        assertEquals("r1", report.id());
        assertEquals("Dept count", report.name());
        assertEquals(SavedReportKind.SQL_LIKE, report.kind());
        assertEquals(SQL_QUERY, report.queryText());
        assertEquals(SavedReport.FORMAT_VERSION, report.version());
        assertTrue(report.defaultParams().isEmpty());
        assertNull(report.chartSpec());
        assertNull(report.schema());
    }

    @Test
    public void naturalFactoryShouldPopulateFields() {
        SavedReport report = SavedReport.natural("r2", "Dept count natural", NATURAL_QUERY);

        assertEquals("r2", report.id());
        assertEquals(SavedReportKind.NATURAL, report.kind());
        assertEquals(NATURAL_QUERY, report.queryText());
        assertEquals(SavedReport.FORMAT_VERSION, report.version());
    }

    @Test
    public void factoryShouldRejectBlankId() {
        assertThrows(IllegalArgumentException.class, () -> SavedReport.sqlLike("", "name", SQL_QUERY));
        assertThrows(IllegalArgumentException.class, () -> SavedReport.sqlLike(null, "name", SQL_QUERY));
    }

    @Test
    public void factoryShouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class, () -> SavedReport.sqlLike("id", "", SQL_QUERY));
    }

    @Test
    public void factoryShouldRejectBlankQueryText() {
        assertThrows(IllegalArgumentException.class, () -> SavedReport.sqlLike("id", "name", ""));
    }

    // -------------------------------------------------------------------------
    // Builders — immutability
    // -------------------------------------------------------------------------

    @Test
    public void withDefaultParamShouldReturnNewInstanceAndPreserveExisting() {
        SavedReport original = SavedReport.sqlLike("r1", "R", SQL_QUERY);
        SavedReport updated = original.withDefaultParam("active", true);

        assertNotSame(original, updated);
        assertTrue(original.defaultParams().isEmpty());
        assertEquals(Map.of("active", true), updated.defaultParams());
    }

    @Test
    public void withDefaultParamsShouldMergeIntoExistingParams() {
        SavedReport base = SavedReport.sqlLike("r1", "R", SQL_QUERY)
                .withDefaultParam("active", true);
        SavedReport merged = base.withDefaultParams(Map.of("dept", "Engineering"));

        assertEquals(2, merged.defaultParams().size());
        assertEquals(true, merged.defaultParams().get("active"));
        assertEquals("Engineering", merged.defaultParams().get("dept"));
    }

    @Test
    public void withChartSpecShouldReturnNewInstance() {
        var spec = ChartSpec.of(ChartType.BAR, "department", "total");
        SavedReport original = SavedReport.sqlLike("r1", "R", SQL_QUERY);
        SavedReport updated = original.withChartSpec(spec);

        assertNotSame(original, updated);
        assertNull(original.chartSpec());
        assertNotNull(updated.chartSpec());
    }

    @Test
    public void withSchemaShouldReturnNewInstance() {
        var schema = SqlLikeQuery.of(SQL_QUERY).schema(DepartmentCountRow.class);
        SavedReport original = SavedReport.sqlLike("r1", "R", SQL_QUERY);
        SavedReport updated = original.withSchema(schema);

        assertNotSame(original, updated);
        assertNull(original.schema());
        assertNotNull(updated.schema());
    }

    // -------------------------------------------------------------------------
    // Review
    // -------------------------------------------------------------------------

    @Test
    public void planPreviewShouldReflectSqlLikeQueryShape() {
        SavedReport report = SavedReport.sqlLike("r1", "R",
                "select department, count(*) as total group by department order by department asc");

        SqlLikePlanPreview preview = report.planPreview();

        assertTrue(preview.hasGrouping());
        assertFalse(preview.isWildcard());
        assertEquals(List.of("department"), preview.groupByFields());
    }

    @Test
    public void planPreviewShouldWorkForNaturalKind() {
        SavedReport report = SavedReport.natural("r2", "R", NATURAL_QUERY);

        SqlLikePlanPreview preview = report.planPreview();

        assertNotNull(preview);
        assertTrue(preview.hasGrouping());
    }

    @Test
    public void diagnosticsShouldBePresentForSqlLikeKind() {
        SavedReport report = SavedReport.sqlLike("r1", "R",
                "where active = :active order by salary desc");

        QueryDiagnostics diag = report.diagnostics();

        assertTrue(diag.valid());
        assertTrue(diag.requiredParams().contains("active"));
    }

    @Test
    public void diagnosticsShouldBePresentForNaturalKind() {
        SavedReport report = SavedReport.natural("r2", "R",
                "show employees where active is true sort by salary descending");

        QueryDiagnostics diag = report.diagnostics();

        assertTrue(diag.valid());
    }

    // -------------------------------------------------------------------------
    // Replay — toQuery / toNaturalQuery
    // -------------------------------------------------------------------------

    @Test
    public void toQueryShouldApplyDefaultParams() {
        SavedReport report = SavedReport.sqlLike("r1", "R",
                "where active = :active order by salary desc limit 2")
                .withDefaultParam("active", true);

        SqlLikeQuery query = report.toQuery();
        List<?> rows = query.filter(sampleEmployees(), sampleEmployees().get(0).getClass());

        assertEquals(2, rows.size());
    }

    @Test
    public void toQueryShouldThrowForNaturalKind() {
        SavedReport report = SavedReport.natural("r2", "R", NATURAL_QUERY);

        var ex = assertThrows(IllegalStateException.class, report::toQuery);
        assertTrue(ex.getMessage().contains("NATURAL"));
    }

    @Test
    public void toNaturalQueryShouldThrowForSqlLikeKind() {
        SavedReport report = SavedReport.sqlLike("r1", "R", SQL_QUERY);

        var ex = assertThrows(IllegalStateException.class, report::toNaturalQuery);
        assertTrue(ex.getMessage().contains("SQL_LIKE"));
    }

    @Test
    public void toNaturalQueryShouldReturnQueryForNaturalKind() {
        SavedReport report = SavedReport.natural("r2", "R",
                "show employees where active is true sort by salary descending limit 2");

        NaturalQuery query = report.toNaturalQuery();
        List<?> rows = query.filter(sampleEmployees(), sampleEmployees().get(0).getClass());

        assertEquals(2, rows.size());
    }

    // -------------------------------------------------------------------------
    // Replay — toDefinition
    // -------------------------------------------------------------------------

    @Test
    public void toDefinitionShouldReplayAndExecuteSqlLikeReport() {
        SavedReport report = SavedReport.sqlLike("r1", "Active dept counts",
                "select department, count(*) as total group by department order by department asc");

        ReportDefinition<DepartmentCountRow> def = report.toDefinition(DepartmentCountRow.class);
        List<DepartmentCountRow> rows = def.rows(sampleEmployees());

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(3L, rows.get(0).total);
        assertEquals("Finance", rows.get(1).department);
    }

    @Test
    public void toDefinitionShouldReplayAndExecuteNaturalReport() {
        SavedReport report = SavedReport.natural("r2", "Active dept counts natural",
                "show department, count of employees as total group by department sort by department ascending");

        ReportDefinition<DepartmentCountRow> def = report.toDefinition(DepartmentCountRow.class);
        List<DepartmentCountRow> rows = def.rows(sampleEmployees());

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).department);
    }

    @Test
    public void toDefinitionShouldWireChartSpecWhenPresent() {
        var spec = ChartSpec.of(ChartType.BAR, "department", "total");
        SavedReport report = SavedReport.sqlLike("r1", "R", SQL_QUERY).withChartSpec(spec);

        ReportDefinition<DepartmentCountRow> def = report.toDefinition(DepartmentCountRow.class);

        assertNotNull(def.chartSpec());
        assertEquals(ChartType.BAR, def.chartSpec().type());
    }

    @Test
    public void toDefinitionShouldWireSchemaWhenPresent() {
        TabularSchema customSchema = SqlLikeQuery.of(SQL_QUERY).schema(DepartmentCountRow.class);
        SavedReport report = SavedReport.sqlLike("r1", "R", SQL_QUERY).withSchema(customSchema);

        ReportDefinition<DepartmentCountRow> def = report.toDefinition(DepartmentCountRow.class);

        assertEquals(customSchema, def.schema());
    }

    @Test
    public void toDefinitionShouldApplyDefaultParamsDuringReplay() {
        SavedReport report = SavedReport.sqlLike("r1", "Active only",
                "where active = :active order by name asc")
                .withDefaultParam("active", true);

        ReportDefinition<?> def = report.toDefinition(sampleEmployees().get(0).getClass());
        List<?> rows = def.rows(sampleEmployees());

        assertEquals(3, rows.size());
    }

    // -------------------------------------------------------------------------
    // TabularColumn.typeName()
    // -------------------------------------------------------------------------

    @Test
    public void tabularColumnTypeNameShouldReturnSimpleClassName() {
        TabularSchema schema = SqlLikeQuery.of(SQL_QUERY).schema(DepartmentCountRow.class);

        TabularColumn deptCol = schema.column("department");
        TabularColumn totalCol = schema.column("total");

        assertNotNull(deptCol);
        assertEquals("String", deptCol.typeName());
        assertNotNull(totalCol);
        assertEquals("Long", totalCol.typeName());
    }

    // -------------------------------------------------------------------------
    // Source derivation
    // -------------------------------------------------------------------------

    @Test
    public void sourceFieldShouldBeConsistentWithQueryPlanPreview() {
        SavedReport report = SavedReport.sqlLike("r1", "R", SQL_QUERY);

        assertEquals(report.planPreview().source(), report.source());
    }

    // -------------------------------------------------------------------------
    // Full workflow example — create, configure, review, replay
    // -------------------------------------------------------------------------

    @Test
    public void fullWorkflowShouldCreateConfigureReviewAndReplaySavedReport() {
        // 1. Define the saved report
        SavedReport report = SavedReport
                .sqlLike("active-by-dept", "Active employees by department",
                         "select department, count(*) as total where active = :active"
                                 + " group by department order by department asc")
                .withDefaultParam("active", true)
                .withChartSpec(ChartSpec.of(ChartType.BAR, "department", "total"));

        // 2. Review without data
        SqlLikePlanPreview preview = report.planPreview();
        assertTrue(preview.hasGrouping());
        assertTrue(preview.requiredParams().contains("active"));

        QueryDiagnostics diag = report.diagnostics();
        assertTrue(diag.valid());

        // 3. Replay as a live definition
        ReportDefinition<DepartmentCountRow> def = report.toDefinition(DepartmentCountRow.class);

        // 4. Execute against two different snapshots
        List<DepartmentCountRow> allActive = def.rows(sampleEmployees());
        List<DepartmentCountRow> engineeringOnly = def.rows(
                sampleEmployees().stream()
                        .filter(e -> e.department.equals("Engineering"))
                        .toList());

        assertEquals(2, allActive.size());
        assertEquals(1, engineeringOnly.size());
        assertEquals("Engineering", engineeringOnly.get(0).department);
        assertNotNull(def.chartSpec());
    }
}
