package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryMetric;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.GroupKeyUtil;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.TimeBucketUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class FastStatsQuerySupport {

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
                    || !builder.getOrderFields().isEmpty()
                    || !builder.getAllOfGroups().isEmpty()
                    || !builder.getAnyOfGroups().isEmpty()
                    || !builder.getHavingAllOfGroups().isEmpty()
                    || !builder.getHavingAnyOfGroups().isEmpty()
                    || builder.getLimit() != null
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
            if (metric == null || Metric.COUNT.equals(metric.getMetric())) {
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
                new LinkedHashMap<>(CollectionUtil.expectedMapCapacity(source.size()));
        Object[] rowValues = new Object[readPlan.size()];
        String[] keyParts = new String[columnCount];
        Object[] projectedValues = new Object[columnCount];

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
                keyParts[i] = GroupKeyUtil.toGroupKeyValue(projectedValue, column.dateFormat());
            }

            QueryKey key = new QueryKey(keyParts, columnCount);
            GroupAccumulator accumulator = grouped.get(key);
            if (accumulator == null) {
                accumulator = new GroupAccumulator(copyValues(projectedValues, columnCount), metricPlans);
                grouped.put(key, accumulator);
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
        LinkedHashMap<String, GroupAccumulator> grouped =
                new LinkedHashMap<>(CollectionUtil.expectedMapCapacity(source.size()));
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
            String key = GroupKeyUtil.toGroupKeyValue(projectedValue, groupColumn.dateFormat());
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
            this.schemaFields = schemaFields;
            this.rows = rows;
        }

        public List<String> schemaFields() {
            return schemaFields;
        }

        public List<Object[]> rows() {
            return rows;
        }
    }

    private static final class GroupAccumulator {
        private final Object[] groupProjection;
        private final MetricAccumulator[] metricAccumulators;

        private GroupAccumulator(Object[] groupProjection, List<FilterExecutionPlan.MetricPlan> metricPlans) {
            this.groupProjection = groupProjection;
            this.metricAccumulators = FastStatsQuerySupport.metricAccumulators(metricPlans);
        }

        private void accumulate(Object[] rowValues) {
            FastStatsQuerySupport.accumulate(metricAccumulators, rowValues);
        }

        private Object[] groupProjection() {
            return groupProjection;
        }

        private MetricAccumulator[] metricAccumulators() {
            return metricAccumulators;
        }
    }

    private static final class MetricAccumulator {
        private final FilterExecutionPlan.MetricPlan metric;
        private long count;
        private boolean present;
        private Number min;
        private Number max;
        private double sum;
        private boolean hasFraction;

        private MetricAccumulator(FilterExecutionPlan.MetricPlan metric) {
            this.metric = metric;
        }

        private void accumulate(Object[] rowValues) {
            if (Metric.COUNT.equals(metric.metric())) {
                count++;
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
            if (number instanceof Float || number instanceof Double) {
                hasFraction = true;
            }

            double asDouble = number.doubleValue();
            if (!present) {
                min = number;
                max = number;
                present = true;
            } else {
                if (asDouble < min.doubleValue()) {
                    min = number;
                }
                if (asDouble > max.doubleValue()) {
                    max = number;
                }
            }
            count++;
            sum += asDouble;
        }

        private Object result() {
            if (Metric.COUNT.equals(metric.metric())) {
                return count;
            }
            if (!present) {
                return null;
            }
            if (Metric.SUM.equals(metric.metric())) {
                return hasFraction ? sum : (long) sum;
            }
            if (Metric.AVG.equals(metric.metric())) {
                return sum / count;
            }
            if (Metric.MIN.equals(metric.metric())) {
                return min;
            }
            if (Metric.MAX.equals(metric.metric())) {
                return max;
            }
            throw new IllegalArgumentException("Unsupported metric: " + metric.metric());
        }
    }
}
