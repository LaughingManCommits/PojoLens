package laughing.man.commits.computed.internal;

import laughing.man.commits.computed.ComputedFieldDefinition;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;

import java.util.ArrayList;
import java.util.Collection;
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
        if (rows == null || rows.isEmpty() || registry == null || registry.isEmpty()) {
            return rows;
        }
        Set<String> baseFields = rowFieldNames(rows.get(0));
        List<ComputedFieldDefinition> definitions = resolveApplicableDefinitions(baseFields, registry);
        if (definitions.isEmpty()) {
            return rows;
        }
        ArrayList<QueryRow> materialized = new ArrayList<>(rows.size());
        for (QueryRow row : rows) {
            materialized.add(materializeRow(row, definitions));
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

    private static QueryRow materializeRow(QueryRow row, List<ComputedFieldDefinition> definitions) {
        QueryRow copy = new QueryRow();
        copy.setRowId(row.getRowId());
        copy.setRowType(row.getRowType());
        List<QueryField> fields = new ArrayList<>();
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        if (row.getFields() != null) {
            for (QueryField field : row.getFields()) {
                QueryField fieldCopy = new QueryField();
                fieldCopy.setFieldName(field.getFieldName());
                fieldCopy.setValue(field.getValue());
                fields.add(fieldCopy);
                values.put(field.getFieldName(), field.getValue());
            }
        }
        for (ComputedFieldDefinition definition : definitions) {
            Object value = castNumericValue(
                    SqlExpressionEvaluator.evaluateNumeric(definition.expression(), values::get),
                    definition.outputType()
            );
            values.put(definition.name(), value);
            upsertField(fields, definition.name(), value);
        }
        copy.setFields(fields);
        return copy;
    }

    private static void upsertField(List<QueryField> fields, String name, Object value) {
        for (QueryField field : fields) {
            if (name.equals(field.getFieldName())) {
                field.setValue(value);
                return;
            }
        }
        QueryField computedField = new QueryField();
        computedField.setFieldName(name);
        computedField.setValue(value);
        fields.add(computedField);
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
}

