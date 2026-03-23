package laughing.man.commits.sqllike.ast;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.time.TimeBucketPreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SelectFieldAst {

    private final String field;
    private final String alias;
    private final Metric metric;
    private final boolean countAll;
    private final TimeBucketPreset timeBucketPreset;
    private final boolean computedExpression;
    private final String windowFunction;
    private final List<String> windowPartitionFields;
    private final List<OrderAst> windowOrderFields;

    public SelectFieldAst(String field,
                          String alias,
                          Metric metric,
                          boolean countAll,
                          TimeBucket timeBucket,
                          boolean computedExpression) {
        this(
                field,
                alias,
                metric,
                countAll,
                timeBucket == null ? null : TimeBucketPreset.of(timeBucket),
                computedExpression,
                null,
                List.of(),
                List.of()
        );
    }

    public SelectFieldAst(String field,
                          String alias,
                          Metric metric,
                          boolean countAll,
                          TimeBucketPreset timeBucketPreset,
                          boolean computedExpression) {
        this(field, alias, metric, countAll, timeBucketPreset, computedExpression, null, List.of(), List.of());
    }

    public SelectFieldAst(String field,
                          String alias,
                          Metric metric,
                          boolean countAll,
                          TimeBucketPreset timeBucketPreset,
                          boolean computedExpression,
                          String windowFunction,
                          List<String> windowPartitionFields,
                          List<OrderAst> windowOrderFields) {
        this.field = Objects.requireNonNull(field, "field must not be null");
        this.alias = alias;
        this.metric = metric;
        this.countAll = countAll;
        this.timeBucketPreset = timeBucketPreset;
        this.computedExpression = computedExpression;
        this.windowFunction = windowFunction;
        this.windowPartitionFields = Collections.unmodifiableList(new ArrayList<>(
                windowPartitionFields == null ? List.of() : windowPartitionFields
        ));
        this.windowOrderFields = Collections.unmodifiableList(new ArrayList<>(
                windowOrderFields == null ? List.of() : windowOrderFields
        ));
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

    public boolean windowField() {
        return windowFunction != null;
    }

    public String windowFunction() {
        return windowFunction;
    }

    public List<String> windowPartitionFields() {
        return windowPartitionFields;
    }

    public List<OrderAst> windowOrderFields() {
        return windowOrderFields;
    }

    public String outputName() {
        if (alias != null) {
            return alias;
        }
        if (metric == null) {
            if (windowFunction != null) {
                return windowFunction.toLowerCase(Locale.ROOT);
            }
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

