package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.RawQueryRow;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Fast path for POJO-source simple filter queries.
 * <p>
 * For queries against a POJO source with no joins, stats, computed fields, or
 * explicit rule groups, this path evaluates filter rules directly against POJO
 * objects using the cached field-read plan, materializing {@link QueryRow}
 * objects only for rows that pass the filter.  This avoids the O(n) allocation
 * cost of the standard {@code toDomainRows} path which materializes every row
 * before any filtering is applied.
 */
final class FastPojoFilterSupport {

    private FastPojoFilterSupport() {}

    /**
     * Returns true when the builder's shape is compatible with the fast POJO
     * filter path: POJO source, no joins, no stats, no computed fields, no
     * explicit rule groups, and at least one filter rule to evaluate.
     */
    static boolean isApplicable(FilterQueryBuilder builder) {
        List<?> sourceBeans = builder.getSourceBeansForExecution();
        if (sourceBeans == null || sourceBeans.isEmpty()) {
            return false;
        }
        if (CollectionUtil.firstNonNull(sourceBeans) instanceof QueryRow) {
            return false;
        }
        return builder.getJoinClasses().isEmpty()
                && builder.getJoinSourceBeansForExecution().isEmpty()
                && builder.getMetrics().isEmpty()
                && builder.getGroupFields().isEmpty()
                && builder.getTimeBuckets().isEmpty()
                && builder.getAllOfGroups().isEmpty()
                && builder.getAnyOfGroups().isEmpty()
                && builder.getHavingAllOfGroups().isEmpty()
                && builder.getHavingAnyOfGroups().isEmpty()
                && builder.getComputedFieldRegistry().isEmpty()
                && !builder.getFilterFields().isEmpty();
    }

    /**
     * Materializes only POJO rows that pass the configured filter rules,
     * without creating {@link QueryRow} objects for non-matching rows.
     * Returns {@code null} to signal fallback to the normal execution path.
     */
    static List<QueryRow> tryFilterRows(FilterQueryBuilder builder) {
        return tryFilterRows(builder, null);
    }

    /**
     * Materializes only POJO rows that pass the configured filter rules from
     * the provided candidate source rows.
     */
    static List<QueryRow> tryFilterRows(FilterQueryBuilder builder, List<?> candidateSourceBeans) {
        List<?> sourceBeans = candidateSourceBeans == null
                ? builder.getSourceBeansForExecution()
                : candidateSourceBeans;
        Object firstBean = CollectionUtil.firstNonNull(sourceBeans);
        if (firstBean == null) {
            return new ArrayList<>(0);
        }

        List<String> fieldNames = requiredReadFieldNames(builder);
        if (fieldNames.isEmpty()) {
            return null;
        }

        FilterExecutionPlan plan = FilterExecutionPlan.forSchema(builder, fieldNames);
        Map<Integer, List<CompiledRule>> rulesByField = plan.getRulesByFieldIndex();
        if (rulesByField.isEmpty()) {
            return null;
        }

        ReflectionUtil.FlatRowReadPlan readPlan =
                ReflectionUtil.compileFlatRowReadPlan(firstBean.getClass(), fieldNames);
        List<String> readFieldNames = readPlan.fieldNames();
        CompiledRuleBundle ruleBundle = compileRuleBundle(rulesByField, readPlan.size());
        Object[] buffer = new Object[readPlan.size()];
        List<QueryRow> matched = new ArrayList<>();

        for (int i = 0; i < sourceBeans.size(); i++) {
            Object bean = sourceBeans.get(i);
            if (bean == null) {
                continue;
            }
            try {
                ReflectionUtil.readFlatRowValues(bean, readPlan, buffer, 0);
            } catch (IllegalAccessException e) {
                return null;
            }
            if (passesFilter(buffer, ruleBundle)) {
                matched.add(toQueryRow(buffer, readFieldNames));
            }
        }
        return matched;
    }

    /**
     * Returns only the source field names that are actually needed: filter fields,
     * order fields, display/return fields, and distinct fields. Reading fewer fields
     * per row reduces the reflection overhead in the hot loop.
     * Falls back to the full source schema if no specific fields can be identified.
     */
    private static List<String> requiredReadFieldNames(FilterQueryBuilder builder) {
        Map<String, Class<?>> sourceFieldTypes = builder.getSourceFieldTypesForExecution();
        if (sourceFieldTypes.isEmpty()) {
            return List.of();
        }

        if (builder.getReturnFields().isEmpty()) {
            return new ArrayList<>(sourceFieldTypes.keySet());
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        addKnownFields(selected, sourceFieldTypes, builder.getFilterFields().values());
        addKnownFields(selected, sourceFieldTypes, builder.getOrderFields().values());
        addKnownFields(selected, sourceFieldTypes, builder.getReturnFields());
        addKnownFields(selected, sourceFieldTypes, builder.getDistinctFields().values());

        if (selected.isEmpty()) {
            return new ArrayList<>(sourceFieldTypes.keySet());
        }

        ArrayList<String> ordered = new ArrayList<>(selected.size());
        for (String fieldName : sourceFieldTypes.keySet()) {
            if (selected.contains(fieldName)) {
                ordered.add(fieldName);
            }
        }
        return ordered.isEmpty() ? new ArrayList<>(sourceFieldTypes.keySet()) : ordered;
    }

    private static void addKnownFields(LinkedHashSet<String> selected,
                                       Map<String, Class<?>> sourceFieldTypes,
                                       Iterable<String> candidateFieldNames) {
        for (String fieldName : candidateFieldNames) {
            if (sourceFieldTypes.containsKey(fieldName)) {
                selected.add(fieldName);
            }
        }
    }

    private static CompiledRuleBundle compileRuleBundle(Map<Integer, List<CompiledRule>> rulesByField,
                                                        int valueCount) {
        int validCount = 0;
        for (Map.Entry<Integer, List<CompiledRule>> entry : rulesByField.entrySet()) {
            int fieldIndex = entry.getKey();
            List<CompiledRule> rules = entry.getValue();
            if (fieldIndex >= 0 && fieldIndex < valueCount && rules != null && !rules.isEmpty()) {
                validCount++;
            }
        }
        if (validCount == 0) {
            return new CompiledRuleBundle(new int[0], new CompiledRule[0][]);
        }

        int[] fieldIndexes = new int[validCount];
        CompiledRule[][] compiledRules = new CompiledRule[validCount][];
        int position = 0;
        for (Map.Entry<Integer, List<CompiledRule>> entry : rulesByField.entrySet()) {
            int fieldIndex = entry.getKey();
            List<CompiledRule> rules = entry.getValue();
            if (fieldIndex < 0 || fieldIndex >= valueCount || rules == null || rules.isEmpty()) {
                continue;
            }
            fieldIndexes[position] = fieldIndex;
            compiledRules[position] = rules.toArray(new CompiledRule[0]);
            position++;
        }
        return new CompiledRuleBundle(fieldIndexes, compiledRules);
    }

    private static boolean passesFilter(Object[] values,
                                        CompiledRuleBundle rulesByField) {
        boolean andMatched = false;
        boolean andFailed = false;
        boolean orMatched = false;

        outer:
        for (int i = 0; i < rulesByField.fieldIndexes().length; i++) {
            int fieldIndex = rulesByField.fieldIndexes()[i];
            Object fieldValue = values[fieldIndex];
            CompiledRule[] rules = rulesByField.compiledRules()[i];
            for (CompiledRule rule : rules) {
                boolean matched = ObjectUtil.compareObject(
                        fieldValue, rule.compareValue, rule.clause, rule.dateFormat);
                if (Separator.AND.equals(rule.separator)) {
                    if (matched) {
                        andMatched = true;
                    } else {
                        andFailed = true;
                    }
                } else if (Separator.OR.equals(rule.separator) && matched) {
                    orMatched = true;
                }
                if (andFailed && orMatched) {
                    break outer;
                }
            }
        }
        return (andMatched && !andFailed) || orMatched;
    }

    private static QueryRow toQueryRow(Object[] values, List<String> fieldNames) {
        return new RawQueryRow(values.clone(), fieldNames);
    }

    private record CompiledRuleBundle(int[] fieldIndexes, CompiledRule[][] compiledRules) {
    }
}
