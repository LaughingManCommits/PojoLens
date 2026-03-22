package laughing.man.commits.sqllike.internal.expression;

import laughing.man.commits.enums.Separator;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes filter-expression trees into DNF groups.
 */
public final class BooleanExpressionNormalizer {

    private BooleanExpressionNormalizer() {
    }

    public static List<List<FilterAst>> toDnf(FilterExpressionAst expression, int maxGroups) {
        List<List<FilterAst>> groups = toDnfInternal(expression, maxGroups);
        if (groups.size() > maxGroups) {
            throw tooComplex(maxGroups);
        }
        return groups;
    }

    private static List<List<FilterAst>> toDnfInternal(FilterExpressionAst expression, int maxGroups) {
        if (expression instanceof FilterPredicateAst) {
            List<List<FilterAst>> single = new ArrayList<>();
            List<FilterAst> group = new ArrayList<>();
            group.add(((FilterPredicateAst) expression).filter());
            single.add(group);
            return single;
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        List<List<FilterAst>> left = toDnfInternal(binary.left(), maxGroups);
        List<List<FilterAst>> right = toDnfInternal(binary.right(), maxGroups);

        if (binary.operator() == Separator.OR) {
            List<List<FilterAst>> merged = new ArrayList<>(left.size() + right.size());
            merged.addAll(left);
            merged.addAll(right);
            if (merged.size() > maxGroups) {
                throw tooComplex(maxGroups);
            }
            return merged;
        }

        List<List<FilterAst>> product = new ArrayList<>();
        for (List<FilterAst> l : left) {
            for (List<FilterAst> r : right) {
                List<FilterAst> group = new ArrayList<>(l.size() + r.size());
                group.addAll(l);
                group.addAll(r);
                product.add(group);
                if (product.size() > maxGroups) {
                    throw tooComplex(maxGroups);
                }
            }
        }
        return product;
    }

    private static IllegalArgumentException tooComplex(int maxGroups) {
        return SqlLikeErrors.argument(
                SqlLikeErrorCodes.BIND_BOOLEAN_COMPLEXITY,
                "Boolean expression is too complex after normalization (max groups " + maxGroups + ")"
        );
    }
}

