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
import laughing.man.commits.time.TimeBucketPreset;
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

    private final QuerySpec spec = new QuerySpec();
    private final FilterExecutionPlanCacheStore executionPlanCache;
    private boolean copyOnBuild = true;
    private QueryTelemetryListener telemetryListener;
    private String telemetryQueryType = "fluent";
    private String telemetrySource = "fluent";
    private ComputedFieldRegistry computedFieldRegistry = ComputedFieldRegistry.empty();
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
        spec.setSourceFieldTypes(inferSourceFieldTypes(pojos));
        refreshFieldTypes();
        initializeSourceRows(pojos);
    }

    private FilterQueryBuilder(QuerySpec snapshot, FilterExecutionPlanCacheStore executionPlanCache) {
        this.executionPlanCache = requireCacheStore(executionPlanCache);
        this.spec.replaceWith(snapshot, false);
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
    public Filter initFilter() {
        return new FilterImpl(copyOnBuild ? snapshotForExecution() : this);
    }

    @Override
    public Map<String, Object> explain() {
        Map<String, Object> explain = new LinkedHashMap<>();
        explain.put("type", "fluent");
        explain.put("copyOnBuild", copyOnBuild);
        explain.put("limit", spec.getLimit());
        explain.put("selectedFields", new ArrayList<>(spec.getReturnFields()));
        explain.put("groupBy", new TreeMap<>(spec.getGroupFields()));
        explain.put("orderBy", new TreeMap<>(spec.getOrderFields()));
        explain.put("distinct", new TreeMap<>(spec.getDistinctFields()));
        explain.put("whereRuleCount", spec.getFilterValues().size());
        explain.put("havingRuleCount", spec.getHavingValues().size());
        explain.put("joinCount", spec.getJoinClasses().size());
        explain.put("metrics", metricEntries(spec.getMetrics()));
        explain.put("timeBuckets", timeBucketEntries(spec.getTimeBuckets()));
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

    public void setRows(List<QueryRow> rows) {
        markExecutionPlanShapeChanged();
        sourceBeans = List.of();
        clearMaterializedSourceRows();
        spec.setSourceFieldTypes(ReflectionUtil.collectQueryRowFieldTypes(rows));
        refreshFieldTypes();
        spec.setRows(materializedRows(rows));
    }

    @Override
    public FilterQueryBuilder addJoinBeans(String parentField, List<?> children, String childField, Join joinMethod) {
        if (children == null || children.isEmpty()) {
            addJoinRows(parentField, new ArrayList<>(), childField, joinMethod);
            return this;
        }
        Object first = firstNonNull(children);
        if (first instanceof QueryRow || !computedFieldRegistry.isEmpty()) {
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

    public Integer getLimit() {
        return spec.getLimit();
    }

    private int nextJoinIndex() {
        return spec.getJoinClasses().size() + 1;
    }

    private void addJoinRows(String parentField, List<QueryRow> children, String childField, Join joinMethod) {
        int index = nextJoinIndex();
        storeJoinDefinition(index, ComputedFieldSupport.materializeRows(children, computedFieldRegistry), parentField, childField, joinMethod);
    }

    private void addLazyJoinSource(String parentField, List<?> children, String childField, Join joinMethod) {
        int index = nextJoinIndex();
        joinSourceBeans.put(index, copySourceBeans(children));
        storeJoinDefinition(index, new ArrayList<>(), parentField, childField, joinMethod);
    }

    private void storeJoinDefinition(int index,
                                     List<QueryRow> children,
                                     String parentField,
                                     String childField,
                                     Join joinMethod) {
        spec.getJoinClasses().put(index, children);
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
        FilterQueryBuilder snapshot = new FilterQueryBuilder(spec.executionCopy(), executionPlanCache);
        snapshot.copyOnBuild = copyOnBuild;
        snapshot.telemetryListener = telemetryListener;
        snapshot.telemetryQueryType = telemetryQueryType;
        snapshot.telemetrySource = telemetrySource;
        snapshot.computedFieldRegistry = computedFieldRegistry;
        snapshot.sourceBeans = copySourceBeans(sourceBeans);
        snapshot.joinSourceBeans = copyJoinSourceBeans();
        snapshot.fullyMaterializedSourceRows = fullyMaterializedSourceRows;
        snapshot.materializedSourceFields = materializedSourceFields;
        snapshot.executionPlanShapeVersion = executionPlanShapeVersion;
        return snapshot;
    }

    public FilterQueryBuilder snapshotForRows(List<QueryRow> rows) {
        FilterQueryBuilder snapshot = snapshotForExecution();
        snapshot.setRows(rows);
        return snapshot;
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

    public FilterQueryBuilder telemetryContext(String queryType,
                                               String source,
                                               QueryTelemetryListener listener) {
        this.telemetryQueryType = requireIdentifier(queryType, "queryType");
        this.telemetrySource = requireIdentifier(source, "source");
        this.telemetryListener = listener;
        return this;
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

    private static FilterExecutionPlanCacheStore requireCacheStore(FilterExecutionPlanCacheStore cacheStore) {
        if (cacheStore == null) {
            throw new IllegalArgumentException("executionPlanCache must not be null");
        }
        return cacheStore;
    }

    private void materializeJoinRows() {
        materializePendingJoinRows();
        if (computedFieldRegistry.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, List<QueryRow>> entry : spec.getJoinClasses().entrySet()) {
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

    private void initializeSourceRows(List<?> pojos) {
        Object first = firstNonNull(pojos);
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
        Object first = firstNonNull(pojos);
        if (first == null) {
            return Map.of();
        }
        if (first instanceof QueryRow) {
            return ReflectionUtil.collectQueryRowFieldTypes(queryRows(pojos));
        }
        return ReflectionUtil.collectQueryableFieldTypes(first.getClass());
    }

    private List<QueryRow> toSourceRows(List<?> pojos) {
        Object first = firstNonNull(pojos);
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
        addSourceFields(selected, spec.getReturnFields());
        addSourceFields(selected, spec.getFilterFields().values());
        addSourceFields(selected, spec.getDistinctFields().values());
        addSourceFields(selected, spec.getOrderFields().values());
        addSourceFields(selected, spec.getJoinParentFields().values());
        addGroupSourceFields(selected);
        addMetricSourceFields(selected);
        addTimeBucketSourceFields(selected);

        if (selected.isEmpty()) {
            return SourceMaterializationPlan.fullPlan();
        }
        return SourceMaterializationPlan.selectivePlan(selected);
    }

    private boolean requiresFullSourceMaterialization() {
        if (!computedFieldRegistry.isEmpty() && hasJoinDefinitions()) {
            return true;
        }
        if (!spec.getJoinClasses().isEmpty() || !joinSourceBeans.isEmpty()) {
            if (!supportsSelectiveJoinMaterialization()) {
                return true;
            }
        }
        if (!spec.getAllOfGroups().isEmpty()
                || !spec.getAnyOfGroups().isEmpty()
                || !spec.getHavingAllOfGroups().isEmpty()
                || !spec.getHavingAnyOfGroups().isEmpty()) {
            return true;
        }
        if (requiresOpenEndedRowMaterialization()) {
            return true;
        }
        return false;
    }

    private void addSourceFields(LinkedHashSet<String> selected, Iterable<String> fieldNames) {
        for (String fieldName : fieldNames) {
            addSourceField(selected, fieldName);
        }
    }

    private void addSourceField(LinkedHashSet<String> selected, String fieldName) {
        if (fieldName == null) {
            return;
        }
        if (spec.getSourceFieldTypes().containsKey(fieldName)) {
            selected.add(fieldName);
            return;
        }
        if (computedFieldRegistry.contains(fieldName)) {
            addComputedSourceDependencies(selected, fieldName, new LinkedHashSet<>());
        }
    }

    private void addComputedSourceDependencies(LinkedHashSet<String> selected,
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
                if (spec.getSourceFieldTypes().containsKey(dependency)) {
                    selected.add(dependency);
                    continue;
                }
                if (computedFieldRegistry.contains(dependency)) {
                    addComputedSourceDependencies(selected, dependency, visiting);
                }
            }
        } finally {
            visiting.remove(computedFieldName);
        }
    }

    private void addGroupSourceFields(LinkedHashSet<String> selected) {
        for (String fieldName : spec.getGroupFields().values()) {
            if (!spec.getTimeBuckets().containsKey(fieldName)) {
                addSourceField(selected, fieldName);
            }
        }
    }

    private void addMetricSourceFields(LinkedHashSet<String> selected) {
        for (QueryMetric metric : spec.getMetrics()) {
            if (!Metric.COUNT.equals(metric.getMetric())) {
                addSourceField(selected, metric.getField());
            }
        }
    }

    private void addTimeBucketSourceFields(LinkedHashSet<String> selected) {
        for (QueryTimeBucket bucket : spec.getTimeBuckets().values()) {
            addSourceField(selected, bucket.getDateField());
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
        addConfiguredFieldIfPresent(selected, childFieldTypes, spec.getJoinChildFields().get(joinIndex));
        addConfiguredFieldsIfPresent(selected, childFieldTypes, spec.getReturnFields());
        addConfiguredFieldsIfPresent(selected, childFieldTypes, spec.getFilterFields().values());
        addConfiguredFieldsIfPresent(selected, childFieldTypes, spec.getDistinctFields().values());
        addConfiguredFieldsIfPresent(selected, childFieldTypes, spec.getOrderFields().values());
        addConfiguredFieldsIfPresent(selected, childFieldTypes, spec.getGroupFields().values());
        for (QueryMetric metric : spec.getMetrics()) {
            if (!Metric.COUNT.equals(metric.getMetric())) {
                addConfiguredFieldIfPresent(selected, childFieldTypes, metric.getField());
            }
        }
        for (QueryTimeBucket bucket : spec.getTimeBuckets().values()) {
            addConfiguredFieldIfPresent(selected, childFieldTypes, bucket.getDateField());
        }
        if (selected.isEmpty()) {
            return SourceMaterializationPlan.fullPlan();
        }
        return SourceMaterializationPlan.selectivePlan(selected);
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
        Map<String, Class<?>> childFieldTypes = joinFieldTypes(joinIndex);
        if (childFieldTypes.isEmpty()) {
            return false;
        }
        for (String fieldName : childFieldTypes.keySet()) {
            if (spec.getSourceFieldTypes().containsKey(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Class<?>> joinFieldTypes(int joinIndex) {
        List<?> pending = joinSourceBeans.get(joinIndex);
        if (pending != null) {
            return inferSourceFieldTypes(pending);
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

    private void addConfiguredFieldsIfPresent(LinkedHashSet<String> selected,
                                              Map<String, Class<?>> availableFields,
                                              Iterable<String> fieldNames) {
        for (String fieldName : fieldNames) {
            addConfiguredFieldIfPresent(selected, availableFields, fieldName);
        }
    }

    private void addConfiguredFieldIfPresent(LinkedHashSet<String> selected,
                                             Map<String, Class<?>> availableFields,
                                             String fieldName) {
        if (fieldName != null && availableFields.containsKey(fieldName)) {
            selected.add(fieldName);
        }
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

    private Object firstNonNull(List<?> pojos) {
        if (pojos == null) {
            return null;
        }
        for (Object pojo : pojos) {
            if (pojo != null) {
                return pojo;
            }
        }
        return null;
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

