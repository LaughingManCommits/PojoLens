package laughing.man.commits.sqllike.ast;

import laughing.man.commits.enums.Join;

import java.util.Objects;

public final class JoinAst {

    private final Join joinType;
    private final String childSource;
    private final String parentField;
    private final String childField;

    public JoinAst(Join joinType, String childSource, String parentField, String childField) {
        this.joinType = Objects.requireNonNull(joinType, "joinType must not be null");
        this.childSource = Objects.requireNonNull(childSource, "childSource must not be null");
        this.parentField = Objects.requireNonNull(parentField, "parentField must not be null");
        this.childField = Objects.requireNonNull(childField, "childField must not be null");
    }

    public Join joinType() {
        return joinType;
    }

    public String childSource() {
        return childSource;
    }

    public String parentField() {
        return parentField;
    }

    public String childField() {
        return childField;
    }
}

