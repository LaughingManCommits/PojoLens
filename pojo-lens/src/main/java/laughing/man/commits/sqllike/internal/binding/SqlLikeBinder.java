package laughing.man.commits.sqllike.internal.binding;

import laughing.man.commits.PojoLens;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.builder.QueryRule;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.builder.QueryWindowOrder;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.sqllike.internal.aggregate.AggregateExpressionSupport;
import laughing.man.commits.sqllike.internal.aggregate.AggregateExpressionSupport.ParsedAggregateExpression;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.sqllike.internal.expression.BooleanExpressionNormalizer;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;
import laughing.man.commits.sqllike.internal.execution.SqlLikeExecutionSupport;
import laughing.man.commits.sqllike.internal.params.BoundParameterValue;
import laughing.man.commits.sqllike.internal.validation.SqlLikeJoinResolution;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Internal SQL-like AST -> fluent query binder.
 */
public final class SqlLikeBinder {

    private static final int MAX_BOOLEAN_DNF_GROUPS = 2048;

    private SqlLikeBinder() {
    }

    public static QueryBuilder bind(QueryAst ast, List<?> pojos) {
        return bind(ast, pojos, java.util.Collections.emptyMap(), inferSourceClass(pojos), ComputedFieldRegistry.empty());
    }

    public static QueryBuilder bind(QueryAst ast, List<?> pojos, Map<String, List<?>> joinSources) {
        return bind(ast, pojos, joinSources, inferSourceClass(pojos), ComputedFieldRegistry.empty());
    }

    public static QueryBuilder bind(QueryAst ast,
                                    List<?> pojos,
                                    Map<String, List<?>> joinSources,
                                    Class<?> sourceClass) {
        return bind(ast, pojos, joinSources, sourceClass, ComputedFieldRegistry.empty());
    }

    public static QueryBuilder bind(QueryAst ast,
                                    List<?> pojos,
                                    Map<String, List<?>> joinSources,
                                    Class<?> sourceClass,
                                    ComputedFieldRegistry computedFieldRegistry) {
        SqlLikeJoinResolution.Plan joinPlan = SqlLikeJoinResolution.resolve(ast, sourceClass, joinSources);
        QueryAst normalizedAst = SqlLikeJoinResolution.canonicalize(ast, joinPlan);
        QueryBuilder builder = PojoLens.newQueryBuilder(pojos).computedFields(computedFieldRegistry);

        SelectAst select = normalizedAst.select();
        boolean groupedAggregation = normalizedAst.hasAggregation() || !normalizedAst.groupByFields().isEmpty();
        Set<String> configuredGroups = new LinkedHashSet<>();

        if (select != null && !select.wildcard()) {
            for (SelectFieldAst field : select.fields()) {
                if (!field.timeBucketField()) {
                    continue;
                }
                builder.addTimeBucket(field.field(), field.timeBucketPreset(), field.outputName());
                configuredGroups.add(field.outputName());
            }
        }

        if (groupedAggregation) {
            for (String group : normalizedAst.groupByFields()) {
                if (configuredGroups.contains(group)) {
                    continue;
                }
                builder.addGroup(group);
                configuredGroups.add(group);
            }
            if (select != null && !select.wildcard()) {
                for (SelectFieldAst field : select.fields()) {
                    if (!field.metricField()) {
                        continue;
                    }
                    if (field.countAll()) {
                        builder.addCount(field.outputName());
                    } else {
                        builder.addMetric(field.field(), field.metric(), field.outputName());
                    }
                }
            }
        } else if (select != null && !select.wildcard()) {
            if (!select.hasComputedFields() && !select.hasWindowFields()) {
                for (SelectFieldAst field : select.fields()) {
                    builder.addField(field.field());
                }
            }
        }

        if (normalizedAst.whereExpression() != null) {
            applyWhereExpression(builder, normalizedAst.whereExpression(), pojos, joinSources, computedFieldRegistry);
        } else {
            applyLegacyWhereFilters(builder, normalizedAst.filters(), pojos, joinSources, computedFieldRegistry);
        }

        Map<String, String> aggregateExpressionOutputs = resolveAggregateExpressionOutputs(select);
        Map<String, String> hiddenHavingAliases = new LinkedHashMap<>();
        Map<String, String> hiddenOrderAliases = new LinkedHashMap<>();
        if (normalizedAst.havingExpression() != null) {
            applyHavingExpression(builder, normalizedAst.havingExpression(), aggregateExpressionOutputs, hiddenHavingAliases, pojos, joinSources, computedFieldRegistry);
        } else {
            applyLegacyHavingFilters(builder, normalizedAst.havingFilters(), aggregateExpressionOutputs, hiddenHavingAliases, pojos, joinSources, computedFieldRegistry);
        }
        applyWindowDefinitions(builder, select);
        if (normalizedAst.qualifyExpression() != null) {
            applyQualifyExpression(builder, normalizedAst.qualifyExpression(), pojos, joinSources, computedFieldRegistry);
        } else {
            applyLegacyQualifyFilters(builder, normalizedAst.qualifyFilters(), pojos, joinSources, computedFieldRegistry);
        }

        int orderIndex = 1;
        for (OrderAst order : normalizedAst.orders()) {
            builder.addOrder(resolveOrderField(builder, order.field(), aggregateExpressionOutputs, hiddenOrderAliases), orderIndex++);
        }

        if (normalizedAst.limit() != null) {
            builder.limit(normalizedAst.limit());
        }
        if (normalizedAst.offset() != null) {
            builder.offset(normalizedAst.offset());
        }

        for (SqlLikeJoinResolution.ResolvedJoin join : joinPlan.joins()) {
            List<?> children = joinSources.get(join.join().childSource());
            if (children == null) {
                throw SqlLikeErrors.argument(SqlLikeErrorCodes.VALIDATION_MISSING_JOIN_SOURCE,
                        "Missing JOIN source binding for '" + join.join().childSource() + "'");
            }
            builder.addJoinBeans(join.parentField(), children, join.childField(), join.join().joinType());
        }

        return builder;
    }

    public static Sort resolveSort(QueryAst ast) {
        if (ast.orders().isEmpty()) {
            return null;
        }
        Sort first = ast.orders().get(0).sort();
        for (OrderAst order : ast.orders()) {
            if (order.sort() != first) {
                throw SqlLikeErrors.argument(SqlLikeErrorCodes.BIND_MIXED_ORDER_DIRECTIONS,
                        "Mixed ORDER BY directions are not supported in v1; use all ASC or all DESC");
            }
        }
        return first;
    }

    private static void applyLegacyWhereFilters(QueryBuilder builder,
                                                List<FilterAst> filters,
                                                List<?> pojos,
                                                Map<String, List<?>> joinSources,
                                                ComputedFieldRegistry computedFieldRegistry) {
        for (FilterAst filter : filters) {
            Separator separator = filter.separator() == null
                    ? Separator.AND
                    : filter.separator();
            builder.addRule(filter.field(), resolveValue(filter.value(), pojos, joinSources, computedFieldRegistry), filter.clause(), separator);
        }
    }

    private static void applyLegacyHavingFilters(QueryBuilder builder,
                                                 List<FilterAst> filters,
                                                 Map<String, String> aggregateExpressionOutputs,
                                                 Map<String, String> hiddenHavingAliases,
                                                 List<?> pojos,
                                                 Map<String, List<?>> joinSources,
                                                 ComputedFieldRegistry computedFieldRegistry) {
        for (FilterAst filter : filters) {
            Separator separator = filter.separator() == null
                    ? Separator.AND
                    : filter.separator();
            FilterAst resolved = resolveHavingFilter(builder, filter, aggregateExpressionOutputs, hiddenHavingAliases);
            builder.addHaving(resolved.field(), resolveValue(resolved.value(), pojos, joinSources, computedFieldRegistry), resolved.clause(), separator);
        }
    }

    private static void applyWhereExpression(QueryBuilder builder,
                                             FilterExpressionAst expression,
                                             List<?> pojos,
                                             Map<String, List<?>> joinSources,
                                             ComputedFieldRegistry computedFieldRegistry) {
        if (expression instanceof FilterPredicateAst) {
            FilterAst filter = ((FilterPredicateAst) expression).filter();
            if (!SqlExpressionEvaluator.looksLikeExpression(filter.field())) {
                builder.addRule(filter.field(),
                        resolveValue(filter.value(), pojos, joinSources, computedFieldRegistry),
                        filter.clause(),
                        Separator.AND);
                return;
            }
        }
        List<List<FilterAst>> groups = BooleanExpressionNormalizer.toDnf(expression, MAX_BOOLEAN_DNF_GROUPS);
        for (List<FilterAst> group : groups) {
            QueryRule[] rules = toQueryRules(group, pojos, joinSources, computedFieldRegistry);
            builder.allOf(rules);
        }
    }

    private static void applyWindowDefinitions(QueryBuilder builder, SelectAst select) {
        if (select == null || select.wildcard()) {
            return;
        }
        for (SelectFieldAst field : select.fields()) {
            if (!field.windowField()) {
                continue;
            }
            builder.addWindow(
                    field.outputName(),
                    resolveWindowFunction(field.windowFunction()),
                    field.windowValueField(),
                    field.windowCountAll(),
                    field.windowPartitionFields(),
                    toWindowOrderFields(field.windowOrderFields())
            );
        }
    }

    private static WindowFunction resolveWindowFunction(String function) {
        if (function == null) {
            throw new IllegalArgumentException("Window function is required");
        }
        return switch (function.trim().toUpperCase(Locale.ROOT)) {
            case "ROW_NUMBER" -> WindowFunction.ROW_NUMBER;
            case "RANK" -> WindowFunction.RANK;
            case "DENSE_RANK" -> WindowFunction.DENSE_RANK;
            case "COUNT" -> WindowFunction.COUNT;
            case "SUM" -> WindowFunction.SUM;
            case "AVG" -> WindowFunction.AVG;
            case "MIN" -> WindowFunction.MIN;
            case "MAX" -> WindowFunction.MAX;
            default -> throw new IllegalArgumentException("Unsupported window function '" + function + "'");
        };
    }

    private static List<QueryWindowOrder> toWindowOrderFields(List<OrderAst> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        ArrayList<QueryWindowOrder> orderFields = new ArrayList<>(orders.size());
        for (OrderAst order : orders) {
            orderFields.add(QueryWindowOrder.of(order.field(), order.sort()));
        }
        return List.copyOf(orderFields);
    }

    private static void applyHavingExpression(QueryBuilder builder,
                                              FilterExpressionAst expression,
                                              Map<String, String> aggregateExpressionOutputs,
                                              Map<String, String> hiddenHavingAliases,
                                              List<?> pojos,
                                              Map<String, List<?>> joinSources,
                                              ComputedFieldRegistry computedFieldRegistry) {
        if (expression instanceof FilterPredicateAst) {
            FilterAst resolved = resolveHavingFilter(
                    builder,
                    ((FilterPredicateAst) expression).filter(),
                    aggregateExpressionOutputs,
                    hiddenHavingAliases
            );
            if (!SqlExpressionEvaluator.looksLikeExpression(resolved.field())) {
                builder.addHaving(resolved.field(),
                        resolveValue(resolved.value(), pojos, joinSources, computedFieldRegistry),
                        resolved.clause(),
                        Separator.AND);
                return;
            }
        }
        List<List<FilterAst>> groups = BooleanExpressionNormalizer.toDnf(expression, MAX_BOOLEAN_DNF_GROUPS);
        for (List<FilterAst> group : groups) {
            QueryRule[] rules = new QueryRule[group.size()];
            for (int i = 0; i < group.size(); i++) {
                FilterAst resolved = resolveHavingFilter(builder, group.get(i), aggregateExpressionOutputs, hiddenHavingAliases);
                rules[i] = QueryRule.of(resolved.field(), resolveValue(resolved.value(), pojos, joinSources, computedFieldRegistry), resolved.clause());
            }
            builder.addHavingAllOf(rules);
        }
    }

    private static void applyLegacyQualifyFilters(QueryBuilder builder,
                                                  List<FilterAst> filters,
                                                  List<?> pojos,
                                                  Map<String, List<?>> joinSources,
                                                  ComputedFieldRegistry computedFieldRegistry) {
        for (FilterAst filter : filters) {
            Separator separator = filter.separator() == null
                    ? Separator.AND
                    : filter.separator();
            builder.addQualify(
                    filter.field(),
                    resolveValue(filter.value(), pojos, joinSources, computedFieldRegistry),
                    filter.clause(),
                    separator
            );
        }
    }

    private static void applyQualifyExpression(QueryBuilder builder,
                                               FilterExpressionAst expression,
                                               List<?> pojos,
                                               Map<String, List<?>> joinSources,
                                               ComputedFieldRegistry computedFieldRegistry) {
        if (expression instanceof FilterPredicateAst) {
            FilterAst filter = ((FilterPredicateAst) expression).filter();
            if (!SqlExpressionEvaluator.looksLikeExpression(filter.field())) {
                builder.addQualify(
                        filter.field(),
                        resolveValue(filter.value(), pojos, joinSources, computedFieldRegistry),
                        filter.clause(),
                        Separator.AND
                );
                return;
            }
        }
        List<List<FilterAst>> groups = BooleanExpressionNormalizer.toDnf(expression, MAX_BOOLEAN_DNF_GROUPS);
        for (List<FilterAst> group : groups) {
            QueryRule[] rules = toQueryRules(group, pojos, joinSources, computedFieldRegistry);
            builder.addQualifyAllOf(rules);
        }
    }

    private static FilterAst resolveHavingFilter(QueryBuilder builder,
                                                 FilterAst filter,
                                                 Map<String, String> aggregateExpressionOutputs,
                                                 Map<String, String> hiddenHavingAliases) {
        String fieldName = aggregateExpressionOutputs.getOrDefault(filter.field(), filter.field());
        ParsedAggregateExpression aggregateExpression = AggregateExpressionSupport.parse(filter.field());
        if (aggregateExpression != null && !aggregateExpressionOutputs.containsKey(filter.field())) {
            fieldName = hiddenHavingAliases.computeIfAbsent(
                    filter.field(),
                    ignored -> AggregateExpressionSupport.addHiddenHavingAggregate(builder, aggregateExpression)
            );
        }
        return new FilterAst(fieldName, filter.clause(), filter.value(), filter.separator());
    }

    private static QueryRule[] toQueryRules(List<FilterAst> group,
                                            List<?> pojos,
                                            Map<String, List<?>> joinSources,
                                            ComputedFieldRegistry computedFieldRegistry) {
        QueryRule[] rules = new QueryRule[group.size()];
        for (int i = 0; i < group.size(); i++) {
            FilterAst filter = group.get(i);
            rules[i] = QueryRule.of(filter.field(), resolveValue(filter.value(), pojos, joinSources, computedFieldRegistry), filter.clause());
        }
        return rules;
    }

    private static Map<String, String> resolveAggregateExpressionOutputs(SelectAst select) {
        return AggregateExpressionSupport.resolveSelectAggregateOutputAliases(select);
    }

    private static String resolveOrderField(QueryBuilder builder,
                                            String field,
                                            Map<String, String> aggregateExpressionOutputs,
                                            Map<String, String> hiddenOrderAliases) {
        String resolved = aggregateExpressionOutputs.get(field);
        if (resolved != null) {
            return resolved;
        }
        ParsedAggregateExpression aggregateExpression = AggregateExpressionSupport.parse(field);
        if (aggregateExpression == null) {
            return field;
        }
        return hiddenOrderAliases.computeIfAbsent(
                field,
                ignored -> AggregateExpressionSupport.addHiddenOrderAggregate(builder, aggregateExpression)
        );
    }

    private static Object unwrapValue(Object value) {
        if (value instanceof BoundParameterValue boundParameterValue) {
            return boundParameterValue.value();
        }
        return value;
    }

    private static Object resolveValue(Object value,
                                       List<?> pojos,
                                       Map<String, List<?>> joinSources,
                                       ComputedFieldRegistry computedFieldRegistry) {
        Object unwrapped = unwrapValue(value);
        if (unwrapped instanceof SubqueryValueAst subqueryValueAst) {
            return resolveSubqueryValues(subqueryValueAst, pojos, joinSources, computedFieldRegistry);
        }
        return unwrapped;
    }

    private static List<Object> resolveSubqueryValues(SubqueryValueAst subqueryValueAst,
                                                      List<?> pojos,
                                                      Map<String, List<?>> joinSources,
                                                      ComputedFieldRegistry computedFieldRegistry) {
        QueryAst subquery = subqueryValueAst.query();
        SelectFieldAst selectedField = subquery.select().fields().get(0);
        List<?> sourceRows = resolveSubquerySourceRows(subquery.select(), pojos, joinSources);
        if (sourceRows == null || sourceRows.isEmpty()) {
            return Collections.emptyList();
        }

        QueryBuilder subqueryBuilder = bind(subquery, sourceRows, Collections.emptyMap(), inferSourceClass(sourceRows), computedFieldRegistry);
        Sort subquerySort = resolveSort(subquery);
        Class<?> sourceClass = inferSourceClass(sourceRows);
        List<?> rows = SqlLikeExecutionSupport.executeWithOptionalJoin(subqueryBuilder, subquerySort, false, sourceClass);

        ArrayList<Object> values = new ArrayList<>(rows.size());
        for (Object row : rows) {
            try {
                values.add(ReflectionUtil.getFieldValue(row, selectedField.field()));
            } catch (Exception e) {
                throw SqlLikeErrors.argument(SqlLikeErrorCodes.RUNTIME_EXPRESSION_IDENTIFIER_RESOLUTION_FAILED,
                        "Failed to resolve subquery field '" + selectedField.field() + "'");
            }
        }
        return values;
    }

    private static List<?> resolveSubquerySourceRows(SelectAst select,
                                                     List<?> pojos,
                                                     Map<String, List<?>> joinSources) {
        if (select.sourceName() == null) {
            return pojos;
        }
        List<?> rows = joinSources.get(select.sourceName());
        if (rows == null) {
            throw SqlLikeErrors.argument(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                    "Missing subquery source binding for '" + select.sourceName() + "'");
        }
        return rows;
    }

    private static Class<?> inferSourceClass(List<?> rows) {
        for (Object row : rows) {
            if (row != null) {
                return row.getClass();
            }
        }
        throw SqlLikeErrors.argument(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                "Subquery source rows must contain at least one non-null element");
    }
}

