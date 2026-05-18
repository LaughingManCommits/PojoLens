package laughing.man.commits.dsl;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Compile-time typed reference to a queryable field on entity type {@code T} with value type {@code V}.
 * Carries the field name string used by the query engine alongside the Java type for safe DSL composition.
 */
public final class TypedField<T, V> {

    private final String fieldName;
    private final Class<V> valueType;

    private TypedField(String fieldName, Class<V> valueType) {
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName must not be null");
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be blank");
        }
        this.valueType = Objects.requireNonNull(valueType, "valueType must not be null");
    }

    public static <T, V> TypedField<T, V> of(String fieldName, Class<V> valueType) {
        return new TypedField<>(fieldName, valueType);
    }

    public String fieldName() {
        return fieldName;
    }

    public Class<V> valueType() {
        return valueType;
    }

    @Override
    public String toString() {
        return fieldName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TypedField<?, ?> other)) {
            return false;
        }
        return fieldName.equals(other.fieldName) && valueType.equals(other.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName, valueType);
    }

    // --- Predicate factories ---

    public TypedPredicate<T> eq(V value) {
        return TypedPredicate.eq(this, value);
    }

    public TypedPredicate<T> ne(V value) {
        return TypedPredicate.ne(this, value);
    }

    public TypedPredicate<T> gt(V value) {
        return TypedPredicate.gt(this, value);
    }

    public TypedPredicate<T> gte(V value) {
        return TypedPredicate.gte(this, value);
    }

    public TypedPredicate<T> lt(V value) {
        return TypedPredicate.lt(this, value);
    }

    public TypedPredicate<T> lte(V value) {
        return TypedPredicate.lte(this, value);
    }

    public TypedPredicate<T> between(V lo, V hi) {
        return TypedPredicate.between(this, lo, hi);
    }

    public TypedPredicate<T> in(Collection<? extends V> values) {
        return TypedPredicate.in(this, values);
    }

    @SafeVarargs
    public final TypedPredicate<T> in(V... values) {
        return TypedPredicate.in(this, values);
    }

    public TypedPredicate<T> isNull() {
        return TypedPredicate.isNull(this);
    }

    public TypedPredicate<T> isNotNull() {
        return TypedPredicate.isNotNull(this);
    }

    public TypedPredicate<T> contains(String value) {
        return TypedPredicate.contains(this, value);
    }

    public TypedPredicate<T> matches(String pattern) {
        return TypedPredicate.matches(this, pattern);
    }

    public TypedPredicate<T> inSubquery(TypedField<T, ? extends V> subqueryOutputField, TypedQuery<T> subquery) {
        return TypedPredicate.inSubquery(this, subqueryOutputField, subquery);
    }

    public <S> TypedPredicate<T> inSubquery(TypedField<S, ? extends V> subqueryOutputField,
                                            List<S> subqueryRows,
                                            TypedQuery<S> subquery) {
        return TypedPredicate.inSubquery(this, subqueryOutputField, subqueryRows, subquery);
    }
}
