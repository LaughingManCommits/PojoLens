package laughing.man.commits.builder;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.filter.Filter;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.telemetry.QueryTelemetryListener;
import java.util.List;
import java.util.Map;

public interface QueryBuilder {

    /**
     * Thread-safety contract:
     * <p>
     * Query builders are mutable and not safe for concurrent mutation.
     * Configure builders on one thread, then call {@link #initFilter()}.
     * With {@link #copyOnBuild(boolean)} enabled (default), each filter is
     * created from an isolated execution snapshot.
     * <p>
     * Controls whether {@link #initFilter()} captures an immutable execution
     * snapshot of the current builder state.
     * <p>
     * When enabled, builders can be reused safely across threads as long as
     * query mutation methods are not invoked concurrently.
     *
     * @param enabled true to copy on build, false to reuse mutable builder state
     * @return builder
     */
    QueryBuilder copyOnBuild(boolean enabled);

    /**
     * Attaches an optional telemetry listener to this builder.
     *
     * @param listener listener to receive stage events, or null to disable
     * @return builder
     */
    QueryBuilder telemetry(QueryTelemetryListener listener);

    /**
     * Attaches a computed-field registry that materializes reusable derived
     * numeric fields onto the working row schema.
     *
     * @param registry computed-field registry
     * @return builder
     */
    QueryBuilder computedFields(ComputedFieldRegistry registry);

    /**
     * Limits the maximum number of rows returned by {@code filter(...)}.
     *
     * @param maxRows max rows to return; must be >= 0
     * @return builder
     */
    QueryBuilder limit(int maxRows);

    /**
     * Builds an executable filter pipeline from the currently configured query.
     *
     * @return filter executor
     */
    Filter initFilter();

    /**
     * Returns deterministic debug metadata for the current fluent query plan.
     *
     * @return explain payload
     */
    Map<String, Object> explain();

    /**
     * Returns deterministic ordered tabular metadata for the configured output.
     *
     * @param projectionClass projection/output class
     * @return tabular schema metadata
     */
    TabularSchema schema(Class<?> projectionClass);

    /**
     * Adds join rules using plain child beans/POJOs.
     *
     * @param parentField parent-side field name
     * @param children child beans to join against
     * @param childField child-side field name
     * @param joinMethod INNER/LEFT/RIGHT join behavior
     * @return builder
     */
    QueryBuilder addJoinBeans(String parentField, List<?> children, String childField, Join joinMethod);

    <P, PR, C, CR> QueryBuilder addJoinBeans(FieldSelector<P, PR> parentField,
                                             List<C> children,
                                             FieldSelector<C, CR> childField,
                                             Join joinMethod);

    /**
     * Adds an ORDER BY field.
     *
     * @param column field name
     * @return builder
     */
    QueryBuilder addOrder(String column);

    <T, R> QueryBuilder addOrder(FieldSelector<T, R> selector);

    QueryBuilder addOrder(String column, int index);

    <T, R> QueryBuilder addOrder(FieldSelector<T, R> selector, int index);

    /**
     * Adds an ORDER BY field with an explicit date format.
     */
    QueryBuilder addOrder(String column, String dateFormat);

    QueryBuilder addOrder(String column, String dateFormat, int index);

    /**
     * Adds a GROUP BY field.
     */
    QueryBuilder addGroup(String column);

    <T, R> QueryBuilder addGroup(FieldSelector<T, R> selector);

    QueryBuilder addGroup(String column, int index);

    <T, R> QueryBuilder addGroup(FieldSelector<T, R> selector, int index);

    /**
     * Adds a GROUP BY field with an explicit date format.
     */
    QueryBuilder addGroup(String column, String dateFormat);

    QueryBuilder addGroup(String column, String dateFormat, int index);

    /**
     * Adds a projected field to the SELECT list.
     */
    QueryBuilder addField(String column);

    <T, R> QueryBuilder addField(FieldSelector<T, R> selector);

    /**
     * Adds an aggregate metric projected under the provided alias.
     */
    QueryBuilder addMetric(String field, Metric metric, String alias);

    <T, R> QueryBuilder addMetric(FieldSelector<T, R> selector, Metric metric, String alias);

    /**
     * Adds row-count metric projected under the provided alias.
     */
    QueryBuilder addCount(String alias);

    /**
     * Adds a grouped time bucket projection for the given date field.
     */
    QueryBuilder addTimeBucket(String dateField, TimeBucket bucket, String alias);

    <T, R> QueryBuilder addTimeBucket(FieldSelector<T, R> selector, TimeBucket bucket, String alias);

    QueryBuilder addTimeBucket(String dateField, TimeBucketPreset preset, String alias);

    <T, R> QueryBuilder addTimeBucket(FieldSelector<T, R> selector, TimeBucketPreset preset, String alias);

    /**
     * Adds a DISTINCT field using the provided priority index.
     */
    QueryBuilder addDistinct(String column);

    <T, R> QueryBuilder addDistinct(FieldSelector<T, R> selector);

    QueryBuilder addDistinct(String column, int index);

    <T, R> QueryBuilder addDistinct(FieldSelector<T, R> selector, int index);

    /**
     * Adds a single rule with explicit separator (AND/OR).
     */
    QueryBuilder addRule(String column, Object value,
            Clauses clause, Separator separator);

    <T, R> QueryBuilder addRule(FieldSelector<T, R> selector, Object value,
                                Clauses clause, Separator separator);

    /**
     * Adds a single rule with explicit separator and date format.
     */
    QueryBuilder addRule(String column, Object value,
            Clauses clause, Separator separator,
            String commonDateFormat);

    <T, R> QueryBuilder addRule(FieldSelector<T, R> selector, Object value,
                                Clauses clause, Separator separator,
                                String commonDateFormat);

    /**
     * Adds a single rule with default {@link Separator#AND}.
     */
    QueryBuilder addRule(String column, Object value,
            Clauses clause);

    <T, R> QueryBuilder addRule(FieldSelector<T, R> selector, Object value,
                                Clauses clause);

    /**
     * Adds a HAVING rule with explicit separator (applied after aggregation).
     */
    QueryBuilder addHaving(String column, Object value,
                           Clauses clause, Separator separator);

    <T, R> QueryBuilder addHaving(FieldSelector<T, R> selector, Object value,
                                  Clauses clause, Separator separator);

    QueryBuilder addHaving(String column, Object value,
                           Clauses clause, Separator separator,
                           String commonDateFormat);

    <T, R> QueryBuilder addHaving(FieldSelector<T, R> selector, Object value,
                                  Clauses clause, Separator separator,
                                  String commonDateFormat);

    /**
     * Adds a HAVING rule using default {@link Separator#AND}.
     */
    QueryBuilder addHaving(String column, Object value,
                           Clauses clause);

    <T, R> QueryBuilder addHaving(FieldSelector<T, R> selector, Object value,
                                  Clauses clause);

    QueryBuilder allOf(QueryRule... rules);

    QueryBuilder anyOf(QueryRule... rules);

    QueryBuilder addHavingAllOf(QueryRule... rules);

    QueryBuilder addHavingAnyOf(QueryRule... rules);
}

