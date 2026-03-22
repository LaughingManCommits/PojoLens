package laughing.man.commits;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.NullPointPolicy;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FluentChartIntegrationTest {

    @Test
    public void fluentGroupedMetricShouldMapToChartInOneStep() {
        List<Employee> employees = sampleEmployees();

        ChartData chart = PojoLens.newQueryBuilder(employees)
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
        List<EmployeePoint> rows = new ArrayList<>();
        rows.add(new EmployeePoint(utcDate(2025, Calendar.JANUARY, 3), 100));
        rows.add(new EmployeePoint(utcDate(2025, Calendar.JANUARY, 20), 200));
        rows.add(new EmployeePoint(utcDate(2025, Calendar.FEBRUARY, 1), 150));

        ChartData chart = PojoLens.newQueryBuilder(rows)
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

        ChartData chart = PojoLens.newQueryBuilder(employees)
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
        List<SeriesMetricRow> rows = new ArrayList<>();
        rows.add(new SeriesMetricRow("Engineering", "2025-01", 300));
        rows.add(new SeriesMetricRow("Finance", "2025-01", 100));
        rows.add(new SeriesMetricRow("Engineering", "2025-02", 150));
        rows.add(new SeriesMetricRow("Finance", "2025-02", 150));

        ChartData chart = PojoLens.newQueryBuilder(rows)
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

    public static class DepartmentPayrollRow {
        public String department;
        public long payroll;

        public DepartmentPayrollRow() {
        }
    }

    public static class PeriodPayrollRow {
        public String period;
        public long payroll;

        public PeriodPayrollRow() {
        }
    }

    public static class EmployeePoint {
        public Date hireDate;
        public int salary;

        public EmployeePoint() {
        }

        public EmployeePoint(Date hireDate, int salary) {
            this.hireDate = hireDate;
            this.salary = salary;
        }
    }

    public static class SeriesMetricRow {
        public String department;
        public String period;
        public int payroll;

        public SeriesMetricRow() {
        }

        public SeriesMetricRow(String department, String period, int payroll) {
            this.department = department;
            this.period = period;
            this.payroll = payroll;
        }
    }

    private static Date utcDate(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}

