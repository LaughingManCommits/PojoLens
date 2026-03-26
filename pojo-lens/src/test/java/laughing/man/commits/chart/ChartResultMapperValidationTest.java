package laughing.man.commits.chart;

import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ChartResultMapperValidationTest {

    @Test
    public void toChartDataShouldRejectNonNumericYValues() {
        List<ChartResultMapperFixtures.InvalidMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.InvalidMetricRow("Engineering", "abc"));

        try {
            ChartResultMapper.toChartData(rows, ChartSpec.of(ChartType.BAR, "department", "payroll"));
            fail("Expected numeric y-field validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("must be numeric"));
        }
    }

    @Test
    public void toChartDataShouldRejectUnknownFields() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));

        try {
            ChartResultMapper.toChartData(rows, ChartSpec.of(ChartType.BAR, "missing", "payroll"));
            fail("Expected unknown x-field error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown chart field 'missing'"));
        }
    }

    @Test
    public void toChartDataShouldRejectUnknownFieldsForQueryRows() {
        QueryField department = new QueryField();
        department.setFieldName("department");
        department.setValue("Engineering");
        QueryField payroll = new QueryField();
        payroll.setFieldName("payroll");
        payroll.setValue(300L);
        QueryRow row = new QueryRow();
        row.setFields(List.of(department, payroll));

        try {
            ChartResultMapper.toChartData(List.of(row), ChartSpec.of(ChartType.BAR, "missing", "payroll"));
            fail("Expected unknown x-field error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown chart field 'missing'"));
        }
    }

    @Test
    public void toChartDataShouldRejectNullSpec() {
        try {
            ChartResultMapper.toChartData(
                    List.of(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300)),
                    null
            );
            fail("Expected missing spec error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Chart spec is required"));
        }
    }

    @Test
    public void toChartDataShouldRejectMissingRequiredFieldsInSpec() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = List.of(
                new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300)
        );
        try {
            ChartResultMapper.toChartData(rows, new ChartSpec(null, "department", "payroll", null, null, null, null, null));
            fail("Expected missing chart type error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Chart type is required"));
        }

        try {
            ChartResultMapper.toChartData(rows, new ChartSpec(ChartType.BAR, " ", "payroll", null, null, null, null, null));
            fail("Expected missing xField error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("xField is required"));
        }

        try {
            ChartResultMapper.toChartData(rows, new ChartSpec(ChartType.BAR, "department", " ", null, null, null, null, null));
            fail("Expected missing yField error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("yField is required"));
        }
    }

    @Test
    public void toChartDataShouldRejectBlankSeriesFieldWhenProvided() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = List.of(
                new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300)
        );
        try {
            ChartResultMapper.toChartData(rows, new ChartSpec(ChartType.BAR, "period", "payroll", " ", null, null, null, null));
            fail("Expected blank seriesField error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Chart seriesField must not be blank when provided"));
        }
    }

    @Test
    public void toChartDataShouldRejectSeriesFieldForPieType() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = List.of(
                new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300)
        );
        try {
            ChartResultMapper.toChartData(rows, ChartSpec.of(ChartType.PIE, "period", "payroll", "department"));
            fail("Expected pie seriesField validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("PIE does not support seriesField"));
        }
    }

    @Test
    public void toChartDataShouldRejectPercentStackedWithoutStacked() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = List.of(
                new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300)
        );
        try {
            ChartResultMapper.toChartData(rows, ChartSpec.of(ChartType.BAR, "period", "payroll", "department")
                    .withPercentStacked(true));
            fail("Expected percentStacked validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("percentStacked requires stacked=true"));
        }
    }

    @Test
    public void toChartDataShouldRejectStackedWithoutSeriesField() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = List.of(
                new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300)
        );
        try {
            ChartResultMapper.toChartData(rows, ChartSpec.of(ChartType.BAR, "period", "payroll")
                    .withStacked(true));
            fail("Expected stacked seriesField validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("stacked charts require seriesField"));
        }
    }

    @Test
    public void toChartDataShouldRejectStackedForUnsupportedChartType() {
        List<ChartResultMapperFixtures.ScatterRow> rows = List.of(new ChartResultMapperFixtures.ScatterRow(1, 10));
        try {
            ChartResultMapper.toChartData(rows, ChartSpec.of(ChartType.SCATTER, "x", "y", "x")
                    .withStacked(true));
            fail("Expected stacked chart-type validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("supported only for BAR and AREA"));
        }
    }

    @Test
    public void toChartDataShouldRejectUnknownSeriesField() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = List.of(
                new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300)
        );
        try {
            ChartResultMapper.toChartData(rows, ChartSpec.of(ChartType.LINE, "period", "payroll", "missingSeries"));
            fail("Expected unknown series field error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown chart field 'missingSeries'"));
        }
    }

    @Test
    public void toChartDataShouldRejectUnsupportedXAxisType() {
        List<ChartResultMapperFixtures.UnsupportedXRow> rows = List.of(new ChartResultMapperFixtures.UnsupportedXRow(new Object(), 10));
        try {
            ChartResultMapper.toChartData(rows, ChartSpec.of(ChartType.BAR, "x", "value"));
            fail("Expected unsupported x type error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("has unsupported type"));
        }
    }

    @Test
    public void toChartDataShouldRejectNullYValues() {
        List<ChartResultMapperFixtures.NullableYRow> rows = List.of(new ChartResultMapperFixtures.NullableYRow("Engineering", null));
        try {
            ChartResultMapper.toChartData(rows, ChartSpec.of(ChartType.BAR, "department", "payroll"));
            fail("Expected null y-value error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("must not be null"));
        }
    }
}

