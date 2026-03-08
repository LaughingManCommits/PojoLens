package laughing.man.commits.chart;

import laughing.man.commits.chart.validation.ChartValidation;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Core chart mapping engine for converting query rows into {@link ChartData}.
 */
public final class ChartMapper {

    private ChartMapper() {
    }

    public static <T> ChartData toChartData(List<T> rows, ChartSpec spec) {
        ChartValidation.validateSpec(spec);

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

        if (rows == null || rows.isEmpty()) {
            return chartData;
        }

        Class<?> rowType = firstNonNullType(rows);
        if (rowType == null) {
            return chartData;
        }
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

    private static <T> Class<?> firstNonNullType(List<T> rows) {
        for (T row : rows) {
            if (row != null) {
                return row.getClass();
            }
        }
        return null;
    }

    private static <T> ChartData mapSingleSeries(List<T> rows, ChartSpec spec, ChartData chartData) {
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
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
}

