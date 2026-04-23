package laughing.man.commits.dsl;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.filter.Filter;
import laughing.man.commits.internal.FluentEngine;
import laughing.man.commits.internal.builder.QueryBuilder;
import laughing.man.commits.sqllike.QueryExecutionGuard;
import laughing.man.commits.sqllike.QueryExecutionGuardException;
import laughing.man.commits.sqllike.QueryGuardOutcome;
import laughing.man.commits.table.TabularSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable typed query builder that lowers into the shared PojoLens filter engine.
 * Each fluent method returns a new instance; the original is not modified.
 *
 * <p>Usage:
 * <pre>{@code
 *   List<Employee> result = TypedQuery.from(Employee.class)
 *       .where(EmployeeFields.SALARY.gt(100_000).and(EmployeeFields.ACTIVE.eq(true)))
 *       .orderByDesc(EmployeeFields.SALARY)
 *       .limit(10)
 *       .filter(employees);
 * }</pre>
 *
 * <p>Phase 3 limitations:
 * <ul>
 *   <li>Sort direction is global — the last {@code orderByDesc} or {@code orderBy} call wins.</li>
 *   <li>{@code NOT} predicates are not supported; use negated operators ({@code ne}, {@code lte},
 *       {@code isNotNull}) instead.</li>
 * </ul>
 */
public final class TypedQuery<T> {

    private static final int UNSET = -1;

    private final Class<T> entityClass;
    private final List<TypedField<T, ?>> selectFields;
    private final TypedPredicate<T> wherePredicate;
    private final List<String> orderByFieldNames;
    private final Sort sortDirection;
    private final int limit;
    private final int offset;
    private final QueryExecutionGuard executionGuard;

    private TypedQuery(Class<T> entityClass,
                       List<TypedField<T, ?>> selectFields,
                       TypedPredicate<T> wherePredicate,
                       List<String> orderByFieldNames,
                       Sort sortDirection,
                       int limit,
                       int offset,
                       QueryExecutionGuard executionGuard) {
        this.entityClass = entityClass;
        this.selectFields = List.copyOf(selectFields);
        this.wherePredicate = wherePredicate;
        this.orderByFieldNames = List.copyOf(orderByFieldNames);
        this.sortDirection = sortDirection;
        this.limit = limit;
        this.offset = offset;
        this.executionGuard = executionGuard;
    }

    // --- Factory ---

    public static <T> TypedQuery<T> from(Class<T> entityClass) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        return new TypedQuery<>(entityClass, List.of(), null, List.of(), Sort.ASC, UNSET, UNSET, null);
    }

    // --- Fluent configuration ---

    @SafeVarargs
    public final TypedQuery<T> select(TypedField<T, ?>... fields) {
        Objects.requireNonNull(fields, "fields must not be null");
        return new TypedQuery<>(entityClass, List.of(fields), wherePredicate,
                orderByFieldNames, sortDirection, limit, offset, executionGuard);
    }

    public TypedQuery<T> where(TypedPredicate<T> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        return new TypedQuery<>(entityClass, selectFields, predicate,
                orderByFieldNames, sortDirection, limit, offset, executionGuard);
    }

    public TypedQuery<T> orderBy(TypedField<T, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
        List<String> updated = new ArrayList<>(orderByFieldNames);
        updated.add(field.fieldName());
        return new TypedQuery<>(entityClass, selectFields, wherePredicate,
                updated, Sort.ASC, limit, offset, executionGuard);
    }

    public TypedQuery<T> orderByDesc(TypedField<T, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
        List<String> updated = new ArrayList<>(orderByFieldNames);
        updated.add(field.fieldName());
        return new TypedQuery<>(entityClass, selectFields, wherePredicate,
                updated, Sort.DESC, limit, offset, executionGuard);
    }

    public TypedQuery<T> limit(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("limit must be >= 0, got: " + n);
        }
        return new TypedQuery<>(entityClass, selectFields, wherePredicate,
                orderByFieldNames, sortDirection, n, offset, executionGuard);
    }

    public TypedQuery<T> offset(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("offset must be >= 0, got: " + n);
        }
        return new TypedQuery<>(entityClass, selectFields, wherePredicate,
                orderByFieldNames, sortDirection, limit, n, executionGuard);
    }

    public TypedQuery<T> executionGuard(QueryExecutionGuard guard) {
        Objects.requireNonNull(guard, "guard must not be null");
        return new TypedQuery<>(entityClass, selectFields, wherePredicate,
                orderByFieldNames, sortDirection, limit, offset, guard);
    }

    // --- Accessors ---

    public Class<T> entityClass() {
        return entityClass;
    }

    public List<TypedField<T, ?>> selectFields() {
        return selectFields;
    }

    public TypedPredicate<T> wherePredicate() {
        return wherePredicate;
    }

    public List<String> orderByFieldNames() {
        return orderByFieldNames;
    }

    public Sort sortDirection() {
        return sortDirection;
    }

    public int limit() {
        return limit;
    }

    public int offset() {
        return offset;
    }

    public boolean hasSelect() {
        return !selectFields.isEmpty();
    }

    public boolean hasWhere() {
        return wherePredicate != null;
    }

    public boolean hasOrderBy() {
        return !orderByFieldNames.isEmpty();
    }

    public boolean hasLimit() {
        return limit != UNSET;
    }

    public boolean hasOffset() {
        return offset != UNSET;
    }

    // --- Execution ---

    public List<T> filter(List<T> rows) {
        return filter(rows, entityClass);
    }

    public <P> List<P> filter(List<T> rows, Class<P> projectionClass) {
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        if (rows.isEmpty()) {
            return List.of();
        }
        if (executionGuard != null) {
            applyPreExecutionGuard(rows.size());
        }
        QueryBuilder builder = FluentEngine.newQueryBuilder(rows);
        applySelect(builder);
        applyWhere(builder);
        applyOrderBy(builder);
        applyLimit(builder);
        applyOffset(builder);
        Filter filter = builder.initFilter();
        long startMillis = System.currentTimeMillis();
        List<P> result = sortDirection == Sort.DESC
                ? filter.filter(Sort.DESC, projectionClass)
                : filter.filter(projectionClass);
        if (executionGuard != null) {
            long durationMillis = System.currentTimeMillis() - startMillis;
            QueryGuardOutcome post = executionGuard.checkPostExecution(result.size(), durationMillis);
            if (post.blocked()) {
                throw QueryExecutionGuardException.of(post);
            }
        }
        return result;
    }

    /**
     * Returns the engine's debug explain payload for this query against the
     * provided rows without executing the filter.
     *
     * @param rows source rows; must not be null
     * @return explain map
     */
    public Map<String, Object> explain(List<T> rows) {
        Objects.requireNonNull(rows, "rows must not be null");
        QueryBuilder builder = FluentEngine.newQueryBuilder(rows);
        applySelect(builder);
        applyWhere(builder);
        applyOrderBy(builder);
        applyLimit(builder);
        applyOffset(builder);
        return builder.explain();
    }

    /**
     * Returns the tabular schema for this query against the provided rows,
     * projecting to the entity class.
     *
     * @param rows source rows; must not be null
     * @return tabular schema
     */
    public TabularSchema schema(List<T> rows) {
        return schema(rows, entityClass);
    }

    /**
     * Returns the tabular schema for this query against the provided rows,
     * projecting to an explicit class.
     *
     * @param rows            source rows; must not be null
     * @param projectionClass output class; must not be null
     * @param <P>             projection type
     * @return tabular schema
     */
    public <P> TabularSchema schema(List<T> rows, Class<P> projectionClass) {
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        QueryBuilder builder = FluentEngine.newQueryBuilder(rows);
        applySelect(builder);
        applyWhere(builder);
        applyOrderBy(builder);
        applyLimit(builder);
        applyOffset(builder);
        return builder.schema(projectionClass);
    }

    // --- Guard helpers ---

    private void applyPreExecutionGuard(int rowCount) {
        int scanLimit = executionGuard.maxRowsScanned();
        if (scanLimit >= 0 && rowCount > scanLimit) {
            QueryGuardOutcome outcome = QueryGuardOutcome.blocked(
                    "GUARD_ROWS_SCANNED_EXCEEDED",
                    "TypedQuery would scan " + rowCount + " rows, limit is " + scanLimit,
                    null);
            throw QueryExecutionGuardException.of(outcome);
        }
    }

    // --- Internal lowering ---

    private void applySelect(QueryBuilder builder) {
        for (TypedField<T, ?> field : selectFields) {
            builder.addField(field.fieldName());
        }
    }

    private void applyWhere(QueryBuilder builder) {
        if (wherePredicate != null) {
            lowerPredicate(wherePredicate, builder, Separator.AND);
        }
    }

    private void applyOrderBy(QueryBuilder builder) {
        for (String fieldName : orderByFieldNames) {
            builder.addOrder(fieldName);
        }
    }

    private void applyLimit(QueryBuilder builder) {
        if (limit != UNSET) {
            builder.limit(limit);
        }
    }

    private void applyOffset(QueryBuilder builder) {
        if (offset != UNSET) {
            builder.offset(offset);
        }
    }

    private static <T> void lowerPredicate(TypedPredicate<T> node,
                                            QueryBuilder builder,
                                            Separator firstChildSep) {
        switch (node.operator()) {
            case AND -> {
                List<TypedPredicate<T>> children = node.children();
                for (int i = 0; i < children.size(); i++) {
                    lowerPredicate(children.get(i), builder,
                            i == 0 ? firstChildSep : Separator.AND);
                }
            }
            case OR -> {
                List<TypedPredicate<T>> children = node.children();
                for (int i = 0; i < children.size(); i++) {
                    lowerPredicate(children.get(i), builder,
                            i == 0 ? firstChildSep : Separator.OR);
                }
            }
            case NOT -> throw new UnsupportedOperationException(
                    "NOT predicates are not supported in TypedQuery. "
                    + "Use negated operators (ne, lte, gte, isNotNull) instead.");
            default -> lowerLeaf(node, builder, firstChildSep);
        }
    }

    private static <T> void lowerLeaf(TypedPredicate<T> leaf,
                                       QueryBuilder builder,
                                       Separator sep) {
        String field = leaf.field().fieldName();
        switch (leaf.operator()) {
            case EQ -> builder.addRule(field, leaf.value(), Clauses.EQUAL, sep);
            case NE -> builder.addRule(field, leaf.value(), Clauses.NOT_EQUAL, sep);
            case GT -> builder.addRule(field, leaf.value(), Clauses.BIGGER, sep);
            case GTE -> builder.addRule(field, leaf.value(), Clauses.BIGGER_EQUAL, sep);
            case LT -> builder.addRule(field, leaf.value(), Clauses.SMALLER, sep);
            case LTE -> builder.addRule(field, leaf.value(), Clauses.SMALLER_EQUAL, sep);
            case IN -> builder.addRule(field, leaf.values(), Clauses.IN, sep);
            case IS_NULL -> builder.addRule(field, null, Clauses.EQUAL, sep);
            case IS_NOT_NULL -> builder.addRule(field, null, Clauses.NOT_EQUAL, sep);
            default -> throw new UnsupportedOperationException(
                    "Unexpected leaf operator: " + leaf.operator());
        }
    }
}
