package laughing.man.commits.chart;

import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.ReflectionUtil;

import java.util.Date;

/**
 * Validation helpers for chart specification and value constraints.
 */
final class ChartValidation {

    private ChartValidation() {
    }

    static void validateSpec(final ChartSpec spec) {
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
            throw new IllegalArgumentException(
                    "Chart seriesField must not be blank when provided");
        }
        if (ChartType.PIE.equals(spec.type()) && spec.multiSeries()) {
            throw new IllegalArgumentException(
                    "Chart type PIE does not support seriesField");
        }
        if (spec.percentStacked() && !spec.stacked()) {
            throw new IllegalArgumentException("percentStacked requires stacked=true");
        }
        if ((spec.stacked() || spec.percentStacked()) && !spec.multiSeries()) {
            throw new IllegalArgumentException("stacked charts require seriesField");
        }
        if ((spec.stacked() || spec.percentStacked())
                && !(ChartType.BAR.equals(spec.type()) || ChartType.AREA.equals(spec.type()))) {
            throw new IllegalArgumentException(
                    "stacked/percentStacked is supported only for BAR and AREA charts");
        }
    }

    static void requireFieldExists(final Class<?> type, final String fieldName) {
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

    static String validateXValue(final Object value,
                                 final String fieldName,
                                 final String dateFormat) {
        return switch (value) {
            case null -> null;
            case String stringValue -> stringValue;
            case Number number -> String.valueOf(number);
            case Date date -> ObjectUtil.castToString(date, dateFormat);
            default -> throw new IllegalArgumentException(
                    "Chart xField '" + fieldName + "' has unsupported type '"
                            + value.getClass().getSimpleName() + "'");
        };
    }

    static double validateYValue(final Object value, final String fieldName) {
        return switch (value) {
            case null -> throw new IllegalArgumentException(
                    "Chart yField '" + fieldName + "' must not be null");
            case Number number -> number.doubleValue();
            default -> throw new IllegalArgumentException(
                    "Chart yField '" + fieldName + "' must be numeric");
        };
    }

    static Double validateYValueBoxed(final Object value,
                                      final String fieldName) {
        return switch (value) {
            case null -> throw new IllegalArgumentException(
                    "Chart yField '" + fieldName + "' must not be null");
            case Double doubleValue -> doubleValue;
            case Number number -> number.doubleValue();
            default -> throw new IllegalArgumentException(
                    "Chart yField '" + fieldName + "' must be numeric");
        };
    }
}
