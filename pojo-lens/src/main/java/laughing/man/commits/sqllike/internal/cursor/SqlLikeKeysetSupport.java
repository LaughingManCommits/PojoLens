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
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Internal helper that transforms keyset cursor values into SQL-like AST predicates
 * aligned with query ORDER BY definitions.
 *
 * <p>The cursor predicate lands where the ORDER BY fields are visible: HAVING for grouped
 * or aggregate queries (aliases and aggregates), QUALIFY when ordering by a window alias,
 * otherwise WHERE (select aliases rewritten to their source field or expression). It is
 * null-aware, matching the engine's null placement (first in ASC, last in DESC), and a
 * cursor value may itself be null.
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
        }

        Placement placement = placementOf(ast);
        FilterExpressionAst cursorExpression = buildCursorExpression(
                orders, predicateFields(orders, ast, placement), values, direction);
        FilterExpressionAst where = ast.whereExpression();
        FilterExpressionAst having = ast.havingExpression();
        FilterExpressionAst qualify = ast.qualifyExpression();
        switch (placement) {
            case WHERE -> where = and(where, cursorExpression);
            case HAVING -> having = and(having, cursorExpression);
            case QUALIFY -> qualify = and(qualify, cursorExpression);
        }

        return new QueryAst(
                ast.select(),
                ast.joins(),
                placement == Placement.WHERE ? flattenExpression(where) : ast.filters(),
                where,
                ast.groupByFields(),
                placement == Placement.HAVING ? flattenExpression(having) : ast.havingFilters(),
                having,
                placement == Placement.QUALIFY ? flattenExpression(qualify) : ast.qualifyFilters(),
                qualify,
                // Previous page: walk backwards from the cursor so LIMIT keeps the nearest rows;
                // SqlLikeQuery flips the result back to the declared order.
                direction == Direction.BEFORE ? reversedOrders(orders) : ast.orders(),
                ast.limit(),
                ast.limitParameter(),
                ast.offset(),
                ast.offsetParameter()
        );
    }

    private enum Placement {
        WHERE,
        HAVING,
        QUALIFY
    }

    private static Placement placementOf(QueryAst ast) {
        if (!ast.groupByFields().isEmpty() || ast.hasAggregation()) {
            return Placement.HAVING;
        }
        if (ast.select() != null) {
            for (OrderAst order : ast.orders()) {
                for (SelectFieldAst field : ast.select().fields()) {
                    if (field.windowField() && order.field().equals(field.outputName())) {
                        return Placement.QUALIFY;
                    }
                }
            }
        }
        return Placement.WHERE;
    }

    /**
     * Field references for the cursor predicate: WHERE runs before projection, so an
     * ORDER BY select alias becomes the aliased source field or expression.
     */
    private static List<String> predicateFields(List<OrderAst> orders, QueryAst ast, Placement placement) {
        ArrayList<String> fields = new ArrayList<>(orders.size());
        for (OrderAst order : orders) {
            String field = order.field();
            if (placement == Placement.WHERE && ast.select() != null) {
                for (SelectFieldAst selectField : ast.select().fields()) {
                    if (selectField.aliased() && field.equals(selectField.outputName())) {
                        field = selectField.field();
                        break;
                    }
                }
            }
            fields.add(field);
        }
        return fields;
    }

    private static FilterExpressionAst and(FilterExpressionAst existing, FilterExpressionAst added) {
        return existing == null ? added : new FilterBinaryAst(existing, added, Separator.AND);
    }

    private static FilterExpressionAst buildCursorExpression(List<OrderAst> orders,
                                                             List<String> fields,
                                                             Map<String, Object> values,
                                                             Direction direction) {
        FilterExpressionAst root = null;
        for (int i = 0; i < orders.size(); i++) {
            FilterExpressionAst beyond = beyondCursor(
                    fields.get(i), orders.get(i).sort(), values.get(orders.get(i).field()), direction);
            if (beyond == null) {
                continue; // nothing lies beyond this cursor value in the paging direction
            }
            FilterExpressionAst conjunction = null;
            for (int j = 0; j < i; j++) {
                conjunction = and(conjunction, predicate(fields.get(j), Clauses.EQUAL, values.get(orders.get(j).field())));
            }
            conjunction = and(conjunction, beyond);
            root = root == null ? conjunction : new FilterBinaryAst(root, conjunction, Separator.OR);
        }
        if (root == null) {
            // No row can follow the cursor: an always-false predicate on the first key.
            String field = fields.get(0);
            return new FilterBinaryAst(
                    predicate(field, Clauses.EQUAL, null), predicate(field, Clauses.NOT_EQUAL, null), Separator.AND);
        }
        return root;
    }

    /**
     * Rows strictly beyond {@code value} for one ORDER BY key in the paging direction, or
     * {@code null} when none can be. Nulls sort first in ASC and last in DESC, so moving
     * toward the null end also admits null values.
     */
    private static FilterExpressionAst beyondCursor(String field, Sort sort, Object value, Direction direction) {
        boolean nullsFirst = sort == Sort.ASC;
        boolean towardNulls = direction == Direction.AFTER ? !nullsFirst : nullsFirst;
        if (value == null) {
            return towardNulls ? null : predicate(field, Clauses.NOT_EQUAL, null);
        }
        FilterExpressionAst beyond = predicate(field, comparisonClause(sort, direction), value);
        return towardNulls
                ? new FilterBinaryAst(beyond, predicate(field, Clauses.EQUAL, null), Separator.OR)
                : beyond;
    }

    private static FilterExpressionAst predicate(String field, Clauses clause, Object value) {
        return new FilterPredicateAst(new FilterAst(field, clause, value, null));
    }

    private static List<OrderAst> reversedOrders(List<OrderAst> orders) {
        ArrayList<OrderAst> reversed = new ArrayList<>(orders.size());
        for (OrderAst order : orders) {
            reversed.add(new OrderAst(order.field(), order.sort() == Sort.ASC ? Sort.DESC : Sort.ASC));
        }
        return List.copyOf(reversed);
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
        switch (expression) {
            case FilterPredicateAst predicateAst -> {
                FilterAst filter = predicateAst.filter();
                out.add(new FilterAst(filter.field(), filter.clause(), filter.value(), inheritedSeparator));
            }
            case FilterBinaryAst binaryAst -> {
                flatten(binaryAst.left(), out, inheritedSeparator);
                flatten(binaryAst.right(), out, binaryAst.operator());
            }
        }
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
