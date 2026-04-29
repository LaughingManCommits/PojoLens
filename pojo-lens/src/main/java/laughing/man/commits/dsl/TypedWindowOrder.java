package laughing.man.commits.dsl;

import laughing.man.commits.enums.Sort;

import java.util.Objects;

/**
 * Typed window ORDER BY descriptor used by {@link TypedQuery} window methods.
 */
public final class TypedWindowOrder {

    private final String fieldName;
    private final Sort sort;

    private TypedWindowOrder(String fieldName, Sort sort) {
        this.fieldName = fieldName;
        this.sort = sort;
    }

    public static TypedWindowOrder asc(TypedField<?, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
        return new TypedWindowOrder(field.fieldName(), Sort.ASC);
    }

    public static TypedWindowOrder desc(TypedField<?, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
        return new TypedWindowOrder(field.fieldName(), Sort.DESC);
    }

    public String fieldName() {
        return fieldName;
    }

    public Sort sort() {
        return sort;
    }
}
