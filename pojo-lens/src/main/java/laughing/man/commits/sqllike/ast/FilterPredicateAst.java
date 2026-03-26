package laughing.man.commits.sqllike.ast;

import java.util.Objects;

public final class FilterPredicateAst implements FilterExpressionAst {

    private final FilterAst filter;

    public FilterPredicateAst(FilterAst filter) {
        this.filter = Objects.requireNonNull(filter, "filter must not be null");
    }

    public FilterAst filter() {
        return filter;
    }
}

