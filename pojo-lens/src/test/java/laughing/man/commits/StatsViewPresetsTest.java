package laughing.man.commits;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.stats.StatsTable;
import laughing.man.commits.stats.StatsViewPreset;
import laughing.man.commits.stats.StatsViewPresets;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
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

    public static class SummaryRow {
        public long total;

        public SummaryRow() {
        }
    }

    public static class DepartmentTotalRow {
        public String department;
        public long total;

        public DepartmentTotalRow() {
        }
    }

    public static class DepartmentPayrollRow {
        public String department;
        public long payroll;

        public DepartmentPayrollRow() {
        }
    }
}
