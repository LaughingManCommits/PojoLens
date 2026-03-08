package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import laughing.man.commits.builder.QueryMetric;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class RuleCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(RuleCleaner.class);
    private final FilterQueryBuilder builder;

    RuleCleaner(FilterQueryBuilder builder) {
        this.builder = builder;
    }

    Map<Integer, String> cleanRuleFields(List<? extends QueryField> allFields,
                                         Map<Integer, String> rules, String ruleType) {
        if (!rules.isEmpty()) {
            Iterator<Map.Entry<Integer, String>> iterator = rules.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, String> groups = iterator.next();
                String fieldName = groups.getValue();
                boolean remove = true;
                for (QueryField field : allFields) {
                    if (fieldName.equals(field.getFieldName())) {
                        remove = false;
                        break;
                    }
                }
                if (remove && builder.getTimeBuckets().containsKey(fieldName)) {
                    remove = false;
                }
                if (remove && isMetricAlias(fieldName)) {
                    remove = false;
                }

                if (remove) {
                    LOG.info("No field with the name[" + fieldName + "]"
                            + " was found in row fields."
                            + "Removing field from " + ruleType + " field rules!");
                    iterator.remove();
                }
            }
        }
        return rules;
    }

    void clean(QueryRow row) {
        if (row == null) {
            return;
        }
        List<? extends QueryField> allFields = row.getFields();
        if (allFields.isEmpty()) {
            return;
        }

        if (!builder.getFilterFields().isEmpty()) {
            List<String> idsToRemove = new ArrayList<>();
            List<String> fieldNamesToRemove = new ArrayList<>();
            for (Map.Entry<String, String> filters : builder.getFilterFields().entrySet()) {
                String uniqueID = filters.getKey();
                String fieldName = filters.getValue();
                boolean remove = true;
                for (QueryField field : allFields) {
                    if (fieldName.equals(field.getFieldName())) {
                        remove = false;
                        break;
                    }
                }
                if (remove) {
                    LOG.info("No field with the name[" + fieldName + "]"
                            + " was found in row fields. "
                            + "Removed field from filter field rules!");
                    idsToRemove.add(uniqueID);
                    fieldNamesToRemove.add(fieldName);
                }
            }
            for (String uniqueID : idsToRemove) {
                builder.removeFilterRule(uniqueID);
            }
            for (String fieldName : fieldNamesToRemove) {
                builder.getFilterIDs().remove(fieldName);
            }
        }

        if (!builder.getGroupFields().isEmpty()) {
            cleanRuleFields(allFields, builder.getGroupFields(), "group by");
        }

        if (!builder.getOrderFields().isEmpty()) {
            cleanRuleFields(allFields, builder.getOrderFields(), "order by");
        }

        if (!builder.getDistinctFields().isEmpty()) {
            cleanRuleFields(allFields, builder.getDistinctFields(), "distinct");
        }

        if (!builder.getReturnFields().isEmpty()) {
            Iterator<String> fieldIterator = builder.getReturnFields().iterator();
            while (fieldIterator.hasNext()) {
                String fieldName = fieldIterator.next();
                boolean remove = true;
                for (QueryField field : allFields) {
                    if (fieldName.equals(field.getFieldName())) {
                        remove = false;
                        break;
                    }
                }
                if (remove && builder.getTimeBuckets().containsKey(fieldName)) {
                    remove = false;
                }
                if (remove && isMetricAlias(fieldName)) {
                    remove = false;
                }
                if (remove) {
                    LOG.info("No field with the name[" + fieldName + "]"
                            + " was found in row fields. "
                            + "Removing field from return field rules!");
                    fieldIterator.remove();
                }
            }
        }
    }

    private boolean isMetricAlias(String fieldName) {
        for (QueryMetric metric : builder.getMetrics()) {
            if (fieldName.equals(metric.getAlias())) {
                return true;
            }
        }
        return false;
    }
}

