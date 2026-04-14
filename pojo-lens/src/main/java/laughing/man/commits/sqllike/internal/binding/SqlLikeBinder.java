package laughing.man.commits.sqllike.internal.binding;

import laughing.man.commits.PojoLensCore;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.builder.QueryRule;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.builder.QueryWindowOrder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.filter.FilterExecutionPlanCacheStore;
import laughing.man.commits.filter.internal.DefaultFilterExecutionPlanCacheSupport;
import laughing.man.commits.sqllike.ast.ExistsSubqueryValueAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
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
import laughing.man.commits.sqllike.internal.validation.SqlLikeValidator;
import laughing.man.commits.util.QueryFieldLookupUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Internal SQL-like AST -> fluent query binder.
 */
public final class SqlLikeBinder {

    private static final int MAX_BOOLEAN_DNF_GROUPS = 2048;
    private static final String IMPOSSIBLE_EXISTS_FIELD = "__pojo_lens_exists_false";

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
        return bind(ast, pojos, joinSources, sourceClass, computedFieldRegistry,
                DefaultFilterExecutionPlanCacheSupport.defaultStore());
    }

    public static QueryBuilder bind(QueryAst ast,
                                    List<?> pojos,
                                    Map<String, List<?>> joinSources,
                                    Class<?> sourceClass,
                                    ComputedFieldRegistry computedFieldRegistry,
                                    FilterExecutionPlanCacheStore executionPlanCache) {
        SqlLikeJoinResolution.Plan joinPlan = SqlLikeJoinResolution.resolve(ast, sourceClass, joinSources);
        QueryAst normalizedAst = SqlLikeJoinResolution.canonicalize(ast, joinPlan);
        QueryBuilder builder = PojoLensCore.newQueryBuilder(pojos, executionPlanCache).computedFields(computedFieldRegistry);
        return configureBoundBuilder(builder, normalizedAst, joinPlan, pojos, joinSources, computedFieldRegistry);
    }

    private static QueryBuilder configureBoundBuilder(QueryBuilder builder,
                                                      QueryAst normalizedAst,
                                                      SqlLikeJoinResolution.Plan joinPlan,
                                                      List<?> pojos,
                                                      Map<String, List<?>> joinSources,
                                                      ComputedFieldRegistry computedFieldRegistry) {
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
        if (hasSubqueryFilter(filters) && !hasOrSeparator(filters)) {
            for (FilterAst filter : filters) {
                if (applyDirectWhereSubquery(builder, filter, pojos, joinSources, computedFieldRegistry)) {
                    continue;
                }
                Separator separator = filter.separator() == null
                        ? Separator.AND
                        : filter.separator();
                builder.addRule(
                        filter.field(),
                        resolveValue(filter.value(), pojos, joinSources, computedFieldRegistry),
                        filter.clause(),
                        separator
                );
            }
            return;
        }
        if (hasExistsFilter(filters)) {
            applyWhereExpression(builder, expressionFromLegacyFilters(filters), pojos, joinSources, computedFieldRegistry);
            return;
        }
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
            if (applyDirectWhereSubquery(builder, filter, pojos, joinSources, computedFieldRegistry)) {
                return;
            }
            if (!SqlExpressionEvaluator.looksLikeExpression(filter.field())) {
                builder.addRule(filter.field(),
                        resolveValue(filter.value(), pojos, joinSources, computedFieldRegistry),
                        filter.clause(),
                        Separator.AND);
                return;
            }
        }
        List<List<FilterAst>> groups = BooleanExpressionNormalizer.toDnf(expression, MAX_BOOLEAN_DNF_GROUPS);
        boolean matchedAnyGroup = false;
        for (List<FilterAst> group : groups) {
            ResolvedWhereGroup resolved = resolveWhereGroup(group, pojos, joinSources, computedFieldRegistry);
            if (resolved.unsatisfiable()) {
                continue;
            }
            if (resolved.rules().isEmpty()) {
                return;
            }
            builder.allOf(resolved.rules().toArray(new QueryRule[0]));
            matchedAnyGroup = true;
        }
        if (!matchedAnyGroup) {
            addImpossibleWhereGroup(builder);
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
                    toWindowOrderFields(field.windowOrderFields()),
                    field.windowFrame()
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

    private static boolean hasExistsFilter(List<FilterAst> filters) {
        for (FilterAst filter : filters) {
            if (isExistsFilter(filter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSubqueryFilter(List<FilterAst> filters) {
        for (FilterAst filter : filters) {
            Object value = unwrapValue(filter.value());
            if (value instanceof SubqueryValueAst || value instanceof ExistsSubqueryValueAst) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOrSeparator(List<FilterAst> filters) {
        for (FilterAst filter : filters) {
            if (Separator.OR.equals(filter.separator())) {
                return true;
            }
        }
        return false;
    }

    private static FilterExpressionAst expressionFromLegacyFilters(List<FilterAst> filters) {
        FilterExpressionAst expression = null;
        for (FilterAst filter : filters) {
            FilterExpressionAst predicate = new FilterPredicateAst(
                    new FilterAst(filter.field(), filter.clause(), filter.value(), null)
            );
            if (expression == null) {
                expression = predicate;
                continue;
            }
            Separator operator = filter.separator() == null ? Separator.AND : filter.separator();
            expression = new FilterBinaryAst(expression, predicate, operator);
        }
        return expression;
    }

    private static boolean isExistsFilter(FilterAst filter) {
        return unwrapValue(filter.value()) instanceof ExistsSubqueryValueAst;
    }

    private static boolean applyDirectWhereSubquery(QueryBuilder builder,
                                                   FilterAst filter,
                                                   List<?> pojos,
                                                   Map<String, List<?>> joinSources,
                                                   ComputedFieldRegistry computedFieldRegistry) {
        Object value = unwrapValue(filter.value());
        if (value instanceof SubqueryValueAst subqueryValueAst && Clauses.IN.equals(filter.clause())) {
            applyInSubquery(builder, filter.field(), subqueryValueAst, pojos, joinSources, computedFieldRegistry);
            return true;
        }
        if (value instanceof ExistsSubqueryValueAst existsSubqueryValueAst) {
            applyExistsSubquery(builder, existsSubqueryValueAst, pojos, joinSources, computedFieldRegistry);
            return true;
        }
        return false;
    }

    private static void applyInSubquery(QueryBuilder builder,
                                        String targetField,
                                        SubqueryValueAst subqueryValueAst,
                                        List<?> pojos,
                                        Map<String, List<?>> joinSources,
                                        ComputedFieldRegistry computedFieldRegistry) {
        QueryAst subquery = SqlLikeValidator.normalizeAggregationAliases(subqueryValueAst.query());
        SelectFieldAst selectedField = subquery.select().fields().get(0);
        String outputField = subqueryOutputField(selectedField);
        List<?> sourceRows = resolveSubquerySourceRows(subquery.select(), pojos, joinSources);
        Consumer<QueryBuilder> configurer = subqueryConfigurer(subquery, sourceRows, joinSources, computedFieldRegistry);
        if (subquery.select().sourceName() == null) {
            builder.addInSubquery(targetField, outputField, configurer);
        } else {
            builder.addInSubquery(targetField, sourceRows, outputField, configurer);
        }
    }

    private static void applyExistsSubquery(QueryBuilder builder,
                                            ExistsSubqueryValueAst existsSubqueryValueAst,
                                            List<?> pojos,
                                            Map<String, List<?>> joinSources,
                                            ComputedFieldRegistry computedFieldRegistry) {
        QueryAst subquery = SqlLikeValidator.normalizeAggregationAliases(existsSubqueryValueAst.query());
        List<?> sourceRows = resolveSubquerySourceRows(subquery.select(), pojos, joinSources);
        Consumer<QueryBuilder> configurer = subqueryConfigurer(subquery, sourceRows, joinSources, computedFieldRegistry);
        if (subquery.select().sourceName() == null) {
            if (existsSubqueryValueAst.negated()) {
                builder.addNotExists(configurer);
            } else {
                builder.addExists(configurer);
            }
            return;
        }
        if (existsSubqueryValueAst.negated()) {
            builder.addNotExists(sourceRows, configurer);
        } else {
            builder.addExists(sourceRows, configurer);
        }
    }

    private static Consumer<QueryBuilder> subqueryConfigurer(QueryAst subquery,
                                                             List<?> sourceRows,
                                                             Map<String, List<?>> joinSources,
                                                             ComputedFieldRegistry computedFieldRegistry) {
        return subqueryBuilder -> configureSubqueryBuilder(subqueryBuilder, subquery, sourceRows, joinSources,
                computedFieldRegistry);
    }

    private static void configureSubqueryBuilder(QueryBuilder builder,
                                                 QueryAst subquery,
                                                 List<?> sourceRows,
                                                 Map<String, List<?>> joinSources,
                                                 ComputedFieldRegistry computedFieldRegistry) {
        Class<?> sourceClass = inferSourceClass(sourceRows);
        SqlLikeJoinResolution.Plan joinPlan = SqlLikeJoinResolution.resolve(subquery, sourceClass, joinSources);
        QueryAst normalizedSubquery = SqlLikeJoinResolution.canonicalize(subquery, joinPlan);
        configureBoundBuilder(
                builder.computedFields(computedFieldRegistry),
                normalizedSubquery,
                joinPlan,
                sourceRows,
                joinSources,
                computedFieldRegistry
        );
    }

    private static ResolvedWhereGroup resolveWhereGroup(List<FilterAst> group,
                                                        List<?> pojos,
                                                        Map<String, List<?>> joinSources,
                                                        ComputedFieldRegistry computedFieldRegistry) {
        ArrayList<QueryRule> rules = new ArrayList<>(group.size());
        for (FilterAst filter : group) {
            rules.add(toWhereQueryRule(filter, pojos, joinSources, computedFieldRegistry));
        }
        return ResolvedWhereGroup.satisfiable(rules);
    }

    private static QueryRule toWhereQueryRule(FilterAst filter,
                                              List<?> pojos,
                                              Map<String, List<?>> joinSources,
                                              ComputedFieldRegistry computedFieldRegistry) {
        Object value = unwrapValue(filter.value());
        if (value instanceof SubqueryValueAst subqueryValueAst && Clauses.IN.equals(filter.clause())) {
            return inSubqueryRule(filter.field(), subqueryValueAst, pojos, joinSources, computedFieldRegistry);
        }
        if (value instanceof ExistsSubqueryValueAst existsSubqueryValueAst) {
            return existsSubqueryRule(existsSubqueryValueAst, pojos, joinSources, computedFieldRegistry);
        }
        return QueryRule.of(
                filter.field(),
                resolveValue(filter.value(), pojos, joinSources, computedFieldRegistry),
                filter.clause()
        );
    }

    private static QueryRule inSubqueryRule(String targetField,
                                            SubqueryValueAst subqueryValueAst,
                                            List<?> pojos,
                                            Map<String, List<?>> joinSources,
                                            ComputedFieldRegistry computedFieldRegistry) {
        QueryAst subquery = SqlLikeValidator.normalizeAggregationAliases(subqueryValueAst.query());
        SelectFieldAst selectedField = subquery.select().fields().get(0);
        String outputField = subqueryOutputField(selectedField);
        List<?> sourceRows = resolveSubquerySourceRows(subquery.select(), pojos, joinSources);
        Consumer<QueryBuilder> configurer = subqueryConfigurer(subquery, sourceRows, joinSources, computedFieldRegistry);
        if (subquery.select().sourceName() == null) {
            return QueryRule.inSubquery(targetField, outputField, configurer);
        }
        return QueryRule.inSubquery(targetField, sourceRows, outputField, configurer);
    }

    private static QueryRule existsSubqueryRule(ExistsSubqueryValueAst existsSubqueryValueAst,
                                                List<?> pojos,
                                                Map<String, List<?>> joinSources,
                                                ComputedFieldRegistry computedFieldRegistry) {
        QueryAst subquery = SqlLikeValidator.normalizeAggregationAliases(existsSubqueryValueAst.query());
        List<?> sourceRows = resolveSubquerySourceRows(subquery.select(), pojos, joinSources);
        Consumer<QueryBuilder> configurer = subqueryConfigurer(subquery, sourceRows, joinSources, computedFieldRegistry);
        if (subquery.select().sourceName() == null) {
            return existsSubqueryValueAst.negated()
                    ? QueryRule.notExists(configurer)
                    : QueryRule.exists(configurer);
        }
        return existsSubqueryValueAst.negated()
                ? QueryRule.notExists(sourceRows, configurer)
                : QueryRule.exists(sourceRows, configurer);
    }

    private static void addImpossibleWhereGroup(QueryBuilder builder) {
        builder.allOf(QueryRule.of(IMPOSSIBLE_EXISTS_FIELD, Boolean.TRUE, Clauses.EQUAL));
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

    private static boolean resolveExistsSubquery(ExistsSubqueryValueAst existsSubqueryValueAst,
                                                 List<?> pojos,
                                                 Map<String, List<?>> joinSources,
                                                 ComputedFieldRegistry computedFieldRegistry) {
        QueryAst subquery = SqlLikeValidator.normalizeAggregationAliases(existsSubqueryValueAst.query());
        List<?> sourceRows = resolveSubquerySourceRows(subquery.select(), pojos, joinSources);
        if (sourceRows == null || sourceRows.isEmpty()) {
            return existsSubqueryValueAst.negated();
        }

        boolean exists = !executeSubqueryRows(subquery, sourceRows, joinSources, computedFieldRegistry).isEmpty();
        return existsSubqueryValueAst.negated() ? !exists : exists;
    }

    private static List<Object> resolveSubqueryValues(SubqueryValueAst subqueryValueAst,
                                                      List<?> pojos,
                                                      Map<String, List<?>> joinSources,
                                                      ComputedFieldRegistry computedFieldRegistry) {
        QueryAst subquery = SqlLikeValidator.normalizeAggregationAliases(subqueryValueAst.query());
        SelectFieldAst selectedField = subquery.select().fields().get(0);
        List<?> sourceRows = resolveSubquerySourceRows(subquery.select(), pojos, joinSources);
        if (sourceRows == null || sourceRows.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<Object> values = new ArrayList<>(sourceRows.size());
        List<QueryRow> rows = executeSubqueryRows(subquery, sourceRows, joinSources, computedFieldRegistry);
        String outputField = subqueryOutputField(selectedField);
        for (QueryRow row : rows) {
            values.add(resolveSubqueryRowValue(row, outputField));
        }
        return values;
    }

    private static List<QueryRow> executeSubqueryRows(QueryAst subquery,
                                                      List<?> sourceRows,
                                                      Map<String, List<?>> joinSources,
                                                      ComputedFieldRegistry computedFieldRegistry) {
        Class<?> sourceClass = inferSourceClass(sourceRows);
        QueryBuilder subqueryBuilder = bind(subquery, sourceRows, joinSources, sourceClass, computedFieldRegistry);
        Sort subquerySort = resolveSort(subquery);
        List<?> rows = SqlLikeExecutionSupport.executeWithOptionalJoin(
                subqueryBuilder,
                subquerySort,
                subquery.hasJoins(),
                QueryRow.class
        );
        ArrayList<QueryRow> queryRows = new ArrayList<>(rows.size());
        for (Object row : rows) {
            queryRows.add((QueryRow) row);
        }
        return queryRows;
    }

    private static String subqueryOutputField(SelectFieldAst selectedField) {
        if (selectedField.metricField()
                || selectedField.timeBucketField()
                || selectedField.computedField()
                || selectedField.windowField()) {
            return selectedField.outputName();
        }
        return selectedField.field();
    }

    private static Object resolveSubqueryRowValue(QueryRow row, String fieldName) {
        int fieldIndex = QueryFieldLookupUtil.findFieldIndex(row.getFields(), fieldName);
        if (fieldIndex < 0) {
            throw SqlLikeErrors.argument(SqlLikeErrorCodes.RUNTIME_EXPRESSION_IDENTIFIER_RESOLUTION_FAILED,
                    "Failed to resolve subquery field '" + fieldName + "'");
        }
        return row.getValueAt(fieldIndex);
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

    private record ResolvedWhereGroup(List<QueryRule> rules, boolean unsatisfiable) {

        static ResolvedWhereGroup satisfiable(List<QueryRule> rules) {
            return new ResolvedWhereGroup(List.copyOf(rules), false);
        }

        static ResolvedWhereGroup unsatisfiableGroup() {
            return new ResolvedWhereGroup(List.of(), true);
        }
    }
}

