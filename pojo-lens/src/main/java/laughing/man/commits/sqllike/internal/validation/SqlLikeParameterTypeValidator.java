package laughing.man.commits.sqllike.internal.validation;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.internal.aggregate.AggregateExpressionSupport;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;
import laughing.man.commits.sqllike.internal.params.BoundParameterValue;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

final class SqlLikeParameterTypeValidator {

    private SqlLikeParameterTypeValidator() {
    }

    static void validate(QueryAst ast,
                         Map<String, Class<?>> queryableFieldTypes,
                         Map<String, Class<?>> sourceFieldTypes) {
        for (FilterAst filter : ast.filters()) {
            validateParameterFilterType(filter, "WHERE", resolveWhereExpectedType(filter, queryableFieldTypes));
        }

        Map<String, Class<?>> havingFieldTypes = resolveHavingFieldTypes(ast, queryableFieldTypes, sourceFieldTypes);
        for (FilterAst filter : ast.havingFilters()) {
            validateParameterFilterType(filter, "HAVING", resolveHavingExpectedType(filter, havingFieldTypes, sourceFieldTypes));
        }

        Map<String, Class<?>> qualifyFieldTypes = resolveQualifyFieldTypes(ast);
        for (FilterAst filter : ast.qualifyFilters()) {
            validateParameterFilterType(filter, "QUALIFY", resolveQualifyExpectedType(filter, qualifyFieldTypes));
        }
    }

    private static Class<?> resolveWhereExpectedType(FilterAst filter, Map<String, Class<?>> queryableFieldTypes) {
        if (SqlExpressionEvaluator.looksLikeExpression(filter.field())) {
            return Number.class;
        }
        if (filter.clause() == Clauses.CONTAINS
                || filter.clause() == Clauses.MATCHES) {
            return String.class;
        }
        return queryableFieldTypes.get(filter.field());
    }

    private static Map<String, Class<?>> resolveHavingFieldTypes(QueryAst ast,
                                                                 Map<String, Class<?>> queryableFieldTypes,
                                                                 Map<String, Class<?>> sourceFieldTypes) {
        LinkedHashMap<String, Class<?>> havingFieldTypes = new LinkedHashMap<>();
        Map<String, Class<?>> selectOutputTypes = resolveSelectOutputTypes(ast.select(), queryableFieldTypes, sourceFieldTypes);
        for (String grouped : ast.groupByFields()) {
            Class<?> groupedType = selectOutputTypes.get(grouped);
            if (groupedType == null) {
                groupedType = sourceFieldTypes.get(grouped);
            }
            if (groupedType != null) {
                havingFieldTypes.put(grouped, groupedType);
            }
        }
        if (ast.select() != null) {
            for (SelectFieldAst field : ast.select().fields()) {
                if (field.metricField()) {
                    havingFieldTypes.put(field.outputName(), metricOutputType(field.metric(), sourceFieldTypes.get(field.field())));
                }
            }
        }
        return havingFieldTypes;
    }

    private static Map<String, Class<?>> resolveSelectOutputTypes(SelectAst select,
                                                                  Map<String, Class<?>> queryableFieldTypes,
                                                                  Map<String, Class<?>> sourceFieldTypes) {
        LinkedHashMap<String, Class<?>> outputTypes = new LinkedHashMap<>();
        if (select == null || select.wildcard()) {
            return outputTypes;
        }
        for (SelectFieldAst field : select.fields()) {
            if (field.metricField()) {
                outputTypes.put(field.outputName(), metricOutputType(field.metric(), sourceFieldTypes.get(field.field())));
            } else if (field.timeBucketField()) {
                outputTypes.put(field.outputName(), String.class);
            } else if (field.windowField()) {
                outputTypes.put(field.outputName(), Long.class);
            } else if (field.computedField()) {
                outputTypes.put(field.outputName(), Double.class);
            } else {
                outputTypes.put(field.outputName(), queryableFieldTypes.get(field.field()));
            }
        }
        return outputTypes;
    }

    private static Class<?> resolveHavingExpectedType(FilterAst filter,
                                                      Map<String, Class<?>> havingFieldTypes,
                                                      Map<String, Class<?>> sourceFieldTypes) {
        if (SqlExpressionEvaluator.looksLikeExpression(filter.field())) {
            return Number.class;
        }
        Class<?> direct = havingFieldTypes.get(filter.field());
        if (direct != null) {
            return direct;
        }
        AggregateExpressionSupport.ParsedAggregateExpression expression = AggregateExpressionSupport.parse(filter.field());
        if (expression != null) {
            Class<?> fieldType = expression.countAll() ? Long.class : sourceFieldTypes.get(expression.field());
            return metricOutputType(expression.metric(), fieldType);
        }
        return null;
    }

    private static Map<String, Class<?>> resolveQualifyFieldTypes(QueryAst ast) {
        LinkedHashMap<String, Class<?>> qualifyFieldTypes = new LinkedHashMap<>();
        SelectAst select = ast.select();
        if (select == null || select.wildcard()) {
            return qualifyFieldTypes;
        }
        for (SelectFieldAst field : select.fields()) {
            if (!field.windowField()) {
                continue;
            }
            qualifyFieldTypes.put(field.outputName(), Long.class);
        }
        return qualifyFieldTypes;
    }

    private static Class<?> resolveQualifyExpectedType(FilterAst filter, Map<String, Class<?>> qualifyFieldTypes) {
        if (SqlExpressionEvaluator.looksLikeExpression(filter.field())) {
            return Number.class;
        }
        return qualifyFieldTypes.get(filter.field());
    }

    private static Class<?> metricOutputType(Metric metric, Class<?> fieldType) {
        if (metric == Metric.COUNT) {
            return Long.class;
        }
        if (metric == Metric.AVG) {
            return Double.class;
        }
        if (fieldType == null) {
            return Number.class;
        }
        return wrap(fieldType);
    }

    private static void validateParameterFilterType(FilterAst filter, String clauseName, Class<?> expectedType) {
        if (!(filter.value() instanceof BoundParameterValue boundParameterValue)) {
            return;
        }
        if (expectedType == null) {
            return;
        }
        if (isCompatibleParameterValue(boundParameterValue.value(), expectedType)) {
            return;
        }
        throw parameterTypeMismatch(boundParameterValue.name(), filter.field(), clauseName, expectedType, boundParameterValue.value());
    }

    private static boolean isCompatibleParameterValue(Object value, Class<?> expectedType) {
        if (value == null) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object nested : map.values()) {
                if (!isCompatibleParameterValue(nested, expectedType)) {
                    return false;
                }
            }
            return true;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object nested : iterable) {
                if (!isCompatibleParameterValue(nested, expectedType)) {
                    return false;
                }
            }
            return true;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (!isCompatibleParameterValue(java.lang.reflect.Array.get(value, i), expectedType)) {
                    return false;
                }
            }
            return true;
        }
        Class<?> wrappedExpected = wrap(expectedType);
        if (isNumericType(wrappedExpected)) {
            return value instanceof Number;
        }
        if (Date.class.equals(wrappedExpected)) {
            return value instanceof Date;
        }
        if (Boolean.class.equals(wrappedExpected)) {
            return value instanceof Boolean;
        }
        if (String.class.equals(wrappedExpected)) {
            return value instanceof String;
        }
        return wrappedExpected.isInstance(value);
    }

    private static Class<?> wrap(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static IllegalArgumentException parameterTypeMismatch(String parameterName,
                                                                 String fieldName,
                                                                 String clauseName,
                                                                 Class<?> expectedType,
                                                                 Object actualValue) {
        String actualType = actualValue == null ? "null" : actualValue.getClass().getSimpleName();
        return SqlLikeErrors.argument(
                SqlLikeErrorCodes.PARAM_TYPE_MISMATCH,
                "Strict parameter typing rejected parameter '"
                        + parameterName
                        + "' for field '"
                        + fieldName
                        + "' in "
                        + clauseName
                        + " clause: expected "
                        + expectedTypeLabel(expectedType)
                        + " but received "
                        + actualType
        );
    }

    private static String expectedTypeLabel(Class<?> expectedType) {
        Class<?> wrapped = wrap(expectedType);
        if (wrapped == null) {
            return "compatible value";
        }
        if (isNumericType(wrapped)) {
            return "numeric value";
        }
        if (Date.class.equals(wrapped)) {
            return "Date value";
        }
        if (Boolean.class.equals(wrapped)) {
            return "Boolean value";
        }
        if (String.class.equals(wrapped)) {
            return "String value";
        }
        return wrapped.getSimpleName() + " value";
    }

    private static boolean isNumericType(Class<?> type) {
        return type != null && (Number.class.equals(type) || Number.class.isAssignableFrom(type));
    }
}

