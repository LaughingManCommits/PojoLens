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
        CONTAINS, CONTAINS_IGNORE_CASE, MATCHES,
        IN_SUBQUERY, EXISTS, NOT_EXISTS,
        AND, OR, NOT,
        ANY, NONE
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
        if (this.operator == Operator.ANY) return other;
        if (other.operator() == Operator.ANY) return this;
        if (this.operator == Operator.NONE) return this;
        if (other.operator() == Operator.NONE) return other;
        return compound(Operator.AND, List.of(this, other));
    }

    public TypedPredicate<T> or(TypedPredicate<T> other) {
        Objects.requireNonNull(other, "other must not be null");
        if (this.operator == Operator.ANY) return this;
        if (other.operator() == Operator.ANY) return other;
        if (this.operator == Operator.NONE) return other;
        if (other.operator() == Operator.NONE) return this;
        return compound(Operator.OR, List.of(this, other));
    }

    public TypedPredicate<T> not() {
        if (this.operator == Operator.ANY) return TypedPredicate.none();
        if (this.operator == Operator.NONE) return TypedPredicate.any();
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

    public static <T, V> TypedPredicate<T> between(TypedField<T, V> field, V lo, V hi) {
        requireField(field);
        Objects.requireNonNull(lo, "lo must not be null for between");
        Objects.requireNonNull(hi, "hi must not be null for between");
        return gte(field, lo).and(lte(field, hi));
    }

    public static <T> TypedPredicate<T> isNull(TypedField<T, ?> field) {
        requireField(field);
        return new TypedPredicate<>(Operator.IS_NULL, field, null, List.of(), List.of(), null);
    }

    public static <T> TypedPredicate<T> isNotNull(TypedField<T, ?> field) {
        requireField(field);
        return new TypedPredicate<>(Operator.IS_NOT_NULL, field, null, List.of(), List.of(), null);
    }

    // --- Static string-match factories ---

    public static <T> TypedPredicate<T> contains(TypedField<T, ?> field, String value) {
        requireField(field);
        Objects.requireNonNull(value, "value must not be null for contains");
        return new TypedPredicate<>(Operator.CONTAINS, field, value, null, null, null);
    }

    public static <T> TypedPredicate<T> containsIgnoreCase(TypedField<T, ?> field, String value) {
        requireField(field);
        Objects.requireNonNull(value, "value must not be null for containsIgnoreCase");
        return new TypedPredicate<>(Operator.CONTAINS_IGNORE_CASE, field, value, null, null, null);
    }

    public static <T> TypedPredicate<T> matches(TypedField<T, ?> field, String pattern) {
        requireField(field);
        Objects.requireNonNull(pattern, "pattern must not be null for matches");
        return new TypedPredicate<>(Operator.MATCHES, field, pattern, null, null, null);
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
        // absorption: any NONE absorbs the entire AND
        for (TypedPredicate<T> p : predicates) {
            if (p.operator == Operator.NONE) {
                return TypedPredicate.none();
            }
        }
        // identity: ANY is identity for AND — filter it out
        List<TypedPredicate<T>> filtered = new java.util.ArrayList<>();
        for (TypedPredicate<T> p : predicates) {
            if (p.operator != Operator.ANY) {
                filtered.add(p);
            }
        }
        if (filtered.isEmpty()) {
            return TypedPredicate.any();
        }
        if (filtered.size() == 1) {
            return filtered.get(0);
        }
        return compound(Operator.AND, List.copyOf(filtered));
    }

    @SafeVarargs
    public static <T> TypedPredicate<T> anyOf(TypedPredicate<T>... predicates) {
        requirePredicates(predicates, "anyOf");
        if (predicates.length == 1) {
            return predicates[0];
        }
        // absorption: any ANY absorbs the entire OR
        for (TypedPredicate<T> p : predicates) {
            if (p.operator == Operator.ANY) {
                return TypedPredicate.any();
            }
        }
        // identity: NONE is identity for OR — filter it out
        List<TypedPredicate<T>> filtered = new java.util.ArrayList<>();
        for (TypedPredicate<T> p : predicates) {
            if (p.operator != Operator.NONE) {
                filtered.add(p);
            }
        }
        if (filtered.isEmpty()) {
            return TypedPredicate.none();
        }
        if (filtered.size() == 1) {
            return filtered.get(0);
        }
        return compound(Operator.OR, List.copyOf(filtered));
    }

    /** Always-true sentinel: lowers to no WHERE clause, returning all rows. */
    public static <T> TypedPredicate<T> any() {
        return new TypedPredicate<>(Operator.ANY, null, null, List.of(), List.of(), null);
    }

    /** Always-false sentinel: lowers to an empty result, returning no rows. */
    public static <T> TypedPredicate<T> none() {
        return new TypedPredicate<>(Operator.NONE, null, null, List.of(), List.of(), null);
    }

    // --- Package-private helpers used by TypedQuery lowering ---

    boolean hasSubqueryDescriptor() {
        return subquery != null;
    }

    TypedSubqueryDescriptor subqueryDescriptor() {
        return subquery;
    }

    /**
     * Negates {@code node} using DeMorgan's laws, distributing NOT down to leaves.
     * Called by TypedQuery lowering when a NOT compound is encountered.
     * <p>
     * Rules:
     * <ul>
     *   <li>NOT(NOT(x)) → x</li>
     *   <li>NOT(AND(a,b,...)) → OR(NOT(a), NOT(b), ...)</li>
     *   <li>NOT(OR(a,b,...)) → AND(NOT(a), NOT(b), ...)</li>
     *   <li>NOT(EQ) → NE, NOT(NE) → EQ, NOT(GT) → LTE, NOT(GTE) → LT,
     *       NOT(LT) → GTE, NOT(LTE) → GT</li>
     *   <li>NOT(IS_NULL) → IS_NOT_NULL, NOT(IS_NOT_NULL) → IS_NULL</li>
     *   <li>NOT(EXISTS) → NOT_EXISTS, NOT(NOT_EXISTS) → EXISTS</li>
     *   <li>NOT(IN(v1,v2,...)) → AND(NE(v1), NE(v2), ...)</li>
     *   <li>NOT(IN_SUBQUERY) → throws; use NOT EXISTS instead</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    static <T> TypedPredicate<T> negate(TypedPredicate<T> node) {
        return switch (node.operator()) {
            case NOT -> (TypedPredicate<T>) node.children().get(0);
            case AND -> {
                List<TypedPredicate<T>> negated = node.children().stream()
                        .map(c -> TypedPredicate.negate(c)).toList();
                yield negated.size() == 1 ? negated.get(0) : compound(Operator.OR, negated);
            }
            case OR -> {
                List<TypedPredicate<T>> negated = node.children().stream()
                        .map(c -> TypedPredicate.negate(c)).toList();
                yield negated.size() == 1 ? negated.get(0) : compound(Operator.AND, negated);
            }
            case EQ -> new TypedPredicate<>(Operator.NE, node.field(), node.value(), null, null, null);
            case NE -> new TypedPredicate<>(Operator.EQ, node.field(), node.value(), null, null, null);
            case GT -> new TypedPredicate<>(Operator.LTE, node.field(), node.value(), null, null, null);
            case GTE -> new TypedPredicate<>(Operator.LT, node.field(), node.value(), null, null, null);
            case LT -> new TypedPredicate<>(Operator.GTE, node.field(), node.value(), null, null, null);
            case LTE -> new TypedPredicate<>(Operator.GT, node.field(), node.value(), null, null, null);
            case IS_NULL -> new TypedPredicate<>(Operator.IS_NOT_NULL, node.field(), null, null, null, null);
            case IS_NOT_NULL -> new TypedPredicate<>(Operator.IS_NULL, node.field(), null, null, null, null);
            case EXISTS -> new TypedPredicate<>(Operator.NOT_EXISTS, null, null, null, null, node.subqueryDescriptor());
            case NOT_EXISTS -> new TypedPredicate<>(Operator.EXISTS, null, null, null, null, node.subqueryDescriptor());
            case IN -> {
                List<TypedPredicate<T>> nePredicates = node.values().stream()
                        .map(v -> new TypedPredicate<T>(Operator.NE, node.field(), v, null, null, null))
                        .toList();
                yield nePredicates.size() == 1 ? nePredicates.get(0) : compound(Operator.AND, nePredicates);
            }
            case ANY -> TypedPredicate.none();
            case NONE -> TypedPredicate.any();
            case CONTAINS -> throw new UnsupportedOperationException(
                    "NOT(CONTAINS) is not supported in TypedQuery. Use SQL-like or filter in application code.");
            case CONTAINS_IGNORE_CASE -> throw new UnsupportedOperationException(
                    "NOT(CONTAINS_IGNORE_CASE) is not supported in TypedQuery. Use SQL-like or filter in application code.");
            case MATCHES -> throw new UnsupportedOperationException(
                    "NOT(MATCHES) is not supported in TypedQuery. Use SQL-like or filter in application code.");
            case IN_SUBQUERY -> throw new UnsupportedOperationException(
                    "NOT(IN_SUBQUERY) is not supported in TypedQuery. Use NOT EXISTS instead.");
        };
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
