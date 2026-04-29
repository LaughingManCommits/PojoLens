package laughing.man.commits.sqllike;

/**
 * LIMIT and OFFSET configuration in a {@link SqlLikePlanPreview}.
 * <p>
 * Each bound is either a literal integer or a named parameter, never both.
 */
public final class PlanPreviewPaging {

    private final Integer limit;
    private final String limitParameter;
    private final Integer offset;
    private final String offsetParameter;

    public PlanPreviewPaging(Integer limit,
                             String limitParameter,
                             Integer offset,
                             String offsetParameter) {
        this.limit = limit;
        this.limitParameter = limitParameter;
        this.offset = offset;
        this.offsetParameter = offsetParameter;
    }

    /**
     * Returns the literal LIMIT value, or {@code null} when absent or parameter-driven.
     *
     * @return literal limit or null
     */
    public Integer limit() {
        return limit;
    }

    /**
     * Returns the LIMIT parameter name (without {@code :}), or {@code null} when
     * absent or literal.
     *
     * @return limit parameter name or null
     */
    public String limitParameter() {
        return limitParameter;
    }

    /**
     * Returns the literal OFFSET value, or {@code null} when absent or parameter-driven.
     *
     * @return literal offset or null
     */
    public Integer offset() {
        return offset;
    }

    /**
     * Returns the OFFSET parameter name (without {@code :}), or {@code null} when
     * absent or literal.
     *
     * @return offset parameter name or null
     */
    public String offsetParameter() {
        return offsetParameter;
    }

    /**
     * Returns true when a LIMIT clause is present.
     *
     * @return true when LIMIT is declared
     */
    public boolean hasLimit() {
        return limit != null || limitParameter != null;
    }

    /**
     * Returns true when an OFFSET clause is present.
     *
     * @return true when OFFSET is declared
     */
    public boolean hasOffset() {
        return offset != null || offsetParameter != null;
    }
}
