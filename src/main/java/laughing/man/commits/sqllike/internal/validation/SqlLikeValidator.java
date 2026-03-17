package laughing.man.commits.sqllike.internal.validation;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.computed.internal.ComputedFieldSupport;
import laughing.man.commits.enums.Clauses;
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
import laughing.man.commits.sqllike.internal.aggregate.AggregateExpressionSupport;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
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

    private static final int MAX_SUGGESTIONS = 3;

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
        Map<String, Class<?>> queryableFieldTypes = joinPlan.isEmpty()
                ? ComputedFieldSupport.augmentFieldTypes(sourceFieldTypes, computedFieldRegistry)
                : ComputedFieldSupport.augmentFieldTypes(joinPlan.mergedFieldTypes(), computedFieldRegistry);
        Set<String> queryableSourceFields = new LinkedHashSet<>(queryableFieldTypes.keySet());
        Set<String> projectionFields = collectFields(projectionClass);
        validateAggregationSemantics(normalizedAst, queryableSourceFields, queryableFieldTypes);
        validateSelect(normalizedAst, queryableSourceFields, queryableFieldTypes, projectionFields);
        validateFilters(normalizedAst.filters(), queryableSourceFields, sourceClass, joinSources, computedFieldRegistry);
        validateHaving(normalizedAst, queryableSourceFields, sourceClass, joinSources, computedFieldRegistry);
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
                                       Set<String> projectionFields) {
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
                requireKnownField(outputName, projectionFields, "SELECT/projection");
                continue;
            }
            if (!field.metricField()) {
                requireKnownField(field.field(), sourceFields, "SELECT");
            }
            if (field.timeBucketField()) {
                requireDateField(field.field(), sourceFieldTypes);
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
            requireKnownField(outputName, projectionFields, "SELECT/projection");
        }
    }

    private static void validateFilters(List<FilterAst> filters,
                                        Set<String> allowedFields,
                                        Class<?> sourceClass,
                                        Map<String, List<?>> joinSources,
                                        ComputedFieldRegistry computedFieldRegistry) {
        for (FilterAst filter : filters) {
            if (filter.value() instanceof SubqueryValueAst subqueryValueAst) {
                validateInSubquery(filter, subqueryValueAst, sourceClass, joinSources, computedFieldRegistry);
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
        for (OrderAst order : ast.orders()) {
            if ((ast.hasAggregation() || !ast.groupByFields().isEmpty())
                    && AggregateExpressionSupport.parse(order.field()) != null) {
                AggregateExpressionSupport.canonicalFromReference(order.field(), sourceFields);
                continue;
            }
            requireKnownField(order.field(), allowedFields, "ORDER BY");
        }
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
            if (filter.value() instanceof SubqueryValueAst) {
                throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                        "Subqueries are only supported in WHERE IN (...) filters");
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
        if (subquery.hasJoins()) {
            throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                    "Subqueries do not support JOIN clauses in v1");
        }
        if (subquery.hasAggregation()
                || !subquery.groupByFields().isEmpty()
                || !subquery.havingFilters().isEmpty()) {
            throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                    "Subqueries support only non-aggregate SELECT filters in v1");
        }
        SelectAst select = subquery.select();
        if (select == null || select.wildcard() || select.fields().size() != 1) {
            throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                    "Subqueries must select exactly one explicit field");
        }
        SelectFieldAst selectedField = select.fields().get(0);
        if (selectedField.metricField() || selectedField.timeBucketField() || selectedField.computedField()) {
            throw validation(SqlLikeErrorCodes.VALIDATION_SUBQUERY,
                    "Subqueries support only simple field SELECTs in v1");
        }

        Class<?> subquerySourceClass = resolveSubquerySourceClass(sourceClass, joinSources, select);
        validateForFilter(subquery, subquerySourceClass, subquerySourceClass, java.util.Collections.emptyMap(), false, computedFieldRegistry);
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
                    "Missing subquery source binding for '" + select.sourceName() + "'");
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
                requireDateField(field.field(), sourceFieldTypes);
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

    private static QueryAst normalizeAggregationAliases(QueryAst ast) {
        SelectAst select = ast.select();
        if (select == null || select.wildcard()) {
            return ast;
        }
        LinkedHashMap<String, String> groupedAliases = new LinkedHashMap<>();
        for (SelectFieldAst field : select.fields()) {
            if (field.metricField() || field.timeBucketField() || field.computedField() || !field.aliased()) {
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
                normalizedOrders,
                ast.limit()
        );
    }

    private static FilterExpressionAst normalizeGroupedAliasExpression(FilterExpressionAst expression,
                                                                      Map<String, String> groupedAliases) {
        if (expression == null || groupedAliases.isEmpty()) {
            return expression;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            FilterAst filter = predicateAst.filter();
            String field = SqlExpressionEvaluator.looksLikeExpression(filter.field())
                    ? SqlExpressionEvaluator.rewriteIdentifiers(filter.field(), identifier -> groupedAliases.getOrDefault(identifier, identifier))
                    : groupedAliases.getOrDefault(filter.field(), filter.field());
            return new FilterPredicateAst(
                    new FilterAst(field, filter.clause(), filter.value(), filter.separator())
            );
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        return new FilterBinaryAst(
                normalizeGroupedAliasExpression(binary.left(), groupedAliases),
                normalizeGroupedAliasExpression(binary.right(), groupedAliases),
                binary.operator()
        );
    }

    private static Set<String> resolveAllowedOrderFields(QueryAst ast, Set<String> sourceFields) {
        if (!ast.hasAggregation() && ast.groupByFields().isEmpty()) {
            return sourceFields;
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

    private static void requireDateField(String fieldName, Map<String, Class<?>> fieldTypes) {
        Class<?> type = fieldTypes.get(fieldName);
        if (type == null || !Date.class.isAssignableFrom(type)) {
            throw validation(SqlLikeErrorCodes.VALIDATION_TIME_BUCKET,
                    "Time bucket requires date field '" + fieldName + "'");
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
        StringBuilder message = new StringBuilder()
                .append("Unknown field '")
                .append(field)
                .append("' in ")
                .append(clauseName)
                .append(" clause.");
        List<String> suggestions = suggestFields(field, allowedFields);
        if (!suggestions.isEmpty()) {
            if (suggestions.size() == 1) {
                message.append(" Did you mean '").append(suggestions.get(0)).append("'?");
            } else {
                message.append(" Did you mean one of ").append(suggestions).append("?");
            }
        }
        message.append(" Allowed fields: ").append(new TreeSet<>(allowedFields));
        return message.toString();
    }

    private static List<String> suggestFields(String unknownField, Set<String> allowedFields) {
        if (unknownField == null || unknownField.isBlank() || allowedFields.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String normalizedUnknown = normalizeIdentifier(unknownField);
        int threshold = 2;
        List<FieldSuggestion> ranked = new ArrayList<>();
        for (String allowedField : allowedFields) {
            String normalizedAllowed = normalizeIdentifier(allowedField);
            int distance = levenshteinDistance(normalizedUnknown, normalizedAllowed);
            boolean prefixMatch = normalizedAllowed.startsWith(normalizedUnknown)
                    || normalizedUnknown.startsWith(normalizedAllowed);
            if (distance <= threshold || prefixMatch) {
                ranked.add(new FieldSuggestion(allowedField, distance));
            }
        }
        ranked.sort(Comparator
                .comparingInt(FieldSuggestion::distance)
                .thenComparing(FieldSuggestion::name));
        List<String> suggestions = new ArrayList<>();
        for (int i = 0; i < ranked.size() && i < MAX_SUGGESTIONS; i++) {
            suggestions.add(ranked.get(i).name());
        }
        return suggestions;
    }

    private static String normalizeIdentifier(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static int levenshteinDistance(String left, String right) {
        int leftLength = left.length();
        int rightLength = right.length();
        if (leftLength == 0) {
            return rightLength;
        }
        if (rightLength == 0) {
            return leftLength;
        }
        int[] previous = new int[rightLength + 1];
        int[] current = new int[rightLength + 1];
        for (int j = 0; j <= rightLength; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= leftLength; i++) {
            current[0] = i;
            char leftChar = left.charAt(i - 1);
            for (int j = 1; j <= rightLength; j++) {
                int cost = leftChar == right.charAt(j - 1) ? 0 : 1;
                int deletion = previous[j] + 1;
                int insertion = current[j - 1] + 1;
                int substitution = previous[j - 1] + cost;
                current[j] = Math.min(Math.min(deletion, insertion), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[rightLength];
    }

    private static final class FieldSuggestion {
        private final String name;
        private final int distance;

        private FieldSuggestion(String name, int distance) {
            this.name = name;
            this.distance = distance;
        }

        private String name() {
            return name;
        }

        private int distance() {
            return distance;
        }
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

