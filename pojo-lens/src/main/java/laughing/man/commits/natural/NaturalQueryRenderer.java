package laughing.man.commits.natural;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.ParameterValueAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.internal.params.BoundParameterValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class NaturalQueryRenderer {

    private NaturalQueryRenderer() {
    }

    static String toSqlLike(QueryAst ast) {
        ArrayList<String> clauses = new ArrayList<>();
        SelectAst select = ast.select();
        if (select != null) {
            clauses.add(renderSelect(select));
        }
        if (ast.whereExpression() != null) {
            clauses.add("where " + renderExpression(ast.whereExpression()));
        }
        if (!ast.groupByFields().isEmpty()) {
            clauses.add("group by " + String.join(", ", ast.groupByFields()));
        }
        if (ast.havingExpression() != null) {
            clauses.add("having " + renderExpression(ast.havingExpression()));
        }
        if (!ast.orders().isEmpty()) {
            clauses.add("order by " + renderOrderBy(ast.orders()));
        }
        if (ast.hasLimitClause()) {
            clauses.add("limit " + renderPagination(ast.limit(), ast.limitParameter()));
        }
        if (ast.hasOffsetClause()) {
            clauses.add("offset " + renderPagination(ast.offset(), ast.offsetParameter()));
        }
        return String.join(" ", clauses);
    }

    private static String renderSelect(SelectAst select) {
        if (select.wildcard()) {
            return "select *";
        }
        ArrayList<String> parts = new ArrayList<>(select.fields().size());
        for (SelectFieldAst field : select.fields()) {
            String rendered;
            if (field.metricField()) {
                rendered = field.metric().name().toLowerCase(Locale.ROOT)
                        + "("
                        + (field.countAll() ? "*" : field.field())
                        + ")";
            } else if (field.timeBucketField()) {
                rendered = "bucket(" + field.field() + "," + field.timeBucketPreset().sqlArgumentList() + ")";
            } else {
                rendered = field.field();
            }
            if (field.alias() != null) {
                rendered += " as " + field.alias();
            }
            parts.add(rendered);
        }
        return "select " + String.join(", ", parts);
    }

    private static String renderExpression(FilterExpressionAst expression) {
        if (expression instanceof FilterPredicateAst predicateAst) {
            return renderPredicate(predicateAst.filter());
        }
        FilterBinaryAst binaryAst = (FilterBinaryAst) expression;
        return "("
                + renderExpression(binaryAst.left())
                + " "
                + renderSeparator(binaryAst.operator())
                + " "
                + renderExpression(binaryAst.right())
                + ")";
    }

    private static String renderPredicate(FilterAst filter) {
        return filter.field()
                + " "
                + renderClause(filter.clause())
                + " "
                + renderValue(filter.value());
    }

    private static String renderOrderBy(List<OrderAst> orders) {
        ArrayList<String> rendered = new ArrayList<>(orders.size());
        for (OrderAst order : orders) {
            rendered.add(order.field() + " " + order.sort().name().toLowerCase(Locale.ROOT));
        }
        return String.join(", ", rendered);
    }

    private static String renderPagination(Integer literal, String parameterName) {
        return parameterName == null ? String.valueOf(literal) : ":" + parameterName;
    }

    private static String renderSeparator(Separator separator) {
        return separator.name().toLowerCase(Locale.ROOT);
    }

    private static String renderClause(Clauses clause) {
        return switch (clause) {
            case EQUAL -> "=";
            case NOT_EQUAL -> "!=";
            case BIGGER -> ">";
            case BIGGER_EQUAL -> ">=";
            case SMALLER -> "<";
            case SMALLER_EQUAL -> "<=";
            case CONTAINS -> "contains";
            case MATCHES -> "matches";
            case IN -> "in";
            default -> clause.name().toLowerCase(Locale.ROOT);
        };
    }

    private static String renderValue(Object value) {
        if (value instanceof ParameterValueAst parameterValueAst) {
            return ":" + parameterValueAst.name();
        }
        if (value instanceof BoundParameterValue boundParameterValue) {
            return ":" + boundParameterValue.name();
        }
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return "'" + string.replace("'", "''") + "'";
        }
        if (value instanceof Boolean bool) {
            return bool ? "true" : "false";
        }
        return String.valueOf(value);
    }
}
