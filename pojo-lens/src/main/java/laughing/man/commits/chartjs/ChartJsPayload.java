package laughing.man.commits.chartjs;

import java.util.Map;

/**
 * Chart.js-ready payload produced by {@link ChartJsAdapter}.
 */
public record ChartJsPayload(String type,
                             ChartJsData data,
                             Map<String, Object> options) {
}
