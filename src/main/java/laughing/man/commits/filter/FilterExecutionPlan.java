package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static laughing.man.commits.EngineDefaults.SDF;

public final class FilterExecutionPlan {

    private final Map<String, Integer> fieldIndexByName = new HashMap<>();
    private final Map<String, Integer> fieldIndexByLowerName = new HashMap<>();
    private final Map<Integer, List<CompiledRule>> rulesByFieldIndex = new LinkedHashMap<>();

    FilterExecutionPlan(FilterQueryBuilder builder) {
        List<QueryRow> rows = builder.getRows();
        if (rows != null && !rows.isEmpty()) {
            List<? extends QueryField> firstFields = rows.get(0).getFields();
            for (int i = 0; i < firstFields.size(); i++) {
                String name = firstFields.get(i).getFieldName();
                fieldIndexByName.put(name, i);
                fieldIndexByLowerName.put(name.toLowerCase(Locale.ROOT), i);
            }
        }
        compileRules(builder);
    }

    Map<Integer, List<CompiledRule>> getRulesByFieldIndex() {
        return rulesByFieldIndex;
    }

    int findFieldIndex(String fieldName) {
        Integer index = fieldIndexByName.get(fieldName);
        return index == null ? -1 : index;
    }

    int findFieldIndexIgnoreCase(String fieldName) {
        if (fieldName == null) {
            return -1;
        }
        Integer index = fieldIndexByLowerName.get(fieldName.toLowerCase(Locale.ROOT));
        return index == null ? -1 : index;
    }

    List<Integer> resolveFieldIndexes(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> indexes = new ArrayList<>(names.size());
        for (String name : names) {
            int index = findFieldIndex(name);
            if (index >= 0) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private void compileRules(FilterQueryBuilder builder) {
        Map<String, String> filterFields = builder.getFilterFields();
        Map<String, String> filterDateFormats = builder.getFilterDateFormats();
        Map<String, Object> filterValues = builder.getFilterValues();
        Map<String, Clauses> filterClauses = builder.getFilterClause();
        Map<String, Separator> filterSeparators = builder.getFilterSeparator();
        for (Map.Entry<String, List<String>> entry : builder.getFilterIDs().entrySet()) {
            String fieldName = entry.getKey();
            int fieldIndex = findFieldIndex(fieldName);
            if (fieldIndex < 0) {
                continue;
            }
            for (String fieldID : entry.getValue()) {
                String configuredField = filterFields.get(fieldID);
                if (configuredField == null) {
                    continue;
                }
                if (!fieldName.equals(configuredField)) {
                    continue;
                }
                String dateFormat = filterDateFormats.getOrDefault(fieldID, SDF);
                CompiledRule rule = new CompiledRule(
                        filterValues.get(fieldID),
                        filterClauses.get(fieldID),
                        filterSeparators.get(fieldID),
                        dateFormat
                );
                rulesByFieldIndex.computeIfAbsent(fieldIndex, key -> new ArrayList<>()).add(rule);
            }
        }
    }
}

