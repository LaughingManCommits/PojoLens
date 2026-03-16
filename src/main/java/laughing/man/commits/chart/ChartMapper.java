package laughing.man.commits.chart;

import laughing.man.commits.chart.validation.ChartValidation;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
        MultiSeriesAccumulator accumulator = new MultiSeriesAccumulator(spec);

        for (T row : rows) {
            if (row == null) {
                continue;
            }
            Object x = readField(row, spec.xField());
            String series = stringSeriesValue(readField(row, spec.seriesField()));
            Double value = ChartValidation.validateYValue(readField(row, spec.yField()), spec.yField());
            accumulator.addPoint(x, series, value);
        }
        return accumulator.finish(chartData);
    }

    private static ChartData mapMultiSeriesQueryRows(List<QueryRow> rows,
                                                     ChartSpec spec,
                                                     ChartData chartData,
                                                     IndexedRowReadPlan readPlan) {
        MultiSeriesAccumulator accumulator = new MultiSeriesAccumulator(spec);

        for (QueryRow row : rows) {
            if (row == null) {
                continue;
            }
            Object x = readQueryRowField(row, spec.xField(), readPlan.xFieldIndex());
            String series = stringSeriesValue(
                    readQueryRowField(row, spec.seriesField(), readPlan.seriesFieldIndex())
            );
            Double value = ChartValidation.validateYValue(
                    readQueryRowField(row, spec.yField(), readPlan.yFieldIndex()),
                    spec.yField()
            );
            accumulator.addPoint(x, series, value);
        }
        return accumulator.finish(chartData);
    }

    private static ChartData mapMultiSeriesArrayRows(List<Object[]> rows,
                                                     ChartSpec spec,
                                                     ChartData chartData,
                                                     IndexedRowReadPlan readPlan) {
        MultiSeriesAccumulator accumulator = new MultiSeriesAccumulator(spec);

        for (Object[] row : rows) {
            if (row == null) {
                continue;
            }
            Object x = readArrayRowField(row, readPlan.xFieldIndex());
            String series = stringSeriesValue(readArrayRowField(row, readPlan.seriesFieldIndex()));
            Double value = ChartValidation.validateYValue(
                    readArrayRowField(row, readPlan.yFieldIndex()),
                    spec.yField()
            );
            accumulator.addPoint(x, series, value);
        }
        return accumulator.finish(chartData);
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
        List<Integer> indexes = sortedLabelIndexes(labels);

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

    private static List<Integer> sortedLabelIndexes(List<String> labels) {
        List<Integer> indexes = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            indexes.add(i);
        }
        indexes.sort((left, right) -> Comparator.nullsFirst(String::compareTo)
                .compare(labels.get(left), labels.get(right)));
        return indexes;
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

    private static final class MultiSeriesAccumulator {
        private final ChartSpec spec;
        private final Map<Object, String> labelTextCache;
        private final LinkedHashMap<String, Integer> labelIndexes;
        private final LinkedHashMap<String, Integer> seriesIndexes;
        private final List<String> labels;
        private final List<String> series;
        private final List<List<Double>> valuesBySeries;

        private MultiSeriesAccumulator(ChartSpec spec) {
            this.spec = spec;
            this.labelTextCache = new HashMap<>();
            this.labelIndexes = new LinkedHashMap<>();
            this.seriesIndexes = new LinkedHashMap<>();
            this.labels = new ArrayList<>();
            this.series = new ArrayList<>();
            this.valuesBySeries = new ArrayList<>();
        }

        private void addPoint(Object rawX, String seriesValue, Double value) {
            int labelIndex = labelIndex(rawX);
            int seriesIndex = seriesIndex(seriesValue);
            valuesBySeries.get(seriesIndex).set(labelIndex, value);
        }

        private ChartData finish(ChartData chartData) {
            List<Integer> sortedLabelIndexes = spec.sortLabels() ? sortedLabelIndexes(labels) : null;
            List<String> finalLabels = materializeLabels(sortedLabelIndexes);
            List<ChartDataset> datasets = new ArrayList<>(series.size());
            for (int i = 0; i < series.size(); i++) {
                datasets.add(newDataset(spec, series.get(i), materializeValues(valuesBySeries.get(i), sortedLabelIndexes)));
            }
            if (spec.percentStacked()) {
                applyPercentStacking(datasets, finalLabels.size());
            }
            chartData.setLabels(finalLabels);
            chartData.setDatasets(datasets);
            return chartData;
        }

        private int labelIndex(Object rawX) {
            String label = labelText(rawX);
            Integer index = labelIndexes.get(label);
            if (index != null) {
                return index;
            }
            int newIndex = labels.size();
            labelIndexes.put(label, newIndex);
            labels.add(label);
            for (List<Double> values : valuesBySeries) {
                values.add(null);
            }
            return newIndex;
        }

        private String labelText(Object rawX) {
            if (labelTextCache.containsKey(rawX)) {
                return labelTextCache.get(rawX);
            }
            String label = ChartValidation.validateXValue(rawX, spec.xField(), spec.dateFormat());
            labelTextCache.put(rawX, label);
            return label;
        }

        private int seriesIndex(String seriesValue) {
            Integer index = seriesIndexes.get(seriesValue);
            if (index != null) {
                return index;
            }
            int newIndex = series.size();
            seriesIndexes.put(seriesValue, newIndex);
            series.add(seriesValue);
            List<Double> values = new ArrayList<>(labels.size());
            for (int i = 0; i < labels.size(); i++) {
                values.add(null);
            }
            valuesBySeries.add(values);
            return newIndex;
        }

        private List<String> materializeLabels(List<Integer> sortedLabelIndexes) {
            if (sortedLabelIndexes == null) {
                return new ArrayList<>(labels);
            }
            List<String> sortedLabels = new ArrayList<>(labels.size());
            for (Integer index : sortedLabelIndexes) {
                sortedLabels.add(labels.get(index));
            }
            return sortedLabels;
        }

        private List<Double> materializeValues(List<Double> values, List<Integer> sortedLabelIndexes) {
            List<Double> materialized = new ArrayList<>(values.size());
            if (sortedLabelIndexes == null) {
                for (Double value : values) {
                    materialized.add(zeroFilled(value));
                }
                return materialized;
            }
            for (Integer index : sortedLabelIndexes) {
                materialized.add(zeroFilled(values.get(index)));
            }
            return materialized;
        }

        private Double zeroFilled(Double value) {
            if (value == null && NullPointPolicy.ZERO.equals(spec.nullPointPolicy())) {
                return 0d;
            }
            return value;
        }
    }

    private record IndexedRowReadPlan(int xFieldIndex, int yFieldIndex, int seriesFieldIndex) {
    }
}
