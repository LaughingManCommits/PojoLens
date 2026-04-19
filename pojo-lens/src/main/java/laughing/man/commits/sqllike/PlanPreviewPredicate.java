package laughing.man.commits.sqllike;

import java.util.List;
import java.util.Objects;

/**
 * Structural description of a WHERE, HAVING, or QUALIFY predicate expression.
 * <p>
 * Leaf nodes expose a {@link PlanPreviewFilter}. Group nodes expose an
 * {@code "AND"} or {@code "OR"} operator plus ordered child predicates.
 */
public final class PlanPreviewPredicate {

    private final PlanPreviewFilter filter;
    private final String operator;
    private final List<PlanPreviewPredicate> children;

    public PlanPreviewPredicate(PlanPreviewFilter filter,
                                String operator,
                                List<PlanPreviewPredicate> children) {
        if (filter == null && operator == null) {
            throw new IllegalArgumentException("filter or operator must be provided");
        }
        if (filter != null && operator != null) {
            throw new IllegalArgumentException("predicate leaf cannot also declare an operator");
        }
        this.filter = filter;
        this.operator = operator;
        this.children = List.copyOf(Objects.requireNonNull(children, "children must not be null"));
        if (filter != null && !this.children.isEmpty()) {
            throw new IllegalArgumentException("predicate leaf cannot declare children");
        }
        if (operator != null && this.children.isEmpty()) {
            throw new IllegalArgumentException("predicate group must declare children");
        }
    }

    /**
     * Returns true when this node is a single predicate leaf.
     *
     * @return true for leaf predicates
     */
    public boolean isLeaf() {
        return filter != null;
    }

    /**
     * Returns the leaf filter, or {@code null} when this node is a group.
     *
     * @return leaf filter or null
     */
    public PlanPreviewFilter filter() {
        return filter;
    }

    /**
     * Returns the group operator ({@code "AND"} or {@code "OR"}), or
     * {@code null} when this node is a leaf.
     *
     * @return group operator or null
     */
    public String operator() {
        return operator;
    }

    /**
     * Returns ordered child predicates for group nodes.
     * Empty when this node is a leaf.
     *
     * @return child predicates
     */
    public List<PlanPreviewPredicate> children() {
        return children;
    }
}
