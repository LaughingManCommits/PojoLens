package laughing.man.commits.chartjs;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal Chart.js dataset contract produced by {@link ChartJsAdapter}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChartJsDataset(String label,
                             Object data,
                             Object backgroundColor,
                             String borderColor,
                             String stack,
                             String yAxisID,
                             Boolean fill,
                             Double tension) {
}
