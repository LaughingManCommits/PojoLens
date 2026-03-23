package laughing.man.commits.table.internal;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryMetric;
import laughing.man.commits.builder.QueryTimeBucket;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.table.TabularColumn;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Internal schema derivation helpers for fluent and SQL-like contracts.
 */
public final class TabularSchemaSupport {

    private TabularSchemaSupport() {
    }

    public static TabularSchema fromFluentBuilder(FilterQueryBuilder builder, Class<?> projectionClass) {
        Map<String, Class<?>> projectionTypes = ReflectionUtil.collectQueryableFieldTypes(projectionClass);
        ArrayList<TabularColumn> columns = new ArrayList<>();
        int order = 0;

        if (!builder.getMetrics().isEmpty()) {
            if (!builder.getGroupFields().isEmpty()) {
                List<Map.Entry<Integer, String>> groups = CollectionUtil.sortedEntriesByKey(builder.getGroupFields());
                for (Map.Entry<Integer, String> entry : groups) {
                    String fieldName = entry.getValue();
                    QueryTimeBucket timeBucket = builder.getTimeBuckets().get(fieldName);
                    columns.add(column(fieldName,
                            projectionTypes.getOrDefault(fieldName, fallbackType(projectionTypes, fieldName, String.class)),
                            order++,
                            timeBucket == null ? null : "time-bucket:" + timeBucket.getPreset().explainToken()));
                }
            }
            for (QueryMetric metric : builder.getMetrics()) {
                columns.add(column(metric.getAlias(),
                        projectionTypes.getOrDefault(metric.getAlias(), defaultMetricType(metric.getMetric())),
                        order++,
                        "metric:" + metric.getMetric().name()));
            }
            return TabularSchema.of(projectionClass, columns);
        }

        if (!builder.getReturnFields().isEmpty()) {
            for (String fieldName : builder.getReturnFields()) {
                columns.add(column(fieldName,
                        projectionTypes.getOrDefault(fieldName, Object.class),
                        order++,
                        null));
            }
            return TabularSchema.of(projectionClass, columns);
        }

        for (Map.Entry<String, Class<?>> entry : projectionTypes.entrySet()) {
            columns.add(column(entry.getKey(), entry.getValue(), order++, null));
        }
        return TabularSchema.of(projectionClass, columns);
    }

    public static TabularSchema fromSqlLikeQuery(QueryAst ast, Class<?> projectionClass) {
        Map<String, Class<?>> projectionTypes = ReflectionUtil.collectQueryableFieldTypes(projectionClass);
        ArrayList<TabularColumn> columns = new ArrayList<>();
        SelectAst select = ast.select();
        int order = 0;

        if (select == null || select.wildcard()) {
            for (Map.Entry<String, Class<?>> entry : projectionTypes.entrySet()) {
                columns.add(column(entry.getKey(), entry.getValue(), order++, null));
            }
            return TabularSchema.of(projectionClass, columns);
        }

        for (SelectFieldAst field : select.fields()) {
            String outputName = field.outputName();
            String formatHint = null;
            if (field.timeBucketField()) {
                formatHint = "time-bucket:" + field.timeBucketPreset().explainToken();
            } else if (field.metricField()) {
                formatHint = "metric:" + field.metric().name();
            } else if (field.windowField()) {
                formatHint = "window:" + field.windowFunction();
            }
            columns.add(column(outputName,
                    projectionTypes.getOrDefault(outputName, defaultSqlLikeType(field, projectionTypes)),
                    order++,
                    formatHint));
        }
        return TabularSchema.of(projectionClass, columns);
    }

    private static TabularColumn column(String name, Class<?> type, int order, String formatHint) {
        return TabularColumn.of(name, labelFor(name), type, order, formatHint);
    }

    private static Class<?> defaultSqlLikeType(SelectFieldAst field, Map<String, Class<?>> projectionTypes) {
        if (field.metricField()) {
            return defaultMetricType(field.metric());
        }
        if (field.timeBucketField()) {
            return String.class;
        }
        if (field.windowField()) {
            return defaultWindowType(field);
        }
        if (!field.computedField()) {
            return projectionTypes.getOrDefault(field.field(), Object.class);
        }
        return Number.class;
    }

    private static Class<?> defaultMetricType(Metric metric) {
        if (metric == Metric.AVG) {
            return Double.class;
        }
        return Long.class;
    }

    private static Class<?> defaultWindowType(SelectFieldAst field) {
        String function = field.windowFunction();
        if (function == null) {
            return Number.class;
        }
        if ("ROW_NUMBER".equalsIgnoreCase(function)
                || "RANK".equalsIgnoreCase(function)
                || "DENSE_RANK".equalsIgnoreCase(function)
                || "COUNT".equalsIgnoreCase(function)) {
            return Long.class;
        }
        if ("AVG".equalsIgnoreCase(function)) {
            return Double.class;
        }
        return Number.class;
    }

    private static Class<?> fallbackType(Map<String, Class<?>> projectionTypes, String fieldName, Class<?> fallback) {
        return projectionTypes.getOrDefault(fieldName, fallback);
    }

    private static String labelFor(String name) {
        String normalized = name.replace('.', ' ').replace('_', ' ');
        StringBuilder spaced = new StringBuilder(normalized.length() + 8);
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (i > 0 && Character.isUpperCase(current) && Character.isLowerCase(normalized.charAt(i - 1))) {
                spaced.append(' ');
            }
            spaced.append(current);
        }
        String[] parts = spaced.toString().trim().split("\\s+");
        ArrayList<String> words = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }
        return String.join(" ", words);
    }
}

