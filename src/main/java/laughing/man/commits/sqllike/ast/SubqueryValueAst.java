package laughing.man.commits.sqllike.ast;

import java.util.Objects;

/**
 * Nested SQL-like subquery value used by limited IN-subquery support.
 */
public final class SubqueryValueAst {

    private final String source;
    private final QueryAst query;

    public SubqueryValueAst(String source, QueryAst query) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.query = Objects.requireNonNull(query, "query must not be null");
    }

    public String source() {
        return source;
    }

    public QueryAst query() {
        return query;
    }
}

