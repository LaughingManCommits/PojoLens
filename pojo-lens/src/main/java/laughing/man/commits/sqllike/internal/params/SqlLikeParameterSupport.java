package laughing.man.commits.sqllike.internal.params;

import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.ParameterValueAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.util.StringUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Internal helpers for resolving SQL-like named parameter placeholders.
 */
public final class SqlLikeParameterSupport {

    private SqlLikeParameterSupport() {
    }

    public static QueryAst bind(QueryAst ast, Map<String, ?> parameters) {
        Objects.requireNonNull(ast, "ast must not be null");
        Map<String, Object> normalized = normalizeParameters(parameters);
        LinkedHashSet<String> used = collectParameterNamesInternal(ast);
        TreeSet<String> missing = new TreeSet<>(used);
        missing.removeAll(normalized.keySet());
        if (!missing.isEmpty()) {
            throw parameter(SqlLikeErrorCodes.PARAM_MISSING,
                    "Missing SQL-like parameter(s): " + missing);
        }
        TreeSet<String> unknown = new TreeSet<>(normalized.keySet());
        unknown.removeAll(used);
        if (!unknown.isEmpty()) {
            throw parameter(SqlLikeErrorCodes.PARAM_UNKNOWN,
                    "Unknown SQL-like parameter(s): " + unknown);
        }

        List<FilterAst> resolvedFilters = resolveFilters(ast.filters(), normalized);
        FilterExpressionAst resolvedWhere = resolveExpression(ast.whereExpression(), normalized);
        List<FilterAst> resolvedHaving = resolveFilters(ast.havingFilters(), normalized);
        FilterExpressionAst resolvedHavingExpr = resolveExpression(ast.havingExpression(), normalized);
        List<FilterAst> resolvedQualify = resolveFilters(ast.qualifyFilters(), normalized);
        FilterExpressionAst resolvedQualifyExpr = resolveExpression(ast.qualifyExpression(), normalized);
        Integer resolvedLimit = resolvePagination(ast.limit(), ast.limitParameter(), "LIMIT", normalized);
        Integer resolvedOffset = resolvePagination(ast.offset(), ast.offsetParameter(), "OFFSET", normalized);

        return new QueryAst(
                ast.select(),
                ast.joins(),
                resolvedFilters,
                resolvedWhere,
                ast.groupByFields(),
                resolvedHaving,
                resolvedHavingExpr,
                resolvedQualify,
                resolvedQualifyExpr,
                ast.orders(),
                resolvedLimit,
                resolvedOffset
        );
    }

    public static void assertFullyBound(QueryAst ast) {
        Objects.requireNonNull(ast, "ast must not be null");
        LinkedHashSet<String> unresolved = collectParameterNamesInternal(ast);
        if (unresolved.isEmpty()) {
            return;
        }
        throw parameter(SqlLikeErrorCodes.PARAM_UNRESOLVED,
                "Query contains unresolved SQL-like parameter(s): "
                        + new TreeSet<>(unresolved)
                        + ". Call params(...) before execution.");
    }

    public static Set<String> collectParameterNames(QueryAst ast) {
        Objects.requireNonNull(ast, "ast must not be null");
        return Collections.unmodifiableSet(new LinkedHashSet<>(collectParameterNamesInternal(ast)));
    }

    private static Map<String, Object> normalizeParameters(Map<String, ?> parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : parameters.entrySet()) {
            String key = entry.getKey();
            if (StringUtil.isNullOrBlank(key)) {
                throw parameter(SqlLikeErrorCodes.PARAM_NAME_INVALID,
                        "Parameter name must not be null/blank");
            }
            normalized.put(key, entry.getValue());
        }
        return normalized;
    }

    private static LinkedHashSet<String> collectParameterNamesInternal(QueryAst ast) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        collectParameterNames(ast.filters(), names);
        collectParameterNames(ast.havingFilters(), names);
        collectParameterNames(ast.qualifyFilters(), names);
        collectParameterNames(ast.whereExpression(), names);
        collectParameterNames(ast.havingExpression(), names);
        collectParameterNames(ast.qualifyExpression(), names);
        if (ast.limitParameter() != null) {
            names.add(ast.limitParameter());
        }
        if (ast.offsetParameter() != null) {
            names.add(ast.offsetParameter());
        }
        return names;
    }

    private static void collectParameterNames(List<FilterAst> filters, Set<String> names) {
        for (FilterAst filter : filters) {
            Object value = filter.value();
            if (value instanceof ParameterValueAst) {
                names.add(((ParameterValueAst) value).name());
            } else if (value instanceof SubqueryValueAst subqueryValueAst) {
                names.addAll(collectParameterNamesInternal(subqueryValueAst.query()));
            }
        }
    }

    private static void collectParameterNames(FilterExpressionAst expression, Set<String> names) {
        if (expression == null) {
            return;
        }
        if (expression instanceof FilterPredicateAst) {
            Object value = ((FilterPredicateAst) expression).filter().value();
            if (value instanceof ParameterValueAst) {
                names.add(((ParameterValueAst) value).name());
            }
            return;
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        collectParameterNames(binary.left(), names);
        collectParameterNames(binary.right(), names);
    }

    private static List<FilterAst> resolveFilters(List<FilterAst> filters, Map<String, Object> parameters) {
        ArrayList<FilterAst> resolved = new ArrayList<>(filters.size());
        for (FilterAst filter : filters) {
            resolved.add(resolveFilter(filter, parameters));
        }
        return resolved;
    }

    private static FilterExpressionAst resolveExpression(FilterExpressionAst expression, Map<String, Object> parameters) {
        if (expression == null) {
            return null;
        }
        if (expression instanceof FilterPredicateAst) {
            return new FilterPredicateAst(resolveFilter(((FilterPredicateAst) expression).filter(), parameters));
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        return new FilterBinaryAst(
                resolveExpression(binary.left(), parameters),
                resolveExpression(binary.right(), parameters),
                binary.operator()
        );
    }

    private static FilterAst resolveFilter(FilterAst filter, Map<String, Object> parameters) {
        Object value = filter.value();
        if (value instanceof ParameterValueAst) {
            String name = ((ParameterValueAst) value).name();
            if (!parameters.containsKey(name)) {
                throw parameter(SqlLikeErrorCodes.PARAM_MISSING,
                        "Missing SQL-like parameter(s): [" + name + "]");
            }
            value = new BoundParameterValue(name, parameters.get(name));
        } else if (value instanceof SubqueryValueAst subqueryValueAst) {
            value = new SubqueryValueAst(
                    subqueryValueAst.source(),
                    bind(subqueryValueAst.query(), parameters)
            );
        }
        return new FilterAst(filter.field(), filter.clause(), value, filter.separator());
    }

    private static IllegalArgumentException parameter(String code, String message) {
        return SqlLikeErrors.argument(code, message);
    }

    private static Integer resolvePagination(Integer literalValue,
                                             String parameterName,
                                             String clauseName,
                                             Map<String, Object> parameters) {
        if (parameterName == null) {
            return literalValue;
        }
        if (!parameters.containsKey(parameterName)) {
            throw parameter(SqlLikeErrorCodes.PARAM_MISSING,
                    "Missing SQL-like parameter(s): [" + parameterName + "]");
        }
        Object value = parameters.get(parameterName);
        return coercePaginationParameter(parameterName, clauseName, value);
    }

    private static Integer coercePaginationParameter(String parameterName, String clauseName, Object value) {
        if (!(value instanceof Number)) {
            throw parameter(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH,
                    "Pagination " + clauseName + " parameter '" + parameterName
                            + "' must be a non-negative integer, but received "
                            + typeName(value));
        }
        BigDecimal decimal;
        try {
            decimal = new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            throw parameter(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH,
                    "Pagination " + clauseName + " parameter '" + parameterName
                            + "' must be a non-negative integer, but received "
                            + typeName(value));
        }
        if (decimal.signum() < 0) {
            throw parameter(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH,
                    "Pagination " + clauseName + " parameter '" + parameterName
                            + "' must be >= 0");
        }
        BigDecimal normalized = decimal.stripTrailingZeros();
        if (normalized.scale() > 0) {
            throw parameter(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH,
                    "Pagination " + clauseName + " parameter '" + parameterName
                            + "' must be an integer");
        }
        try {
            return normalized.intValueExact();
        } catch (ArithmeticException ex) {
            throw parameter(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH,
                    "Pagination " + clauseName + " parameter '" + parameterName
                            + "' is too large");
        }
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
