package laughing.man.commits.dsl;

import laughing.man.commits.sqllike.PlanPreviewJoin;
import laughing.man.commits.sqllike.PlanPreviewOrder;
import laughing.man.commits.sqllike.PlanPreviewPaging;
import laughing.man.commits.sqllike.QueryExecutionGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Structural preview of a typed query's execution shape, produced without
 * executing the query against source rows.
 */
public final class TypedPlanPreview {

    private final String source;
    private final Class<?> entityClass;
    private final List<String> selectFields;
    private final TypedPlanPredicate filterExpression;
    private final List<String> groupByFields;
    private final List<TypedPlanMetric> metrics;
    private final TypedPlanPredicate havingExpression;
    private final List<TypedPlanWindow> windows;
    private final TypedPlanPredicate qualifyExpression;
    private final List<PlanPreviewOrder> orderFields;
    private final List<PlanPreviewJoin> joins;
    private final PlanPreviewPaging paging;
    private final List<TypedPlanTimeBucket> timeBuckets;
    private final List<String> computedFields;
    private final List<String> referencedFields;
    private final List<String> outputFields;
    private final List<String> joinSources;
    private final QueryExecutionGuard executionGuard;
    private final boolean hasSubqueries;

    public TypedPlanPreview(String source,
                            Class<?> entityClass,
                            List<String> selectFields,
                            TypedPlanPredicate filterExpression,
                            List<String> groupByFields,
                            List<TypedPlanMetric> metrics,
                            TypedPlanPredicate havingExpression,
                            List<TypedPlanWindow> windows,
                            TypedPlanPredicate qualifyExpression,
                            List<PlanPreviewOrder> orderFields,
                            List<PlanPreviewJoin> joins,
                            PlanPreviewPaging paging,
                            List<TypedPlanTimeBucket> timeBuckets,
                            List<String> computedFields,
                            List<String> referencedFields,
                            List<String> outputFields,
                            List<String> joinSources,
                            QueryExecutionGuard executionGuard,
                            boolean hasSubqueries) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass must not be null");
        this.selectFields = List.copyOf(Objects.requireNonNull(selectFields, "selectFields must not be null"));
        this.filterExpression = filterExpression;
        this.groupByFields = List.copyOf(Objects.requireNonNull(groupByFields, "groupByFields must not be null"));
        this.metrics = List.copyOf(new ArrayList<>(Objects.requireNonNull(metrics, "metrics must not be null")));
        this.havingExpression = havingExpression;
        this.windows = List.copyOf(new ArrayList<>(Objects.requireNonNull(windows, "windows must not be null")));
        this.qualifyExpression = qualifyExpression;
        this.orderFields = List.copyOf(new ArrayList<>(Objects.requireNonNull(orderFields, "orderFields must not be null")));
        this.joins = List.copyOf(new ArrayList<>(Objects.requireNonNull(joins, "joins must not be null")));
        this.paging = paging;
        this.timeBuckets = List.copyOf(new ArrayList<>(Objects.requireNonNull(timeBuckets, "timeBuckets must not be null")));
        this.computedFields = List.copyOf(Objects.requireNonNull(computedFields, "computedFields must not be null"));
        this.referencedFields = List.copyOf(Objects.requireNonNull(referencedFields, "referencedFields must not be null"));
        this.outputFields = List.copyOf(Objects.requireNonNull(outputFields, "outputFields must not be null"));
        this.joinSources = List.copyOf(Objects.requireNonNull(joinSources, "joinSources must not be null"));
        this.executionGuard = executionGuard;
        this.hasSubqueries = hasSubqueries;
    }

    public String source() {
        return source;
    }

    public Class<?> entityClass() {
        return entityClass;
    }

    public List<String> selectFields() {
        return selectFields;
    }

    public TypedPlanPredicate filterExpression() {
        return filterExpression;
    }

    public List<String> groupByFields() {
        return groupByFields;
    }

    public List<TypedPlanMetric> metrics() {
        return metrics;
    }

    public TypedPlanPredicate havingExpression() {
        return havingExpression;
    }

    public List<TypedPlanWindow> windows() {
        return windows;
    }

    public TypedPlanPredicate qualifyExpression() {
        return qualifyExpression;
    }

    public List<PlanPreviewOrder> orderFields() {
        return orderFields;
    }

    public List<PlanPreviewJoin> joins() {
        return joins;
    }

    public PlanPreviewPaging paging() {
        return paging;
    }

    public List<TypedPlanTimeBucket> timeBuckets() {
        return timeBuckets;
    }

    public List<String> computedFields() {
        return computedFields;
    }

    public List<String> referencedFields() {
        return referencedFields;
    }

    public List<String> outputFields() {
        return outputFields;
    }

    public List<String> joinSources() {
        return joinSources;
    }

    public QueryExecutionGuard executionGuard() {
        return executionGuard;
    }

    public boolean hasSubqueries() {
        return hasSubqueries;
    }

    public boolean hasSelect() {
        return !selectFields.isEmpty();
    }

    public boolean hasGrouping() {
        return !groupByFields.isEmpty();
    }

    public boolean hasAggregation() {
        return !metrics.isEmpty();
    }

    public boolean hasWindows() {
        return !windows.isEmpty();
    }

    public boolean hasJoins() {
        return !joins.isEmpty();
    }

    public boolean hasPaging() {
        return paging != null;
    }

    public boolean hasTimeBuckets() {
        return !timeBuckets.isEmpty();
    }

    public boolean hasComputedFields() {
        return !computedFields.isEmpty();
    }

    public boolean hasExecutionGuard() {
        return executionGuard != null && !executionGuard.isUnrestricted();
    }
}
