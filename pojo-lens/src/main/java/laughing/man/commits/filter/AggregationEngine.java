package laughing.man.commits.filter;

import laughing.man.commits.internal.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.RawQueryRow;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.GroupKeyUtil;
import laughing.man.commits.util.TimeBucketUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AggregationEngine {

    private final FilterQueryBuilder builder;

    AggregationEngine(FilterQueryBuilder builder) {
        this.builder = builder;
    }

    List<QueryRow> aggregateMetrics(List<QueryRow> rows, FilterExecutionPlan plan) {
        List<FilterExecutionPlan.MetricPlan> metrics = plan.getMetricPlans();
        if (!builder.getGroupFields().isEmpty()) {
            return aggregateGroupedMetrics(rows, plan.getGroupColumns(), metrics);
        }
        Object[] values = new Object[metrics.size()];
        ArrayList<String> schema = new ArrayList<>(metrics.size());
        for (int i = 0; i < metrics.size(); i++) {
            FilterExecutionPlan.MetricPlan metric = metrics.get(i);
            values[i] = calculateMetricValue(rows, metric);
            schema.add(metric.alias());
        }
        return List.of(new RawQueryRow(values, schema));
    }

    private List<QueryRow> aggregateGroupedMetrics(List<QueryRow> rows,
                                                   List<FilterExecutionPlan.GroupColumn> columns,
                                                   List<FilterExecutionPlan.MetricPlan> metrics) {
        int columnCount = columns.size();
        int metricCount = metrics.size();

        // Build shared output schema once: group columns first, then metric aliases.
        ArrayList<String> outputSchema = new ArrayList<>(columnCount + metricCount);
        for (FilterExecutionPlan.GroupColumn column : columns) {
            outputSchema.add(column.fieldName());
        }
        for (FilterExecutionPlan.MetricPlan metric : metrics) {
            outputSchema.add(metric.alias());
        }

        Map<QueryKey, GroupAccumulator> grouped =
                new LinkedHashMap<>(CollectionUtil.expectedMapCapacity(rows == null ? 0 : rows.size()));

        if (rows != null) {
            String[] keyParts = new String[columnCount];
            Object[] projectedValues = new Object[columnCount];
            QueryKey lookupKey = QueryKey.forMutableLookup(keyParts, columnCount);
            @SuppressWarnings("unchecked")
            HashMap<Object, String>[] keyStringCaches = new HashMap[columnCount];
            for (int i = 0; i < columnCount; i++) {
                keyStringCaches[i] = new HashMap<>();
            }
            for (QueryRow row : rows) {
                if (row == null) {
                    continue;
                }
                for (int i = 0; i < columnCount; i++) {
                    FilterExecutionPlan.GroupColumn column = columns.get(i);
                    Object rawValue = row.getValueAt(column.fieldIndex());
                    Object projectedValue = bucketedOrRawValue(column, rawValue);
                    projectedValues[i] = projectedValue;
                    String keyStr = keyStringCaches[i].get(projectedValue);
                    if (keyStr == null) {
                        keyStr = GroupKeyUtil.toGroupKeyValue(projectedValue, column.dateFormat());
                        keyStringCaches[i].put(projectedValue, keyStr);
                    }
                    keyParts[i] = keyStr;
                }
                lookupKey.refresh();
                GroupAccumulator accumulator = grouped.get(lookupKey);
                if (accumulator == null) {
                    accumulator = new GroupAccumulator(projectedValues, columnCount, metrics);
                    grouped.put(new QueryKey(keyParts, columnCount), accumulator);
                }
                accumulator.accumulate(row);
            }
        }

        List<QueryRow> aggregatedRows = new ArrayList<>(grouped.size());
        for (GroupAccumulator group : grouped.values()) {
            Object[] rowValues = new Object[columnCount + metricCount];
            System.arraycopy(group.groupValues, 0, rowValues, 0, columnCount);
            for (int i = 0; i < metricCount; i++) {
                rowValues[columnCount + i] = group.metricAccumulators[i].result();
            }
            aggregatedRows.add(new RawQueryRow(rowValues, outputSchema));
        }
        return aggregatedRows;
    }

    private Object calculateMetricValue(List<QueryRow> rows, FilterExecutionPlan.MetricPlan metric) {
        return switch (metric.metric()) {
            case COUNT -> (long) (rows == null ? 0 : rows.size());
            case SUM, AVG, MIN, MAX -> {
                int fieldIndex = metric.fieldIndex();
                if (fieldIndex < 0) {
                    throw new IllegalArgumentException("Unknown metric field: " + metric.fieldName());
                }

                NumericStats stats = collectNumericStats(rows, fieldIndex, metric);
                if (!stats.present()) {
                    yield null;
                }
                yield switch (metric.metric()) {
                    case SUM -> stats.hasFraction() ? stats.sum() : (long) stats.sum();
                    case AVG -> stats.sum() / stats.count();
                    case MIN -> stats.min();
                    case MAX -> stats.max();
                    case COUNT -> throw new IllegalStateException("COUNT handled before numeric aggregation");
                };
            }
        };
    }

    private NumericStats collectNumericStats(List<QueryRow> rows, int fieldIndex, FilterExecutionPlan.MetricPlan metric) {
        boolean present = false;
        int count = 0;
        Number min = null;
        Number max = null;
        double sum = 0;
        boolean hasFraction = false;
        if (rows == null) {
            return new NumericStats(false, 0, null, null, 0, false);
        }
        for (QueryRow row : rows) {
            if (row == null) {
                continue;
            }
            Object value = row.getValueAt(fieldIndex);
            if (value == null) {
                continue;
            }
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException(
                        "Metric " + metric.metric() + " requires numeric field: " + metric.fieldName());
            }
            Number number = (Number) value;
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
        return new NumericStats(present, count, min, max, sum, hasFraction);
    }

    private Object bucketedOrRawValue(FilterExecutionPlan.GroupColumn column, Object rawValue) {
        if (column.timeBucket() == null) {
            return rawValue;
        }
        return TimeBucketUtil.bucketValue(rawValue, column.timeBucket());
    }

    private record NumericStats(boolean present, int count, Number min, Number max, double sum, boolean hasFraction) {
    }

    private record GroupAccumulator(Object[] groupValues, MetricAccumulator[] metricAccumulators) {

        private GroupAccumulator(Object[] sourceValues, int columnCount, List<FilterExecutionPlan.MetricPlan> metrics) {
            this(Arrays.copyOf(sourceValues, columnCount), new MetricAccumulator[metrics.size()]);
            for (int i = 0; i < metrics.size(); i++) {
                metricAccumulators[i] = new MetricAccumulator(metrics.get(i));
            }
        }

        private void accumulate(QueryRow row) {
            for (MetricAccumulator metricAccumulator : metricAccumulators) {
                metricAccumulator.accumulate(row);
            }
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

        private void accumulate(QueryRow row) {
            if (metric.metric() == Metric.COUNT) {
                count++;
                return;
            }

            int fieldIndex = metric.fieldIndex();
            if (fieldIndex < 0) {
                throw new IllegalArgumentException("Unknown metric field: " + metric.fieldName());
            }

            Object value = row.getValueAt(fieldIndex);
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
            return switch (metric.metric()) {
                case COUNT -> count;
                case SUM -> present ? (hasFraction ? sum : (long) sum) : null;
                case AVG -> present ? sum / count : null;
                case MIN -> present ? min : null;
                case MAX -> present ? max : null;
            };
        }
    }
}

