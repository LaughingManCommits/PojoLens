package laughing.man.commits.dsl;

import laughing.man.commits.enums.Metric;

import java.util.Objects;

/**
 * Structural description of a configured typed aggregate metric.
 */
public final class TypedPlanMetric {

    private final String field;
    private final Metric metric;
    private final String alias;
    private final boolean count;

    public TypedPlanMetric(String field, Metric metric, String alias, boolean count) {
        this.field = field;
        this.metric = Objects.requireNonNull(metric, "metric must not be null");
        this.alias = Objects.requireNonNull(alias, "alias must not be null");
        this.count = count;
    }

    public String field() {
        return field;
    }

    public Metric metric() {
        return metric;
    }

    public String alias() {
        return alias;
    }

    public boolean count() {
        return count;
    }
}
