package laughing.man.commits.facet;

import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory facet aggregation over a field name.
 * Counts distinct values across a list of rows using reflection.
 */
public final class FacetQuery {

    private final String fieldName;

    FacetQuery(String fieldName) {
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName must not be null");
    }

    public <T> List<FacetOption> options(List<T> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (T row : rows) {
            if (row == null) {
                continue;
            }
            Object raw;
            try {
                raw = ReflectionUtil.getFieldValue(row, fieldName);
            } catch (Exception ex) {
                throw new IllegalArgumentException(
                        "FacetQuery: cannot read field '" + fieldName + "' from " + row.getClass().getSimpleName(), ex);
            }
            String value = raw == null ? null : String.valueOf(raw);
            counts.merge(value, 1L, Long::sum);
        }
        List<FacetOption> result = new ArrayList<>(counts.size());
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(e -> e.getKey() == null ? "" : e.getKey()))
                .forEach(e -> result.add(new FacetOption(e.getKey(), e.getValue())));
        return List.copyOf(result);
    }
}
