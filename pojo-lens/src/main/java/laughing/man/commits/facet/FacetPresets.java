package laughing.man.commits.facet;

import laughing.man.commits.util.StringUtil;

/**
 * Factory for common in-memory facet aggregation patterns.
 */
public final class FacetPresets {

    private FacetPresets() {
    }

    /**
     * Returns a {@link FacetQuery} that counts distinct values for {@code fieldName}.
     * Results are sorted by count descending, then value ascending.
     */
    public static FacetQuery distinctCounts(String fieldName) {
        if (StringUtil.isNullOrBlank(fieldName)) {
            throw new IllegalArgumentException("fieldName must not be null/blank");
        }
        return new FacetQuery(fieldName);
    }
}
