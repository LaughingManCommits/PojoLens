package laughing.man.commits.chartjs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Chart.js data block produced by {@link ChartJsAdapter}.
 */
public record ChartJsData(List<String> labels,
                          List<ChartJsDataset> datasets) {
    public ChartJsData {
        labels = new ArrayList<>(Objects.requireNonNull(labels, "labels must not be null"));
        datasets = new ArrayList<>(Objects.requireNonNull(datasets, "datasets must not be null"));
    }

    public List<String> labels() {
        return Collections.unmodifiableList(labels);
    }

    public List<ChartJsDataset> datasets() {
        return Collections.unmodifiableList(datasets);
    }
}
