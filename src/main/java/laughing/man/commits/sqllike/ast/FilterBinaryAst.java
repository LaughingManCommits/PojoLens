package laughing.man.commits.sqllike.ast;

import laughing.man.commits.enums.Separator;

import java.util.Objects;

public final class FilterBinaryAst implements FilterExpressionAst {

    private final FilterExpressionAst left;
    private final FilterExpressionAst right;
    private final Separator operator;

    public FilterBinaryAst(FilterExpressionAst left, FilterExpressionAst right, Separator operator) {
        this.left = Objects.requireNonNull(left, "left must not be null");
        this.right = Objects.requireNonNull(right, "right must not be null");
        this.operator = Objects.requireNonNull(operator, "operator must not be null");
    }

    public FilterExpressionAst left() {
        return left;
    }

    public FilterExpressionAst right() {
        return right;
    }

    public Separator operator() {
        return operator;
    }
}

