package laughing.man.commits.sqllike.internal.diagnostics;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.internal.NameSuggestions;
import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.QueryDiagnosticsError;
import laughing.man.commits.sqllike.QueryExposurePolicy;
import laughing.man.commits.sqllike.SqlLikeLintWarning;
import laughing.man.commits.sqllike.ast.ExistsSubqueryValueAst;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.JoinAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeSourceBindingMessages;
import laughing.man.commits.sqllike.internal.lint.SqlLikeLintSupport;
import laughing.man.commits.sqllike.internal.params.SqlLikeParameterSupport;
import laughing.man.commits.sqllike.internal.validation.SqlLikeValidator;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Internal helpers for building {@link QueryDiagnostics} from SQL-like AST metadata.
 */
public final class SqlLikeDiagnosticsSupport {

    private SqlLikeDiagnosticsSupport() {
    }

    public static QueryDiagnostics buildFromAst(QueryAst ast, Set<String> suppressedLintCodes) {
        List<SqlLikeLintWarning> lintWarnings = SqlLikeLintSupport.warnings(ast, suppressedLintCodes);
        List<String> requiredParams = new ArrayList<>(SqlLikeParameterSupport.collectParameterNames(ast));
        List<String> referencedFields = collectReferencedFields(ast);
        List<String> outputFields = collectOutputFields(ast);
        List<String> joinSourceNames = collectJoinSources(ast);
        boolean subqueries = hasSubqueries(ast);
        return new QueryDiagnostics(
                true,
                Collections.emptyList(),
                lintWarnings,
                requiredParams,
                referencedFields,
                outputFields,
                joinSourceNames,
                subqueries
        );
    }

    public static QueryDiagnostics buildWithValidation(QueryAst ast,
                                                       Set<String> suppressedLintCodes,
                                                       Class<?> sourceClass,
                                                       Class<?> projectionClass,
                                                       Map<String, List<?>> joinSources,
                                                       ComputedFieldRegistry computedFieldRegistry) {
        return buildWithValidation(
                ast,
                suppressedLintCodes,
                sourceClass,
                projectionClass,
                joinSources,
                computedFieldRegistry,
                QueryExposurePolicy.unrestricted());
    }

    public static QueryDiagnostics buildWithValidation(QueryAst ast,
                                                       Set<String> suppressedLintCodes,
                                                       Class<?> sourceClass,
                                                       Class<?> projectionClass,
                                                       Map<String, List<?>> joinSources,
                                                       ComputedFieldRegistry computedFieldRegistry,
                                                       QueryExposurePolicy exposurePolicy) {
        QueryExposurePolicy policy = effectivePolicy(exposurePolicy);
        List<SqlLikeLintWarning> lintWarnings = SqlLikeLintSupport.warnings(ast, suppressedLintCodes);
        List<String> requiredParams = new ArrayList<>(SqlLikeParameterSupport.collectParameterNames(ast));
        List<String> referencedFields = collectReferencedFields(ast);
        List<String> outputFields = collectOutputFields(ast);
        List<String> joinSourceNames = collectJoinSources(ast);
        boolean subqueries = hasSubqueries(ast);

        List<QueryDiagnosticsError> errors = collectStructuralErrors(
                ast, sourceClass, joinSources, computedFieldRegistry, policy);
        try {
            SqlLikeValidator.validateForFilter(ast, sourceClass, projectionClass, joinSources, false, computedFieldRegistry);
        } catch (IllegalArgumentException ex) {
            QueryDiagnosticsError error = extractError(ex);
            if (!shouldSkipValidatorError(error, policy)
                    && (!SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD.equals(error.code())
                    || !hasErrorCode(errors, SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD))) {
                addError(errors, error);
            }
        }

        return new QueryDiagnostics(
                errors.isEmpty(),
                errors,
                lintWarnings,
                requiredParams,
                referencedFields,
                outputFields,
                joinSourceNames,
                subqueries
        );
    }

    private static QueryDiagnosticsError extractError(IllegalArgumentException ex) {
        String fullMessage = ex.getMessage();
        if (fullMessage == null) {
            return new QueryDiagnosticsError("EQ-SQL-ERR", "Validation failed");
        }
        int colonIdx = fullMessage.indexOf(": ");
        if (colonIdx > 0) {
            String candidate = fullMessage.substring(0, colonIdx);
            if (candidate.startsWith("EQ-SQL-")) {
                String afterCode = fullMessage.substring(colonIdx + 2);
                int troubleshootIdx = afterCode.lastIndexOf(" Troubleshooting: ");
                String message = troubleshootIdx >= 0 ? afterCode.substring(0, troubleshootIdx) : afterCode;
                return new QueryDiagnosticsError(candidate, message);
            }
        }
        return new QueryDiagnosticsError("EQ-SQL-ERR", fullMessage);
    }

    public static List<String> collectReferencedFields(QueryAst ast) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        collectReferencedFields(ast, fields);
        return List.copyOf(fields);
    }

    private static void collectReferencedFields(QueryAst ast, LinkedHashSet<String> fields) {
        if (ast.select() != null && !ast.select().wildcard()) {
            for (SelectFieldAst field : ast.select().fields()) {
                if (field.computedField()) {
                    continue;
                }
                if (field.windowField()) {
                    if (field.windowValueField() != null && !field.windowCountAll()) {
                        fields.add(field.windowValueField());
                    }
                    fields.addAll(field.windowPartitionFields());
                    for (OrderAst order : field.windowOrderFields()) {
                        fields.add(order.field());
                    }
                    continue;
                }
                if (!field.countAll()) {
                    fields.add(field.field());
                }
            }
        }

        collectFilterFields(ast.filters(), fields);
        collectFilterExpressionFields(ast.whereExpression(), fields);

        fields.addAll(ast.groupByFields());
        collectFilterFields(ast.havingFilters(), fields);
        collectFilterExpressionFields(ast.havingExpression(), fields);
        collectFilterFields(ast.qualifyFilters(), fields);
        collectFilterExpressionFields(ast.qualifyExpression(), fields);

        for (OrderAst order : ast.orders()) {
            fields.add(order.field());
        }

        for (JoinAst join : ast.joins()) {
            fields.add(join.parentField());
            fields.add(join.childField());
        }
    }

    public static List<String> collectOutputFields(QueryAst ast) {
        if (ast.select() == null || ast.select().wildcard()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (SelectFieldAst field : ast.select().fields()) {
            names.add(field.outputName());
        }
        return List.copyOf(names);
    }

    public static List<String> collectJoinSources(QueryAst ast) {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        collectJoinSources(ast, sources, false);
        return List.copyOf(sources);
    }

    private static void collectJoinSources(QueryAst ast, LinkedHashSet<String> sources, boolean includeSelectSource) {
        if (includeSelectSource && ast.select() != null && ast.select().sourceName() != null) {
            sources.add(ast.select().sourceName());
        }
        for (JoinAst join : ast.joins()) {
            sources.add(join.childSource());
        }
        collectJoinSources(ast.filters(), sources);
        collectJoinSources(ast.whereExpression(), sources);
        collectJoinSources(ast.havingFilters(), sources);
        collectJoinSources(ast.havingExpression(), sources);
        collectJoinSources(ast.qualifyFilters(), sources);
        collectJoinSources(ast.qualifyExpression(), sources);
    }

    private static void collectJoinSources(List<FilterAst> filters, LinkedHashSet<String> sources) {
        for (FilterAst filter : filters) {
            collectJoinSources(filter, sources);
        }
    }

    private static void collectJoinSources(FilterExpressionAst expression, LinkedHashSet<String> sources) {
        if (expression == null) {
            return;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            collectJoinSources(predicateAst.filter(), sources);
            return;
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        collectJoinSources(binary.left(), sources);
        collectJoinSources(binary.right(), sources);
    }

    private static void collectJoinSources(FilterAst filter, LinkedHashSet<String> sources) {
        Object value = filter.value();
        if (value instanceof SubqueryValueAst subqueryValueAst) {
            addSubquerySource(sources, subqueryValueAst.query());
            collectJoinSources(subqueryValueAst.query(), sources, true);
        } else if (value instanceof ExistsSubqueryValueAst existsSubqueryValueAst) {
            addSubquerySource(sources, existsSubqueryValueAst.query());
            collectJoinSources(existsSubqueryValueAst.query(), sources, true);
        }
    }

    private static void addSubquerySource(LinkedHashSet<String> sources, QueryAst subquery) {
        if (subquery.select() != null && subquery.select().sourceName() != null) {
            sources.add(subquery.select().sourceName());
        }
    }

    public static boolean hasSubqueries(QueryAst ast) {
        return hasSubqueries(ast.filters())
                || hasSubqueries(ast.whereExpression())
                || hasSubqueries(ast.havingFilters())
                || hasSubqueries(ast.havingExpression())
                || hasSubqueries(ast.qualifyFilters())
                || hasSubqueries(ast.qualifyExpression());
    }

    private static boolean hasSubqueries(List<FilterAst> filters) {
        for (FilterAst filter : filters) {
            Object value = filter.value();
            if (value instanceof SubqueryValueAst) {
                return true;
            }
            if (value instanceof ExistsSubqueryValueAst) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSubqueries(FilterExpressionAst expression) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            return hasSubquery(predicateAst.filter());
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        return hasSubqueries(binary.left()) || hasSubqueries(binary.right());
    }

    private static boolean hasSubquery(FilterAst filter) {
        Object value = filter.value();
        return value instanceof SubqueryValueAst || value instanceof ExistsSubqueryValueAst;
    }

    private static void collectFilterFields(List<FilterAst> filters, LinkedHashSet<String> fields) {
        for (FilterAst filter : filters) {
            collectFilterField(filter, fields);
        }
    }

    private static void collectFilterExpressionFields(FilterExpressionAst expression, LinkedHashSet<String> fields) {
        if (expression == null) {
            return;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            collectFilterField(predicateAst.filter(), fields);
            return;
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        collectFilterExpressionFields(binary.left(), fields);
        collectFilterExpressionFields(binary.right(), fields);
    }

    private static void collectFilterField(FilterAst filter, LinkedHashSet<String> fields) {
        Object value = filter.value();
        if (!(value instanceof ExistsSubqueryValueAst)) {
            fields.add(filter.field());
        }
        if (value instanceof SubqueryValueAst subqueryValueAst) {
            collectReferencedFields(subqueryValueAst.query(), fields);
        } else if (value instanceof ExistsSubqueryValueAst existsSubqueryValueAst) {
            collectReferencedFields(existsSubqueryValueAst.query(), fields);
        }
    }

    private static List<QueryDiagnosticsError> collectStructuralErrors(QueryAst ast,
                                                                       Class<?> sourceClass,
                                                                       Map<String, List<?>> joinSources,
                                                                       ComputedFieldRegistry computedFieldRegistry,
                                                                       QueryExposurePolicy exposurePolicy) {
        ArrayList<QueryDiagnosticsError> errors = new ArrayList<>();
        collectMissingJoinSourceErrors(ast, joinSources, exposurePolicy, errors);
        collectUnknownWhereFieldErrors(ast, sourceClass, joinSources, computedFieldRegistry, exposurePolicy, errors);
        return errors;
    }

    private static void collectMissingJoinSourceErrors(QueryAst ast,
                                                       Map<String, List<?>> joinSources,
                                                       QueryExposurePolicy exposurePolicy,
                                                       List<QueryDiagnosticsError> errors) {
        Set<String> candidateSources = filterAllowedSources(joinSources.keySet(), exposurePolicy);
        for (JoinAst join : ast.joins()) {
            if (!joinSources.containsKey(join.childSource())) {
                addError(errors, new QueryDiagnosticsError(
                        SqlLikeErrorCodes.VALIDATION_MISSING_JOIN_SOURCE,
                        SqlLikeSourceBindingMessages.missingJoinSourceBinding(join.childSource(), candidateSources)
                ));
            }
        }
        collectNestedMissingJoinSourceErrors(ast.filters(), joinSources, exposurePolicy, errors);
        collectNestedMissingJoinSourceErrors(ast.whereExpression(), joinSources, exposurePolicy, errors);
    }

    private static void collectNestedMissingJoinSourceErrors(List<FilterAst> filters,
                                                            Map<String, List<?>> joinSources,
                                                            QueryExposurePolicy exposurePolicy,
                                                            List<QueryDiagnosticsError> errors) {
        for (FilterAst filter : filters) {
            collectNestedMissingJoinSourceErrors(filter, joinSources, exposurePolicy, errors);
        }
    }

    private static void collectNestedMissingJoinSourceErrors(FilterExpressionAst expression,
                                                            Map<String, List<?>> joinSources,
                                                            QueryExposurePolicy exposurePolicy,
                                                            List<QueryDiagnosticsError> errors) {
        if (expression == null) {
            return;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            collectNestedMissingJoinSourceErrors(predicateAst.filter(), joinSources, exposurePolicy, errors);
            return;
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        collectNestedMissingJoinSourceErrors(binary.left(), joinSources, exposurePolicy, errors);
        collectNestedMissingJoinSourceErrors(binary.right(), joinSources, exposurePolicy, errors);
    }

    private static void collectNestedMissingJoinSourceErrors(FilterAst filter,
                                                            Map<String, List<?>> joinSources,
                                                            QueryExposurePolicy exposurePolicy,
                                                            List<QueryDiagnosticsError> errors) {
        Object value = filter.value();
        if (value instanceof SubqueryValueAst subqueryValueAst) {
            collectMissingJoinSourceErrors(subqueryValueAst.query(), joinSources, exposurePolicy, errors);
        } else if (value instanceof ExistsSubqueryValueAst existsSubqueryValueAst) {
            collectMissingJoinSourceErrors(existsSubqueryValueAst.query(), joinSources, exposurePolicy, errors);
        }
    }

    private static void collectUnknownWhereFieldErrors(QueryAst ast,
                                                       Class<?> sourceClass,
                                                       Map<String, List<?>> joinSources,
                                                       ComputedFieldRegistry computedFieldRegistry,
                                                       QueryExposurePolicy exposurePolicy,
                                                       List<QueryDiagnosticsError> errors) {
        LinkedHashSet<String> allowed = allowedFieldNames(sourceClass, computedFieldRegistry);
        String sourceName = ast.select() == null ? null : ast.select().sourceName();
        if (sourceName != null) {
            addQualifiedFields(allowed, sourceName, sourceClass);
        }
        for (Map.Entry<String, List<?>> entry : joinSources.entrySet()) {
            Class<?> joinClass = inferRowClass(entry.getValue());
            if (joinClass != null) {
                allowed.addAll(ReflectionUtil.collectQueryableFieldNames(joinClass));
                addQualifiedFields(allowed, entry.getKey(), joinClass);
            }
        }
        collectUnknownWhereFieldErrors(
                ast,
                sourceClass,
                joinSources,
                computedFieldRegistry,
                exposurePolicy,
                filterAllowedFields(allowed, exposurePolicy),
                errors);
    }

    private static void collectUnknownWhereFieldErrors(QueryAst ast,
                                                       Class<?> sourceClass,
                                                       Map<String, List<?>> joinSources,
                                                       ComputedFieldRegistry computedFieldRegistry,
                                                       QueryExposurePolicy exposurePolicy,
                                                       Set<String> allowed,
                                                       List<QueryDiagnosticsError> errors) {
        collectUnknownWhereFieldErrors(ast.filters(), sourceClass, joinSources, computedFieldRegistry,
                exposurePolicy, allowed, errors);
        collectUnknownWhereFieldErrors(ast.whereExpression(), sourceClass, joinSources, computedFieldRegistry,
                exposurePolicy, allowed, errors);
    }

    private static void collectUnknownWhereFieldErrors(List<FilterAst> filters,
                                                       Class<?> sourceClass,
                                                       Map<String, List<?>> joinSources,
                                                       ComputedFieldRegistry computedFieldRegistry,
                                                       QueryExposurePolicy exposurePolicy,
                                                       Set<String> allowed,
                                                       List<QueryDiagnosticsError> errors) {
        for (FilterAst filter : filters) {
            collectUnknownWhereFieldErrors(filter, sourceClass, joinSources, computedFieldRegistry,
                    exposurePolicy, allowed, errors);
        }
    }

    private static void collectUnknownWhereFieldErrors(FilterExpressionAst expression,
                                                       Class<?> sourceClass,
                                                       Map<String, List<?>> joinSources,
                                                       ComputedFieldRegistry computedFieldRegistry,
                                                       QueryExposurePolicy exposurePolicy,
                                                       Set<String> allowed,
                                                       List<QueryDiagnosticsError> errors) {
        if (expression == null) {
            return;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            collectUnknownWhereFieldErrors(predicateAst.filter(), sourceClass, joinSources, computedFieldRegistry,
                    exposurePolicy, allowed, errors);
            return;
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        collectUnknownWhereFieldErrors(binary.left(), sourceClass, joinSources, computedFieldRegistry,
                exposurePolicy, allowed, errors);
        collectUnknownWhereFieldErrors(binary.right(), sourceClass, joinSources, computedFieldRegistry,
                exposurePolicy, allowed, errors);
    }

    private static void collectUnknownWhereFieldErrors(FilterAst filter,
                                                       Class<?> sourceClass,
                                                       Map<String, List<?>> joinSources,
                                                       ComputedFieldRegistry computedFieldRegistry,
                                                       QueryExposurePolicy exposurePolicy,
                                                       Set<String> allowed,
                                                       List<QueryDiagnosticsError> errors) {
        Object value = filter.value();
        if (!(value instanceof ExistsSubqueryValueAst) && !fieldAllowed(allowed, filter.field())) {
            addError(errors, new QueryDiagnosticsError(
                    SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD,
                    unknownWhereFieldMessage(filter.field(), allowed)
            ));
        }
        if (value instanceof SubqueryValueAst subqueryValueAst) {
            Class<?> subquerySourceClass = subquerySourceClass(
                    sourceName(subqueryValueAst.query()), sourceClass, joinSources
            );
            collectUnknownWhereFieldErrors(
                    subqueryValueAst.query(),
                    subquerySourceClass,
                    joinSources,
                    computedFieldRegistry,
                    exposurePolicy,
                    allowedFieldNames(subquerySourceClass, computedFieldRegistry, exposurePolicy),
                    errors
            );
        } else if (value instanceof ExistsSubqueryValueAst existsSubqueryValueAst) {
            Class<?> subquerySourceClass = subquerySourceClass(
                    sourceName(existsSubqueryValueAst.query()), sourceClass, joinSources
            );
            collectUnknownWhereFieldErrors(
                    existsSubqueryValueAst.query(),
                    subquerySourceClass,
                    joinSources,
                    computedFieldRegistry,
                    exposurePolicy,
                    allowedFieldNames(subquerySourceClass, computedFieldRegistry, exposurePolicy),
                    errors
            );
        }
    }

    private static LinkedHashSet<String> allowedFieldNames(Class<?> sourceClass,
                                                          ComputedFieldRegistry computedFieldRegistry) {
        LinkedHashSet<String> allowed = new LinkedHashSet<>(ReflectionUtil.collectQueryableFieldNames(sourceClass));
        allowed.addAll(computedFieldRegistry.names());
        return allowed;
    }

    private static LinkedHashSet<String> allowedFieldNames(Class<?> sourceClass,
                                                          ComputedFieldRegistry computedFieldRegistry,
                                                          QueryExposurePolicy exposurePolicy) {
        return filterAllowedFields(allowedFieldNames(sourceClass, computedFieldRegistry), exposurePolicy);
    }

    private static void addQualifiedFields(Set<String> allowed, String sourceName, Class<?> sourceClass) {
        for (String field : ReflectionUtil.collectQueryableFieldNames(sourceClass)) {
            allowed.add(sourceName + "." + field);
        }
    }

    private static Class<?> subquerySourceClass(String source,
                                                Class<?> selfSourceClass,
                                                Map<String, List<?>> joinSources) {
        if (source == null) {
            return selfSourceClass;
        }
        List<?> rows = joinSources.get(source);
        Class<?> joinClass = inferRowClass(rows);
        return joinClass == null ? selfSourceClass : joinClass;
    }

    private static String sourceName(QueryAst ast) {
        return ast.select() == null ? null : ast.select().sourceName();
    }

    private static Class<?> inferRowClass(List<?> rows) {
        if (rows == null) {
            return null;
        }
        for (Object row : rows) {
            if (row != null) {
                return row.getClass();
            }
        }
        return null;
    }

    private static String unknownWhereFieldMessage(String field, Set<String> allowedFields) {
        List<String> suggestions = NameSuggestions.suggest(field, allowedFields);
        return "Unknown field '" + field + "' in WHERE clause."
                + NameSuggestions.formatFragment(suggestions)
                + allowedFieldsFragment(allowedFields);
    }

    private static String allowedFieldsFragment(Set<String> allowedFields) {
        return allowedFields.isEmpty() ? "" : " Allowed fields: " + new TreeSet<>(allowedFields);
    }

    private static LinkedHashSet<String> filterAllowedFields(Set<String> candidates,
                                                             QueryExposurePolicy exposurePolicy) {
        QueryExposurePolicy policy = effectivePolicy(exposurePolicy);
        if (!policy.restrictsFields()) {
            return new LinkedHashSet<>(candidates);
        }
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (fieldAllowed(policy.allowedFields(), candidate)) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    private static Set<String> filterAllowedSources(Set<String> candidates, QueryExposurePolicy exposurePolicy) {
        QueryExposurePolicy policy = effectivePolicy(exposurePolicy);
        if (!policy.restrictsSources()) {
            return new LinkedHashSet<>(candidates);
        }
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (policy.allowsSource(candidate)) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    private static boolean fieldAllowed(Set<String> allowedFields, String field) {
        if (allowedFields.contains(field)) {
            return true;
        }
        int dotIndex = field.lastIndexOf('.');
        if (dotIndex >= 0 && allowedFields.contains(field.substring(dotIndex + 1))) {
            return true;
        }
        for (String allowedField : allowedFields) {
            if (allowedField.endsWith("." + field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldSkipValidatorError(QueryDiagnosticsError error, QueryExposurePolicy exposurePolicy) {
        QueryExposurePolicy policy = effectivePolicy(exposurePolicy);
        if (policy.restrictsFields() && SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD.equals(error.code())) {
            return true;
        }
        if (policy.restrictsSources() && SqlLikeErrorCodes.VALIDATION_MISSING_JOIN_SOURCE.equals(error.code())) {
            return true;
        }
        return policy.restrictsSources()
                && SqlLikeErrorCodes.VALIDATION_SUBQUERY.equals(error.code())
                && error.message().startsWith("Missing subquery source binding");
    }

    private static QueryExposurePolicy effectivePolicy(QueryExposurePolicy policy) {
        return policy == null ? QueryExposurePolicy.unrestricted() : policy;
    }

    private static void addError(List<QueryDiagnosticsError> errors, QueryDiagnosticsError error) {
        HashSet<String> seen = new HashSet<>();
        for (QueryDiagnosticsError existing : errors) {
            seen.add(existing.code() + "\n" + existing.message());
        }
        String key = error.code() + "\n" + error.message();
        if (!seen.contains(key)) {
            errors.add(error);
        }
    }

    private static boolean hasErrorCode(List<QueryDiagnosticsError> errors, String code) {
        for (QueryDiagnosticsError error : errors) {
            if (error.code().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
