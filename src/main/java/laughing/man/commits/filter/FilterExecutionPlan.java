package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryMetric;
import laughing.man.commits.builder.QueryTimeBucket;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.time.TimeBucketPreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static laughing.man.commits.EngineDefaults.SDF;

public final class FilterExecutionPlan {

    private final Map<String, Integer> fieldIndexByName = new HashMap<>();
    private final Map<String, Integer> fieldIndexByLowerName = new HashMap<>();
    private final Map<Integer, List<CompiledRule>> rulesByFieldIndex;
    private final Map<Integer, List<CompiledRule>> havingRulesByFieldIndex;
    private final List<Integer> returnFieldIndexes;
    private final List<Integer> distinctFieldIndexes;
    private final List<OrderColumn> orderColumns;
    private final List<GroupColumn> groupColumns;
    private final List<MetricPlan> metricPlans;

    FilterExecutionPlan(FilterQueryBuilder builder) {
        this(builder, fieldNames(builder.getRows()));
    }

    static FilterExecutionPlan forSchema(FilterQueryBuilder builder, List<String> fieldNames) {
        return new FilterExecutionPlan(builder, fieldNames);
    }

    private FilterExecutionPlan(FilterQueryBuilder builder, List<String> fieldNames) {
        for (int i = 0; i < fieldNames.size(); i++) {
            String name = fieldNames.get(i);
            fieldIndexByName.put(name, i);
            fieldIndexByLowerName.put(name.toLowerCase(Locale.ROOT), i);
        }
        rulesByFieldIndex = freezeCompiledRules(compileRules(
                builder.getFilterIDs(),
                builder.getFilterFields(),
                builder.getFilterValues(),
                builder.getFilterClause(),
                builder.getFilterSeparator(),
                builder.getFilterDateFormats()
        ));
        havingRulesByFieldIndex = freezeCompiledRules(compileRules(
                builder.getHavingIDs(),
                builder.getHavingFields(),
                builder.getHavingValues(),
                builder.getHavingClause(),
                builder.getHavingSeparator(),
                builder.getHavingDateFormats()
        ));
        returnFieldIndexes = List.copyOf(resolveFieldIndexes(builder.getReturnFields()));
        distinctFieldIndexes = List.copyOf(compileDistinctFieldIndexes(builder));
        orderColumns = List.copyOf(compileOrderColumns(builder));
        groupColumns = List.copyOf(compileGroupColumns(builder));
        metricPlans = List.copyOf(compileMetricPlans(builder));
    }

    Map<Integer, List<CompiledRule>> getRulesByFieldIndex() {
        return rulesByFieldIndex;
    }

    Map<Integer, List<CompiledRule>> getHavingRulesByFieldIndex() {
        return havingRulesByFieldIndex;
    }

    List<Integer> getReturnFieldIndexes() {
        return returnFieldIndexes;
    }

    List<Integer> getDistinctFieldIndexes() {
        return distinctFieldIndexes;
    }

    List<OrderColumn> getOrderColumns() {
        return orderColumns;
    }

    List<GroupColumn> getGroupColumns() {
        return groupColumns;
    }

    List<MetricPlan> getMetricPlans() {
        return metricPlans;
    }

    int findFieldIndex(String fieldName) {
        Integer index = fieldIndexByName.get(fieldName);
        return index == null ? -1 : index;
    }

    int findFieldIndexIgnoreCase(String fieldName) {
        if (fieldName == null) {
            return -1;
        }
        Integer index = fieldIndexByLowerName.get(fieldName.toLowerCase(Locale.ROOT));
        return index == null ? -1 : index;
    }

    List<Integer> resolveFieldIndexes(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> indexes = new ArrayList<>(names.size());
        for (String name : names) {
            int index = findFieldIndex(name);
            if (index >= 0) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private Map<Integer, List<CompiledRule>> compileRules(Map<String, List<String>> idsByField,
                                                          Map<String, String> configuredFields,
                                                          Map<String, Object> values,
                                                          Map<String, Clauses> clauses,
                                                          Map<String, Separator> separators,
                                                          Map<String, String> dateFormats) {
        Map<Integer, List<CompiledRule>> compiled = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : idsByField.entrySet()) {
            String fieldName = entry.getKey();
            int fieldIndex = findFieldIndex(fieldName);
            if (fieldIndex < 0) {
                continue;
            }
            for (String fieldID : entry.getValue()) {
                String configuredField = configuredFields.get(fieldID);
                if (configuredField == null) {
                    continue;
                }
                if (!fieldName.equals(configuredField)) {
                    continue;
                }
                String dateFormat = dateFormats.getOrDefault(fieldID, SDF);
                CompiledRule rule = new CompiledRule(
                        values.get(fieldID),
                        clauses.get(fieldID),
                        separators.get(fieldID),
                        dateFormat
                );
                compiled.computeIfAbsent(fieldIndex, key -> new ArrayList<>(2)).add(rule);
            }
        }
        return compiled;
    }

    private List<Integer> compileDistinctFieldIndexes(FilterQueryBuilder builder) {
        List<Map.Entry<Integer, String>> entries = sortedIntegerEntries(builder.getDistinctFields());
        List<Integer> indexes = new ArrayList<>(entries.size());
        for (Map.Entry<Integer, String> entry : entries) {
            int fieldIndex = findFieldIndex(entry.getValue());
            if (fieldIndex >= 0) {
                indexes.add(fieldIndex);
            }
        }
        return indexes;
    }

    private List<OrderColumn> compileOrderColumns(FilterQueryBuilder builder) {
        List<Map.Entry<Integer, String>> entries = sortedIntegerEntries(builder.getOrderFields());
        List<OrderColumn> columns = new ArrayList<>(entries.size());
        for (Map.Entry<Integer, String> entry : entries) {
            int fieldIndex = findFieldIndexIgnoreCase(entry.getValue());
            if (fieldIndex < 0) {
                continue;
            }
            String dateFormat = builder.getFilterDateFormats().getOrDefault(Integer.toString(entry.getKey()), SDF);
            columns.add(new OrderColumn(fieldIndex, dateFormat));
        }
        return columns;
    }

    private List<GroupColumn> compileGroupColumns(FilterQueryBuilder builder) {
        List<Map.Entry<Integer, String>> entries = sortedIntegerEntries(builder.getGroupFields());
        List<GroupColumn> columns = new ArrayList<>(entries.size());
        for (Map.Entry<Integer, String> entry : entries) {
            String fieldName = entry.getValue();
            String dateFormat = builder.getFilterDateFormats().getOrDefault(Integer.toString(entry.getKey()), SDF);
            QueryTimeBucket bucket = builder.getTimeBuckets().get(fieldName);
            if (bucket != null) {
                int dateFieldIndex = findFieldIndex(bucket.getDateField());
                if (dateFieldIndex >= 0) {
                    columns.add(new GroupColumn(fieldName, dateFieldIndex, dateFormat, bucket.getPreset()));
                }
                continue;
            }

            int fieldIndex = findFieldIndex(fieldName);
            if (fieldIndex >= 0) {
                columns.add(new GroupColumn(fieldName, fieldIndex, dateFormat, null));
            }
        }
        return columns;
    }

    private List<MetricPlan> compileMetricPlans(FilterQueryBuilder builder) {
        List<QueryMetric> configuredMetrics = builder.getMetrics();
        List<MetricPlan> plans = new ArrayList<>(configuredMetrics.size());
        for (QueryMetric metric : configuredMetrics) {
            int fieldIndex = Metric.COUNT.equals(metric.getMetric()) ? -1 : findFieldIndex(metric.getField());
            plans.add(new MetricPlan(metric.getField(), metric.getMetric(), metric.getAlias(), fieldIndex));
        }
        return plans;
    }

    private static List<Map.Entry<Integer, String>> sortedIntegerEntries(Map<Integer, String> configuredFields) {
        List<Map.Entry<Integer, String>> entries = new ArrayList<>(configuredFields.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        return entries;
    }

    private static Map<Integer, List<CompiledRule>> freezeCompiledRules(Map<Integer, List<CompiledRule>> compiled) {
        if (compiled.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<CompiledRule>> frozen = new LinkedHashMap<>(compiled.size());
        for (Map.Entry<Integer, List<CompiledRule>> entry : compiled.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static List<String> fieldNames(List<QueryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<? extends QueryField> firstFields = rows.get(0).getFields();
        if (firstFields == null || firstFields.isEmpty()) {
            return List.of();
        }
        ArrayList<String> names = new ArrayList<>(firstFields.size());
        for (int i = 0; i < firstFields.size(); i++) {
            QueryField field = firstFields.get(i);
            names.add(field == null || field.getFieldName() == null ? "" : field.getFieldName());
        }
        return List.copyOf(names);
    }

    static record OrderColumn(int fieldIndex, String dateFormat) {
    }

    static record GroupColumn(String fieldName, int fieldIndex, String dateFormat, TimeBucketPreset timeBucket) {
    }

    static record MetricPlan(String fieldName, Metric metric, String alias, int fieldIndex) {
    }
}

