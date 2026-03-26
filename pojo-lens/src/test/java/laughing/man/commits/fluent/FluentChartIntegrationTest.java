package laughing.man.commits.fluent;

import laughing.man.commits.PojoLensCore;

import laughing.man.commits.PojoLens;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.NullPointPolicy;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentPayrollRow;
import laughing.man.commits.testutil.ChartTestFixtures.PeriodPayrollRow;
import laughing.man.commits.testutil.ChartTestFixtures.SalaryPoint;
import laughing.man.commits.testutil.ChartTestFixtures.SeriesMetricRow;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static laughing.man.commits.testutil.ChartTestFixtures.monthlySalaryPoints;
import static laughing.man.commits.testutil.ChartTestFixtures.periodSeriesRows;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FluentChartIntegrationTest {

    @Test
    public void fluentGroupedMetricShouldMapToChartInOneStep() {
        List<Employee> employees = sampleEmployees();

        ChartData chart = PojoLensCore.newQueryBuilder(employees)
                .addGroup("department")
                .addMetric("salary", Metric.SUM, "payroll")
                .addOrder("payroll")
                .initFilter()
                .chart(Sort.DESC, DepartmentPayrollRow.class, ChartSpec.of(ChartType.BAR, "department", "payroll"));

        assertEquals(2, chart.getLabels().size());
        assertEquals("Engineering", chart.getLabels().get(0));
        assertEquals("Finance", chart.getLabels().get(1));
        assertEquals(1, chart.getDatasets().size());
        assertEquals(360000d, chart.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(90000d, chart.getDatasets().get(0).getValues().get(1), 0.0001d);
    }

    @Test
    public void fluentTimeBucketAliasShouldMapToChart() {
        List<SalaryPoint> rows = monthlySalaryPoints();

        ChartData chart = PojoLensCore.newQueryBuilder(rows)
                .addTimeBucket("hireDate", TimeBucket.MONTH, "period")
                .addMetric("salary", Metric.SUM, "payroll")
                .addOrder("period")
                .initFilter()
                .chart(Sort.ASC, PeriodPayrollRow.class,
                        ChartSpec.of(ChartType.LINE, "period", "payroll").withSortedLabels(true));

        assertEquals(2, chart.getLabels().size());
        assertEquals("2025-01", chart.getLabels().get(0));
        assertEquals("2025-02", chart.getLabels().get(1));
        assertEquals(300d, chart.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(150d, chart.getDatasets().get(0).getValues().get(1), 0.0001d);
    }

    @Test
    public void fluentGroupedMetricShouldMapToPieChart() {
        List<Employee> employees = sampleEmployees();

        ChartData chart = PojoLensCore.newQueryBuilder(employees)
                .addGroup("department")
                .addMetric("salary", Metric.SUM, "payroll")
                .initFilter()
                .chart(DepartmentPayrollRow.class, ChartSpec.of(ChartType.PIE, "department", "payroll"));

        assertEquals(ChartType.PIE, chart.getType());
        assertEquals(2, chart.getLabels().size());
        assertEquals(1, chart.getDatasets().size());
    }

    @Test
    public void fluentMultiSeriesShouldSupportStackedPercentPolicies() {
        List<SeriesMetricRow> rows = periodSeriesRows();

        ChartData chart = PojoLensCore.newQueryBuilder(rows)
                .initFilter()
                .chart(SeriesMetricRow.class,
                        ChartSpec.of(ChartType.AREA, "period", "payroll", "department")
                                .withStacked(true)
                                .withPercentStacked(true)
                                .withNullPointPolicy(NullPointPolicy.ZERO)
                                .withSortedLabels(true));

        assertEquals(ChartType.AREA, chart.getType());
        assertEquals(true, chart.isStacked());
        assertEquals(true, chart.isPercentStacked());
        assertEquals(NullPointPolicy.ZERO, chart.getNullPointPolicy());
    }
}




