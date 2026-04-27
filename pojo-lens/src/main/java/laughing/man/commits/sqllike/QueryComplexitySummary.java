package laughing.man.commits.sqllike;

import java.util.Objects;

/**
 * Pre-execution shape-based complexity assessment derived from a
 * {@link SqlLikePlanPreview}.
 *
 * <p>The {@link #estimatedComplexityScore()} is an additive integer computed
 * from structural query features:
 * <ul>
 *   <li>1 point per filter predicate</li>
 *   <li>3 points per join source</li>
 *   <li>2 points when grouping is present</li>
 *   <li>2 points when aggregation is present</li>
 *   <li>4 points when window functions are present</li>
 *   <li>3 points when subqueries are present</li>
 * </ul>
 *
 * <p>Use the score as the input to
 * {@link QueryExecutionGuard.Builder#maxComplexityScore(int)} to bound
 * query complexity. The score intentionally has no unit; calibrate the limit
 * against your workload's typical range.
 */
public final class QueryComplexitySummary {

    private final int filterCount;
    private final int joinCount;
    private final boolean hasGrouping;
    private final boolean hasAggregation;
    private final boolean hasWindows;
    private final boolean hasSubqueries;
    private final boolean hasPaging;
    private final int estimatedComplexityScore;

    private QueryComplexitySummary(int filterCount,
                                   int joinCount,
                                   boolean hasGrouping,
                                   boolean hasAggregation,
                                   boolean hasWindows,
                                   boolean hasSubqueries,
                                   boolean hasPaging) {
        this.filterCount = filterCount;
        this.joinCount = joinCount;
        this.hasGrouping = hasGrouping;
        this.hasAggregation = hasAggregation;
        this.hasWindows = hasWindows;
        this.hasSubqueries = hasSubqueries;
        this.hasPaging = hasPaging;
        this.estimatedComplexityScore = score(filterCount, joinCount, hasGrouping,
                hasAggregation, hasWindows, hasSubqueries);
    }

    /**
     * Derives a complexity summary from the given plan preview.
     *
     * @param preview plan preview for the query being assessed
     * @return complexity summary
     */
    public static QueryComplexitySummary from(SqlLikePlanPreview preview) {
        Objects.requireNonNull(preview, "preview must not be null");
        return new QueryComplexitySummary(
                preview.filters().size(),
                preview.joins().size(),
                preview.hasGrouping(),
                preview.hasAggregation(),
                preview.hasWindows(),
                preview.hasSubqueries(),
                preview.hasPaging()
        );
    }

    private static int score(int filterCount, int joinCount, boolean hasGrouping,
                             boolean hasAggregation, boolean hasWindows, boolean hasSubqueries) {
        int s = filterCount;
        s += joinCount * 3;
        if (hasGrouping) {
            s += 2;
        }
        if (hasAggregation) {
            s += 2;
        }
        if (hasWindows) {
            s += 4;
        }
        if (hasSubqueries) {
            s += 3;
        }
        return s;
    }

    /** Number of WHERE/HAVING/QUALIFY filter predicates. */
    public int filterCount() {
        return filterCount;
    }

    /** Number of JOIN sources. */
    public int joinCount() {
        return joinCount;
    }

    public boolean hasGrouping() {
        return hasGrouping;
    }

    public boolean hasAggregation() {
        return hasAggregation;
    }

    public boolean hasWindows() {
        return hasWindows;
    }

    public boolean hasSubqueries() {
        return hasSubqueries;
    }

    public boolean hasPaging() {
        return hasPaging;
    }

    /**
     * Additive complexity score for this query shape.
     * Higher scores indicate more expensive query shapes.
     * Use as the limit input for
     * {@link QueryExecutionGuard.Builder#maxComplexityScore(int)}.
     */
    public int estimatedComplexityScore() {
        return estimatedComplexityScore;
    }
}
