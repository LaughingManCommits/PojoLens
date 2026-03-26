package laughing.man.commits.sqllike.internal.execution;

import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.RawQueryRow;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.QueryFieldLookupUtil;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.SchemaIndexUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
        if (sourceRows == null || sourceRows.isEmpty()) {
            return new ArrayList<>(0);
        }
        try {
            ArrayList<String> outputSchema = new ArrayList<>(select.fields().size());
            for (SelectFieldAst field : select.fields()) {
                outputSchema.add(field.outputName());
            }
            if (QueryRow.class.equals(targetClass)) {
                ArrayList<QueryRow> projectedRows = new ArrayList<>(sourceRows.size());
                for (Object sourceRow : sourceRows) {
                    Object[] values = new Object[select.fields().size()];
                    int index = 0;
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
                        values[index++] = value;
                    }
                    projectedRows.add(new RawQueryRow(values, outputSchema));
                }
                @SuppressWarnings("unchecked")
                List<T> casted = (List<T>) projectedRows;
                return casted;
            }
            ensureNoArgConstructor(targetClass);
            ArrayList<Object[]> projectedRows = new ArrayList<>(sourceRows.size());
            for (Object sourceRow : sourceRows) {
                Object[] values = new Object[select.fields().size()];
                int index = 0;
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
                    values[index++] = value;
                }
                projectedRows.add(values);
            }
            return ReflectionUtil.toClassList(targetClass, projectedRows, outputSchema);
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

    public static <T> Iterator<T> executeIteratorWithOptionalJoin(QueryBuilder builder,
                                                                  Sort sort,
                                                                  boolean applyJoin,
                                                                  Class<T> targetClass) {
        if (sort == null) {
            if (applyJoin) {
                return builder.initFilter().join().iterator(targetClass);
            }
            return builder.initFilter().iterator(targetClass);
        }
        if (applyJoin) {
            return builder.initFilter().join().iterator(sort, targetClass);
        }
        return builder.initFilter().iterator(sort, targetClass);
    }

    public static <T> Stream<T> executeStreamWithOptionalJoin(QueryBuilder builder,
                                                              Sort sort,
                                                              boolean applyJoin,
                                                              Class<T> targetClass) {
        if (sort == null) {
            if (applyJoin) {
                return builder.initFilter().join().stream(targetClass);
            }
            return builder.initFilter().stream(targetClass);
        }
        if (applyJoin) {
            return builder.initFilter().join().stream(sort, targetClass);
        }
        return builder.initFilter().stream(sort, targetClass);
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
        if (field.metricField() || field.timeBucketField() || field.windowField()) {
            return field.outputName();
        }
        return field.field();
    }

    private static void ensureNoArgConstructor(Class<?> targetClass) throws NoSuchMethodException {
        targetClass.getDeclaredConstructor();
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
        return QueryFieldLookupUtil.findFieldValue(row.getFields(), fieldName);
    }

    private record AliasedProjectionPlan(List<String> outputSchema, int[] sourceIndexes) {
    }
}

