package laughing.man.commits.builder;

import laughing.man.commits.computed.ComputedFieldDefinition;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.computed.internal.ComputedFieldSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.StringUtil;
import static laughing.man.commits.util.ObjectUtil.castToString;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.filter.FilterExecutionPlanCache;
import laughing.man.commits.filter.FilterExecutionPlanCacheStore;
import laughing.man.commits.filter.Filter;
import laughing.man.commits.filter.FilterImpl;
import laughing.man.commits.telemetry.QueryTelemetryListener;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.table.internal.TabularSchemaSupport;

/**
 * Mutable query builder.
 * <p>
 * This type is not safe for concurrent mutation. Use request-scoped builder
 * instances, or configure once and rely on {@code copyOnBuild(true)} to create
 * isolated execution snapshots per {@code initFilter()} call.
 */
public class FilterQueryBuilder implements QueryBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(FilterQueryBuilder.class);

    private final QuerySpec spec;
    private final FilterExecutionPlanCacheStore executionPlanCache;
    private boolean copyOnBuild = true;
    private QueryTelemetryListener telemetryListener;
    private String telemetryQueryType = "fluent";
    private String telemetrySource = "fluent";
    private ComputedFieldRegistry computedFieldRegistry = ComputedFieldRegistry.empty();
    private boolean runtimeSchemaValidated;
    private List<?> sourceBeans = List.of();
    private Map<Integer, List<?>> joinSourceBeans = new HashMap<>();
    private boolean fullyMaterializedSourceRows;
    private Set<String> materializedSourceFields = Set.of();
    private volatile long executionPlanShapeVersion;

    public FilterQueryBuilder(List<?> pojos) {
        this(pojos, FilterExecutionPlanCache.defaultStore());
    }

    public FilterQueryBuilder(List<?> pojos, FilterExecutionPlanCacheStore executionPlanCache) {
        this.executionPlanCache = requireCacheStore(executionPlanCache);
        this.spec = new QuerySpec();
        spec.setSourceFieldTypes(inferSourceFieldTypes(pojos));
        refreshFieldTypes();
        initializeSourceRows(pojos);
    }

    private FilterQueryBuilder(QuerySpec snapshot, FilterExecutionPlanCacheStore executionPlanCache) {
        this.executionPlanCache = requireCacheStore(executionPlanCache);
        this.spec = snapshot == null ? new QuerySpec() : snapshot;
    }

    @Override
    public FilterQueryBuilder copyOnBuild(boolean enabled) {
        this.copyOnBuild = enabled;
        return this;
    }

    @Override
    public FilterQueryBuilder telemetry(QueryTelemetryListener listener) {
        this.telemetryListener = listener;
        return this;
    }

    @Override
    public FilterQueryBuilder computedFields(ComputedFieldRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        markExecutionPlanShapeChanged();
        this.computedFieldRegistry = registry;
        refreshFieldTypes();
        if (hasSourceBeans()) {
            clearMaterializedSourceRows();
        } else {
            spec.setRows(materializedRows(spec.getRows()));
        }
        materializeJoinRows();
        return this;
    }

    @Override
    public FilterQueryBuilder limit(int maxRows) {
        if (maxRows < 0) {
            throw new IllegalArgumentException("maxRows must be >= 0");
        }
        spec.setLimit(maxRows);
        return this;
    }

    @Override
    public FilterQueryBuilder offset(int rowOffset) {
        if (rowOffset < 0) {
            throw new IllegalArgumentException("rowOffset must be >= 0");
        }
        spec.setOffset(rowOffset);
        return this;
    }

    @Override
    public Filter initFilter() {
        return new FilterImpl(copyOnBuild ? snapshotForExecution() : this);
    }

    @Override
    public Map<String, Object> explain() {
        Map<String, Object> explain = new LinkedHashMap<>();
        explain.put("type", "fluent");
        explain.put("copyOnBuild", copyOnBuild);
        explain.put("offset", spec.getOffset());
        explain.put("limit", spec.getLimit());
        explain.put("selectedFields", new ArrayList<>(spec.getReturnFields()));
        explain.put("groupBy", new TreeMap<>(spec.getGroupFields()));
        explain.put("orderBy", new TreeMap<>(spec.getOrderFields()));
        explain.put("distinct", new TreeMap<>(spec.getDistinctFields()));
        explain.put("indexes", new ArrayList<>(spec.getIndexedFields()));
        explain.put("whereRuleCount", spec.getFilterValues().size());
        explain.put("havingRuleCount", spec.getHavingValues().size());
        explain.put("qualifyRuleCount", spec.getQualifyValues().size());
        explain.put("joinCount", spec.getJoinClasses().size());
        explain.put("metrics", metricEntries(spec.getMetrics()));
        explain.put("timeBuckets", timeBucketEntries(spec.getTimeBuckets()));
        explain.put("windows", windowEntries(spec.getWindows()));
        explain.put("computedFields", ComputedFieldSupport.explainEntries(computedFieldRegistry, schemaFields()));
        explain.put("statsPlanCache", executionPlanCache.snapshot());
        return Collections.unmodifiableMap(explain);
    }

    @Override
    public TabularSchema schema(Class<?> projectionClass) {
        if (projectionClass == null) {
            throw new IllegalArgumentException("projectionClass must not be null");
        }
        return TabularSchemaSupport.fromFluentBuilder(this, projectionClass);
    }

    public List<QueryRow> getRows() {
        ensureRowsMaterialized();
        return spec.getRows();
    }

    public Map<String, Class<?>> getFieldTypes() {
        return spec.getFieldTypes();
    }

    public Map<String, Class<?>> getSourceFieldTypesForExecution() {
        return spec.getSourceFieldTypes();
    }

    public Map<Integer, Map<String, Class<?>>> getJoinSourceFieldTypesForExecution() {
        return spec.getJoinSourceFieldTypes();
    }

    public List<?> getSourceBeansForExecution() {
        return sourceBeans;
    }

    public Map<Integer, List<?>> getJoinSourceBeansForExecution() {
        return joinSourceBeans;
    }

    public void setRows(List<QueryRow> rows) {
        setRows(rows, ReflectionUtil.collectQueryRowFieldTypes(rows));
    }

    public void setRows(List<QueryRow> rows, Map<String, Class<?>> sourceFieldTypes) {
        markExecutionPlanShapeChanged();
        sourceBeans = List.of();
        clearMaterializedSourceRows();
        spec.setSourceFieldTypes(sourceFieldTypes);
        refreshFieldTypes();
        spec.setRows(materializedRows(rows));
    }

    public void setMaterializedRows(List<QueryRow> rows, Map<String, Class<?>> sourceFieldTypes) {
        markExecutionPlanShapeChanged();
        sourceBeans = List.of();
        clearMaterializedSourceRows();
        spec.setSourceFieldTypes(sourceFieldTypes);
        refreshFieldTypes();
        spec.setRows(rows == null ? new ArrayList<>() : new ArrayList<>(rows));
    }

    public void setExecutionSchema(Map<String, Class<?>> sourceFieldTypes) {
        markExecutionPlanShapeChanged();
        spec.setSourceFieldTypes(new LinkedHashMap<>(sourceFieldTypes));
        refreshFieldTypes();
    }

    @Override
    public FilterQueryBuilder addJoinBeans(String parentField, List<?> children, String childField, Join joinMethod) {
        if (children == null || children.isEmpty()) {
            addJoinRows(parentField, new ArrayList<>(), childField, joinMethod);
            return this;
        }
        Object first = CollectionUtil.firstNonNull(children);
        if (first instanceof QueryRow) {
            List<QueryRow> childRows = toSourceRows(children);
            addJoinRows(parentField, childRows, childField, joinMethod);
            return this;
        }
        addLazyJoinSource(parentField, children, childField, joinMethod);
        return this;
    }

    @Override
    public <P, PR, C, CR> FilterQueryBuilder addJoinBeans(FieldSelector<P, PR> parentField,
                                                          List<C> children,
                                                          FieldSelector<C, CR> childField,
                                                          Join joinMethod) {
        return addJoinBeans(
                FieldSelectors.resolve(parentField),
                children,
                FieldSelectors.resolve(childField),
                joinMethod
        );
    }

    /**
     * Adds an ORDER BY field using the next available priority index.
     */
    @Override
    public FilterQueryBuilder addOrder(String column) {
        return addOrder(column, nextPriorityIndex(spec.getOrderFields()));
    }

    @Override
    public <T, R> FilterQueryBuilder addOrder(FieldSelector<T, R> selector) {
        return addOrder(FieldSelectors.resolve(selector));
    }

    @Override
    public FilterQueryBuilder addOrder(String column, int index) {
        Map<Integer, String> orderFields = spec.getOrderFields();
        if (!orderFields.containsValue(column) || !orderFields.containsKey(index)) {
            orderFields.put(index, column);
            markExecutionPlanShapeChanged();
        } else {
            if (orderFields.containsKey(index)) {
                LOG.info("Index [" + index + "] found "
                        + "value [" + orderFields.get(index) + "] assigned in map, "
                        + "supply new index value!");
            }

            if (orderFields.containsValue(column)) {
                LOG.info("Column[" + column + "] found in map, supply new column value!");
            }
        }

        return this;
    }

    /**
     * Adds an ORDER BY field with an explicit date format.
     */
    @Override
    public FilterQueryBuilder addOrder(String column, String dateFormat) {
        return addOrder(column, dateFormat, nextPriorityIndex(spec.getOrderFields()));
    }

    @Override
    public FilterQueryBuilder addOrder(String column, String dateFormat, int index) {
        try {
            String id = castToString(index);
            if (!spec.getFilterDateFormats().containsKey(id) && dateFormat != null) {
                spec.getFilterDateFormats().put(id, dateFormat);
                markExecutionPlanShapeChanged();
                addOrder(column, index);
            }

        } catch (Exception e) {
            LOG.error("Could not addOrder by fields "
                    + "column[" + column + "] "
                    + "dateFormat[" + dateFormat + "] "
                    + "index[" + index + "]", e);
        }
        return this;
    }

    /**
     * Adds a GROUP BY field using the next available priority index.
     */
    @Override
    public FilterQueryBuilder addGroup(String column) {
        return addGroup(column, nextPriorityIndex(spec.getGroupFields()));
    }

    @Override
    public <T, R> FilterQueryBuilder addGroup(FieldSelector<T, R> selector) {
        return addGroup(FieldSelectors.resolve(selector));
    }

    @Override
    public FilterQueryBuilder addGroup(String column, int index) {
        if (spec.getGroupFields().containsValue(column)) {
            return this;
        }
        addGroup(column, null, index);
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addGroup(FieldSelector<T, R> selector, int index) {
        return addGroup(FieldSelectors.resolve(selector), index);
    }

    /**
     * Adds a GROUP BY field with an explicit date format.
     */
    @Override
    public FilterQueryBuilder addGroup(String column, String dateFormat) {
        return addGroup(column, dateFormat, nextPriorityIndex(spec.getGroupFields()));
    }

    @Override
    public FilterQueryBuilder addGroup(String column, String dateFormat, int index) {
        try {
            String id = castToString(index);
            saveDateFormatIfAbsent(spec.getFilterDateFormats(), id, dateFormat);
            spec.getGroupFields().put(index, column);
            markExecutionPlanShapeChanged();
        } catch (Exception e) {
            LOG.error("Could not add Group by fields "
                    + "column[" + column + "] "
                    + "dateFormat[" + dateFormat + "] "
                    + "index[" + index + "]", e);
        }
        return this;
    }

    /**
     * Adds a projected field.
     */
    @Override
    public FilterQueryBuilder addField(String column) {
        List<String> returnFields = spec.getReturnFields();
        if (!returnFields.contains(column)) {
            returnFields.add(column);
            markExecutionPlanShapeChanged();
        }
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addField(FieldSelector<T, R> selector) {
        return addField(FieldSelectors.resolve(selector));
    }

    @Override
    public FilterQueryBuilder addIndex(String column) {
        String normalizedColumn = requireIdentifier(column, "column");
        List<String> indexes = spec.getIndexedFields();
        if (!indexes.contains(normalizedColumn)) {
            indexes.add(normalizedColumn);
            markExecutionPlanShapeChanged();
        }
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addIndex(FieldSelector<T, R> selector) {
        return addIndex(FieldSelectors.resolve(selector));
    }

    @Override
    public FilterQueryBuilder addMetric(String field, Metric metric, String alias) {
        Metric normalizedMetric = requireMetric(metric);
        String normalizedAlias = requireIdentifier(alias, "alias");
        ensureOutputAliasAvailable(normalizedAlias);
        String normalizedField = requireIdentifier(field, "field");
        ensureFieldExists(normalizedField);
        if (normalizedMetric.requiresNumericField()) {
            ensureNumericField(normalizedField, normalizedMetric);
        }
        spec.getMetrics().add(QueryMetric.of(normalizedField, normalizedMetric, normalizedAlias));
        markExecutionPlanShapeChanged();
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addMetric(FieldSelector<T, R> selector, Metric metric, String alias) {
        return addMetric(FieldSelectors.resolve(selector), metric, alias);
    }

    @Override
    public FilterQueryBuilder addCount(String alias) {
        String normalizedAlias = requireIdentifier(alias, "alias");
        ensureOutputAliasAvailable(normalizedAlias);
        spec.getMetrics().add(QueryMetric.count(normalizedAlias));
        markExecutionPlanShapeChanged();
        return this;
    }

    @Override
    public FilterQueryBuilder addWindow(String alias,
                                        WindowFunction function,
                                        List<String> partitionFields,
                                        List<QueryWindowOrder> orderFields) {
        return addWindow(alias, function, null, false, partitionFields, orderFields);
    }

    @Override
    public FilterQueryBuilder addWindow(String alias,
                                        WindowFunction function,
                                        String valueField,
                                        boolean countAll,
                                        List<String> partitionFields,
                                        List<QueryWindowOrder> orderFields) {
        WindowFunction normalizedFunction = requireWindowFunction(function);
        String normalizedAlias = requireIdentifier(alias, "alias");
        ensureOutputAliasAvailable(normalizedAlias);
        String normalizedValueField = valueField;
        if (normalizedFunction.isAggregateFunction() && !countAll) {
            normalizedValueField = requireIdentifier(valueField, "valueField");
            ensureFieldExists(normalizedValueField);
            if (normalizedFunction.requiresNumericField()) {
                ensureNumericWindowField(normalizedValueField, normalizedFunction);
            }
        }
        QueryWindow window = QueryWindow.of(
                normalizedAlias,
                normalizedFunction,
                normalizedValueField,
                countAll,
                partitionFields,
                orderFields
        );
        spec.getWindows().add(window);
        markExecutionPlanShapeChanged();
        return this;
    }

    @Override
    public FilterQueryBuilder addTimeBucket(String dateField, TimeBucket bucket, String alias) {
        return addTimeBucket(dateField, TimeBucketPreset.of(bucket), alias);
    }

    @Override
    public FilterQueryBuilder addTimeBucket(String dateField, TimeBucketPreset preset, String alias) {
        String normalizedDateField = requireIdentifier(dateField, "dateField");
        String normalizedAlias = requireIdentifier(alias, "alias");
        TimeBucketPreset normalizedPreset = requireTimeBucketPreset(preset);
        ensureOutputAliasAvailable(normalizedAlias);
        ensureFieldExists(normalizedDateField);
        ensureDateField(normalizedDateField);
        spec.getTimeBuckets().put(normalizedAlias, QueryTimeBucket.of(normalizedDateField, normalizedPreset, normalizedAlias));
        markExecutionPlanShapeChanged();
        addGroup(normalizedAlias);
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addTimeBucket(FieldSelector<T, R> selector, TimeBucket bucket, String alias) {
        return addTimeBucket(FieldSelectors.resolve(selector), TimeBucketPreset.of(bucket), alias);
    }

    @Override
    public <T, R> FilterQueryBuilder addTimeBucket(FieldSelector<T, R> selector, TimeBucketPreset preset, String alias) {
        return addTimeBucket(FieldSelectors.resolve(selector), preset, alias);
    }

    @Override
    public <T, R> FilterQueryBuilder addOrder(FieldSelector<T, R> selector, int index) {
        return addOrder(FieldSelectors.resolve(selector), index);
    }

    /**
     * Adds a DISTINCT field with explicit priority index.
     */
    @Override
    public FilterQueryBuilder addDistinct(String column) {
        return addDistinct(column, nextPriorityIndex(spec.getDistinctFields()));
    }

    @Override
    public <T, R> FilterQueryBuilder addDistinct(FieldSelector<T, R> selector) {
        return addDistinct(FieldSelectors.resolve(selector));
    }

    @Override
    public FilterQueryBuilder addDistinct(String column, int index) {
        Map<Integer, String> distinctFields = spec.getDistinctFields();
        if (!distinctFields.containsValue(column) && !distinctFields.containsKey(index)) {
            distinctFields.put(index, column);
            markExecutionPlanShapeChanged();
        }
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addDistinct(FieldSelector<T, R> selector, int index) {
        return addDistinct(FieldSelectors.resolve(selector), index);
    }

    /**
     * Adds a filter rule with explicit separator.
     */
    @Override
    public FilterQueryBuilder addRule(String column, Object value,
            Clauses clause, Separator separator) {
        addRule(column, value, clause, separator, null);
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addRule(FieldSelector<T, R> selector, Object value,
                                             Clauses clause, Separator separator) {
        return addRule(FieldSelectors.resolve(selector), value, clause, separator);
    }

    /**
     * Adds a filter rule using default {@link Separator#AND}.
     */
    @Override
    public FilterQueryBuilder addRule(String column, Object value,
            Clauses clause) {
        addRule(column, value, clause, Separator.AND, null);
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addRule(FieldSelector<T, R> selector, Object value,
                                             Clauses clause) {
        return addRule(FieldSelectors.resolve(selector), value, clause);
    }

    @Override
    public FilterQueryBuilder addHaving(String column, Object value,
                                        Clauses clause, Separator separator) {
        addHaving(column, value, clause, separator, null);
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addHaving(FieldSelector<T, R> selector, Object value,
                                               Clauses clause, Separator separator) {
        return addHaving(FieldSelectors.resolve(selector), value, clause, separator);
    }

    @Override
    public FilterQueryBuilder addHaving(String column, Object value,
                                        Clauses clause) {
        addHaving(column, value, clause, Separator.AND, null);
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addHaving(FieldSelector<T, R> selector, Object value,
                                               Clauses clause) {
        return addHaving(FieldSelectors.resolve(selector), value, clause);
    }

    @Override
    public FilterQueryBuilder addQualify(String column, Object value,
                                         Clauses clause, Separator separator) {
        addQualify(column, value, clause, separator, null);
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addQualify(FieldSelector<T, R> selector, Object value,
                                                Clauses clause, Separator separator) {
        return addQualify(FieldSelectors.resolve(selector), value, clause, separator);
    }

    @Override
    public FilterQueryBuilder addQualify(String column, Object value,
                                         Clauses clause) {
        addQualify(column, value, clause, Separator.AND, null);
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addQualify(FieldSelector<T, R> selector, Object value,
                                                Clauses clause) {
        return addQualify(FieldSelectors.resolve(selector), value, clause);
    }

    @Override
    public FilterQueryBuilder allOf(QueryRule... rules) {
        addRuleGroup(spec.getAllOfGroups(), rules);
        return this;
    }

    @Override
    public FilterQueryBuilder anyOf(QueryRule... rules) {
        addRuleGroup(spec.getAnyOfGroups(), rules);
        return this;
    }

    @Override
    public FilterQueryBuilder addHavingAllOf(QueryRule... rules) {
        addRuleGroup(spec.getHavingAllOfGroups(), rules);
        return this;
    }

    @Override
    public FilterQueryBuilder addHavingAnyOf(QueryRule... rules) {
        addRuleGroup(spec.getHavingAnyOfGroups(), rules);
        return this;
    }

    @Override
    public FilterQueryBuilder addQualifyAllOf(QueryRule... rules) {
        addRuleGroup(spec.getQualifyAllOfGroups(), rules);
        markExecutionPlanShapeChanged();
        return this;
    }

    @Override
    public FilterQueryBuilder addQualifyAnyOf(QueryRule... rules) {
        addRuleGroup(spec.getQualifyAnyOfGroups(), rules);
        markExecutionPlanShapeChanged();
        return this;
    }

    /**
     * Adds a filter rule with separator and optional date format.
     */
    @Override
    public FilterQueryBuilder addRule(String column, Object value,
            Clauses clause, Separator separator,
            String commonDateFormat) {
        addCriteriaRule(column, value, clause, separator, commonDateFormat, false);
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addRule(FieldSelector<T, R> selector, Object value,
                                             Clauses clause, Separator separator,
                                             String commonDateFormat) {
        return addRule(FieldSelectors.resolve(selector), value, clause, separator, commonDateFormat);
    }

    @Override
    public FilterQueryBuilder addHaving(String column, Object value,
                                        Clauses clause, Separator separator,
                                        String commonDateFormat) {
        addCriteriaRule(column, value, clause, separator, commonDateFormat, true);
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addHaving(FieldSelector<T, R> selector, Object value,
                                               Clauses clause, Separator separator,
                                               String commonDateFormat) {
        return addHaving(FieldSelectors.resolve(selector), value, clause, separator, commonDateFormat);
    }

    @Override
    public FilterQueryBuilder addQualify(String column, Object value,
                                         Clauses clause, Separator separator,
                                         String commonDateFormat) {
        addQualifyCriteriaRule(column, value, clause, separator, commonDateFormat);
        return this;
    }

    @Override
    public <T, R> FilterQueryBuilder addQualify(FieldSelector<T, R> selector, Object value,
                                                Clauses clause, Separator separator,
                                                String commonDateFormat) {
        return addQualify(FieldSelectors.resolve(selector), value, clause, separator, commonDateFormat);
    }

    public Map<String, Object> getFilterValues() {
        return spec.getFilterValues();
    }

    public Map<String, String> getFilterFields() {
        return spec.getFilterFields();
    }

    public Map<String, Clauses> getFilterClause() {
        return spec.getFilterClause();
    }

    public Map<String, Separator> getFilterSeparator() {
        return spec.getFilterSeparator();
    }

    public Map<String, String> getFilterDateFormats() {
        return spec.getFilterDateFormats();
    }

    public Map<String, Object> getHavingValues() {
        return spec.getHavingValues();
    }

    public Map<String, String> getHavingFields() {
        return spec.getHavingFields();
    }

    public Map<String, Clauses> getHavingClause() {
        return spec.getHavingClause();
    }

    public Map<String, Separator> getHavingSeparator() {
        return spec.getHavingSeparator();
    }

    public Map<String, String> getHavingDateFormats() {
        return spec.getHavingDateFormats();
    }

    public Map<String, List<String>> getHavingIDs() {
        return spec.getHavingIDs();
    }

    public Map<String, Object> getQualifyValues() {
        return spec.getQualifyValues();
    }

    public Map<String, String> getQualifyFields() {
        return spec.getQualifyFields();
    }

    public Map<String, Clauses> getQualifyClause() {
        return spec.getQualifyClause();
    }

    public Map<String, Separator> getQualifySeparator() {
        return spec.getQualifySeparator();
    }

    public Map<String, String> getQualifyDateFormats() {
        return spec.getQualifyDateFormats();
    }

    public Map<String, List<String>> getQualifyIDs() {
        return spec.getQualifyIDs();
    }

    public Map<Integer, String> getGroupFields() {
        return spec.getGroupFields();
    }

    public Map<Integer, String> getOrderFields() {
        return spec.getOrderFields();
    }

    public Map<Integer, String> getDistinctFields() {
        return spec.getDistinctFields();
    }

    public Map<String, List<String>> getFilterIDs() {
        return spec.getFilterIDs();
    }

    /**
     * Removes a WHERE rule by rule id.
     * Intended for internal cleaner/normalization flows.
     */
    public FilterQueryBuilder removeFilterRule(String ruleId) {
        spec.removeFilterRule(ruleId);
        markExecutionPlanShapeChanged();
        return this;
    }

    /**
     * Removes a HAVING rule by rule id.
     * Intended for internal cleaner/normalization flows.
     */
    public FilterQueryBuilder removeHavingRule(String ruleId) {
        spec.removeHavingRule(ruleId);
        markExecutionPlanShapeChanged();
        return this;
    }

    /**
     * Removes a QUALIFY rule by rule id.
     * Intended for internal cleaner/normalization flows.
     */
    public FilterQueryBuilder removeQualifyRule(String ruleId) {
        spec.removeQualifyRule(ruleId);
        markExecutionPlanShapeChanged();
        return this;
    }

    public Map<Integer, List<QueryRow>> getJoinClasses() {
        return spec.getJoinClasses();
    }

    public Map<Integer, List<QueryRow>> getJoinClassesForExecution() {
        materializePendingJoinRows();
        return spec.getJoinClasses();
    }

    public Map<Integer, Join> getJoinMethods() {
        return spec.getJoinMethods();
    }

    public Map<Integer, String> getJoinParentFields() {
        return spec.getJoinParentFields();
    }

    public Map<Integer, String> getJoinChildFields() {
        return spec.getJoinChildFields();
    }

    public List<String> getReturnFields() {
        return spec.getReturnFields();
    }

    public List<String> getIndexedFields() {
        return spec.getIndexedFields();
    }

    public List<QueryMetric> getMetrics() {
        return spec.getMetrics();
    }

    public Map<String, QueryTimeBucket> getTimeBuckets() {
        return spec.getTimeBuckets();
    }

    public List<List<QueryRule>> getAllOfGroups() {
        return spec.getAllOfGroups();
    }

    public List<List<QueryRule>> getAnyOfGroups() {
        return spec.getAnyOfGroups();
    }

    public List<List<QueryRule>> getHavingAllOfGroups() {
        return spec.getHavingAllOfGroups();
    }

    public List<List<QueryRule>> getHavingAnyOfGroups() {
        return spec.getHavingAnyOfGroups();
    }

    public List<List<QueryRule>> getQualifyAllOfGroups() {
        return spec.getQualifyAllOfGroups();
    }

    public List<List<QueryRule>> getQualifyAnyOfGroups() {
        return spec.getQualifyAnyOfGroups();
    }

    public Integer getLimit() {
        return spec.getLimit();
    }

    public Integer getOffset() {
        return spec.getOffset();
    }

    public List<QueryWindow> getWindows() {
        return spec.getWindows();
    }

    private int nextJoinIndex() {
        return spec.getJoinClasses().size() + 1;
    }

    private void addJoinRows(String parentField, List<QueryRow> children, String childField, Join joinMethod) {
        int index = nextJoinIndex();
        storeJoinDefinition(index,
                ComputedFieldSupport.materializeRows(children, computedFieldRegistry),
                inferSourceFieldTypes(children),
                parentField,
                childField,
                joinMethod);
    }

    private void addLazyJoinSource(String parentField, List<?> children, String childField, Join joinMethod) {
        int index = nextJoinIndex();
        joinSourceBeans.put(index, copySourceBeans(children));
        storeJoinDefinition(index, new ArrayList<>(), inferSourceFieldTypes(children), parentField, childField, joinMethod);
    }

    private void storeJoinDefinition(int index,
                                     List<QueryRow> children,
                                     Map<String, Class<?>> childFieldTypes,
                                     String parentField,
                                     String childField,
                                     Join joinMethod) {
        spec.getJoinClasses().put(index, children);
        spec.getJoinSourceFieldTypes().put(index, new LinkedHashMap<>(childFieldTypes));
        spec.getJoinMethods().put(index, joinMethod);
        spec.getJoinParentFields().put(index, parentField);
        spec.getJoinChildFields().put(index, childField);
    }

    private void addCriteriaRule(String column,
                                 Object value,
                                 Clauses clause,
                                 Separator separator,
                                 String commonDateFormat,
                                 boolean havingRule) {
        String ruleId = ReflectionUtil.newUUID();
        QuerySpec.CriteriaRule rule = new QuerySpec.CriteriaRule(
                ruleId,
                column,
                value,
                clause,
                separator,
                commonDateFormat
        );
        if (havingRule) {
            spec.addHavingRule(rule);
        } else {
            spec.addFilterRule(rule);
        }
        markExecutionPlanShapeChanged();
    }

    private void addQualifyCriteriaRule(String column,
                                        Object value,
                                        Clauses clause,
                                        Separator separator,
                                        String commonDateFormat) {
        String ruleId = ReflectionUtil.newUUID();
        QuerySpec.CriteriaRule rule = new QuerySpec.CriteriaRule(
                ruleId,
                column,
                value,
                clause,
                separator == null ? Separator.AND : separator,
                commonDateFormat
        );
        spec.addQualifyRule(rule);
        markExecutionPlanShapeChanged();
    }

    private void saveDateFormatIfAbsent(Map<String, String> dateFormatMap, String id, String dateFormat) {
        if (!StringUtil.isNull(id) && !StringUtil.isNull(dateFormat) && !dateFormatMap.containsKey(id)) {
            dateFormatMap.put(id, dateFormat);
        }
    }

    private void addRuleGroup(List<List<QueryRule>> groups, QueryRule... rules) {
        if (rules == null || rules.length == 0) {
            return;
        }
        List<QueryRule> group = new ArrayList<>();
        for (QueryRule rule : rules) {
            if (rule != null) {
                group.add(rule);
            }
        }
        if (!group.isEmpty()) {
            groups.add(group);
        }
    }

    private int nextPriorityIndex(Map<Integer, String> configuredFields) {
        int max = 0;
        for (Integer key : configuredFields.keySet()) {
            if (key != null && key > max) {
                max = key;
            }
        }
        return max + 1;
    }

    public FilterQueryBuilder snapshotForExecution() {
        FilterQueryBuilder snapshot = copyBuilderState(spec.executionCopy(), copyOnBuild);
        snapshot.sourceBeans = copySourceBeans(sourceBeans);
        snapshot.joinSourceBeans = copyJoinSourceBeans();
        snapshot.fullyMaterializedSourceRows = fullyMaterializedSourceRows;
        snapshot.materializedSourceFields = materializedSourceFields;
        return snapshot;
    }

    public FilterQueryBuilder snapshotForPreparedExecution() {
        FilterQueryBuilder snapshot = copyBuilderState(spec.executionCopy(), true);
        snapshot.sourceBeans = List.of();
        snapshot.joinSourceBeans = new HashMap<>();
        snapshot.fullyMaterializedSourceRows = false;
        snapshot.materializedSourceFields = Set.of();
        snapshot.spec.setRows(new ArrayList<>());
        for (Integer joinIndex : new ArrayList<>(snapshot.spec.getJoinClasses().keySet())) {
            snapshot.spec.getJoinClasses().put(joinIndex, new ArrayList<>());
        }
        return snapshot;
    }

    public FilterQueryBuilder preparedExecutionCopy(List<?> pojos, Map<Integer, List<?>> joinSourcesByIndex) {
        FilterQueryBuilder snapshot = copyBuilderState(spec.executionCopy(), false);
        snapshot.bindPreparedExecutionSources(pojos, joinSourcesByIndex == null ? Map.of() : joinSourcesByIndex);
        return snapshot;
    }

    public FilterQueryBuilder preparedExecutionView(List<?> pojos, Map<Integer, List<?>> joinSourcesByIndex) {
        Map<Integer, List<?>> joinSources = joinSourcesByIndex == null ? Map.of() : joinSourcesByIndex;
        if (!supportsPreparedExecutionView(pojos, joinSources)) {
            return preparedExecutionCopy(pojos, joinSources);
        }
        FilterQueryBuilder snapshot = copyBuilderState(spec.preparedExecutionViewCopy(), false);
        snapshot.bindPreparedExecutionSources(pojos, joinSources);
        snapshot.runtimeSchemaValidated = true;
        return snapshot;
    }

    public FilterQueryBuilder snapshotForRows(List<QueryRow> rows) {
        FilterQueryBuilder snapshot = snapshotForExecution();
        snapshot.setRows(rows);
        return snapshot;
    }

    public Map<String, Class<?>> deriveJoinedSourceFieldTypes() {
        Map<String, Class<?>> current = new LinkedHashMap<>(spec.getSourceFieldTypes());
        if (current.isEmpty() || !hasJoinDefinitions()) {
            return current;
        }
        for (Integer joinIndex : new java.util.TreeSet<>(joinDefinitionIndexes())) {
            current = mergeJoinFieldTypes(current, joinFieldTypes(joinIndex), spec.getJoinMethods().get(joinIndex));
        }
        return current;
    }

    public FilterExecutionPlanCacheStore getExecutionPlanCache() {
        return executionPlanCache;
    }

    public QueryTelemetryListener getTelemetryListener() {
        return telemetryListener;
    }

    public ComputedFieldRegistry getComputedFieldRegistry() {
        return computedFieldRegistry;
    }

    public String getTelemetryQueryType() {
        return telemetryQueryType;
    }

    public String getTelemetrySource() {
        return telemetrySource;
    }

    public long getExecutionPlanShapeVersion() {
        return executionPlanShapeVersion;
    }

    public boolean requiresRuntimeSchemaCleaning() {
        return !runtimeSchemaValidated;
    }

    public FilterQueryBuilder telemetryContext(String queryType,
                                               String source,
                                               QueryTelemetryListener listener) {
        this.telemetryQueryType = requireIdentifier(queryType, "queryType");
        this.telemetrySource = requireIdentifier(source, "source");
        this.telemetryListener = listener;
        return this;
    }

    private FilterQueryBuilder copyBuilderState(QuerySpec snapshotSpec, boolean snapshotCopyOnBuild) {
        FilterQueryBuilder snapshot = new FilterQueryBuilder(snapshotSpec, executionPlanCache);
        snapshot.copyOnBuild = snapshotCopyOnBuild;
        snapshot.telemetryListener = telemetryListener;
        snapshot.telemetryQueryType = telemetryQueryType;
        snapshot.telemetrySource = telemetrySource;
        snapshot.computedFieldRegistry = computedFieldRegistry;
        snapshot.runtimeSchemaValidated = runtimeSchemaValidated;
        snapshot.executionPlanShapeVersion = executionPlanShapeVersion;
        return snapshot;
    }

    private void ensureOutputAliasAvailable(String alias) {
        for (QueryMetric configured : spec.getMetrics()) {
            if (configured.getAlias().equals(alias)) {
                throw new IllegalArgumentException("Metric alias already configured: " + alias);
            }
        }
        if (spec.getTimeBuckets().containsKey(alias)) {
            throw new IllegalArgumentException("Time bucket alias already configured: " + alias);
        }
        for (QueryWindow configured : spec.getWindows()) {
            if (configured.alias().equals(alias)) {
                throw new IllegalArgumentException("Window alias already configured: " + alias);
            }
        }
    }

    private void ensureFieldExists(String fieldName) {
        if (configuredFieldExists(fieldName)) {
            return;
        }
        if (validationSchemaUnknown()) {
            return;
        }
        throw new IllegalArgumentException("Unknown metric field: " + fieldName);
    }

    private void ensureNumericField(String fieldName, Metric metric) {
        Class<?> fieldType = configuredFieldType(fieldName);
        if (fieldType == null) {
            return;
        }
        if (!Number.class.isAssignableFrom(fieldType)) {
            throw new IllegalArgumentException("Metric " + metric + " requires numeric field: " + fieldName);
        }
    }

    private void ensureNumericWindowField(String fieldName, WindowFunction function) {
        Class<?> fieldType = configuredFieldType(fieldName);
        if (fieldType == null) {
            return;
        }
        if (!Number.class.isAssignableFrom(fieldType)) {
            throw new IllegalArgumentException(
                    "Window function " + function + " requires numeric field: " + fieldName);
        }
    }

    private void ensureDateField(String fieldName) {
        Class<?> fieldType = configuredFieldType(fieldName);
        if (fieldType == null) {
            return;
        }
        if (!java.util.Date.class.isAssignableFrom(fieldType)) {
            throw new IllegalArgumentException("Time bucket requires date field: " + fieldName);
        }
    }

    private Metric requireMetric(Metric metric) {
        if (metric == null) {
            throw new IllegalArgumentException("metric is required");
        }
        if (Metric.COUNT.equals(metric)) {
            throw new IllegalArgumentException("Use addCount(alias) for row count");
        }
        return metric;
    }

    private WindowFunction requireWindowFunction(WindowFunction function) {
        if (function == null) {
            throw new IllegalArgumentException("function is required");
        }
        return function;
    }

    private String requireIdentifier(String value, String label) {
        if (value == null || StringUtil.isNull(value.trim())) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private TimeBucket requireTimeBucket(TimeBucket bucket) {
        if (bucket == null) {
            throw new IllegalArgumentException("bucket is required");
        }
        return bucket;
    }

    private TimeBucketPreset requireTimeBucketPreset(TimeBucketPreset preset) {
        if (preset == null) {
            throw new IllegalArgumentException("preset is required");
        }
        return preset;
    }

    private static List<String> metricEntries(List<QueryMetric> metrics) {
        List<String> entries = new ArrayList<>();
        for (QueryMetric metric : metrics) {
            entries.add(metric.getMetric() + ":" + metric.getField() + ":" + metric.getAlias());
        }
        return entries;
    }

    private static List<String> timeBucketEntries(Map<String, QueryTimeBucket> buckets) {
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, QueryTimeBucket> entry : buckets.entrySet()) {
            QueryTimeBucket bucket = entry.getValue();
            entries.add(entry.getKey() + ":" + bucket.getDateField() + ":" + bucket.getPreset().explainToken());
        }
        Collections.sort(entries);
        return entries;
    }

    private static List<String> windowEntries(List<QueryWindow> windows) {
        List<String> entries = new ArrayList<>(windows.size());
        for (QueryWindow window : windows) {
            StringBuilder orderFields = new StringBuilder();
            for (int i = 0; i < window.orderFields().size(); i++) {
                QueryWindowOrder order = window.orderFields().get(i);
                if (i > 0) {
                    orderFields.append(",");
                }
                orderFields.append(order.field()).append(" ").append(order.sort().name());
            }
            entries.add(window.alias()
                    + ":" + window.function().name()
                    + (window.countAll()
                    ? ":value=*"
                    : window.valueField() == null ? "" : ":value=" + window.valueField())
                    + ":partition=" + window.partitionFields()
                    + ":order=[" + orderFields + "]");
        }
        return entries;
    }

    private static FilterExecutionPlanCacheStore requireCacheStore(FilterExecutionPlanCacheStore cacheStore) {
        if (cacheStore == null) {
            throw new IllegalArgumentException("executionPlanCache must not be null");
        }
        return cacheStore;
    }

    private void materializeJoinRows() {
        if (computedFieldRegistry.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, List<QueryRow>> entry : spec.getJoinClasses().entrySet()) {
            if (joinSourceBeans.containsKey(entry.getKey())) {
                continue;
            }
            entry.setValue(materializedRows(entry.getValue()));
        }
    }

    private List<String> schemaFields() {
        if (spec.getFieldTypes().isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(spec.getFieldTypes().keySet());
    }

    private boolean configuredFieldExists(String fieldName) {
        return computedFieldRegistry.contains(fieldName) || spec.getFieldTypes().containsKey(fieldName);
    }

    private Class<?> configuredFieldType(String fieldName) {
        if (computedFieldRegistry.contains(fieldName)) {
            return computedFieldRegistry.get(fieldName).outputType();
        }
        return spec.getFieldTypes().get(fieldName);
    }

    private boolean validationSchemaUnknown() {
        return spec.getRows().isEmpty() && spec.getFieldTypes().isEmpty();
    }

    private void refreshFieldTypes() {
        spec.setFieldTypes(ComputedFieldSupport.augmentFieldTypes(spec.getSourceFieldTypes(), computedFieldRegistry));
    }

    private List<QueryRow> materializedRows(List<QueryRow> rows) {
        return ComputedFieldSupport.materializeRows(rows, computedFieldRegistry);
    }

    private void bindPreparedExecutionSources(List<?> pojos, Map<Integer, List<?>> joinSourcesByIndex) {
        bindPreparedSourceRows(pojos);
        joinSourceBeans = new HashMap<>();
        for (Integer joinIndex : new ArrayList<>(spec.getJoinClasses().keySet())) {
            spec.getJoinClasses().put(joinIndex, new ArrayList<>());
            bindPreparedJoinSource(joinIndex, joinSourcesByIndex.get(joinIndex));
        }
    }

    private void bindPreparedSourceRows(List<?> pojos) {
        spec.setSourceFieldTypes(inferSourceFieldTypes(pojos));
        refreshFieldTypes();
        Object first = CollectionUtil.firstNonNull(pojos);
        if (first instanceof QueryRow) {
            sourceBeans = List.of();
            fullyMaterializedSourceRows = false;
            materializedSourceFields = Set.of();
            spec.setRows(materializedRows(queryRows(pojos)));
            return;
        }
        sourceBeans = directSourceBeans(pojos);
        fullyMaterializedSourceRows = false;
        materializedSourceFields = Set.of();
        spec.setRows(new ArrayList<>());
    }

    private void bindPreparedJoinSource(int joinIndex, List<?> children) {
        Map<String, Class<?>> childFieldTypes = inferSourceFieldTypes(children);
        spec.getJoinSourceFieldTypes().put(joinIndex, new LinkedHashMap<>(childFieldTypes));
        Object first = CollectionUtil.firstNonNull(children);
        if (first instanceof QueryRow) {
            spec.getJoinClasses().put(joinIndex, materializedRows(queryRows(children)));
            return;
        }
        if (children == null || children.isEmpty()) {
            spec.getJoinClasses().put(joinIndex, new ArrayList<>());
            return;
        }
        joinSourceBeans.put(joinIndex, directSourceBeans(children));
    }

    private void initializeSourceRows(List<?> pojos) {
        Object first = CollectionUtil.firstNonNull(pojos);
        if (first instanceof QueryRow) {
            sourceBeans = List.of();
            clearMaterializedSourceRows();
            spec.setRows(materializedRows(queryRows(pojos)));
            return;
        }
        sourceBeans = copySourceBeans(pojos);
        clearMaterializedSourceRows();
        spec.setRows(new ArrayList<>());
    }

    private Map<String, Class<?>> inferSourceFieldTypes(List<?> pojos) {
        Object first = CollectionUtil.firstNonNull(pojos);
        if (first == null) {
            return Map.of();
        }
        if (first instanceof QueryRow) {
            return ReflectionUtil.collectQueryRowFieldTypes(queryRows(pojos));
        }
        return ReflectionUtil.collectQueryableFieldTypes(first.getClass());
    }

    private List<QueryRow> toSourceRows(List<?> pojos) {
        Object first = CollectionUtil.firstNonNull(pojos);
        if (first instanceof QueryRow) {
            return queryRows(pojos);
        }
        return ReflectionUtil.toDomainRows(pojos);
    }

    private void ensureRowsMaterialized() {
        if (!hasSourceBeans()) {
            return;
        }
        SourceMaterializationPlan plan = sourceMaterializationPlan();
        if (materializedSourceRowsSatisfy(plan)) {
            return;
        }

        List<QueryRow> baseRows = plan.full()
                ? ReflectionUtil.toDomainRows(sourceBeans)
                : ReflectionUtil.toDomainRows(sourceBeans, plan.fields());
        spec.setRows(materializedRows(baseRows));
        fullyMaterializedSourceRows = plan.full();
        materializedSourceFields = plan.full() ? Set.of() : Set.copyOf(plan.fields());
    }

    private SourceMaterializationPlan sourceMaterializationPlan() {
        if (requiresFullSourceMaterialization()) {
            return SourceMaterializationPlan.fullPlan();
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        addSelectedFields(selected, spec.getSourceFieldTypes(), spec.getReturnFields());
        addSelectedFields(selected, spec.getSourceFieldTypes(), spec.getFilterFields().values());
        addSelectedFields(selected, spec.getSourceFieldTypes(), spec.getDistinctFields().values());
        addSelectedFields(selected, spec.getSourceFieldTypes(), spec.getOrderFields().values());
        addSelectedFields(selected, spec.getSourceFieldTypes(), spec.getJoinParentFields().values());
        addWindowSourceFields(selected, spec.getSourceFieldTypes());
        addGroupSourceFields(selected);
        addMetricSourceFields(selected);
        addTimeBucketSourceFields(selected);

        if (selected.isEmpty()) {
            return SourceMaterializationPlan.fullPlan();
        }
        return SourceMaterializationPlan.selectivePlan(selected);
    }

    private boolean requiresFullSourceMaterialization() {
        if (!spec.getJoinClasses().isEmpty() || !joinSourceBeans.isEmpty()) {
            if (!supportsSelectiveJoinMaterialization()) {
                return true;
            }
        }
        if (!spec.getAllOfGroups().isEmpty()
                || !spec.getAnyOfGroups().isEmpty()
                || !spec.getHavingAllOfGroups().isEmpty()
                || !spec.getHavingAnyOfGroups().isEmpty()
                || !spec.getQualifyAllOfGroups().isEmpty()
                || !spec.getQualifyAnyOfGroups().isEmpty()) {
            return true;
        }
        if (requiresOpenEndedRowMaterialization()) {
            return true;
        }
        return false;
    }

    private void addSelectedFields(LinkedHashSet<String> selected,
                                   Map<String, Class<?>> availableFieldTypes,
                                   Iterable<String> fieldNames) {
        for (String fieldName : fieldNames) {
            addSelectedField(selected, availableFieldTypes, fieldName);
        }
    }

    private void addSelectedField(LinkedHashSet<String> selected,
                                  Map<String, Class<?>> availableFieldTypes,
                                  String fieldName) {
        if (fieldName == null) {
            return;
        }
        if (availableFieldTypes.containsKey(fieldName)) {
            selected.add(fieldName);
            return;
        }
        if (computedFieldRegistry.contains(fieldName)) {
            addComputedDependencies(selected, availableFieldTypes, fieldName, new LinkedHashSet<>());
        }
    }

    private void addComputedDependencies(LinkedHashSet<String> selected,
                                         Map<String, Class<?>> availableFieldTypes,
                                         String computedFieldName,
                                         Set<String> visiting) {
        if (!visiting.add(computedFieldName)) {
            return;
        }
        try {
            ComputedFieldDefinition definition = computedFieldRegistry.get(computedFieldName);
            if (definition == null) {
                return;
            }
            for (String dependency : definition.dependencies()) {
                if (availableFieldTypes.containsKey(dependency)) {
                    selected.add(dependency);
                    continue;
                }
                if (computedFieldRegistry.contains(dependency)) {
                    addComputedDependencies(selected, availableFieldTypes, dependency, visiting);
                }
            }
        } finally {
            visiting.remove(computedFieldName);
        }
    }

    private void addGroupSourceFields(LinkedHashSet<String> selected) {
        for (String fieldName : spec.getGroupFields().values()) {
            if (!spec.getTimeBuckets().containsKey(fieldName)) {
                addSelectedField(selected, spec.getSourceFieldTypes(), fieldName);
            }
        }
    }

    private void addMetricSourceFields(LinkedHashSet<String> selected) {
        for (QueryMetric metric : spec.getMetrics()) {
            if (!Metric.COUNT.equals(metric.getMetric())) {
                addSelectedField(selected, spec.getSourceFieldTypes(), metric.getField());
            }
        }
    }

    private void addTimeBucketSourceFields(LinkedHashSet<String> selected) {
        for (QueryTimeBucket bucket : spec.getTimeBuckets().values()) {
            addSelectedField(selected, spec.getSourceFieldTypes(), bucket.getDateField());
        }
    }

    private boolean materializedSourceRowsSatisfy(SourceMaterializationPlan plan) {
        if (spec.getRows().isEmpty()) {
            return false;
        }
        if (fullyMaterializedSourceRows) {
            return true;
        }
        if (plan.full()) {
            return false;
        }
        return materializedSourceFields.containsAll(plan.fields());
    }

    private boolean hasSourceBeans() {
        return sourceBeans != null && !sourceBeans.isEmpty();
    }

    private void clearMaterializedSourceRows() {
        fullyMaterializedSourceRows = false;
        materializedSourceFields = Set.of();
        if (hasSourceBeans()) {
            spec.setRows(new ArrayList<>());
        }
    }

    private List<?> copySourceBeans(List<?> pojos) {
        if (pojos == null || pojos.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(pojos);
    }

    private List<?> directSourceBeans(List<?> pojos) {
        if (pojos == null || pojos.isEmpty()) {
            return List.of();
        }
        return pojos;
    }

    private Map<Integer, List<?>> copyJoinSourceBeans() {
        if (joinSourceBeans.isEmpty()) {
            return new HashMap<>();
        }
        HashMap<Integer, List<?>> copy = new HashMap<>(Math.max(16, joinSourceBeans.size() * 2));
        for (Map.Entry<Integer, List<?>> entry : joinSourceBeans.entrySet()) {
            copy.put(entry.getKey(), copySourceBeans(entry.getValue()));
        }
        return copy;
    }

    private void materializePendingJoinRows() {
        if (joinSourceBeans.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, List<?>> entry : new ArrayList<>(joinSourceBeans.entrySet())) {
            List<?> source = entry.getValue();
            List<QueryRow> rows;
            if (supportsSelectiveJoinMaterialization()) {
                Map<String, Class<?>> childFieldTypes = inferSourceFieldTypes(source);
                SourceMaterializationPlan plan = joinSourceMaterializationPlan(entry.getKey(), childFieldTypes);
                rows = materializedRows(plan.full()
                        ? toSourceRows(source)
                        : ReflectionUtil.toDomainRows(source, plan.fields()));
            } else {
                rows = materializedRows(toSourceRows(source));
            }
            spec.getJoinClasses().put(entry.getKey(), rows);
            joinSourceBeans.remove(entry.getKey());
        }
    }

    private SourceMaterializationPlan joinSourceMaterializationPlan(int joinIndex,
                                                                    Map<String, Class<?>> childFieldTypes) {
        if (requiresOpenEndedRowMaterialization()) {
            return SourceMaterializationPlan.fullPlan();
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        addSelectedField(selected, childFieldTypes, spec.getJoinChildFields().get(joinIndex));
        addSelectedFields(selected, childFieldTypes, spec.getReturnFields());
        addSelectedFields(selected, childFieldTypes, spec.getFilterFields().values());
        addSelectedFields(selected, childFieldTypes, spec.getDistinctFields().values());
        addSelectedFields(selected, childFieldTypes, spec.getOrderFields().values());
        addSelectedFields(selected, childFieldTypes, spec.getGroupFields().values());
        addWindowSourceFields(selected, childFieldTypes);
        for (QueryMetric metric : spec.getMetrics()) {
            if (!Metric.COUNT.equals(metric.getMetric())) {
                addSelectedField(selected, childFieldTypes, metric.getField());
            }
        }
        for (QueryTimeBucket bucket : spec.getTimeBuckets().values()) {
            addSelectedField(selected, childFieldTypes, bucket.getDateField());
        }
        if (selected.isEmpty()) {
            return SourceMaterializationPlan.fullPlan();
        }
        return SourceMaterializationPlan.selectivePlan(selected);
    }

    private void addWindowSourceFields(LinkedHashSet<String> selected,
                                       Map<String, Class<?>> fieldTypes) {
        for (QueryWindow window : spec.getWindows()) {
            if (window.valueField() != null) {
                addSelectedField(selected, fieldTypes, window.valueField());
            }
            for (String partitionField : window.partitionFields()) {
                addSelectedField(selected, fieldTypes, partitionField);
            }
            for (QueryWindowOrder orderField : window.orderFields()) {
                addSelectedField(selected, fieldTypes, orderField.field());
            }
        }
    }

    private boolean supportsSelectiveJoinMaterialization() {
        if (joinDefinitionCount() != 1) {
            return false;
        }
        Integer joinIndex = joinDefinitionIndexes().stream().findFirst().orElse(null);
        if (joinIndex == null) {
            return false;
        }
        return !hasJoinSchemaCollision(joinIndex);
    }

    private boolean hasJoinSchemaCollision(int joinIndex) {
        Set<String> parentFieldNames = materializedFieldNames(spec.getSourceFieldTypes());
        Set<String> childFieldNames = materializedFieldNames(joinFieldTypes(joinIndex));
        if (childFieldNames.isEmpty()) {
            return false;
        }
        for (String fieldName : childFieldNames) {
            if (parentFieldNames.contains(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> materializedFieldNames(Map<String, Class<?>> availableFieldTypes) {
        if (availableFieldTypes.isEmpty()) {
            return Set.of();
        }
        return ComputedFieldSupport.augmentFieldNames(availableFieldTypes.keySet(), computedFieldRegistry);
    }

    private Map<String, Class<?>> joinFieldTypes(int joinIndex) {
        List<?> pending = joinSourceBeans.get(joinIndex);
        if (pending != null) {
            return inferSourceFieldTypes(pending);
        }
        Map<String, Class<?>> known = spec.getJoinSourceFieldTypes().get(joinIndex);
        if (known != null && !known.isEmpty()) {
            return known;
        }
        List<QueryRow> rows = spec.getJoinClasses().get(joinIndex);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return ReflectionUtil.collectQueryRowFieldTypes(rows);
    }

    private int joinDefinitionCount() {
        return joinDefinitionIndexes().size();
    }

    private boolean hasJoinDefinitions() {
        return joinDefinitionCount() > 0;
    }

    private Set<Integer> joinDefinitionIndexes() {
        LinkedHashSet<Integer> indexes = new LinkedHashSet<>();
        indexes.addAll(spec.getJoinClasses().keySet());
        indexes.addAll(joinSourceBeans.keySet());
        return indexes;
    }

    private boolean requiresOpenEndedRowMaterialization() {
        return spec.getReturnFields().isEmpty()
                && spec.getMetrics().isEmpty()
                && spec.getGroupFields().isEmpty()
                && spec.getTimeBuckets().isEmpty();
    }

    private Map<String, Class<?>> mergeJoinFieldTypes(Map<String, Class<?>> currentFieldTypes,
                                                      Map<String, Class<?>> joinFieldTypes,
                                                      Join joinMethod) {
        if (joinFieldTypes == null || joinFieldTypes.isEmpty() || joinMethod == null) {
            return currentFieldTypes;
        }
        Map<String, Class<?>> primary = currentFieldTypes;
        Map<String, Class<?>> secondary = joinFieldTypes;
        if (Join.RIGHT_JOIN.equals(joinMethod)) {
            primary = joinFieldTypes;
            secondary = currentFieldTypes;
        }

        LinkedHashMap<String, Class<?>> merged = new LinkedHashMap<>(primary.size() + secondary.size());
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Map.Entry<String, Class<?>> entry : primary.entrySet()) {
            merged.put(entry.getKey(), entry.getValue());
            names.add(entry.getKey());
        }
        for (Map.Entry<String, Class<?>> entry : secondary.entrySet()) {
            String fieldName = entry.getKey();
            if (names.contains(fieldName)) {
                fieldName = uniqueJoinedFieldName(fieldName, names);
            }
            merged.put(fieldName, entry.getValue());
            names.add(fieldName);
        }
        return merged;
    }

    private String uniqueJoinedFieldName(String baseName, Set<String> existing) {
        String candidate = "child_" + baseName;
        int index = 1;
        while (existing.contains(candidate)) {
            candidate = "child_" + baseName + "_" + index;
            index++;
        }
        return candidate;
    }

    private List<QueryRow> queryRows(List<?> pojos) {
        List<QueryRow> rows = new ArrayList<>();
        if (pojos == null) {
            return rows;
        }
        for (Object pojo : pojos) {
            if (pojo instanceof QueryRow row) {
                rows.add(row);
            }
        }
        return rows;
    }

    private boolean supportsPreparedExecutionView(List<?> pojos, Map<Integer, List<?>> joinSourcesByIndex) {
        if (CollectionUtil.firstNonNull(pojos) instanceof QueryRow) {
            return false;
        }
        for (List<?> rows : joinSourcesByIndex.values()) {
            if (CollectionUtil.firstNonNull(rows) instanceof QueryRow) {
                return false;
            }
        }
        return true;
    }

    private void markExecutionPlanShapeChanged() {
        executionPlanShapeVersion++;
    }

    private record SourceMaterializationPlan(boolean full, List<String> fields) {
        private SourceMaterializationPlan(boolean full, List<String> fields) {
            this.full = full;
            this.fields = List.copyOf(fields);
        }

        private static SourceMaterializationPlan fullPlan() {
            return new SourceMaterializationPlan(true, List.of());
        }

        private static SourceMaterializationPlan selectivePlan(Set<String> fields) {
            return new SourceMaterializationPlan(false, new ArrayList<>(fields));
        }
    }
}

