package laughing.man.commits.sqllike.internal.validation;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.computed.internal.ComputedFieldSupport;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.internal.NameSuggestions;
import laughing.man.commits.sqllike.ast.ExistsSubqueryValueAst;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.sqllike.internal.error.SqlLikeFieldMessages;
import laughing.man.commits.sqllike.internal.error.SqlLikeSourceBindingMessages;
import laughing.man.commits.sqllike.internal.aggregate.AggregateExpressionSupport;
import laughing.man.commits.sqllike.internal.aggregate.AggregateExpressionSupport.ParsedAggregateExpression;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.TimeBucketUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Internal query validation for SQL-like execution.
 */
public final class SqlLikeValidator {

    private SqlLikeValidator() {
    }

    public static QueryAst validateForFilter(QueryAst ast,
                                             Class<?> sourceClass,
                                             Class<?> projectionClass,
                                             Map<String, List<?>> joinSources,
                                             boolean strictParameterTypes,
                                             ComputedFieldRegistry computedFieldRegistry) {
        Set<String> sourceFields = collectFields(sourceClass);
        Map<String, Class<?>> sourceFieldTypes = collectFieldTypes(sourceClass);
        SqlLikeJoinResolution.Plan joinPlan = SqlLikeJoinResolution.resolve(ast, sourceClass, joinSources);
        QueryAst normalizedAst = SqlLikeJoinResolution.canonicalize(ast, joinPlan);
        normalizedAst = normalizeAggregationAliases(normalizedAst);
        normalizedAst = normalizeQualifyWindowReferences(normalizedAst);
        Map<String, Class<?>> queryableFieldTypes = joinPlan.isEmpty()
                ? ComputedFieldSupport.augmentFieldTypes(sourceFieldTypes, computedFieldRegistry)
                : ComputedFieldSupport.augmentFieldTypes(joinPlan.mergedFieldTypes(), computedFieldRegistry);
        Set<String> queryableSourceFields = new LinkedHashSet<>(queryableFieldTypes.keySet());
        Set<String> projectionFields = collectFields(projectionClass);
        boolean dynamicProjection = QueryRow.class.isAssignableFrom(projectionClass);
        validateAggregationSemantics(normalizedAst, queryableSourceFields, queryableFieldTypes);
        validateSelect(normalizedAst, queryableSourceFields, queryableFieldTypes, projectionFields, dynamicProjection);
        validateFilters(normalizedAst.filters(), queryableSourceFields, sourceClass, joinSources, computedFieldRegistry);
        validateHaving(normalizedAst, queryableSourceFields, sourceClass, joinSources, computedFieldRegistry);
        validateQualify(normalizedAst, queryableSourceFields);
        validateOrders(normalizedAst, resolveAllowedOrderFields(normalizedAst, queryableSourceFields), queryableSourceFields);
        if (strictParameterTypes) {
            SqlLikeParameterTypeValidator.validate(normalizedAst, queryableFieldTypes, sourceFieldTypes);
        }
        return normalizedAst;
    }

    static Class<?> inferListElementClass(List<?> rows) {
        if (rows == null || rows.isEmpty()) {
            throw validation(SqlLikeErrorCodes.VALIDATION_INVALID_JOIN_ROWS,
                    "JOIN source rows must not be null/empty");
        }
        for (Object row : rows) {
            if (row != null) {
                return row.getClass();
            }
        }
        throw validation(SqlLikeErrorCodes.VALIDATION_INVALID_JOIN_ROWS,
                "JOIN source rows must contain at least one non-null element");
    }

    private static void validateSelect(QueryAst ast,
                                       Set<String> sourceFields,
                                       Map<String, Class<?>> sourceFieldTypes,
                                       Set<String> projectionFields,
                                       boolean dynamicProjection) {
        SelectAst select = ast.select();
        if (select == null || select.wildcard()) {
            return;
        }
        Set<String> seenOutputNames = new HashSet<>();
        for (SelectFieldAst field : select.fields()) {
            if (field.computedField()) {
                if (ast.hasAggregation() || !ast.groupByFields().isEmpty()) {
                    throw validation(SqlLikeErrorCodes.VALIDATION_COMPUTED_SELECT,
                            "Computed SELECT expressions are only supported for non-aggregate queries");
                }
                if (!field.aliased()) {
                    throw validation(SqlLikeErrorCodes.VALIDATION_COMPUTED_SELECT,
                            "Computed SELECT expressions require AS alias");
                }
                validateExpressionIdentifiers(field.field(), sourceFields, "SELECT");
                String outputName = field.outputName();
                if (!seenOutputNames.add(outputName)) {
                    throw validation(SqlLikeErrorCodes.VALIDATION_DUPLICATE_SELECT_OUTPUT,
                            "Duplicate SELECT output name '" + outputName + "'");
                }
                requireProjectionField(outputName, projectionFields, dynamicProjection);
                continue;
            }
            if (field.windowField()) {
                if (ast.hasAggregation() || !ast.groupByFields().isEmpty()) {
                    throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                            "Window SELECT expressions are only supported for non-aggregate queries");
                }
                if (!field.aliased()) {
                    throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                            "Window SELECT expressions require AS alias");
                }
                if (isAggregateWindowFunction(field.windowFunction())) {
                    if (field.windowCountAll() && !"COUNT".equalsIgnoreCase(field.windowFunction())) {
                        throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                                field.windowFunction() + " window does not support '*' argument");
                    }
                    if (!field.windowCountAll()) {
                        if (field.windowValueField() == null || field.windowValueField().isBlank()) {
                            throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                                    "Window SELECT expression '" + field.windowFunction()
                                            + "' requires value field argument");
                        }
                        requireKnownField(field.windowValueField(), sourceFields, "SELECT");
                        if (requiresNumericWindowFunction(field.windowFunction())) {
                            requireNumericField(field.windowValueField(), sourceFieldTypes, field.windowFunction());
                        }
                    }
                } else if (field.windowValueField() != null || field.windowCountAll()) {
                    throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                            "Rank window SELECT expressions do not accept value field arguments");
                }
                if (field.windowOrderFields().isEmpty()) {
                    throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                            "Window SELECT expressions require OVER(... ORDER BY ...)");
                }
                for (String partitionField : field.windowPartitionFields()) {
                    requireKnownField(partitionField, sourceFields, "SELECT");
                }
                for (OrderAst order : field.windowOrderFields()) {
                    requireKnownField(order.field(), sourceFields, "SELECT");
                }
                String outputName = field.outputName();
                if (!seenOutputNames.add(outputName)) {
                    throw validation(SqlLikeErrorCodes.VALIDATION_DUPLICATE_SELECT_OUTPUT,
                            "Duplicate SELECT output name '" + outputName + "'");
                }
                requireProjectionField(outputName, projectionFields, dynamicProjection);
                continue;
            }
            if (!field.metricField()) {
                requireKnownField(field.field(), sourceFields, "SELECT");
            }
            if (field.timeBucketField()) {
                requireTimeBucketField(field.field(), sourceFieldTypes);
                if (!field.aliased()) {
                    throw validation(SqlLikeErrorCodes.VALIDATION_TIME_BUCKET,
                            "bucket(dateField,'granularity'[, 'zone'[, 'weekStart']]) requires AS alias");
                }
            }
            String outputName = field.outputName();
            if (!seenOutputNames.add(outputName)) {
                throw validation(SqlLikeErrorCodes.VALIDATION_DUPLICATE_SELECT_OUTPUT,
                        "Duplicate SELECT output name '" + outputName + "'");
            }
            requireProjectionField(outputName, projectionFields, dynamicProjection);
        }
    }

    private static void requireProjectionField(String outputName,
                                               Set<String> projectionFields,
                                               boolean dynamicProjection) {
        if (dynamicProjection) {
            return;
        }
        requireKnownField(outputName, projectionFields, "SELECT/projection");
    }

    private static void validateFilters(List<FilterAst> filters,
                                        Set<String> allowedFields,
                                        Class<?> sourceClass,
                                        Map<String, List<?>> joinSources,
                                        ComputedFieldRegistry computedFieldRegistry) {
        for (FilterAst filter : filters) {
            switch (filter.value()) {
                case null -> {
                }
                case SubqueryValueAst subqueryValueAst ->
                        validateInSubquery(filter, subqueryValueAst, sourceClass, joinSources, computedFieldRegistry);
                case ExistsSubqueryValueAst existsSubqueryValueAst -> {
                    validateExistsSubquery(filter, existsSubqueryValueAst, sourceClass, joinSources, computedFieldRegistry);
                    continue;
                }
                default -> {
                }
            }
            if (SqlExpressionEvaluator.looksLikeExpression(filter.field())) {
                ensureExpressionClauseSupported(filter, "WHERE");
                validateExpressionIdentifiers(filter.field(), allowedFields, "WHERE");
                continue;
            }
            requireKnownField(filter.field(), allowedFields, "WHERE");
        }
    }

    private static void validateOrders(QueryAst ast, Set<String> allowedFields, Set<String> sourceFields) {
        boolean aggregateShape = ast.hasAggregation() || !ast.groupByFields().isEmpty();
        for (OrderAst order : ast.orders()) {
            if (aggregateShape) {
                validateAggregateOrderReference(order.field(), allowedFields, sourceFields);
                continue;
            }
            requireKnownField(order.field(), allowedFields, "ORDER BY");
        }
    }

    private static void validateAggregateOrderReference(String reference,
                                                        Set<String> allowedFields,
                                                        Set<String> sourceFields) {
        if (allowedFields.contains(reference)) {
            return;
        }
        ParsedAggregateExpression aggregateExpression = AggregateExpressionSupport.parse(reference);
        if (aggregateExpression != null) {
            validateAggregateOrderFunction(reference, aggregateExpression, sourceFields);
            return;
        }
        if (SqlExpressionEvaluator.looksLikeExpression(reference)) {
            validateAggregateOrderExpression(reference, allowedFields, sourceFields);
            return;
        }
        if (sourceFields.contains(reference)) {
            throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                    formatInvalidAggregateOrderReferenceMessage(reference, allowedFields));
        }
        requireKnownField(reference, allowedFields, "ORDER BY");
    }

    private static void validateAggregateOrderFunction(String reference,
                                                       ParsedAggregateExpression aggregateExpression,
                                                       Set<String> sourceFields) {
        if (aggregateExpression.countAll()) {
            if (aggregateExpression.metric() == Metric.COUNT) {
                return;
            }
            throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                    "Invalid aggregate ORDER BY expression '" + reference + "': only COUNT(*) supports '*'");
        }
        if (sourceFields.contains(aggregateExpression.field())) {
            return;
        }
        throw validation(SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD,
                formatUnknownAggregateOrderArgumentMessage(reference, aggregateExpression.field(), sourceFields));
    }

    private static void validateAggregateOrderExpression(String expression,
                                                         Set<String> allowedFields,
                                                         Set<String> sourceFields) {
        Set<String> identifiers = collectExpressionIdentifiers(expression);
        for (String identifier : identifiers) {
            if (allowedFields.contains(identifier)) {
                continue;
            }
            if (sourceFields.contains(identifier)) {
                throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                        "Invalid aggregate ORDER BY expression '" + expression
                                + "': expected grouped field, aggregate output, or aggregate expression");
            }
            throw validation(SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD,
                    formatUnknownFieldMessage(identifier, allowedFields, "ORDER BY"));
        }
        throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                "Invalid aggregate ORDER BY expression '" + expression
                        + "': expected grouped field, aggregate output, or aggregate expression");
    }

    private static void validateHaving(QueryAst ast,
                                       Set<String> sourceFields,
                                       Class<?> sourceClass,
                                       Map<String, List<?>> joinSources,
                                       ComputedFieldRegistry computedFieldRegistry) {
        List<FilterAst> having = ast.havingFilters();
        if (having.isEmpty()) {
            return;
        }
        if (!ast.hasAggregation() && ast.groupByFields().isEmpty()) {
            throw validation(SqlLikeErrorCodes.VALIDATION_HAVING_REFERENCE,
                    "HAVING requires GROUP BY or aggregate SELECT output");
        }

        LinkedHashSet<String> groupedFields = new LinkedHashSet<>(ast.groupByFields());
        LinkedHashSet<String> aggregateOutputs = new LinkedHashSet<>();
        if (ast.select() != null) {
            for (SelectFieldAst field : ast.select().fields()) {
                if (field.metricField()) {
                    aggregateOutputs.add(field.outputName());
                }
            }
        }
        Set<String> ambiguous = new LinkedHashSet<>(groupedFields);
        ambiguous.retainAll(aggregateOutputs);

        for (FilterAst filter : having) {
            if (hasSubqueryValue(filter.value())) {
                throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                        "Subqueries are only supported in WHERE IN (...) or WHERE EXISTS (...) filters");
            }
            String reference = filter.field();
            if (ambiguous.contains(reference)) {
                throw validation(SqlLikeErrorCodes.VALIDATION_HAVING_REFERENCE,
                        "Ambiguous HAVING reference '" + reference + "'");
            }
            if (groupedFields.contains(reference) || aggregateOutputs.contains(reference)) {
                continue;
            }
            String canonicalExpression = AggregateExpressionSupport.canonicalFromReference(reference, sourceFields);
            if (canonicalExpression != null) {
                // HAVING aggregate expressions are valid even when the aggregate
                // is not part of SELECT output (resolved during SQL-like binding).
                continue;
            }
            if (SqlExpressionEvaluator.looksLikeExpression(reference)) {
                ensureExpressionClauseSupported(filter, "HAVING");
                validateHavingExpression(reference, groupedFields, aggregateOutputs, sourceFields);
                continue;
            }
            if (sourceFields.contains(reference)) {
                throw validation(SqlLikeErrorCodes.VALIDATION_HAVING_REFERENCE,
                        "Invalid HAVING reference '" + reference + "': expected grouped field or aggregate output");
            }
            throw validation(SqlLikeErrorCodes.VALIDATION_HAVING_REFERENCE,
                    "Unknown HAVING reference '" + reference + "'");
        }
    }

    private static void validateQualify(QueryAst ast, Set<String> sourceFields) {
        if (!ast.hasQualifyClause()) {
            return;
        }
        if (ast.hasAggregation() || !ast.groupByFields().isEmpty()) {
            throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                    "QUALIFY is only supported for non-aggregate SQL-like queries");
        }
        SelectAst select = ast.select();
        if (select == null || select.wildcard() || !select.hasWindowFields()) {
            throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                    "QUALIFY requires at least one window SELECT output");
        }
        LinkedHashSet<String> qualifyOutputs = new LinkedHashSet<>();
        for (SelectFieldAst field : select.fields()) {
            if (field.windowField()) {
                qualifyOutputs.add(field.outputName());
            }
        }
        for (FilterAst filter : ast.qualifyFilters()) {
            if (hasSubqueryValue(filter.value())) {
                throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                        "Subqueries are only supported in WHERE IN (...) or WHERE EXISTS (...) filters");
            }
            if (SqlExpressionEvaluator.looksLikeExpression(filter.field())) {
                ensureExpressionClauseSupported(filter, "QUALIFY");
                validateExpressionIdentifiers(filter.field(), qualifyOutputs, "QUALIFY");
                continue;
            }
            if (!qualifyOutputs.contains(filter.field())) {
                throw validation(SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD,
                        formatUnknownFieldMessage(filter.field(), qualifyOutputs, "QUALIFY"));
            }
        }
    }

    private static void validateExistsSubquery(FilterAst filter,
                                               ExistsSubqueryValueAst existsSubqueryValueAst,
                                               Class<?> sourceClass,
                                               Map<String, List<?>> joinSources,
                                               ComputedFieldRegistry computedFieldRegistry) {
        if (filter.clause() != Clauses.EQUAL) {
            throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                    "EXISTS subquery predicates are only supported as WHERE EXISTS/WHERE NOT EXISTS");
        }
        QueryAst subquery = existsSubqueryValueAst.query();
        SelectAst select = subquery.select();
        if (select == null) {
            throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                    "EXISTS subqueries require SELECT");
        }
        Class<?> subquerySourceClass = resolveSubquerySourceClass(sourceClass, joinSources, select);
        validateForFilter(subquery, subquerySourceClass, QueryRow.class,
                joinSources, false, computedFieldRegistry);
    }

    private static void validateInSubquery(FilterAst filter,
                                           SubqueryValueAst subqueryValueAst,
                                           Class<?> sourceClass,
                                           Map<String, List<?>> joinSources,
                                           ComputedFieldRegistry computedFieldRegistry) {
        if (filter.clause() != Clauses.IN) {
            throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                    "Subquery values are only supported with IN");
        }
        QueryAst subquery = subqueryValueAst.query();
        SelectAst select = subquery.select();
        if (select == null || select.wildcard() || select.fields().size() != 1) {
            throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                    "Subqueries must select exactly one explicit field");
        }
        SelectFieldAst selectedField = select.fields().get(0);
        if (selectedField.timeBucketField()
                || selectedField.computedField()
                || selectedField.windowField()) {
            throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                    "Subqueries support only simple field or aggregate SELECTs in v1");
        }

        Class<?> subquerySourceClass = resolveSubquerySourceClass(sourceClass, joinSources, select);
        boolean groupedOnly = !subquery.hasAggregation() && !subquery.groupByFields().isEmpty();
        if (groupedOnly) {
            validateGroupedOnlySubquery(subquery, subquerySourceClass, joinSources, computedFieldRegistry);
        } else {
            validateForFilter(subquery, subquerySourceClass, QueryRow.class,
                    joinSources, false, computedFieldRegistry);
        }
    }

    private static void validateGroupedOnlySubquery(QueryAst subquery,
                                                    Class<?> sourceClass,
                                                    Map<String, List<?>> joinSources,
                                                    ComputedFieldRegistry computedFieldRegistry) {
        SqlLikeJoinResolution.Plan joinPlan = SqlLikeJoinResolution.resolve(subquery, sourceClass, joinSources);
        QueryAst normalizedSubquery = SqlLikeJoinResolution.canonicalize(subquery, joinPlan);
        normalizedSubquery = normalizeAggregationAliases(normalizedSubquery);
        Map<String, Class<?>> sourceFieldTypes = joinPlan.isEmpty()
                ? collectFieldTypes(sourceClass)
                : joinPlan.mergedFieldTypes();
        Set<String> sourceFields = ComputedFieldSupport
                .augmentFieldTypes(sourceFieldTypes, computedFieldRegistry)
                .keySet();
        for (String group : normalizedSubquery.groupByFields()) {
            requireKnownField(group, sourceFields, "GROUP BY");
        }
        SelectFieldAst selectedField = normalizedSubquery.select().fields().get(0);
        String fieldName = selectedField.field();
        if (!normalizedSubquery.groupByFields().contains(fieldName)
                && !normalizedSubquery.groupByFields().contains(selectedField.outputName())) {
            throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                    "Subquery grouped field '" + fieldName + "' must be present in GROUP BY");
        }
        validateFilters(normalizedSubquery.filters(), sourceFields, sourceClass,
                joinSources, computedFieldRegistry);
        validateHaving(normalizedSubquery, sourceFields, sourceClass,
                joinSources, computedFieldRegistry);
    }

    private static Class<?> resolveSubquerySourceClass(Class<?> sourceClass,
                                                       Map<String, List<?>> joinSources,
                                                       SelectAst select) {
        if (select.sourceName() == null) {
            return sourceClass;
        }
        List<?> sourceRows = joinSources.get(select.sourceName());
        if (sourceRows == null) {
            throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                    SqlLikeSourceBindingMessages.missingSubquerySourceBinding(select.sourceName(), joinSources.keySet()));
        }
        return inferListElementClass(sourceRows);
    }

    private static void validateExpressionIdentifiers(String expression,
                                                      Set<String> allowedIdentifiers,
                                                      String clauseName) {
        Set<String> identifiers = collectExpressionIdentifiers(expression);
        for (String identifier : identifiers) {
            if (!allowedIdentifiers.contains(identifier)) {
                throw validation(SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD,
                        "Unknown field '" + identifier + "' in " + clauseName + " clause");
            }
        }
    }

    private static void validateHavingExpression(String expression,
                                                 Set<String> groupedFields,
                                                 Set<String> aggregateOutputs,
                                                 Set<String> sourceFields) {
        Set<String> allowed = new LinkedHashSet<>(groupedFields);
        allowed.addAll(aggregateOutputs);
        Set<String> identifiers = collectExpressionIdentifiers(expression);
        for (String identifier : identifiers) {
            if (allowed.contains(identifier)) {
                continue;
            }
            if (sourceFields.contains(identifier)) {
                throw validation(SqlLikeErrorCodes.VALIDATION_HAVING_REFERENCE,
                        "Invalid HAVING reference '" + identifier + "': expected grouped field or aggregate output");
            }
            throw validation(SqlLikeErrorCodes.VALIDATION_HAVING_REFERENCE,
                    "Unknown HAVING reference '" + identifier + "'");
        }
    }

    private static void ensureExpressionClauseSupported(FilterAst filter, String clauseName) {
        if (filter.clause() == Clauses.CONTAINS
                || filter.clause() == Clauses.MATCHES) {
            throw validation(SqlLikeErrorCodes.VALIDATION_EXPRESSION_REFERENCE,
                    "Expression references in " + clauseName + " only support numeric comparison operators");
        }
    }

    private static void validateAggregationSemantics(QueryAst ast,
                                                     Set<String> sourceFields,
                                                     Map<String, Class<?>> sourceFieldTypes) {
        List<String> groupBy = ast.groupByFields();
        boolean hasAggregation = ast.hasAggregation();
        SelectAst select = ast.select();
        Map<String, SelectFieldAst> selectByOutputName = indexSelectByOutputName(select);

        for (String grouped : groupBy) {
            if (!sourceFields.contains(grouped) && !selectByOutputName.containsKey(grouped)) {
                throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                        "Unknown GROUP BY field '" + grouped + "'");
            }
        }

        if (!groupBy.isEmpty() && select == null) {
            throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                    "GROUP BY requires a SELECT clause");
        }
        if (!groupBy.isEmpty() && !hasAggregation) {
            throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                    "GROUP BY requires at least one aggregate function");
        }
        if (!hasAggregation) {
            return;
        }
        if (select == null || select.wildcard()) {
            throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                    "Aggregate queries require explicit SELECT fields");
        }

        for (SelectFieldAst field : select.fields()) {
            if (field.windowField()) {
                throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                        "Window SELECT expressions are only supported for non-aggregate queries");
            }
            if (field.computedField()) {
                throw validation(SqlLikeErrorCodes.VALIDATION_COMPUTED_SELECT,
                        "Computed SELECT expressions are only supported for non-aggregate queries");
            }
            if (field.metricField()) {
                if (field.countAll()) {
                    if (!"*".equals(field.field())) {
                        throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                                "COUNT(*) must use '*' argument");
                    }
                } else {
                    requireKnownField(field.field(), sourceFields, "SELECT");
                }
                continue;
            }
            if (field.timeBucketField()) {
                requireTimeBucketField(field.field(), sourceFieldTypes);
                String bucketOutput = field.outputName();
                if (!groupBy.contains(bucketOutput)) {
                    throw validation(SqlLikeErrorCodes.VALIDATION_TIME_BUCKET,
                            "Time bucket field '" + bucketOutput + "' must be present in GROUP BY");
                }
                continue;
            }
            if (!groupBy.contains(field.field()) && !groupBy.contains(field.outputName())) {
                throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                        "Non-aggregated SELECT field '" + field.field() + "' must be present in GROUP BY");
            }
        }
    }

    public static QueryAst normalizeAggregationAliases(QueryAst ast) {
        SelectAst select = ast.select();
        if (select == null || select.wildcard()) {
            return ast;
        }
        LinkedHashMap<String, String> groupedAliases = new LinkedHashMap<>();
        for (SelectFieldAst field : select.fields()) {
            if (field.metricField()
                    || field.timeBucketField()
                    || field.computedField()
                    || field.windowField()
                    || !field.aliased()) {
                continue;
            }
            groupedAliases.put(field.outputName(), field.field());
        }
        if (groupedAliases.isEmpty()) {
            return ast;
        }

        ArrayList<String> normalizedGroups = new ArrayList<>(ast.groupByFields().size());
        for (String group : ast.groupByFields()) {
            normalizedGroups.add(groupedAliases.getOrDefault(group, group));
        }

        ArrayList<FilterAst> normalizedHaving = new ArrayList<>(ast.havingFilters().size());
        for (FilterAst filter : ast.havingFilters()) {
            String field = SqlExpressionEvaluator.looksLikeExpression(filter.field())
                    ? SqlExpressionEvaluator.rewriteIdentifiers(filter.field(), identifier -> groupedAliases.getOrDefault(identifier, identifier))
                    : groupedAliases.getOrDefault(filter.field(), filter.field());
            normalizedHaving.add(new FilterAst(field, filter.clause(), filter.value(), filter.separator()));
        }

        FilterExpressionAst normalizedHavingExpression = normalizeGroupedAliasExpression(ast.havingExpression(), groupedAliases);

        ArrayList<OrderAst> normalizedOrders = new ArrayList<>(ast.orders().size());
        for (OrderAst order : ast.orders()) {
            normalizedOrders.add(new OrderAst(groupedAliases.getOrDefault(order.field(), order.field()), order.sort()));
        }

        return new QueryAst(
                ast.select(),
                ast.joins(),
                ast.filters(),
                ast.whereExpression(),
                normalizedGroups,
                normalizedHaving,
                normalizedHavingExpression,
                ast.qualifyFilters(),
                ast.qualifyExpression(),
                normalizedOrders,
                ast.limit(),
                ast.limitParameter(),
                ast.offset(),
                ast.offsetParameter()
        );
    }

    private static QueryAst normalizeQualifyWindowReferences(QueryAst ast) {
        if (!ast.hasQualifyClause()) {
            return ast;
        }
        SelectAst select = ast.select();
        if (select == null || select.wildcard()) {
            return ast;
        }
        LinkedHashMap<String, String> windowAliases = new LinkedHashMap<>();
        for (SelectFieldAst field : select.fields()) {
            if (!field.windowField()) {
                continue;
            }
            windowAliases.put(canonicalWindowExpression(field.field()), field.outputName());
        }
        if (windowAliases.isEmpty()) {
            return ast;
        }
        ArrayList<FilterAst> normalizedQualify = new ArrayList<>(ast.qualifyFilters().size());
        for (FilterAst filter : ast.qualifyFilters()) {
            String field = windowAliases.getOrDefault(canonicalWindowExpression(filter.field()), filter.field());
            normalizedQualify.add(new FilterAst(field, filter.clause(), filter.value(), filter.separator()));
        }
        FilterExpressionAst normalizedQualifyExpression =
                normalizeWindowAliasExpression(ast.qualifyExpression(), windowAliases);
        return new QueryAst(
                ast.select(),
                ast.joins(),
                ast.filters(),
                ast.whereExpression(),
                ast.groupByFields(),
                ast.havingFilters(),
                ast.havingExpression(),
                normalizedQualify,
                normalizedQualifyExpression,
                ast.orders(),
                ast.limit(),
                ast.limitParameter(),
                ast.offset(),
                ast.offsetParameter()
        );
    }

    private static FilterExpressionAst normalizeWindowAliasExpression(FilterExpressionAst expression,
                                                                     Map<String, String> windowAliases) {
        if (expression == null || windowAliases.isEmpty()) {
            return expression;
        }
        return switch (expression) {
            case FilterPredicateAst predicateAst -> {
                FilterAst filter = predicateAst.filter();
                String field = windowAliases.getOrDefault(canonicalWindowExpression(filter.field()), filter.field());
                yield new FilterPredicateAst(new FilterAst(field, filter.clause(), filter.value(), filter.separator()));
            }
            case FilterBinaryAst binary -> new FilterBinaryAst(
                    normalizeWindowAliasExpression(binary.left(), windowAliases),
                    normalizeWindowAliasExpression(binary.right(), windowAliases),
                    binary.operator()
            );
        };
    }

    private static FilterExpressionAst normalizeGroupedAliasExpression(FilterExpressionAst expression,
                                                                      Map<String, String> groupedAliases) {
        if (expression == null || groupedAliases.isEmpty()) {
            return expression;
        }
        return switch (expression) {
            case FilterPredicateAst predicateAst -> {
                FilterAst filter = predicateAst.filter();
                String field = SqlExpressionEvaluator.looksLikeExpression(filter.field())
                        ? SqlExpressionEvaluator.rewriteIdentifiers(
                        filter.field(),
                        identifier -> groupedAliases.getOrDefault(identifier, identifier)
                )
                        : groupedAliases.getOrDefault(filter.field(), filter.field());
                yield new FilterPredicateAst(
                        new FilterAst(field, filter.clause(), filter.value(), filter.separator())
                );
            }
            case FilterBinaryAst binary -> new FilterBinaryAst(
                    normalizeGroupedAliasExpression(binary.left(), groupedAliases),
                    normalizeGroupedAliasExpression(binary.right(), groupedAliases),
                    binary.operator()
            );
        };
    }

    private static boolean hasSubqueryValue(Object value) {
        return switch (value) {
            case null -> false;
            case SubqueryValueAst _ -> true;
            case ExistsSubqueryValueAst _ -> true;
            default -> false;
        };
    }

    private static Set<String> resolveAllowedOrderFields(QueryAst ast, Set<String> sourceFields) {
        if (!ast.hasAggregation() && ast.groupByFields().isEmpty()) {
            LinkedHashSet<String> allowed = new LinkedHashSet<>(sourceFields);
            if (ast.select() != null) {
                for (SelectFieldAst field : ast.select().fields()) {
                    if (field.windowField()) {
                        allowed.add(field.outputName());
                    }
                }
            }
            return allowed;
        }
        LinkedHashSet<String> allowed = new LinkedHashSet<>(ast.groupByFields());
        if (ast.select() != null) {
            for (SelectFieldAst field : ast.select().fields()) {
                if (field.metricField() || field.timeBucketField()) {
                    allowed.add(field.outputName());
                }
            }
        }
        return allowed;
    }

    private static Map<String, SelectFieldAst> indexSelectByOutputName(SelectAst select) {
        Map<String, SelectFieldAst> map = new HashMap<>();
        if (select == null) {
            return map;
        }
        for (SelectFieldAst field : select.fields()) {
            map.put(field.outputName(), field);
        }
        return map;
    }

    private static void requireTimeBucketField(String fieldName, Map<String, Class<?>> fieldTypes) {
        Class<?> type = fieldTypes.get(fieldName);
        if (type == null || !TimeBucketUtil.supportsTimeBucketType(type)) {
            throw validation(SqlLikeErrorCodes.VALIDATION_TIME_BUCKET,
                    "Time bucket requires supported date/time field '" + fieldName + "'");
        }
    }

    private static void requireNumericField(String fieldName,
                                            Map<String, Class<?>> fieldTypes,
                                            String functionName) {
        Class<?> type = fieldTypes.get(fieldName);
        if (type == null || !Number.class.isAssignableFrom(type)) {
            throw validation(SqlLikeErrorCodes.VALIDATION_AGGREGATION_SEMANTICS,
                    "Window function " + functionName + " requires numeric field '" + fieldName + "'");
        }
    }

    private static void requireKnownField(String field, Set<String> allowedFields, String clauseName) {
        if (!allowedFields.contains(field)) {
            throw validation(SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD,
                    formatUnknownFieldMessage(field, allowedFields, clauseName));
        }
    }

    private static Set<String> collectExpressionIdentifiers(String expression) {
        try {
            return SqlExpressionEvaluator.collectIdentifiers(expression);
        } catch (IllegalArgumentException ex) {
            throw validation(SqlLikeErrorCodes.VALIDATION_EXPRESSION_REFERENCE, ex.getMessage());
        }
    }

    private static String formatUnknownFieldMessage(String field, Set<String> allowedFields, String clauseName) {
        return SqlLikeFieldMessages.unknownField(field, clauseName, allowedFields);
    }

    private static String formatInvalidAggregateOrderReferenceMessage(String reference, Set<String> allowedFields) {
        return "Invalid aggregate ORDER BY reference '"
                + reference
                + "': expected grouped field, aggregate output, or aggregate expression. Allowed fields: "
                + new TreeSet<>(allowedFields);
    }

    private static String formatUnknownAggregateOrderArgumentMessage(String expression,
                                                                    String argument,
                                                                    Set<String> sourceFields) {
        List<String> suggestions = NameSuggestions.suggest(argument, sourceFields);
        return "Unknown field '" + argument + "' in ORDER BY aggregate expression '" + expression + "'."
                + NameSuggestions.formatFragment(suggestions)
                + " Allowed source fields: " + new TreeSet<>(sourceFields);
    }

    private static String canonicalWindowExpression(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean isAggregateWindowFunction(String functionName) {
        if (functionName == null) {
            return false;
        }
        return !"ROW_NUMBER".equalsIgnoreCase(functionName)
                && !"RANK".equalsIgnoreCase(functionName)
                && !"DENSE_RANK".equalsIgnoreCase(functionName);
    }

    private static boolean requiresNumericWindowFunction(String functionName) {
        return "SUM".equalsIgnoreCase(functionName)
                || "AVG".equalsIgnoreCase(functionName)
                || "MIN".equalsIgnoreCase(functionName)
                || "MAX".equalsIgnoreCase(functionName);
    }

    static Set<String> collectFields(Class<?> root) {
        if (root == null) {
            throw new IllegalArgumentException("projectionClass must not be null");
        }
        return new LinkedHashSet<>(ReflectionUtil.collectQueryableFieldNames(root));
    }

    static Map<String, Class<?>> collectFieldTypes(Class<?> root) {
        if (root == null) {
            throw new IllegalArgumentException("projectionClass must not be null");
        }
        return new LinkedHashMap<>(ReflectionUtil.collectQueryableFieldTypes(root));
    }

    static IllegalArgumentException validation(String code, String message) {
        return SqlLikeErrors.argument(code, message);
    }
}
