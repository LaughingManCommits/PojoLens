package laughing.man.commits.computed.internal;

import laughing.man.commits.computed.ComputedFieldDefinition;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal helpers for applying computed-field registries to row schemas and row values.
 */
public final class ComputedFieldSupport {

    private ComputedFieldSupport() {
    }

    public static Map<String, Class<?>> augmentFieldTypes(Map<String, Class<?>> baseFieldTypes,
                                                          ComputedFieldRegistry registry) {
        LinkedHashMap<String, Class<?>> augmented = new LinkedHashMap<>(baseFieldTypes);
        for (ComputedFieldDefinition definition : resolveApplicableDefinitions(baseFieldTypes.keySet(), registry)) {
            augmented.put(definition.name(), definition.outputType());
        }
        return augmented;
    }

    public static Set<String> augmentFieldNames(Collection<String> baseFieldNames,
                                                ComputedFieldRegistry registry) {
        LinkedHashSet<String> augmented = new LinkedHashSet<>(baseFieldNames);
        for (ComputedFieldDefinition definition : resolveApplicableDefinitions(baseFieldNames, registry)) {
            augmented.add(definition.name());
        }
        return augmented;
    }

    public static List<ComputedFieldDefinition> resolveApplicableDefinitions(Collection<String> baseFieldNames,
                                                                             ComputedFieldRegistry registry) {
        if (registry == null || registry.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> available = new LinkedHashSet<>(baseFieldNames);
        ArrayList<ComputedFieldDefinition> resolved = new ArrayList<>();
        LinkedHashMap<String, ComputedFieldDefinition> unresolved = new LinkedHashMap<>(registry.definitions());
        boolean advanced;
        do {
            advanced = false;
            List<String> ready = new ArrayList<>();
            for (Map.Entry<String, ComputedFieldDefinition> entry : unresolved.entrySet()) {
                if (available.containsAll(entry.getValue().dependencies())) {
                    ready.add(entry.getKey());
                }
            }
            for (String name : ready) {
                ComputedFieldDefinition definition = unresolved.remove(name);
                resolved.add(definition);
                available.add(definition.name());
                advanced = true;
            }
        } while (advanced && !unresolved.isEmpty());
        return resolved;
    }

    public static List<QueryRow> materializeRows(List<QueryRow> rows, ComputedFieldRegistry registry) {
        return materializeRows(rows, registry, true);
    }

    public static List<QueryRow> materializeRowsInPlace(List<QueryRow> rows, ComputedFieldRegistry registry) {
        return materializeRows(rows, registry, false);
    }

    private static List<QueryRow> materializeRows(List<QueryRow> rows,
                                                  ComputedFieldRegistry registry,
                                                  boolean copyRows) {
        if (rows == null || rows.isEmpty() || registry == null || registry.isEmpty()) {
            return rows;
        }
        Set<String> baseFields = rowFieldNames(rows.get(0));
        List<ComputedFieldDefinition> definitions = resolveApplicableDefinitions(baseFields, registry);
        if (definitions.isEmpty()) {
            return rows;
        }
        MaterializationPlan plan = buildMaterializationPlan(rows.get(0), definitions);
        if (!copyRows) {
            for (QueryRow row : rows) {
                materializeRow(row, plan, false);
            }
            return rows;
        }
        ArrayList<QueryRow> materialized = new ArrayList<>(rows.size());
        for (QueryRow row : rows) {
            materialized.add(materializeRow(row, plan, true));
        }
        return materialized;
    }

    public static List<String> explainEntries(ComputedFieldRegistry registry, Collection<String> availableFields) {
        if (registry == null || registry.isEmpty()) {
            return List.of();
        }
        List<ComputedFieldDefinition> definitions = availableFields == null
                ? new ArrayList<>(registry.definitions().values())
                : resolveApplicableDefinitions(availableFields, registry);
        ArrayList<String> entries = new ArrayList<>(definitions.size());
        for (ComputedFieldDefinition definition : definitions) {
            entries.add(definition.name()
                    + ":" + definition.expression()
                    + ":" + definition.outputType().getSimpleName());
        }
        return entries;
    }

    public static List<String> usedExplainEntries(ComputedFieldRegistry registry, Set<String> usedNames) {
        if (registry == null || registry.isEmpty() || usedNames == null || usedNames.isEmpty()) {
            return List.of();
        }
        ArrayList<String> entries = new ArrayList<>();
        for (String usedName : usedNames) {
            ComputedFieldDefinition definition = registry.get(usedName);
            if (definition != null) {
                entries.add(definition.name()
                        + ":" + definition.expression()
                        + ":" + definition.outputType().getSimpleName());
            }
        }
        return entries;
    }

    private static MaterializationPlan buildMaterializationPlan(QueryRow sampleRow,
                                                                List<ComputedFieldDefinition> definitions) {
        List<? extends QueryField> sourceFields = sampleRow == null ? null : sampleRow.getFields();
        int baseFieldCount = sourceFields == null ? 0 : sourceFields.size();
        HashMap<String, ValueSource> availableValues = new HashMap<>(Math.max(16, baseFieldCount + definitions.size() * 2));
        HashMap<String, Integer> outputIndexes = new HashMap<>(Math.max(16, baseFieldCount + definitions.size() * 2));
        CompiledComputedField[] compiledDefinitions = new CompiledComputedField[definitions.size()];
        int appendedFieldCount = 0;

        if (sourceFields != null) {
            for (int i = 0; i < sourceFields.size(); i++) {
                QueryField sourceField = sourceFields.get(i);
                if (sourceField == null || sourceField.getFieldName() == null) {
                    continue;
                }
                String fieldName = sourceField.getFieldName();
                availableValues.put(fieldName, ValueSource.source(i));
                outputIndexes.put(fieldName, i);
            }
        }

        for (int i = 0; i < definitions.size(); i++) {
            ComputedFieldDefinition definition = definitions.get(i);
            SqlExpressionEvaluator.CompiledExpression expression =
                    SqlExpressionEvaluator.compileNumeric(definition.expression());
            String[] dependencyNames = expression.identifiers().toArray(String[]::new);
            ValueSource[] dependencySources = new ValueSource[dependencyNames.length];
            for (int dependencyIndex = 0; dependencyIndex < dependencyNames.length; dependencyIndex++) {
                dependencySources[dependencyIndex] = availableValues.get(dependencyNames[dependencyIndex]);
            }

            Integer outputIndex = outputIndexes.get(definition.name());
            boolean replacesExisting = outputIndex != null;
            if (!replacesExisting) {
                outputIndex = baseFieldCount + appendedFieldCount;
                appendedFieldCount++;
            }

            compiledDefinitions[i] = new CompiledComputedField(
                    definition,
                    expression,
                    outputIndex,
                    replacesExisting,
                    dependencyNames,
                    dependencySources
            );
            availableValues.put(definition.name(), ValueSource.computed(i));
            outputIndexes.put(definition.name(), outputIndex);
        }

        return new MaterializationPlan(baseFieldCount, baseFieldCount + appendedFieldCount, compiledDefinitions);
    }

    private static QueryRow materializeRow(QueryRow row,
                                           MaterializationPlan plan,
                                           boolean copyRow) {
        if (row == null) {
            return null;
        }
        List<QueryField> materializedFields = materializeFields(row, plan);
        if (!copyRow) {
            row.setFields(materializedFields);
            return row;
        }
        QueryRow copy = new QueryRow();
        copy.setRowId(row.getRowId());
        copy.setRowType(row.getRowType());
        copy.setFields(materializedFields);
        return copy;
    }

    private static final class CompiledComputedField {
        private final ComputedFieldDefinition definition;
        private final SqlExpressionEvaluator.CompiledExpression expression;
        private final int outputIndex;
        private final boolean replacesExisting;
        private final String[] dependencyNames;
        private final ValueSource[] dependencySources;

        private CompiledComputedField(ComputedFieldDefinition definition,
                                      SqlExpressionEvaluator.CompiledExpression expression,
                                      int outputIndex,
                                      boolean replacesExisting,
                                      String[] dependencyNames,
                                      ValueSource[] dependencySources) {
            this.definition = definition;
            this.expression = expression;
            this.outputIndex = outputIndex;
            this.replacesExisting = replacesExisting;
            this.dependencyNames = dependencyNames;
            this.dependencySources = dependencySources;
        }

        private ComputedFieldDefinition definition() {
            return definition;
        }

        private SqlExpressionEvaluator.CompiledExpression expression() {
            return expression;
        }

        private int outputIndex() {
            return outputIndex;
        }

        private boolean replacesExisting() {
            return replacesExisting;
        }

        private Object resolveValue(String identifier,
                                    List<? extends QueryField> sourceFields,
                                    Object[] computedValues) {
            for (int i = 0; i < dependencyNames.length; i++) {
                if (!dependencyNames[i].equals(identifier)) {
                    continue;
                }
                ValueSource source = dependencySources[i];
                if (source == null) {
                    return null;
                }
                if (source.computed()) {
                    return computedValues[source.index()];
                }
                int sourceIndex = source.index();
                if (sourceFields == null || sourceIndex < 0 || sourceIndex >= sourceFields.size()) {
                    return null;
                }
                QueryField field = sourceFields.get(sourceIndex);
                return field == null ? null : field.getValue();
            }
            return null;
        }
    }

    private static List<QueryField> materializeFields(QueryRow row, MaterializationPlan plan) {
        List<? extends QueryField> sourceFields = row.getFields();
        if (sourceFields == null || sourceFields.size() != plan.baseFieldCount()) {
            return materializeFieldsLegacy(sourceFields, plan.compiledDefinitions());
        }

        ArrayList<QueryField> fields = new ArrayList<>(plan.totalFieldCount());
        for (int i = 0; i < sourceFields.size(); i++) {
            fields.add(sourceFields.get(i));
        }

        Object[] computedValues = new Object[plan.compiledDefinitions().length];
        for (int i = 0; i < plan.compiledDefinitions().length; i++) {
            CompiledComputedField definition = plan.compiledDefinitions()[i];
            Object value = castNumericValue(
                    definition.expression().evaluate(identifier -> definition.resolveValue(identifier, sourceFields, computedValues)),
                    definition.definition().outputType()
            );
            computedValues[i] = value;
            QueryField computedField = newQueryField(definition.definition().name(), value);
            if (definition.replacesExisting()) {
                fields.set(definition.outputIndex(), computedField);
            } else {
                fields.add(computedField);
            }
        }
        return fields;
    }

    private static List<QueryField> materializeFieldsLegacy(List<? extends QueryField> sourceFields,
                                                            CompiledComputedField[] definitions) {
        ArrayList<QueryField> fields = new ArrayList<>(sourceFields == null ? definitions.length : sourceFields.size() + definitions.length);
        LinkedHashMap<String, Object> values = new LinkedHashMap<>(Math.max(16, fields.size() * 2));
        if (sourceFields != null) {
            for (QueryField field : sourceFields) {
                fields.add(field);
                values.put(field.getFieldName(), field.getValue());
            }
        }
        for (CompiledComputedField definition : definitions) {
            Object value = castNumericValue(
                    definition.expression().evaluate(values::get),
                    definition.definition().outputType()
            );
            values.put(definition.definition().name(), value);
            upsertField(fields, definition.definition().name(), value);
        }
        return fields;
    }

    private static void upsertField(List<QueryField> fields, String name, Object value) {
        for (int i = 0; i < fields.size(); i++) {
            QueryField field = fields.get(i);
            if (name.equals(field.getFieldName())) {
                fields.set(i, newQueryField(name, value));
                return;
            }
        }
        fields.add(newQueryField(name, value));
    }

    private static QueryField newQueryField(String name, Object value) {
        QueryField field = new QueryField();
        field.setFieldName(name);
        field.setValue(value);
        return field;
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

    private static Set<String> rowFieldNames(QueryRow row) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (row == null || row.getFields() == null) {
            return names;
        }
        for (QueryField field : row.getFields()) {
            names.add(field.getFieldName());
        }
        return names;
    }

    private record MaterializationPlan(int baseFieldCount,
                                       int totalFieldCount,
                                       CompiledComputedField[] compiledDefinitions) {
    }

    private record ValueSource(boolean computed, int index) {
        private static ValueSource source(int index) {
            return new ValueSource(false, index);
        }

        private static ValueSource computed(int index) {
            return new ValueSource(true, index);
        }
    }
}

