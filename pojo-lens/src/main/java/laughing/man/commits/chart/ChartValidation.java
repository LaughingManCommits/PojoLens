package laughing.man.commits.chart;

import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.ReflectionUtil;

/**
 * Validation helpers for chart specification and value constraints.
 */
final class ChartValidation {

    private ChartValidation() {
    }

    static void validateSpec(ChartSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Chart spec is required");
        }
        if (spec.type() == null) {
            throw new IllegalArgumentException("Chart type is required");
        }
        if (spec.xField() == null || spec.xField().isBlank()) {
            throw new IllegalArgumentException("xField is required");
        }
        if (spec.yField() == null || spec.yField().isBlank()) {
            throw new IllegalArgumentException("yField is required");
        }
        if (spec.seriesField() != null && spec.seriesField().isBlank()) {
            throw new IllegalArgumentException("Chart seriesField must not be blank when provided");
        }
        if (ChartType.PIE.equals(spec.type()) && spec.multiSeries()) {
            throw new IllegalArgumentException("Chart type PIE does not support seriesField");
        }
        if (spec.percentStacked() && !spec.stacked()) {
            throw new IllegalArgumentException("percentStacked requires stacked=true");
        }
        if ((spec.stacked() || spec.percentStacked()) && !spec.multiSeries()) {
            throw new IllegalArgumentException("stacked charts require seriesField");
        }
        if ((spec.stacked() || spec.percentStacked())
                && !(ChartType.BAR.equals(spec.type()) || ChartType.AREA.equals(spec.type()))) {
            throw new IllegalArgumentException("stacked/percentStacked is supported only for BAR and AREA charts");
        }
    }

    static void requireFieldExists(Class<?> type, String fieldName) {
        for (java.lang.reflect.Field field : ReflectionUtil.getFields(type)) {
            if (fieldName.equals(field.getName())) {
                return;
            }
        }
        for (String queryableField : ReflectionUtil.collectQueryableFieldNames(type)) {
            if (fieldName.equals(queryableField)) {
                return;
            }
        }
        throw new IllegalArgumentException("Unknown chart field '" + fieldName + "'");
    }

    static String validateXValue(Object value, String fieldName, String dateFormat) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof java.util.Date) {
            return ObjectUtil.castToString(value, dateFormat);
        }
        throw new IllegalArgumentException(
                "Chart xField '" + fieldName + "' has unsupported type '" + value.getClass().getSimpleName() + "'");
    }

    static double validateYValue(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Chart yField '" + fieldName + "' must not be null");
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Chart yField '" + fieldName + "' must be numeric");
        }
        return ((Number) value).doubleValue();
    }
}
