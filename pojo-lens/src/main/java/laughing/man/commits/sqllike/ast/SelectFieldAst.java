package laughing.man.commits.sqllike.ast;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.time.TimeBucketPreset;

import java.util.Objects;

public final class SelectFieldAst {

    private final String field;
    private final String alias;
    private final Metric metric;
    private final boolean countAll;
    private final TimeBucketPreset timeBucketPreset;
    private final boolean computedExpression;

    public SelectFieldAst(String field,
                          String alias,
                          Metric metric,
                          boolean countAll,
                          TimeBucket timeBucket,
                          boolean computedExpression) {
        this(field, alias, metric, countAll, timeBucket == null ? null : TimeBucketPreset.of(timeBucket), computedExpression);
    }

    public SelectFieldAst(String field,
                          String alias,
                          Metric metric,
                          boolean countAll,
                          TimeBucketPreset timeBucketPreset,
                          boolean computedExpression) {
        this.field = Objects.requireNonNull(field, "field must not be null");
        this.alias = alias;
        this.metric = metric;
        this.countAll = countAll;
        this.timeBucketPreset = timeBucketPreset;
        this.computedExpression = computedExpression;
    }

    public String field() {
        return field;
    }

    public String alias() {
        return alias;
    }

    public Metric metric() {
        return metric;
    }

    public boolean metricField() {
        return metric != null;
    }

    public boolean countAll() {
        return countAll;
    }

    public TimeBucket timeBucket() {
        return timeBucketPreset == null ? null : timeBucketPreset.bucket();
    }

    public TimeBucketPreset timeBucketPreset() {
        return timeBucketPreset;
    }

    public boolean timeBucketField() {
        return timeBucketPreset != null;
    }

    public boolean computedField() {
        return computedExpression;
    }

    public String outputName() {
        if (alias != null) {
            return alias;
        }
        if (metric == null) {
            if (timeBucketPreset != null) {
                return "bucket_" + field + "_" + timeBucketPreset.bucket().name().toLowerCase();
            }
            return field;
        }
        if (countAll) {
            return "count_all";
        }
        return metric.name().toLowerCase() + "_" + field;
    }

    public boolean aliased() {
        return alias != null && !alias.isEmpty();
    }
}

