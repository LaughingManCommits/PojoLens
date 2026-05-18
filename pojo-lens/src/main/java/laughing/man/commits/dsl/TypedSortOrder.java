package laughing.man.commits.dsl;

import laughing.man.commits.enums.Sort;

import java.util.Objects;

/**
 * Typed ORDER BY descriptor used by {@link TypedQuery#orderBy(TypedSortOrder...)}.
 * Each instance pairs a field with an explicit sort direction, mirroring
 * {@link TypedWindowOrder} for window ORDER BY.
 *
 * <p>Note: the underlying engine requires all ORDER BY fields to share the same
 * direction. {@link TypedQuery} validates this at execution time and throws
 * {@link IllegalStateException} when mixed directions are detected.
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
