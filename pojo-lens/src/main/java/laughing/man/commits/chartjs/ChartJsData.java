package laughing.man.commits.chartjs;

import java.util.List;

/**
 * Chart.js data block produced by {@link ChartJsAdapter}.
 */
public record ChartJsData(List<String> labels,
                          List<ChartJsDataset> datasets) {
}
