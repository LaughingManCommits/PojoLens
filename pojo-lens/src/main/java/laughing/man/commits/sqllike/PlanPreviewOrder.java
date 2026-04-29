package laughing.man.commits.sqllike;

import java.util.Objects;

/**
 * Structural description of a single ORDER BY field in a {@link SqlLikePlanPreview}.
 */
public final class PlanPreviewOrder {

    private final String field;
    private final String direction;

    public PlanPreviewOrder(String field, String direction) {
        this.field = Objects.requireNonNull(field, "field must not be null");
        this.direction = Objects.requireNonNull(direction, "direction must not be null");
    }

    /**
     * Returns the field name used for ordering.
     *
     * @return field name
     */
    public String field() {
        return field;
    }

    /**
     * Returns the sort direction: {@code "ASC"} or {@code "DESC"}.
     *
     * @return sort direction
     */
    public String direction() {
        return direction;
    }
}
