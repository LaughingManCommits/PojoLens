package laughing.man.commits.filter;

import laughing.man.commits.internal.builder.FilterQueryBuilder;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.util.StringUtil;

import java.util.List;
import java.util.Map;

/**
 * Resolves indexed candidate rows for simple POJO filter workloads.
 */
final class FastPojoIndexSupport {

    private FastPojoIndexSupport() {
    }

    static List<?> indexedCandidates(FilterQueryBuilder builder, SourceIndexLookup lookup) {
        List<String> indexedFields = builder.getIndexedFields();
        if (indexedFields.isEmpty()) {
            return null;
        }

        Map<String, List<String>> ruleIdsByField = builder.getFilterIDs();
        if (ruleIdsByField.isEmpty()) {
            return null;
        }
        if (builder.getFilterSeparator().containsValue(Separator.OR)) {
            // An OR rule can admit rows the equality index would exclude.
            return null;
        }

        List<?> best = null;
        for (String indexedField : indexedFields) {
            if (StringUtil.isNullOrBlank(indexedField)) {
                continue;
            }
            List<String> ids = ruleIdsByField.get(indexedField);
            if (ids == null || ids.isEmpty()) {
                continue;
            }
            for (String id : ids) {
                if (!Clauses.EQUAL.equals(builder.getFilterClause().get(id))) {
                    continue;
                }
                if (!Separator.AND.equals(builder.getFilterSeparator().get(id))) {
                    continue;
                }
                Object compareValue = builder.getFilterValues().get(id);
                List<?> candidates = lookup.lookup(indexedField, compareValue);
                if (candidates == null) {
                    continue;
                }
                if (best == null || candidates.size() < best.size()) {
                    best = candidates;
                }
            }
        }
        return best;
    }

    /**
     * True when {@code Map} lookup by {@code equals} gives the same answer as the engine's
     * EQUAL comparison for this value; other types (dates, floating point, BigDecimal,
     * collections) must scan.
     */
    static boolean isIndexSafeValue(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Short
                || value instanceof Byte;
    }

    static Class<?> indexKeyType(Object value) {
        return value instanceof Enum<?> constant ? constant.getDeclaringClass() : value.getClass();
    }

    interface SourceIndexLookup {
        /**
         * Returns the indexed rows equal to {@code value}, or {@code null} when the index
         * cannot answer (the caller then scans).
         */
        List<?> lookup(String fieldName, Object value);
    }
}
