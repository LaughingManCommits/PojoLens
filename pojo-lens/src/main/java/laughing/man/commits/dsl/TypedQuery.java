package laughing.man.commits.dsl;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.filter.Filter;
import laughing.man.commits.internal.FluentEngine;
import laughing.man.commits.internal.builder.QueryBuilder;
import laughing.man.commits.internal.builder.QueryRule;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.QueryExecutionGuard;
import laughing.man.commits.sqllike.QueryExecutionGuardException;
import laughing.man.commits.sqllike.QueryGuardOutcome;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
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
 * <p>Current limitations:
 * <ul>
 *   <li>Sort direction is global - the last {@code orderByDesc} or {@code orderBy} call wins.</li>
 *   <li>{@code NOT} predicates are not supported; use negated operators ({@code ne}, {@code lte},
 *       {@code isNotNull}) instead.</li>
 *   <li>Typed HAVING, windows, and subqueries are not part of this foundation surface.</li>
 * </ul>
 */
public final class TypedQuery<T> {

    private static final int UNSET = -1;

    private final Class<T> entityClass;
    private final List<TypedField<T, ?>> selectFields;
    private final TypedPredicate<T> wherePredicate;
    private final List<TypedJoin> joins;
    private final List<String> groupByFieldNames;
    private final List<TypedMetric> metrics;
    private final List<String> orderByFieldNames;
    private final Sort sortDirection;
    private final int limit;
    private final int offset;
    private final QueryExecutionGuard executionGuard;

    private TypedQuery(Class<T> entityClass,
                       List<TypedField<T, ?>> selectFields,
                       TypedPredicate<T> wherePredicate,
                       List<TypedJoin> joins,
                       List<String> groupByFieldNames,
                       List<TypedMetric> metrics,
                       List<String> orderByFieldNames,
                       Sort sortDirection,
                       int limit,
                       int offset,
                       QueryExecutionGuard executionGuard) {
        this.entityClass = entityClass;
        this.selectFields = List.copyOf(selectFields);
        this.wherePredicate = wherePredicate;
        this.joins = List.copyOf(joins);
        this.groupByFieldNames = List.copyOf(groupByFieldNames);
        this.metrics = List.copyOf(metrics);
        this.orderByFieldNames = List.copyOf(orderByFieldNames);
        this.sortDirection = sortDirection;
        this.limit = limit;
        this.offset = offset;
        this.executionGuard = executionGuard;
    }

    // --- Factory ---

    public static <T> TypedQuery<T> from(Class<T> entityClass) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        return new TypedQuery<>(entityClass, List.of(), null, List.of(), List.of(), List.of(),
                List.of(), Sort.ASC, UNSET, UNSET, null);
    }

    // --- Fluent configuration ---

    @SafeVarargs
    public final TypedQuery<T> select(TypedField<T, ?>... fields) {
        Objects.requireNonNull(fields, "fields must not be null");
        return new TypedQuery<>(entityClass, List.of(fields), wherePredicate, joins, groupByFieldNames, metrics,
                orderByFieldNames, sortDirection, limit, offset, executionGuard);
    }

    public TypedQuery<T> where(TypedPredicate<T> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        return new TypedQuery<>(entityClass, selectFields, predicate, joins, groupByFieldNames, metrics,
                orderByFieldNames, sortDirection, limit, offset, executionGuard);
    }

    public <J, K> TypedQuery<T> join(String sourceName,
                                     TypedField<T, K> parentField,
                                     TypedField<J, K> childField,
                                     Join joinType) {
        Objects.requireNonNull(parentField, "parentField must not be null");
        Objects.requireNonNull(childField, "childField must not be null");
        Objects.requireNonNull(joinType, "joinType must not be null");
        ArrayList<TypedJoin> updated = new ArrayList<>(joins);
        updated.add(new TypedJoin(
                normalizeJoinSourceName(sourceName),
                parentField.fieldName(),
                childField.fieldName(),
                joinType
        ));
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, updated, groupByFieldNames, metrics,
                orderByFieldNames, sortDirection, limit, offset, executionGuard);
    }

    public TypedQuery<T> groupBy(TypedField<T, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
        List<String> updated = new ArrayList<>(groupByFieldNames);
        updated.add(field.fieldName());
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                updated, metrics, orderByFieldNames, sortDirection, limit, offset, executionGuard);
    }

    public TypedQuery<T> count(String alias) {
        ArrayList<TypedMetric> updated = new ArrayList<>(metrics);
        updated.add(TypedMetric.count(normalizeAlias(alias)));
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, updated, orderByFieldNames, sortDirection, limit, offset, executionGuard);
    }

    public TypedQuery<T> count(TypedField<?, ?> outputField) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return count(outputField.fieldName());
    }

    public <V> TypedQuery<T> metric(TypedField<T, V> field, Metric metric, String alias) {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(metric, "metric must not be null");
        ArrayList<TypedMetric> updated = new ArrayList<>(metrics);
        updated.add(TypedMetric.of(field.fieldName(), metric, normalizeAlias(alias)));
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, updated, orderByFieldNames, sortDirection, limit, offset, executionGuard);
    }

    public <V> TypedQuery<T> metric(TypedField<T, V> field, Metric metric, TypedField<?, ?> outputField) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return metric(field, metric, outputField.fieldName());
    }

    public TypedQuery<T> orderBy(TypedField<?, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
        List<String> updated = new ArrayList<>(orderByFieldNames);
        updated.add(field.fieldName());
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, updated, Sort.ASC, limit, offset, executionGuard);
    }

    public TypedQuery<T> orderByDesc(TypedField<?, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
        List<String> updated = new ArrayList<>(orderByFieldNames);
        updated.add(field.fieldName());
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, updated, Sort.DESC, limit, offset, executionGuard);
    }

    public TypedQuery<T> limit(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("limit must be >= 0, got: " + n);
        }
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, orderByFieldNames, sortDirection, n, offset, executionGuard);
    }

    public TypedQuery<T> offset(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("offset must be >= 0, got: " + n);
        }
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, orderByFieldNames, sortDirection, limit, n, executionGuard);
    }

    public TypedQuery<T> executionGuard(QueryExecutionGuard guard) {
        Objects.requireNonNull(guard, "guard must not be null");
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, orderByFieldNames, sortDirection, limit, offset, guard);
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

    public boolean hasJoins() {
        return !joins.isEmpty();
    }

    public boolean hasGroupBy() {
        return !groupByFieldNames.isEmpty();
    }

    public boolean hasMetrics() {
        return !metrics.isEmpty();
    }

    public boolean hasLimit() {
        return limit != UNSET;
    }

    public boolean hasOffset() {
        return offset != UNSET;
    }

    // --- Execution ---

    public List<T> filter(List<T> rows) {
        return filter(rows, JoinBindings.empty(), entityClass);
    }

    public <P> List<P> filter(List<T> rows, Class<P> projectionClass) {
        return filter(rows, JoinBindings.empty(), projectionClass);
    }

    public List<T> filter(List<T> rows, JoinBindings joinBindings) {
        return filter(rows, joinBindings, entityClass);
    }

    public List<T> filter(DatasetBundle datasetBundle) {
        return filter(datasetBundle, entityClass);
    }

    public <P> List<P> filter(DatasetBundle datasetBundle, Class<P> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return filterInternal(datasetBundle.primaryRows(), datasetBundle.joinBindings(), projectionClass);
    }

    public <P> List<P> filter(List<T> rows, JoinBindings joinBindings, Class<P> projectionClass) {
        return filterInternal(rows, joinBindings, projectionClass);
    }

    /**
     * Returns the engine's debug explain payload for this query against the
     * provided rows without executing the filter.
     *
     * @param rows source rows; must not be null
     * @return explain map
     */
    public Map<String, Object> explain(List<T> rows) {
        return explain(rows, JoinBindings.empty());
    }

    public Map<String, Object> explain(List<T> rows, JoinBindings joinBindings) {
        return explainInternal(rows, joinBindings);
    }

    public Map<String, Object> explain(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return explainInternal(datasetBundle.primaryRows(), datasetBundle.joinBindings());
    }

    /**
     * Returns the tabular schema for this query against the provided rows,
     * projecting to the entity class.
     *
     * @param rows source rows; must not be null
     * @return tabular schema
     */
    public TabularSchema schema(List<T> rows) {
        return schema(rows, JoinBindings.empty(), entityClass);
    }

    public TabularSchema schema(List<T> rows, JoinBindings joinBindings) {
        return schema(rows, joinBindings, entityClass);
    }

    public TabularSchema schema(DatasetBundle datasetBundle) {
        return schema(datasetBundle, entityClass);
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
        return schema(rows, JoinBindings.empty(), projectionClass);
    }

    public <P> TabularSchema schema(List<T> rows, JoinBindings joinBindings, Class<P> projectionClass) {
        return schemaInternal(rows, joinBindings, projectionClass);
    }

    public <P> TabularSchema schema(DatasetBundle datasetBundle, Class<P> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return schemaInternal(datasetBundle.primaryRows(), datasetBundle.joinBindings(), projectionClass);
    }

    // --- Guard helpers ---

    private void applyPreExecutionGuard(int rowCount) {
        QueryGuardOutcome cancelOutcome = executionGuard.checkCancellation(0);
        if (cancelOutcome.blocked()) {
            throw QueryExecutionGuardException.of(cancelOutcome);
        }
        int scanLimit = executionGuard.maxRowsScanned();
        if (scanLimit >= 0 && rowCount > scanLimit) {
            QueryGuardOutcome outcome = QueryGuardOutcome.blocked(
                    "GUARD_ROWS_SCANNED_EXCEEDED",
                    "TypedQuery would scan " + rowCount + " rows, limit is " + scanLimit,
                    null);
            throw QueryExecutionGuardException.of(outcome);
        }
    }

    private <P> List<P> filterInternal(List<?> rows, JoinBindings joinBindings, Class<P> projectionClass) {
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        if (executionGuard != null) {
            applyPreExecutionGuard(rows.size());
        }
        if (rows.isEmpty() && joins.isEmpty() && groupByFieldNames.isEmpty() && metrics.isEmpty()) {
            return List.of();
        }
        QueryBuilder builder = configuredBuilder(rows, joinBindings);
        Filter filter = preparedFilter(builder);
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

    private Map<String, Object> explainInternal(List<?> rows, JoinBindings joinBindings) {
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        return configuredBuilder(rows, joinBindings).explain();
    }

    private <P> TabularSchema schemaInternal(List<?> rows, JoinBindings joinBindings, Class<P> projectionClass) {
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        return configuredBuilder(rows, joinBindings).schema(projectionClass);
    }

    // --- Internal lowering ---

    private QueryBuilder configuredBuilder(List<?> rows, JoinBindings joinBindings) {
        QueryBuilder builder = FluentEngine.newQueryBuilder(rows);
        validateQueryShape();
        applyJoins(builder, joinBindings);
        applySelect(builder);
        applyWhere(builder);
        applyGroupBy(builder);
        applyMetrics(builder);
        applyOrderBy(builder);
        applyLimit(builder);
        applyOffset(builder);
        return builder;
    }

    private Filter preparedFilter(QueryBuilder builder) {
        Filter filter = builder.initFilter();
        return joins.isEmpty() ? filter : filter.join();
    }

    private void applyJoins(QueryBuilder builder, JoinBindings joinBindings) {
        if (joins.isEmpty()) {
            return;
        }
        Map<String, List<?>> joinSources = joinBindings.asMap();
        for (TypedJoin join : joins) {
            List<?> joinRows = joinSources.get(join.sourceName());
            if (joinRows == null) {
                throw SqlLikeErrors.argument(
                        SqlLikeErrorCodes.VALIDATION_MISSING_JOIN_SOURCE,
                        "Missing JOIN source binding for '" + join.sourceName() + "'"
                );
            }
            builder.addJoinBeans(join.parentField(), joinRows, join.childField(), join.joinType());
        }
    }

    private void applySelect(QueryBuilder builder) {
        if (!supportsSelectProjection()) {
            return;
        }
        for (TypedField<T, ?> field : selectFields) {
            builder.addField(field.fieldName());
        }
    }

    private void applyWhere(QueryBuilder builder) {
        if (wherePredicate != null) {
            List<List<QueryRule>> disjunction = toDisjunctiveNormalForm(wherePredicate);
            for (List<QueryRule> conjunction : disjunction) {
                builder.allOf(conjunction.toArray(new QueryRule[0]));
            }
        }
    }

    private void applyOrderBy(QueryBuilder builder) {
        for (String fieldName : orderByFieldNames) {
            builder.addOrder(fieldName);
        }
    }

    private void applyGroupBy(QueryBuilder builder) {
        for (String fieldName : groupByFieldNames) {
            builder.addGroup(fieldName);
        }
    }

    private void applyMetrics(QueryBuilder builder) {
        for (TypedMetric metric : metrics) {
            if (metric.count()) {
                builder.addCount(metric.alias());
                continue;
            }
            builder.addMetric(metric.fieldName(), metric.metric(), metric.alias());
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

    private void validateQueryShape() {
        if (!supportsSelectProjection()) {
            throw new IllegalStateException(
                    "TypedQuery select(...) cannot be combined with groupBy/count/metric; "
                            + "grouped output is derived from group and metric definitions."
            );
        }
    }

    private boolean supportsSelectProjection() {
        return selectFields.isEmpty() || (!hasGroupBy() && !hasMetrics());
    }

    private static String normalizeJoinSourceName(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            throw SqlLikeErrors.argument(SqlLikeErrorCodes.JOIN_SOURCE_NAME_INVALID,
                    "sourceName must not be null/blank");
        }
        return sourceName.trim();
    }

    private static String normalizeAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("alias must not be null/blank");
        }
        return alias.trim();
    }

    private static <T> List<List<QueryRule>> toDisjunctiveNormalForm(TypedPredicate<T> node) {
        switch (node.operator()) {
            case AND -> {
                return combineAnd(node.children());
            }
            case OR -> {
                return combineOr(node.children());
            }
            case NOT -> throw new UnsupportedOperationException(
                    "NOT predicates are not supported in TypedQuery. "
                    + "Use negated operators (ne, lte, gte, isNotNull) instead.");
            default -> {
                return List.of(List.of(toQueryRule(node)));
            }
        }
    }

    private static <T> List<List<QueryRule>> combineAnd(List<TypedPredicate<T>> children) {
        List<List<QueryRule>> result = List.of(List.of());
        for (TypedPredicate<T> child : children) {
            List<List<QueryRule>> childGroups = toDisjunctiveNormalForm(child);
            List<List<QueryRule>> combined = new ArrayList<>(result.size() * childGroups.size());
            for (List<QueryRule> left : result) {
                for (List<QueryRule> right : childGroups) {
                    List<QueryRule> conjunction = new ArrayList<>(left.size() + right.size());
                    conjunction.addAll(left);
                    conjunction.addAll(right);
                    combined.add(List.copyOf(conjunction));
                }
            }
            result = List.copyOf(combined);
        }
        return result;
    }

    private static <T> List<List<QueryRule>> combineOr(List<TypedPredicate<T>> children) {
        List<List<QueryRule>> result = new ArrayList<>();
        for (TypedPredicate<T> child : children) {
            result.addAll(toDisjunctiveNormalForm(child));
        }
        return List.copyOf(result);
    }

    private static <T> QueryRule toQueryRule(TypedPredicate<T> leaf) {
        String field = leaf.field().fieldName();
        return switch (leaf.operator()) {
            case EQ -> QueryRule.of(field, leaf.value(), Clauses.EQUAL);
            case NE -> QueryRule.of(field, leaf.value(), Clauses.NOT_EQUAL);
            case GT -> QueryRule.of(field, leaf.value(), Clauses.BIGGER);
            case GTE -> QueryRule.of(field, leaf.value(), Clauses.BIGGER_EQUAL);
            case LT -> QueryRule.of(field, leaf.value(), Clauses.SMALLER);
            case LTE -> QueryRule.of(field, leaf.value(), Clauses.SMALLER_EQUAL);
            case IN -> QueryRule.of(field, leaf.values(), Clauses.IN);
            case IS_NULL -> QueryRule.of(field, null, Clauses.EQUAL);
            case IS_NOT_NULL -> QueryRule.of(field, null, Clauses.NOT_EQUAL);
            default -> throw new UnsupportedOperationException(
                    "Unexpected leaf operator: " + leaf.operator());
        };
    }

    private record TypedJoin(String sourceName,
                             String parentField,
                             String childField,
                             Join joinType) {
    }

    private record TypedMetric(String fieldName,
                               Metric metric,
                               String alias,
                               boolean count) {

        private static TypedMetric of(String fieldName, Metric metric, String alias) {
            return new TypedMetric(fieldName, metric, alias, false);
        }

        private static TypedMetric count(String alias) {
            return new TypedMetric(null, Metric.COUNT, alias, true);
        }
    }
}
