package laughing.man.commits;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.stats.StatsTable;
import laughing.man.commits.stats.StatsTablePayload;
import laughing.man.commits.stats.StatsViewPreset;
import laughing.man.commits.stats.StatsViewPresets;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentPayrollRow;
import laughing.man.commits.testutil.StatsExampleFixtures.DepartmentTotalRow;
import laughing.man.commits.testutil.StatsExampleFixtures.SummaryRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StatsViewPresetsTest {

    @Test
    public void summaryPresetShouldExposeSingleRowWithoutTotalsPayload() {
        StatsViewPreset<SummaryRow> preset = StatsViewPresets.summary(SummaryRow.class);

        StatsTable<SummaryRow> table = preset.table(sampleEmployees());

        assertEquals(1, table.rows().size());
        assertEquals(4L, table.rows().get(0).total);
        assertEquals(List.of("total"), table.schema().names());
        assertTrue(table.totals().isEmpty());
        assertFalse(table.hasTotals());
    }

    @Test
    public void groupedPresetShouldReturnTotalsAndStableOutputColumns() {
        StatsViewPreset<DepartmentTotalRow> preset = StatsViewPresets.by("department", DepartmentTotalRow.class);

        StatsTable<DepartmentTotalRow> table = preset.table(sampleEmployees());

        assertEquals(2, table.rows().size());
        assertEquals("Engineering", table.rows().get(0).department);
        assertEquals(3L, table.rows().get(0).total);
        assertEquals("Finance", table.rows().get(1).department);
        assertEquals(1L, table.rows().get(1).total);
        assertEquals(List.of("department", "total"), table.schema().names());
        assertEquals(List.of("total"), table.totals().keySet().stream().toList());
        assertEquals(4L, ((Number) table.totals().get("total")).longValue());
        assertTrue(table.hasTotals());
    }

    @Test
    public void topNPresetShouldBuildLeaderboardWithOverallTotals() {
        List<Employee> employees = sampleEmployees();
        StatsViewPreset<DepartmentPayrollRow> preset = StatsViewPresets.topNBy(
                "department",
                Metric.SUM,
                "salary",
                "payroll",
                1,
                DepartmentPayrollRow.class
        );

        StatsTable<DepartmentPayrollRow> table = preset.table(employees);

        assertEquals(1, table.rows().size());
        assertEquals("Engineering", table.rows().get(0).department);
        assertEquals(360000L, table.rows().get(0).payroll);
        assertEquals(List.of("department", "payroll"), table.schema().names());
        assertEquals(450000L, ((Number) table.totals().get("payroll")).longValue());
    }

    @Test
    public void projectionFreePresetShouldExposeDashboardFriendlyPayload() {
        StatsTablePayload payload = StatsViewPresets
                .by("department", Metric.SUM, "salary", "payroll")
                .tablePayload(sampleEmployees());

        assertEquals(List.of("department", "payroll"), payload.columns());
        assertEquals(2, payload.rows().size());
        assertEquals("Engineering", payload.rows().get(0).get("department"));
        assertEquals(360000L, ((Number) payload.rows().get(0).get("payroll")).longValue());
        assertEquals(450000L, ((Number) payload.totals().get("payroll")).longValue());
        assertTrue(payload.hasTotals());
    }

    @Test
    public void topNPresetShouldValidateArguments() {
        try {
            StatsViewPresets.topNBy("department", Metric.COUNT, 0, DepartmentTotalRow.class);
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("n must be > 0"));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    @Test
    public void nonCountPresetsShouldRequireMetricField() {
        try {
            StatsViewPresets.topNBy("department", Metric.SUM, 2, DepartmentPayrollRow.class);
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("metricField must not be null/blank"));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    @Test
    public void groupedPresetShouldShareReportDefinitionRowContract() {
        StatsViewPreset<DepartmentTotalRow> preset = StatsViewPresets.by("department", DepartmentTotalRow.class);
        ReportDefinition<DepartmentTotalRow> report = preset.reportDefinition();
        List<Employee> source = sampleEmployees();
        JoinBindings joinBindings = JoinBindings.of("employees", sampleCompanyEmployees());
        DatasetBundle datasetBundle = DatasetBundle.of(source, joinBindings);

        assertEquals(preset.source(), report.source());
        assertEquals(preset.schema().names(), report.schema().names());
        assertEquals(
                preset.rows(source).stream().map(row -> row.department + ":" + row.total).toList(),
                report.rows(source).stream().map(row -> row.department + ":" + row.total).toList()
        );
        assertEquals(
                preset.rows(source, joinBindings).stream().map(row -> row.department + ":" + row.total).toList(),
                report.rows(source, joinBindings).stream().map(row -> row.department + ":" + row.total).toList()
        );
        assertEquals(
                preset.rows(datasetBundle).stream().map(row -> row.department + ":" + row.total).toList(),
                report.rows(datasetBundle).stream().map(row -> row.department + ":" + row.total).toList()
        );
    }
}


