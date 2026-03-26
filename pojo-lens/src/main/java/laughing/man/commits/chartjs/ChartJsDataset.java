package laughing.man.commits.chartjs;

import java.util.List;

/**
 * Minimal Chart.js dataset contract produced by {@link ChartJsAdapter}.
 */
public record ChartJsDataset(String label,
                             List<Double> data,
                             Object backgroundColor,
                             String borderColor,
                             String stack,
                             String yAxisID,
                             Boolean fill,
                             Double tension) {
}
