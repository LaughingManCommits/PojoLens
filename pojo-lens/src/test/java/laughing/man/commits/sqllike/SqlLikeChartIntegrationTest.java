package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLens;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.chart.ChartDataset;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentHeadcountAliasRow;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentHeadcountRow;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentPayrollRow;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentPeriodPayrollRow;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentSalaryPoint;
import laughing.man.commits.testutil.ChartTestFixtures.ScatterPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static laughing.man.commits.testutil.ChartTestFixtures.departmentMonthlySalaryPoints;
import static laughing.man.commits.testutil.TestDateFixtures.utcDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlLikeChartIntegrationTest {

    @Test
    public void sqlLikeGroupedMetricChartShouldMatchFluentChart() {
        List<Employee> employees = sampleEmployees();

        ChartData fluent = PojoLens.newQueryBuilder(employees)
                .addGroup("department")
                .addMetric("salary", Metric.SUM, "payroll")
                .initFilter()
                .chart(DepartmentPayrollRow.class,
                        ChartSpec.of(ChartType.BAR, "department", "payroll").withSortedLabels(true));

        ChartData sqlLike = PojoLens
                .parse("select department, sum(salary) as payroll group by department")
                .chart(employees, DepartmentPayrollRow.class,
                        ChartSpec.of(ChartType.BAR, "department", "payroll").withSortedLabels(true));

        assertEquals(fluent.getType(), sqlLike.getType());
        assertEquals(fluent.getLabels(), sqlLike.getLabels());
        assertEquals(datasetSummary(fluent.getDatasets()), datasetSummary(sqlLike.getDatasets()));
    }

    @Test
    public void sqlLikeChartShouldSupportAliasedAggregateOutputs() {
        List<Employee> employees = sampleEmployees();

        ChartData chart = PojoLens
                .parse("select department, count(*) as headcount group by department order by headcount desc")
                .chart(employees, DepartmentHeadcountRow.class,
                        ChartSpec.of(ChartType.BAR, "department", "headcount"));

        assertEquals(2, chart.getLabels().size());
        assertEquals(1, chart.getDatasets().size());
        assertEquals("headcount", chart.getDatasets().get(0).getLabel());
        assertEquals(3d, chart.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(1d, chart.getDatasets().get(0).getValues().get(1), 0.0001d);
    }

    @Test
    public void sqlLikeChartShouldSupportAliasedGroupOutputs() {
        List<Employee> employees = sampleEmployees();

        ChartData chart = PojoLens
                .parse("select department as dept, count(*) as total group by department")
                .chart(employees, DepartmentHeadcountAliasRow.class,
                        ChartSpec.of(ChartType.BAR, "dept", "total").withSortedLabels(true));

        assertEquals(List.of("Engineering", "Finance"), chart.getLabels());
        assertEquals(1, chart.getDatasets().size());
        assertEquals("total", chart.getDatasets().get(0).getLabel());
        assertEquals(List.of(3d, 1d), chart.getDatasets().get(0).getValues());
    }

    @Test
    public void sqlLikeChartShouldSupportAliasedGroupOutputsWithOrderBy() {
        List<Employee> employees = sampleEmployees();

        ChartData chart = PojoLens
                .parse("select department as dept, count(*) as total group by department order by total desc")
                .chart(employees, DepartmentHeadcountAliasRow.class,
                        ChartSpec.of(ChartType.BAR, "dept", "total"));

        assertEquals(List.of("Engineering", "Finance"), chart.getLabels());
        assertEquals(1, chart.getDatasets().size());
        assertEquals("total", chart.getDatasets().get(0).getLabel());
        assertEquals(List.of(3d, 1d), chart.getDatasets().get(0).getValues());
    }

    @Test
    public void sqlLikeMultiSeriesChartShouldMapBucketedAggregates() {
        List<DepartmentSalaryPoint> rows = departmentMonthlySalaryPoints();

        ChartData chart = PojoLens
                .parse("select department, bucket(hireDate,'month') as period, sum(salary) as payroll group by department, period")
                .chart(rows, DepartmentPeriodPayrollRow.class,
                        ChartSpec.of(ChartType.LINE, "period", "payroll", "department").withSortedLabels(true));

        assertEquals(2, chart.getLabels().size());
        assertEquals("2025-01", chart.getLabels().get(0));
        assertEquals("2025-02", chart.getLabels().get(1));
        assertEquals(2, chart.getDatasets().size());
        assertEquals("Engineering", chart.getDatasets().get(0).getLabel());
        assertEquals(100d, chart.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(150d, chart.getDatasets().get(0).getValues().get(1), 0.0001d);
    }

    @Test
    public void sqlLikeScatterChartShouldMapNumericXAxis() {
        List<ScatterPoint> rows = new ArrayList<>();
        rows.add(new ScatterPoint(1, 10));
        rows.add(new ScatterPoint(2, 25));

        ChartData chart = PojoLens
                .parse("select x, y")
                .chart(rows, ScatterPoint.class, ChartSpec.of(ChartType.SCATTER, "x", "y"));

        assertEquals(ChartType.SCATTER, chart.getType());
        assertEquals(2, chart.getLabels().size());
        assertEquals("1", chart.getLabels().get(0));
        assertEquals("2", chart.getLabels().get(1));
    }

    @Test
    public void repeatedChartExecutionsShouldRebindToCurrentSourceRows() {
        Date now = utcDate(2025, Calendar.MARCH, 1);
        List<Employee> firstRows = sampleEmployees();
        List<Employee> secondRows = List.of(
                new Employee(10, "Eve", "Engineering", 50_000, now, true),
                new Employee(11, "Frank", "Finance", 200_000, now, true)
        );
        ChartSpec spec = ChartSpec.of(ChartType.BAR, "department", "payroll").withSortedLabels(true);

        SqlLikeQuery query = PojoLens.parse("select department, sum(salary) as payroll group by department");

        ChartData first = query.chart(firstRows, DepartmentPayrollRow.class, spec);
        ChartData second = query.chart(secondRows, DepartmentPayrollRow.class, spec);

        assertEquals(List.of("Engineering", "Finance"), first.getLabels());
        assertEquals(List.of(360000d, 90000d), first.getDatasets().get(0).getValues());
        assertEquals(List.of("Engineering", "Finance"), second.getLabels());
        assertEquals(List.of(50000d, 200000d), second.getDatasets().get(0).getValues());
    }

    private static List<String> datasetSummary(List<ChartDataset> datasets) {
        List<String> summary = new ArrayList<>();
        for (ChartDataset dataset : datasets) {
            summary.add(dataset.getLabel() + ":" + dataset.getValues());
        }
        return summary;
    }

}

