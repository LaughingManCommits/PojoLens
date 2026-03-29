package laughing.man.commits.chart;

import laughing.man.commits.PojoLensCore;
import laughing.man.commits.PojoLensSql;
import laughing.man.commits.PojoLensChart;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentHeadcountRow;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentPayrollRow;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentPeriodPayrollRow;
import laughing.man.commits.testutil.ChartTestFixtures.EmployeeEvent;
import laughing.man.commits.testutil.ChartTestFixtures.PeriodSeriesPayrollRow;
import laughing.man.commits.testutil.ChartTestFixtures.ScatterSignalRow;
import org.junit.jupiter.api.Test;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieChartBuilder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.internal.chartpart.Chart;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static laughing.man.commits.testutil.ChartTestFixtures.interopEmployeeEvents;
import static laughing.man.commits.testutil.ChartTestFixtures.interopMonthlyDepartmentPayroll;
import static laughing.man.commits.testutil.ChartTestFixtures.interopMonthlyDepartmentPayrollWithGaps;
import static laughing.man.commits.testutil.ChartTestFixtures.interopScatterSignals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChartLibraryInteropTest {

    @Test
    public void xChartBarChartShouldRenderFromLargerFluentGroupedDataset() throws Exception {
        List<EmployeeEvent> events = interopEmployeeEvents();
        ChartData chartData = PojoLensCore.newQueryBuilder(events)
                .addGroup("department")
                .addMetric("salary", Metric.SUM, "payroll")
                .initFilter()
                .chart(DepartmentPayrollRow.class,
                        ChartSpec.of(ChartType.BAR, "department", "payroll").withSortedLabels(true));
        assertChartShape(chartData, 3, 1);
        assertAllValuesPositive(chartData);

        CategoryChart chart = new CategoryChartBuilder()
                .title("Payroll by Department")
                .xAxisTitle("Department")
                .yAxisTitle("Payroll")
                .width(900)
                .height(600)
                .build();

        for (ChartDataset dataset : chartData.getDatasets()) {
            chart.addSeries(dataset.getLabel(), chartData.getLabels(), dataset.getValues());
        }

        assertNotNull(chart);
        assertEquals(1, chart.getSeriesMap().size());

        Path outDir = ensureOutputDirectory();
        Path chartFile = savePng(chart, outDir, "bar-payroll-by-department");
        assertFileGenerated(chartFile);
    }

    @Test
    public void xChartLineChartShouldRenderFromLargerSqlLikeMonthlyTrend() throws Exception {
        List<DepartmentPeriodPayrollRow> rows = interopMonthlyDepartmentPayroll();
        ChartData chartData = PojoLensSql.parse("select period, department, sum(payroll) as totalPayroll group by period, department")
                .chart(rows, PeriodSeriesPayrollRow.class,
                        ChartSpec.of(ChartType.LINE, "period", "totalPayroll", "department").withSortedLabels(true));
        assertChartShape(chartData, 12, 3);
        assertAllValuesPositive(chartData);

        XYChart chart = new XYChartBuilder()
                .title("Payroll Trend")
                .xAxisTitle("Period Index")
                .yAxisTitle("Payroll")
                .width(900)
                .height(600)
                .build();

        for (ChartDataset dataset : chartData.getDatasets()) {
            XYSeries series = chart.addSeries(dataset.getLabel(),
                    nonNullXIndexes(chartData.getLabels().size(), dataset.getValues()),
                    nonNullYValues(dataset.getValues()));
            series.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        }

        assertNotNull(chart);
        assertEquals(3, chart.getSeriesMap().size());

        Path outDir = ensureOutputDirectory();
        Path chartFile = savePng(chart, outDir, "line-monthly-payroll-trend");
        assertFileGenerated(chartFile);
    }

    @Test
    public void xChartPieChartShouldRenderFromSqlLikeHeadcountAggregation() throws Exception {
        List<EmployeeEvent> events = interopEmployeeEvents();
        ChartData chartData = PojoLensSql.parse("select department, count(*) as headcount group by department")
                .chart(events, DepartmentHeadcountRow.class, ChartSpec.of(ChartType.PIE, "department", "headcount"));
        assertChartShape(chartData, 3, 1);
        assertAllValuesPositive(chartData);

        PieChart chart = new PieChartBuilder()
                .title("Headcount by Department")
                .width(900)
                .height(600)
                .build();
        List<Double> values = chartData.getDatasets().get(0).getValues();
        for (int i = 0; i < chartData.getLabels().size(); i++) {
            chart.addSeries(chartData.getLabels().get(i), values.get(i));
        }

        assertNotNull(chart);
        assertEquals(3, chart.getSeriesMap().size());

        Path outDir = ensureOutputDirectory();
        Path chartFile = savePng(chart, outDir, "pie-headcount-by-department");
        assertFileGenerated(chartFile);
    }

    @Test
    public void xChartAreaChartShouldRenderStackedPercentPolicies() throws Exception {
        List<DepartmentPeriodPayrollRow> rows = interopMonthlyDepartmentPayrollWithGaps();
        ChartData chartData = PojoLensSql.parse("select period, department, sum(payroll) as totalPayroll group by period, department")
                .chart(rows, PeriodSeriesPayrollRow.class,
                        ChartSpec.of(ChartType.AREA, "period", "totalPayroll", "department")
                                .withSortedLabels(true)
                                .withStacked(true)
                                .withPercentStacked(true)
                                .withNullPointPolicy(NullPointPolicy.ZERO));
        assertChartShape(chartData, 12, 3);
        assertAllValuesWithinRange(chartData, 0.0, 100.0);
        assertPercentStackedSumsNearHundred(chartData, 0.001);

        XYChart chart = new XYChartBuilder()
                .title("Payroll Share by Month")
                .xAxisTitle("Period Index")
                .yAxisTitle("Share %")
                .width(900)
                .height(600)
                .build();

        for (ChartDataset dataset : chartData.getDatasets()) {
            XYSeries series = chart.addSeries(dataset.getLabel(), indexRange(chartData.getLabels().size()), dataset.getValues());
            series.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Area);
        }

        assertNotNull(chart);
        assertEquals(3, chart.getSeriesMap().size());

        Path outDir = ensureOutputDirectory();
        Path chartFile = savePng(chart, outDir, "area-payroll-share-by-month");
        assertFileGenerated(chartFile);
    }

    @Test
    public void xChartScatterChartShouldRenderLargerSignalDataset() throws Exception {
        List<ScatterSignalRow> rows = interopScatterSignals();
        ChartData chartData = PojoLensChart.toChartData(rows,
                ChartSpec.of(ChartType.SCATTER, "x", "y", "series").withSortedLabels(true));
        assertChartShape(chartData, 240, 3);
        assertAllValuesPositive(chartData);

        XYChart chart = new XYChartBuilder()
                .title("Latency vs Throughput")
                .xAxisTitle("Throughput")
                .yAxisTitle("Latency")
                .width(900)
                .height(600)
                .build();

        for (ChartDataset dataset : chartData.getDatasets()) {
            XYSeries series = chart.addSeries(dataset.getLabel(),
                    nonNullXFromLabels(chartData.getLabels(), dataset.getValues()),
                    nonNullYValues(dataset.getValues()));
            series.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        }

        assertNotNull(chart);
        assertEquals(3, chart.getSeriesMap().size());

        Path outDir = ensureOutputDirectory();
        Path chartFile = savePng(chart, outDir, "scatter-latency-throughput");
        assertFileGenerated(chartFile);
    }

    private static Path ensureOutputDirectory() throws Exception {
        Path outDir = Paths.get("target", "generated-charts");
        Files.createDirectories(outDir);
        return outDir;
    }

    private static Path savePng(Chart<?, ?> chart, Path outDir, String baseName) throws Exception {
        Path base = outDir.resolve(baseName);
        Path lowercase = Paths.get(base.toString() + ".png");
        Path uppercase = Paths.get(base.toString() + ".PNG");
        Files.deleteIfExists(lowercase);
        Files.deleteIfExists(uppercase);

        BitmapEncoder.saveBitmap(chart, base.toString(), BitmapEncoder.BitmapFormat.PNG);
        if (Files.exists(lowercase)) {
            return lowercase;
        }
        return uppercase;
    }

    private static void assertFileGenerated(Path chartFile) throws Exception {
        assertTrue(Files.exists(chartFile));
        assertTrue(Files.size(chartFile) > 0L);
        Path benchmarkImageDir = Paths.get("target", "benchmarks", "charts", "images");
        Files.createDirectories(benchmarkImageDir);
        Path target = benchmarkImageDir.resolve(chartFile.getFileName());
        Files.copy(chartFile, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void assertChartShape(ChartData chartData, int labelCount, int datasetCount) {
        assertEquals(labelCount, chartData.getLabels().size());
        assertEquals(datasetCount, chartData.getDatasets().size());
    }

    private static void assertAllValuesPositive(ChartData chartData) {
        for (ChartDataset dataset : chartData.getDatasets()) {
            for (Double value : dataset.getValues()) {
                if (value == null) {
                    continue;
                }
                assertTrue(value > 0.0);
            }
        }
    }

    private static void assertAllValuesWithinRange(ChartData chartData, double min, double max) {
        for (ChartDataset dataset : chartData.getDatasets()) {
            for (Double value : dataset.getValues()) {
                if (value == null) {
                    continue;
                }
                assertTrue(value >= min && value <= max);
            }
        }
    }

    private static void assertPercentStackedSumsNearHundred(ChartData chartData, double tolerance) {
        int labelCount = chartData.getLabels().size();
        for (int i = 0; i < labelCount; i++) {
            double sum = 0.0;
            for (ChartDataset dataset : chartData.getDatasets()) {
                Double value = dataset.getValues().get(i);
                if (value != null) {
                    sum += value;
                }
            }
            assertTrue(Math.abs(100.0 - sum) <= tolerance);
        }
    }

    private static List<Integer> indexRange(int size) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            indexes.add(i);
        }
        return indexes;
    }

    private static List<Integer> nonNullXIndexes(int size, List<Double> values) {
        List<Integer> x = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (values.get(i) != null) {
                x.add(i);
            }
        }
        return x;
    }

    private static List<Double> nonNullYValues(List<Double> values) {
        List<Double> y = new ArrayList<>();
        for (Double value : values) {
            if (value != null) {
                y.add(value);
            }
        }
        return y;
    }

    private static List<Double> nonNullXFromLabels(List<String> labels, List<Double> values) {
        List<Double> x = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            if (values.get(i) == null) {
                continue;
            }
            x.add(Double.parseDouble(labels.get(i)));
        }
        return x;
    }
}







