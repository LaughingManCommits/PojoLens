package laughing.man.commits.builder;

import laughing.man.commits.enums.Metric;

/**
 * Immutable metric descriptor used by aggregation execution.
 */
public final class QueryMetric {

    private final String field;
    private final Metric metric;
    private final String alias;

    private QueryMetric(String field, Metric metric, String alias) {
        this.field = field;
        this.metric = metric;
        this.alias = alias;
    }

    public static QueryMetric of(String field, Metric metric, String alias) {
        return new QueryMetric(field, metric, alias);
    }

    public static QueryMetric count(String alias) {
        return new QueryMetric(null, Metric.COUNT, alias);
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

