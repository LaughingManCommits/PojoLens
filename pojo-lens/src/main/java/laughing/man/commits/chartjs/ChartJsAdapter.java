package laughing.man.commits.chartjs;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartDataset;
import laughing.man.commits.chart.ChartType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter that turns PojoLens {@link ChartData} into a Chart.js-ready payload.
 */
public final class ChartJsAdapter {

    private static final String[] PALETTE = {
            "#0d6efd", "#198754", "#fd7e14", "#dc3545",
            "#6610f2", "#20c997", "#ffc107", "#6f42c1"
    };

    private ChartJsAdapter() {
    }

    public static ChartJsPayload toPayload(ChartData chartData) {
        Objects.requireNonNull(chartData, "chartData must not be null");
        List<String> datasetColors = palette(chartData.getDatasets().size());
        List<ChartJsDataset> datasets = new ArrayList<>(chartData.getDatasets().size());
        for (int index = 0; index < chartData.getDatasets().size(); index++) {
            datasets.add(datasetPayload(chartData, chartData.getDatasets().get(index), datasetColors.get(index)));
        }
        return new ChartJsPayload(
                toChartJsType(chartData.getType()),
                new ChartJsData(List.copyOf(chartData.getLabels()), List.copyOf(datasets)),
                chartOptions(chartData)
        );
    }

    private static ChartJsDataset datasetPayload(ChartData chartData, ChartDataset dataset, String defaultColor) {
        String color = dataset.getColorHint() != null ? dataset.getColorHint() : defaultColor;
        Object background = chartData.getType() == ChartType.PIE
                ? palette(chartData.getLabels().size())
                : color;
        String borderColor = chartData.getType() == ChartType.PIE ? null : color;
        Boolean fill = chartData.getType() == ChartType.AREA ? Boolean.TRUE : null;
        Double tension = chartData.getType() == ChartType.AREA || chartData.getType() == ChartType.LINE ? 0.25d : null;
        return new ChartJsDataset(
                dataset.getLabel(),
                List.copyOf(dataset.getValues()),
                background,
                borderColor,
                dataset.getStackGroupId(),
                dataset.getAxisId(),
                fill,
                tension
        );
    }

    private static String toChartJsType(ChartType type) {
        if (type == ChartType.AREA) {
            return "line";
        }
        return type == null ? "bar" : type.name().toLowerCase();
    }

    private static Map<String, Object> chartOptions(ChartData chartData) {
        Map<String, Object> plugins = new LinkedHashMap<>();
        plugins.put("legend", Map.of("display", true, "position", "bottom"));
        if (hasText(chartData.getTitle())) {
            plugins.put("title", Map.of("display", true, "text", chartData.getTitle()));
        }
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("responsive", true);
        options.put("maintainAspectRatio", false);
        options.put("plugins", plugins);
        options.put("pojoLens", pojoLensMeta(chartData));
        if (chartData.getType() != ChartType.PIE) {
            options.put("scales", Map.of(
                    "x", axisOptions(chartData.isStacked(), chartData.getXLabel()),
                    "y", numericAxisOptions(chartData)
            ));
        }
        return options;
    }

    private static Map<String, Object> pojoLensMeta(ChartData chartData) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("stacked", chartData.isStacked());
        meta.put("percentStacked", chartData.isPercentStacked());
        meta.put("nullPointPolicy", chartData.getNullPointPolicy() == null ? null : chartData.getNullPointPolicy().name());
        meta.put("xLabel", chartData.getXLabel());
        meta.put("yLabel", chartData.getYLabel());
        return meta;
    }

    private static Map<String, Object> axisOptions(boolean stacked, String label) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("stacked", stacked);
        if (hasText(label)) {
            options.put("title", Map.of("display", true, "text", label));
        }
        return options;
    }

    private static Map<String, Object> numericAxisOptions(ChartData chartData) {
        Map<String, Object> options = axisOptions(chartData.isStacked(), chartData.getYLabel());
        options.put("beginAtZero", true);
        if (chartData.isPercentStacked()) {
            options.put("max", 100);
        }
        return options;
    }

    private static List<String> palette(int size) {
        List<String> colors = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            colors.add(PALETTE[index % PALETTE.length]);
        }
        return colors;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
