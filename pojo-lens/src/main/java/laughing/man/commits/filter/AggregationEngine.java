package laughing.man.commits.filter;

import laughing.man.commits.internal.NumericStatistics;
import laughing.man.commits.internal.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.RawQueryRow;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.GroupKeyUtil;
import laughing.man.commits.util.TimeBucketUtil;

import java.util.ArrayList;
import java.util.Arrays;
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
            Object[] keyParts = new Object[columnCount];
            Object[] projectedValues = new Object[columnCount];
            QueryKey lookupKey = QueryKey.forMutableLookup(keyParts, columnCount);
            for (QueryRow row : rows) {
                if (row == null) {
                    continue;
                }
                for (int i = 0; i < columnCount; i++) {
                    FilterExecutionPlan.GroupColumn column = columns.get(i);
                    Object rawValue = row.getValueAt(column.fieldIndex());
                    Object projectedValue = bucketedOrRawValue(column, rawValue);
                    projectedValues[i] = projectedValue;
                    keyParts[i] = GroupKeyUtil.groupKey(projectedValue, column.dateFormat());
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
            case COUNT -> countValues(rows, metric);
            case COUNT_DISTINCT -> countDistinctValues(rows, metric);
            case MEDIAN, PERCENTILE, STDDEV, STDDEV_POP, VARIANCE, VAR_POP -> statisticValue(rows, metric);
            case SUM, AVG, MIN, MAX -> {
                int fieldIndex = metric.fieldIndex();
                if (fieldIndex < 0) {
                    throw new IllegalArgumentException("Unknown metric field: " + metric.fieldName());
                }

                NumericAccumulator stats = collectNumericStats(rows, fieldIndex, metric);
                yield switch (metric.metric()) {
                    case SUM -> stats.sum();
                    case AVG -> stats.avg();
                    case MIN -> stats.min();
                    case MAX -> stats.max();
                    default -> throw new IllegalStateException(metric.metric() + " handled before numeric aggregation");
                };
            }
        };
    }

    /**
     * COUNT(*) counts rows; COUNT(field) counts rows whose field is not null.
     */
    private static long countValues(List<QueryRow> rows, FilterExecutionPlan.MetricPlan metric) {
        if (rows == null) {
            return 0L;
        }
        if (metric.fieldIndex() < 0) {
            return rows.size();
        }
        long count = 0;
        for (QueryRow row : rows) {
            if (row != null && row.getValueAt(metric.fieldIndex()) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * COUNT(DISTINCT field): distinct non-null values, compared like GROUP BY keys.
     */
    private static long countDistinctValues(List<QueryRow> rows, FilterExecutionPlan.MetricPlan metric) {
        if (rows == null) {
            return 0L;
        }
        DistinctValueCounter counter = new DistinctValueCounter();
        for (QueryRow row : rows) {
            if (row != null) {
                counter.add(row.getValueAt(requireFieldIndex(metric)));
            }
        }
        return counter.count();
    }

    private static Double statisticValue(List<QueryRow> rows, FilterExecutionPlan.MetricPlan metric) {
        NumericStatistics statistics = NumericStatistics.of(metric.metric(), metric.argument());
        if (rows != null) {
            int fieldIndex = requireFieldIndex(metric);
            for (QueryRow row : rows) {
                Number number = row == null ? null : numericValue(row.getValueAt(fieldIndex), metric);
                if (number != null) {
                    statistics.add(number);
                }
            }
        }
        return statistics.result();
    }

    static Number numericValue(Object value, FilterExecutionPlan.MetricPlan metric) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Metric " + metric.metric() + " requires numeric field: " + metric.fieldName());
        }
        return number;
    }

    private static int requireFieldIndex(FilterExecutionPlan.MetricPlan metric) {
        if (metric.fieldIndex() < 0) {
            throw new IllegalArgumentException("Unknown metric field: " + metric.fieldName());
        }
        return metric.fieldIndex();
    }

    private NumericAccumulator collectNumericStats(List<QueryRow> rows, int fieldIndex, FilterExecutionPlan.MetricPlan metric) {
        NumericAccumulator stats = new NumericAccumulator(metric.fieldName());
        if (rows == null) {
            return stats;
        }
        for (QueryRow row : rows) {
            if (row == null) {
                continue;
            }
            Object value = row.getValueAt(fieldIndex);
            if (value == null) {
                continue;
            }
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException(
                        "Metric " + metric.metric() + " requires numeric field: " + metric.fieldName());
            }
            stats.add(number);
        }
        return stats;
    }

    private Object bucketedOrRawValue(FilterExecutionPlan.GroupColumn column, Object rawValue) {
        if (column.timeBucket() == null) {
            return rawValue;
        }
        return TimeBucketUtil.bucketValue(rawValue, column.timeBucket());
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

        private void accumulate(QueryRow row) {
            if (distinctValues != null) {
                distinctValues.add(row.getValueAt(requireFieldIndex(metric)));
                return;
            }
            if (statistics != null) {
                Number number = numericValue(row.getValueAt(requireFieldIndex(metric)), metric);
                if (number != null) {
                    statistics.add(number);
                }
                return;
            }
            if (metric.metric() == Metric.COUNT) {
                if (metric.fieldIndex() < 0 || row.getValueAt(metric.fieldIndex()) != null) {
                    count++;
                }
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

