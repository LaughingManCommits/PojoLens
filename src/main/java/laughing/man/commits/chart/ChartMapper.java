package laughing.man.commits.chart;

import laughing.man.commits.chart.validation.ChartValidation;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Core chart mapping engine for converting result rows into {@link ChartData}.
 */
public final class ChartMapper {

    private ChartMapper() {
    }

    public static <T> ChartData toChartData(List<T> rows, ChartSpec spec) {
        ChartValidation.validateSpec(spec);

        ChartData chartData = newChartData(spec);
        if (rows == null || rows.isEmpty()) {
            return chartData;
        }

        Object firstRow = firstNonNull(rows);
        if (firstRow == null) {
            return chartData;
        }
        if (firstRow instanceof QueryRow) {
            @SuppressWarnings("unchecked")
            List<QueryRow> queryRows = (List<QueryRow>) (List<?>) rows;
            return mapQueryRows(queryRows, spec, chartData);
        }

        Class<?> rowType = firstRow.getClass();
        ChartValidation.requireFieldExists(rowType, spec.xField());
        ChartValidation.requireFieldExists(rowType, spec.yField());
        if (spec.multiSeries()) {
            ChartValidation.requireFieldExists(rowType, spec.seriesField());
        }

        if (!spec.multiSeries()) {
            return mapSingleSeries(rows, spec, chartData);
        }
        return mapMultiSeries(rows, spec, chartData);
    }

    public static ChartData toChartData(List<Object[]> rows, List<String> fieldNames, ChartSpec spec) {
        ChartValidation.validateSpec(spec);

        ChartData chartData = newChartData(spec);
        if (rows == null || rows.isEmpty()) {
            return chartData;
        }

        IndexedRowReadPlan readPlan = indexedRowReadPlan(fieldNames, spec);
        if (!spec.multiSeries()) {
            return mapSingleSeriesArrayRows(rows, spec, chartData, readPlan);
        }
        return mapMultiSeriesArrayRows(rows, spec, chartData, readPlan);
    }

    private static ChartData newChartData(ChartSpec spec) {
        ChartData chartData = new ChartData();
        chartData.setType(spec.type());
        chartData.setTitle(spec.title());
        chartData.setXLabel(spec.xLabel());
        chartData.setYLabel(spec.yLabel());
        chartData.setStacked(spec.stacked());
        chartData.setPercentStacked(spec.percentStacked());
        chartData.setNullPointPolicy(spec.nullPointPolicy());
        chartData.setLabels(new ArrayList<>());
        chartData.setDatasets(new ArrayList<>());
        return chartData;
    }

    private static Object firstNonNull(List<?> rows) {
        for (Object row : rows) {
            if (row != null) {
                return row;
            }
        }
        return null;
    }

    private static <T> ChartData mapSingleSeries(List<T> rows, ChartSpec spec, ChartData chartData) {
        List<String> labels = new ArrayList<>(rows.size());
        List<Double> values = new ArrayList<>(rows.size());
        for (T row : rows) {
            if (row == null) {
                continue;
            }
            Object x = readField(row, spec.xField());
            Object y = readField(row, spec.yField());
            labels.add(ChartValidation.validateXValue(x, spec.xField(), spec.dateFormat()));
            values.add(ChartValidation.validateYValue(y, spec.yField()));
        }
        if (spec.sortLabels()) {
            sortSingleSeries(labels, values);
        }
        chartData.setLabels(labels);
        chartData.setDatasets(List.of(newDataset(spec, spec.yField(), values)));
        return chartData;
    }

    private static ChartData mapQueryRows(List<QueryRow> rows, ChartSpec spec, ChartData chartData) {
        IndexedRowReadPlan readPlan = queryRowReadPlan(rows, spec);
        if (!spec.multiSeries()) {
            return mapSingleSeriesQueryRows(rows, spec, chartData, readPlan);
        }
        return mapMultiSeriesQueryRows(rows, spec, chartData, readPlan);
    }

    private static ChartData mapSingleSeriesQueryRows(List<QueryRow> rows,
                                                      ChartSpec spec,
                                                      ChartData chartData,
                                                      IndexedRowReadPlan readPlan) {
        List<String> labels = new ArrayList<>(rows.size());
        List<Double> values = new ArrayList<>(rows.size());
        for (QueryRow row : rows) {
            if (row == null) {
                continue;
            }
            Object x = readQueryRowField(row, spec.xField(), readPlan.xFieldIndex());
            Object y = readQueryRowField(row, spec.yField(), readPlan.yFieldIndex());
            labels.add(ChartValidation.validateXValue(x, spec.xField(), spec.dateFormat()));
            values.add(ChartValidation.validateYValue(y, spec.yField()));
        }
        if (spec.sortLabels()) {
            sortSingleSeries(labels, values);
        }
        chartData.setLabels(labels);
        chartData.setDatasets(List.of(newDataset(spec, spec.yField(), values)));
        return chartData;
    }

    private static ChartData mapSingleSeriesArrayRows(List<Object[]> rows,
                                                      ChartSpec spec,
                                                      ChartData chartData,
                                                      IndexedRowReadPlan readPlan) {
        List<String> labels = new ArrayList<>(rows.size());
        List<Double> values = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            if (row == null) {
                continue;
            }
            Object x = readArrayRowField(row, readPlan.xFieldIndex());
            Object y = readArrayRowField(row, readPlan.yFieldIndex());
            labels.add(ChartValidation.validateXValue(x, spec.xField(), spec.dateFormat()));
            values.add(ChartValidation.validateYValue(y, spec.yField()));
        }
        if (spec.sortLabels()) {
            sortSingleSeries(labels, values);
        }
        chartData.setLabels(labels);
        chartData.setDatasets(List.of(newDataset(spec, spec.yField(), values)));
        return chartData;
    }

    private static <T> ChartData mapMultiSeries(List<T> rows, ChartSpec spec, ChartData chartData) {
        LinkedHashSet<String> orderedLabels = new LinkedHashSet<>();
        LinkedHashSet<String> orderedSeries = new LinkedHashSet<>();
        Map<String, Map<String, Double>> valuesBySeries = new LinkedHashMap<>();

        for (T row : rows) {
            if (row == null) {
                continue;
            }
            String x = ChartValidation.validateXValue(readField(row, spec.xField()), spec.xField(), spec.dateFormat());
            String series = stringSeriesValue(readField(row, spec.seriesField()));
            Double value = ChartValidation.validateYValue(readField(row, spec.yField()), spec.yField());
            orderedLabels.add(x);
            orderedSeries.add(series);
            valuesBySeries.computeIfAbsent(series, key -> new LinkedHashMap<>()).put(x, value);
        }

        List<String> labels = new ArrayList<>(orderedLabels);
        if (spec.sortLabels()) {
            labels.sort(Comparator.nullsFirst(String::compareTo));
        }

        List<ChartDataset> datasets = new ArrayList<>();
        for (String series : orderedSeries) {
            Map<String, Double> points = valuesBySeries.getOrDefault(series, Collections.emptyMap());
            List<Double> values = new ArrayList<>(labels.size());
            for (String label : labels) {
                Double point = points.get(label);
                if (point == null && NullPointPolicy.ZERO.equals(spec.nullPointPolicy())) {
                    point = 0d;
                }
                values.add(point);
            }
            datasets.add(newDataset(spec, series, values));
        }
        if (spec.percentStacked()) {
            applyPercentStacking(datasets, labels.size());
        }
        chartData.setLabels(labels);
        chartData.setDatasets(datasets);
        return chartData;
    }

    private static ChartData mapMultiSeriesQueryRows(List<QueryRow> rows,
                                                     ChartSpec spec,
                                                     ChartData chartData,
                                                     IndexedRowReadPlan readPlan) {
        LinkedHashSet<String> orderedLabels = new LinkedHashSet<>();
        LinkedHashSet<String> orderedSeries = new LinkedHashSet<>();
        Map<String, Map<String, Double>> valuesBySeries = new LinkedHashMap<>();

        for (QueryRow row : rows) {
            if (row == null) {
                continue;
            }
            String x = ChartValidation.validateXValue(
                    readQueryRowField(row, spec.xField(), readPlan.xFieldIndex()),
                    spec.xField(),
                    spec.dateFormat()
            );
            String series = stringSeriesValue(
                    readQueryRowField(row, spec.seriesField(), readPlan.seriesFieldIndex())
            );
            Double value = ChartValidation.validateYValue(
                    readQueryRowField(row, spec.yField(), readPlan.yFieldIndex()),
                    spec.yField()
            );
            orderedLabels.add(x);
            orderedSeries.add(series);
            valuesBySeries.computeIfAbsent(series, key -> new LinkedHashMap<>()).put(x, value);
        }

        List<String> labels = new ArrayList<>(orderedLabels);
        if (spec.sortLabels()) {
            labels.sort(Comparator.nullsFirst(String::compareTo));
        }

        List<ChartDataset> datasets = new ArrayList<>();
        for (String series : orderedSeries) {
            Map<String, Double> points = valuesBySeries.getOrDefault(series, Collections.emptyMap());
            List<Double> values = new ArrayList<>(labels.size());
            for (String label : labels) {
                Double point = points.get(label);
                if (point == null && NullPointPolicy.ZERO.equals(spec.nullPointPolicy())) {
                    point = 0d;
                }
                values.add(point);
            }
            datasets.add(newDataset(spec, series, values));
        }
        if (spec.percentStacked()) {
            applyPercentStacking(datasets, labels.size());
        }
        chartData.setLabels(labels);
        chartData.setDatasets(datasets);
        return chartData;
    }

    private static ChartData mapMultiSeriesArrayRows(List<Object[]> rows,
                                                     ChartSpec spec,
                                                     ChartData chartData,
                                                     IndexedRowReadPlan readPlan) {
        LinkedHashSet<String> orderedLabels = new LinkedHashSet<>();
        LinkedHashSet<String> orderedSeries = new LinkedHashSet<>();
        Map<String, Map<String, Double>> valuesBySeries = new LinkedHashMap<>();

        for (Object[] row : rows) {
            if (row == null) {
                continue;
            }
            String x = ChartValidation.validateXValue(
                    readArrayRowField(row, readPlan.xFieldIndex()),
                    spec.xField(),
                    spec.dateFormat()
            );
            String series = stringSeriesValue(readArrayRowField(row, readPlan.seriesFieldIndex()));
            Double value = ChartValidation.validateYValue(
                    readArrayRowField(row, readPlan.yFieldIndex()),
                    spec.yField()
            );
            orderedLabels.add(x);
            orderedSeries.add(series);
            valuesBySeries.computeIfAbsent(series, key -> new LinkedHashMap<>()).put(x, value);
        }

        List<String> labels = new ArrayList<>(orderedLabels);
        if (spec.sortLabels()) {
            labels.sort(Comparator.nullsFirst(String::compareTo));
        }

        List<ChartDataset> datasets = new ArrayList<>();
        for (String series : orderedSeries) {
            Map<String, Double> points = valuesBySeries.getOrDefault(series, Collections.emptyMap());
            List<Double> values = new ArrayList<>(labels.size());
            for (String label : labels) {
                Double point = points.get(label);
                if (point == null && NullPointPolicy.ZERO.equals(spec.nullPointPolicy())) {
                    point = 0d;
                }
                values.add(point);
            }
            datasets.add(newDataset(spec, series, values));
        }
        if (spec.percentStacked()) {
            applyPercentStacking(datasets, labels.size());
        }
        chartData.setLabels(labels);
        chartData.setDatasets(datasets);
        return chartData;
    }

    private static IndexedRowReadPlan queryRowReadPlan(List<QueryRow> rows, ChartSpec spec) {
        for (QueryRow row : rows) {
            if (row == null || row.getFields() == null || row.getFields().isEmpty()) {
                continue;
            }
            LinkedHashMap<String, Integer> fieldIndexes = new LinkedHashMap<>();
            List<? extends QueryField> fields = row.getFields();
            for (int i = 0; i < fields.size(); i++) {
                QueryField field = fields.get(i);
                if (field == null || field.getFieldName() == null || field.getFieldName().isBlank()) {
                    continue;
                }
                fieldIndexes.putIfAbsent(field.getFieldName(), i);
            }
            return new IndexedRowReadPlan(
                    requireQueryRowFieldIndex(fieldIndexes, spec.xField()),
                    requireQueryRowFieldIndex(fieldIndexes, spec.yField()),
                    spec.multiSeries() ? requireQueryRowFieldIndex(fieldIndexes, spec.seriesField()) : -1
            );
        }
        throw new IllegalArgumentException("Unknown chart field '" + spec.xField() + "'");
    }

    private static IndexedRowReadPlan indexedRowReadPlan(List<String> fieldNames, ChartSpec spec) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            throw new IllegalArgumentException("Unknown chart field '" + spec.xField() + "'");
        }
        LinkedHashMap<String, Integer> fieldIndexes = new LinkedHashMap<>();
        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            fieldIndexes.putIfAbsent(fieldName, i);
        }
        return new IndexedRowReadPlan(
                requireQueryRowFieldIndex(fieldIndexes, spec.xField()),
                requireQueryRowFieldIndex(fieldIndexes, spec.yField()),
                spec.multiSeries() ? requireQueryRowFieldIndex(fieldIndexes, spec.seriesField()) : -1
        );
    }

    private static int requireQueryRowFieldIndex(Map<String, Integer> fieldIndexes, String fieldName) {
        Integer index = fieldIndexes.get(fieldName);
        if (index == null) {
            throw new IllegalArgumentException("Unknown chart field '" + fieldName + "'");
        }
        return index;
    }

    private static void applyPercentStacking(List<ChartDataset> datasets, int labelCount) {
        for (int labelIndex = 0; labelIndex < labelCount; labelIndex++) {
            double total = 0d;
            for (ChartDataset dataset : datasets) {
                Double value = dataset.getValues().get(labelIndex);
                if (value != null) {
                    total += value;
                }
            }
            if (total == 0d) {
                continue;
            }
            for (ChartDataset dataset : datasets) {
                Double value = dataset.getValues().get(labelIndex);
                if (value == null) {
                    continue;
                }
                dataset.getValues().set(labelIndex, (value / total) * 100d);
            }
        }
    }

    private static Object readQueryRowField(QueryRow row, String fieldName, int fieldIndex) {
        List<? extends QueryField> fields = row.getFields();
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        if (fieldIndex >= 0 && fieldIndex < fields.size()) {
            QueryField indexedField = fields.get(fieldIndex);
            if (indexedField != null && fieldName.equals(indexedField.getFieldName())) {
                return indexedField.getValue();
            }
        }
        for (int i = 0; i < fields.size(); i++) {
            QueryField field = fields.get(i);
            if (field != null && fieldName.equals(field.getFieldName())) {
                return field.getValue();
            }
        }
        return null;
    }

    private static Object readArrayRowField(Object[] row, int fieldIndex) {
        if (row == null || fieldIndex < 0 || fieldIndex >= row.length) {
            return null;
        }
        return row[fieldIndex];
    }

    private static Object readField(Object row, String fieldName) {
        try {
            return ReflectionUtil.getFieldValue(row, fieldName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read chart field '" + fieldName + "'", e);
        }
    }

    private static String stringSeriesValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static void sortSingleSeries(List<String> labels, List<Double> values) {
        List<Integer> indexes = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            indexes.add(i);
        }
        indexes.sort((left, right) -> Comparator.nullsFirst(String::compareTo)
                .compare(labels.get(left), labels.get(right)));

        List<String> sortedLabels = new ArrayList<>(labels.size());
        List<Double> sortedValues = new ArrayList<>(values.size());
        for (Integer index : indexes) {
            sortedLabels.add(labels.get(index));
            sortedValues.add(values.get(index));
        }
        labels.clear();
        labels.addAll(sortedLabels);
        values.clear();
        values.addAll(sortedValues);
    }

    private static ChartDataset newDataset(ChartSpec spec, String label, List<Double> values) {
        return new ChartDataset(
                label,
                values,
                spec.colorHintForDataset(label),
                spec.stackGroupIdForDataset(label),
                spec.axisIdForDataset(label)
        );
    }

    private record IndexedRowReadPlan(int xFieldIndex, int yFieldIndex, int seriesFieldIndex) {
    }
}
