package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.RawQueryRow;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Lazy row-by-row streaming path for simple POJO-source queries.
 * <p>
 * This path avoids full result materialization by evaluating filter rules and
 * projection during iteration.
 */
final class FastPojoStreamSupport {

    private FastPojoStreamSupport() {
    }

    static boolean isApplicable(FilterQueryBuilder builder) {
        List<?> sourceBeans = builder.getSourceBeansForExecution();
        if (sourceBeans == null || sourceBeans.isEmpty()) {
            return false;
        }
        if (CollectionUtil.firstNonNull(sourceBeans) instanceof QueryRow) {
            return false;
        }
        return builder.getJoinClasses().isEmpty()
                && builder.getJoinSourceBeansForExecution().isEmpty()
                && builder.getMetrics().isEmpty()
                && builder.getGroupFields().isEmpty()
                && builder.getTimeBuckets().isEmpty()
                && builder.getDistinctFields().isEmpty()
                && builder.getOrderFields().isEmpty()
                && builder.getHavingFields().isEmpty()
                && builder.getHavingAllOfGroups().isEmpty()
                && builder.getHavingAnyOfGroups().isEmpty()
                && builder.getAllOfGroups().isEmpty()
                && builder.getAnyOfGroups().isEmpty()
                && builder.getComputedFieldRegistry().isEmpty();
    }

    static <T> Stream<T> stream(FilterQueryBuilder builder, Class<T> cls) {
        StreamingPlan plan = compileStreamingPlan(builder);
        Iterator<T> iterator = new StreamingIterator<>(builder, plan, cls);
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false);
    }

    private static StreamingPlan compileStreamingPlan(FilterQueryBuilder builder) {
        List<?> sourceBeans = builder.getSourceBeansForExecution();
        Object first = CollectionUtil.firstNonNull(sourceBeans);
        if (first == null) {
            return new StreamingPlan(
                    ReflectionUtil.compileFlatRowReadPlan(Object.class, List.of()),
                    List.of(),
                    new int[0],
                    new CompiledRuleBundle(new int[0], new CompiledRule[0][]),
                    normalizeOffset(builder.getOffset()),
                    builder.getLimit()
            );
        }

        List<String> readSchema = requiredReadFieldNames(builder);
        ReflectionUtil.FlatRowReadPlan readPlan = ReflectionUtil.compileFlatRowReadPlan(first.getClass(), readSchema);
        List<String> effectiveReadSchema = readPlan.fieldNames();

        FilterExecutionPlan plan = FilterExecutionPlan.forSchema(builder, effectiveReadSchema);
        List<Integer> returnFieldIndexes = plan.getReturnFieldIndexes();
        List<String> returnFieldNames = plan.getReturnFieldNames();
        int[] projectionIndexes = toIntArray(returnFieldIndexes);
        List<String> projectionSchema = builder.getReturnFields().isEmpty()
                ? effectiveReadSchema
                : returnFieldNames;
        CompiledRuleBundle ruleBundle = compileRuleBundle(plan.getRulesByFieldIndex(), readPlan.size());
        return new StreamingPlan(
                readPlan,
                projectionSchema,
                projectionIndexes,
                ruleBundle,
                normalizeOffset(builder.getOffset()),
                builder.getLimit()
        );
    }

    private static int normalizeOffset(Integer offset) {
        return offset == null ? 0 : Math.max(0, offset);
    }

    private static int[] toIntArray(List<Integer> indexes) {
        int[] values = new int[indexes.size()];
        for (int i = 0; i < indexes.size(); i++) {
            values[i] = indexes.get(i);
        }
        return values;
    }

    private static List<String> requiredReadFieldNames(FilterQueryBuilder builder) {
        Map<String, Class<?>> sourceFieldTypes = builder.getSourceFieldTypesForExecution();
        if (sourceFieldTypes.isEmpty()) {
            return List.of();
        }

        if (builder.getReturnFields().isEmpty()) {
            return new ArrayList<>(sourceFieldTypes.keySet());
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        addKnownFields(selected, sourceFieldTypes, builder.getFilterFields().values());
        addKnownFields(selected, sourceFieldTypes, builder.getReturnFields());

        if (selected.isEmpty()) {
            return new ArrayList<>(sourceFieldTypes.keySet());
        }

        ArrayList<String> ordered = new ArrayList<>(selected.size());
        for (String fieldName : sourceFieldTypes.keySet()) {
            if (selected.contains(fieldName)) {
                ordered.add(fieldName);
            }
        }
        return ordered.isEmpty() ? new ArrayList<>(sourceFieldTypes.keySet()) : ordered;
    }

    private static void addKnownFields(LinkedHashSet<String> selected,
                                       Map<String, Class<?>> sourceFieldTypes,
                                       Iterable<String> candidateFieldNames) {
        for (String fieldName : candidateFieldNames) {
            if (sourceFieldTypes.containsKey(fieldName)) {
                selected.add(fieldName);
            }
        }
    }

    private static CompiledRuleBundle compileRuleBundle(Map<Integer, List<CompiledRule>> rulesByField,
                                                        int valueCount) {
        int validCount = 0;
        for (Map.Entry<Integer, List<CompiledRule>> entry : rulesByField.entrySet()) {
            int fieldIndex = entry.getKey();
            List<CompiledRule> rules = entry.getValue();
            if (fieldIndex >= 0 && fieldIndex < valueCount && rules != null && !rules.isEmpty()) {
                validCount++;
            }
        }
        if (validCount == 0) {
            return new CompiledRuleBundle(new int[0], new CompiledRule[0][]);
        }

        int[] fieldIndexes = new int[validCount];
        CompiledRule[][] compiledRules = new CompiledRule[validCount][];
        int position = 0;
        for (Map.Entry<Integer, List<CompiledRule>> entry : rulesByField.entrySet()) {
            int fieldIndex = entry.getKey();
            List<CompiledRule> rules = entry.getValue();
            if (fieldIndex < 0 || fieldIndex >= valueCount || rules == null || rules.isEmpty()) {
                continue;
            }
            fieldIndexes[position] = fieldIndex;
            compiledRules[position] = rules.toArray(new CompiledRule[0]);
            position++;
        }
        return new CompiledRuleBundle(fieldIndexes, compiledRules);
    }

    private static boolean passesFilter(Object[] values, CompiledRuleBundle rulesByField) {
        if (rulesByField.fieldIndexes().length == 0) {
            return true;
        }
        boolean andMatched = false;
        boolean andFailed = false;
        boolean orMatched = false;

        outer:
        for (int i = 0; i < rulesByField.fieldIndexes().length; i++) {
            int fieldIndex = rulesByField.fieldIndexes()[i];
            Object fieldValue = values[fieldIndex];
            CompiledRule[] rules = rulesByField.compiledRules()[i];
            for (CompiledRule rule : rules) {
                boolean matched = ObjectUtil.compareObject(fieldValue, rule.compareValue, rule.clause, rule.dateFormat);
                if (Separator.AND.equals(rule.separator)) {
                    if (matched) {
                        andMatched = true;
                    } else {
                        andFailed = true;
                    }
                } else if (Separator.OR.equals(rule.separator) && matched) {
                    orMatched = true;
                }
                if (andFailed && orMatched) {
                    break outer;
                }
            }
        }
        return (andMatched && !andFailed) || orMatched;
    }

    private static Object[] projectValues(Object[] values, int[] projectionIndexes) {
        if (projectionIndexes.length == 0) {
            return values.clone();
        }
        Object[] projected = new Object[projectionIndexes.length];
        for (int i = 0; i < projectionIndexes.length; i++) {
            int sourceIndex = projectionIndexes[i];
            projected[i] = sourceIndex >= 0 && sourceIndex < values.length ? values[sourceIndex] : null;
        }
        return projected;
    }

    private static final class StreamingIterator<T> implements Iterator<T> {
        private final List<?> sourceBeans;
        private final StreamingPlan plan;
        private final Class<T> projectionClass;
        private final Object[] buffer;
        private int sourceIndex;
        private int skipped;
        private int emitted;
        private T nextValue;
        private boolean nextReady;
        private boolean done;

        private StreamingIterator(FilterQueryBuilder builder, StreamingPlan plan, Class<T> projectionClass) {
            this.sourceBeans = builder.getSourceBeansForExecution();
            this.plan = plan;
            this.projectionClass = projectionClass;
            this.buffer = new Object[plan.readPlan().size()];
        }

        @Override
        public boolean hasNext() {
            if (done) {
                return false;
            }
            if (!nextReady) {
                loadNext();
            }
            return nextReady;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more streamed results");
            }
            T value = nextValue;
            nextValue = null;
            nextReady = false;
            return value;
        }

        private void loadNext() {
            Integer limit = plan.limit();
            while (sourceIndex < sourceBeans.size()) {
                if (limit != null && emitted >= limit) {
                    done = true;
                    return;
                }
                Object bean = sourceBeans.get(sourceIndex++);
                if (bean == null) {
                    continue;
                }
                try {
                    ReflectionUtil.readFlatRowValues(bean, plan.readPlan(), buffer, 0);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Failed to read streaming source row", e);
                }
                if (!passesFilter(buffer, plan.ruleBundle())) {
                    continue;
                }
                if (skipped < plan.offset()) {
                    skipped++;
                    continue;
                }

                Object[] projected = projectValues(buffer, plan.projectionIndexes());
                RawQueryRow row = new RawQueryRow(projected, plan.projectionSchema());
                nextValue = ReflectionUtil.toClass(projectionClass, row);
                emitted++;
                nextReady = true;
                return;
            }
            done = true;
        }
    }

    private record StreamingPlan(ReflectionUtil.FlatRowReadPlan readPlan,
                                 List<String> projectionSchema,
                                 int[] projectionIndexes,
                                 CompiledRuleBundle ruleBundle,
                                 int offset,
                                 Integer limit) {
    }

    private record CompiledRuleBundle(int[] fieldIndexes, CompiledRule[][] compiledRules) {
    }
}

