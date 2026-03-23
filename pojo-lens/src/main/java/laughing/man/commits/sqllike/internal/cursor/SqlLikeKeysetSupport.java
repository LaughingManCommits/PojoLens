package laughing.man.commits.sqllike.internal.cursor;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.sqllike.SqlLikeCursor;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Internal helper that transforms keyset cursor values into SQL-like where AST
 * predicates aligned with query ORDER BY definitions.
 */
public final class SqlLikeKeysetSupport {

    private SqlLikeKeysetSupport() {
    }

    public static QueryAst applyAfter(QueryAst ast, SqlLikeCursor cursor) {
        return apply(ast, cursor, Direction.AFTER);
    }

    public static QueryAst applyBefore(QueryAst ast, SqlLikeCursor cursor) {
        return apply(ast, cursor, Direction.BEFORE);
    }

    private static QueryAst apply(QueryAst ast, SqlLikeCursor cursor, Direction direction) {
        Objects.requireNonNull(ast, "ast must not be null");
        Objects.requireNonNull(cursor, "cursor must not be null");
        if (ast.orders().isEmpty()) {
            throw cursor(SqlLikeErrorCodes.CURSOR_ORDER_REQUIRED,
                    "Keyset cursor paging requires ORDER BY fields");
        }

        Map<String, Object> values = cursor.values();
        if (values.isEmpty()) {
            throw cursor(SqlLikeErrorCodes.CURSOR_VALUE_INVALID,
                    "Cursor must include at least one field value");
        }

        List<OrderAst> orders = ast.orders();
        if (values.size() != orders.size()) {
            throw cursor(SqlLikeErrorCodes.CURSOR_FIELD_MISMATCH,
                    "Cursor fields must match ORDER BY fields exactly: expected "
                            + orderedFieldNames(orders)
                            + " but received "
                            + values.keySet());
        }

        for (OrderAst order : orders) {
            String field = order.field();
            if (StringUtil.isNullOrBlank(field)) {
                throw cursor(SqlLikeErrorCodes.CURSOR_FIELD_MISMATCH,
                        "ORDER BY field is invalid for keyset cursor paging");
            }
            if (!values.containsKey(field)) {
                throw cursor(SqlLikeErrorCodes.CURSOR_FIELD_MISMATCH,
                        "Cursor is missing ORDER BY field '" + field + "'");
            }
            if (values.get(field) == null) {
                throw cursor(SqlLikeErrorCodes.CURSOR_VALUE_INVALID,
                        "Cursor value for ORDER BY field '" + field + "' must not be null");
            }
        }

        FilterExpressionAst cursorExpression = buildCursorExpression(orders, values, direction);
        FilterExpressionAst existingWhere = ast.whereExpression();
        FilterExpressionAst mergedWhere = existingWhere == null
                ? cursorExpression
                : new FilterBinaryAst(existingWhere, cursorExpression, Separator.AND);
        List<FilterAst> mergedFilters = flattenExpression(mergedWhere);

        return new QueryAst(
                ast.select(),
                ast.joins(),
                mergedFilters,
                mergedWhere,
                ast.groupByFields(),
                ast.havingFilters(),
                ast.havingExpression(),
                ast.qualifyFilters(),
                ast.qualifyExpression(),
                ast.orders(),
                ast.limit(),
                ast.limitParameter(),
                ast.offset(),
                ast.offsetParameter()
        );
    }

    private static FilterExpressionAst buildCursorExpression(List<OrderAst> orders,
                                                             Map<String, Object> values,
                                                             Direction direction) {
        FilterExpressionAst root = null;
        for (int i = 0; i < orders.size(); i++) {
            FilterExpressionAst conjunction = null;
            for (int j = 0; j <= i; j++) {
                OrderAst order = orders.get(j);
                Object value = values.get(order.field());
                Clauses clause = j < i
                        ? Clauses.EQUAL
                        : comparisonClause(order.sort(), direction);
                FilterExpressionAst predicate = new FilterPredicateAst(
                        new FilterAst(order.field(), clause, value, null)
                );
                conjunction = conjunction == null
                        ? predicate
                        : new FilterBinaryAst(conjunction, predicate, Separator.AND);
            }
            root = root == null
                    ? conjunction
                    : new FilterBinaryAst(root, conjunction, Separator.OR);
        }
        return root;
    }

    private static Clauses comparisonClause(Sort sort, Direction direction) {
        if (direction == Direction.AFTER) {
            return sort == Sort.ASC ? Clauses.BIGGER : Clauses.SMALLER;
        }
        return sort == Sort.ASC ? Clauses.SMALLER : Clauses.BIGGER;
    }

    private static List<FilterAst> flattenExpression(FilterExpressionAst expression) {
        ArrayList<FilterAst> filters = new ArrayList<>();
        flatten(expression, filters, null);
        return filters;
    }

    private static void flatten(FilterExpressionAst expression,
                                List<FilterAst> out,
                                Separator inheritedSeparator) {
        if (expression instanceof FilterPredicateAst predicateAst) {
            FilterAst filter = predicateAst.filter();
            out.add(new FilterAst(filter.field(), filter.clause(), filter.value(), inheritedSeparator));
            return;
        }
        FilterBinaryAst binaryAst = (FilterBinaryAst) expression;
        flatten(binaryAst.left(), out, inheritedSeparator);
        flatten(binaryAst.right(), out, binaryAst.operator());
    }

    private static List<String> orderedFieldNames(List<OrderAst> orders) {
        ArrayList<String> names = new ArrayList<>(orders.size());
        for (OrderAst order : orders) {
            names.add(order.field());
        }
        return names;
    }

    private static IllegalArgumentException cursor(String code, String message) {
        return SqlLikeErrors.argument(code, message);
    }

    private enum Direction {
        AFTER,
        BEFORE
    }
}
