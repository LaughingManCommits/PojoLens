package laughing.man.commits.chartjs;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Chart.js-ready payload produced by {@link ChartJsAdapter}.
 */
public record ChartJsPayload(String type,
                             ChartJsData data,
                             Map<String, Object> options) {
    public ChartJsPayload {
        type = Objects.requireNonNull(type, "type must not be null");
        data = Objects.requireNonNull(data, "data must not be null");
        options = new LinkedHashMap<>(Objects.requireNonNull(options, "options must not be null"));
    }

    public Map<String, Object> options() {
        return Collections.unmodifiableMap(options);
    }
}
