package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.StringUtil;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.TimeBucketUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AggregationEngine {

    private static final String NULL_GROUP_KEY = "<NULL>";
    private final FilterQueryBuilder builder;

    AggregationEngine(FilterQueryBuilder builder) {
        this.builder = builder;
    }

    List<QueryRow> aggregateMetrics(List<QueryRow> rows, FilterExecutionPlan plan) {
        List<FilterExecutionPlan.MetricPlan> metrics = plan.getMetricPlans();
        if (!builder.getGroupFields().isEmpty()) {
            return aggregateGroupedMetrics(rows, plan, metrics);
        }
        List<QueryField> metricFields = new ArrayList<>(metrics.size());
        for (FilterExecutionPlan.MetricPlan metric : metrics) {
            QueryField field = new QueryField();
            field.setFieldName(metric.alias());
            field.setValue(calculateMetricValue(rows, metric));
            metricFields.add(field);
        }
        QueryRow metricRow = new QueryRow();
        metricRow.setRowId(ReflectionUtil.newUUID());
        metricRow.setFields(metricFields);
        return List.of(metricRow);
    }

    private List<QueryRow> aggregateGroupedMetrics(List<QueryRow> rows,
                                                   FilterExecutionPlan plan,
                                                   List<FilterExecutionPlan.MetricPlan> metrics) {
        List<FilterExecutionPlan.GroupColumn> columns = plan.getGroupColumns();
        int columnCount = columns.size();
        Map<QueryKey, GroupAccumulator> grouped = new LinkedHashMap<>(expectedMapSize(rows == null ? 0 : rows.size()));

        if (rows != null) {
            for (QueryRow row : rows) {
                List<? extends QueryField> fields = row == null ? null : row.getFields();
                if (fields == null) {
                    continue;
                }
                String[] keyParts = new String[columnCount];
                Object[] projectedValues = new Object[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    FilterExecutionPlan.GroupColumn column = columns.get(i);
                    Object rawValue = fieldValue(fields, column.fieldIndex());
                    Object projectedValue = bucketedOrRawValue(column, rawValue);
                    String keyPart = toGroupKeyValue(projectedValue, column.dateFormat());
                    keyParts[i] = keyPart;
                    projectedValues[i] = projectedValue;
                }

                QueryKey key = new QueryKey(keyParts, columnCount);
                GroupAccumulator accumulator = grouped.get(key);
                if (accumulator == null) {
                    List<QueryField> groupProjection = new ArrayList<>(columnCount);
                    for (int i = 0; i < columnCount; i++) {
                        FilterExecutionPlan.GroupColumn column = columns.get(i);
                        QueryField projectionField = new QueryField();
                        projectionField.setFieldName(column.fieldName());
                        projectionField.setValue(projectedValues[i]);
                        groupProjection.add(projectionField);
                    }
                    accumulator = new GroupAccumulator(groupProjection);
                    grouped.put(key, accumulator);
                }
                accumulator.rows.add(row);
            }
        }

        List<QueryRow> aggregatedRows = new ArrayList<>(grouped.size());
        for (GroupAccumulator group : grouped.values()) {
            List<QueryField> fields = new ArrayList<>(group.groupProjection.size() + metrics.size());
            fields.addAll(group.groupProjection);
            for (FilterExecutionPlan.MetricPlan metric : metrics) {
                QueryField metricField = new QueryField();
                metricField.setFieldName(metric.alias());
                metricField.setValue(calculateMetricValue(group.rows, metric));
                fields.add(metricField);
            }
            QueryRow row = new QueryRow();
            row.setRowId(ReflectionUtil.newUUID());
            row.setFields(fields);
            aggregatedRows.add(row);
        }
        return aggregatedRows;
    }

    private Object calculateMetricValue(List<QueryRow> rows, FilterExecutionPlan.MetricPlan metric) {
        if (laughing.man.commits.enums.Metric.COUNT.equals(metric.metric())) {
            return (long) (rows == null ? 0 : rows.size());
        }

        int fieldIndex = metric.fieldIndex();
        if (fieldIndex < 0) {
            throw new IllegalArgumentException("Unknown metric field: " + metric.fieldName());
        }

        NumericStats stats = collectNumericStats(rows, fieldIndex, metric);
        if (!stats.present) {
            return null;
        }

        if (laughing.man.commits.enums.Metric.SUM.equals(metric.metric())) {
            return stats.hasFraction ? stats.sum : (long) stats.sum;
        }
        if (laughing.man.commits.enums.Metric.AVG.equals(metric.metric())) {
            return stats.sum / stats.count;
        }
        if (laughing.man.commits.enums.Metric.MIN.equals(metric.metric())) {
            return stats.min;
        }
        if (laughing.man.commits.enums.Metric.MAX.equals(metric.metric())) {
            return stats.max;
        }

        throw new IllegalArgumentException("Unsupported metric: " + metric.metric());
    }

    private NumericStats collectNumericStats(List<QueryRow> rows, int fieldIndex, FilterExecutionPlan.MetricPlan metric) {
        NumericStats stats = new NumericStats();
        if (rows == null) {
            return stats;
        }
        for (QueryRow row : rows) {
            List<? extends QueryField> fields = row == null ? null : row.getFields();
            if (fields == null || fieldIndex >= fields.size()) {
                continue;
            }
            Object value = fields.get(fieldIndex).getValue();
            if (value == null) {
                continue;
            }
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException(
                        "Metric " + metric.metric() + " requires numeric field: " + metric.fieldName());
            }
            Number number = (Number) value;
            if (number instanceof Float || number instanceof Double) {
                stats.hasFraction = true;
            }
            double asDouble = number.doubleValue();
            if (!stats.present) {
                stats.min = number;
                stats.max = number;
                stats.present = true;
            } else {
                if (asDouble < stats.min.doubleValue()) {
                    stats.min = number;
                }
                if (asDouble > stats.max.doubleValue()) {
                    stats.max = number;
                }
            }
            stats.count++;
            stats.sum += asDouble;
        }
        return stats;
    }

    private Object fieldValue(List<? extends QueryField> fields, int fieldIndex) {
        if (fieldIndex < 0 || fieldIndex >= fields.size()) {
            return null;
        }
        return fields.get(fieldIndex).getValue();
    }

    private String toGroupKeyValue(Object rawValue, String dateFormat) {
        if (rawValue == null) {
            return NULL_GROUP_KEY;
        }
        String value = ObjectUtil.castToString(rawValue, dateFormat);
        return StringUtil.isNull(value) ? NULL_GROUP_KEY : value;
    }

    private Object bucketedOrRawValue(FilterExecutionPlan.GroupColumn column, Object rawValue) {
        if (column.timeBucket() == null) {
            return rawValue;
        }
        return TimeBucketUtil.bucketValue(rawValue, column.timeBucket());
    }

    private static final class NumericStats {
        private boolean present;
        private int count;
        private Number min;
        private Number max;
        private double sum;
        private boolean hasFraction;
    }

    private static final class GroupAccumulator {
        private final List<QueryField> groupProjection;
        private final List<QueryRow> rows;

        private GroupAccumulator(List<QueryField> groupProjection) {
            this.groupProjection = groupProjection;
            this.rows = new ArrayList<>(4);
        }
    }

    private int expectedMapSize(int sourceSize) {
        if (sourceSize <= 0) {
            return 16;
        }
        return (int) ((sourceSize / 0.75f) + 1.0f);
    }
}

