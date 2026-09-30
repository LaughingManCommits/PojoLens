package laughing.man.commits.sqllike.internal.expression;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.sqllike.ast.ExistsSubqueryValueAst;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;

import java.util.function.Function;

/**
 * Lowers {@code NOT (...)} into an equivalent expression without negation, using the
 * same De Morgan rules as {@code TypedPredicate.not()}.
 *
 * <p>Negated comparisons keep the null rule: a null field never matches a value
 * comparison, so {@code NOT (x = 1)} and {@code NOT (x > 1)} both exclude null, as in
 * SQL. Null tests flip ({@code = null} and {@code != null}), literal lists flip between
 * {@code IN} and {@code NOT IN}, {@code CONTAINS}/{@code MATCHES} flip to their
 * {@code NOT_} forms, and {@code EXISTS} flips its negated flag.</p>
 */
public final class FilterExpressionNegation {

    public static final String IN_SUBQUERY_MESSAGE =
            "NOT cannot negate an IN subquery; use NOT EXISTS instead";

    private FilterExpressionNegation() {
    }

    /**
     * Returns the negation of {@code expression}.
     *
     * @param errors builds the parse error for predicates that cannot be negated
     */
    public static FilterExpressionAst negate(FilterExpressionAst expression,
                                             Function<String, ? extends IllegalArgumentException> errors) {
        return switch (expression) {
            case FilterPredicateAst predicate -> new FilterPredicateAst(negate(predicate.filter(), errors));
            case FilterBinaryAst binary -> new FilterBinaryAst(
                    negate(binary.left(), errors),
                    negate(binary.right(), errors),
                    binary.operator() == Separator.AND ? Separator.OR : Separator.AND);
        };
    }

    private static FilterAst negate(FilterAst filter, Function<String, ? extends IllegalArgumentException> errors) {
        Object value = filter.value();
        if (value instanceof ExistsSubqueryValueAst exists) {
            ExistsSubqueryValueAst flipped =
                    new ExistsSubqueryValueAst(exists.source(), exists.query(), !exists.negated());
            return new FilterAst(filter.field(), filter.clause(), flipped, null);
        }
        if (filter.clause() == Clauses.IN && value instanceof SubqueryValueAst) {
            throw errors.apply(IN_SUBQUERY_MESSAGE);
        }
        return new FilterAst(filter.field(), negatedClause(filter.clause()), value, null);
    }

    private static Clauses negatedClause(Clauses clause) {
        return switch (clause) {
            case EQUAL -> Clauses.NOT_EQUAL;
            case NOT_EQUAL -> Clauses.EQUAL;
            // IN over a list or list parameter becomes the engine's negated set comparison.
            case IN -> Clauses.NOT_EQUAL;
            case BIGGER -> Clauses.SMALLER_EQUAL;
            case BIGGER_EQUAL, NOT_SMALLER -> Clauses.SMALLER;
            case SMALLER -> Clauses.BIGGER_EQUAL;
            case SMALLER_EQUAL, NOT_BIGGER -> Clauses.BIGGER;
            case CONTAINS -> Clauses.NOT_CONTAINS;
            case NOT_CONTAINS -> Clauses.CONTAINS;
            case MATCHES -> Clauses.NOT_MATCHES;
            case NOT_MATCHES -> Clauses.MATCHES;
        };
    }
}
