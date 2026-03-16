package laughing.man.commits.sqllike.internal.execution;

import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.SchemaIndexUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Internal helpers for SQL-like runtime execution.
 */
public final class SqlLikeExecutionSupport {

    private SqlLikeExecutionSupport() {
    }

    public static <T> Class<?> inferSourceClass(List<?> pojos, Class<T> fallback) {
        Object sample = CollectionUtil.firstNonNull(pojos);
        if (sample != null) {
            return sample.getClass();
        }
        return fallback;
    }

    public static <T> List<T> projectAliasedRows(List<?> sourceRows, Class<T> targetClass, SelectAst select) {
        List<T> results = new ArrayList<>();
        try {
            for (Object sourceRow : sourceRows) {
                T target = targetClass.getDeclaredConstructor().newInstance();
                for (SelectFieldAst field : select.fields()) {
                    Object value;
                    if (field.computedField()) {
                        value = SqlExpressionEvaluator.evaluateNumeric(
                                field.field(),
                                identifier -> resolveFieldValue(sourceRow, identifier)
                        );
                    } else {
                        value = resolveProjectedFieldValue(sourceRow, field);
                    }
                    ReflectionUtil.setFieldValue(target, field.outputName(), value);
                }
                results.add(target);
            }
            return results;
        } catch (Exception e) {
            throw SqlLikeErrors.state(SqlLikeErrorCodes.RUNTIME_ALIASED_PROJECTION_FAILED,
                    "Failed to project aliased SQL-like query results",
                    e);
        }
    }

    public static <T> List<T> projectAliasedRows(List<Object[]> sourceRows,
                                                 List<String> sourceFieldSchema,
                                                 Class<T> targetClass,
                                                 SelectAst select) {
        AliasedProjectionPlan plan = aliasedProjectionPlan(sourceFieldSchema, select);
        return ReflectionUtil.toClassList(targetClass, sourceRows, plan.outputSchema(), plan.sourceIndexes());
    }

    public static List<String> aliasedOutputSchema(List<String> sourceFieldSchema, SelectAst select) {
        return aliasedProjectionPlan(sourceFieldSchema, select).outputSchema();
    }

    public static <T> List<T> executeWithOptionalJoin(QueryBuilder builder,
                                                      Sort sort,
                                                      boolean applyJoin,
                                                      Class<T> targetClass) {
        if (sort == null) {
            if (applyJoin) {
                return builder.initFilter().join().filter(targetClass);
            }
            return builder.initFilter().filter(targetClass);
        }
        if (applyJoin) {
            return builder.initFilter().join().filter(sort, targetClass);
        }
        return builder.initFilter().filter(sort, targetClass);
    }

    private static Object resolveFieldValue(Object sourceRow, String fieldName) {
        try {
            if (sourceRow instanceof QueryRow queryRow) {
                return queryRowFieldValue(queryRow, fieldName);
            }
            return ReflectionUtil.getFieldValue(sourceRow, fieldName);
        } catch (Exception e) {
            throw SqlLikeErrors.state(SqlLikeErrorCodes.RUNTIME_EXPRESSION_IDENTIFIER_RESOLUTION_FAILED,
                    "Failed to resolve expression identifier '" + fieldName + "'",
                    e);
        }
    }

    private static Object resolveProjectedFieldValue(Object sourceRow, SelectFieldAst field) throws Exception {
        String sourceFieldName = projectionSourceField(field);
        if (sourceRow instanceof QueryRow queryRow) {
            return queryRowFieldValue(queryRow, sourceFieldName);
        }
        return ReflectionUtil.getFieldValue(sourceRow, sourceFieldName);
    }

    private static String projectionSourceField(SelectFieldAst field) {
        if (field.metricField() || field.timeBucketField()) {
            return field.outputName();
        }
        return field.field();
    }

    private static AliasedProjectionPlan aliasedProjectionPlan(List<String> sourceFieldSchema, SelectAst select) {
        if (select == null || select.fields().isEmpty()) {
            return new AliasedProjectionPlan(List.of(), new int[0]);
        }
        Map<String, Integer> sourceFieldIndexes = SchemaIndexUtil.indexFieldNames(sourceFieldSchema);
        ArrayList<String> outputSchema = new ArrayList<>(select.fields().size());
        int[] sourceIndexes = new int[select.fields().size()];
        int mapped = 0;
        for (SelectFieldAst field : select.fields()) {
            if (field.computedField()) {
                throw SqlLikeErrors.state(SqlLikeErrorCodes.RUNTIME_ALIASED_PROJECTION_FAILED,
                        "Computed select fields require row-based alias projection",
                        null);
            }
            String sourceFieldName = projectionSourceField(field);
            int sourceIndex = SchemaIndexUtil.findFieldIndex(sourceFieldIndexes, sourceFieldName);
            if (sourceIndex < 0) {
                throw SqlLikeErrors.state(SqlLikeErrorCodes.RUNTIME_ALIASED_PROJECTION_FAILED,
                        "Failed to resolve aliased SQL-like projection field '" + sourceFieldName + "'",
                        null);
            }
            outputSchema.add(field.outputName());
            sourceIndexes[mapped++] = sourceIndex;
        }
        return new AliasedProjectionPlan(
                List.copyOf(outputSchema),
                mapped == sourceIndexes.length ? sourceIndexes : Arrays.copyOf(sourceIndexes, mapped)
        );
    }

    private static Object queryRowFieldValue(QueryRow row, String fieldName) {
        if (row.getFields() == null) {
            return null;
        }
        for (QueryField field : row.getFields()) {
            if (fieldName.equals(field.getFieldName())) {
                return field.getValue();
            }
        }
        return null;
    }

    private record AliasedProjectionPlan(List<String> outputSchema, int[] sourceIndexes) {
    }
}

