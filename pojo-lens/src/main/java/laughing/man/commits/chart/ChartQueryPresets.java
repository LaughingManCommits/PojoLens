package laughing.man.commits.chart;

import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.util.StringUtil;

import java.util.Objects;

/**
 * Advanced convenience factories for common chart-first SQL-like query
 * patterns.
 *
 * <p>Prefer {@link laughing.man.commits.report.ReportDefinition} when the
 * reusable contract should stay row-first or serve multiple consumers.
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

    public static ChartQueryPreset<QueryRow> categoryTotals(String categoryField,
                                                            Metric metric,
                                                            String metricField,
                                                            String valueAlias) {
        return categoryTotals(categoryField, metric, metricField, valueAlias, ChartType.BAR, QueryRow.class);
    }

    public static <T> ChartQueryPreset<T> categoryTotals(String categoryField,
                                                         Metric metric,
                                                         String metricField,
                                                         String valueAlias,
                                                         ChartType chartType,
                                                         Class<T> projectionClass) {
        String normalizedCategoryField = StringUtil.requireNonBlank(categoryField, "categoryField");
        String normalizedValueAlias = StringUtil.requireNonBlank(valueAlias, "valueAlias");
        Metric normalizedMetric = requireMetric(metric);
        String metricExpression = normalizedMetric.expressionFor(metricField);
        String sql = "select " + normalizedCategoryField + ", "
                + metricExpression + " as " + normalizedValueAlias
                + " group by " + normalizedCategoryField
                + " order by " + normalizedValueAlias + " desc";
        ChartSpec chartSpec = ChartSpec.of(requireChartType(chartType), normalizedCategoryField, normalizedValueAlias);
        return new ChartQueryPreset<>(SqlLikeQuery.of(sql), requireProjectionClass(projectionClass), chartSpec);
    }

    public static ChartQueryPreset<QueryRow> categoryTotals(String categoryField,
                                                            Metric metric,
                                                            String metricField,
                                                            String valueAlias,
                                                            ChartType chartType) {
        return categoryTotals(categoryField, metric, metricField, valueAlias, chartType, QueryRow.class);
    }

    public static <T> ChartQueryPreset<T> categoryCounts(String categoryField,
                                                         String valueAlias,
                                                         Class<T> projectionClass) {
        return categoryTotals(categoryField, Metric.COUNT, null, valueAlias, ChartType.BAR, projectionClass);
    }

    public static ChartQueryPreset<QueryRow> categoryCounts(String categoryField,
                                                            String valueAlias) {
        return categoryTotals(categoryField, Metric.COUNT, null, valueAlias, ChartType.BAR, QueryRow.class);
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

    public static ChartQueryPreset<QueryRow> timeSeriesTotals(String dateField,
                                                              TimeBucket bucket,
                                                              Metric metric,
                                                              String metricField,
                                                              String periodAlias,
                                                              String valueAlias) {
        return timeSeriesTotals(dateField, TimeBucketPreset.of(bucket), metric, metricField, periodAlias, valueAlias, ChartType.LINE, QueryRow.class);
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

    public static ChartQueryPreset<QueryRow> timeSeriesTotals(String dateField,
                                                              TimeBucket bucket,
                                                              Metric metric,
                                                              String metricField,
                                                              String periodAlias,
                                                              String valueAlias,
                                                              ChartType chartType) {
        return timeSeriesTotals(dateField, TimeBucketPreset.of(bucket), metric, metricField, periodAlias, valueAlias, chartType, QueryRow.class);
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

    public static ChartQueryPreset<QueryRow> timeSeriesTotals(String dateField,
                                                              TimeBucketPreset preset,
                                                              Metric metric,
                                                              String metricField,
                                                              String periodAlias,
                                                              String valueAlias) {
        return timeSeriesTotals(dateField, preset, metric, metricField, periodAlias, valueAlias, ChartType.LINE, QueryRow.class);
    }

    public static <T> ChartQueryPreset<T> timeSeriesTotals(String dateField,
                                                           TimeBucketPreset preset,
                                                           Metric metric,
                                                           String metricField,
                                                           String periodAlias,
                                                           String valueAlias,
                                                           ChartType chartType,
                                                           Class<T> projectionClass) {
        String normalizedDateField = StringUtil.requireNonBlank(dateField, "dateField");
        TimeBucketPreset normalizedPreset = Objects.requireNonNull(preset, "preset must not be null");
        String normalizedPeriodAlias = StringUtil.requireNonBlank(periodAlias, "periodAlias");
        String normalizedValueAlias = StringUtil.requireNonBlank(valueAlias, "valueAlias");
        Metric normalizedMetric = requireMetric(metric);
        String metricExpression = normalizedMetric.expressionFor(metricField);
        String sql = "select bucket(" + normalizedDateField + "," + normalizedPreset.sqlArgumentList() + ") as "
                + normalizedPeriodAlias + ", "
                + metricExpression + " as " + normalizedValueAlias
                + " group by " + normalizedPeriodAlias
                + " order by " + normalizedPeriodAlias + " asc";
        ChartSpec chartSpec = ChartSpec.of(requireChartType(chartType), normalizedPeriodAlias, normalizedValueAlias)
                .withSortedLabels(true);
        return new ChartQueryPreset<>(SqlLikeQuery.of(sql), requireProjectionClass(projectionClass), chartSpec);
    }

    public static ChartQueryPreset<QueryRow> timeSeriesTotals(String dateField,
                                                              TimeBucketPreset preset,
                                                              Metric metric,
                                                              String metricField,
                                                              String periodAlias,
                                                              String valueAlias,
                                                              ChartType chartType) {
        return timeSeriesTotals(dateField, preset, metric, metricField, periodAlias, valueAlias, chartType, QueryRow.class);
    }

    public static <T> ChartQueryPreset<T> timeSeriesCounts(String dateField,
                                                           TimeBucket bucket,
                                                           String periodAlias,
                                                           String valueAlias,
                                                           Class<T> projectionClass) {
        return timeSeriesTotals(dateField, TimeBucketPreset.of(bucket), Metric.COUNT, null, periodAlias, valueAlias, ChartType.LINE, projectionClass);
    }

    public static ChartQueryPreset<QueryRow> timeSeriesCounts(String dateField,
                                                              TimeBucket bucket,
                                                              String periodAlias,
                                                              String valueAlias) {
        return timeSeriesTotals(dateField, TimeBucketPreset.of(bucket), Metric.COUNT, null, periodAlias, valueAlias, ChartType.LINE, QueryRow.class);
    }

    public static <T> ChartQueryPreset<T> timeSeriesCounts(String dateField,
                                                           TimeBucketPreset preset,
                                                           String periodAlias,
                                                           String valueAlias,
                                                           Class<T> projectionClass) {
        return timeSeriesTotals(dateField, preset, Metric.COUNT, null, periodAlias, valueAlias, ChartType.LINE, projectionClass);
    }

    public static ChartQueryPreset<QueryRow> timeSeriesCounts(String dateField,
                                                              TimeBucketPreset preset,
                                                              String periodAlias,
                                                              String valueAlias) {
        return timeSeriesTotals(dateField, preset, Metric.COUNT, null, periodAlias, valueAlias, ChartType.LINE, QueryRow.class);
    }

    public static <T> ChartQueryPreset<T> groupedBreakdown(String categoryField,
                                                           String seriesField,
                                                           Metric metric,
                                                           String metricField,
                                                           String valueAlias,
                                                           Class<T> projectionClass) {
        return groupedBreakdown(categoryField, seriesField, metric, metricField, valueAlias, ChartType.BAR, projectionClass);
    }

    public static ChartQueryPreset<QueryRow> groupedBreakdown(String categoryField,
                                                              String seriesField,
                                                              Metric metric,
                                                              String metricField,
                                                              String valueAlias) {
        return groupedBreakdown(categoryField, seriesField, metric, metricField, valueAlias, ChartType.BAR, QueryRow.class);
    }

    public static <T> ChartQueryPreset<T> groupedBreakdown(String categoryField,
                                                           String seriesField,
                                                           Metric metric,
                                                           String metricField,
                                                           String valueAlias,
                                                           ChartType chartType,
                                                           Class<T> projectionClass) {
        String normalizedCategoryField = StringUtil.requireNonBlank(categoryField, "categoryField");
        String normalizedSeriesField = StringUtil.requireNonBlank(seriesField, "seriesField");
        String normalizedValueAlias = StringUtil.requireNonBlank(valueAlias, "valueAlias");
        Metric normalizedMetric = requireMetric(metric);
        String metricExpression = normalizedMetric.expressionFor(metricField);
        String sql = "select " + normalizedSeriesField + ", "
                + normalizedCategoryField + ", "
                + metricExpression + " as " + normalizedValueAlias
                + " group by " + normalizedSeriesField + ", " + normalizedCategoryField
                + " order by " + normalizedCategoryField + " asc";
        ChartSpec chartSpec = ChartSpec.of(requireChartType(chartType), normalizedCategoryField, normalizedValueAlias, normalizedSeriesField)
                .withSortedLabels(true);
        return new ChartQueryPreset<>(SqlLikeQuery.of(sql), requireProjectionClass(projectionClass), chartSpec);
    }

    public static ChartQueryPreset<QueryRow> groupedBreakdown(String categoryField,
                                                              String seriesField,
                                                              Metric metric,
                                                              String metricField,
                                                              String valueAlias,
                                                              ChartType chartType) {
        return groupedBreakdown(categoryField, seriesField, metric, metricField, valueAlias, chartType, QueryRow.class);
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

}

