package laughing.man.commits.examples.spring.boot.basic;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartDataset;
import laughing.man.commits.chart.ChartType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the frontend renderer thin by translating PojoLens chart models into Chart.js payloads.
 *
 * Read next:
 * - /docs/charts.md
 */
final class ChartJsPayloadMapper {

    private static final String[] PALETTE = {
            "#0d6efd", "#198754", "#fd7e14", "#dc3545",
            "#6610f2", "#20c997", "#ffc107", "#6f42c1"
    };

    private ChartJsPayloadMapper() {
    }

    static Map<String, Object> toPayload(ChartData chartData) {
        List<Map<String, Object>> datasets = new ArrayList<>();
        List<String> datasetColors = palette(chartData.getDatasets().size());
        for (int index = 0; index < chartData.getDatasets().size(); index++) {
            datasets.add(datasetPayload(chartData, chartData.getDatasets().get(index), datasetColors.get(index)));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", toChartJsType(chartData.getType()));
        payload.put("data", Map.of(
                "labels", chartData.getLabels(),
                "datasets", datasets
        ));
        payload.put("options", chartOptions(chartData));
        return payload;
    }

    private static Map<String, Object> datasetPayload(ChartData chartData, ChartDataset dataset, String defaultColor) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("label", dataset.getLabel());
        mapped.put("data", dataset.getValues());
        String color = dataset.getColorHint() != null ? dataset.getColorHint() : defaultColor;
        if (chartData.getType() == ChartType.PIE) {
            mapped.put("backgroundColor", palette(chartData.getLabels().size()));
        } else {
            mapped.put("backgroundColor", color);
            mapped.put("borderColor", color);
        }
        if (chartData.getType() == ChartType.AREA) {
            mapped.put("fill", true);
            mapped.put("tension", 0.25);
        }
        if (chartData.getType() == ChartType.LINE) {
            mapped.put("tension", 0.25);
        }
        if (dataset.getStackGroupId() != null) {
            mapped.put("stack", dataset.getStackGroupId());
        }
        if (dataset.getAxisId() != null) {
            mapped.put("yAxisID", dataset.getAxisId());
        }
        return mapped;
    }

    private static String toChartJsType(ChartType type) {
        if (type == ChartType.AREA) {
            return "line";
        }
        return type.name().toLowerCase();
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
        if (chartData.getType() != ChartType.PIE) {
            options.put("scales", Map.of(
                    "x", axisOptions(chartData.isStacked(), chartData.getXLabel()),
                    "y", numericAxisOptions(chartData.isStacked(), chartData.getYLabel())
            ));
        }
        return options;
    }

    private static Map<String, Object> axisOptions(boolean stacked, String label) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("stacked", stacked);
        if (hasText(label)) {
            options.put("title", Map.of("display", true, "text", label));
        }
        return options;
    }

    private static Map<String, Object> numericAxisOptions(boolean stacked, String label) {
        Map<String, Object> options = axisOptions(stacked, label);
        options.put("beginAtZero", true);
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
