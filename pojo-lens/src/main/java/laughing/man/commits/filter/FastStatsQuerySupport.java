package laughing.man.commits.filter;

import laughing.man.commits.internal.NumericStatistics;
import laughing.man.commits.internal.builder.FilterQueryBuilder;
import laughing.man.commits.internal.builder.QueryMetric;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.GroupKeyUtil;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.TimeBucketUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public final class FastStatsQuerySupport {

    private static final int INITIAL_GROUP_MAP_SIZE_CAP = 1024;

    private FastStatsQuerySupport() {
    }

    public static FastStatsState tryBuildState(FilterQueryBuilder builder) {
        return tryBuildState(builder, null);
    }

    public static FastStatsState tryBuildState(FilterQueryBuilder builder,
                                               FilterExecutionPlanCacheKey planCacheKey) {
        if (!canUseFastStatsPath(builder)) {
            return null;
        }

        List<?> source = builder.getSourceBeansForExecution();
        Object sample = CollectionUtil.firstNonNull(source);
        if (sample == null) {
            return null;
        }

        LinkedHashSet<String> selectedFieldNames = new LinkedHashSet<>();
        addSelectedSourceFields(builder, selectedFieldNames);
        if (selectedFieldNames.isEmpty()) {
            return null;
        }

        ReflectionUtil.FlatRowReadPlan readPlan =
                ReflectionUtil.compileFlatRowReadPlan(sample.getClass(), selectedFieldNames);
        if (readPlan.size() == 0) {
            return null;
        }

        FilterExecutionPlanCacheKey cacheKey =
                planCacheKey == null ? FilterExecutionPlanCacheKey.from(builder) : planCacheKey;
        FilterExecutionPlan plan = builder.getExecutionPlanCache().getOrBuild(
                cacheKey,
                () -> FilterExecutionPlan.forSchema(builder, readPlan.fieldNames())
        );
        List<FilterExecutionPlan.GroupColumn> groupColumns = plan.getGroupColumns();
        List<FilterExecutionPlan.MetricPlan> metricPlans = plan.getMetricPlans();
        if (metricPlans.isEmpty()) {
            return null;
        }

        List<String> outputSchema = outputSchema(groupColumns, metricPlans);
        if (outputSchema.isEmpty()) {
            return null;
        }

        ArrayList<Object[]> rows = groupColumns.isEmpty()
                ? aggregateGlobal(source, readPlan, metricPlans)
                : aggregateGrouped(source, readPlan, groupColumns, metricPlans);
        return new FastStatsState(List.copyOf(outputSchema), rows);
    }

    public static List<QueryRow> toQueryRows(FastStatsState state) {
        return QueryRowAdapterSupport.toQueryRows(state.schemaFields(), state.rows());
    }

    private static boolean canUseFastStatsPath(FilterQueryBuilder builder) {
        if (builder == null) {
            return false;
        }
        if (builder.getSourceBeansForExecution() == null || builder.getSourceBeansForExecution().isEmpty()) {
            return false;
        }
        if (!builder.getMetrics().isEmpty()) {
            if (!builder.getJoinMethods().isEmpty()
                    || !builder.getJoinSourceBeansForExecution().isEmpty()
                    || !builder.getJoinClasses().isEmpty()) {
                return false;
            }
            if (!builder.getFilterFields().isEmpty()
                    || !builder.getHavingFields().isEmpty()
                    || !builder.getDistinctFields().isEmpty()
                    || builder.isDistinctRows()
                    || !builder.getOrderFields().isEmpty()
                    || !builder.getAllOfGroups().isEmpty()
                    || !builder.getAnyOfGroups().isEmpty()
                    || !builder.getHavingAllOfGroups().isEmpty()
                    || !builder.getHavingAnyOfGroups().isEmpty()
                    || !builder.getQualifyAllOfGroups().isEmpty()
                    || !builder.getQualifyAnyOfGroups().isEmpty()
                    || !builder.getQualifyFields().isEmpty()
                    || !builder.getWindows().isEmpty()
                    || builder.getLimit() != null
                    || builder.getOffset() != null
                    || !builder.getComputedFieldRegistry().isEmpty()) {
                return false;
            }
            return !(CollectionUtil.firstNonNull(builder.getSourceBeansForExecution()) instanceof QueryRow);
        }
        return false;
    }

    private static void addSelectedSourceFields(FilterQueryBuilder builder, LinkedHashSet<String> selectedFieldNames) {
        for (String fieldName : builder.getGroupFields().values()) {
            if (fieldName == null) {
                continue;
            }
            if (builder.getTimeBuckets().containsKey(fieldName)) {
                selectedFieldNames.add(builder.getTimeBuckets().get(fieldName).getDateField());
            } else {
                selectedFieldNames.add(fieldName);
            }
        }
        for (QueryMetric metric : builder.getMetrics()) {
            if (metric == null || metric.getField() == null) {
                continue;
            }
            selectedFieldNames.add(metric.getField());
        }
    }

    private static ArrayList<Object[]> aggregateGlobal(List<?> source,
                                                       ReflectionUtil.FlatRowReadPlan readPlan,
                                                       List<FilterExecutionPlan.MetricPlan> metricPlans) {
        MetricAccumulator[] accumulators = metricAccumulators(metricPlans);
        Object[] rowValues = new Object[readPlan.size()];

        for (Object bean : source) {
            if (bean == null) {
                continue;
            }
            try {
                ReflectionUtil.readFlatRowValues(bean, readPlan, rowValues, 0);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read stats row values", e);
            }
            accumulate(accumulators, rowValues);
        }

        ArrayList<Object[]> rows = new ArrayList<>(1);
        rows.add(metricResults(accumulators));
        return rows;
    }

    private static ArrayList<Object[]> aggregateGrouped(List<?> source,
                                                        ReflectionUtil.FlatRowReadPlan readPlan,
                                                        List<FilterExecutionPlan.GroupColumn> groupColumns,
                                                        List<FilterExecutionPlan.MetricPlan> metricPlans) {
        int columnCount = groupColumns.size();
        if (columnCount == 1) {
            return aggregateSingleGroup(source, readPlan, groupColumns.get(0), metricPlans);
        }
        LinkedHashMap<QueryKey, GroupAccumulator> grouped =
                new LinkedHashMap<>(CollectionUtil.expectedMapCapacity(Math.min(source.size(), INITIAL_GROUP_MAP_SIZE_CAP)));
        Object[] rowValues = new Object[readPlan.size()];
        Object[] keyParts = new Object[columnCount];
        Object[] projectedValues = new Object[columnCount];
        QueryKey lookupKey = QueryKey.forMutableLookup(keyParts, columnCount);

        for (Object bean : source) {
            if (bean == null) {
                continue;
            }
            try {
                ReflectionUtil.readFlatRowValues(bean, readPlan, rowValues, 0);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read stats row values", e);
            }

            for (int i = 0; i < columnCount; i++) {
                FilterExecutionPlan.GroupColumn column = groupColumns.get(i);
                Object rawValue = valueAt(rowValues, column.fieldIndex());
                Object projectedValue = column.timeBucket() == null
                        ? rawValue
                        : TimeBucketUtil.bucketValue(rawValue, column.timeBucket());
                projectedValues[i] = projectedValue;
                keyParts[i] = GroupKeyUtil.groupKey(projectedValue, column.dateFormat());
            }

            lookupKey.refresh();
            GroupAccumulator accumulator = grouped.get(lookupKey);
            if (accumulator == null) {
                accumulator = new GroupAccumulator(copyValues(projectedValues, columnCount), metricPlans);
                grouped.put(new QueryKey(keyParts, columnCount), accumulator);
            }
            accumulator.accumulate(rowValues);
        }

        ArrayList<Object[]> rows = new ArrayList<>(grouped.size());
        for (GroupAccumulator accumulator : grouped.values()) {
            Object[] result = new Object[columnCount + metricPlans.size()];
            System.arraycopy(accumulator.groupProjection(), 0, result, 0, columnCount);
            Object[] metricResults = metricResults(accumulator.metricAccumulators());
            System.arraycopy(metricResults, 0, result, columnCount, metricResults.length);
            rows.add(result);
        }
        return rows;
    }

    private static ArrayList<Object[]> aggregateSingleGroup(List<?> source,
                                                            ReflectionUtil.FlatRowReadPlan readPlan,
                                                            FilterExecutionPlan.GroupColumn groupColumn,
                                                            List<FilterExecutionPlan.MetricPlan> metricPlans) {
        LinkedHashMap<Object, GroupAccumulator> grouped =
                new LinkedHashMap<>(CollectionUtil.expectedMapCapacity(Math.min(source.size(), INITIAL_GROUP_MAP_SIZE_CAP)));
        Object[] rowValues = new Object[readPlan.size()];

        for (Object bean : source) {
            if (bean == null) {
                continue;
            }
            try {
                ReflectionUtil.readFlatRowValues(bean, readPlan, rowValues, 0);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read stats row values", e);
            }

            Object rawValue = valueAt(rowValues, groupColumn.fieldIndex());
            Object projectedValue = groupColumn.timeBucket() == null
                    ? rawValue
                    : TimeBucketUtil.bucketValue(rawValue, groupColumn.timeBucket());
            Object key = GroupKeyUtil.groupKey(projectedValue, groupColumn.dateFormat());
            GroupAccumulator accumulator = grouped.get(key);
            if (accumulator == null) {
                accumulator = new GroupAccumulator(new Object[]{projectedValue}, metricPlans);
                grouped.put(key, accumulator);
            }
            accumulator.accumulate(rowValues);
        }

        ArrayList<Object[]> rows = new ArrayList<>(grouped.size());
        for (GroupAccumulator accumulator : grouped.values()) {
            Object[] result = new Object[1 + metricPlans.size()];
            result[0] = accumulator.groupProjection()[0];
            Object[] metricResults = metricResults(accumulator.metricAccumulators());
            System.arraycopy(metricResults, 0, result, 1, metricResults.length);
            rows.add(result);
        }
        return rows;
    }

    private static List<String> outputSchema(List<FilterExecutionPlan.GroupColumn> groupColumns,
                                             List<FilterExecutionPlan.MetricPlan> metricPlans) {
        ArrayList<String> schema = new ArrayList<>(groupColumns.size() + metricPlans.size());
        for (FilterExecutionPlan.GroupColumn column : groupColumns) {
            schema.add(column.fieldName());
        }
        for (FilterExecutionPlan.MetricPlan metricPlan : metricPlans) {
            schema.add(metricPlan.alias());
        }
        return schema;
    }

    private static MetricAccumulator[] metricAccumulators(List<FilterExecutionPlan.MetricPlan> metricPlans) {
        MetricAccumulator[] accumulators = new MetricAccumulator[metricPlans.size()];
        for (int i = 0; i < metricPlans.size(); i++) {
            accumulators[i] = new MetricAccumulator(metricPlans.get(i));
        }
        return accumulators;
    }

    private static void accumulate(MetricAccumulator[] accumulators, Object[] rowValues) {
        for (MetricAccumulator accumulator : accumulators) {
            accumulator.accumulate(rowValues);
        }
    }

    private static Object[] metricResults(MetricAccumulator[] accumulators) {
        Object[] results = new Object[accumulators.length];
        for (int i = 0; i < accumulators.length; i++) {
            results[i] = accumulators[i].result();
        }
        return results;
    }

    private static Object valueAt(Object[] values, int fieldIndex) {
        if (values == null || fieldIndex < 0 || fieldIndex >= values.length) {
            return null;
        }
        return values[fieldIndex];
    }

    private static Object[] copyValues(Object[] values, int size) {
        Object[] copy = new Object[size];
        System.arraycopy(values, 0, copy, 0, size);
        return copy;
    }
    public static final class FastStatsState {
        private final List<String> schemaFields;
        private final List<Object[]> rows;

        private FastStatsState(List<String> schemaFields, List<Object[]> rows) {
            this.schemaFields = schemaFields == null ? List.of() : List.copyOf(schemaFields);
            this.rows = rows == null ? List.of() : List.copyOf(rows);
        }

        public List<String> schemaFields() {
            return Collections.unmodifiableList(schemaFields);
        }

        public List<Object[]> rows() {
            return Collections.unmodifiableList(rows);
        }
    }

    private record GroupAccumulator(Object[] groupProjection, MetricAccumulator[] metricAccumulators) {

        private GroupAccumulator(Object[] groupProjection, List<FilterExecutionPlan.MetricPlan> metricPlans) {
            this(groupProjection, FastStatsQuerySupport.metricAccumulators(metricPlans));
        }

        private void accumulate(Object[] rowValues) {
            FastStatsQuerySupport.accumulate(metricAccumulators, rowValues);
        }
    }

    private static final class MetricAccumulator {
        private final FilterExecutionPlan.MetricPlan metric;
        private final NumericAccumulator stats;
        private final DistinctValueCounter distinctValues;
        private final NumericStatistics statistics;
        private long count;

        private MetricAccumulator(FilterExecutionPlan.MetricPlan metric) {
            this.metric = metric;
            this.stats = new NumericAccumulator(metric.fieldName());
            this.distinctValues = metric.metric() == Metric.COUNT_DISTINCT ? new DistinctValueCounter() : null;
            this.statistics = NumericStatistics.isStatistical(metric.metric())
                    ? NumericStatistics.of(metric.metric(), metric.argument()) : null;
        }

        private void accumulate(Object[] rowValues) {
            if (distinctValues != null) {
                distinctValues.add(valueAt(rowValues, metric.fieldIndex()));
                return;
            }
            if (statistics != null) {
                Number number = AggregationEngine.numericValue(valueAt(rowValues, metric.fieldIndex()), metric);
                if (number != null) {
                    statistics.add(number);
                }
                return;
            }
            if (metric.metric() == Metric.COUNT) {
                if (metric.fieldIndex() < 0 || valueAt(rowValues, metric.fieldIndex()) != null) {
                    count++;
                }
                return;
            }

            Object value = valueAt(rowValues, metric.fieldIndex());
            if (value == null) {
                return;
            }
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException(
                        "Metric " + metric.metric() + " requires numeric field: " + metric.fieldName());
            }
            stats.add(number);
        }

        private Object result() {
            return switch (metric.metric()) {
                case COUNT -> count;
                case COUNT_DISTINCT -> distinctValues.count();
                case MEDIAN, PERCENTILE, STDDEV, STDDEV_POP, VARIANCE, VAR_POP -> statistics.result();
                case SUM -> stats.sum();
                case AVG -> stats.avg();
                case MIN -> stats.min();
                case MAX -> stats.max();
            };
        }
    }
}
