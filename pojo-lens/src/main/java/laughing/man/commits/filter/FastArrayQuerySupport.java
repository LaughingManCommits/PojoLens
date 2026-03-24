package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.computed.ComputedFieldDefinition;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.computed.internal.ComputedFieldSupport;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.util.CollectionUtil;
import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.StringUtil;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FastArrayQuerySupport {
    private static final int TOP_K_MIN_INPUT_ROWS = 64;
    private static final int TOP_K_MAX_LIMIT = 512;
    private static final int DEFAULT_MAP_CAPACITY = 16;
    private static final int DENSE_INDEX_INITIAL_CAPACITY = 16;
    private static final int DENSE_INDEX_GROWTH_FACTOR = 4;
    private static final long DENSE_INDEX_MAX_FACTOR = 4L;
    private static final int TOP_K_ROW_RATIO = 4;

    private FastArrayQuerySupport() {
    }

    static FastArrayState tryBuildJoinedState(FilterQueryBuilder builder) {
        if (!canUseFastJoinPath(builder)) {
            return null;
        }

        Integer joinIndex = builder.getJoinSourceBeansForExecution().keySet().stream().findFirst().orElse(null);
        if (joinIndex == null) {
            return null;
        }

        List<?> parents = builder.getSourceBeansForExecution();
        List<?> children = builder.getJoinSourceBeansForExecution().get(joinIndex);
        Object parentSample = CollectionUtil.firstNonNull(parents);
        Object childSample = CollectionUtil.firstNonNull(children);
        if (parentSample == null || childSample == null) {
            return null;
        }

        Join joinMethod = builder.getJoinMethods().get(joinIndex);
        if (joinMethod == null || Join.RIGHT_JOIN.equals(joinMethod)) {
            return null;
        }

        JoinCompilePlan plan = compileJoinPlan(
                builder,
                parentSample.getClass(),
                childSample.getClass(),
                builder.getSourceFieldTypesForExecution(),
                builder.getJoinSourceFieldTypesForExecution().get(joinIndex),
                joinIndex
        );
        if (plan == null) {
            return null;
        }

        ChildIndex childIndex = buildChildIndex(children, plan);
        ArrayList<Object[]> joinedRows = new ArrayList<>(parents.size());
        Object[] parentValues = new Object[plan.parentReadPlan().size()];
        for (Object parent : parents) {
            if (parent == null) {
                continue;
            }
            try {
                ReflectionUtil.readFlatRowValues(parent, plan.parentReadPlan(), parentValues, 0);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read parent join values", e);
            }
            Object joinKey = parentValues[plan.parentJoinIndex()];
            Object matchingChildren = childIndex.get(joinKey);
            if (matchingChildren instanceof Object[] childValues) {
                joinedRows.add(materializeJoinedRow(parentValues, childValues, plan));
                continue;
            }
            if (matchingChildren instanceof List<?> childValueList && !childValueList.isEmpty()) {
                for (Object childValue : childValueList) {
                    joinedRows.add(materializeJoinedRow(parentValues, (Object[]) childValue, plan));
                }
                continue;
            }
            if (Join.LEFT_JOIN.equals(joinMethod)) {
                joinedRows.add(materializeJoinedRow(parentValues, plan.nullChildValues(), plan));
            }
        }

        return new FastArrayState(List.copyOf(plan.schemaFields()), plan.schemaTypes(), joinedRows);
    }

    static <T> List<T> filter(FilterQueryBuilder builder,
                              FastArrayState state,
                              Sort sortMethod,
                              Class<T> projectionClass) {
        FilterExecutionPlan plan = FilterExecutionPlan.forSchema(builder, state.schemaFields());
        List<Object[]> filtered = filterRows(state.rows(), plan);
        Integer paginationWindow = CollectionUtil.pagingWindow(builder.getOffset(), builder.getLimit());
        List<Object[]> ordered = orderRows(filtered, sortMethod, plan, paginationWindow);
        List<String> outputSchema = outputSchema(builder, state.schemaFields(), plan);
        int[] outputIndexes = outputIndexes(builder, state.schemaFields(), plan);
        List<Object[]> limited = CollectionUtil.applyOffsetAndLimit(ordered, builder.getOffset(), builder.getLimit());
        return ReflectionUtil.toClassList(projectionClass, limited, outputSchema, outputIndexes);
    }

    static List<QueryRow> toQueryRows(FastArrayState state) {
        return QueryRowAdapterSupport.toQueryRows(state.schemaFields(), state.rows());
    }

    private static boolean canUseFastJoinPath(FilterQueryBuilder builder) {
        if (builder == null) {
            return false;
        }
        if (builder.getSourceBeansForExecution() == null || builder.getSourceBeansForExecution().isEmpty()) {
            return false;
        }
        if (builder.getJoinSourceBeansForExecution().size() != 1) {
            return false;
        }
        if (builder.getJoinMethods().size() != 1) {
            return false;
        }
        if (builder.getReturnFields().isEmpty()) {
            return false;
        }
        if (!builder.getDistinctFields().isEmpty()
                || !builder.getMetrics().isEmpty()
                || !builder.getGroupFields().isEmpty()
                || !builder.getTimeBuckets().isEmpty()
                || !builder.getHavingFields().isEmpty()
                || !builder.getHavingAllOfGroups().isEmpty()
                || !builder.getHavingAnyOfGroups().isEmpty()
                || !builder.getQualifyFields().isEmpty()
                || !builder.getQualifyAllOfGroups().isEmpty()
                || !builder.getQualifyAnyOfGroups().isEmpty()
                || !builder.getAllOfGroups().isEmpty()
                || !builder.getAnyOfGroups().isEmpty()
                || !builder.getWindows().isEmpty()) {
            return false;
        }
        Join joinMethod = builder.getJoinMethods().values().stream().findFirst().orElse(null);
        return Join.LEFT_JOIN.equals(joinMethod) || Join.INNER_JOIN.equals(joinMethod);
    }

    private static JoinCompilePlan compileJoinPlan(FilterQueryBuilder builder,
                                                   Class<?> parentType,
                                                   Class<?> childType,
                                                   Map<String, Class<?>> parentFieldTypes,
                                                   Map<String, Class<?>> childFieldTypes,
                                                   int joinIndex) {
        if (parentFieldTypes == null || parentFieldTypes.isEmpty()
                || childFieldTypes == null || childFieldTypes.isEmpty()) {
            return null;
        }

        LinkedHashSet<String> parentSelected = new LinkedHashSet<>();
        LinkedHashSet<String> childSelected = new LinkedHashSet<>();
        LinkedHashSet<String> neededComputedNames = new LinkedHashSet<>();

        if (!addFieldReference(builder.getJoinParentFields().get(joinIndex),
                parentFieldTypes,
                childFieldTypes,
                builder.getComputedFieldRegistry(),
                parentSelected,
                childSelected,
                neededComputedNames,
                new LinkedHashSet<>())) {
            return null;
        }
        if (!addFieldReference(builder.getJoinChildFields().get(joinIndex),
                parentFieldTypes,
                childFieldTypes,
                builder.getComputedFieldRegistry(),
                parentSelected,
                childSelected,
                neededComputedNames,
                new LinkedHashSet<>())) {
            return null;
        }

        if (!addConfiguredFieldReferences(builder.getReturnFields(),
                parentFieldTypes,
                childFieldTypes,
                builder.getComputedFieldRegistry(),
                parentSelected,
                childSelected,
                neededComputedNames)) {
            return null;
        }
        if (!addConfiguredFieldReferences(builder.getFilterFields().values(),
                parentFieldTypes,
                childFieldTypes,
                builder.getComputedFieldRegistry(),
                parentSelected,
                childSelected,
                neededComputedNames)) {
            return null;
        }
        if (!addConfiguredFieldReferences(builder.getOrderFields().values(),
                parentFieldTypes,
                childFieldTypes,
                builder.getComputedFieldRegistry(),
                parentSelected,
                childSelected,
                neededComputedNames)) {
            return null;
        }

        if (!disjoint(parentSelected, childSelected)) {
            return null;
        }

        ReflectionUtil.FlatRowReadPlan parentReadPlan = ReflectionUtil.compileFlatRowReadPlan(parentType, parentSelected);
        ReflectionUtil.FlatRowReadPlan childReadPlan = ReflectionUtil.compileFlatRowReadPlan(childType, childSelected);

        int parentJoinIndex = parentReadPlan.fieldNames().indexOf(builder.getJoinParentFields().get(joinIndex));
        int childJoinIndex = childReadPlan.fieldNames().indexOf(builder.getJoinChildFields().get(joinIndex));
        if (parentJoinIndex < 0 || childJoinIndex < 0) {
            return null;
        }

        LinkedHashMap<String, Class<?>> baseSchemaTypes = new LinkedHashMap<>();
        for (Map.Entry<String, Class<?>> entry : parentReadPlan.fieldTypes().entrySet()) {
            baseSchemaTypes.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Class<?>> entry : childReadPlan.fieldTypes().entrySet()) {
            if (baseSchemaTypes.containsKey(entry.getKey())) {
                return null;
            }
            baseSchemaTypes.put(entry.getKey(), entry.getValue());
        }

        List<ComputedFieldDefinition> computedDefinitions =
                selectComputedDefinitions(baseSchemaTypes.keySet(), builder.getComputedFieldRegistry(), neededComputedNames);
        for (ComputedFieldDefinition definition : computedDefinitions) {
            if (baseSchemaTypes.containsKey(definition.name())) {
                return null;
            }
        }

        ArrayList<String> schemaFields = new ArrayList<>(baseSchemaTypes.size() + computedDefinitions.size());
        schemaFields.addAll(parentReadPlan.fieldNames());
        schemaFields.addAll(childReadPlan.fieldNames());

        LinkedHashMap<String, Integer> schemaIndexByName = new LinkedHashMap<>(Math.max(DEFAULT_MAP_CAPACITY,schemaFields.size() * 2));
        for (int i = 0; i < schemaFields.size(); i++) {
            schemaIndexByName.put(schemaFields.get(i), i);
        }

        ComputedFieldPlan[] computedPlans = new ComputedFieldPlan[computedDefinitions.size()];
        for (int i = 0; i < computedDefinitions.size(); i++) {
            ComputedFieldDefinition definition = computedDefinitions.get(i);
            SqlExpressionEvaluator.CompiledExpression expression =
                    SqlExpressionEvaluator.compileNumeric(definition.expression());
            List<String> dependencyNames = new ArrayList<>(expression.identifiers());
            int[] dependencyIndexes = new int[dependencyNames.size()];
            for (int dependencyIndex = 0; dependencyIndex < dependencyNames.size(); dependencyIndex++) {
                Integer resolvedIndex = schemaIndexByName.get(dependencyNames.get(dependencyIndex));
                if (resolvedIndex == null) {
                    return null;
                }
                dependencyIndexes[dependencyIndex] = resolvedIndex;
            }
            int outputIndex = schemaFields.size();
            schemaFields.add(definition.name());
            schemaIndexByName.put(definition.name(), outputIndex);
            computedPlans[i] = new ComputedFieldPlan(
                    definition,
                    expression.bind(dependencyIndexes),
                    outputIndex
            );
        }

        LinkedHashMap<String, Class<?>> schemaTypes = new LinkedHashMap<>(Math.max(DEFAULT_MAP_CAPACITY,schemaFields.size() * 2));
        for (Map.Entry<String, Class<?>> entry : baseSchemaTypes.entrySet()) {
            schemaTypes.put(entry.getKey(), entry.getValue());
        }
        for (ComputedFieldDefinition definition : computedDefinitions) {
            schemaTypes.put(definition.name(), definition.outputType());
        }

        return new JoinCompilePlan(
                parentReadPlan,
                childReadPlan,
                parentJoinIndex,
                childJoinIndex,
                parentReadPlan.size() + childReadPlan.size(),
                List.copyOf(schemaFields),
                Collections.unmodifiableMap(new LinkedHashMap<>(schemaTypes)),
                computedPlans,
                new Object[childReadPlan.size()]
        );
    }

    private static boolean addConfiguredFieldReferences(Iterable<String> configuredFields,
                                                        Map<String, Class<?>> parentFieldTypes,
                                                        Map<String, Class<?>> childFieldTypes,
                                                        ComputedFieldRegistry registry,
                                                        Set<String> parentSelected,
                                                        Set<String> childSelected,
                                                        Set<String> neededComputedNames) {
        for (String fieldName : configuredFields) {
            if (!addFieldReference(fieldName,
                    parentFieldTypes,
                    childFieldTypes,
                    registry,
                    parentSelected,
                    childSelected,
                    neededComputedNames,
                    new LinkedHashSet<>())) {
                return false;
            }
        }
        return true;
    }

    private static boolean addFieldReference(String fieldName,
                                             Map<String, Class<?>> parentFieldTypes,
                                             Map<String, Class<?>> childFieldTypes,
                                             ComputedFieldRegistry registry,
                                             Set<String> parentSelected,
                                             Set<String> childSelected,
                                             Set<String> neededComputedNames,
                                             Set<String> visitingComputedNames) {
        if (StringUtil.isNullOrBlank(fieldName)) {
            return false;
        }

        boolean inParent = parentFieldTypes.containsKey(fieldName);
        boolean inChild = childFieldTypes.containsKey(fieldName);
        if (inParent && inChild) {
            return false;
        }
        if (inParent) {
            parentSelected.add(fieldName);
            return true;
        }
        if (inChild) {
            childSelected.add(fieldName);
            return true;
        }
        if (registry == null || !registry.contains(fieldName)) {
            return false;
        }
        if (!visitingComputedNames.add(fieldName)) {
            return true;
        }
        neededComputedNames.add(fieldName);
        try {
            ComputedFieldDefinition definition = registry.get(fieldName);
            if (definition == null) {
                return false;
            }
            for (String dependency : definition.dependencies()) {
                if (!addFieldReference(dependency,
                        parentFieldTypes,
                        childFieldTypes,
                        registry,
                        parentSelected,
                        childSelected,
                        neededComputedNames,
                        visitingComputedNames)) {
                    return false;
                }
            }
            return true;
        } finally {
            visitingComputedNames.remove(fieldName);
        }
    }

    private static boolean disjoint(Set<String> left, Set<String> right) {
        for (String value : left) {
            if (right.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private static List<ComputedFieldDefinition> selectComputedDefinitions(Set<String> baseFieldNames,
                                                                           ComputedFieldRegistry registry,
                                                                           Set<String> neededComputedNames) {
        if (registry == null || registry.isEmpty() || neededComputedNames.isEmpty()) {
            return List.of();
        }
        List<ComputedFieldDefinition> applicable = ComputedFieldSupport.resolveApplicableDefinitions(baseFieldNames, registry);
        ArrayList<ComputedFieldDefinition> selected = new ArrayList<>(neededComputedNames.size());
        for (ComputedFieldDefinition definition : applicable) {
            if (neededComputedNames.contains(definition.name())) {
                selected.add(definition);
            }
        }
        return selected;
    }

    private static ChildIndex buildChildIndex(List<?> children, JoinCompilePlan plan) {
        int childJoinIndex = plan.childJoinIndex();
        int denseMaxLength = maxDenseIndexLength(children.size());
        boolean denseEligible = denseMaxLength > 0;
        Object[] denseIndex = denseEligible ? new Object[Math.min(DENSE_INDEX_INITIAL_CAPACITY, denseMaxLength)] : null;
        int maxDenseKey = -1;
        int childRowCount = 0;
        HashMap<Object, Object> hashIndex = null;
        for (Object child : children) {
            if (child == null) {
                continue;
            }
            Object[] childValues = ReflectionUtil.readFlatRowValues(child, plan.childReadPlan());
            childRowCount++;
            Object joinKey = childValues[childJoinIndex];
            if (denseEligible
                    && joinKey instanceof Integer intKey
                    && intKey >= 0
                    && intKey < denseMaxLength) {
                if (intKey >= denseIndex.length) {
                    denseIndex = growDenseIndex(denseIndex, intKey + 1, denseMaxLength);
                }
                putDenseChildValue(denseIndex, intKey, childValues);
                maxDenseKey = Math.max(maxDenseKey, intKey);
                continue;
            }

            if (hashIndex == null) {
                hashIndex = new HashMap<>(CollectionUtil.expectedMapCapacity(Math.max(childRowCount, 1)));
            }
            if (denseEligible) {
                denseEligible = false;
                transferDenseToHashIndex(hashIndex, denseIndex);
            }
            putHashedChildValue(hashIndex, joinKey, childValues);
        }

        if (denseEligible && isDenseKeyRangeReasonable(maxDenseKey, childRowCount)) {
            return new DenseIntChildIndex(trimDenseIndex(denseIndex, maxDenseKey + 1));
        }
        if (hashIndex == null) {
            hashIndex = new HashMap<>(CollectionUtil.expectedMapCapacity(Math.max(childRowCount, 1)));
        }
        if (denseEligible) {
            transferDenseToHashIndex(hashIndex, denseIndex);
        }
        return new HashChildIndex(hashIndex);
    }

    private static int maxDenseIndexLength(int sourceSize) {
        if (sourceSize <= 0) {
            return 0;
        }
        long maxLength = (long) sourceSize * DENSE_INDEX_MAX_FACTOR;
        return maxLength > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maxLength;
    }

    private static Object[] growDenseIndex(Object[] current, int minLength, int maxLength) {
        int newLength = current.length;
        while (newLength < minLength && newLength < maxLength) {
            newLength = Math.min(maxLength, Math.max(newLength << 1, 1));
        }
        if (newLength < minLength) {
            newLength = minLength;
        }
        Object[] grown = new Object[newLength];
        System.arraycopy(current, 0, grown, 0, current.length);
        return grown;
    }

    private static Object[] trimDenseIndex(Object[] denseIndex, int length) {
        if (denseIndex == null || length <= 0) {
            return new Object[0];
        }
        if (denseIndex.length == length) {
            return denseIndex;
        }
        Object[] trimmed = new Object[length];
        System.arraycopy(denseIndex, 0, trimmed, 0, length);
        return trimmed;
    }

    private static void putDenseChildValue(Object[] denseIndex, int intKey, Object[] childValues) {
        Object existing = denseIndex[intKey];
        if (existing == null) {
            denseIndex[intKey] = childValues;
            return;
        }
        if (existing instanceof Object[] existingValues) {
            ArrayList<Object[]> bucket = new ArrayList<>(2);
            bucket.add(existingValues);
            bucket.add(childValues);
            denseIndex[intKey] = bucket;
            return;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> bucket = (List<Object[]>) existing;
        bucket.add(childValues);
    }

    private static void transferDenseToHashIndex(Map<Object, Object> hashIndex, Object[] denseIndex) {
        if (denseIndex == null) {
            return;
        }
        for (int key = 0; key < denseIndex.length; key++) {
            Object slotValue = denseIndex[key];
            if (slotValue == null) {
                continue;
            }
            if (slotValue instanceof Object[] childValues) {
                putHashedChildValue(hashIndex, key, childValues);
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Object[]> bucket = (List<Object[]>) slotValue;
            for (Object[] childValues : bucket) {
                putHashedChildValue(hashIndex, key, childValues);
            }
        }
    }

    private static void putHashedChildValue(Map<Object, Object> index, Object joinKey, Object[] childValues) {
        Object existing = index.putIfAbsent(joinKey, childValues);
        if (existing == null) {
            return;
        }
        if (existing instanceof Object[] existingValues) {
            ArrayList<Object[]> bucket = new ArrayList<>(2);
            bucket.add(existingValues);
            bucket.add(childValues);
            index.put(joinKey, bucket);
            return;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> bucket = (List<Object[]>) existing;
        bucket.add(childValues);
    }

    private static boolean isDenseKeyRangeReasonable(int maxDenseKey, int populatedKeys) {
        if (maxDenseKey < 0 || populatedKeys <= 0) {
            return false;
        }
        int denseArrayLength = maxDenseKey + 1;
        return denseArrayLength <= populatedKeys * DENSE_INDEX_GROWTH_FACTOR;
    }

    private static Object[] materializeJoinedRow(Object[] parentValues,
                                                 Object[] childValues,
                                                 JoinCompilePlan plan) {
        Object[] values = new Object[plan.schemaFields().size()];
        System.arraycopy(parentValues, 0, values, 0, parentValues.length);
        System.arraycopy(childValues, 0, values, parentValues.length, childValues.length);
        applyComputedValues(values, plan);
        return values;
    }

    private static void applyComputedValues(Object[] values, JoinCompilePlan plan) {
        for (ComputedFieldPlan computedPlan : plan.computedPlans()) {
            values[computedPlan.outputIndex()] = castNumericValue(
                    computedPlan.expression().evaluate(values),
                    computedPlan.definition().outputType()
            );
        }
    }

    private static List<Object[]> filterRows(List<Object[]> rows, FilterExecutionPlan plan) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        FastRowMatcher matcher = compileRowMatcher(plan.getRulesByFieldIndex());
        if (matcher == MatchAllRowMatcher.INSTANCE) {
            return rows;
        }
        ArrayList<Object[]> results = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            if (matcher.matches(row)) {
                results.add(row);
            }
        }
        return results;
    }

    private static FastRowMatcher compileRowMatcher(Map<Integer, List<CompiledRule>> rulesByField) {
        if (rulesByField == null || rulesByField.isEmpty()) {
            return MatchAllRowMatcher.INSTANCE;
        }
        if (rulesByField.size() == 1) {
            Map.Entry<Integer, List<CompiledRule>> entry = rulesByField.entrySet().iterator().next();
            List<CompiledRule> rules = entry.getValue();
            if (rules != null && rules.size() == 1) {
                return compileSingleRuleMatcher(entry.getKey(), rules.get(0));
            }
        }
        FastRuleGroup[] ruleGroups = compileRuleGroups(rulesByField);
        return ruleGroups.length == 0 ? MatchAllRowMatcher.INSTANCE : new GenericFastRowMatcher(ruleGroups);
    }

    private static FastRowMatcher compileSingleRuleMatcher(int fieldIndex, CompiledRule rule) {
        if (rule == null) {
            return MatchAllRowMatcher.INSTANCE;
        }
        if (rule.compareValue instanceof Number number && isNumericClause(rule.clause)) {
            return new SingleNumericRuleMatcher(fieldIndex, number.doubleValue(), rule);
        }
        return new SingleRuleMatcher(fieldIndex, rule);
    }

    private static FastRuleGroup[] compileRuleGroups(Map<Integer, List<CompiledRule>> rulesByField) {
        if (rulesByField == null || rulesByField.isEmpty()) {
            return new FastRuleGroup[0];
        }
        FastRuleGroup[] groups = new FastRuleGroup[rulesByField.size()];
        int index = 0;
        for (Map.Entry<Integer, List<CompiledRule>> entry : rulesByField.entrySet()) {
            groups[index++] = new FastRuleGroup(entry.getKey(), entry.getValue().toArray(CompiledRule[]::new));
        }
        return groups;
    }

    private static boolean matchesRuleGroups(Object[] row, FastRuleGroup[] ruleGroups) {
        if (ruleGroups.length == 0) {
            return true;
        }
        boolean andMatched = false;
        boolean andFailed = false;
        boolean orMatched = false;

        outer:
        for (FastRuleGroup group : ruleGroups) {
            int fieldIndex = group.fieldIndex();
            if (fieldIndex < 0 || fieldIndex >= row.length) {
                continue;
            }
            Object fieldValue = row[fieldIndex];
            for (CompiledRule rule : group.rules()) {
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

    private static boolean isNumericClause(Clauses clause) {
        return clause == Clauses.BIGGER
                || clause == Clauses.BIGGER_EQUAL
                || clause == Clauses.EQUAL
                || clause == Clauses.IN
                || clause == Clauses.NOT_BIGGER
                || clause == Clauses.NOT_EQUAL
                || clause == Clauses.NOT_SMALLER
                || clause == Clauses.SMALLER
                || clause == Clauses.SMALLER_EQUAL;
    }

    private static boolean compareNumbers(double left, double right, Clauses clause) {
        return switch (clause) {
            case BIGGER -> left > right;
            case BIGGER_EQUAL, NOT_SMALLER -> left >= right;
            case EQUAL, IN -> left == right;
            case NOT_BIGGER, SMALLER_EQUAL -> left <= right;
            case NOT_EQUAL -> left != right;
            case SMALLER -> left < right;
            default -> false;
        };
    }

    private static List<Object[]> orderRows(List<Object[]> rows, Sort sortMethod, FilterExecutionPlan plan) {
        return orderRows(rows, sortMethod, plan, null);
    }

    private static List<Object[]> orderRows(List<Object[]> rows,
                                            Sort sortMethod,
                                            FilterExecutionPlan plan,
                                            Integer limit) {
        if (sortMethod == null || rows == null || rows.isEmpty()) {
            return rows;
        }
        List<FilterExecutionPlan.OrderColumn> columns = plan.getOrderColumns();
        if (columns.isEmpty()) {
            return rows;
        }
        int topKLimit = normalizedTopKLimit(limit, rows.size());
        if (shouldUseTopK(topKLimit, rows.size())) {
            return topKOrderedRows(rows, columns, sortMethod, topKLimit);
        }
        ArrayList<Object[]> ordered = new ArrayList<>(rows);
        ordered.sort((left, right) -> compareByColumns(left, right, columns, sortMethod));
        return ordered;
    }

    private static int normalizedTopKLimit(Integer limit, int rowCount) {
        if (limit == null || limit <= 0 || rowCount <= 0 || limit >= rowCount) {
            return -1;
        }
        return limit;
    }

    private static boolean shouldUseTopK(int limit, int rowCount) {
        return limit > 0
                && rowCount >= TOP_K_MIN_INPUT_ROWS
                && limit <= TOP_K_MAX_LIMIT
                && limit * TOP_K_ROW_RATIO <= rowCount;
    }

    private static List<Object[]> topKOrderedRows(List<Object[]> rows,
                                                  List<FilterExecutionPlan.OrderColumn> columns,
                                                  Sort sortMethod,
                                                  int limit) {
        int[] heap = new int[limit];
        int size = 0;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            if (size < limit) {
                heap[size] = rowIndex;
                siftUpWorst(heap, size, rows, columns, sortMethod);
                size++;
                continue;
            }
            if (compareRowIndexes(rowIndex, heap[0], rows, columns, sortMethod) < 0) {
                heap[0] = rowIndex;
                siftDownWorst(heap, 0, size, rows, columns, sortMethod);
            }
        }

        int[] orderedIndexes = drainHeapBestFirst(heap, size, rows, columns, sortMethod);
        ArrayList<Object[]> results = new ArrayList<>(size);
        for (int i = 0; i < orderedIndexes.length; i++) {
            results.add(rows.get(orderedIndexes[i]));
        }
        return results;
    }

    private static void siftUpWorst(int[] heap,
                                    int index,
                                    List<Object[]> rows,
                                    List<FilterExecutionPlan.OrderColumn> columns,
                                    Sort sortMethod) {
        int current = index;
        while (current > 0) {
            int parent = (current - 1) >>> 1;
            if (compareRowIndexes(heap[current], heap[parent], rows, columns, sortMethod) <= 0) {
                break;
            }
            swap(heap, current, parent);
            current = parent;
        }
    }

    private static void siftDownWorst(int[] heap,
                                      int index,
                                      int size,
                                      List<Object[]> rows,
                                      List<FilterExecutionPlan.OrderColumn> columns,
                                      Sort sortMethod) {
        int current = index;
        while (true) {
            int left = (current << 1) + 1;
            if (left >= size) {
                return;
            }
            int right = left + 1;
            int worstChild = left;
            if (right < size && compareRowIndexes(heap[right], heap[left], rows, columns, sortMethod) > 0) {
                worstChild = right;
            }
            if (compareRowIndexes(heap[worstChild], heap[current], rows, columns, sortMethod) <= 0) {
                return;
            }
            swap(heap, current, worstChild);
            current = worstChild;
        }
    }

    private static int[] drainHeapBestFirst(int[] heap,
                                            int size,
                                            List<Object[]> rows,
                                            List<FilterExecutionPlan.OrderColumn> columns,
                                            Sort sortMethod) {
        int[] ordered = new int[size];
        int heapSize = size;
        for (int target = size - 1; target >= 0; target--) {
            ordered[target] = heap[0];
            heapSize--;
            if (heapSize == 0) {
                break;
            }
            heap[0] = heap[heapSize];
            siftDownWorst(heap, 0, heapSize, rows, columns, sortMethod);
        }
        return ordered;
    }

    private static int compareRowIndexes(int leftIndex,
                                         int rightIndex,
                                         List<Object[]> rows,
                                         List<FilterExecutionPlan.OrderColumn> columns,
                                         Sort sortMethod) {
        int compared = compareByColumns(rows.get(leftIndex), rows.get(rightIndex), columns, sortMethod);
        if (compared != 0) {
            return compared;
        }
        return Integer.compare(leftIndex, rightIndex);
    }

    private static void swap(int[] array, int left, int right) {
        int temp = array[left];
        array[left] = array[right];
        array[right] = temp;
    }

    private static int compareByColumns(Object[] left,
                                        Object[] right,
                                        List<FilterExecutionPlan.OrderColumn> columns,
                                        Sort sortMethod) {
        for (FilterExecutionPlan.OrderColumn column : columns) {
            Object leftValue = column.fieldIndex() < left.length ? left[column.fieldIndex()] : null;
            Object rightValue = column.fieldIndex() < right.length ? right[column.fieldIndex()] : null;
            int compared = compareValues(leftValue, rightValue, column.dateFormat());
            if (compared != 0) {
                return Sort.DESC.equals(sortMethod) ? -compared : compared;
            }
        }
        return 0;
    }

    private static int compareValues(Object leftValue, Object rightValue, String dateFormat) {
        if (leftValue == null && rightValue == null) {
            return 0;
        }
        if (leftValue == null) {
            return -1;
        }
        if (rightValue == null) {
            return 1;
        }
        if (leftValue instanceof Number && rightValue instanceof Number) {
            return Double.compare(((Number) leftValue).doubleValue(), ((Number) rightValue).doubleValue());
        }
        if (leftValue instanceof java.util.Date && rightValue instanceof java.util.Date) {
            return ((java.util.Date) leftValue).compareTo((java.util.Date) rightValue);
        }
        if (leftValue instanceof Boolean && rightValue instanceof Boolean) {
            return Boolean.compare((Boolean) leftValue, (Boolean) rightValue);
        }
        if (leftValue instanceof Comparable && leftValue.getClass().isInstance(rightValue)) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            int compared = ((Comparable) leftValue).compareTo(rightValue);
            return compared;
        }
        String leftText = ObjectUtil.castToString(leftValue, dateFormat);
        String rightText = ObjectUtil.castToString(rightValue, dateFormat);
        if (leftText == null && rightText == null) {
            return 0;
        }
        if (leftText == null) {
            return -1;
        }
        if (rightText == null) {
            return 1;
        }
        return leftText.compareTo(rightText);
    }

    private static List<String> outputSchema(FilterQueryBuilder builder,
                                             List<String> schemaFields,
                                             FilterExecutionPlan plan) {
        if (builder.getReturnFields().isEmpty()) {
            return schemaFields;
        }
        ArrayList<String> fields = new ArrayList<>(plan.getReturnFieldIndexes().size());
        for (int index : plan.getReturnFieldIndexes()) {
            if (index >= 0 && index < schemaFields.size()) {
                fields.add(schemaFields.get(index));
            }
        }
        return List.copyOf(fields);
    }

    private static int[] outputIndexes(FilterQueryBuilder builder,
                                       List<String> schemaFields,
                                       FilterExecutionPlan plan) {
        if (builder.getReturnFields().isEmpty()) {
            return null;
        }
        List<Integer> returnIndexes = plan.getReturnFieldIndexes();
        int[] indexes = new int[returnIndexes.size()];
        for (int i = 0; i < returnIndexes.size(); i++) {
            indexes[i] = returnIndexes.get(i);
        }
        return indexes;
    }

    private static Object castNumericValue(double value, Class<?> outputType) {
        if (outputType == Integer.class) {
            return (int) Math.round(value);
        }
        if (outputType == Long.class) {
            return Math.round(value);
        }
        if (outputType == Float.class) {
            return (float) value;
        }
        if (outputType == Short.class) {
            return (short) Math.round(value);
        }
        if (outputType == Byte.class) {
            return (byte) Math.round(value);
        }
        return value;
    }
    static record FastArrayState(List<String> schemaFields,
                                 Map<String, Class<?>> schemaTypes,
                                 List<Object[]> rows) {
    }

    private record JoinCompilePlan(ReflectionUtil.FlatRowReadPlan parentReadPlan,
                                   ReflectionUtil.FlatRowReadPlan childReadPlan,
                                   int parentJoinIndex,
                                   int childJoinIndex,
                                   int baseFieldCount,
                                   List<String> schemaFields,
                                   Map<String, Class<?>> schemaTypes,
                                   ComputedFieldPlan[] computedPlans,
                                   Object[] nullChildValues) {
    }

    private record ComputedFieldPlan(ComputedFieldDefinition definition,
                                     SqlExpressionEvaluator.BoundExpression expression,
                                     int outputIndex) {
    }

    private interface ChildIndex {
        Object get(Object joinKey);
    }

    private record HashChildIndex(Map<Object, Object> index) implements ChildIndex {
        @Override
        public Object get(Object joinKey) {
            return index.get(joinKey);
        }
    }

    private record DenseIntChildIndex(Object[] slots) implements ChildIndex {
        @Override
        public Object get(Object joinKey) {
            if (!(joinKey instanceof Integer intKey)) {
                return null;
            }
            if (intKey < 0 || intKey >= slots.length) {
                return null;
            }
            return slots[intKey];
        }
    }

    private record FastRuleGroup(int fieldIndex, CompiledRule[] rules) {
    }

    private interface FastRowMatcher {
        boolean matches(Object[] row);
    }

    private enum MatchAllRowMatcher implements FastRowMatcher {
        INSTANCE;

        @Override
        public boolean matches(Object[] row) {
            return true;
        }
    }

    private record GenericFastRowMatcher(FastRuleGroup[] ruleGroups) implements FastRowMatcher {
        @Override
        public boolean matches(Object[] row) {
            return matchesRuleGroups(row, ruleGroups);
        }
    }

    private record SingleRuleMatcher(int fieldIndex, CompiledRule rule) implements FastRowMatcher {
        @Override
        public boolean matches(Object[] row) {
            if (row == null || fieldIndex < 0 || fieldIndex >= row.length) {
                return false;
            }
            return ObjectUtil.compareObject(row[fieldIndex], rule.compareValue, rule.clause, rule.dateFormat);
        }
    }

    private record SingleNumericRuleMatcher(int fieldIndex,
                                            double compareValue,
                                            CompiledRule rule) implements FastRowMatcher {
        @Override
        public boolean matches(Object[] row) {
            if (row == null || fieldIndex < 0 || fieldIndex >= row.length) {
                return false;
            }
            Object fieldValue = row[fieldIndex];
            if (fieldValue instanceof Number number) {
                return compareNumbers(number.doubleValue(), compareValue, rule.clause);
            }
            return ObjectUtil.compareObject(fieldValue, rule.compareValue, rule.clause, rule.dateFormat);
        }
    }
}
