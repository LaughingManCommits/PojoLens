package laughing.man.commits.chart;

import laughing.man.commits.PojoLensCore;
import laughing.man.commits.PojoLensSql;
import laughing.man.commits.PojoLensChart;

import laughing.man.commits.PojoLens;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentPayrollRow;
import laughing.man.commits.testutil.ChartTestFixtures.DepartmentPeriodPayrollRow;
import laughing.man.commits.testutil.ChartTestFixtures.EmployeeEvent;
import laughing.man.commits.testutil.ChartTestFixtures.PeriodSeriesPayrollRow;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ChartJsAdapterBridgeTest {

    @Test
    public void chartJsBridgeShouldMatchGoldenBasicBarPayload() throws Exception {
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
                .chart(DepartmentPayrollRow.class, ChartSpec.of(ChartType.BAR, "department", "payroll").withSortedLabels(true));

        String payload = toChartJsPayload(chartData);
        String fixture = readFixture("chartjs-basic-bar.json");
        assertEquals(normalize(fixture), normalize(payload));
    }

    @Test
    public void chartJsBridgeShouldMatchGoldenPercentStackedPayload() throws Exception {
        List<DepartmentPeriodPayrollRow> rows = Arrays.asList(
                new DepartmentPeriodPayrollRow("Engineering", "2025-01", 300),
                new DepartmentPeriodPayrollRow("Finance", "2025-01", 100),
                new DepartmentPeriodPayrollRow("Engineering", "2025-02", 150),
                new DepartmentPeriodPayrollRow("Finance", "2025-02", 150)
        );

        ChartData chartData = PojoLensSql.parse("select period, department, sum(payroll) as totalPayroll group by period, department")
                .chart(rows, PeriodSeriesPayrollRow.class,
                        ChartSpec.of(ChartType.AREA, "period", "totalPayroll", "department")
                                .withSortedLabels(true)
                                .withStacked(true)
                                .withPercentStacked(true)
                                .withNullPointPolicy(NullPointPolicy.ZERO));

        String payload = toChartJsPayload(chartData);
        String fixture = readFixture("chartjs-area-percent-stacked.json");
        assertEquals(normalize(fixture), normalize(payload));
    }

    @Test
    public void chartJsBridgeShouldMatchGoldenMetadataPayload() throws Exception {
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

        String payload = toChartJsPayload(chartData);
        String fixture = readFixture("chartjs-line-metadata.json");
        assertEquals(normalize(fixture), normalize(payload));
    }

    private static String toChartJsPayload(ChartData data) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{");
        appendJsonField(sb, "type", mapType(data.getType()), true);
        sb.append(",");
        sb.append("\"data\":{");
        sb.append("\"labels\":");
        appendStringArray(sb, data.getLabels());
        sb.append(",\"datasets\":[");
        for (int i = 0; i < data.getDatasets().size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            ChartDataset dataset = data.getDatasets().get(i);
            sb.append("{");
            appendJsonField(sb, "label", dataset.getLabel(), true);
            sb.append(",");
            sb.append("\"data\":");
            appendDoubleArray(sb, dataset.getValues());
            if (dataset.getColorHint() != null) {
                sb.append(",");
                appendJsonField(sb, "backgroundColor", dataset.getColorHint(), true);
            }
            if (dataset.getStackGroupId() != null) {
                sb.append(",");
                appendJsonField(sb, "stack", dataset.getStackGroupId(), true);
            }
            if (dataset.getAxisId() != null) {
                sb.append(",");
                appendJsonField(sb, "yAxisID", dataset.getAxisId(), true);
            }
            sb.append("}");
        }
        sb.append("]},");
        sb.append("\"options\":{");
        appendJsonField(sb, "stacked", String.valueOf(data.isStacked()), false);
        sb.append(",");
        appendJsonField(sb, "percentStacked", String.valueOf(data.isPercentStacked()), false);
        sb.append(",");
        appendJsonField(sb, "nullPointPolicy", data.getNullPointPolicy() == null ? null : data.getNullPointPolicy().name(), true);
        sb.append(",");
        appendJsonField(sb, "title", data.getTitle(), true);
        sb.append(",");
        appendJsonField(sb, "xLabel", data.getXLabel(), true);
        sb.append(",");
        appendJsonField(sb, "yLabel", data.getYLabel(), true);
        sb.append("}}");
        return sb.toString();
    }

    private static void appendJsonField(StringBuilder sb, String key, String value, boolean quotedValue) {
        sb.append("\"").append(escape(key)).append("\":");
        if (value == null) {
            sb.append("null");
            return;
        }
        if (!quotedValue) {
            sb.append(value);
            return;
        }
        sb.append("\"").append(escape(value)).append("\"");
    }

    private static void appendStringArray(StringBuilder sb, List<String> values) {
        sb.append("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            String value = values.get(i);
            if (value == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(escape(value)).append("\"");
            }
        }
        sb.append("]");
    }

    private static void appendDoubleArray(StringBuilder sb, List<Double> values) {
        sb.append("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            Double value = values.get(i);
            if (value == null) {
                sb.append("null");
            } else {
                sb.append(Double.toString(value));
            }
        }
        sb.append("]");
    }

    private static String mapType(ChartType type) {
        if (type == null) {
            return "bar";
        }
        switch (type) {
            case BAR:
                return "bar";
            case LINE:
                return "line";
            case PIE:
                return "pie";
            case AREA:
                return "line";
            case SCATTER:
                return "scatter";
            default:
                return "bar";
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String readFixture(String name) throws Exception {
        String path = "/fixtures/chart/" + name;
        InputStream stream = ChartJsAdapterBridgeTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing fixture: " + path);
        byte[] bytes = stream.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").trim();
    }

}





