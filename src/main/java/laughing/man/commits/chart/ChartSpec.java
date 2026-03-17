package laughing.man.commits.chart;

import laughing.man.commits.builder.FieldSelector;
import laughing.man.commits.builder.FieldSelectors;
import laughing.man.commits.util.StringUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declarative mapping contract for converting query rows into chart payloads.
 */
public final class ChartSpec {

    private final ChartType type;
    private final String xField;
    private final String yField;
    private final String seriesField;
    private final String title;
    private final String xLabel;
    private final String yLabel;
    private final String dateFormat;
    private final boolean sortLabels;
    private final boolean stacked;
    private final boolean percentStacked;
    private final NullPointPolicy nullPointPolicy;
    private final Map<String, String> colorHintByDataset;
    private final Map<String, String> stackGroupIdByDataset;
    private final Map<String, String> axisIdByDataset;

    public ChartSpec(ChartType type,
                     String xField,
                     String yField,
                     String seriesField,
                     String title,
                     String xLabel,
                     String yLabel,
                     String dateFormat) {
        this(type, xField, yField, seriesField, title, xLabel, yLabel, dateFormat,
                false, false, false, NullPointPolicy.PRESERVE,
                null, null, null);
    }

    public ChartSpec(ChartType type,
                     String xField,
                     String yField,
                     String seriesField,
                     String title,
                     String xLabel,
                     String yLabel,
                     String dateFormat,
                     boolean sortLabels) {
        this(type, xField, yField, seriesField, title, xLabel, yLabel, dateFormat,
                sortLabels, false, false, NullPointPolicy.PRESERVE,
                null, null, null);
    }

    public ChartSpec(ChartType type,
                     String xField,
                     String yField,
                     String seriesField,
                     String title,
                     String xLabel,
                     String yLabel,
                     String dateFormat,
                     boolean sortLabels,
                     boolean stacked,
                     boolean percentStacked,
                     NullPointPolicy nullPointPolicy) {
        this(type, xField, yField, seriesField, title, xLabel, yLabel, dateFormat,
                sortLabels, stacked, percentStacked, nullPointPolicy,
                null, null, null);
    }

    private ChartSpec(ChartType type,
                      String xField,
                      String yField,
                      String seriesField,
                      String title,
                      String xLabel,
                      String yLabel,
                      String dateFormat,
                      boolean sortLabels,
                      boolean stacked,
                      boolean percentStacked,
                      NullPointPolicy nullPointPolicy,
                      Map<String, String> colorHintByDataset,
                      Map<String, String> stackGroupIdByDataset,
                      Map<String, String> axisIdByDataset) {
        this.type = type;
        this.xField = xField;
        this.yField = yField;
        this.seriesField = seriesField;
        this.title = title;
        this.xLabel = xLabel;
        this.yLabel = yLabel;
        this.dateFormat = dateFormat;
        this.sortLabels = sortLabels;
        this.stacked = stacked;
        this.percentStacked = percentStacked;
        this.nullPointPolicy = nullPointPolicy == null ? NullPointPolicy.PRESERVE : nullPointPolicy;
        this.colorHintByDataset = copyMap(colorHintByDataset);
        this.stackGroupIdByDataset = copyMap(stackGroupIdByDataset);
        this.axisIdByDataset = copyMap(axisIdByDataset);
    }

    public static ChartSpec of(ChartType type, String xField, String yField) {
        return new ChartSpec(type, xField, yField, null, null, null, null, null);
    }

    public static ChartSpec of(ChartType type, String xField, String yField, String seriesField) {
        return new ChartSpec(type, xField, yField, seriesField, null, null, null, null);
    }

    public static <T, X, Y> ChartSpec of(ChartType type,
                                         FieldSelector<T, X> xField,
                                         FieldSelector<T, Y> yField) {
        return of(type, FieldSelectors.resolve(xField), FieldSelectors.resolve(yField));
    }

    public static <T, X, Y, S> ChartSpec of(ChartType type,
                                            FieldSelector<T, X> xField,
                                            FieldSelector<T, Y> yField,
                                            FieldSelector<T, S> seriesField) {
        return of(type,
                FieldSelectors.resolve(xField),
                FieldSelectors.resolve(yField),
                FieldSelectors.resolve(seriesField));
    }

    public ChartSpec withTitle(String value) {
        return copyWith(value, xLabel, yLabel, dateFormat, sortLabels, stacked, percentStacked, nullPointPolicy,
                colorHintByDataset, stackGroupIdByDataset, axisIdByDataset);
    }

    public ChartSpec withAxisLabels(String xAxisLabel, String yAxisLabel) {
        return copyWith(title, xAxisLabel, yAxisLabel, dateFormat, sortLabels, stacked, percentStacked, nullPointPolicy,
                colorHintByDataset, stackGroupIdByDataset, axisIdByDataset);
    }

    public ChartSpec withDateFormat(String value) {
        return copyWith(title, xLabel, yLabel, value, sortLabels, stacked, percentStacked, nullPointPolicy,
                colorHintByDataset, stackGroupIdByDataset, axisIdByDataset);
    }

    public ChartSpec withSortedLabels(boolean value) {
        return copyWith(title, xLabel, yLabel, dateFormat, value, stacked, percentStacked, nullPointPolicy,
                colorHintByDataset, stackGroupIdByDataset, axisIdByDataset);
    }

    public ChartSpec withStacked(boolean value) {
        return copyWith(title, xLabel, yLabel, dateFormat, sortLabels, value, percentStacked, nullPointPolicy,
                colorHintByDataset, stackGroupIdByDataset, axisIdByDataset);
    }

    public ChartSpec withPercentStacked(boolean value) {
        return copyWith(title, xLabel, yLabel, dateFormat, sortLabels, stacked, value, nullPointPolicy,
                colorHintByDataset, stackGroupIdByDataset, axisIdByDataset);
    }

    public ChartSpec withNullPointPolicy(NullPointPolicy value) {
        return copyWith(title, xLabel, yLabel, dateFormat, sortLabels, stacked, percentStacked, value,
                colorHintByDataset, stackGroupIdByDataset, axisIdByDataset);
    }

    public ChartSpec withDatasetColorHint(String datasetLabel, String colorHint) {
        Map<String, String> updated = copyMap(colorHintByDataset);
        putOrRemove(updated, datasetLabel, colorHint);
        return copyWith(title, xLabel, yLabel, dateFormat, sortLabels, stacked, percentStacked, nullPointPolicy,
                updated, stackGroupIdByDataset, axisIdByDataset);
    }

    public ChartSpec withDatasetStackGroupId(String datasetLabel, String stackGroupId) {
        Map<String, String> updated = copyMap(stackGroupIdByDataset);
        putOrRemove(updated, datasetLabel, stackGroupId);
        return copyWith(title, xLabel, yLabel, dateFormat, sortLabels, stacked, percentStacked, nullPointPolicy,
                colorHintByDataset, updated, axisIdByDataset);
    }

    public ChartSpec withDatasetAxisId(String datasetLabel, String axisId) {
        Map<String, String> updated = copyMap(axisIdByDataset);
        putOrRemove(updated, datasetLabel, axisId);
        return copyWith(title, xLabel, yLabel, dateFormat, sortLabels, stacked, percentStacked, nullPointPolicy,
                colorHintByDataset, stackGroupIdByDataset, updated);
    }

    public ChartType type() {
        return type;
    }

    public String xField() {
        return xField;
    }

    public String yField() {
        return yField;
    }

    public String seriesField() {
        return seriesField;
    }

    public String title() {
        return title;
    }

    public String xLabel() {
        return xLabel;
    }

    public String yLabel() {
        return yLabel;
    }

    public String dateFormat() {
        return dateFormat;
    }

    public boolean sortLabels() {
        return sortLabels;
    }

    public boolean stacked() {
        return stacked;
    }

    public boolean percentStacked() {
        return percentStacked;
    }

    public NullPointPolicy nullPointPolicy() {
        return nullPointPolicy;
    }

    public String colorHintForDataset(String datasetLabel) {
        return colorHintByDataset.get(datasetLabel);
    }

    public String stackGroupIdForDataset(String datasetLabel) {
        return stackGroupIdByDataset.get(datasetLabel);
    }

    public String axisIdForDataset(String datasetLabel) {
        return axisIdByDataset.get(datasetLabel);
    }

    public boolean multiSeries() {
        return seriesField != null && !seriesField.isBlank();
    }

    private ChartSpec copyWith(String title,
                               String xLabel,
                               String yLabel,
                               String dateFormat,
                               boolean sortLabels,
                               boolean stacked,
                               boolean percentStacked,
                               NullPointPolicy nullPointPolicy,
                               Map<String, String> colorHintByDataset,
                               Map<String, String> stackGroupIdByDataset,
                               Map<String, String> axisIdByDataset) {
        return new ChartSpec(type, xField, yField, seriesField, title, xLabel, yLabel, dateFormat,
                sortLabels, stacked, percentStacked, nullPointPolicy,
                colorHintByDataset, stackGroupIdByDataset, axisIdByDataset);
    }

    private static Map<String, String> copyMap(Map<String, String> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private static void putOrRemove(Map<String, String> map, String key, String value) {
        if (StringUtil.isNullOrBlank(key)) {
            throw new IllegalArgumentException("datasetLabel is required");
        }
        if (StringUtil.isNullOrBlank(value)) {
            map.remove(key);
            return;
        }
        map.put(key, value);
    }
}

