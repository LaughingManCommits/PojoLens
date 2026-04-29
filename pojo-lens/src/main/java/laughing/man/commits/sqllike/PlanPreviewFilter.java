package laughing.man.commits.sqllike;

import java.util.Objects;

/**
 * Structural description of a single filter predicate in a {@link SqlLikePlanPreview}.
 * <p>
 * Covers WHERE, HAVING, and QUALIFY predicates.
 * <p>
 * {@link #valueKind()} identifies how the right-hand value is supplied:
 * <ul>
 *   <li>{@code "LITERAL"} - inline literal value</li>
 *   <li>{@code "PARAMETER"} - named parameter placeholder; see {@link #parameterName()}</li>
 *   <li>{@code "SUBQUERY"} - IN-subquery</li>
 *   <li>{@code "EXISTS_SUBQUERY"} - EXISTS or NOT EXISTS subquery</li>
 * </ul>
 */
public final class PlanPreviewFilter {

    private final String field;
    private final String operator;
    private final String valueKind;
    private final String parameterName;
    private final SqlLikePlanPreview subqueryPreview;

    public PlanPreviewFilter(String field,
                             String operator,
                             String valueKind,
                             String parameterName) {
        this(field, operator, valueKind, parameterName, null);
    }

    public PlanPreviewFilter(String field,
                             String operator,
                             String valueKind,
                             String parameterName,
                             SqlLikePlanPreview subqueryPreview) {
        this.field = Objects.requireNonNull(field, "field must not be null");
        this.operator = Objects.requireNonNull(operator, "operator must not be null");
        this.valueKind = Objects.requireNonNull(valueKind, "valueKind must not be null");
        this.parameterName = parameterName;
        this.subqueryPreview = subqueryPreview;
    }

    /**
     * Returns the field name the predicate applies to.
     *
     * @return field name
     */
    public String field() {
        return field;
    }

    /**
     * Returns the comparison operator as a SQL-like string
     * (e.g. {@code "="}, {@code "!="}, {@code "<"}, {@code ">"}, {@code "IN"},
     * {@code "CONTAINS"}, {@code "MATCHES"}, {@code "EXISTS"}, {@code "NOT EXISTS"}).
     *
     * @return operator string
     */
    public String operator() {
        return operator;
    }

    /**
     * Returns how the right-hand value is supplied.
     * One of {@code "LITERAL"}, {@code "PARAMETER"}, {@code "SUBQUERY"},
     * {@code "EXISTS_SUBQUERY"}.
     *
     * @return value kind
     */
    public String valueKind() {
        return valueKind;
    }

    /**
     * Returns the named parameter name (without the {@code :} prefix) when
     * {@link #valueKind()} is {@code "PARAMETER"}, or {@code null} otherwise.
     *
     * @return parameter name or null
     */
    public String parameterName() {
        return parameterName;
    }

    /**
     * Returns the nested subquery plan preview when {@link #valueKind()} is
     * {@code "SUBQUERY"} or {@code "EXISTS_SUBQUERY"}, or {@code null}
     * otherwise.
     *
     * @return nested subquery preview or null
     */
    public SqlLikePlanPreview subqueryPreview() {
        return subqueryPreview;
    }
}
