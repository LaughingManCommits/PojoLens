package laughing.man.commits.chart;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.time.TimeBucketPreset;

import java.util.Locale;
import java.util.Objects;

/**
 * Lightweight factories for common chart/report SQL-like query patterns.
 */
public final class ChartQueryPresets {

    private ChartQueryPresets() {
    }

    public static <T> ChartQueryPreset<T> categoryTotals(String categoryField,
                                                         Metric metric,
                                                         String metricField,
                                                         String valueAlias,
                                                         Class<T> projectionClass) {
        return categoryTotals(categoryField, metric, metricField, valueAlias, ChartType.BAR, projectionClass);
    }

    public static <T> ChartQueryPreset<T> categoryTotals(String categoryField,
                                                         Metric metric,
                                                         String metricField,
                                                         String valueAlias,
                                                         ChartType chartType,
                                                         Class<T> projectionClass) {
        String normalizedCategoryField = requireName(categoryField, "categoryField");
        String normalizedValueAlias = requireName(valueAlias, "valueAlias");
        Metric normalizedMetric = requireMetric(metric);
        String metricExpression = metricExpression(normalizedMetric, metricField);
        String sql = "select " + normalizedCategoryField + ", "
                + metricExpression + " as " + normalizedValueAlias
                + " group by " + normalizedCategoryField
                + " order by " + normalizedValueAlias + " desc";
        ChartSpec chartSpec = ChartSpec.of(requireChartType(chartType), normalizedCategoryField, normalizedValueAlias);
        return new ChartQueryPreset<>(SqlLikeQuery.of(sql), requireProjectionClass(projectionClass), chartSpec);
    }

    public static <T> ChartQueryPreset<T> categoryCounts(String categoryField,
                                                         String valueAlias,
                                                         Class<T> projectionClass) {
        return categoryTotals(categoryField, Metric.COUNT, null, valueAlias, ChartType.BAR, projectionClass);
    }

    public static <T> ChartQueryPreset<T> timeSeriesTotals(String dateField,
                                                           TimeBucket bucket,
                                                           Metric metric,
                                                           String metricField,
                                                           String periodAlias,
                                                           String valueAlias,
                                                           Class<T> projectionClass) {
        return timeSeriesTotals(dateField, TimeBucketPreset.of(bucket), metric, metricField, periodAlias, valueAlias, ChartType.LINE, projectionClass);
    }

    public static <T> ChartQueryPreset<T> timeSeriesTotals(String dateField,
                                                           TimeBucket bucket,
                                                           Metric metric,
                                                           String metricField,
                                                           String periodAlias,
                                                           String valueAlias,
                                                           ChartType chartType,
                                                           Class<T> projectionClass) {
        return timeSeriesTotals(dateField, TimeBucketPreset.of(bucket), metric, metricField, periodAlias, valueAlias, chartType, projectionClass);
    }

    public static <T> ChartQueryPreset<T> timeSeriesTotals(String dateField,
                                                           TimeBucketPreset preset,
                                                           Metric metric,
                                                           String metricField,
                                                           String periodAlias,
                                                           String valueAlias,
                                                           Class<T> projectionClass) {
        return timeSeriesTotals(dateField, preset, metric, metricField, periodAlias, valueAlias, ChartType.LINE, projectionClass);
    }

    public static <T> ChartQueryPreset<T> timeSeriesTotals(String dateField,
                                                           TimeBucketPreset preset,
                                                           Metric metric,
                                                           String metricField,
                                                           String periodAlias,
                                                           String valueAlias,
                                                           ChartType chartType,
                                                           Class<T> projectionClass) {
        String normalizedDateField = requireName(dateField, "dateField");
        TimeBucketPreset normalizedPreset = Objects.requireNonNull(preset, "preset must not be null");
        String normalizedPeriodAlias = requireName(periodAlias, "periodAlias");
        String normalizedValueAlias = requireName(valueAlias, "valueAlias");
        Metric normalizedMetric = requireMetric(metric);
        String metricExpression = metricExpression(normalizedMetric, metricField);
        String sql = "select bucket(" + normalizedDateField + "," + normalizedPreset.sqlArgumentList() + ") as "
                + normalizedPeriodAlias + ", "
                + metricExpression + " as " + normalizedValueAlias
                + " group by " + normalizedPeriodAlias
                + " order by " + normalizedPeriodAlias + " asc";
        ChartSpec chartSpec = ChartSpec.of(requireChartType(chartType), normalizedPeriodAlias, normalizedValueAlias)
                .withSortedLabels(true);
        return new ChartQueryPreset<>(SqlLikeQuery.of(sql), requireProjectionClass(projectionClass), chartSpec);
    }

    public static <T> ChartQueryPreset<T> timeSeriesCounts(String dateField,
                                                           TimeBucket bucket,
                                                           String periodAlias,
                                                           String valueAlias,
                                                           Class<T> projectionClass) {
        return timeSeriesTotals(dateField, TimeBucketPreset.of(bucket), Metric.COUNT, null, periodAlias, valueAlias, ChartType.LINE, projectionClass);
    }

    public static <T> ChartQueryPreset<T> timeSeriesCounts(String dateField,
                                                           TimeBucketPreset preset,
                                                           String periodAlias,
                                                           String valueAlias,
                                                           Class<T> projectionClass) {
        return timeSeriesTotals(dateField, preset, Metric.COUNT, null, periodAlias, valueAlias, ChartType.LINE, projectionClass);
    }

    public static <T> ChartQueryPreset<T> groupedBreakdown(String categoryField,
                                                           String seriesField,
                                                           Metric metric,
                                                           String metricField,
                                                           String valueAlias,
                                                           Class<T> projectionClass) {
        return groupedBreakdown(categoryField, seriesField, metric, metricField, valueAlias, ChartType.BAR, projectionClass);
    }

    public static <T> ChartQueryPreset<T> groupedBreakdown(String categoryField,
                                                           String seriesField,
                                                           Metric metric,
                                                           String metricField,
                                                           String valueAlias,
                                                           ChartType chartType,
                                                           Class<T> projectionClass) {
        String normalizedCategoryField = requireName(categoryField, "categoryField");
        String normalizedSeriesField = requireName(seriesField, "seriesField");
        String normalizedValueAlias = requireName(valueAlias, "valueAlias");
        Metric normalizedMetric = requireMetric(metric);
        String metricExpression = metricExpression(normalizedMetric, metricField);
        String sql = "select " + normalizedSeriesField + ", "
                + normalizedCategoryField + ", "
                + metricExpression + " as " + normalizedValueAlias
                + " group by " + normalizedSeriesField + ", " + normalizedCategoryField
                + " order by " + normalizedCategoryField + " asc";
        ChartSpec chartSpec = ChartSpec.of(requireChartType(chartType), normalizedCategoryField, normalizedValueAlias, normalizedSeriesField)
                .withSortedLabels(true);
        return new ChartQueryPreset<>(SqlLikeQuery.of(sql), requireProjectionClass(projectionClass), chartSpec);
    }

    private static String requireName(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be null/blank");
        }
        return value;
    }

    private static Metric requireMetric(Metric metric) {
        return Objects.requireNonNull(metric, "metric must not be null");
    }

    private static ChartType requireChartType(ChartType chartType) {
        return Objects.requireNonNull(chartType, "chartType must not be null");
    }

    private static <T> Class<T> requireProjectionClass(Class<T> projectionClass) {
        return Objects.requireNonNull(projectionClass, "projectionClass must not be null");
    }

    private static String metricExpression(Metric metric, String metricField) {
        if (metric == Metric.COUNT) {
            return "count(*)";
        }
        return metric.name().toLowerCase(Locale.ROOT) + "(" + requireName(metricField, "metricField") + ")";
    }
}

