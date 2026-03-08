package laughing.man.commits.chart;

import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for converting query result rows into chart-friendly shapes.
 */
public final class ChartResultMapper {

    private ChartResultMapper() {
    }

    public static <T> List<SeriesPoint> toSeriesPoints(List<T> rows,
                                                       String labelField,
                                                       String valueField) {
        List<SeriesPoint> points = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return points;
        }
        for (T row : rows) {
            if (row == null) {
                continue;
            }
            try {
                Object label = ReflectionUtil.getFieldValue(row, labelField);
                Object value = ReflectionUtil.getFieldValue(row, valueField);
                points.add(new SeriesPoint(stringValue(label), numberValue(value, valueField)));
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to map row to SeriesPoint", e);
            }
        }
        return points;
    }

    public static <T> List<MultiSeriesPoint> toMultiSeriesPoints(List<T> rows,
                                                                  String xField,
                                                                  String seriesField,
                                                                  String valueField) {
        List<MultiSeriesPoint> points = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return points;
        }
        for (T row : rows) {
            if (row == null) {
                continue;
            }
            try {
                Object x = ReflectionUtil.getFieldValue(row, xField);
                Object series = ReflectionUtil.getFieldValue(row, seriesField);
                Object value = ReflectionUtil.getFieldValue(row, valueField);
                points.add(new MultiSeriesPoint(
                        stringValue(x),
                        stringValue(series),
                        numberValue(value, valueField)
                ));
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to map row to MultiSeriesPoint", e);
            }
        }
        return points;
    }

    public static <T> ChartData toChartData(List<T> rows, ChartSpec spec) {
        return ChartMapper.toChartData(rows, spec);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static double numberValue(Object value, String fieldName) {
        if (value == null) {
            return 0d;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Field '" + fieldName + "' is not numeric");
        }
    }
}

