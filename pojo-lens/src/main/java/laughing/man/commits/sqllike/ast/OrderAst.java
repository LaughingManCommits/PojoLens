package laughing.man.commits.sqllike.ast;

import laughing.man.commits.enums.Sort;

import java.util.Objects;

public final class OrderAst {

    private final String field;
    private final Sort sort;

    public OrderAst(String field, Sort sort) {
        this.field = Objects.requireNonNull(field, "field must not be null");
        this.sort = Objects.requireNonNull(sort, "sort must not be null");
    }

    public String field() {
        return field;
    }

    public Sort sort() {
        return sort;
    }
}

