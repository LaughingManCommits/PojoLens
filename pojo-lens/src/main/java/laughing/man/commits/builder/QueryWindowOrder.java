package laughing.man.commits.builder;

import laughing.man.commits.enums.Sort;
import laughing.man.commits.util.StringUtil;

/**
 * Immutable window ORDER BY descriptor for fluent window definitions.
 */
public final class QueryWindowOrder {

    private final String field;
    private final Sort sort;

    private QueryWindowOrder(String field, Sort sort) {
        this.field = field;
        this.sort = sort;
    }

    public static QueryWindowOrder of(String field, Sort sort) {
        if (field == null || StringUtil.isNull(field.trim())) {
            throw new IllegalArgumentException("field is required");
        }
        if (sort == null) {
            throw new IllegalArgumentException("sort is required");
        }
        return new QueryWindowOrder(field.trim(), sort);
    }

    public static <T, R> QueryWindowOrder of(FieldSelector<T, R> selector, Sort sort) {
        return of(FieldSelectors.resolve(selector), sort);
    }

    public String field() {
        return field;
    }

    public Sort sort() {
        return sort;
    }
}

