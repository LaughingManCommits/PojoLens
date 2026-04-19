package laughing.man.commits.sqllike.internal.exposure;

import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.QueryDiagnosticsError;
import laughing.man.commits.sqllike.QueryExposurePolicy;
import laughing.man.commits.sqllike.ast.ExistsSubqueryValueAst;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.JoinAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.sqllike.internal.diagnostics.SqlLikeDiagnosticsSupport;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal policy checks for public query exposure allowlists.
 */
public final class QueryExposurePolicySupport {

    private QueryExposurePolicySupport() {
    }

    public static QueryDiagnostics apply(QueryDiagnostics diagnostics,
                                         QueryAst ast,
                                         QueryExposurePolicy policy,
                                         Class<?> sourceClass,
                                         Map<String, List<?>> joinSources) {
        List<QueryDiagnosticsError> exposureErrors = errors(
                diagnostics, ast, effectivePolicy(policy), sourceClass, joinSources
        );
        if (exposureErrors.isEmpty()) {
            return diagnostics;
        }
        ArrayList<QueryDiagnosticsError> combined = new ArrayList<>(diagnostics.errors());
        combined.addAll(exposureErrors);
        return new QueryDiagnostics(
                false,
                combined,
                diagnostics.lintWarnings(),
                diagnostics.requiredParams(),
                diagnostics.referencedFields(),
                diagnostics.outputFields(),
                diagnostics.joinSources(),
                diagnostics.hasSubqueries()
        );
    }

    public static void requireAllowed(QueryAst ast,
                                      QueryExposurePolicy policy,
                                      Class<?> sourceClass,
                                      Map<String, List<?>> joinSources,
                                      Set<String> suppressedLintCodes) {
        QueryExposurePolicy effectivePolicy = effectivePolicy(policy);
        if (!effectivePolicy.restrictsFields() && !effectivePolicy.restrictsSources()) {
            return;
        }
        QueryDiagnostics diagnostics = SqlLikeDiagnosticsSupport.buildFromAst(ast, suppressedLintCodes);
        List<QueryDiagnosticsError> errors = errors(diagnostics, ast, effectivePolicy, sourceClass, joinSources);
        if (errors.isEmpty()) {
            return;
        }
        QueryDiagnosticsError first = errors.get(0);
        throw SqlLikeErrors.argument(first.code(), first.message());
    }

    private static List<QueryDiagnosticsError> errors(QueryDiagnostics diagnostics,
                                                      QueryAst ast,
                                                      QueryExposurePolicy policy,
                                                      Class<?> sourceClass,
                                                      Map<String, List<?>> joinSources) {
        ArrayList<QueryDiagnosticsError> errors = new ArrayList<>();
        if (policy.restrictsFields()) {
            LinkedHashSet<String> blockedFields = blockedFields(
                    diagnostics, ast, policy, sourceClass, safeJoinSources(joinSources)
            );
            if (!blockedFields.isEmpty()) {
                errors.add(new QueryDiagnosticsError(
                        SqlLikeErrorCodes.EXPOSURE_FIELD_BLOCKED,
                        "Query references field(s) outside exposure policy: " + blockedFields
                ));
            }
        }
        if (policy.restrictsSources()) {
            LinkedHashSet<String> blockedSources = blockedSources(diagnostics, ast, policy);
            if (!blockedSources.isEmpty()) {
                errors.add(new QueryDiagnosticsError(
                        SqlLikeErrorCodes.EXPOSURE_SOURCE_BLOCKED,
                        "Query references source(s) outside exposure policy: " + blockedSources
                ));
            }
        }
        return errors;
    }

    private static LinkedHashSet<String> blockedFields(QueryDiagnostics diagnostics,
                                                       QueryAst ast,
                                                       QueryExposurePolicy policy,
                                                       Class<?> sourceClass,
                                                       Map<String, List<?>> joinSources) {
        LinkedHashSet<String> fields = new LinkedHashSet<>(diagnostics.referencedFields());
        collectExposureFields(ast, sourceClass, joinSources, fields);
        LinkedHashSet<String> blocked = new LinkedHashSet<>();
        for (String field : fields) {
            if (!fieldAllowed(policy.allowedFields(), field)) {
                blocked.add(field);
            }
        }
        return blocked;
    }

    private static LinkedHashSet<String> blockedSources(QueryDiagnostics diagnostics,
                                                        QueryAst ast,
                                                        QueryExposurePolicy policy) {
        LinkedHashSet<String> sources = new LinkedHashSet<>(diagnostics.joinSources());
        collectSourceNames(ast, sources);
        LinkedHashSet<String> blocked = new LinkedHashSet<>();
        for (String source : sources) {
            if (!policy.allowsSource(source)) {
                blocked.add(source);
            }
        }
        return blocked;
    }

    private static void collectExposureFields(QueryAst ast,
                                              Class<?> sourceClass,
                                              Map<String, List<?>> joinSources,
                                              LinkedHashSet<String> fields) {
        if (ast.select() != null && ast.select().wildcard()) {
            Class<?> wildcardSourceClass = classForSource(ast.select().sourceName(), sourceClass, joinSources);
            if (wildcardSourceClass != null) {
                for (String field : ReflectionUtil.collectQueryableFieldNames(wildcardSourceClass)) {
                    fields.add(field);
                    if (ast.select().sourceName() != null) {
                        fields.add(ast.select().sourceName() + "." + field);
                    }
                }
            }
        }
        for (JoinAst join : ast.joins()) {
            fields.add(join.childSource() + "." + join.childField());
        }
        collectNestedExposureFields(ast.filters(), sourceClass, joinSources, fields);
        collectNestedExposureFields(ast.whereExpression(), sourceClass, joinSources, fields);
        collectNestedExposureFields(ast.havingFilters(), sourceClass, joinSources, fields);
        collectNestedExposureFields(ast.havingExpression(), sourceClass, joinSources, fields);
        collectNestedExposureFields(ast.qualifyFilters(), sourceClass, joinSources, fields);
        collectNestedExposureFields(ast.qualifyExpression(), sourceClass, joinSources, fields);
    }

    private static void collectNestedExposureFields(List<FilterAst> filters,
                                                    Class<?> sourceClass,
                                                    Map<String, List<?>> joinSources,
                                                    LinkedHashSet<String> fields) {
        for (FilterAst filter : filters) {
            collectNestedExposureFields(filter, sourceClass, joinSources, fields);
        }
    }

    private static void collectNestedExposureFields(FilterExpressionAst expression,
                                                    Class<?> sourceClass,
                                                    Map<String, List<?>> joinSources,
                                                    LinkedHashSet<String> fields) {
        if (expression == null) {
            return;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            collectNestedExposureFields(predicateAst.filter(), sourceClass, joinSources, fields);
            return;
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        collectNestedExposureFields(binary.left(), sourceClass, joinSources, fields);
        collectNestedExposureFields(binary.right(), sourceClass, joinSources, fields);
    }

    private static void collectNestedExposureFields(FilterAst filter,
                                                    Class<?> sourceClass,
                                                    Map<String, List<?>> joinSources,
                                                    LinkedHashSet<String> fields) {
        Object value = filter.value();
        if (value instanceof SubqueryValueAst subqueryValueAst) {
            collectExposureFields(subqueryValueAst.query(), sourceClass, joinSources, fields);
        } else if (value instanceof ExistsSubqueryValueAst existsSubqueryValueAst) {
            collectExposureFields(existsSubqueryValueAst.query(), sourceClass, joinSources, fields);
        }
    }

    private static void collectSourceNames(QueryAst ast, LinkedHashSet<String> sources) {
        if (ast.select() != null && ast.select().sourceName() != null) {
            sources.add(ast.select().sourceName());
        }
        for (JoinAst join : ast.joins()) {
            sources.add(join.childSource());
        }
        collectNestedSources(ast.filters(), sources);
        collectNestedSources(ast.whereExpression(), sources);
        collectNestedSources(ast.havingFilters(), sources);
        collectNestedSources(ast.havingExpression(), sources);
        collectNestedSources(ast.qualifyFilters(), sources);
        collectNestedSources(ast.qualifyExpression(), sources);
    }

    private static void collectNestedSources(List<FilterAst> filters, LinkedHashSet<String> sources) {
        for (FilterAst filter : filters) {
            collectNestedSources(filter, sources);
        }
    }

    private static void collectNestedSources(FilterExpressionAst expression, LinkedHashSet<String> sources) {
        if (expression == null) {
            return;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            collectNestedSources(predicateAst.filter(), sources);
            return;
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        collectNestedSources(binary.left(), sources);
        collectNestedSources(binary.right(), sources);
    }

    private static void collectNestedSources(FilterAst filter, LinkedHashSet<String> sources) {
        Object value = filter.value();
        if (value instanceof SubqueryValueAst subqueryValueAst) {
            collectSourceNames(subqueryValueAst.query(), sources);
        } else if (value instanceof ExistsSubqueryValueAst existsSubqueryValueAst) {
            collectSourceNames(existsSubqueryValueAst.query(), sources);
        }
    }

    private static Class<?> classForSource(String sourceName,
                                           Class<?> sourceClass,
                                           Map<String, List<?>> joinSources) {
        if (sourceName == null) {
            return sourceClass;
        }
        Class<?> joinSourceClass = inferRowClass(joinSources.get(sourceName));
        return joinSourceClass == null ? sourceClass : joinSourceClass;
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

    private static QueryExposurePolicy effectivePolicy(QueryExposurePolicy policy) {
        return policy == null ? QueryExposurePolicy.unrestricted() : policy;
    }

    private static Map<String, List<?>> safeJoinSources(Map<String, List<?>> joinSources) {
        return joinSources == null ? Collections.emptyMap() : joinSources;
    }
}
