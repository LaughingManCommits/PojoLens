package laughing.man.commits.internal.builder;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.internal.NumericStatistics;
import laughing.man.commits.util.ReflectionUtil;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Immutable metric descriptor used by aggregation execution.
 */
public final class QueryMetric {

    private final String field;
    private final Metric metric;
    private final String alias;
    private final Double argument;

    private QueryMetric(String field, Metric metric, String alias, Double argument) {
        this.field = field;
        this.metric = metric;
        this.alias = alias;
        this.argument = argument;
    }

    public static QueryMetric of(String field, Metric metric, String alias) {
        return new QueryMetric(field, metric, alias, null);
    }

    /**
     * Metric with an argument, such as the fraction of {@link Metric#PERCENTILE}.
     */
    public static QueryMetric of(String field, Metric metric, Double argument, String alias) {
        return new QueryMetric(field, metric, alias, argument);
    }

    public static QueryMetric count(String alias) {
        return new QueryMetric(null, Metric.COUNT, alias, null);
    }

    /**
     * Result type of {@code metric} over a field of {@code fieldType}, matching the
     * aggregation engine: counts are {@code Long}; averages and statistical metrics
     * {@code Double}; {@code SUM} is {@code Double} over floating fields, {@code BigDecimal}
     * over {@code BigDecimal}/{@code BigInteger}, and {@code Long} over other integral
     * fields; {@code MIN}/{@code MAX} keep the (boxed) field type. An unknown field type
     * gives {@code Number}.
     */
    public static Class<?> outputType(Metric metric, Class<?> fieldType) {
        if (metric == Metric.COUNT || metric == Metric.COUNT_DISTINCT) {
            return Long.class;
        }
        if (metric == Metric.AVG || NumericStatistics.isStatistical(metric)) {
            return Double.class;
        }
        Class<?> boxed = ReflectionUtil.wrapPrimitive(fieldType);
        if (boxed == null) {
            return Number.class;
        }
        if (metric != Metric.SUM) {
            return boxed;
        }
        if (boxed == Double.class || boxed == Float.class) {
            return Double.class;
        }
        if (boxed == BigDecimal.class || boxed == BigInteger.class) {
            return BigDecimal.class;
        }
        if (boxed == Integer.class || boxed == Long.class || boxed == Short.class || boxed == Byte.class) {
            return Long.class;
        }
        return Number.class;
    }

    /**
     * The {@link Metric#PERCENTILE} fraction, or {@code null}.
     */
    public Double getArgument() {
        return argument;
    }

    public String getField() {
        return field;
    }

    public Metric getMetric() {
        return metric;
    }

    public String getAlias() {
        return alias;
    }
}

