package laughing.man.commits.sqllike;

import java.util.Objects;

/**
 * Structural description of a JOIN clause in a {@link SqlLikePlanPreview}.
 */
public final class PlanPreviewJoin {

    private final String type;
    private final String source;
    private final String parentField;
    private final String childField;

    public PlanPreviewJoin(String type,
                           String source,
                           String parentField,
                           String childField) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.parentField = Objects.requireNonNull(parentField, "parentField must not be null");
        this.childField = Objects.requireNonNull(childField, "childField must not be null");
    }

    /**
     * Returns the join type: {@code "INNER"}, {@code "LEFT"}, or {@code "RIGHT"}.
     *
     * @return join type
     */
    public String type() {
        return type;
    }

    /**
     * Returns the named source being joined.
     *
     * @return join source name
     */
    public String source() {
        return source;
    }

    /**
     * Returns the parent (primary) field used in the join condition.
     *
     * @return parent field name
     */
    public String parentField() {
        return parentField;
    }

    /**
     * Returns the child (joined source) field used in the join condition.
     *
     * @return child field name
     */
    public String childField() {
        return childField;
    }
}
