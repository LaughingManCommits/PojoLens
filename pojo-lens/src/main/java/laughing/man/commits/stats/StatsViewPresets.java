package laughing.man.commits.stats;

import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.util.StringUtil;

import java.util.Locale;
import java.util.Objects;

/**
 * Lightweight factories for common table-stats query patterns.
 */
public final class StatsViewPresets {

    private static final String DEFAULT_VALUE_ALIAS = "total";

    private StatsViewPresets() {
    }

    public static <T> StatsViewPreset<T> summary(Class<T> projectionClass) {
        return summary(Metric.COUNT, null, DEFAULT_VALUE_ALIAS, projectionClass);
    }

    public static StatsViewPreset<QueryRow> summary() {
        return summary(Metric.COUNT, null, DEFAULT_VALUE_ALIAS, QueryRow.class);
    }

    public static <T> StatsViewPreset<T> summary(String valueAlias, Class<T> projectionClass) {
        return summary(Metric.COUNT, null, valueAlias, projectionClass);
    }

    public static StatsViewPreset<QueryRow> summary(String valueAlias) {
        return summary(Metric.COUNT, null, valueAlias, QueryRow.class);
    }

    public static <T> StatsViewPreset<T> summary(Metric metric,
                                                 String metricField,
                                                 Class<T> projectionClass) {
        return summary(metric, metricField, DEFAULT_VALUE_ALIAS, projectionClass);
    }

    public static StatsViewPreset<QueryRow> summary(Metric metric,
                                                    String metricField) {
        return summary(metric, metricField, DEFAULT_VALUE_ALIAS, QueryRow.class);
    }

    public static <T> StatsViewPreset<T> summary(Metric metric,
                                                 String metricField,
                                                 String valueAlias,
                                                 Class<T> projectionClass) {
        String normalizedAlias = requireName(valueAlias, "valueAlias");
        String metricExpression = metricExpression(requireMetric(metric), metricField);
        String sql = "select " + metricExpression + " as " + normalizedAlias;
        return new StatsViewPreset<>(SqlLikeQuery.of(sql), null, requireProjectionClass(projectionClass));
    }

    public static StatsViewPreset<QueryRow> summary(Metric metric,
                                                    String metricField,
                                                    String valueAlias) {
        return summary(metric, metricField, valueAlias, QueryRow.class);
    }

    public static <T> StatsViewPreset<T> by(String groupField, Class<T> projectionClass) {
        return by(groupField, Metric.COUNT, null, DEFAULT_VALUE_ALIAS, projectionClass);
    }

    public static StatsViewPreset<QueryRow> by(String groupField) {
        return by(groupField, Metric.COUNT, null, DEFAULT_VALUE_ALIAS, QueryRow.class);
    }

    public static <T> StatsViewPreset<T> by(String groupField,
                                            String valueAlias,
                                            Class<T> projectionClass) {
        return by(groupField, Metric.COUNT, null, valueAlias, projectionClass);
    }

    public static StatsViewPreset<QueryRow> by(String groupField,
                                               String valueAlias) {
        return by(groupField, Metric.COUNT, null, valueAlias, QueryRow.class);
    }

    public static <T> StatsViewPreset<T> by(String groupField,
                                            Metric metric,
                                            String metricField,
                                            Class<T> projectionClass) {
        return by(groupField, metric, metricField, DEFAULT_VALUE_ALIAS, projectionClass);
    }

    public static StatsViewPreset<QueryRow> by(String groupField,
                                               Metric metric,
                                               String metricField) {
        return by(groupField, metric, metricField, DEFAULT_VALUE_ALIAS, QueryRow.class);
    }

    public static <T> StatsViewPreset<T> by(String groupField,
                                            Metric metric,
                                            String metricField,
                                            String valueAlias,
                                            Class<T> projectionClass) {
        String normalizedGroupField = requireName(groupField, "groupField");
        String normalizedAlias = requireName(valueAlias, "valueAlias");
        String metricExpression = metricExpression(requireMetric(metric), metricField);
        String rowSql = "select " + normalizedGroupField + ", "
                + metricExpression + " as " + normalizedAlias
                + " group by " + normalizedGroupField
                + " order by " + normalizedAlias + " desc, " + normalizedGroupField + " desc";
        String totalsSql = "select " + metricExpression + " as " + normalizedAlias;
        return new StatsViewPreset<>(
                SqlLikeQuery.of(rowSql),
                SqlLikeQuery.of(totalsSql),
                requireProjectionClass(projectionClass)
        );
    }

    public static StatsViewPreset<QueryRow> by(String groupField,
                                               Metric metric,
                                               String metricField,
                                               String valueAlias) {
        return by(groupField, metric, metricField, valueAlias, QueryRow.class);
    }

    public static <T> StatsViewPreset<T> topNBy(String groupField,
                                                Metric metric,
                                                int n,
                                                Class<T> projectionClass) {
        return topNBy(groupField, metric, null, DEFAULT_VALUE_ALIAS, n, projectionClass);
    }

    public static StatsViewPreset<QueryRow> topNBy(String groupField,
                                                   Metric metric,
                                                   int n) {
        return topNBy(groupField, metric, null, DEFAULT_VALUE_ALIAS, n, QueryRow.class);
    }

    public static <T> StatsViewPreset<T> topNBy(String groupField,
                                                Metric metric,
                                                String metricField,
                                                int n,
                                                Class<T> projectionClass) {
        return topNBy(groupField, metric, metricField, DEFAULT_VALUE_ALIAS, n, projectionClass);
    }

    public static StatsViewPreset<QueryRow> topNBy(String groupField,
                                                   Metric metric,
                                                   String metricField,
                                                   int n) {
        return topNBy(groupField, metric, metricField, DEFAULT_VALUE_ALIAS, n, QueryRow.class);
    }

    public static <T> StatsViewPreset<T> topNBy(String groupField,
                                                Metric metric,
                                                String metricField,
                                                String valueAlias,
                                                int n,
                                                Class<T> projectionClass) {
        String normalizedGroupField = requireName(groupField, "groupField");
        String normalizedAlias = requireName(valueAlias, "valueAlias");
        int normalizedN = requirePositiveTopN(n);
        String metricExpression = metricExpression(requireMetric(metric), metricField);
        String rowSql = "select " + normalizedGroupField + ", "
                + metricExpression + " as " + normalizedAlias
                + " group by " + normalizedGroupField
                + " order by " + normalizedAlias + " desc, " + normalizedGroupField + " desc"
                + " limit " + normalizedN;
        String totalsSql = "select " + metricExpression + " as " + normalizedAlias;
        return new StatsViewPreset<>(
                SqlLikeQuery.of(rowSql),
                SqlLikeQuery.of(totalsSql),
                requireProjectionClass(projectionClass)
        );
    }

    public static StatsViewPreset<QueryRow> topNBy(String groupField,
                                                   Metric metric,
                                                   String metricField,
                                                   String valueAlias,
                                                   int n) {
        return topNBy(groupField, metric, metricField, valueAlias, n, QueryRow.class);
    }

    private static int requirePositiveTopN(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("n must be > 0");
        }
        return value;
    }

    private static String requireName(String value, String label) {
        if (StringUtil.isNullOrBlank(value)) {
            throw new IllegalArgumentException(label + " must not be null/blank");
        }
        return value;
    }

    private static Metric requireMetric(Metric metric) {
        return Objects.requireNonNull(metric, "metric must not be null");
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
