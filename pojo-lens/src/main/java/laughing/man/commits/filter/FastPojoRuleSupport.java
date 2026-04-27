package laughing.man.commits.filter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Shared rule-compilation and field-selection utilities for fast-path filter classes.
 * <p>
 * {@link FastPojoFilterSupport} and {@link FastPojoStreamSupport} use the same
 * rule-bundle compilation and field-accumulation logic. This class owns the single
 * canonical implementation to prevent AND/OR evaluation semantics from drifting.
 */
final class FastPojoRuleSupport {

    private FastPojoRuleSupport() {
    }

    static void addKnownFields(LinkedHashSet<String> selected,
                               Map<String, Class<?>> sourceFieldTypes,
                               Iterable<String> candidateFieldNames) {
        for (String fieldName : candidateFieldNames) {
            if (sourceFieldTypes.containsKey(fieldName)) {
                selected.add(fieldName);
            }
        }
    }

    static CompiledRuleBundle compileRuleBundle(Map<Integer, List<CompiledRule>> rulesByField,
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

    record CompiledRuleBundle(int[] fieldIndexes, CompiledRule[][] compiledRules) {
    }
}
