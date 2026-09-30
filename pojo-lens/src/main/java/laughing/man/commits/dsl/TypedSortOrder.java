package laughing.man.commits.dsl;

import laughing.man.commits.enums.Sort;

import java.util.Objects;

/**
 * Typed ORDER BY descriptor used by {@link TypedQuery#orderBy(TypedSortOrder...)}.
 * Each instance pairs a field with an explicit sort direction, mirroring
 * {@link TypedWindowOrder} for window ORDER BY.
 *
 * <p>Each descriptor carries its own direction, so queries can mix ascending
 * and descending fields.
 */
public final class TypedSortOrder {

    private final String fieldName;
    private final Sort sort;

    private TypedSortOrder(String fieldName, Sort sort) {
        this.fieldName = fieldName;
        this.sort = sort;
    }

    public static TypedSortOrder asc(TypedField<?, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
        return new TypedSortOrder(field.fieldName(), Sort.ASC);
    }

    public static TypedSortOrder desc(TypedField<?, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
        return new TypedSortOrder(field.fieldName(), Sort.DESC);
    }

    public String fieldName() {
        return fieldName;
    }

    public Sort sort() {
        return sort;
    }
}
