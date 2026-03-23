package laughing.man.commits.sqllike.internal.qualify;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryRule;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.filter.FilterCore;
import laughing.man.commits.filter.FilterExecutionPlan;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.internal.expression.BooleanExpressionNormalizer;
import laughing.man.commits.sqllike.internal.params.BoundParameterValue;

import java.util.List;

/**
 * Internal helper for SQL-like QUALIFY filtering over post-window rows.
 */
public final class SqlLikeQualifySupport {

    private static final int MAX_BOOLEAN_DNF_GROUPS = 2048;

    private SqlLikeQualifySupport() {
    }

    public static List<QueryRow> apply(List<QueryRow> rows,
                                       FilterExpressionAst qualifyExpression,
                                       List<FilterAst> qualifyFilters) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        boolean hasExpression = qualifyExpression != null;
        boolean hasFilters = qualifyFilters != null && !qualifyFilters.isEmpty();
        if (!hasExpression && !hasFilters) {
            return rows;
        }

        FilterQueryBuilder builder = new FilterQueryBuilder(rows).copyOnBuild(false);
        if (hasExpression) {
            List<List<FilterAst>> groups = BooleanExpressionNormalizer.toDnf(qualifyExpression, MAX_BOOLEAN_DNF_GROUPS);
            for (List<FilterAst> group : groups) {
                QueryRule[] rules = new QueryRule[group.size()];
                for (int i = 0; i < group.size(); i++) {
                    FilterAst filter = group.get(i);
                    rules[i] = QueryRule.of(filter.field(), unwrap(filter.value()), filter.clause());
                }
                builder.allOf(rules);
            }
        } else {
            for (FilterAst filter : qualifyFilters) {
                Separator separator = filter.separator() == null ? Separator.AND : filter.separator();
                builder.addRule(filter.field(), unwrap(filter.value()), filter.clause(), separator);
            }
        }

        FilterCore core = new FilterCore(builder);
        if (builder.requiresRuntimeSchemaCleaning()) {
            core.clean(rows.get(0));
        }
        FilterExecutionPlan plan = core.buildExecutionPlan();
        return core.filterFields(rows, plan);
    }

    private static Object unwrap(Object value) {
        if (value instanceof BoundParameterValue boundParameterValue) {
            return boundParameterValue.value();
        }
        return value;
    }
}
