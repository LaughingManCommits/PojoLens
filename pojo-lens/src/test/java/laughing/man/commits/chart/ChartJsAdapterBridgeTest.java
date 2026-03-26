package laughing.man.commits.chart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import laughing.man.commits.PojoLensChart;
import laughing.man.commits.PojoLensCore;
import laughing.man.commits.chartjs.ChartJsAdapter;
import laughing.man.commits.chartjs.ChartJsDataset;
import laughing.man.commits.chartjs.ChartJsPayload;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentPayrollRow;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentPeriodPayrollRow;
import laughing.man.commits.testutil.ChartTestFixtures.EmployeeEvent;
import laughing.man.commits.testutil.ChartTestFixtures.PeriodSeriesPayrollRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ChartJsAdapterBridgeTest {

    @Test
    public void chartJsAdapterShouldBuildRenderReadyBarPayload() {
        List<EmployeeEvent> events = Arrays.asList(
                new EmployeeEvent("Engineering", 100),
                new EmployeeEvent("Engineering", 120),
                new EmployeeEvent("Finance", 90),
                new EmployeeEvent("HR", 80)
        );

        ChartData chartData = PojoLensCore.newQueryBuilder(events)
                .addGroup("department")
                .addMetric("salary", Metric.SUM, "payroll")
                .initFilter()
                .chart(DepartmentPayrollRow.class,
                        ChartSpec.of(ChartType.BAR, "department", "payroll")
                                .withSortedLabels(true)
                                .withTitle("Payroll by Department")
                                .withAxisLabels("Department", "Payroll"));

        ChartJsPayload payload = ChartJsAdapter.toPayload(chartData);

        assertEquals("bar", payload.type());
        assertEquals(List.of("Engineering", "Finance", "HR"), payload.data().labels());
        assertEquals(1, payload.data().datasets().size());
        ChartJsDataset dataset = payload.data().datasets().get(0);
        assertEquals("payroll", dataset.label());
        assertEquals(List.of(220d, 90d, 80d), dataset.data());
        assertEquals("Payroll by Department", titleText(payload));
        assertEquals("Department", axisTitle(payload, "x"));
        assertEquals("Payroll", axisTitle(payload, "y"));
    }

    @Test
    public void chartJsAdapterShouldPreserveAreaAndPercentMetadata() {
        List<DepartmentPeriodPayrollRow> rows = Arrays.asList(
                new DepartmentPeriodPayrollRow("Engineering", "2025-01", 300),
                new DepartmentPeriodPayrollRow("Finance", "2025-01", 100),
                new DepartmentPeriodPayrollRow("Engineering", "2025-02", 150),
                new DepartmentPeriodPayrollRow("Finance", "2025-02", 150)
        );

        ChartData chartData = PojoLensChart.toChartData(rows,
                ChartSpec.of(ChartType.AREA, "period", "payroll", "department")
                        .withSortedLabels(true)
                        .withStacked(true)
                        .withPercentStacked(true)
                        .withNullPointPolicy(NullPointPolicy.ZERO)
                        .withAxisLabels("Period", "Payroll Share"));

        ChartJsPayload payload = ChartJsAdapter.toPayload(chartData);

        assertEquals("line", payload.type());
        assertEquals(Boolean.TRUE, pojoLensMeta(payload).get("stacked"));
        assertEquals(Boolean.TRUE, pojoLensMeta(payload).get("percentStacked"));
        assertEquals("ZERO", pojoLensMeta(payload).get("nullPointPolicy"));
        assertEquals(100, numericAxis(payload).get("max"));
        assertTrue(payload.data().datasets().stream().allMatch(dataset -> Boolean.TRUE.equals(dataset.fill())));
    }

    @Test
    public void chartJsAdapterShouldCarryDatasetMetadataHints() {
        List<DepartmentPeriodPayrollRow> rows = new ArrayList<>();
        rows.add(new DepartmentPeriodPayrollRow("Engineering", "2025-01", 200));
        rows.add(new DepartmentPeriodPayrollRow("Finance", "2025-01", 120));
        rows.add(new DepartmentPeriodPayrollRow("Engineering", "2025-02", 240));
        rows.add(new DepartmentPeriodPayrollRow("Finance", "2025-02", 130));

        ChartData chartData = PojoLensChart.toChartData(rows,
                ChartSpec.of(ChartType.LINE, "period", "payroll", "department")
                        .withDatasetColorHint("Engineering", "#4caf50")
                        .withDatasetStackGroupId("Engineering", "dept")
                        .withDatasetAxisId("Engineering", "left-y")
                        .withDatasetColorHint("Finance", "#ff9800")
                        .withDatasetStackGroupId("Finance", "dept")
                        .withDatasetAxisId("Finance", "right-y")
                        .withSortedLabels(true));

        ChartJsPayload payload = ChartJsAdapter.toPayload(chartData);

        ChartJsDataset engineering = payload.data().datasets().get(0);
        ChartJsDataset finance = payload.data().datasets().get(1);
        assertEquals("#4caf50", engineering.backgroundColor());
        assertEquals("#4caf50", engineering.borderColor());
        assertEquals("dept", engineering.stack());
        assertEquals("left-y", engineering.yAxisID());
        assertEquals("#ff9800", finance.backgroundColor());
        assertEquals("right-y", finance.yAxisID());
        assertNull(finance.fill());
    }

    @Test
    public void projectionFreePresetShouldExposeChartJsShortcut() {
        List<EmployeeEvent> events = Arrays.asList(
                new EmployeeEvent("Engineering", 100),
                new EmployeeEvent("Engineering", 120),
                new EmployeeEvent("Finance", 90)
        );

        ChartJsPayload payload = ChartQueryPresets
                .categoryTotals("department", Metric.SUM, "salary", "payroll")
                .mapChartSpec(spec -> spec
                        .withTitle("Payroll by Department")
                        .withAxisLabels("Department", "Payroll"))
                .chartJs(events);

        assertEquals("bar", payload.type());
        assertEquals(List.of("Engineering", "Finance"), payload.data().labels());
        assertEquals(List.of(220d, 90d), payload.data().datasets().get(0).data());
        assertEquals("Payroll by Department", titleText(payload));
        assertEquals("Department", axisTitle(payload, "x"));
        assertEquals("Payroll", axisTitle(payload, "y"));
    }

    @Test
    public void chartJsDatasetSerializationShouldOmitNullOptionalFields() throws Exception {
        List<EmployeeEvent> events = Arrays.asList(
                new EmployeeEvent("Engineering", 100),
                new EmployeeEvent("Finance", 90)
        );

        ChartJsPayload payload = ChartQueryPresets
                .categoryTotals("department", Metric.SUM, "salary", "payroll")
                .chartJs(events);

        JsonNode dataset = new ObjectMapper()
                .readTree(new ObjectMapper().writeValueAsBytes(payload))
                .get("data")
                .get("datasets")
                .get(0);

        assertFalse(dataset.has("stack"));
        assertFalse(dataset.has("yAxisID"));
        assertFalse(dataset.has("fill"));
        assertFalse(dataset.has("tension"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pojoLensMeta(ChartJsPayload payload) {
        return (Map<String, Object>) payload.options().get("pojoLens");
    }

    @SuppressWarnings("unchecked")
    private static String titleText(ChartJsPayload payload) {
        Map<String, Object> plugins = (Map<String, Object>) payload.options().get("plugins");
        Map<String, Object> title = (Map<String, Object>) plugins.get("title");
        return (String) title.get("text");
    }

    @SuppressWarnings("unchecked")
    private static String axisTitle(ChartJsPayload payload, String axisName) {
        Map<String, Object> scales = (Map<String, Object>) payload.options().get("scales");
        Map<String, Object> axis = (Map<String, Object>) scales.get(axisName);
        Map<String, Object> title = (Map<String, Object>) axis.get("title");
        return (String) title.get("text");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> numericAxis(ChartJsPayload payload) {
        Map<String, Object> scales = (Map<String, Object>) payload.options().get("scales");
        return (Map<String, Object>) scales.get("y");
    }
}
