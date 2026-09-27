package laughing.man.commits.internal.builder;

import laughing.man.commits.enums.Metric;

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

