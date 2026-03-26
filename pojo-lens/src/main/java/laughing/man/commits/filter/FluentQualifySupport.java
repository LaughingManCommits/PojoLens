package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryRule;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal helper for fluent QUALIFY filtering over post-window rows.
 */
final class FluentQualifySupport {

    private FluentQualifySupport() {
    }

    static boolean hasPredicates(FilterQueryBuilder builder) {
        return !builder.getQualifyFields().isEmpty()
                || !builder.getQualifyAllOfGroups().isEmpty()
                || !builder.getQualifyAnyOfGroups().isEmpty();
    }

    static void validate(FilterQueryBuilder builder) {
        if (!hasPredicates(builder)) {
            return;
        }
        if (!builder.getMetrics().isEmpty() || !builder.getGroupFields().isEmpty() || !builder.getTimeBuckets().isEmpty()) {
            throw new IllegalArgumentException("QUALIFY is only supported for non-aggregate fluent queries");
        }
        if (builder.getWindows().isEmpty()) {
            throw new IllegalArgumentException("QUALIFY requires at least one window output");
        }
        Set<String> aliases = new LinkedHashSet<>();
        builder.getWindows().forEach(window -> aliases.add(window.alias()));
        validateRuleColumns(builder.getQualifyFields(), aliases);
        validateRuleGroups(builder.getQualifyAllOfGroups(), aliases);
        validateRuleGroups(builder.getQualifyAnyOfGroups(), aliases);
    }

    static List<QueryRow> apply(FilterQueryBuilder builder, List<QueryRow> rows) {
        if (rows == null || rows.isEmpty() || !hasPredicates(builder)) {
            return rows;
        }
        FilterQueryBuilder qualifyBuilder = new FilterQueryBuilder(rows).copyOnBuild(false);
        for (Map.Entry<String, String> fieldEntry : builder.getQualifyFields().entrySet()) {
            String ruleId = fieldEntry.getKey();
            qualifyBuilder.addRule(
                    fieldEntry.getValue(),
                    builder.getQualifyValues().get(ruleId),
                    builder.getQualifyClause().get(ruleId),
                    builder.getQualifySeparator().getOrDefault(ruleId, Separator.AND),
                    builder.getQualifyDateFormats().get(ruleId)
            );
        }
        for (List<QueryRule> group : builder.getQualifyAllOfGroups()) {
            qualifyBuilder.allOf(group.toArray(new QueryRule[0]));
        }
        for (List<QueryRule> group : builder.getQualifyAnyOfGroups()) {
            qualifyBuilder.anyOf(group.toArray(new QueryRule[0]));
        }
        FilterCore core = new FilterCore(qualifyBuilder);
        if (qualifyBuilder.requiresRuntimeSchemaCleaning()) {
            core.clean(rows.get(0));
        }
        FilterExecutionPlan plan = core.buildExecutionPlan();
        return core.filterFields(rows, plan);
    }

    private static void validateRuleColumns(Map<String, String> ruleFields, Set<String> allowedAliases) {
        for (String field : ruleFields.values()) {
            validateColumn(field, allowedAliases);
        }
    }

    private static void validateRuleGroups(List<List<QueryRule>> groups, Set<String> allowedAliases) {
        for (List<QueryRule> group : groups) {
            for (QueryRule rule : group) {
                if (rule == null) {
                    continue;
                }
                validateColumn(rule.getColumn(), allowedAliases);
            }
        }
    }

    private static void validateColumn(String column, Set<String> allowedAliases) {
        if (column == null) {
            return;
        }
        if (!SqlExpressionEvaluator.looksLikeExpression(column)) {
            if (!allowedAliases.contains(column)) {
                throw new IllegalArgumentException(formatUnknownQualifyMessage(column, allowedAliases));
            }
            return;
        }
        Set<String> identifiers = SqlExpressionEvaluator.collectIdentifiers(column);
        for (String identifier : identifiers) {
            if (!allowedAliases.contains(identifier)) {
                throw new IllegalArgumentException(formatUnknownQualifyMessage(identifier, allowedAliases));
            }
        }
    }

    private static String formatUnknownQualifyMessage(String field, Set<String> allowedAliases) {
        return "Unknown field '" + field + "' in QUALIFY clause. Allowed fields: " + allowedAliases;
    }
}
