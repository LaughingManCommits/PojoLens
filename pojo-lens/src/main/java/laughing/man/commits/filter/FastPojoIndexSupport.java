package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
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

    interface SourceIndexLookup {
        List<?> lookup(String fieldName, Object value);
    }
}
