package laughing.man.commits.sqllike;

import java.util.List;
import java.util.Objects;

/**
 * Structural preview of a SQL-like query's execution shape, produced without
 * executing the query against rows.
 * <p>
 * Use {@link SqlLikeQuery#planPreview()} to obtain a preview from parsed AST
 * metadata. The preview describes which stages will run, what fields are
 * selected, how data is filtered and ordered, and what parameters are required.
 * <p>
 * The preview does not include cost estimates, row counts, optimizer hints,
 * or database semantics. It is deterministic and safe for logging and tests.
 *
 * <pre>{@code
 * SqlLikePlanPreview preview = PojoLensSql.parse(
 *         "select name, salary from Employee " +
 *         "where department = :dept and salary >= :min " +
 *         "order by salary desc limit 20")
 *     .planPreview();
 *
 * // Inspect structural shape
 * List<String> params = preview.requiredParams();  // ["dept", "min"]
 * boolean paged = preview.hasPaging();             // true
 * boolean grouped = preview.hasGrouping();         // false
 * }</pre>
 */
public final class SqlLikePlanPreview {

    private final String source;
    private final boolean wildcard;
    private final List<PlanPreviewField> selectFields;
    private final List<PlanPreviewFilter> filters;
    private final PlanPreviewPredicate filterExpression;
    private final List<String> groupByFields;
    private final List<PlanPreviewFilter> havingFilters;
    private final PlanPreviewPredicate havingExpression;
    private final List<PlanPreviewFilter> qualifyFilters;
    private final PlanPreviewPredicate qualifyExpression;
    private final List<PlanPreviewOrder> orderFields;
    private final List<PlanPreviewJoin> joins;
    private final PlanPreviewPaging paging;
    private final List<String> requiredParams;
    private final boolean hasSubqueries;

    public SqlLikePlanPreview(String source,
                              boolean wildcard,
                              List<PlanPreviewField> selectFields,
                              List<PlanPreviewFilter> filters,
                              List<String> groupByFields,
                              List<PlanPreviewFilter> havingFilters,
                              List<PlanPreviewFilter> qualifyFilters,
                              List<PlanPreviewOrder> orderFields,
                              List<PlanPreviewJoin> joins,
                              PlanPreviewPaging paging,
                              List<String> requiredParams,
                              boolean hasSubqueries) {
        this(source, wildcard, selectFields, filters, null, groupByFields, havingFilters, null,
                qualifyFilters, null, orderFields, joins, paging, requiredParams, hasSubqueries);
    }

    public SqlLikePlanPreview(String source,
                              boolean wildcard,
                              List<PlanPreviewField> selectFields,
                              List<PlanPreviewFilter> filters,
                              PlanPreviewPredicate filterExpression,
                              List<String> groupByFields,
                              List<PlanPreviewFilter> havingFilters,
                              PlanPreviewPredicate havingExpression,
                              List<PlanPreviewFilter> qualifyFilters,
                              PlanPreviewPredicate qualifyExpression,
                              List<PlanPreviewOrder> orderFields,
                              List<PlanPreviewJoin> joins,
                              PlanPreviewPaging paging,
                              List<String> requiredParams,
                              boolean hasSubqueries) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.wildcard = wildcard;
        this.selectFields = List.copyOf(Objects.requireNonNull(selectFields, "selectFields must not be null"));
        this.filters = List.copyOf(Objects.requireNonNull(filters, "filters must not be null"));
        this.filterExpression = filterExpression;
        this.groupByFields = List.copyOf(Objects.requireNonNull(groupByFields, "groupByFields must not be null"));
        this.havingFilters = List.copyOf(Objects.requireNonNull(havingFilters, "havingFilters must not be null"));
        this.havingExpression = havingExpression;
        this.qualifyFilters = List.copyOf(Objects.requireNonNull(qualifyFilters, "qualifyFilters must not be null"));
        this.qualifyExpression = qualifyExpression;
        this.orderFields = List.copyOf(Objects.requireNonNull(orderFields, "orderFields must not be null"));
        this.joins = List.copyOf(Objects.requireNonNull(joins, "joins must not be null"));
        this.paging = paging;
        this.requiredParams = List.copyOf(Objects.requireNonNull(requiredParams, "requiredParams must not be null"));
        this.hasSubqueries = hasSubqueries;
    }

    /**
     * Returns the primary source name for this query.
     *
     * @return source name
     */
    public String source() {
        return source;
    }

    /**
     * Returns true when the SELECT clause is a wildcard ({@code SELECT *}) or absent.
     *
     * @return true for wildcard select
     */
    public boolean isWildcard() {
        return wildcard;
    }

    /**
     * Returns the SELECT fields with aliasing, metric, time bucket, and window
     * function details. Empty when {@link #isWildcard()} is true.
     *
     * @return select fields
     */
    public List<PlanPreviewField> selectFields() {
        return selectFields;
    }

    /**
     * Returns the WHERE predicate descriptors.
     *
     * @return WHERE filters
     */
    public List<PlanPreviewFilter> filters() {
        return filters;
    }

    /**
     * Returns the grouped WHERE predicate expression, or {@code null} when no
     * WHERE clause is present.
     *
     * @return WHERE predicate expression or null
     */
    public PlanPreviewPredicate filterExpression() {
        return filterExpression;
    }

    /**
     * Returns the GROUP BY field names.
     *
     * @return group-by fields
     */
    public List<String> groupByFields() {
        return groupByFields;
    }

    /**
     * Returns the HAVING predicate descriptors.
     *
     * @return HAVING filters
     */
    public List<PlanPreviewFilter> havingFilters() {
        return havingFilters;
    }

    /**
     * Returns the grouped HAVING predicate expression, or {@code null} when no
     * HAVING clause is present.
     *
     * @return HAVING predicate expression or null
     */
    public PlanPreviewPredicate havingExpression() {
        return havingExpression;
    }

    /**
     * Returns the QUALIFY predicate descriptors (post-window filtering).
     *
     * @return QUALIFY filters
     */
    public List<PlanPreviewFilter> qualifyFilters() {
        return qualifyFilters;
    }

    /**
     * Returns the grouped QUALIFY predicate expression, or {@code null} when no
     * QUALIFY clause is present.
     *
     * @return QUALIFY predicate expression or null
     */
    public PlanPreviewPredicate qualifyExpression() {
        return qualifyExpression;
    }

    /**
     * Returns the ORDER BY field descriptors with sort direction.
     *
     * @return order fields
     */
    public List<PlanPreviewOrder> orderFields() {
        return orderFields;
    }

    /**
     * Returns the JOIN clause descriptors.
     *
     * @return joins
     */
    public List<PlanPreviewJoin> joins() {
        return joins;
    }

    /**
     * Returns LIMIT and OFFSET configuration, or {@code null} when neither
     * clause is present.
     *
     * @return paging descriptor or null
     */
    public PlanPreviewPaging paging() {
        return paging;
    }

    /**
     * Returns named parameter placeholders required by the query,
     * without the {@code :} prefix.
     *
     * @return required parameter names
     */
    public List<String> requiredParams() {
        return requiredParams;
    }

    /**
     * Returns true when the query uses IN-subquery or EXISTS subquery predicates.
     *
     * @return true when the query contains subqueries
     */
    public boolean hasSubqueries() {
        return hasSubqueries;
    }

    /**
     * Returns true when the query has a GROUP BY clause.
     *
     * @return true when grouping is declared
     */
    public boolean hasGrouping() {
        return !groupByFields.isEmpty();
    }

    /**
     * Returns true when the query has at least one JOIN clause.
     *
     * @return true when joins are declared
     */
    public boolean hasJoins() {
        return !joins.isEmpty();
    }

    /**
     * Returns true when any SELECT field uses a window function.
     *
     * @return true when window functions are used
     */
    public boolean hasWindows() {
        for (PlanPreviewField f : selectFields) {
            if (f.isWindow()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true when a LIMIT or OFFSET clause is present.
     *
     * @return true when paging is declared
     */
    public boolean hasPaging() {
        return paging != null;
    }

    /**
     * Returns true when any SELECT field uses a metric aggregate function.
     *
     * @return true when aggregation is declared
     */
    public boolean hasAggregation() {
        for (PlanPreviewField f : selectFields) {
            if (f.isMetric()) {
                return true;
            }
        }
        return false;
    }
}
