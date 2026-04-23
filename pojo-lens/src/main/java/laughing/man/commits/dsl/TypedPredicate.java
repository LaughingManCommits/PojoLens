package laughing.man.commits.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable descriptor for a typed filter condition over entity type {@code T}.
 * Leaf predicates carry a field, operator, and value(s).
 * Compound predicates (AND/OR/NOT) carry child predicates.
 * Instances are obtained via {@link TypedField} convenience methods or the static factories here.
 */
public final class TypedPredicate<T> {

    public enum Operator {
        EQ, NE, GT, GTE, LT, LTE, IN, IS_NULL, IS_NOT_NULL,
        AND, OR, NOT
    }

    private final Operator operator;
    private final TypedField<T, ?> field;
    private final Object value;
    private final List<Object> values;
    private final List<TypedPredicate<T>> children;

    private TypedPredicate(Operator operator,
                            TypedField<T, ?> field,
                            Object value,
                            List<Object> values,
                            List<TypedPredicate<T>> children) {
        this.operator = operator;
        this.field = field;
        this.value = value;
        this.values = values;
        this.children = children;
    }

    // --- Accessors ---

    public Operator operator() {
        return operator;
    }

    /** Non-null for leaf predicates; null for compound (AND/OR/NOT). */
    public TypedField<T, ?> field() {
        return field;
    }

    /** Non-null for scalar leaf predicates (EQ/NE/GT/GTE/LT/LTE); null otherwise. */
    public Object value() {
        return value;
    }

    /** Non-empty for IN predicates; empty otherwise. */
    public List<Object> values() {
        return values;
    }

    /** Non-empty for compound predicates (AND/OR/NOT); empty otherwise. */
    public List<TypedPredicate<T>> children() {
        return children;
    }

    public boolean isLeaf() {
        return operator != Operator.AND && operator != Operator.OR && operator != Operator.NOT;
    }

    // --- Instance combinators ---

    public TypedPredicate<T> and(TypedPredicate<T> other) {
        Objects.requireNonNull(other, "other must not be null");
        return compound(Operator.AND, List.of(this, other));
    }

    public TypedPredicate<T> or(TypedPredicate<T> other) {
        Objects.requireNonNull(other, "other must not be null");
        return compound(Operator.OR, List.of(this, other));
    }

    public TypedPredicate<T> not() {
        return compound(Operator.NOT, List.of(this));
    }

    // --- Static scalar factories ---

    public static <T, V> TypedPredicate<T> eq(TypedField<T, V> field, V value) {
        requireField(field);
        return scalar(Operator.EQ, field, value);
    }

    public static <T, V> TypedPredicate<T> ne(TypedField<T, V> field, V value) {
        requireField(field);
        return scalar(Operator.NE, field, value);
    }

    public static <T, V> TypedPredicate<T> gt(TypedField<T, V> field, V value) {
        requireField(field);
        Objects.requireNonNull(value, "value must not be null for gt");
        return scalar(Operator.GT, field, value);
    }

    public static <T, V> TypedPredicate<T> gte(TypedField<T, V> field, V value) {
        requireField(field);
        Objects.requireNonNull(value, "value must not be null for gte");
        return scalar(Operator.GTE, field, value);
    }

    public static <T, V> TypedPredicate<T> lt(TypedField<T, V> field, V value) {
        requireField(field);
        Objects.requireNonNull(value, "value must not be null for lt");
        return scalar(Operator.LT, field, value);
    }

    public static <T, V> TypedPredicate<T> lte(TypedField<T, V> field, V value) {
        requireField(field);
        Objects.requireNonNull(value, "value must not be null for lte");
        return scalar(Operator.LTE, field, value);
    }

    public static <T> TypedPredicate<T> isNull(TypedField<T, ?> field) {
        requireField(field);
        return new TypedPredicate<>(Operator.IS_NULL, field, null, List.of(), List.of());
    }

    public static <T> TypedPredicate<T> isNotNull(TypedField<T, ?> field) {
        requireField(field);
        return new TypedPredicate<>(Operator.IS_NOT_NULL, field, null, List.of(), List.of());
    }

    // --- Static IN factories ---

    @SafeVarargs
    public static <T, V> TypedPredicate<T> in(TypedField<T, V> field, V... values) {
        requireField(field);
        Objects.requireNonNull(values, "values must not be null");
        if (values.length == 0) {
            throw new IllegalArgumentException("in() requires at least one value");
        }
        return new TypedPredicate<>(Operator.IN, field, null,
                asObjectList(Arrays.asList(values)), List.of());
    }

    public static <T, V> TypedPredicate<T> in(TypedField<T, V> field, Collection<? extends V> values) {
        requireField(field);
        Objects.requireNonNull(values, "values must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("in() requires at least one value");
        }
        return new TypedPredicate<>(Operator.IN, field, null, asObjectList(values), List.of());
    }

    // --- Static compound factories (allOf/anyOf avoids name conflict with instance and/or) ---

    @SafeVarargs
    public static <T> TypedPredicate<T> allOf(TypedPredicate<T>... predicates) {
        requirePredicates(predicates, "allOf");
        if (predicates.length == 1) {
            return predicates[0];
        }
        return compound(Operator.AND, copyPredicateList(predicates));
    }

    @SafeVarargs
    public static <T> TypedPredicate<T> anyOf(TypedPredicate<T>... predicates) {
        requirePredicates(predicates, "anyOf");
        if (predicates.length == 1) {
            return predicates[0];
        }
        return compound(Operator.OR, copyPredicateList(predicates));
    }

    // --- Private helpers ---

    private static <T, V> TypedPredicate<T> scalar(Operator op, TypedField<T, V> field, V value) {
        return new TypedPredicate<>(op, field, value, List.of(), List.of());
    }

    private static <T> TypedPredicate<T> compound(Operator op, List<TypedPredicate<T>> children) {
        return new TypedPredicate<>(op, null, null, List.of(), children);
    }

    private static List<Object> asObjectList(Collection<?> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static <T> List<TypedPredicate<T>> copyPredicateList(TypedPredicate<T>[] predicates) {
        List<TypedPredicate<T>> list = new ArrayList<>(predicates.length);
        for (TypedPredicate<T> p : predicates) {
            Objects.requireNonNull(p, "predicate element must not be null");
            list.add(p);
        }
        return Collections.unmodifiableList(list);
    }

    private static void requireField(TypedField<?, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
    }

    private static <T> void requirePredicates(TypedPredicate<T>[] predicates, String method) {
        Objects.requireNonNull(predicates, "predicates must not be null for " + method);
        if (predicates.length == 0) {
            throw new IllegalArgumentException(method + "() requires at least one predicate");
        }
    }
}
