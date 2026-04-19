package laughing.man.commits.sqllike.internal.preview;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.sqllike.PlanPreviewField;
import laughing.man.commits.sqllike.PlanPreviewFilter;
import laughing.man.commits.sqllike.PlanPreviewJoin;
import laughing.man.commits.sqllike.PlanPreviewOrder;
import laughing.man.commits.sqllike.PlanPreviewPaging;
import laughing.man.commits.sqllike.PlanPreviewPredicate;
import laughing.man.commits.sqllike.SqlLikePlanPreview;
import laughing.man.commits.sqllike.ast.ExistsSubqueryValueAst;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.JoinAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.ParameterValueAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.sqllike.internal.diagnostics.SqlLikeDiagnosticsSupport;
import laughing.man.commits.sqllike.internal.params.SqlLikeParameterSupport;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal helpers for building {@link SqlLikePlanPreview} from SQL-like AST metadata.
 */
public final class SqlLikePlanPreviewSupport {

    private SqlLikePlanPreviewSupport() {
    }

    public static SqlLikePlanPreview buildFromAst(QueryAst ast, String querySource) {
        String source = resolveSource(ast, querySource);
        boolean wildcard = isWildcard(ast);
        List<PlanPreviewField> selectFields = buildSelectFields(ast);
        List<PlanPreviewFilter> filters = collectFilters(ast.filters(), ast.whereExpression());
        PlanPreviewPredicate filterExpression = buildPredicate(ast.whereExpression());
        List<String> groupByFields = new ArrayList<>(ast.groupByFields());
        List<PlanPreviewFilter> havingFilters = collectFilters(ast.havingFilters(), ast.havingExpression());
        PlanPreviewPredicate havingExpression = buildPredicate(ast.havingExpression());
        List<PlanPreviewFilter> qualifyFilters = collectFilters(ast.qualifyFilters(), ast.qualifyExpression());
        PlanPreviewPredicate qualifyExpression = buildPredicate(ast.qualifyExpression());
        List<PlanPreviewOrder> orderFields = buildOrderFields(ast.orders());
        List<PlanPreviewJoin> joins = buildJoins(ast.joins());
        PlanPreviewPaging paging = buildPaging(ast);
        List<String> requiredParams = new ArrayList<>(SqlLikeParameterSupport.collectParameterNames(ast));
        boolean hasSubqueries = SqlLikeDiagnosticsSupport.hasSubqueries(ast);
        return new SqlLikePlanPreview(
                source,
                wildcard,
                selectFields,
                filters,
                filterExpression,
                groupByFields,
                havingFilters,
                havingExpression,
                qualifyFilters,
                qualifyExpression,
                orderFields,
                joins,
                paging,
                requiredParams,
                hasSubqueries
        );
    }

    private static String resolveSource(QueryAst ast, String querySource) {
        SelectAst select = ast.select();
        if (select != null && select.sourceName() != null) {
            return select.sourceName();
        }
        return querySource != null ? querySource : "";
    }

    private static boolean isWildcard(QueryAst ast) {
        return ast.select() == null || ast.select().wildcard();
    }

    private static List<PlanPreviewField> buildSelectFields(QueryAst ast) {
        if (isWildcard(ast)) {
            return List.of();
        }
        List<PlanPreviewField> result = new ArrayList<>();
        for (SelectFieldAst f : ast.select().fields()) {
            result.add(buildSelectField(f));
        }
        return result;
    }

    private static PlanPreviewField buildSelectField(SelectFieldAst f) {
        String metric = f.metric() != null ? f.metric().name() : null;
        String timeBucket = f.timeBucketPreset() != null ? f.timeBucketPreset().bucket().name() : null;
        String windowFunction = f.windowFunction();
        List<String> windowPartitionFields = f.windowPartitionFields();
        List<String> windowOrderFields = new ArrayList<>();
        for (OrderAst o : f.windowOrderFields()) {
            windowOrderFields.add(o.field());
        }
        String windowFrame = windowFunction != null ? f.windowFrame().sqlExpression() : null;
        return new PlanPreviewField(
                f.field(),
                f.outputName(),
                f.alias(),
                metric,
                timeBucket,
                windowFunction,
                windowPartitionFields,
                windowOrderFields,
                windowFrame,
                f.computedField(),
                f.countAll()
        );
    }

    private static List<PlanPreviewFilter> collectFilters(List<FilterAst> flatList,
                                                          FilterExpressionAst expression) {
        List<PlanPreviewFilter> filters = new ArrayList<>();
        if (expression != null) {
            collectFromExpression(expression, filters);
            return filters;
        }
        for (FilterAst f : flatList) {
            filters.add(buildPreviewFilter(f));
        }
        return filters;
    }

    private static void collectFromExpression(FilterExpressionAst expression,
                                              List<PlanPreviewFilter> filters) {
        if (expression == null) {
            return;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            filters.add(buildPreviewFilter(predicateAst.filter()));
            return;
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        collectFromExpression(binary.left(), filters);
        collectFromExpression(binary.right(), filters);
    }

    private static PlanPreviewPredicate buildPredicate(FilterExpressionAst expression) {
        if (expression == null) {
            return null;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            return new PlanPreviewPredicate(buildPreviewFilter(predicateAst.filter()), null, List.of());
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        return new PlanPreviewPredicate(
                null,
                binary.operator().name(),
                List.of(buildPredicate(binary.left()), buildPredicate(binary.right()))
        );
    }

    private static PlanPreviewFilter buildPreviewFilter(FilterAst f) {
        Object value = f.value();
        String operator;
        String valueKind;
        String paramName = null;
        SqlLikePlanPreview subqueryPreview = null;

        if (value instanceof ExistsSubqueryValueAst existsAst) {
            operator = existsAst.negated() ? "NOT EXISTS" : "EXISTS";
            valueKind = "EXISTS_SUBQUERY";
            subqueryPreview = buildFromAst(existsAst.query(), existsAst.source());
        } else if (value instanceof SubqueryValueAst subqueryAst) {
            operator = clauseOperator(f.clause());
            valueKind = "SUBQUERY";
            subqueryPreview = buildFromAst(subqueryAst.query(), subqueryAst.source());
        } else if (value instanceof ParameterValueAst paramAst) {
            operator = clauseOperator(f.clause());
            valueKind = "PARAMETER";
            paramName = paramAst.name();
        } else {
            operator = clauseOperator(f.clause());
            valueKind = "LITERAL";
        }

        return new PlanPreviewFilter(f.field(), operator, valueKind, paramName, subqueryPreview);
    }

    private static String clauseOperator(Clauses clause) {
        return switch (clause) {
            case EQUAL -> "=";
            case NOT_EQUAL -> "!=";
            case SMALLER -> "<";
            case BIGGER -> ">";
            case SMALLER_EQUAL, NOT_BIGGER -> "<=";
            case BIGGER_EQUAL, NOT_SMALLER -> ">=";
            case CONTAINS -> "CONTAINS";
            case MATCHES -> "MATCHES";
            case IN -> "IN";
        };
    }

    private static List<PlanPreviewOrder> buildOrderFields(List<OrderAst> orders) {
        List<PlanPreviewOrder> result = new ArrayList<>(orders.size());
        for (OrderAst o : orders) {
            result.add(new PlanPreviewOrder(o.field(), o.sort().name()));
        }
        return result;
    }

    private static List<PlanPreviewJoin> buildJoins(List<JoinAst> joins) {
        List<PlanPreviewJoin> result = new ArrayList<>(joins.size());
        for (JoinAst j : joins) {
            result.add(new PlanPreviewJoin(joinTypeName(j.joinType()), j.childSource(),
                    j.parentField(), j.childField()));
        }
        return result;
    }

    private static String joinTypeName(Join joinType) {
        return switch (joinType) {
            case INNER_JOIN -> "INNER";
            case LEFT_JOIN -> "LEFT";
            case RIGHT_JOIN -> "RIGHT";
        };
    }

    private static PlanPreviewPaging buildPaging(QueryAst ast) {
        if (!ast.hasLimitClause() && !ast.hasOffsetClause()) {
            return null;
        }
        return new PlanPreviewPaging(ast.limit(), ast.limitParameter(), ast.offset(), ast.offsetParameter());
    }
}
