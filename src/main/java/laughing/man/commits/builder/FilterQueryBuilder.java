package laughing.man.commits.builder;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.computed.internal.ComputedFieldSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import laughing.man.commits.domain.QueryField;
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

    public FilterQueryBuilder(List<?> pojos) {
        this(pojos, FilterExecutionPlanCache.defaultStore());
    }

    public FilterQueryBuilder(List<?> pojos, FilterExecutionPlanCacheStore executionPlanCache) {
        this.executionPlanCache = requireCacheStore(executionPlanCache);
        this.spec.setRows(ReflectionUtil.toDomainRows(pojos));
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
        this.computedFieldRegistry = registry;
        spec.setRows(ComputedFieldSupport.materializeRows(spec.getRows(), registry));
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
        return spec.getRows();
    }

    public void setRows(List<QueryRow> rows) {
        spec.setRows(ComputedFieldSupport.materializeRows(rows, computedFieldRegistry));
    }

    @Override
    public FilterQueryBuilder addJoinBeans(String parentField, List<?> children, String childField, Join joinMethod) {
        List<QueryRow> childRows = ReflectionUtil.toDomainRows(children);
        addJoinRows(parentField, childRows, childField, joinMethod);
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
        return this;
    }

    /**
     * Removes a HAVING rule by rule id.
     * Intended for internal cleaner/normalization flows.
     */
    public FilterQueryBuilder removeHavingRule(String ruleId) {
        spec.removeHavingRule(ruleId);
        return this;
    }

    public Map<Integer, List<QueryRow>> getJoinClasses() {
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
        getJoinClasses().put(index, ComputedFieldSupport.materializeRows(children, computedFieldRegistry));
        getJoinMethods().put(index, joinMethod);
        getJoinParentFields().put(index, parentField);
        getJoinChildFields().put(index, childField);
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
        if (computedFieldRegistry.contains(fieldName)) {
            return;
        }
        if (spec.getRows().isEmpty()) {
            return;
        }
        List<? extends QueryField> fields = spec.getRows().get(0).getFields();
        for (QueryField field : fields) {
            if (fieldName.equals(field.getFieldName())) {
                return;
            }
        }
        throw new IllegalArgumentException("Unknown metric field: " + fieldName);
    }

    private void ensureNumericField(String fieldName, Metric metric) {
        if (computedFieldRegistry.contains(fieldName)) {
            Class<?> outputType = computedFieldRegistry.get(fieldName).outputType();
            if (Number.class.isAssignableFrom(outputType)) {
                return;
            }
            throw new IllegalArgumentException("Metric " + metric + " requires numeric field: " + fieldName);
        }
        if (spec.getRows().isEmpty()) {
            return;
        }
        for (QueryRow row : spec.getRows()) {
            if (row == null || row.getFields() == null) {
                continue;
            }
            for (QueryField field : row.getFields()) {
                if (!fieldName.equals(field.getFieldName())) {
                    continue;
                }
                Object value = field.getValue();
                if (value == null) {
                    continue;
                }
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException("Metric " + metric + " requires numeric field: " + fieldName);
                }
                return;
            }
        }
    }

    private void ensureDateField(String fieldName) {
        if (spec.getRows().isEmpty()) {
            return;
        }
        for (QueryRow row : spec.getRows()) {
            if (row == null || row.getFields() == null) {
                continue;
            }
            for (QueryField field : row.getFields()) {
                if (!fieldName.equals(field.getFieldName())) {
                    continue;
                }
                Object value = field.getValue();
                if (value == null) {
                    continue;
                }
                if (!(value instanceof java.util.Date)) {
                    throw new IllegalArgumentException("Time bucket requires date field: " + fieldName);
                }
                return;
            }
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
        if (computedFieldRegistry.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, List<QueryRow>> entry : spec.getJoinClasses().entrySet()) {
            entry.setValue(ComputedFieldSupport.materializeRows(entry.getValue(), computedFieldRegistry));
        }
    }

    private List<String> schemaFields() {
        if (spec.getRows() == null || spec.getRows().isEmpty() || spec.getRows().get(0) == null || spec.getRows().get(0).getFields() == null) {
            return List.of();
        }
        List<String> fields = new ArrayList<>(spec.getRows().get(0).getFields().size());
        for (QueryField field : spec.getRows().get(0).getFields()) {
            fields.add(field.getFieldName());
        }
        return fields;
    }
}

