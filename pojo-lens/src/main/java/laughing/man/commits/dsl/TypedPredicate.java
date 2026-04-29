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
        IN_SUBQUERY, EXISTS, NOT_EXISTS,
        AND, OR, NOT
    }

    private final Operator operator;
    private final TypedField<T, ?> field;
    private final Object value;
    private final List<Object> values;
    private final List<TypedPredicate<T>> children;
    private final TypedSubqueryDescriptor subquery;

    private TypedPredicate(Operator operator,
                           TypedField<T, ?> field,
                           Object value,
                           List<Object> values,
                           List<TypedPredicate<T>> children,
                           TypedSubqueryDescriptor subquery) {
        this.operator = operator;
        this.field = field;
        this.value = value;
        this.values = values == null ? List.of() : new ArrayList<>(values);
        this.children = children == null ? List.of() : new ArrayList<>(children);
        this.subquery = subquery;
    }

    // --- Accessors ---

    public Operator operator() {
        return operator;
    }

    /** Non-null for scalar leaves and IN-subquery target fields; null for compound and EXISTS leaves. */
    public TypedField<T, ?> field() {
        return field;
    }

    /** Non-null for scalar leaf predicates (EQ/NE/GT/GTE/LT/LTE); null otherwise. */
    public Object value() {
        return value;
    }

    /** Non-empty for IN predicates; empty otherwise. */
    public List<Object> values() {
        return Collections.unmodifiableList(values);
    }

    /** Non-empty for compound predicates (AND/OR/NOT); empty otherwise. */
    public List<TypedPredicate<T>> children() {
        return Collections.unmodifiableList(children);
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
        return new TypedPredicate<>(Operator.IS_NULL, field, null, List.of(), List.of(), null);
    }

    public static <T> TypedPredicate<T> isNotNull(TypedField<T, ?> field) {
        requireField(field);
        return new TypedPredicate<>(Operator.IS_NOT_NULL, field, null, List.of(), List.of(), null);
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
                asObjectList(Arrays.asList(values)), List.of(), null);
    }

    public static <T, V> TypedPredicate<T> in(TypedField<T, V> field, Collection<? extends V> values) {
        requireField(field);
        Objects.requireNonNull(values, "values must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("in() requires at least one value");
        }
        return new TypedPredicate<>(Operator.IN, field, null, asObjectList(values), List.of(), null);
    }

    public static <T, V> TypedPredicate<T> inSubquery(TypedField<T, V> field,
                                                      TypedField<T, ? extends V> subqueryOutputField,
                                                      TypedQuery<T> subquery) {
        requireField(field);
        requireField(subqueryOutputField);
        Objects.requireNonNull(subquery, "subquery must not be null");
        return new TypedPredicate<>(
                Operator.IN_SUBQUERY,
                field,
                null,
                List.of(),
                List.of(),
                TypedSubqueryDescriptor.selfSource(subqueryOutputField.fieldName(), subquery)
        );
    }

    public static <T, V, S> TypedPredicate<T> inSubquery(TypedField<T, V> field,
                                                         TypedField<S, ? extends V> subqueryOutputField,
                                                         List<S> subqueryRows,
                                                         TypedQuery<S> subquery) {
        requireField(field);
        requireField(subqueryOutputField);
        Objects.requireNonNull(subqueryRows, "subqueryRows must not be null");
        Objects.requireNonNull(subquery, "subquery must not be null");
        return new TypedPredicate<>(
                Operator.IN_SUBQUERY,
                field,
                null,
                List.of(),
                List.of(),
                TypedSubqueryDescriptor.explicitSource(subqueryOutputField.fieldName(), subqueryRows, subquery)
        );
    }

    public static <T> TypedPredicate<T> exists(TypedQuery<T> subquery) {
        Objects.requireNonNull(subquery, "subquery must not be null");
        return new TypedPredicate<>(
                Operator.EXISTS,
                null,
                null,
                List.of(),
                List.of(),
                TypedSubqueryDescriptor.selfSource(null, subquery)
        );
    }

    public static <T> TypedPredicate<T> exists(Class<T> rowType, TypedQuery<T> subquery) {
        Objects.requireNonNull(rowType, "rowType must not be null");
        return exists(subquery);
    }

    public static <T, S> TypedPredicate<T> exists(List<S> subqueryRows, TypedQuery<S> subquery) {
        Objects.requireNonNull(subqueryRows, "subqueryRows must not be null");
        Objects.requireNonNull(subquery, "subquery must not be null");
        return new TypedPredicate<>(
                Operator.EXISTS,
                null,
                null,
                List.of(),
                List.of(),
                TypedSubqueryDescriptor.explicitSource(null, subqueryRows, subquery)
        );
    }

    public static <T, S> TypedPredicate<T> exists(Class<T> rowType, List<S> subqueryRows, TypedQuery<S> subquery) {
        Objects.requireNonNull(rowType, "rowType must not be null");
        return exists(subqueryRows, subquery);
    }

    public static <T> TypedPredicate<T> notExists(TypedQuery<T> subquery) {
        Objects.requireNonNull(subquery, "subquery must not be null");
        return new TypedPredicate<>(
                Operator.NOT_EXISTS,
                null,
                null,
                List.of(),
                List.of(),
                TypedSubqueryDescriptor.selfSource(null, subquery)
        );
    }

    public static <T> TypedPredicate<T> notExists(Class<T> rowType, TypedQuery<T> subquery) {
        Objects.requireNonNull(rowType, "rowType must not be null");
        return notExists(subquery);
    }

    public static <T, S> TypedPredicate<T> notExists(List<S> subqueryRows, TypedQuery<S> subquery) {
        Objects.requireNonNull(subqueryRows, "subqueryRows must not be null");
        Objects.requireNonNull(subquery, "subquery must not be null");
        return new TypedPredicate<>(
                Operator.NOT_EXISTS,
                null,
                null,
                List.of(),
                List.of(),
                TypedSubqueryDescriptor.explicitSource(null, subqueryRows, subquery)
        );
    }

    public static <T, S> TypedPredicate<T> notExists(Class<T> rowType,
                                                     List<S> subqueryRows,
                                                     TypedQuery<S> subquery) {
        Objects.requireNonNull(rowType, "rowType must not be null");
        return notExists(subqueryRows, subquery);
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

    // --- Package-private helpers used by TypedQuery lowering ---

    boolean hasSubqueryDescriptor() {
        return subquery != null;
    }

    TypedSubqueryDescriptor subqueryDescriptor() {
        return subquery;
    }

    // --- Private helpers ---

    private static <T, V> TypedPredicate<T> scalar(Operator op, TypedField<T, V> field, V value) {
        return new TypedPredicate<>(op, field, value, List.of(), List.of(), null);
    }

    private static <T> TypedPredicate<T> compound(Operator op, List<TypedPredicate<T>> children) {
        return new TypedPredicate<>(op, null, null, List.of(), children, null);
    }

    private static List<Object> asObjectList(Collection<?> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static <T> List<TypedPredicate<T>> copyPredicateList(TypedPredicate<T>[] predicates) {
        List<TypedPredicate<T>> list = new ArrayList<>(predicates.length);
        for (TypedPredicate<T> predicate : predicates) {
            Objects.requireNonNull(predicate, "predicate element must not be null");
            list.add(predicate);
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

    static final class TypedSubqueryDescriptor {
        private final String outputField;
        private final List<?> sourceRows;
        private final boolean explicitSource;
        private final TypedQuery<?> subquery;

        private TypedSubqueryDescriptor(String outputField,
                                        List<?> sourceRows,
                                        boolean explicitSource,
                                        TypedQuery<?> subquery) {
            this.outputField = outputField;
            this.sourceRows = sourceRows == null ? List.of() : List.copyOf(sourceRows);
            this.explicitSource = explicitSource;
            this.subquery = subquery;
        }

        private static TypedSubqueryDescriptor selfSource(String outputField, TypedQuery<?> subquery) {
            return new TypedSubqueryDescriptor(outputField, List.of(), false, subquery);
        }

        private static TypedSubqueryDescriptor explicitSource(String outputField,
                                                              List<?> sourceRows,
                                                              TypedQuery<?> subquery) {
            return new TypedSubqueryDescriptor(outputField, sourceRows, true, subquery);
        }

        String outputField() {
            return outputField;
        }

        List<?> sourceRows() {
            return sourceRows;
        }

        boolean explicitSource() {
            return explicitSource;
        }

        TypedQuery<?> subquery() {
            return subquery;
        }
    }
}
