package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.computed.ComputedFieldDefinition;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.computed.internal.ComputedFieldSupport;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.ReflectionUtil;
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
        Object parentSample = firstNonNull(parents);
        Object childSample = firstNonNull(children);
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

        Map<Object, Object> childIndex = buildChildIndex(children, plan);
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
        List<Object[]> ordered = orderRows(filtered, sortMethod, plan);
        List<String> outputSchema = outputSchema(builder, state.schemaFields(), plan);
        int[] outputIndexes = outputIndexes(builder, state.schemaFields(), plan);
        List<Object[]> limited = applyLimit(ordered, builder.getLimit());
        return ReflectionUtil.toClassList(projectionClass, limited, outputSchema, outputIndexes);
    }

    static List<QueryRow> toQueryRows(FastArrayState state) {
        ArrayList<QueryRow> rows = new ArrayList<>(state.rows().size());
        for (Object[] values : state.rows()) {
            ArrayList<QueryField> fields = new ArrayList<>(state.schemaFields().size());
            for (int i = 0; i < state.schemaFields().size(); i++) {
                QueryField field = new QueryField();
                field.setFieldName(state.schemaFields().get(i));
                field.setValue(i < values.length ? values[i] : null);
                fields.add(field);
            }
            QueryRow row = new QueryRow();
            row.setFields(fields);
            rows.add(row);
        }
        return rows;
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
                || !builder.getAllOfGroups().isEmpty()
                || !builder.getAnyOfGroups().isEmpty()) {
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

        LinkedHashMap<String, Integer> schemaIndexByName = new LinkedHashMap<>(Math.max(16, schemaFields.size() * 2));
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

        LinkedHashMap<String, Class<?>> schemaTypes = new LinkedHashMap<>(Math.max(16, schemaFields.size() * 2));
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
        if (fieldName == null || fieldName.isBlank()) {
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

    private static Map<Object, Object> buildChildIndex(List<?> children, JoinCompilePlan plan) {
        HashMap<Object, Object> index = new HashMap<>(expectedMapSize(children.size()));
        for (Object child : children) {
            if (child == null) {
                continue;
            }
            Object[] childValues = ReflectionUtil.readFlatRowValues(child, plan.childReadPlan());
            Object joinKey = childValues[plan.childJoinIndex()];
            Object existing = index.putIfAbsent(joinKey, childValues);
            if (existing == null) {
                continue;
            }
            if (existing instanceof Object[] existingValues) {
                ArrayList<Object[]> bucket = new ArrayList<>(2);
                bucket.add(existingValues);
                bucket.add(childValues);
                index.put(joinKey, bucket);
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Object[]> bucket = (List<Object[]>) existing;
            bucket.add(childValues);
        }
        return index;
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
        if (sortMethod == null || rows == null || rows.isEmpty()) {
            return rows;
        }
        List<FilterExecutionPlan.OrderColumn> columns = plan.getOrderColumns();
        if (columns.isEmpty()) {
            return rows;
        }
        ArrayList<Object[]> ordered = new ArrayList<>(rows);
        ordered.sort((left, right) -> compareByColumns(left, right, columns, sortMethod));
        return ordered;
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

    private static List<Object[]> applyLimit(List<Object[]> rows, Integer limit) {
        if (rows == null || limit == null || limit >= rows.size()) {
            return rows;
        }
        if (limit <= 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(rows.subList(0, limit));
    }

    private static Object firstNonNull(List<?> rows) {
        if (rows == null) {
            return null;
        }
        for (Object row : rows) {
            if (row != null) {
                return row;
            }
        }
        return null;
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

    private static int expectedMapSize(int sourceSize) {
        if (sourceSize <= 0) {
            return 16;
        }
        return (int) ((sourceSize / 0.75f) + 1.0f);
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
