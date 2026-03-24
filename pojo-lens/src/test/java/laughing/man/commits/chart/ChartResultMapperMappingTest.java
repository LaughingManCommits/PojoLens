package laughing.man.commits.chart;

import laughing.man.commits.PojoLens;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChartResultMapperMappingTest {

    @Test
    public void toSeriesPointsShouldMapSingleSeriesRows() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-02", 1, 300));

        List<SeriesPoint> points = ChartResultMapper.toSeriesPoints(rows, "department", "payroll");

        assertEquals(2, points.size());
        assertEquals("Engineering", points.get(0).getLabel());
        assertEquals(300d, points.get(0).getValue(), 0.0001d);
        assertEquals("Finance", points.get(1).getLabel());
        assertEquals(300d, points.get(1).getValue(), 0.0001d);
    }

    @Test
    public void toMultiSeriesPointsShouldMapMultiSeriesRows() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-02", 1, 150));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-02", 1, 300));

        List<MultiSeriesPoint> points = ChartResultMapper.toMultiSeriesPoints(rows, "period", "department", "payroll");

        assertEquals(3, points.size());
        assertEquals("2025-01", points.get(0).getX());
        assertEquals("Engineering", points.get(0).getSeries());
        assertEquals(300d, points.get(0).getValue(), 0.0001d);
    }

    @Test
    public void chartMappersShouldReturnEmptyListForEmptyInput() {
        List<SeriesPoint> single = ChartResultMapper.toSeriesPoints(List.of(), "department", "payroll");
        List<MultiSeriesPoint> multi = ChartResultMapper.toMultiSeriesPoints(List.of(), "period", "department", "payroll");

        assertTrue(single.isEmpty());
        assertTrue(multi.isEmpty());
    }

    @Test
    public void toChartDataShouldMapSingleSeries() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-02", 1, 150));

        ChartSpec spec = ChartSpec.of(ChartType.BAR, "department", "payroll")
                .withTitle("Payroll by Department")
                .withAxisLabels("Department", "Payroll");
        ChartData data = ChartResultMapper.toChartData(rows, spec);

        assertEquals(ChartType.BAR, data.getType());
        assertEquals("Payroll by Department", data.getTitle());
        assertEquals(2, data.getLabels().size());
        assertEquals("Engineering", data.getLabels().get(0));
        assertEquals("Finance", data.getLabels().get(1));
        assertEquals(1, data.getDatasets().size());
        assertEquals("payroll", data.getDatasets().get(0).getLabel());
        assertEquals(300d, data.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(150d, data.getDatasets().get(0).getValues().get(1), 0.0001d);
    }

    @Test
    public void toChartDataShouldMapQueryRows() {
        List<QueryRow> rows = List.of(
                queryRow("department", "Engineering", "payroll", 300L),
                queryRow("department", "Finance", "payroll", 150L)
        );

        ChartData data = ChartResultMapper.toChartData(rows, ChartSpec.of(ChartType.BAR, "department", "payroll"));

        assertEquals(2, data.getLabels().size());
        assertEquals("Engineering", data.getLabels().get(0));
        assertEquals("Finance", data.getLabels().get(1));
        assertEquals(300d, data.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(150d, data.getDatasets().get(0).getValues().get(1), 0.0001d);
    }

    @Test
    public void toChartDataShouldMapPieSingleSeries() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-02", 1, 150));

        ChartData data = ChartResultMapper.toChartData(rows, ChartSpec.of(ChartType.PIE, "department", "payroll"));

        assertEquals(ChartType.PIE, data.getType());
        assertEquals(2, data.getLabels().size());
        assertEquals(1, data.getDatasets().size());
        assertEquals(300d, data.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(150d, data.getDatasets().get(0).getValues().get(1), 0.0001d);
    }

    @Test
    public void toChartDataShouldSupportTypedSpecSelectors() {
        List<ChartResultMapperFixtures.TypedMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.TypedMetricRow("Engineering", "2025-01", 300));
        rows.add(new ChartResultMapperFixtures.TypedMetricRow("Finance", "2025-02", 150));

        ChartData data = ChartResultMapper.toChartData(rows,
                ChartSpec.of(ChartType.BAR, ChartResultMapperFixtures.TypedMetricRow::getDepartment,
                        ChartResultMapperFixtures.TypedMetricRow::getPayroll));

        assertEquals(2, data.getLabels().size());
        assertEquals("Engineering", data.getLabels().get(0));
        assertEquals("Finance", data.getLabels().get(1));
    }

    @Test
    public void toChartDataShouldSupportTypedSpecSelectorsForMultiSeries() {
        List<ChartResultMapperFixtures.TypedMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.TypedMetricRow("Engineering", "2025-01", 300));
        rows.add(new ChartResultMapperFixtures.TypedMetricRow("Engineering", "2025-02", 150));
        rows.add(new ChartResultMapperFixtures.TypedMetricRow("Finance", "2025-02", 200));

        ChartData data = ChartResultMapper.toChartData(rows,
                ChartSpec.of(ChartType.LINE,
                        ChartResultMapperFixtures.TypedMetricRow::getPeriod,
                        ChartResultMapperFixtures.TypedMetricRow::getPayroll,
                        ChartResultMapperFixtures.TypedMetricRow::getDepartment));

        assertEquals(2, data.getLabels().size());
        assertEquals(2, data.getDatasets().size());
        assertEquals("Engineering", data.getDatasets().get(0).getLabel());
    }

    @Test
    public void toChartDataShouldMapAreaMultiSeries() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-02", 1, 150));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-02", 1, 200));

        ChartData data = ChartResultMapper.toChartData(rows,
                ChartSpec.of(ChartType.AREA, "period", "payroll", "department"));

        assertEquals(ChartType.AREA, data.getType());
        assertEquals(2, data.getLabels().size());
        assertEquals(2, data.getDatasets().size());
    }

    @Test
    public void toChartDataShouldMapScatterSingleSeries() {
        List<ChartResultMapperFixtures.ScatterRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.ScatterRow(1, 10));
        rows.add(new ChartResultMapperFixtures.ScatterRow(2, 25));

        ChartData data = ChartResultMapper.toChartData(rows, ChartSpec.of(ChartType.SCATTER, "x", "y"));

        assertEquals(ChartType.SCATTER, data.getType());
        assertEquals("1", data.getLabels().get(0));
        assertEquals("2", data.getLabels().get(1));
        assertEquals(10d, data.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(25d, data.getDatasets().get(0).getValues().get(1), 0.0001d);
    }

    @Test
    public void toChartDataShouldMapScatterMultiSeriesQueryRows() {
        List<QueryRow> rows = List.of(
                queryRow("x", 2, "series", "A", "y", 20L),
                queryRow("x", 1, "series", "B", "y", 10L),
                queryRow("x", 1, "series", "A", "y", 15L),
                queryRow("x", 2, "series", "A", "y", 25L)
        );

        ChartData data = ChartResultMapper.toChartData(
                rows,
                ChartSpec.of(ChartType.SCATTER, "x", "y", "series").withSortedLabels(true)
        );

        assertEquals(List.of("1", "2"), data.getLabels());
        assertEquals(2, data.getDatasets().size());
        assertEquals("A", data.getDatasets().get(0).getLabel());
        assertEquals(List.of(15d, 25d), data.getDatasets().get(0).getValues());
        assertEquals("B", data.getDatasets().get(1).getLabel());
        assertEquals(Arrays.asList(10d, null), data.getDatasets().get(1).getValues());
    }

    @Test
    public void toChartDataShouldMapMultiSeries() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-02", 1, 150));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-02", 1, 200));

        ChartSpec spec = ChartSpec.of(ChartType.LINE, "period", "payroll", "department");
        ChartData data = ChartResultMapper.toChartData(rows, spec);

        assertEquals(2, data.getLabels().size());
        assertEquals("2025-01", data.getLabels().get(0));
        assertEquals("2025-02", data.getLabels().get(1));
        assertEquals(2, data.getDatasets().size());
        assertEquals("Engineering", data.getDatasets().get(0).getLabel());
        assertEquals(300d, data.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(150d, data.getDatasets().get(0).getValues().get(1), 0.0001d);
        assertEquals("Finance", data.getDatasets().get(1).getLabel());
        assertEquals(null, data.getDatasets().get(1).getValues().get(0));
        assertEquals(200d, data.getDatasets().get(1).getValues().get(1), 0.0001d);
    }

    @Test
    public void toChartDataShouldMapOptionalDatasetMetadataForSingleSeries() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-02", 1, 150));

        ChartSpec spec = ChartSpec.of(ChartType.BAR, "department", "payroll")
                .withDatasetColorHint("payroll", "#1f77b4")
                .withDatasetStackGroupId("payroll", "payroll-stack")
                .withDatasetAxisId("payroll", "left-y");
        ChartData data = ChartResultMapper.toChartData(rows, spec);

        assertEquals(1, data.getDatasets().size());
        assertEquals("#1f77b4", data.getDatasets().get(0).getColorHint());
        assertEquals("payroll-stack", data.getDatasets().get(0).getStackGroupId());
        assertEquals("left-y", data.getDatasets().get(0).getAxisId());
    }

    @Test
    public void toChartDataShouldMapOptionalDatasetMetadataForMultiSeries() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-01", 1, 100));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-02", 1, 150));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-02", 1, 200));

        ChartSpec spec = ChartSpec.of(ChartType.AREA, "period", "payroll", "department")
                .withDatasetColorHint("Engineering", "#4caf50")
                .withDatasetStackGroupId("Engineering", "dept-stack")
                .withDatasetAxisId("Engineering", "left-y")
                .withDatasetColorHint("Finance", "#ff9800")
                .withDatasetStackGroupId("Finance", "dept-stack")
                .withDatasetAxisId("Finance", "right-y");
        ChartData data = ChartResultMapper.toChartData(rows, spec);

        assertEquals(2, data.getDatasets().size());
        assertEquals("Engineering", data.getDatasets().get(0).getLabel());
        assertEquals("#4caf50", data.getDatasets().get(0).getColorHint());
        assertEquals("dept-stack", data.getDatasets().get(0).getStackGroupId());
        assertEquals("left-y", data.getDatasets().get(0).getAxisId());
        assertEquals("Finance", data.getDatasets().get(1).getLabel());
        assertEquals("#ff9800", data.getDatasets().get(1).getColorHint());
        assertEquals("dept-stack", data.getDatasets().get(1).getStackGroupId());
        assertEquals("right-y", data.getDatasets().get(1).getAxisId());
    }

    @Test
    public void toChartDataShouldZeroFillMissingSeriesPointsWhenConfigured() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-02", 1, 150));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-02", 1, 200));

        ChartSpec spec = ChartSpec.of(ChartType.BAR, "period", "payroll", "department")
                .withNullPointPolicy(NullPointPolicy.ZERO);
        ChartData data = ChartResultMapper.toChartData(rows, spec);

        assertEquals(2, data.getDatasets().size());
        assertEquals(0d, data.getDatasets().get(1).getValues().get(0), 0.0001d);
        assertEquals(200d, data.getDatasets().get(1).getValues().get(1), 0.0001d);
        assertEquals(NullPointPolicy.ZERO, data.getNullPointPolicy());
    }

    @Test
    public void toChartDataShouldPercentStackMultiSeriesWhenConfigured() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-01", 1, 100));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-02", 1, 150));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-02", 1, 150));

        ChartSpec spec = ChartSpec.of(ChartType.BAR, "period", "payroll", "department")
                .withStacked(true)
                .withPercentStacked(true)
                .withSortedLabels(true)
                .withNullPointPolicy(NullPointPolicy.ZERO);
        ChartData data = ChartResultMapper.toChartData(rows, spec);

        assertTrue(data.isStacked());
        assertTrue(data.isPercentStacked());
        assertEquals(75d, data.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(25d, data.getDatasets().get(1).getValues().get(0), 0.0001d);
        assertEquals(50d, data.getDatasets().get(0).getValues().get(1), 0.0001d);
        assertEquals(50d, data.getDatasets().get(1).getValues().get(1), 0.0001d);
    }

    @Test
    public void pojoLensToChartDataShouldExposePublicEntryPoint() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-02", 1, 150));

        ChartData data = PojoLens.toChartData(rows, ChartSpec.of(ChartType.BAR, "department", "payroll"));
        assertEquals(2, data.getLabels().size());
        assertEquals(1, data.getDatasets().size());
    }

    @Test
    public void toChartDataShouldSortLabelsWhenConfigured() {
        List<ChartResultMapperFixtures.DepartmentMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Finance", "2025-02", 1, 150));
        rows.add(new ChartResultMapperFixtures.DepartmentMetricRow("Engineering", "2025-01", 2, 300));

        ChartSpec spec = ChartSpec.of(ChartType.BAR, "department", "payroll")
                .withSortedLabels(true);
        ChartData data = ChartResultMapper.toChartData(rows, spec);

        assertEquals("Engineering", data.getLabels().get(0));
        assertEquals("Finance", data.getLabels().get(1));
        assertEquals(300d, data.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(150d, data.getDatasets().get(0).getValues().get(1), 0.0001d);
    }

    @Test
    public void toChartDataShouldFormatDateXAxisValues() {
        List<ChartResultMapperFixtures.DateMetricRow> rows = new ArrayList<>();
        rows.add(new ChartResultMapperFixtures.DateMetricRow(new Date(1735689600000L), 42));

        ChartSpec spec = ChartSpec.of(ChartType.LINE, "period", "payroll")
                .withDateFormat("yyyy-MM-dd");
        ChartData data = ChartResultMapper.toChartData(rows, spec);

        assertEquals(1, data.getLabels().size());
        assertEquals("2025-01-01", data.getLabels().get(0));
    }

    private static QueryRow queryRow(String firstField,
                                     Object firstValue,
                                     String secondField,
                                     Object secondValue) {
        QueryField first = new QueryField();
        first.setFieldName(firstField);
        first.setValue(firstValue);

        QueryField second = new QueryField();
        second.setFieldName(secondField);
        second.setValue(secondValue);

        QueryRow row = new QueryRow();
        row.setFields(List.of(first, second));
        return row;
    }

    private static QueryRow queryRow(String firstField,
                                     Object firstValue,
                                     String secondField,
                                     Object secondValue,
                                     String thirdField,
                                     Object thirdValue) {
        QueryField first = new QueryField();
        first.setFieldName(firstField);
        first.setValue(firstValue);

        QueryField second = new QueryField();
        second.setFieldName(secondField);
        second.setValue(secondValue);

        QueryField third = new QueryField();
        third.setFieldName(thirdField);
        third.setValue(thirdValue);

        QueryRow row = new QueryRow();
        row.setFields(List.of(first, second, third));
        return row;
    }
}

