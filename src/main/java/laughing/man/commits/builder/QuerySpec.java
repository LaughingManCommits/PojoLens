package laughing.man.commits.builder;

import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Separator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class QuerySpec {

    private List<QueryRow> rows = new ArrayList<>();
    private final Map<String, CriteriaRule> filterRules = new LinkedHashMap<>();
    private final Map<String, CriteriaRule> havingRules = new LinkedHashMap<>();
    private final Map<String, Object> filterValues = new HashMap<>();
    private final Map<String, String> filterFields = new HashMap<>();
    private final Map<String, Clauses> filterClause = new HashMap<>();
    private final Map<String, Separator> filterSeparator = new HashMap<>();
    private final Map<String, Object> havingValues = new HashMap<>();
    private final Map<String, String> havingFields = new HashMap<>();
    private final Map<String, Clauses> havingClause = new HashMap<>();
    private final Map<String, Separator> havingSeparator = new HashMap<>();
    private final Map<String, String> havingDateFormats = new HashMap<>();
    private final Map<String, List<String>> havingIDs = new HashMap<>();
    private final Map<String, String> filterDateFormats = new HashMap<>();
    private final Map<Integer, String> groupFields = new HashMap<>();
    private final Map<Integer, String> orderFields = new HashMap<>();
    private final Map<Integer, String> distinctFields = new HashMap<>();
    private final Map<String, List<String>> filterIDs = new HashMap<>();
    private final Map<Integer, List<QueryRow>> joinClasses = new HashMap<>();
    private final Map<Integer, Join> joinMethods = new HashMap<>();
    private final Map<Integer, String> joinParentFields = new HashMap<>();
    private final Map<Integer, String> joinChildFields = new HashMap<>();
    private final List<String> returnFields = new ArrayList<>();
    private final List<QueryMetric> metrics = new ArrayList<>();
    private final Map<String, QueryTimeBucket> timeBuckets = new HashMap<>();
    private final List<List<QueryRule>> allOfGroups = new ArrayList<>();
    private final List<List<QueryRule>> anyOfGroups = new ArrayList<>();
    private final List<List<QueryRule>> havingAllOfGroups = new ArrayList<>();
    private final List<List<QueryRule>> havingAnyOfGroups = new ArrayList<>();
    private Integer limit;

    List<QueryRow> getRows() {
        return rows;
    }

    void setRows(List<QueryRow> rows) {
        this.rows = rows == null ? new ArrayList<>() : rows;
    }

    Map<String, Object> getFilterValues() {
        return filterValues;
    }

    Map<String, CriteriaRule> getFilterRules() {
        return filterRules;
    }

    Map<String, String> getFilterFields() {
        return filterFields;
    }

    Map<String, Clauses> getFilterClause() {
        return filterClause;
    }

    Map<String, Separator> getFilterSeparator() {
        return filterSeparator;
    }

    Map<String, String> getFilterDateFormats() {
        return filterDateFormats;
    }

    Map<String, Object> getHavingValues() {
        return havingValues;
    }

    Map<String, CriteriaRule> getHavingRules() {
        return havingRules;
    }

    Map<String, String> getHavingFields() {
        return havingFields;
    }

    Map<String, Clauses> getHavingClause() {
        return havingClause;
    }

    Map<String, Separator> getHavingSeparator() {
        return havingSeparator;
    }

    Map<String, String> getHavingDateFormats() {
        return havingDateFormats;
    }

    Map<String, List<String>> getHavingIDs() {
        return havingIDs;
    }

    Map<Integer, String> getGroupFields() {
        return groupFields;
    }

    Map<Integer, String> getOrderFields() {
        return orderFields;
    }

    Map<Integer, String> getDistinctFields() {
        return distinctFields;
    }

    Map<String, List<String>> getFilterIDs() {
        return filterIDs;
    }

    Map<Integer, List<QueryRow>> getJoinClasses() {
        return joinClasses;
    }

    Map<Integer, Join> getJoinMethods() {
        return joinMethods;
    }

    Map<Integer, String> getJoinParentFields() {
        return joinParentFields;
    }

    Map<Integer, String> getJoinChildFields() {
        return joinChildFields;
    }

    List<String> getReturnFields() {
        return returnFields;
    }

    List<QueryMetric> getMetrics() {
        return metrics;
    }

    Map<String, QueryTimeBucket> getTimeBuckets() {
        return timeBuckets;
    }

    List<List<QueryRule>> getAllOfGroups() {
        return allOfGroups;
    }

    List<List<QueryRule>> getAnyOfGroups() {
        return anyOfGroups;
    }

    List<List<QueryRule>> getHavingAllOfGroups() {
        return havingAllOfGroups;
    }

    List<List<QueryRule>> getHavingAnyOfGroups() {
        return havingAnyOfGroups;
    }

    Integer getLimit() {
        return limit;
    }

    void setLimit(Integer limit) {
        this.limit = limit;
    }

    void addFilterRule(CriteriaRule rule) {
        upsertCriteriaRule(rule, filterRules, filterValues, filterFields, filterClause, filterSeparator, filterDateFormats, filterIDs);
    }

    void addHavingRule(CriteriaRule rule) {
        upsertCriteriaRule(rule, havingRules, havingValues, havingFields, havingClause, havingSeparator, havingDateFormats, havingIDs);
    }

    void removeFilterRule(String ruleId) {
        removeCriteriaRule(ruleId, filterRules, filterValues, filterFields, filterClause, filterSeparator, filterDateFormats, filterIDs);
    }

    void removeHavingRule(String ruleId) {
        removeCriteriaRule(ruleId, havingRules, havingValues, havingFields, havingClause, havingSeparator, havingDateFormats, havingIDs);
    }

    QuerySpec deepCopy() {
        QuerySpec copy = new QuerySpec();
        copy.rows = deepCopyRows(rows);
        copy.filterRules.putAll(filterRules);
        copy.havingRules.putAll(havingRules);
        copy.filterValues.putAll(filterValues);
        copy.filterFields.putAll(filterFields);
        copy.filterClause.putAll(filterClause);
        copy.filterSeparator.putAll(filterSeparator);
        copy.havingValues.putAll(havingValues);
        copy.havingFields.putAll(havingFields);
        copy.havingClause.putAll(havingClause);
        copy.havingSeparator.putAll(havingSeparator);
        copy.havingDateFormats.putAll(havingDateFormats);
        copy.havingIDs.putAll(copyFilterIds(havingIDs));
        copy.filterDateFormats.putAll(filterDateFormats);
        copy.groupFields.putAll(groupFields);
        copy.orderFields.putAll(orderFields);
        copy.distinctFields.putAll(distinctFields);
        copy.filterIDs.putAll(copyFilterIds(filterIDs));
        copy.joinClasses.putAll(copyJoinClasses(joinClasses));
        copy.joinMethods.putAll(joinMethods);
        copy.joinParentFields.putAll(joinParentFields);
        copy.joinChildFields.putAll(joinChildFields);
        copy.returnFields.addAll(returnFields);
        copy.metrics.addAll(metrics);
        copy.timeBuckets.putAll(timeBuckets);
        copy.allOfGroups.addAll(copyRuleGroups(allOfGroups));
        copy.anyOfGroups.addAll(copyRuleGroups(anyOfGroups));
        copy.havingAllOfGroups.addAll(copyRuleGroups(havingAllOfGroups));
        copy.havingAnyOfGroups.addAll(copyRuleGroups(havingAnyOfGroups));
        copy.limit = limit;
        return copy;
    }

    void replaceWith(QuerySpec source) {
        this.rows = deepCopyRows(source.rows);
        replaceMap(this.filterRules, source.filterRules);
        replaceMap(this.havingRules, source.havingRules);
        replaceMap(this.filterValues, source.filterValues);
        replaceMap(this.filterFields, source.filterFields);
        replaceMap(this.filterClause, source.filterClause);
        replaceMap(this.filterSeparator, source.filterSeparator);
        replaceMap(this.havingValues, source.havingValues);
        replaceMap(this.havingFields, source.havingFields);
        replaceMap(this.havingClause, source.havingClause);
        replaceMap(this.havingSeparator, source.havingSeparator);
        replaceMap(this.havingDateFormats, source.havingDateFormats);
        replaceMap(this.havingIDs, copyFilterIds(source.havingIDs));
        replaceMap(this.filterDateFormats, source.filterDateFormats);
        replaceMap(this.groupFields, source.groupFields);
        replaceMap(this.orderFields, source.orderFields);
        replaceMap(this.distinctFields, source.distinctFields);
        replaceMap(this.filterIDs, copyFilterIds(source.filterIDs));
        replaceMap(this.joinClasses, copyJoinClasses(source.joinClasses));
        replaceMap(this.joinMethods, source.joinMethods);
        replaceMap(this.joinParentFields, source.joinParentFields);
        replaceMap(this.joinChildFields, source.joinChildFields);
        this.returnFields.clear();
        this.returnFields.addAll(source.returnFields);
        this.metrics.clear();
        this.metrics.addAll(source.metrics);
        this.timeBuckets.clear();
        this.timeBuckets.putAll(source.timeBuckets);
        this.allOfGroups.clear();
        this.allOfGroups.addAll(copyRuleGroups(source.allOfGroups));
        this.anyOfGroups.clear();
        this.anyOfGroups.addAll(copyRuleGroups(source.anyOfGroups));
        this.havingAllOfGroups.clear();
        this.havingAllOfGroups.addAll(copyRuleGroups(source.havingAllOfGroups));
        this.havingAnyOfGroups.clear();
        this.havingAnyOfGroups.addAll(copyRuleGroups(source.havingAnyOfGroups));
        this.limit = source.limit;
    }

    private static <K, V> void replaceMap(Map<K, V> target, Map<K, V> source) {
        target.clear();
        target.putAll(source);
    }

    private static void upsertCriteriaRule(CriteriaRule rule,
                                           Map<String, CriteriaRule> ruleMap,
                                           Map<String, Object> valueMap,
                                           Map<String, String> fieldMap,
                                           Map<String, Clauses> clauseMap,
                                           Map<String, Separator> separatorMap,
                                           Map<String, String> dateFormatMap,
                                           Map<String, List<String>> idsByFieldMap) {
        if (rule == null) {
            return;
        }
        String ruleId = rule.id();
        if (ruleId == null) {
            return;
        }
        CriteriaRule previous = ruleMap.put(ruleId, rule);
        if (previous != null && previous.field() != null && !previous.field().equals(rule.field())) {
            unregisterRuleId(idsByFieldMap, previous.field(), ruleId);
        }
        valueMap.put(ruleId, rule.value());
        fieldMap.put(ruleId, rule.field());
        clauseMap.put(ruleId, rule.clause());
        separatorMap.put(ruleId, rule.separator());
        saveDateFormatIfAbsent(dateFormatMap, ruleId, rule.dateFormat());
        registerRuleId(idsByFieldMap, rule.field(), ruleId);
    }

    private static void removeCriteriaRule(String ruleId,
                                           Map<String, CriteriaRule> ruleMap,
                                           Map<String, Object> valueMap,
                                           Map<String, String> fieldMap,
                                           Map<String, Clauses> clauseMap,
                                           Map<String, Separator> separatorMap,
                                           Map<String, String> dateFormatMap,
                                           Map<String, List<String>> idsByFieldMap) {
        if (ruleId == null) {
            return;
        }
        CriteriaRule removed = ruleMap.remove(ruleId);
        String fieldName = removed == null ? fieldMap.get(ruleId) : removed.field();
        valueMap.remove(ruleId);
        fieldMap.remove(ruleId);
        clauseMap.remove(ruleId);
        separatorMap.remove(ruleId);
        dateFormatMap.remove(ruleId);
        if (fieldName != null) {
            unregisterRuleId(idsByFieldMap, fieldName, ruleId);
        }
    }

    private static void saveDateFormatIfAbsent(Map<String, String> dateFormatMap, String id, String dateFormat) {
        if (id != null && dateFormat != null && !dateFormat.isBlank() && !dateFormatMap.containsKey(id)) {
            dateFormatMap.put(id, dateFormat);
        }
    }

    private static void registerRuleId(Map<String, List<String>> idsByFieldMap, String column, String uniqueId) {
        if (column == null || uniqueId == null) {
            return;
        }
        List<String> ids = idsByFieldMap.computeIfAbsent(column, key -> new ArrayList<>());
        if (!ids.contains(uniqueId)) {
            ids.add(uniqueId);
        }
    }

    private static void unregisterRuleId(Map<String, List<String>> idsByFieldMap, String column, String uniqueId) {
        List<String> ids = idsByFieldMap.get(column);
        if (ids == null) {
            return;
        }
        ids.removeIf(id -> uniqueId.equals(id));
        if (ids.isEmpty()) {
            idsByFieldMap.remove(column);
        }
    }

    private static Map<String, List<String>> copyFilterIds(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new HashMap<>(Math.max(16, source.size() * 2));
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            List<String> ids = entry.getValue();
            List<String> idCopy = new ArrayList<>(ids == null ? 0 : ids.size());
            if (ids != null) {
                idCopy.addAll(ids);
            }
            copy.put(entry.getKey(), idCopy);
        }
        return copy;
    }

    private static Map<Integer, List<QueryRow>> copyJoinClasses(Map<Integer, List<QueryRow>> source) {
        Map<Integer, List<QueryRow>> copy = new HashMap<>(Math.max(16, source.size() * 2));
        for (Map.Entry<Integer, List<QueryRow>> entry : source.entrySet()) {
            copy.put(entry.getKey(), deepCopyRows(entry.getValue()));
        }
        return copy;
    }

    private static List<List<QueryRule>> copyRuleGroups(List<List<QueryRule>> source) {
        List<List<QueryRule>> copy = new ArrayList<>(source.size());
        for (List<QueryRule> group : source) {
            List<QueryRule> groupCopy = new ArrayList<>(group == null ? 0 : group.size());
            if (group != null) {
                groupCopy.addAll(group);
            }
            copy.add(groupCopy);
        }
        return copy;
    }

    private static List<QueryRow> deepCopyRows(List<QueryRow> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<QueryRow> copy = new ArrayList<>(source.size());
        for (QueryRow row : source) {
            if (row == null) {
                continue;
            }
            QueryRow cloned = new QueryRow();
            cloned.setRowId(row.getRowId());
            cloned.setRowType(row.getRowType());
            List<? extends QueryField> sourceFields = row.getFields();
            List<QueryField> fields = new ArrayList<>(sourceFields == null ? 0 : sourceFields.size());
            if (sourceFields != null) {
                for (QueryField field : sourceFields) {
                    QueryField fieldCopy = new QueryField();
                    fieldCopy.setFieldName(field.getFieldName());
                    fieldCopy.setValue(field.getValue());
                    fields.add(fieldCopy);
                }
            }
            cloned.setFields(fields);
            copy.add(cloned);
        }
        return copy;
    }

    static final class CriteriaRule {
        private final String id;
        private final String field;
        private final Object value;
        private final Clauses clause;
        private final Separator separator;
        private final String dateFormat;

        CriteriaRule(String id, String field, Object value, Clauses clause, Separator separator, String dateFormat) {
            this.id = id;
            this.field = field;
            this.value = value;
            this.clause = clause;
            this.separator = separator;
            this.dateFormat = dateFormat;
        }

        String id() {
            return id;
        }

        String field() {
            return field;
        }

        Object value() {
            return value;
        }

        Clauses clause() {
            return clause;
        }

        Separator separator() {
            return separator;
        }

        String dateFormat() {
            return dateFormat;
        }
    }
}

