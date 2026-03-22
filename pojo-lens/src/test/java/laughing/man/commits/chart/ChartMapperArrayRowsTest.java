package laughing.man.commits.chart;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ChartMapperArrayRowsTest {

    @Test
    public void toChartDataShouldMapSingleSeriesArrayRows() {
        List<Object[]> rows = List.of(
                new Object[]{"Engineering", 300L},
                new Object[]{"Finance", 150L}
        );

        ChartData data = ChartMapper.toChartData(
                rows,
                List.of("department", "payroll"),
                ChartSpec.of(ChartType.BAR, "department", "payroll")
        );

        assertEquals(2, data.getLabels().size());
        assertEquals("Engineering", data.getLabels().get(0));
        assertEquals("Finance", data.getLabels().get(1));
        assertEquals(300d, data.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(150d, data.getDatasets().get(0).getValues().get(1), 0.0001d);
    }

    @Test
    public void toChartDataShouldMapMultiSeriesArrayRows() {
        List<Object[]> rows = List.of(
                new Object[]{"Engineering", "2025-01", 300L},
                new Object[]{"Engineering", "2025-02", 150L},
                new Object[]{"Finance", "2025-02", 200L}
        );

        ChartData data = ChartMapper.toChartData(
                rows,
                List.of("department", "period", "payroll"),
                ChartSpec.of(ChartType.LINE, "period", "payroll", "department")
                        .withSortedLabels(true)
                        .withNullPointPolicy(NullPointPolicy.ZERO)
        );

        assertEquals(List.of("2025-01", "2025-02"), data.getLabels());
        assertEquals(2, data.getDatasets().size());
        assertEquals("Engineering", data.getDatasets().get(0).getLabel());
        assertEquals(List.of(300d, 150d), data.getDatasets().get(0).getValues());
        assertEquals("Finance", data.getDatasets().get(1).getLabel());
        assertEquals(List.of(0d, 200d), data.getDatasets().get(1).getValues());
    }

    @Test
    public void toChartDataShouldMapScatterMultiSeriesArrayRows() {
        List<Object[]> rows = List.of(
                new Object[]{2, "A", 20L},
                new Object[]{1, "B", 10L},
                new Object[]{1, "A", 15L},
                new Object[]{2, "A", 25L}
        );

        ChartData data = ChartMapper.toChartData(
                rows,
                List.of("x", "series", "y"),
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
    public void toChartDataShouldRejectUnknownFieldsForArrayRows() {
        List<Object[]> rows = List.<Object[]>of(new Object[]{"Engineering", 300L});

        try {
            ChartMapper.toChartData(
                    rows,
                    List.of("department", "payroll"),
                    ChartSpec.of(ChartType.BAR, "missing", "payroll")
            );
            fail("Expected unknown x-field error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown chart field 'missing'"));
        }
    }
}
