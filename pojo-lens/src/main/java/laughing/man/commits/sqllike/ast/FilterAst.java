package laughing.man.commits.sqllike.ast;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;

import java.util.Objects;

public final class FilterAst {

    private final String field;
    private final Clauses clause;
    private final Object value;
    private final Separator separator;

    public FilterAst(String field, Clauses clause, Object value, Separator separator) {
        this.field = Objects.requireNonNull(field, "field must not be null");
        this.clause = Objects.requireNonNull(clause, "clause must not be null");
        this.value = value;
        this.separator = separator;
    }

    public String field() {
        return field;
    }

    public Clauses clause() {
        return clause;
    }

    public Object value() {
        return value;
    }

    public Separator separator() {
        return separator;
    }
}

