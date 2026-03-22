package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryMetric;
import laughing.man.commits.builder.QueryRule;
import laughing.man.commits.builder.QueryTimeBucket;
import laughing.man.commits.util.CollectionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable structural cache key for reusable execution plans.
 */
public final class FilterExecutionPlanCacheKey {

    private final List<String> schemaFields;
    private final List<RuleFieldShape> filterRules;
    private final List<RuleFieldShape> havingRules;
    private final List<NamedValue> filterDateFormats;
    private final List<NamedValue> havingDateFormats;
    private final List<NamedValue> groupFields;
    private final List<NamedValue> orderFields;
    private final List<NamedValue> distinctFields;
    private final List<String> returnFields;
    private final List<TimeBucketShape> timeBuckets;
    private final List<MetricShape> metrics;
    private final List<List<RuleShape>> allOfGroups;
    private final List<List<RuleShape>> anyOfGroups;
    private final List<List<RuleShape>> havingAllOfGroups;
    private final List<List<RuleShape>> havingAnyOfGroups;
    private final int weight;
    private final int hashCode;

    private FilterExecutionPlanCacheKey(List<String> schemaFields,
                                        List<RuleFieldShape> filterRules,
                                        List<RuleFieldShape> havingRules,
                                        List<NamedValue> filterDateFormats,
                                        List<NamedValue> havingDateFormats,
                                        List<NamedValue> groupFields,
                                        List<NamedValue> orderFields,
                                        List<NamedValue> distinctFields,
                                        List<String> returnFields,
                                        List<TimeBucketShape> timeBuckets,
                                        List<MetricShape> metrics,
                                        List<List<RuleShape>> allOfGroups,
                                        List<List<RuleShape>> anyOfGroups,
                                        List<List<RuleShape>> havingAllOfGroups,
                                        List<List<RuleShape>> havingAnyOfGroups) {
        this.schemaFields = List.copyOf(schemaFields);
        this.filterRules = List.copyOf(filterRules);
        this.havingRules = List.copyOf(havingRules);
        this.filterDateFormats = List.copyOf(filterDateFormats);
        this.havingDateFormats = List.copyOf(havingDateFormats);
        this.groupFields = List.copyOf(groupFields);
        this.orderFields = List.copyOf(orderFields);
        this.distinctFields = List.copyOf(distinctFields);
        this.returnFields = List.copyOf(returnFields);
        this.timeBuckets = List.copyOf(timeBuckets);
        this.metrics = List.copyOf(metrics);
        this.allOfGroups = freezeRuleGroups(allOfGroups);
        this.anyOfGroups = freezeRuleGroups(anyOfGroups);
        this.havingAllOfGroups = freezeRuleGroups(havingAllOfGroups);
        this.havingAnyOfGroups = freezeRuleGroups(havingAnyOfGroups);
        this.weight = estimateWeight();
        this.hashCode = Objects.hash(
                this.schemaFields,
                this.filterRules,
                this.havingRules,
                this.filterDateFormats,
                this.havingDateFormats,
                this.groupFields,
                this.orderFields,
                this.distinctFields,
                this.returnFields,
                this.timeBuckets,
                this.metrics,
                this.allOfGroups,
                this.anyOfGroups,
                this.havingAllOfGroups,
                this.havingAnyOfGroups
        );
    }

    public static FilterExecutionPlanCacheKey from(FilterQueryBuilder builder) {
        return new FilterExecutionPlanCacheKey(
                new ArrayList<>(builder.getFieldTypes().keySet()),
                directRuleShapes(
                        builder.getFilterIDs(),
                        builder.getFilterFields(),
                        builder.getFilterClause(),
                        builder.getFilterSeparator(),
                        builder.getFilterDateFormats(),
                        builder.getFilterValues()
                ),
                directRuleShapes(
                        builder.getHavingIDs(),
                        builder.getHavingFields(),
                        builder.getHavingClause(),
                        builder.getHavingSeparator(),
                        builder.getHavingDateFormats(),
                        builder.getHavingValues()
                ),
                namedValues(builder.getFilterDateFormats()),
                namedValues(builder.getHavingDateFormats()),
                indexedFieldShapes(builder.getGroupFields()),
                indexedFieldShapes(builder.getOrderFields()),
                indexedFieldShapes(builder.getDistinctFields()),
                builder.getReturnFields(),
                timeBucketShapes(builder.getTimeBuckets()),
                metricShapes(builder.getMetrics()),
                explicitRuleGroups(builder.getAllOfGroups()),
                explicitRuleGroups(builder.getAnyOfGroups()),
                explicitRuleGroups(builder.getHavingAllOfGroups()),
                explicitRuleGroups(builder.getHavingAnyOfGroups())
        );
    }

    int weight() {
        return weight;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FilterExecutionPlanCacheKey that)) {
            return false;
        }
        return hashCode == that.hashCode
                && schemaFields.equals(that.schemaFields)
                && filterRules.equals(that.filterRules)
                && havingRules.equals(that.havingRules)
                && filterDateFormats.equals(that.filterDateFormats)
                && havingDateFormats.equals(that.havingDateFormats)
                && groupFields.equals(that.groupFields)
                && orderFields.equals(that.orderFields)
                && distinctFields.equals(that.distinctFields)
                && returnFields.equals(that.returnFields)
                && timeBuckets.equals(that.timeBuckets)
                && metrics.equals(that.metrics)
                && allOfGroups.equals(that.allOfGroups)
                && anyOfGroups.equals(that.anyOfGroups)
                && havingAllOfGroups.equals(that.havingAllOfGroups)
                && havingAnyOfGroups.equals(that.havingAnyOfGroups);
    }

    private int estimateWeight() {
        int total = stringWeight(schemaFields);
        total += nestedWeight(filterRules);
        total += nestedWeight(havingRules);
        total += nestedWeight(filterDateFormats);
        total += nestedWeight(havingDateFormats);
        total += nestedWeight(groupFields);
        total += nestedWeight(orderFields);
        total += nestedWeight(distinctFields);
        total += stringWeight(returnFields);
        total += nestedWeight(timeBuckets);
        total += nestedWeight(metrics);
        total += nestedGroupWeight(allOfGroups);
        total += nestedGroupWeight(anyOfGroups);
        total += nestedGroupWeight(havingAllOfGroups);
        total += nestedGroupWeight(havingAnyOfGroups);
        return Math.max(1, total);
    }

    private static List<List<RuleShape>> freezeRuleGroups(List<List<RuleShape>> groups) {
        ArrayList<List<RuleShape>> frozen = new ArrayList<>(groups.size());
        for (List<RuleShape> group : groups) {
            frozen.add(List.copyOf(group));
        }
        return List.copyOf(frozen);
    }

    private static List<RuleFieldShape> directRuleShapes(Map<String, List<String>> idsByField,
                                                         Map<String, String> configuredFields,
                                                         Map<String, ?> clauses,
                                                         Map<String, ?> separators,
                                                         Map<String, String> dateFormats,
                                                         Map<String, Object> values) {
        ArrayList<RuleFieldShape> fields = new ArrayList<>(idsByField.size());
        List<Map.Entry<String, List<String>>> entries = CollectionUtil.sortedEntriesByKey(idsByField);
        for (Map.Entry<String, List<String>> entry : entries) {
            ArrayList<RuleShape> rules = new ArrayList<>();
            for (String ruleId : entry.getValue()) {
                String configuredField = configuredFields.get(ruleId);
                if (!entry.getKey().equals(configuredField)) {
                    continue;
                }
                rules.add(new RuleShape(
                        configuredField,
                        String.valueOf(clauses.get(ruleId)),
                        String.valueOf(separators.get(ruleId)),
                        dateFormats.get(ruleId),
                        valueShape(values.get(ruleId))
                ));
            }
            fields.add(new RuleFieldShape(entry.getKey(), rules));
        }
        return fields;
    }

    private static List<NamedValue> indexedFieldShapes(Map<Integer, String> fields) {
        ArrayList<NamedValue> entries = new ArrayList<>(fields.size());
        List<Map.Entry<Integer, String>> sorted = CollectionUtil.sortedEntriesByKey(fields);
        for (Map.Entry<Integer, String> entry : sorted) {
            entries.add(new NamedValue(String.valueOf(entry.getKey()), entry.getValue()));
        }
        return entries;
    }

    private static List<NamedValue> namedValues(Map<String, String> values) {
        ArrayList<NamedValue> entries = new ArrayList<>(values.size());
        List<Map.Entry<String, String>> sorted = CollectionUtil.sortedEntriesByKey(values);
        for (Map.Entry<String, String> entry : sorted) {
            entries.add(new NamedValue(entry.getKey(), entry.getValue()));
        }
        return entries;
    }

    private static List<TimeBucketShape> timeBucketShapes(Map<String, QueryTimeBucket> buckets) {
        ArrayList<TimeBucketShape> shapes = new ArrayList<>(buckets.size());
        List<Map.Entry<String, QueryTimeBucket>> sorted = CollectionUtil.sortedEntriesByKey(buckets);
        for (Map.Entry<String, QueryTimeBucket> entry : sorted) {
            QueryTimeBucket bucket = entry.getValue();
            shapes.add(new TimeBucketShape(entry.getKey(), bucket.getDateField(), bucket.getPreset().explainToken()));
        }
        return shapes;
    }

    private static List<MetricShape> metricShapes(List<QueryMetric> metrics) {
        ArrayList<MetricShape> shapes = new ArrayList<>(metrics.size());
        for (QueryMetric metric : metrics) {
            shapes.add(new MetricShape(
                    String.valueOf(metric.getMetric()),
                    metric.getField(),
                    metric.getAlias()
            ));
        }
        return shapes;
    }

    private static List<List<RuleShape>> explicitRuleGroups(List<List<QueryRule>> groups) {
        ArrayList<List<RuleShape>> shapes = new ArrayList<>(groups.size());
        for (List<QueryRule> group : groups) {
            ArrayList<RuleShape> groupShapes = new ArrayList<>(group.size());
            for (QueryRule rule : group) {
                groupShapes.add(new RuleShape(
                        rule.getColumn(),
                        String.valueOf(rule.getClause()),
                        null,
                        rule.getDateFormat(),
                        valueShape(rule.getValue())
                ));
            }
            shapes.add(groupShapes);
        }
        return shapes;
    }

    private static ValueShape valueShape(Object value) {
        if (value == null) {
            return new ValueShape("null", null);
        }
        if (value instanceof Enum<?>) {
            return new ValueShape(value.getClass().getName(), ((Enum<?>) value).name());
        }
        return new ValueShape(value.getClass().getName(), String.valueOf(value));
    }

    private static int stringWeight(List<String> values) {
        int total = 0;
        for (String value : values) {
            total += value == null ? 4 : value.length();
        }
        return total;
    }

    private static int nestedWeight(List<? extends WeightedComponent> values) {
        int total = 0;
        for (WeightedComponent value : values) {
            total += value.weight();
        }
        return total;
    }

    private static int nestedGroupWeight(List<List<RuleShape>> groups) {
        int total = 0;
        for (List<RuleShape> group : groups) {
            total += nestedWeight(group);
        }
        return total;
    }

    private interface WeightedComponent {
        int weight();
    }

    private record NamedValue(String key, String value) implements WeightedComponent {
        @Override
        public int weight() {
            return length(key) + length(value);
        }
    }

    private record RuleFieldShape(String fieldName, List<RuleShape> rules) implements WeightedComponent {
        private RuleFieldShape(String fieldName, List<RuleShape> rules) {
            this.fieldName = fieldName;
            this.rules = List.copyOf(rules);
        }

        @Override
        public int weight() {
            return length(fieldName) + nestedWeight(rules);
        }
    }

    private record TimeBucketShape(String alias, String dateField, String presetToken) implements WeightedComponent {
        @Override
        public int weight() {
            return length(alias) + length(dateField) + length(presetToken);
        }
    }

    private record MetricShape(String metric, String field, String alias) implements WeightedComponent {
        @Override
        public int weight() {
            return length(metric) + length(field) + length(alias);
        }
    }

    private record RuleShape(String field,
                             String clause,
                             String separator,
                             String dateFormat,
                             ValueShape value) implements WeightedComponent {
        @Override
        public int weight() {
            return length(field) + length(clause) + length(separator) + length(dateFormat) + value.weight();
        }
    }

    private record ValueShape(String typeName, String value) implements WeightedComponent {
        @Override
        public int weight() {
            return length(typeName) + length(value);
        }
    }

    private static int length(String value) {
        return value == null ? 4 : value.length();
    }
}
