package laughing.man.commits.chart;

import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.ReflectionUtil.DirectFieldReadPlan;
import laughing.man.commits.util.SchemaIndexUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core chart mapping engine for converting result rows into {@link ChartData}.
 */
public final class ChartMapper {

    private static final double PERCENTAGE_SCALE = 100d;

    private ChartMapper() {
    }

    public static <T> ChartData toChartData(List<T> rows, ChartSpec spec) {
        ChartValidation.validateSpec(spec);

        ChartData chartData = newChartData(spec);
        if (rows == null || rows.isEmpty()) {
            return chartData;
        }

        Object firstRow = CollectionUtil.firstNonNull(rows);
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
        DirectFieldReadPlan directReadPlan = ReflectionUtil.compileDirectFieldReadPlan(
                rowType,
                chartFieldNames(spec)
        );

        if (!spec.multiSeries()) {
            return mapSingleSeries(rows, spec, chartData, directReadPlan);
        }
        return mapMultiSeries(rows, spec, chartData, directReadPlan);
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

    private static <T> ChartData mapSingleSeries(List<T> rows,
                                                 ChartSpec spec,
                                                 ChartData chartData,
                                                 DirectFieldReadPlan directReadPlan) {
        if (spec.type() == ChartType.SCATTER) {
            return mapScatterPoints(rows, spec, chartData, directReadPlan);
        }
        List<String> labels = new ArrayList<>(rows.size());
        List<Double> values = new ArrayList<>(rows.size());
        for (T row : rows) {
            if (row == null) {
                continue;
            }
            labels.add(readXValue(row, spec, directReadPlan));
            values.add(readYValue(row, spec.yField(), directReadPlan));
        }
        if (spec.sortLabels()) {
            sortSingleSeries(labels, values);
        }
        chartData.setLabels(labels);
        chartData.setDatasets(List.of(newDataset(spec, spec.yField(), values)));
        return chartData;
    }

    private static <T> ChartData mapScatterPoints(List<T> rows,
                                                   ChartSpec spec,
                                                   ChartData chartData,
                                                   DirectFieldReadPlan directReadPlan) {
        List<Double> xValues = new ArrayList<>(rows.size());
        List<Double> yValues = new ArrayList<>(rows.size());
        for (T row : rows) {
            if (row == null) {
                continue;
            }
            xValues.add(readYValue(row, spec.xField(), directReadPlan));
            yValues.add(readYValue(row, spec.yField(), directReadPlan));
        }
        if (spec.sortLabels()) {
            sortScatterPoints(xValues, yValues);
        }
        ChartDataset dataset = newDataset(spec, spec.yField(), yValues);
        dataset.setXValues(xValues);
        chartData.setLabels(new ArrayList<>());
        chartData.setDatasets(List.of(dataset));
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
        if (spec.type() == ChartType.SCATTER) {
            return mapScatterQueryRows(rows, spec, chartData, readPlan);
        }
        List<String> labels = new ArrayList<>(rows.size());
        List<Double> values = new ArrayList<>(rows.size());
        for (QueryRow row : rows) {
            if (row == null) {
                continue;
            }
            Object x = readQueryRowField(row, spec.xField(), readPlan.xFieldIndex());
            Object y = readQueryRowField(row, spec.yField(), readPlan.yFieldIndex());
            labels.add(ChartValidation.validateXValue(x, spec.xField(), spec.dateFormat()));
            values.add(ChartValidation.validateYValueBoxed(y, spec.yField()));
        }
        if (spec.sortLabels()) {
            sortSingleSeries(labels, values);
        }
        chartData.setLabels(labels);
        chartData.setDatasets(List.of(newDataset(spec, spec.yField(), values)));
        return chartData;
    }

    private static ChartData mapScatterQueryRows(List<QueryRow> rows,
                                                  ChartSpec spec,
                                                  ChartData chartData,
                                                  IndexedRowReadPlan readPlan) {
        List<Double> xValues = new ArrayList<>(rows.size());
        List<Double> yValues = new ArrayList<>(rows.size());
        for (QueryRow row : rows) {
            if (row == null) {
                continue;
            }
            Object x = readQueryRowField(row, spec.xField(), readPlan.xFieldIndex());
            Object y = readQueryRowField(row, spec.yField(), readPlan.yFieldIndex());
            xValues.add(ChartValidation.validateYValueBoxed(x, spec.xField()));
            yValues.add(ChartValidation.validateYValueBoxed(y, spec.yField()));
        }
        if (spec.sortLabels()) {
            sortScatterPoints(xValues, yValues);
        }
        ChartDataset dataset = newDataset(spec, spec.yField(), yValues);
        dataset.setXValues(xValues);
        chartData.setLabels(new ArrayList<>());
        chartData.setDatasets(List.of(dataset));
        return chartData;
    }

    private static ChartData mapSingleSeriesArrayRows(List<Object[]> rows,
                                                      ChartSpec spec,
                                                      ChartData chartData,
                                                      IndexedRowReadPlan readPlan) {
        if (spec.type() == ChartType.SCATTER) {
            return mapScatterArrayRows(rows, spec, chartData, readPlan);
        }
        List<String> labels = new ArrayList<>(rows.size());
        List<Double> values = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            if (row == null) {
                continue;
            }
            Object x = readArrayRowField(row, readPlan.xFieldIndex());
            Object y = readArrayRowField(row, readPlan.yFieldIndex());
            labels.add(ChartValidation.validateXValue(x, spec.xField(), spec.dateFormat()));
            values.add(ChartValidation.validateYValueBoxed(y, spec.yField()));
        }
        if (spec.sortLabels()) {
            sortSingleSeries(labels, values);
        }
        chartData.setLabels(labels);
        chartData.setDatasets(List.of(newDataset(spec, spec.yField(), values)));
        return chartData;
    }

    private static ChartData mapScatterArrayRows(List<Object[]> rows,
                                                  ChartSpec spec,
                                                  ChartData chartData,
                                                  IndexedRowReadPlan readPlan) {
        List<Double> xValues = new ArrayList<>(rows.size());
        List<Double> yValues = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            if (row == null) {
                continue;
            }
            Object x = readArrayRowField(row, readPlan.xFieldIndex());
            Object y = readArrayRowField(row, readPlan.yFieldIndex());
            xValues.add(ChartValidation.validateYValueBoxed(x, spec.xField()));
            yValues.add(ChartValidation.validateYValueBoxed(y, spec.yField()));
        }
        if (spec.sortLabels()) {
            sortScatterPoints(xValues, yValues);
        }
        ChartDataset dataset = newDataset(spec, spec.yField(), yValues);
        dataset.setXValues(xValues);
        chartData.setLabels(new ArrayList<>());
        chartData.setDatasets(List.of(dataset));
        return chartData;
    }

    private static List<String> chartFieldNames(ChartSpec spec) {
        if (!spec.multiSeries()) {
            return List.of(spec.xField(), spec.yField());
        }
        return List.of(spec.xField(), spec.yField(), spec.seriesField());
    }

    private static <T> ChartData mapMultiSeries(List<T> rows,
                                                ChartSpec spec,
                                                ChartData chartData,
                                                DirectFieldReadPlan directReadPlan) {
        MultiSeriesAccumulator accumulator = new MultiSeriesAccumulator(spec);
        DirectMultiSeriesReadPlan directRowPlan = directMultiSeriesReadPlan(spec, directReadPlan);

        for (T row : rows) {
            if (row == null) {
                continue;
            }
            Object x;
            String series;
            Double value;
            if (directRowPlan != null && directReadPlan.canRead(row)) {
                x = readDirectMultiSeriesXValue(row, spec, directRowPlan);
                series = readDirectSeriesValue(row, spec.seriesField(), directRowPlan.seriesField());
                value = readDirectYValue(row, spec.yField(), directRowPlan.yField());
            } else {
                x = readMultiSeriesXValue(row, spec, directReadPlan);
                series = readSeriesValue(row, spec.seriesField(), directReadPlan);
                value = readYValue(row, spec.yField(), directReadPlan);
            }
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
            Double value = ChartValidation.validateYValueBoxed(
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
            Double value = ChartValidation.validateYValueBoxed(
                    readArrayRowField(row, readPlan.yFieldIndex()),
                    spec.yField()
            );
            accumulator.addPoint(x, series, value);
        }
        return accumulator.finish(chartData);
    }

    private static IndexedRowReadPlan queryRowReadPlan(List<QueryRow> rows, ChartSpec spec) {
        for (QueryRow row : rows) {
            if (row == null || row.getFieldCount() == 0) {
                continue;
            }
            Map<String, Integer> fieldIndexes = SchemaIndexUtil.indexQueryFields(row.getFields());
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
        Map<String, Integer> fieldIndexes = SchemaIndexUtil.indexFieldNames(fieldNames);
        return new IndexedRowReadPlan(
                requireQueryRowFieldIndex(fieldIndexes, spec.xField()),
                requireQueryRowFieldIndex(fieldIndexes, spec.yField()),
                spec.multiSeries() ? requireQueryRowFieldIndex(fieldIndexes, spec.seriesField()) : -1
        );
    }

    private static int requireQueryRowFieldIndex(Map<String, Integer> fieldIndexes, String fieldName) {
        int index = SchemaIndexUtil.findFieldIndex(fieldIndexes, fieldName);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown chart field '" + fieldName + "'");
        }
        return index;
    }

    private static void applyPercentStacking(List<ChartDataset> datasets, int labelCount) {
        for (int labelIndex = 0; labelIndex < labelCount; labelIndex++) {
            double total = 0d;
            for (ChartDataset dataset : datasets) {
                List<Double> values = dataset.getValues();
                Double value = values.get(labelIndex);
                if (value != null) {
                    total += value;
                }
            }
            if (total == 0d) {
                continue;
            }
            for (ChartDataset dataset : datasets) {
                List<Double> values = dataset.getValues();
                Double value = values.get(labelIndex);
                if (value == null) {
                    continue;
                }
                dataset.setValueAt(labelIndex, (value / total) * PERCENTAGE_SCALE);
            }
        }
    }

    private static Object readQueryRowField(QueryRow row, String fieldName, int fieldIndex) {
        return row.getValueAt(fieldIndex);
    }

    private static Object readArrayRowField(Object[] row, int fieldIndex) {
        if (row == null || fieldIndex < 0 || fieldIndex >= row.length) {
            return null;
        }
        return row[fieldIndex];
    }

    private static String readXValue(Object row, ChartSpec spec, DirectFieldReadPlan directReadPlan) {
        if (canUseDirectFields(row, directReadPlan)
                && directReadPlan.isNumericPrimitiveField(spec.xField())) {
            return readPrimitiveAsString(row, directReadPlan, spec.xField());
        }
        return ChartValidation.validateXValue(
                readField(row, spec.xField(), directReadPlan),
                spec.xField(),
                spec.dateFormat()
        );
    }

    private static Object readMultiSeriesXValue(Object row, ChartSpec spec, DirectFieldReadPlan directReadPlan) {
        if (spec.type() == ChartType.SCATTER) {
            return readField(row, spec.xField(), directReadPlan);
        }
        return readXValue(row, spec, directReadPlan);
    }

    private static DirectMultiSeriesReadPlan directMultiSeriesReadPlan(ChartSpec spec,
                                                                       DirectFieldReadPlan directReadPlan) {
        if (directReadPlan == null) {
            return null;
        }
        Field xField = directReadPlan.field(spec.xField());
        Field yField = directReadPlan.field(spec.yField());
        Field seriesField = directReadPlan.field(spec.seriesField());
        if (xField == null || yField == null || seriesField == null) {
            return null;
        }
        return new DirectMultiSeriesReadPlan(xField, yField, seriesField);
    }

    private static Object readDirectMultiSeriesXValue(Object row,
                                                      ChartSpec spec,
                                                      DirectMultiSeriesReadPlan directReadPlan) {
        Field xField = directReadPlan.xField();
        if (spec.type() == ChartType.SCATTER) {
            return readFieldValue(row, spec.xField(), xField);
        }
        if (xField.getType().isPrimitive() && isNumericPrimitive(xField.getType())) {
            return readPrimitiveAsString(row, spec.xField(), xField);
        }
        return ChartValidation.validateXValue(readFieldValue(row, spec.xField(), xField), spec.xField(), spec.dateFormat());
    }

    private static Double readDirectYValue(Object row, String fieldName, Field field) {
        if (field.getType().isPrimitive() && isNumericPrimitive(field.getType())) {
            return readNumericPrimitiveAsDouble(row, fieldName, field);
        }
        return ChartValidation.validateYValueBoxed(readFieldValue(row, fieldName, field), fieldName);
    }

    private static String readDirectSeriesValue(Object row, String fieldName, Field field) {
        if (field.getType().isPrimitive()) {
            return readPrimitiveAsString(row, fieldName, field);
        }
        return stringSeriesValue(readFieldValue(row, fieldName, field));
    }

    private static Double readYValue(Object row, String fieldName, DirectFieldReadPlan directReadPlan) {
        if (canUseDirectFields(row, directReadPlan)
                && directReadPlan.isNumericPrimitiveField(fieldName)) {
            return readNumericPrimitiveAsDouble(row, directReadPlan, fieldName);
        }
        return ChartValidation.validateYValueBoxed(readField(row, fieldName, directReadPlan), fieldName);
    }

    private static String readSeriesValue(Object row, String fieldName, DirectFieldReadPlan directReadPlan) {
        if (canUseDirectFields(row, directReadPlan)
                && directReadPlan.isPrimitiveField(fieldName)) {
            return readPrimitiveAsString(row, directReadPlan, fieldName);
        }
        return stringSeriesValue(readField(row, fieldName, directReadPlan));
    }

    private static boolean canUseDirectFields(Object row, DirectFieldReadPlan directReadPlan) {
        return directReadPlan != null && directReadPlan.canRead(row);
    }

    private static Object readField(Object row, String fieldName, DirectFieldReadPlan directReadPlan) {
        try {
            if (canUseDirectFields(row, directReadPlan) && directReadPlan.hasField(fieldName)) {
                return directReadPlan.readValue(row, fieldName);
            }
            return ReflectionUtil.getFieldValue(row, fieldName);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("Failed to read chart field '" + fieldName + "'", ex);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to read chart field '" + fieldName + "'", ex);
        }
    }

    private static String readPrimitiveAsString(Object row,
                                                DirectFieldReadPlan directReadPlan,
                                                String fieldName) {
        try {
            return directReadPlan.readPrimitiveAsString(row, fieldName);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("Failed to read chart field '" + fieldName + "'", ex);
        }
    }

    private static String readPrimitiveAsString(Object row,
                                                String fieldName,
                                                Field field) {
        try {
            Class<?> fieldType = field.getType();
            if (fieldType == int.class) {
                return String.valueOf(field.getInt(row));
            }
            if (fieldType == long.class) {
                return String.valueOf(field.getLong(row));
            }
            if (fieldType == double.class) {
                return String.valueOf(field.getDouble(row));
            }
            if (fieldType == float.class) {
                return String.valueOf(field.getFloat(row));
            }
            if (fieldType == short.class) {
                return String.valueOf(field.getShort(row));
            }
            if (fieldType == byte.class) {
                return String.valueOf(field.getByte(row));
            }
            if (fieldType == boolean.class) {
                return String.valueOf(field.getBoolean(row));
            }
            if (fieldType == char.class) {
                return String.valueOf(field.getChar(row));
            }
            return null;
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("Failed to read chart field '" + fieldName + "'", ex);
        }
    }

    private static Double readNumericPrimitiveAsDouble(Object row,
                                                       DirectFieldReadPlan directReadPlan,
                                                       String fieldName) {
        try {
            return directReadPlan.readNumericPrimitiveAsDouble(row, fieldName);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("Failed to read chart field '" + fieldName + "'", ex);
        }
    }

    private static Double readNumericPrimitiveAsDouble(Object row,
                                                       String fieldName,
                                                       Field field) {
        try {
            Class<?> fieldType = field.getType();
            if (fieldType == int.class) {
                return (double) field.getInt(row);
            }
            if (fieldType == long.class) {
                return (double) field.getLong(row);
            }
            if (fieldType == double.class) {
                return field.getDouble(row);
            }
            if (fieldType == float.class) {
                return (double) field.getFloat(row);
            }
            if (fieldType == short.class) {
                return (double) field.getShort(row);
            }
            if (fieldType == byte.class) {
                return (double) field.getByte(row);
            }
            return null;
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("Failed to read chart field '" + fieldName + "'", ex);
        }
    }

    private static Object readFieldValue(Object row, String fieldName, Field field) {
        try {
            return field.get(row);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("Failed to read chart field '" + fieldName + "'", ex);
        }
    }

    private static boolean isNumericPrimitive(Class<?> fieldType) {
        return fieldType == int.class
                || fieldType == long.class
                || fieldType == double.class
                || fieldType == float.class
                || fieldType == short.class
                || fieldType == byte.class;
    }

    private static String stringSeriesValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static void sortScatterPoints(List<Double> xValues, List<Double> yValues) {
        List<Integer> indexes = new ArrayList<>(xValues.size());
        for (int i = 0; i < xValues.size(); i++) {
            indexes.add(i);
        }
        indexes.sort(Comparator.comparingDouble(i -> {
            Double x = xValues.get(i);
            return x == null ? Double.MAX_VALUE : x;
        }));
        List<Double> sortedX = new ArrayList<>(xValues.size());
        List<Double> sortedY = new ArrayList<>(yValues.size());
        for (Integer i : indexes) {
            sortedX.add(xValues.get(i));
            sortedY.add(yValues.get(i));
        }
        xValues.clear();
        xValues.addAll(sortedX);
        yValues.clear();
        yValues.addAll(sortedY);
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

    private record DirectMultiSeriesReadPlan(Field xField, Field yField, Field seriesField) {
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
            String cached = labelTextCache.get(rawX);
            if (cached != null) {
                return cached;
            }
            if (labelTextCache.containsKey(rawX)) {
                return null;
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
