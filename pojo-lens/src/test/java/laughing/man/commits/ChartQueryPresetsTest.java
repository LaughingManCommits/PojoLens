package laughing.man.commits;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartQueryPreset;
import laughing.man.commits.chart.ChartQueryPresets;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.StatsExampleFixtures.DepartmentActiveBreakdownRow;
import laughing.man.commits.testutil.StatsExampleFixtures.DepartmentPayrollRow;
import laughing.man.commits.testutil.StatsExampleFixtures.PeriodHeadcountRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static laughing.man.commits.testutil.StatsExampleFixtures.utcDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChartQueryPresetsTest {

    @Test
    public void categoryTotalsPresetShouldBuildExecutableBarChartFlow() {
        ChartQueryPreset<DepartmentPayrollRow> preset = ChartQueryPresets
                .categoryTotals("department", Metric.SUM, "salary", "payroll", DepartmentPayrollRow.class);

        List<DepartmentPayrollRow> rows = preset.rows(sampleEmployees());
        ChartData chart = preset.chart(sampleEmployees());

        assertTrue(preset.source().contains("select department, sum(salary) as payroll"));
        assertEquals(ChartType.BAR, preset.chartSpec().type());
        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(360000L, rows.get(0).payroll);
        assertEquals(2, chart.getLabels().size());
        assertEquals("Engineering", chart.getLabels().get(0));
    }

    @Test
    public void timeSeriesCountsPresetShouldBuildSortedLineChartFlow() {
        List<Employee> source = new ArrayList<>();
        source.add(new Employee(1, "Alice", "Engineering", 120000, utcDate(2025, Calendar.JANUARY, 3), true));
        source.add(new Employee(2, "Bob", "Finance", 90000, utcDate(2025, Calendar.FEBRUARY, 4), true));
        source.add(new Employee(3, "Cara", "Engineering", 130000, utcDate(2025, Calendar.FEBRUARY, 5), true));

        ChartQueryPreset<PeriodHeadcountRow> preset = ChartQueryPresets
                .timeSeriesCounts("hireDate", TimeBucket.MONTH, "period", "headcount", PeriodHeadcountRow.class);

        List<PeriodHeadcountRow> rows = preset.rows(source);
        ChartData chart = preset.chart(source);

        assertTrue(preset.source().contains("bucket(hireDate,'month') as period"));
        assertEquals(ChartType.LINE, preset.chartSpec().type());
        assertTrue(preset.chartSpec().sortLabels());
        assertEquals(2, rows.size());
        assertEquals("2025-01", rows.get(0).period);
        assertEquals(1L, rows.get(0).headcount);
        assertEquals("2025-02", rows.get(1).period);
        assertEquals(2L, rows.get(1).headcount);
        assertEquals(List.of("2025-01", "2025-02"), chart.getLabels());
    }

    @Test
    public void groupedBreakdownPresetShouldBuildMultiSeriesChartFlow() {
        ChartQueryPreset<DepartmentActiveBreakdownRow> preset = ChartQueryPresets
                .groupedBreakdown("department", "active", Metric.COUNT, null, "headcount", DepartmentActiveBreakdownRow.class);

        List<DepartmentActiveBreakdownRow> rows = preset.rows(sampleEmployees());
        ChartData chart = preset.chart(sampleEmployees());

        assertTrue(preset.source().contains("group by active, department"));
        assertEquals(ChartType.BAR, preset.chartSpec().type());
        assertEquals("active", preset.chartSpec().seriesField());
        assertEquals(3, rows.size());
        assertEquals(2, chart.getDatasets().size());
        assertEquals(2, chart.getLabels().size());
    }

    @Test
    public void timeSeriesPresetShouldSupportExplicitCalendarPreset() {
        ChartQueryPreset<PeriodHeadcountRow> preset = ChartQueryPresets
                .timeSeriesCounts("hireDate",
                        TimeBucketPreset.week().withZone("Europe/Amsterdam").withWeekStart("sunday"),
                        "period",
                        "headcount",
                        PeriodHeadcountRow.class);

        assertTrue(preset.source().contains("bucket(hireDate,'week','Europe/Amsterdam','sunday') as period"));
        assertEquals(ChartType.LINE, preset.chartSpec().type());
    }

    @Test
    public void nonCountPresetShouldRequireMetricField() {
        try {
            ChartQueryPresets.categoryTotals("department", Metric.SUM, "   ", "payroll", DepartmentPayrollRow.class);
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("metricField must not be null/blank"));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }
}

