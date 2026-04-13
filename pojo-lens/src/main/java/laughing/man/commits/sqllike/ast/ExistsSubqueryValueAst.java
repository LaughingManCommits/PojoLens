package laughing.man.commits.sqllike.ast;

import java.util.Objects;

/**
 * Nested SQL-like subquery predicate used by limited EXISTS support.
 */
public final class ExistsSubqueryValueAst {

    private final String source;
    private final QueryAst query;
    private final boolean negated;

    public ExistsSubqueryValueAst(String source, QueryAst query, boolean negated) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.negated = negated;
    }

    public String source() {
        return source;
    }

    public QueryAst query() {
        return query;
    }

    public boolean negated() {
        return negated;
    }
}
