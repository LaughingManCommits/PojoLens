package laughing.man.commits.dsl;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.internal.builder.QueryTimeBucket;
import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.filter.Filter;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.internal.FluentEngine;
import laughing.man.commits.internal.JoinFieldNames;
import laughing.man.commits.internal.LiteralMatchPattern;
import laughing.man.commits.internal.NumericStatistics;
import laughing.man.commits.internal.NameSuggestions;
import laughing.man.commits.internal.builder.QueryBuilder;
import laughing.man.commits.internal.builder.QueryWindowFrame;
import laughing.man.commits.internal.builder.QueryWindowOrder;
import laughing.man.commits.internal.builder.QueryRule;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.PlanPreviewJoin;
import laughing.man.commits.sqllike.PlanPreviewOrder;
import laughing.man.commits.sqllike.PlanPreviewPaging;
import laughing.man.commits.sqllike.PageResult;
import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.QueryDiagnosticsError;
import laughing.man.commits.sqllike.QueryExecutionGuard;
import laughing.man.commits.sqllike.QueryExecutionGuardException;
import laughing.man.commits.sqllike.QueryGuardOutcome;
import laughing.man.commits.sqllike.SqlLikeLintWarning;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
 *   <li>Explicit window-frame configuration is available only for aggregate
 *       windows and {@code COUNT(*)}; rank windows keep their default
 *       semantics and {@code LAG}/{@code LEAD} take no frame.</li>
 *   <li>Windows over grouped queries read grouped outputs only: group fields,
 *       time-bucket aliases, and metric aliases (reference an alias with
 *       {@code TypedField.of(alias, type)}).</li>
 *   <li>{@code NOT(IN_SUBQUERY)} is not supported; use {@code NOT EXISTS} instead.</li>
 * </ul>
 */
public final class TypedQuery<T> {

    private static final int UNSET = -1;
    private static final int DEFAULT_WINDOW_OFFSET = 1;

    private final Class<T> entityClass;
    private final List<TypedField<T, ?>> selectFields;
    private final TypedPredicate<T> wherePredicate;
    private final List<TypedJoin> joins;
    private final List<String> groupByFieldNames;
    private final List<TypedMetric> metrics;
    private final TypedPredicate<?> havingPredicate;
    private final List<TypedWindow> windows;
    private final TypedPredicate<?> qualifyPredicate;
    private final List<QueryTimeBucket> timeBuckets;
    private final List<TypedSortOrder> sortOrders;
    private final int limit;
    private final int offset;
    private final ComputedFieldRegistry computedFieldRegistry;
    private final QueryExecutionGuard executionGuard;
    private final boolean distinct;

    private TypedQuery(Class<T> entityClass,
                       List<TypedField<T, ?>> selectFields,
                       TypedPredicate<T> wherePredicate,
                       List<TypedJoin> joins,
                       List<String> groupByFieldNames,
                       List<TypedMetric> metrics,
                       TypedPredicate<?> havingPredicate,
                       List<TypedWindow> windows,
                       TypedPredicate<?> qualifyPredicate,
                       List<QueryTimeBucket> timeBuckets,
                       List<TypedSortOrder> sortOrders,
                       int limit,
                       int offset,
                       ComputedFieldRegistry computedFieldRegistry,
                       QueryExecutionGuard executionGuard,
                       boolean distinct) {
        this.entityClass = entityClass;
        this.selectFields = List.copyOf(selectFields);
        this.wherePredicate = wherePredicate;
        this.joins = List.copyOf(joins);
        this.groupByFieldNames = List.copyOf(groupByFieldNames);
        this.metrics = List.copyOf(metrics);
        this.havingPredicate = havingPredicate;
        this.windows = List.copyOf(windows);
        this.qualifyPredicate = qualifyPredicate;
        this.timeBuckets = List.copyOf(timeBuckets);
        this.sortOrders = List.copyOf(sortOrders);
        this.limit = limit;
        this.offset = offset;
        this.computedFieldRegistry = computedFieldRegistry == null
                ? ComputedFieldRegistry.empty() : computedFieldRegistry;
        this.executionGuard = executionGuard;
        this.distinct = distinct;
    }

    // --- Factory ---

    public static <T> TypedQuery<T> from(Class<T> entityClass) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        return new TypedQuery<>(entityClass, List.of(), null, List.of(), List.of(), List.of(),
                null, List.of(), null, List.of(), List.of(), UNSET, UNSET, null, null, false);
    }

    // --- Fluent configuration ---

    @SafeVarargs
    public final TypedQuery<T> select(TypedField<T, ?>... fields) {
        Objects.requireNonNull(fields, "fields must not be null");
        return new TypedQuery<>(entityClass, List.of(fields), wherePredicate, joins, groupByFieldNames, metrics,
                havingPredicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    public TypedQuery<T> where(TypedPredicate<T> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        return new TypedQuery<>(entityClass, selectFields, predicate, joins, groupByFieldNames, metrics,
                havingPredicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    /**
     * Joins the rows bound under {@code sourceName}. Field names are validated at execution,
     * once the bound rows show the joined class; use
     * {@link #join(String, Class, TypedField, TypedField, Join)} to also validate them in
     * {@link #diagnostics()} and {@link #planPreview()}.
     */
    public <J, K> TypedQuery<T> join(String sourceName,
                                     TypedField<T, K> parentField,
                                     TypedField<J, K> childField,
                                     Join joinType) {
        return addJoin(sourceName, null, parentField, childField, joinType);
    }

    /**
     * Joins the rows bound under {@code sourceName}, declaring their class so that field
     * names of the joined query are validated without data as well.
     */
    public <J, K> TypedQuery<T> join(String sourceName,
                                     Class<J> sourceClass,
                                     TypedField<T, K> parentField,
                                     TypedField<J, K> childField,
                                     Join joinType) {
        Objects.requireNonNull(sourceClass, "sourceClass must not be null");
        return addJoin(sourceName, sourceClass, parentField, childField, joinType);
    }

    private TypedQuery<T> addJoin(String sourceName,
                                  Class<?> sourceClass,
                                  TypedField<?, ?> parentField,
                                  TypedField<?, ?> childField,
                                  Join joinType) {
        Objects.requireNonNull(parentField, "parentField must not be null");
        Objects.requireNonNull(childField, "childField must not be null");
        Objects.requireNonNull(joinType, "joinType must not be null");
        ArrayList<TypedJoin> updated = new ArrayList<>(joins);
        updated.add(new TypedJoin(
                normalizeJoinSourceName(sourceName),
                parentField.fieldName(),
                childField.fieldName(),
                joinType,
                sourceClass
        ));
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, updated, groupByFieldNames, metrics,
                havingPredicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    public TypedQuery<T> groupBy(TypedField<T, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
        List<String> updated = new ArrayList<>(groupByFieldNames);
        updated.add(field.fieldName());
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                updated, metrics, havingPredicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    public TypedQuery<T> count(String alias) {
        ArrayList<TypedMetric> updated = new ArrayList<>(metrics);
        updated.add(TypedMetric.count(normalizeAlias(alias)));
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, updated, havingPredicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    public TypedQuery<T> count(TypedField<?, ?> outputField) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return count(outputField.fieldName());
    }

    public <V> TypedQuery<T> metric(TypedField<T, V> field, Metric metric, String alias) {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(metric, "metric must not be null");
        if (metric.requiresArgument()) {
            throw new IllegalArgumentException(
                    "Metric.PERCENTILE needs a fraction; use percentile(field, fraction, alias)");
        }
        return addMetric(TypedMetric.of(field.fieldName(), metric, normalizeAlias(alias)));
    }

    /**
     * {@code PERCENTILE}: linear interpolation between the closest ranks, like SQL
     * {@code percentile_cont}; the result is a {@code Double}.
     *
     * @param fraction from 0 to 1 ({@code 0.9} is the 90th percentile)
     */
    public <V> TypedQuery<T> percentile(TypedField<T, V> field, double fraction, String alias) {
        Objects.requireNonNull(field, "field must not be null");
        return addMetric(new TypedMetric(field.fieldName(), Metric.PERCENTILE, normalizeAlias(alias), false,
                NumericStatistics.requirePercentile(fraction)));
    }

    public <V> TypedQuery<T> percentile(TypedField<T, V> field, double fraction, TypedField<?, ?> outputField) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return percentile(field, fraction, outputField.fieldName());
    }

    private TypedQuery<T> addMetric(TypedMetric metric) {
        ArrayList<TypedMetric> updated = new ArrayList<>(metrics);
        updated.add(metric);
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, updated, havingPredicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    public <V> TypedQuery<T> metric(TypedField<T, V> field, Metric metric, TypedField<?, ?> outputField) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return metric(field, metric, outputField.fieldName());
    }

    /**
     * {@code COUNT(DISTINCT field)}: the number of distinct non-null values, compared like
     * group-by keys. Shorthand for {@code metric(field, Metric.COUNT_DISTINCT, alias)}.
     */
    public <V> TypedQuery<T> countDistinct(TypedField<T, V> field, String alias) {
        return metric(field, Metric.COUNT_DISTINCT, alias);
    }

    public <V> TypedQuery<T> countDistinct(TypedField<T, V> field, TypedField<?, ?> outputField) {
        return metric(field, Metric.COUNT_DISTINCT, outputField);
    }

    public TypedQuery<T> having(TypedPredicate<?> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, predicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    @SafeVarargs
    public final TypedQuery<T> window(WindowFunction function,
                                      String alias,
                                      List<TypedWindowOrder> orderFields,
                                      TypedField<?, ?>... partitionFields) {
        Objects.requireNonNull(function, "function must not be null");
        if (!function.isRankFunction()) {
            throw new IllegalArgumentException(
                    "TypedQuery window(function, alias, ...) without a value field only supports "
                            + "ROW_NUMBER, RANK, and DENSE_RANK."
            );
        }
        return addWindow(TypedWindow.rank(function, normalizeAlias(alias),
                partitionFieldNames(partitionFields), normalizedWindowOrders(orderFields)));
    }

    @SafeVarargs
    public final TypedQuery<T> window(WindowFunction function,
                                      TypedField<?, ?> outputField,
                                      List<TypedWindowOrder> orderFields,
                                      TypedField<?, ?>... partitionFields) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return window(function, outputField.fieldName(), orderFields, partitionFields);
    }

    @SafeVarargs
    public final <V> TypedQuery<T> window(WindowFunction function,
                                          TypedField<?, V> valueField,
                                          String alias,
                                          List<TypedWindowOrder> orderFields,
                                          TypedField<?, ?>... partitionFields) {
        return window(function, valueField, alias, QueryWindowFrame.running(), orderFields, partitionFields);
    }

    @SafeVarargs
    public final <V> TypedQuery<T> window(WindowFunction function,
                                          TypedField<?, V> valueField,
                                          String alias,
                                          QueryWindowFrame frame,
                                          List<TypedWindowOrder> orderFields,
                                          TypedField<?, ?>... partitionFields) {
        Objects.requireNonNull(function, "function must not be null");
        Objects.requireNonNull(valueField, "valueField must not be null");
        if (function.isOffsetFunction()) {
            if (!normalizedWindowFrame(frame).isRunning()) {
                throw new IllegalArgumentException("TypedQuery window " + function + " does not accept a window frame.");
            }
            return offsetWindow(function, valueField, normalizeAlias(alias), DEFAULT_WINDOW_OFFSET, null,
                    orderFields, partitionFields);
        }
        if (!function.isAggregateFunction()) {
            throw new IllegalArgumentException(
                    "TypedQuery window(function, valueField, alias, ...) with a value field only supports "
                            + "COUNT, SUM, AVG, MIN, MAX, LAG, and LEAD."
            );
        }
        if (function.requiresNumericField() && !isNumericType(valueField.valueType())) {
            throw new IllegalArgumentException(
                    "TypedQuery window " + function + " requires a numeric value field."
            );
        }
        return addWindow(TypedWindow.value(function, valueField.fieldName(), normalizeAlias(alias),
                partitionFieldNames(partitionFields), normalizedWindowOrders(orderFields),
                normalizedWindowFrame(frame)));
    }

    @SafeVarargs
    public final <V> TypedQuery<T> window(WindowFunction function,
                                          TypedField<?, V> valueField,
                                          TypedField<?, ?> outputField,
                                          List<TypedWindowOrder> orderFields,
                                          TypedField<?, ?>... partitionFields) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return window(function, valueField, outputField.fieldName(), orderFields, partitionFields);
    }

    @SafeVarargs
    public final <V> TypedQuery<T> window(WindowFunction function,
                                          TypedField<?, V> valueField,
                                          TypedField<?, ?> outputField,
                                          QueryWindowFrame frame,
                                          List<TypedWindowOrder> orderFields,
                                          TypedField<?, ?>... partitionFields) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return window(function, valueField, outputField.fieldName(), frame, orderFields, partitionFields);
    }

    /**
     * {@code LAG}: {@code valueField} from the row {@code offset} rows before the current
     * row in its partition (window order), or {@code defaultValue} when that row is
     * outside the partition. A null value at the offset row stays null.
     *
     * @param offset rows to look back, {@code >= 0}; {@code 0} reads the current row
     */
    @SafeVarargs
    public final <V> TypedQuery<T> lag(TypedField<?, V> valueField,
                                       String alias,
                                       int offset,
                                       V defaultValue,
                                       List<TypedWindowOrder> orderFields,
                                       TypedField<?, ?>... partitionFields) {
        return offsetWindow(WindowFunction.LAG, valueField, normalizeAlias(alias), offset, defaultValue,
                orderFields, partitionFields);
    }

    @SafeVarargs
    public final <V> TypedQuery<T> lag(TypedField<?, V> valueField,
                                       TypedField<?, ?> outputField,
                                       int offset,
                                       V defaultValue,
                                       List<TypedWindowOrder> orderFields,
                                       TypedField<?, ?>... partitionFields) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return lag(valueField, outputField.fieldName(), offset, defaultValue, orderFields, partitionFields);
    }

    /**
     * {@code LEAD}: {@code valueField} from the row {@code offset} rows after the current
     * row in its partition (window order), or {@code defaultValue} when that row is
     * outside the partition. A null value at the offset row stays null.
     *
     * @param offset rows to look ahead, {@code >= 0}; {@code 0} reads the current row
     */
    @SafeVarargs
    public final <V> TypedQuery<T> lead(TypedField<?, V> valueField,
                                        String alias,
                                        int offset,
                                        V defaultValue,
                                        List<TypedWindowOrder> orderFields,
                                        TypedField<?, ?>... partitionFields) {
        return offsetWindow(WindowFunction.LEAD, valueField, normalizeAlias(alias), offset, defaultValue,
                orderFields, partitionFields);
    }

    @SafeVarargs
    public final <V> TypedQuery<T> lead(TypedField<?, V> valueField,
                                        TypedField<?, ?> outputField,
                                        int offset,
                                        V defaultValue,
                                        List<TypedWindowOrder> orderFields,
                                        TypedField<?, ?>... partitionFields) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return lead(valueField, outputField.fieldName(), offset, defaultValue, orderFields, partitionFields);
    }

    @SafeVarargs
    private TypedQuery<T> offsetWindow(WindowFunction function,
                                       TypedField<?, ?> valueField,
                                       String alias,
                                       int offset,
                                       Object defaultValue,
                                       List<TypedWindowOrder> orderFields,
                                       TypedField<?, ?>... partitionFields) {
        Objects.requireNonNull(valueField, "valueField must not be null");
        if (offset < 0) {
            throw new IllegalArgumentException("TypedQuery " + function + " offset must be >= 0 but was " + offset
                    + "; use " + (function == WindowFunction.LAG ? "lead" : "lag") + "(...) to look the other way.");
        }
        return addWindow(TypedWindow.offset(function, valueField.fieldName(), alias, offset, defaultValue,
                partitionFieldNames(partitionFields), normalizedWindowOrders(orderFields)));
    }

    @SafeVarargs
    public final TypedQuery<T> windowCountAll(String alias,
                                              List<TypedWindowOrder> orderFields,
                                              TypedField<?, ?>... partitionFields) {
        return windowCountAll(alias, QueryWindowFrame.running(), orderFields, partitionFields);
    }

    @SafeVarargs
    public final TypedQuery<T> windowCountAll(String alias,
                                              QueryWindowFrame frame,
                                              List<TypedWindowOrder> orderFields,
                                              TypedField<?, ?>... partitionFields) {
        return addWindow(TypedWindow.countAll(normalizeAlias(alias),
                partitionFieldNames(partitionFields), normalizedWindowOrders(orderFields),
                normalizedWindowFrame(frame)));
    }

    @SafeVarargs
    public final TypedQuery<T> windowCountAll(TypedField<?, ?> outputField,
                                              List<TypedWindowOrder> orderFields,
                                              TypedField<?, ?>... partitionFields) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return windowCountAll(outputField.fieldName(), orderFields, partitionFields);
    }

    @SafeVarargs
    public final TypedQuery<T> windowCountAll(TypedField<?, ?> outputField,
                                              QueryWindowFrame frame,
                                              List<TypedWindowOrder> orderFields,
                                              TypedField<?, ?>... partitionFields) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return windowCountAll(outputField.fieldName(), frame, orderFields, partitionFields);
    }

    public TypedQuery<T> qualify(TypedPredicate<?> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, havingPredicate, windows, predicate,
                timeBuckets, sortOrders, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    public TypedQuery<T> timeBucket(TypedField<T, ?> dateField, TimeBucket unit, String alias) {
        Objects.requireNonNull(dateField, "dateField must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(alias, "alias must not be null");
        return timeBucket(dateField, TimeBucketPreset.of(unit), alias);
    }

    public TypedQuery<T> timeBucket(TypedField<T, ?> dateField, TimeBucket unit, TypedField<?, ?> outputField) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return timeBucket(dateField, unit, outputField.fieldName());
    }

    public TypedQuery<T> timeBucket(TypedField<T, ?> dateField, TimeBucketPreset preset, String alias) {
        Objects.requireNonNull(dateField, "dateField must not be null");
        Objects.requireNonNull(preset, "preset must not be null");
        Objects.requireNonNull(alias, "alias must not be null");
        ArrayList<QueryTimeBucket> updated = new ArrayList<>(timeBuckets);
        updated.add(QueryTimeBucket.of(dateField.fieldName(), preset, alias));
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, havingPredicate, windows, qualifyPredicate,
                updated, sortOrders, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    public TypedQuery<T> timeBucket(TypedField<T, ?> dateField, TimeBucketPreset preset, TypedField<?, ?> outputField) {
        Objects.requireNonNull(outputField, "outputField must not be null");
        return timeBucket(dateField, preset, outputField.fieldName());
    }

    public TypedQuery<T> orderBy(TypedField<?, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
        List<TypedSortOrder> updated = new ArrayList<>(sortOrders);
        updated.add(TypedSortOrder.asc(field));
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, havingPredicate, windows, qualifyPredicate,
                timeBuckets, updated, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    public TypedQuery<T> orderByDesc(TypedField<?, ?> field) {
        Objects.requireNonNull(field, "field must not be null");
        List<TypedSortOrder> updated = new ArrayList<>(sortOrders);
        updated.add(TypedSortOrder.desc(field));
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, havingPredicate, windows, qualifyPredicate,
                timeBuckets, updated, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    @SafeVarargs
    public final TypedQuery<T> orderBy(TypedSortOrder... orders) {
        Objects.requireNonNull(orders, "orders must not be null");
        if (orders.length == 0) {
            throw new IllegalArgumentException("orderBy(TypedSortOrder...) requires at least one order");
        }
        List<TypedSortOrder> updated = new ArrayList<>(sortOrders);
        for (TypedSortOrder order : orders) {
            Objects.requireNonNull(order, "order element must not be null");
            updated.add(order);
        }
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, havingPredicate, windows, qualifyPredicate,
                timeBuckets, updated, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    /**
     * Returns distinct result rows, like SQL {@code SELECT DISTINCT}: rows whose selected
     * values are equal collapse to the first one in {@code orderBy} order, and
     * {@code offset}/{@code limit} apply afterwards. Values compare like group-by keys
     * ({@code null} and {@code ""} stay apart). With {@code select(...)}, ordering must use
     * selected fields; grouped rows are already distinct.
     */
    public TypedQuery<T> distinct() {
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, havingPredicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, limit, offset, computedFieldRegistry, executionGuard, true);
    }

    public TypedQuery<T> limit(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("limit must be >= 0, got: " + n);
        }
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, havingPredicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, n, offset, computedFieldRegistry, executionGuard, distinct);
    }

    public TypedQuery<T> offset(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("offset must be >= 0, got: " + n);
        }
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, havingPredicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, limit, n, computedFieldRegistry, executionGuard, distinct);
    }

    public TypedQuery<T> computedFields(ComputedFieldRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, havingPredicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, limit, offset, registry, executionGuard, distinct);
    }

    public TypedQuery<T> executionGuard(QueryExecutionGuard guard) {
        Objects.requireNonNull(guard, "guard must not be null");
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, havingPredicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, limit, offset, computedFieldRegistry, guard, distinct);
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

    public TypedPredicate<?> havingPredicate() {
        return havingPredicate;
    }

    public TypedPredicate<?> qualifyPredicate() {
        return qualifyPredicate;
    }

    public List<TypedSortOrder> sortOrders() {
        return sortOrders;
    }

    /** Derived for backward compatibility. Returns the field names in order. */
    public List<String> orderByFieldNames() {
        return sortOrders.stream().map(TypedSortOrder::fieldName).toList();
    }

    /** Derived for backward compatibility. Returns the direction of the first sort order, or ASC if none. */
    public Sort sortDirection() {
        return sortOrders.isEmpty() ? Sort.ASC : sortOrders.get(0).sort();
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

    public boolean hasHaving() {
        return havingPredicate != null;
    }

    public boolean hasWindows() {
        return !windows.isEmpty();
    }

    public boolean hasQualify() {
        return qualifyPredicate != null;
    }

    public boolean hasOrderBy() {
        return !sortOrders.isEmpty();
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

    public boolean hasTimeBuckets() {
        return !timeBuckets.isEmpty();
    }

    public List<QueryTimeBucket> timeBuckets() {
        return timeBuckets;
    }

    public boolean hasLimit() {
        return limit != UNSET;
    }

    public boolean hasOffset() {
        return offset != UNSET;
    }

    public boolean hasComputedFields() {
        return computedFieldRegistry != null && !computedFieldRegistry.isEmpty();
    }

    public ComputedFieldRegistry computedFieldRegistry() {
        return computedFieldRegistry;
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

    public PageResult<T> filterPage(List<T> rows) {
        return filterPage(rows, JoinBindings.empty(), entityClass);
    }

    public <P> PageResult<P> filterPage(List<T> rows, Class<P> projectionClass) {
        return filterPage(rows, JoinBindings.empty(), projectionClass);
    }

    public PageResult<T> filterPage(List<T> rows, JoinBindings joinBindings) {
        return filterPage(rows, joinBindings, entityClass);
    }

    public PageResult<T> filterPage(DatasetBundle datasetBundle) {
        return filterPage(datasetBundle, entityClass);
    }

    public <P> PageResult<P> filterPage(DatasetBundle datasetBundle, Class<P> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return filterPageInternal(datasetBundle.primaryRows(), datasetBundle.joinBindings(), projectionClass);
    }

    public <P> PageResult<P> filterPage(List<T> rows, JoinBindings joinBindings, Class<P> projectionClass) {
        return filterPageInternal(rows, joinBindings, projectionClass);
    }

    public long count(List<T> rows) {
        return filter(rows).size();
    }

    public long count(DatasetBundle datasetBundle) {
        return filter(datasetBundle).size();
    }

    public boolean exists(List<T> rows) {
        return !cappedAt(1).filter(rows).isEmpty();
    }

    public boolean exists(DatasetBundle datasetBundle) {
        return !cappedAt(1).filter(datasetBundle).isEmpty();
    }

    public Optional<T> findFirst(List<T> rows) {
        List<T> result = cappedAt(1).filter(rows);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<T> findFirst(DatasetBundle datasetBundle) {
        List<T> result = cappedAt(1).filter(datasetBundle);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<T> findOne(List<T> rows) {
        List<T> result = cappedAt(2).filter(rows);
        if (result.size() > 1) {
            throw new IllegalStateException(
                    "findOne() expected at most one result but found more than one");
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public Optional<T> findOne(DatasetBundle datasetBundle) {
        List<T> result = cappedAt(2).filter(datasetBundle);
        if (result.size() > 1) {
            throw new IllegalStateException(
                    "findOne() expected at most one result but found more than one");
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * Streams the query result. Simple shapes (filters of any predicate shape, projection,
     * offset, and limit over unjoined rows) are evaluated lazily, row by row, so a consumer
     * that stops early does not pay for the rest; ordered, grouped, windowed, joined,
     * distinct, computed-field, and execution-guarded queries materialise first.
     */
    public Stream<T> stream(List<T> rows) {
        return stream(rows, JoinBindings.empty(), entityClass);
    }

    public Stream<T> stream(DatasetBundle datasetBundle) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return streamInternal(datasetBundle.primaryRows(), datasetBundle.joinBindings(), entityClass);
    }

    public Stream<T> stream(List<T> rows, JoinBindings joinBindings) {
        return stream(rows, joinBindings, entityClass);
    }

    public <P> Stream<P> stream(List<T> rows, JoinBindings joinBindings, Class<P> projectionClass) {
        return streamInternal(rows, joinBindings, projectionClass);
    }

    // iterator() wraps stream(...), with the same laziness; remove() is unsupported.
    public Iterator<T> iterator(List<T> rows) {
        return stream(rows).iterator();
    }

    public Iterator<T> iterator(DatasetBundle datasetBundle) {
        return stream(datasetBundle).iterator();
    }

    public Iterator<T> iterator(List<T> rows, JoinBindings joinBindings) {
        return stream(rows, joinBindings).iterator();
    }

    public <P> Iterator<P> iterator(List<T> rows, JoinBindings joinBindings, Class<P> projectionClass) {
        return stream(rows, joinBindings, projectionClass).iterator();
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

    public <P> TabularSchema schema(Class<P> projectionClass) {
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        return schemaInternal(List.of(), schemaJoinBindings(), projectionClass);
    }

    public <P> TabularSchema schema(List<T> rows, JoinBindings joinBindings, Class<P> projectionClass) {
        return schemaInternal(rows, joinBindings, projectionClass);
    }

    public <P> TabularSchema schema(DatasetBundle datasetBundle, Class<P> projectionClass) {
        Objects.requireNonNull(datasetBundle, "datasetBundle must not be null");
        return schemaInternal(datasetBundle.primaryRows(), datasetBundle.joinBindings(), projectionClass);
    }

    public QueryDiagnostics diagnostics() {
        ArrayList<QueryDiagnosticsError> errors = new ArrayList<>();
        try {
            validateQueryShape();
        } catch (RuntimeException ex) {
            errors.add(new QueryDiagnosticsError("EQ-TYPED-ERR", ex.getMessage()));
        }
        collectSubqueryDiagnosticsErrors(wherePredicate, errors);
        collectSubqueryDiagnosticsErrors(havingPredicate, errors);
        collectSubqueryDiagnosticsErrors(qualifyPredicate, errors);
        return new QueryDiagnostics(
                errors.isEmpty(),
                errors,
                List.<SqlLikeLintWarning>of(),
                List.of(),
                previewReferencedFields(),
                previewOutputFields(),
                previewJoinSources(),
                previewHasSubqueries()
        );
    }

    public TypedPlanPreview planPreview() {
        validateQueryShape();
        return buildPlanPreview();
    }

    private TypedQuery<T> cappedAt(int n) {
        if (hasLimit() && limit <= n) {
            return this;
        }
        return limit(n);
    }

    private TypedQuery<T> withoutPaginationAndGuard() {
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, havingPredicate, windows, qualifyPredicate,
                timeBuckets, sortOrders, UNSET, UNSET, computedFieldRegistry, null, distinct);
    }

    private void validatePageShape() {
        if (!hasLimit()) {
            throw new IllegalStateException("TypedQuery.filterPage(...) requires limit(...) to define page size.");
        }
        if (limit <= 0) {
            throw new IllegalStateException("TypedQuery.filterPage(...) requires limit(...) to be greater than zero.");
        }
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
        if (wherePredicate != null && wherePredicate.operator() == TypedPredicate.Operator.NONE) {
            return List.of();
        }
        if (executionGuard != null) {
            applyPreExecutionGuard(rows.size());
        }
        if (rows.isEmpty() && joins.isEmpty() && groupByFieldNames.isEmpty() && metrics.isEmpty()) {
            return List.of();
        }
        QueryBuilder builder = configuredBuilder(rows, joinBindings);
        Filter filter = preparedFilter(builder);
        long startMillis = System.currentTimeMillis();
        List<P> result = filter.filter(projectionClass);
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
     * Guarded queries keep the materialising path, which applies every guard check.
     */
    private <P> Stream<P> streamInternal(List<?> rows, JoinBindings joinBindings, Class<P> projectionClass) {
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        if (executionGuard != null) {
            return filterInternal(rows, joinBindings, projectionClass).stream();
        }
        if (wherePredicate != null && wherePredicate.operator() == TypedPredicate.Operator.NONE) {
            return Stream.empty();
        }
        return preparedFilter(configuredBuilder(rows, joinBindings)).stream(projectionClass);
    }

    private <P> PageResult<P> filterPageInternal(List<?> rows,
                                                 JoinBindings joinBindings,
                                                 Class<P> projectionClass) {
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        validatePageShape();
        long totalRows = withoutPaginationAndGuard()
                .filterInternal(rows, joinBindings, projectionClass)
                .size();
        List<P> pageRows = filterInternal(rows, joinBindings, projectionClass);
        long pageStart = hasOffset() ? offset : 0L;
        boolean hasMore = pageStart + pageRows.size() < totalRows;
        return PageResult.of(pageRows, totalRows, hasMore);
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

    private JoinBindings schemaJoinBindings() {
        if (joins.isEmpty()) {
            return JoinBindings.empty();
        }
        LinkedHashMap<String, List<?>> bindings = new LinkedHashMap<>();
        for (TypedJoin join : joins) {
            bindings.putIfAbsent(join.sourceName(), List.of());
        }
        return JoinBindings.from(bindings);
    }

    private TypedPlanPreview buildPlanPreview() {
        return new TypedPlanPreview(
                entityClass.getSimpleName(),
                entityClass,
                previewSelectFields(),
                buildPlanPredicate(wherePredicate),
                List.copyOf(groupByFieldNames),
                previewMetrics(),
                buildPlanPredicate(havingPredicate),
                previewWindows(),
                buildPlanPredicate(qualifyPredicate),
                previewOrderFields(sortOrders),
                previewJoins(),
                previewPaging(),
                previewTimeBuckets(),
                previewComputedFields(),
                previewReferencedFields(),
                previewOutputFields(),
                previewJoinSources(),
                executionGuard,
                previewHasSubqueries()
        );
    }

    private List<String> previewSelectFields() {
        ArrayList<String> fields = new ArrayList<>(selectFields.size());
        for (TypedField<T, ?> field : selectFields) {
            fields.add(field.fieldName());
        }
        return List.copyOf(fields);
    }

    private List<TypedPlanMetric> previewMetrics() {
        ArrayList<TypedPlanMetric> preview = new ArrayList<>(metrics.size());
        for (TypedMetric metric : metrics) {
            preview.add(new TypedPlanMetric(metric.fieldName(), metric.metric(), metric.alias(), metric.count()));
        }
        return List.copyOf(preview);
    }

    private List<TypedPlanWindow> previewWindows() {
        ArrayList<TypedPlanWindow> preview = new ArrayList<>(windows.size());
        for (TypedWindow window : windows) {
            preview.add(new TypedPlanWindow(
                    window.function(),
                    window.valueField(),
                    window.countAll(),
                    window.alias(),
                    window.partitionFields(),
                    previewWindowOrders(window.orderFields()),
                    window.frame(),
                    window.offset(),
                    window.defaultValue()
            ));
        }
        return List.copyOf(preview);
    }

    private List<PlanPreviewJoin> previewJoins() {
        ArrayList<PlanPreviewJoin> preview = new ArrayList<>(joins.size());
        for (TypedJoin join : joins) {
            preview.add(new PlanPreviewJoin(previewJoinType(join.joinType()), join.sourceName(),
                    join.parentField(), join.childField()));
        }
        return List.copyOf(preview);
    }

    private PlanPreviewPaging previewPaging() {
        if (!hasLimit() && !hasOffset()) {
            return null;
        }
        return new PlanPreviewPaging(hasLimit() ? limit : null, null, hasOffset() ? offset : null, null);
    }

    private List<TypedPlanTimeBucket> previewTimeBuckets() {
        ArrayList<TypedPlanTimeBucket> preview = new ArrayList<>(timeBuckets.size());
        for (QueryTimeBucket bucket : timeBuckets) {
            preview.add(new TypedPlanTimeBucket(
                    bucket.getDateField(),
                    bucket.getBucket(),
                    bucket.getAlias(),
                    bucket.getPreset().explainToken()
            ));
        }
        return List.copyOf(preview);
    }

    private List<String> previewComputedFields() {
        if (!hasComputedFields()) {
            return List.of();
        }
        return computedFieldRegistry.names().stream().toList();
    }

    private List<String> previewReferencedFields() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (TypedField<T, ?> field : selectFields) {
            names.add(field.fieldName());
        }
        collectReferencedFields(wherePredicate, names);
        collectReferencedFields(havingPredicate, names);
        collectReferencedFields(qualifyPredicate, names);
        for (TypedJoin join : joins) {
            names.add(join.parentField());
            names.add(join.childField());
        }
        names.addAll(groupByFieldNames);
        for (TypedMetric metric : metrics) {
            if (metric.fieldName() != null) {
                names.add(metric.fieldName());
            }
        }
        for (TypedWindow window : windows) {
            if (window.valueField() != null) {
                names.add(window.valueField());
            }
            names.addAll(window.partitionFields());
            for (TypedWindowOrder order : window.orderFields()) {
                names.add(order.fieldName());
            }
        }
        for (QueryTimeBucket bucket : timeBuckets) {
            names.add(bucket.getDateField());
        }
        for (TypedSortOrder order : sortOrders) {
            names.add(order.fieldName());
        }
        return List.copyOf(names);
    }

    private List<String> previewOutputFields() {
        LinkedHashSet<String> outputs = new LinkedHashSet<>();
        if (hasGroupBy() || hasMetrics()) {
            for (QueryTimeBucket bucket : timeBuckets) {
                outputs.add(bucket.getAlias());
            }
            outputs.addAll(groupByFieldNames);
            for (TypedMetric metric : metrics) {
                outputs.add(metric.alias());
            }
            for (TypedWindow window : windows) {
                outputs.add(window.alias());
            }
            return List.copyOf(outputs);
        }
        if (selectFields.isEmpty()) {
            outputs.addAll(ReflectionUtil.collectQueryableFieldTypes(entityClass).keySet());
        } else {
            for (TypedField<T, ?> field : selectFields) {
                outputs.add(field.fieldName());
            }
        }
        for (QueryTimeBucket bucket : timeBuckets) {
            outputs.add(bucket.getAlias());
        }
        for (TypedWindow window : windows) {
            outputs.add(window.alias());
        }
        return List.copyOf(outputs);
    }

    private List<String> previewJoinSources() {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        for (TypedJoin join : joins) {
            sources.add(join.sourceName());
        }
        return List.copyOf(sources);
    }

    private boolean previewHasSubqueries() {
        return containsSubqueryPredicate(wherePredicate)
                || containsSubqueryPredicate(havingPredicate)
                || containsSubqueryPredicate(qualifyPredicate);
    }

    private void collectSubqueryDiagnosticsErrors(TypedPredicate<?> predicate,
                                                  List<QueryDiagnosticsError> errors) {
        if (predicate == null) {
            return;
        }
        if (predicate.hasSubqueryDescriptor()) {
            try {
                predicate.subqueryDescriptor().subquery().validateQueryShape();
            } catch (RuntimeException ex) {
                errors.add(new QueryDiagnosticsError("EQ-TYPED-ERR", ex.getMessage()));
            }
        }
        for (TypedPredicate<?> child : predicate.children()) {
            collectSubqueryDiagnosticsErrors(child, errors);
        }
    }

    private TypedPlanPredicate buildPlanPredicate(TypedPredicate<?> predicate) {
        if (predicate == null) {
            return null;
        }
        ArrayList<TypedPlanPredicate> children = new ArrayList<>(predicate.children().size());
        for (TypedPredicate<?> child : predicate.children()) {
            children.add(buildPlanPredicate(child));
        }
        TypedPredicate.TypedSubqueryDescriptor descriptor = predicate.hasSubqueryDescriptor()
                ? predicate.subqueryDescriptor() : null;
        return new TypedPlanPredicate(
                predicate.operator(),
                predicate.field() == null ? null : predicate.field().fieldName(),
                predicate.value(),
                predicate.values(),
                descriptor == null ? null : descriptor.outputField(),
                descriptor != null && descriptor.explicitSource(),
                descriptor == null ? null : descriptor.subquery().planPreview(),
                children
        );
    }

    private static List<PlanPreviewOrder> previewOrderFields(List<TypedSortOrder> orders) {
        ArrayList<PlanPreviewOrder> preview = new ArrayList<>(orders.size());
        for (TypedSortOrder order : orders) {
            preview.add(new PlanPreviewOrder(order.fieldName(), order.sort().name()));
        }
        return List.copyOf(preview);
    }

    private static List<PlanPreviewOrder> previewWindowOrders(List<TypedWindowOrder> orders) {
        ArrayList<PlanPreviewOrder> preview = new ArrayList<>(orders.size());
        for (TypedWindowOrder order : orders) {
            preview.add(new PlanPreviewOrder(order.fieldName(), order.sort().name()));
        }
        return List.copyOf(preview);
    }

    private static String previewJoinType(Join joinType) {
        return switch (joinType) {
            case INNER_JOIN -> "INNER";
            case LEFT_JOIN -> "LEFT";
            case RIGHT_JOIN -> "RIGHT";
        };
    }

    // --- Internal lowering ---

    private QueryBuilder configuredBuilder(List<?> rows, JoinBindings joinBindings) {
        QueryBuilder builder = FluentEngine.newQueryBuilder(rows);
        applyToBuilder(builder, joinBindings);
        return builder;
    }

    void applyToBuilder(QueryBuilder builder, JoinBindings joinBindings) {
        Objects.requireNonNull(builder, "builder must not be null");
        Objects.requireNonNull(joinBindings, "joinBindings must not be null");
        validateQueryShape(joinSourceClasses(joinBindings));
        applyJoins(builder, joinBindings);
        if (hasComputedFields()) {
            builder.computedFields(computedFieldRegistry);
        }
        applySelect(builder);
        applyWhere(builder, joinBindings);
        applyTimeBuckets(builder);
        applyGroupBy(builder);
        applyMetrics(builder);
        applyHaving(builder);
        applyWindows(builder);
        applyQualify(builder);
        applyOrderBy(builder);
        if (distinct) {
            builder.distinctRows();
        }
        applyLimit(builder);
        applyOffset(builder);
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

    private void applyWhere(QueryBuilder builder, JoinBindings joinBindings) {
        if (wherePredicate == null || wherePredicate.operator() == TypedPredicate.Operator.ANY) {
            return;
        }
        List<List<QueryRule>> disjunction = toDisjunctiveNormalForm(wherePredicate, joinBindings);
        for (List<QueryRule> conjunction : disjunction) {
            builder.allOf(conjunction.toArray(new QueryRule[0]));
        }
    }

    private void applyHaving(QueryBuilder builder) {
        if (havingPredicate == null) {
            return;
        }
        List<List<QueryRule>> disjunction = toDisjunctiveNormalForm(havingPredicate, JoinBindings.empty());
        for (List<QueryRule> conjunction : disjunction) {
            builder.addHavingAllOf(conjunction.toArray(new QueryRule[0]));
        }
    }

    private void applyWindows(QueryBuilder builder) {
        for (TypedWindow window : windows) {
            List<QueryWindowOrder> queryOrders = new ArrayList<>(window.orderFields().size());
            for (TypedWindowOrder order : window.orderFields()) {
                queryOrders.add(QueryWindowOrder.of(order.fieldName(), order.sort()));
            }
            if (window.function().isOffsetFunction()) {
                builder.addOffsetWindow(
                        window.alias(),
                        window.function(),
                        window.valueField(),
                        window.offset(),
                        window.defaultValue(),
                        window.partitionFields(),
                        queryOrders
                );
                continue;
            }
            builder.addWindow(
                    window.alias(),
                    window.function(),
                    window.valueField(),
                    window.countAll(),
                    window.partitionFields(),
                    queryOrders,
                    window.frame()
            );
        }
    }

    private void applyQualify(QueryBuilder builder) {
        if (qualifyPredicate == null) {
            return;
        }
        List<List<QueryRule>> disjunction = toDisjunctiveNormalForm(qualifyPredicate, JoinBindings.empty());
        for (List<QueryRule> conjunction : disjunction) {
            builder.addQualifyAllOf(conjunction.toArray(new QueryRule[0]));
        }
    }

    private void applyOrderBy(QueryBuilder builder) {
        int orderIndex = 1;
        for (TypedSortOrder order : sortOrders) {
            builder.addOrder(order.fieldName(), orderIndex++, order.sort());
        }
    }

    private void applyTimeBuckets(QueryBuilder builder) {
        for (QueryTimeBucket bucket : timeBuckets) {
            builder.addTimeBucket(bucket.getDateField(), bucket.getPreset(), bucket.getAlias());
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
            if (metric.metric() == Metric.PERCENTILE) {
                builder.addPercentile(metric.fieldName(), metric.argument(), metric.alias());
            } else {
                builder.addMetric(metric.fieldName(), metric.metric(), metric.alias());
            }
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
        validateQueryShape(declaredJoinSourceClasses());
    }

    /**
     * @param joinSourceClasses row class per join source name; field names are not
     *                          validated while a joined source class is unknown
     */
    private void validateQueryShape(Map<String, Class<?>> joinSourceClasses) {
        if (!supportsSelectProjection()) {
            throw new IllegalStateException(
                    "TypedQuery select(...) cannot be combined with groupBy/count/metric; "
                            + "grouped output is derived from group and metric definitions."
            );
        }
        validateWhereSubqueryShape();
        validateHavingShape();
        validateWindowShape();
        validateQualifyShape();
        validateDistinctShape();
        validateFieldReferences(joinSourceClasses);
    }

    private Map<String, Class<?>> declaredJoinSourceClasses() {
        HashMap<String, Class<?>> classes = new HashMap<>();
        for (TypedJoin join : joins) {
            if (join.sourceClass() != null) {
                classes.put(join.sourceName(), join.sourceClass());
            }
        }
        return classes;
    }

    /**
     * Join source classes seen in the bound rows, falling back to the declared classes.
     */
    private Map<String, Class<?>> joinSourceClasses(JoinBindings joinBindings) {
        Map<String, Class<?>> classes = declaredJoinSourceClasses();
        Map<String, List<?>> sources = joinBindings.asMap();
        for (TypedJoin join : joins) {
            Class<?> rowClass = beanRowClass(sources.get(join.sourceName()));
            if (rowClass == null) {
                continue;
            }
            if (join.sourceClass() != null && !join.sourceClass().isAssignableFrom(rowClass)) {
                throw new IllegalArgumentException("JOIN source '" + join.sourceName() + "' is bound to "
                        + rowClass.getSimpleName() + " rows, but join(...) declared "
                        + join.sourceClass().getSimpleName());
            }
            classes.put(join.sourceName(), rowClass);
        }
        return classes;
    }

    /**
     * The class of the first non-null row, or {@code null} for no rows or rows without a
     * reflective schema ({@code QueryRow}, maps).
     */
    private static Class<?> beanRowClass(List<?> rows) {
        if (rows == null) {
            return null;
        }
        for (Object row : rows) {
            if (row != null) {
                return row instanceof QueryRow || row instanceof Map<?, ?> ? null : row.getClass();
            }
        }
        return null;
    }

    private void validateDistinctShape() {
        if (!distinct || selectFields.isEmpty()) {
            return;
        }
        Set<String> selected = new LinkedHashSet<>();
        for (TypedField<T, ?> field : selectFields) {
            selected.add(field.fieldName());
        }
        for (TypedSortOrder order : sortOrders) {
            if (!selected.contains(order.fieldName())) {
                throw new IllegalStateException("distinct() with orderBy(" + order.fieldName()
                        + ") requires ordering by a selected field; add it to select(...)");
            }
        }
    }

    /**
     * Rejects field names the queried rows do not have (a typo in {@code TypedField.of(...)}
     * would otherwise silently match nothing), like SQL-like's unknown-field validation.
     * Joined queries check against the joined rows once every joined source class is known.
     */
    private void validateFieldReferences(Map<String, Class<?>> joinSourceClasses) {
        Map<String, Class<?>> rowFieldTypes = rowFieldTypes(joinSourceClasses);
        if (rowFieldTypes == null || rowFieldTypes.isEmpty()) {
            return;
        }
        LinkedHashSet<String> sourceFields = new LinkedHashSet<>(rowFieldTypes.keySet());
        sourceFields.addAll(computedFieldRegistry.names());
        LinkedHashSet<String> outputAliases = new LinkedHashSet<>();
        for (QueryTimeBucket bucket : timeBuckets) {
            outputAliases.add(bucket.getAlias());
        }
        for (TypedMetric metric : metrics) {
            outputAliases.add(metric.alias());
        }
        for (TypedWindow window : windows) {
            outputAliases.add(window.alias());
        }
        LinkedHashSet<String> sourceOrAlias = new LinkedHashSet<>(sourceFields);
        sourceOrAlias.addAll(outputAliases);

        for (TypedField<T, ?> field : selectFields) {
            requireKnownField(field.fieldName(), sourceFields, "select");
        }
        for (String fieldName : referencedFields(wherePredicate)) {
            requireKnownField(fieldName, sourceFields, "where");
        }
        for (String fieldName : groupByFieldNames) {
            requireKnownField(fieldName, sourceOrAlias, "groupBy");
        }
        for (TypedMetric metric : metrics) {
            if (metric.fieldName() != null) {
                requireKnownField(metric.fieldName(), sourceFields, "metric");
            }
        }
        for (QueryTimeBucket bucket : timeBuckets) {
            requireKnownField(bucket.getDateField(), sourceFields, "timeBucket");
        }
        Set<String> windowFields = hasGroupBy() || hasMetrics() ? groupedOutputFields() : sourceFields;
        for (TypedWindow window : windows) {
            if (window.valueField() != null) {
                requireKnownField(window.valueField(), windowFields, "window");
            }
            for (String partition : window.partitionFields()) {
                requireKnownField(partition, windowFields, "window partition");
            }
            for (TypedWindowOrder order : window.orderFields()) {
                requireKnownField(order.fieldName(), windowFields, "window order");
            }
        }
        for (String fieldName : referencedFields(qualifyPredicate)) {
            requireKnownField(fieldName, sourceOrAlias, "qualify");
        }
        for (TypedSortOrder order : sortOrders) {
            requireKnownField(order.fieldName(), sourceOrAlias, "orderBy");
        }
    }

    /**
     * Field types of the rows the query filters: the entity's, merged with each joined
     * source's by the engine's join naming rule ({@code child_} prefix on collisions).
     * Also rejects join keys that the rows being joined do not have, which would
     * otherwise skip the join. {@code null} while a joined source class is unknown.
     */
    private Map<String, Class<?>> rowFieldTypes(Map<String, Class<?>> joinSourceClasses) {
        Map<String, Class<?>> current = ReflectionUtil.collectQueryableFieldTypes(entityClass);
        if (current.isEmpty()) {
            return null;
        }
        for (TypedJoin join : joins) {
            Class<?> sourceClass = joinSourceClasses.get(join.sourceName());
            if (sourceClass == null) {
                return null;
            }
            Map<String, Class<?>> joined = ReflectionUtil.collectQueryableFieldTypes(sourceClass);
            requireKnownField(join.parentField(), current.keySet(), "join", fieldOwner());
            requireKnownField(join.childField(), joined.keySet(), "join",
                    "source '" + join.sourceName() + "' (" + sourceClass.getSimpleName() + ")");
            current = JoinFieldNames.merge(current, joined, join.joinType());
        }
        return current;
    }

    private String fieldOwner() {
        if (joins.isEmpty()) {
            return entityClass.getSimpleName();
        }
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        for (TypedJoin join : joins) {
            sources.add(join.sourceName());
        }
        return entityClass.getSimpleName() + " joined with " + String.join(", ", sources);
    }

    /**
     * Columns of grouped rows: group fields, time-bucket aliases, and metric aliases.
     */
    private Set<String> groupedOutputFields() {
        LinkedHashSet<String> outputs = new LinkedHashSet<>(groupByFieldNames);
        for (QueryTimeBucket bucket : timeBuckets) {
            outputs.add(bucket.getAlias());
        }
        for (TypedMetric metric : metrics) {
            outputs.add(metric.alias());
        }
        return outputs;
    }

    private void requireKnownField(String fieldName, Set<String> knownFields, String clause) {
        requireKnownField(fieldName, knownFields, clause, fieldOwner());
    }

    private static void requireKnownField(String fieldName, Set<String> knownFields, String clause, String owner) {
        if (fieldName == null || knownFields.contains(fieldName)
                || SqlExpressionEvaluator.looksLikeExpression(fieldName)) {
            return;
        }
        List<String> suggestions = NameSuggestions.suggest(fieldName, knownFields);
        throw new IllegalArgumentException("Unknown field '" + fieldName + "' in " + clause + "(...) for "
                + owner
                + (suggestions.isEmpty() ? "" : "; did you mean " + String.join(", ", suggestions) + "?")
                + " Known fields: " + knownFields);
    }

    private boolean supportsSelectProjection() {
        return selectFields.isEmpty() || (!hasGroupBy() && !hasMetrics());
    }

    private void validateHavingShape() {
        if (havingPredicate == null) {
            return;
        }
        if (containsSubqueryPredicate(havingPredicate)) {
            throw new IllegalStateException(
                    "TypedQuery subquery predicates are only supported in where(...)."
            );
        }
        if (!hasGroupBy() && !hasMetrics()) {
            throw new IllegalStateException(
                    "TypedQuery having(...) requires groupBy(...) or count/metric output."
            );
        }
        List<String> allowedFields = new ArrayList<>(groupByFieldNames);
        for (TypedMetric metric : metrics) {
            allowedFields.add(metric.alias());
        }
        for (String fieldName : referencedFields(havingPredicate)) {
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalStateException(
                        "TypedQuery having(...) field '" + fieldName
                                + "' must match a grouped field or metric alias."
                );
            }
        }
    }

    private void validateWindowShape() {
        if (windows.isEmpty()) {
            return;
        }
        if (hasGroupBy() && !hasMetrics()) {
            throw new IllegalStateException(
                    "TypedQuery windows over grouped rows require count/metric output."
            );
        }
    }

    private void validateQualifyShape() {
        if (qualifyPredicate == null) {
            return;
        }
        if (containsSubqueryPredicate(qualifyPredicate)) {
            throw new IllegalStateException(
                    "TypedQuery subquery predicates are only supported in where(...)."
            );
        }
        if (windows.isEmpty()) {
            throw new IllegalStateException(
                    "TypedQuery qualify(...) requires at least one window output."
            );
        }
        List<String> allowedFields = new ArrayList<>(windows.size());
        for (TypedWindow window : windows) {
            allowedFields.add(window.alias());
        }
        for (String fieldName : referencedFields(qualifyPredicate)) {
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalStateException(
                        "TypedQuery qualify(...) field '" + fieldName
                                + "' must match a selected window output alias."
                );
            }
        }
    }

    private void validateWhereSubqueryShape() {
        if (wherePredicate == null || !containsSubqueryPredicate(wherePredicate)) {
            return;
        }
        validateSubqueryLeaves(wherePredicate);
    }

    private static void validateSubqueryLeaves(TypedPredicate<?> predicate) {
        if (predicate == null) {
            return;
        }
        if (predicate.hasSubqueryDescriptor()) {
            if (predicate.operator() == TypedPredicate.Operator.IN_SUBQUERY && predicate.field() == null) {
                throw new IllegalStateException(
                        "TypedQuery IN subquery predicates require a target field."
                );
            }
            return;
        }
        for (TypedPredicate<?> child : predicate.children()) {
            validateSubqueryLeaves(child);
        }
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

    @SafeVarargs
    private static List<String> partitionFieldNames(TypedField<?, ?>... partitionFields) {
        Objects.requireNonNull(partitionFields, "partitionFields must not be null");
        List<String> fieldNames = new ArrayList<>(partitionFields.length);
        for (TypedField<?, ?> field : partitionFields) {
            Objects.requireNonNull(field, "partition field must not be null");
            fieldNames.add(field.fieldName());
        }
        return List.copyOf(fieldNames);
    }

    private static List<TypedWindowOrder> normalizedWindowOrders(List<TypedWindowOrder> orderFields) {
        Objects.requireNonNull(orderFields, "orderFields must not be null");
        if (orderFields.isEmpty()) {
            throw new IllegalArgumentException("TypedQuery window orderFields must not be empty.");
        }
        List<TypedWindowOrder> normalized = new ArrayList<>(orderFields.size());
        for (TypedWindowOrder order : orderFields) {
            Objects.requireNonNull(order, "window order entry must not be null");
            normalized.add(order);
        }
        return List.copyOf(normalized);
    }

    private static QueryWindowFrame normalizedWindowFrame(QueryWindowFrame frame) {
        return Objects.requireNonNull(frame, "frame must not be null");
    }

    private static boolean isNumericType(Class<?> type) {
        if (type == null) {
            return false;
        }
        if (Number.class.isAssignableFrom(type)) {
            return true;
        }
        return type == byte.class
                || type == short.class
                || type == int.class
                || type == long.class
                || type == float.class
                || type == double.class;
    }

    private TypedQuery<T> addWindow(TypedWindow window) {
        ArrayList<TypedWindow> updated = new ArrayList<>(windows);
        updated.add(window);
        return new TypedQuery<>(entityClass, selectFields, wherePredicate, joins,
                groupByFieldNames, metrics, havingPredicate, updated, qualifyPredicate,
                timeBuckets, sortOrders, limit, offset, computedFieldRegistry, executionGuard, distinct);
    }

    private static <T> List<List<QueryRule>> toDisjunctiveNormalForm(TypedPredicate<T> node,
                                                                     JoinBindings joinBindings) {
        switch (node.operator()) {
            case ANY -> {
                return List.of(List.of()); // one empty conjunction = always-true identity
            }
            case NONE -> {
                return List.of(); // no disjuncts = always-false (caller must short-circuit)
            }
            case AND -> {
                return combineAnd(node.children(), joinBindings);
            }
            case OR -> {
                return combineOr(node.children(), joinBindings);
            }
            case NOT -> {
                TypedPredicate<T> child = node.children().get(0);
                if (child.isTextMatch()) {
                    return List.of(List.of(toTextRule(child, true)));
                }
                return toDisjunctiveNormalForm(TypedPredicate.negate(child), joinBindings);
            }
            default -> {
                return List.of(List.of(toQueryRule(node, joinBindings)));
            }
        }
    }

    private static <T> List<List<QueryRule>> combineAnd(List<TypedPredicate<T>> children,
                                                        JoinBindings joinBindings) {
        List<List<QueryRule>> result = List.of(List.of());
        for (TypedPredicate<T> child : children) {
            List<List<QueryRule>> childGroups = toDisjunctiveNormalForm(child, joinBindings);
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

    private static <T> List<List<QueryRule>> combineOr(List<TypedPredicate<T>> children,
                                                       JoinBindings joinBindings) {
        List<List<QueryRule>> result = new ArrayList<>();
        for (TypedPredicate<T> child : children) {
            result.addAll(toDisjunctiveNormalForm(child, joinBindings));
        }
        return List.copyOf(result);
    }

    private static <T> QueryRule toQueryRule(TypedPredicate<T> leaf, JoinBindings joinBindings) {
        String field = leaf.field() == null ? null : leaf.field().fieldName();
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
            case CONTAINS, CONTAINS_IGNORE_CASE, MATCHES, STARTS_WITH, ENDS_WITH -> toTextRule(leaf, false);
            case IN_SUBQUERY -> toInSubqueryRule(leaf, joinBindings);
            case EXISTS -> toExistsRule(leaf, joinBindings, false);
            case NOT_EXISTS -> toExistsRule(leaf, joinBindings, true);
            default -> throw new UnsupportedOperationException(
                    "Unexpected leaf operator: " + leaf.operator());
        };
    }

    /**
     * Text-match leaf, optionally negated: literal forms lower to a {@code MATCHES} regex
     * through {@link LiteralMatchPattern}, so negation only flips the engine clause.
     */
    private static <T> QueryRule toTextRule(TypedPredicate<T> leaf, boolean negated) {
        String field = leaf.field().fieldName();
        String value = (String) leaf.value();
        Clauses matches = negated ? Clauses.NOT_MATCHES : Clauses.MATCHES;
        return switch (leaf.operator()) {
            case CONTAINS -> QueryRule.of(field, value, negated ? Clauses.NOT_CONTAINS : Clauses.CONTAINS);
            case CONTAINS_IGNORE_CASE -> QueryRule.of(field, LiteralMatchPattern.CONTAINS_IGNORE_CASE.toRegex(value), matches);
            case MATCHES -> QueryRule.of(field, value, matches);
            case STARTS_WITH -> QueryRule.of(field, LiteralMatchPattern.STARTS_WITH.toRegex(value), matches);
            case ENDS_WITH -> QueryRule.of(field, LiteralMatchPattern.ENDS_WITH.toRegex(value), matches);
            default -> throw new IllegalStateException("Not a text-match operator: " + leaf.operator());
        };
    }

    private static <T> QueryRule toInSubqueryRule(TypedPredicate<T> leaf, JoinBindings inheritedJoinBindings) {
        TypedPredicate.TypedSubqueryDescriptor descriptor = leaf.subqueryDescriptor();
        if (descriptor.explicitSource()) {
            return QueryRule.inSubquery(
                    leaf.field().fieldName(),
                    descriptor.sourceRows(),
                    descriptor.outputField(),
                    builder -> descriptor.subquery().applyToBuilder(builder, JoinBindings.empty())
            );
        }
        return QueryRule.inSubquery(
                leaf.field().fieldName(),
                descriptor.outputField(),
                builder -> descriptor.subquery().applyToBuilder(builder, inheritedJoinBindings)
        );
    }

    private static QueryRule toExistsRule(TypedPredicate<?> leaf,
                                          JoinBindings inheritedJoinBindings,
                                          boolean negated) {
        TypedPredicate.TypedSubqueryDescriptor descriptor = leaf.subqueryDescriptor();
        if (descriptor.explicitSource()) {
            return negated
                    ? QueryRule.notExists(
                    descriptor.sourceRows(),
                    builder -> descriptor.subquery().applyToBuilder(builder, JoinBindings.empty())
            )
                    : QueryRule.exists(
                    descriptor.sourceRows(),
                    builder -> descriptor.subquery().applyToBuilder(builder, JoinBindings.empty())
            );
        }
        return negated
                ? QueryRule.notExists(builder -> descriptor.subquery().applyToBuilder(builder, inheritedJoinBindings))
                : QueryRule.exists(builder -> descriptor.subquery().applyToBuilder(builder, inheritedJoinBindings));
    }

    private static List<String> referencedFields(TypedPredicate<?> predicate) {
        List<String> fieldNames = new ArrayList<>();
        collectReferencedFields(predicate, fieldNames);
        return List.copyOf(fieldNames);
    }

    private static void collectReferencedFields(TypedPredicate<?> predicate, List<String> fieldNames) {
        if (predicate == null) {
            return;
        }
        if (predicate.isLeaf()) {
            if (predicate.field() == null) {
                return;
            }
            fieldNames.add(predicate.field().fieldName());
            return;
        }
        for (TypedPredicate<?> child : predicate.children()) {
            collectReferencedFields(child, fieldNames);
        }
    }

    private static void collectReferencedFields(TypedPredicate<?> predicate, LinkedHashSet<String> fieldNames) {
        if (predicate == null) {
            return;
        }
        if (predicate.isLeaf()) {
            if (predicate.field() != null) {
                fieldNames.add(predicate.field().fieldName());
            }
            if (predicate.hasSubqueryDescriptor()) {
                fieldNames.addAll(predicate.subqueryDescriptor().subquery().previewReferencedFields());
            }
            return;
        }
        for (TypedPredicate<?> child : predicate.children()) {
            collectReferencedFields(child, fieldNames);
        }
    }

    private static boolean containsSubqueryPredicate(TypedPredicate<?> predicate) {
        if (predicate == null) {
            return false;
        }
        if (predicate.hasSubqueryDescriptor()) {
            return true;
        }
        for (TypedPredicate<?> child : predicate.children()) {
            if (containsSubqueryPredicate(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param sourceClass the declared joined row class, or {@code null}
     */
    private record TypedJoin(String sourceName,
                             String parentField,
                             String childField,
                             Join joinType,
                             Class<?> sourceClass) {
    }

    private record TypedMetric(String fieldName,
                               Metric metric,
                               String alias,
                               boolean count,
                               Double argument) {

        private static TypedMetric of(String fieldName, Metric metric, String alias) {
            return new TypedMetric(fieldName, metric, alias, false, null);
        }

        private static TypedMetric count(String alias) {
            return new TypedMetric(null, Metric.COUNT, alias, true, null);
        }
    }

    private record TypedWindow(WindowFunction function,
                               String valueField,
                               boolean countAll,
                               String alias,
                               List<String> partitionFields,
                               List<TypedWindowOrder> orderFields,
                               QueryWindowFrame frame,
                               int offset,
                               Object defaultValue) {

        private static TypedWindow rank(WindowFunction function,
                                        String alias,
                                        List<String> partitionFields,
                                        List<TypedWindowOrder> orderFields) {
            return new TypedWindow(function, null, false, alias, partitionFields, orderFields,
                    QueryWindowFrame.running(), DEFAULT_WINDOW_OFFSET, null);
        }

        private static TypedWindow value(WindowFunction function,
                                         String valueField,
                                         String alias,
                                         List<String> partitionFields,
                                         List<TypedWindowOrder> orderFields,
                                         QueryWindowFrame frame) {
            return new TypedWindow(function, valueField, false, alias, partitionFields, orderFields, frame,
                    DEFAULT_WINDOW_OFFSET, null);
        }

        private static TypedWindow offset(WindowFunction function,
                                          String valueField,
                                          String alias,
                                          int offset,
                                          Object defaultValue,
                                          List<String> partitionFields,
                                          List<TypedWindowOrder> orderFields) {
            return new TypedWindow(function, valueField, false, alias, partitionFields, orderFields,
                    QueryWindowFrame.running(), offset, defaultValue);
        }

        private static TypedWindow countAll(String alias,
                                            List<String> partitionFields,
                                            List<TypedWindowOrder> orderFields,
                                            QueryWindowFrame frame) {
            return new TypedWindow(WindowFunction.COUNT, null, true, alias, partitionFields, orderFields, frame,
                    DEFAULT_WINDOW_OFFSET, null);
        }
    }
}
