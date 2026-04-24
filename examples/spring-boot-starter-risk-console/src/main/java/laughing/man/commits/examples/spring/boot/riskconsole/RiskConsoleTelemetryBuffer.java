package laughing.man.commits.examples.spring.boot.riskconsole;

import laughing.man.commits.telemetry.QueryTelemetryEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class RiskConsoleTelemetryBuffer {

    private static final int MAX_EVENTS = 40;

    private final Deque<QueryTelemetryEvent> events = new ArrayDeque<>();

    synchronized void record(QueryTelemetryEvent event) {
        events.addFirst(event);
        while (events.size() > MAX_EVENTS) {
            events.removeLast();
        }
    }

    synchronized List<Map<String, Object>> snapshot(int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int remaining = Math.max(limit, 0);
        for (QueryTelemetryEvent event : events) {
            if (remaining == 0) {
                break;
            }
            rows.add(toMap(event));
            remaining--;
        }
        return rows;
    }

    private static Map<String, Object> toMap(QueryTelemetryEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stage", event.stage().name());
        row.put("queryType", event.queryType());
        row.put("source", event.source());
        row.put("durationNanos", event.durationNanos());
        row.put("rowCountBefore", event.rowCountBefore());
        row.put("rowCountAfter", event.rowCountAfter());
        row.put("metadata", event.metadata());
        return row;
    }
}
