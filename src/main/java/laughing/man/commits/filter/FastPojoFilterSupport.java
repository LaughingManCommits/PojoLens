package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
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
        List<?> sourceBeans = builder.getSourceBeansForExecution();
        Object firstBean = CollectionUtil.firstNonNull(sourceBeans);
        if (firstBean == null) {
            return new ArrayList<>(0);
        }

        List<String> fieldNames = new ArrayList<>(builder.getSourceFieldTypesForExecution().keySet());
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
            if (passesFilter(buffer, rulesByField)) {
                matched.add(toQueryRow(buffer, readFieldNames));
            }
        }
        return matched;
    }

    private static boolean passesFilter(Object[] values,
                                        Map<Integer, List<CompiledRule>> rulesByField) {
        boolean andMatched = false;
        boolean andFailed = false;
        boolean orMatched = false;

        outer:
        for (Map.Entry<Integer, List<CompiledRule>> ruleEntry : rulesByField.entrySet()) {
            int fieldIndex = ruleEntry.getKey();
            if (fieldIndex < 0 || fieldIndex >= values.length) {
                continue;
            }
            List<CompiledRule> rules = ruleEntry.getValue();
            if (rules == null || rules.isEmpty()) {
                continue;
            }
            Object fieldValue = values[fieldIndex];
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
        List<QueryField> fields = new ArrayList<>(fieldNames.size());
        for (int i = 0; i < fieldNames.size(); i++) {
            QueryField field = new QueryField();
            field.setFieldName(fieldNames.get(i));
            field.setValue(values[i]);
            fields.add(field);
        }
        QueryRow row = new QueryRow();
        row.setFields(fields);
        return row;
    }
}
