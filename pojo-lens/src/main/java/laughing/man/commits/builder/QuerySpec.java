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
    private Map<String, Class<?>> sourceFieldTypes = new LinkedHashMap<>();
    private Map<String, Class<?>> fieldTypes = new LinkedHashMap<>();
    private Map<String, CriteriaRule> filterRules = new LinkedHashMap<>();
    private Map<String, CriteriaRule> havingRules = new LinkedHashMap<>();
    private Map<String, CriteriaRule> qualifyRules = new LinkedHashMap<>();
    private Map<String, Object> filterValues = new HashMap<>();
    private Map<String, String> filterFields = new HashMap<>();
    private Map<String, Clauses> filterClause = new HashMap<>();
    private Map<String, Separator> filterSeparator = new HashMap<>();
    private Map<String, Object> havingValues = new HashMap<>();
    private Map<String, String> havingFields = new HashMap<>();
    private Map<String, Clauses> havingClause = new HashMap<>();
    private Map<String, Separator> havingSeparator = new HashMap<>();
    private Map<String, Object> qualifyValues = new LinkedHashMap<>();
    private Map<String, String> qualifyFields = new LinkedHashMap<>();
    private Map<String, Clauses> qualifyClause = new LinkedHashMap<>();
    private Map<String, Separator> qualifySeparator = new LinkedHashMap<>();
    private Map<String, String> havingDateFormats = new HashMap<>();
    private Map<String, List<String>> havingIDs = new HashMap<>();
    private Map<String, String> qualifyDateFormats = new LinkedHashMap<>();
    private Map<String, List<String>> qualifyIDs = new LinkedHashMap<>();
    private Map<String, String> filterDateFormats = new HashMap<>();
    private Map<Integer, String> groupFields = new HashMap<>();
    private Map<Integer, String> orderFields = new HashMap<>();
    private Map<Integer, String> distinctFields = new HashMap<>();
    private Map<String, List<String>> filterIDs = new HashMap<>();
    private Map<Integer, List<QueryRow>> joinClasses = new HashMap<>();
    private Map<Integer, Map<String, Class<?>>> joinSourceFieldTypes = new HashMap<>();
    private Map<Integer, Join> joinMethods = new HashMap<>();
    private Map<Integer, String> joinParentFields = new HashMap<>();
    private Map<Integer, String> joinChildFields = new HashMap<>();
    private List<String> returnFields = new ArrayList<>();
    private List<String> indexedFields = new ArrayList<>();
    private List<QueryMetric> metrics = new ArrayList<>();
    private Map<String, QueryTimeBucket> timeBuckets = new HashMap<>();
    private List<QueryWindow> windows = new ArrayList<>();
    private List<List<QueryRule>> allOfGroups = new ArrayList<>();
    private List<List<QueryRule>> anyOfGroups = new ArrayList<>();
    private List<List<QueryRule>> havingAllOfGroups = new ArrayList<>();
    private List<List<QueryRule>> havingAnyOfGroups = new ArrayList<>();
    private List<List<QueryRule>> qualifyAllOfGroups = new ArrayList<>();
    private List<List<QueryRule>> qualifyAnyOfGroups = new ArrayList<>();
    private Integer limit;
    private Integer offset;

    List<QueryRow> getRows() {
        return rows;
    }

    void setRows(List<QueryRow> rows) {
        this.rows = rows == null ? new ArrayList<>() : rows;
    }

    Map<String, Class<?>> getSourceFieldTypes() {
        return sourceFieldTypes;
    }

    void setSourceFieldTypes(Map<String, Class<?>> fieldTypes) {
        replaceMap(sourceFieldTypes, fieldTypes == null ? Map.of() : fieldTypes);
    }

    Map<String, Class<?>> getFieldTypes() {
        return fieldTypes;
    }

    void setFieldTypes(Map<String, Class<?>> fieldTypes) {
        replaceMap(this.fieldTypes, fieldTypes == null ? Map.of() : fieldTypes);
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

    Map<String, Object> getQualifyValues() {
        return qualifyValues;
    }

    Map<String, CriteriaRule> getQualifyRules() {
        return qualifyRules;
    }

    Map<String, String> getQualifyFields() {
        return qualifyFields;
    }

    Map<String, Clauses> getQualifyClause() {
        return qualifyClause;
    }

    Map<String, Separator> getQualifySeparator() {
        return qualifySeparator;
    }

    Map<String, String> getHavingDateFormats() {
        return havingDateFormats;
    }

    Map<String, List<String>> getHavingIDs() {
        return havingIDs;
    }

    Map<String, String> getQualifyDateFormats() {
        return qualifyDateFormats;
    }

    Map<String, List<String>> getQualifyIDs() {
        return qualifyIDs;
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

    Map<Integer, Map<String, Class<?>>> getJoinSourceFieldTypes() {
        return joinSourceFieldTypes;
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

    List<String> getIndexedFields() {
        return indexedFields;
    }

    List<QueryMetric> getMetrics() {
        return metrics;
    }

    Map<String, QueryTimeBucket> getTimeBuckets() {
        return timeBuckets;
    }

    List<QueryWindow> getWindows() {
        return windows;
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

    List<List<QueryRule>> getQualifyAllOfGroups() {
        return qualifyAllOfGroups;
    }

    List<List<QueryRule>> getQualifyAnyOfGroups() {
        return qualifyAnyOfGroups;
    }

    Integer getLimit() {
        return limit;
    }

    void setLimit(Integer limit) {
        this.limit = limit;
    }

    Integer getOffset() {
        return offset;
    }

    void setOffset(Integer offset) {
        this.offset = offset;
    }

    void addFilterRule(CriteriaRule rule) {
        upsertCriteriaRule(rule, filterRules, filterValues, filterFields, filterClause, filterSeparator, filterDateFormats, filterIDs);
    }

    void addHavingRule(CriteriaRule rule) {
        upsertCriteriaRule(rule, havingRules, havingValues, havingFields, havingClause, havingSeparator, havingDateFormats, havingIDs);
    }

    void addQualifyRule(CriteriaRule rule) {
        upsertCriteriaRule(rule, qualifyRules, qualifyValues, qualifyFields, qualifyClause, qualifySeparator, qualifyDateFormats, qualifyIDs);
    }

    void removeFilterRule(String ruleId) {
        removeCriteriaRule(ruleId, filterRules, filterValues, filterFields, filterClause, filterSeparator, filterDateFormats, filterIDs);
    }

    void removeHavingRule(String ruleId) {
        removeCriteriaRule(ruleId, havingRules, havingValues, havingFields, havingClause, havingSeparator, havingDateFormats, havingIDs);
    }

    void removeQualifyRule(String ruleId) {
        removeCriteriaRule(ruleId, qualifyRules, qualifyValues, qualifyFields, qualifyClause, qualifySeparator, qualifyDateFormats, qualifyIDs);
    }

    QuerySpec deepCopy() {
        QuerySpec copy = new QuerySpec();
        copyInto(copy, true);
        return copy;
    }

    QuerySpec executionCopy() {
        QuerySpec copy = new QuerySpec();
        copyInto(copy, false);
        return copy;
    }

    QuerySpec preparedExecutionViewCopy() {
        QuerySpec copy = new QuerySpec();
        copy.rows = copyRowsReference(rows);
        copy.sourceFieldTypes = new LinkedHashMap<>(sourceFieldTypes);
        copy.fieldTypes = new LinkedHashMap<>(fieldTypes);
        copy.filterRules = filterRules;
        copy.havingRules = havingRules;
        copy.qualifyRules = qualifyRules;
        copy.filterValues = filterValues;
        copy.filterFields = filterFields;
        copy.filterClause = filterClause;
        copy.filterSeparator = filterSeparator;
        copy.havingValues = havingValues;
        copy.havingFields = havingFields;
        copy.havingClause = havingClause;
        copy.havingSeparator = havingSeparator;
        copy.qualifyValues = qualifyValues;
        copy.qualifyFields = qualifyFields;
        copy.qualifyClause = qualifyClause;
        copy.qualifySeparator = qualifySeparator;
        copy.havingDateFormats = havingDateFormats;
        copy.havingIDs = havingIDs;
        copy.qualifyDateFormats = qualifyDateFormats;
        copy.qualifyIDs = qualifyIDs;
        copy.filterDateFormats = filterDateFormats;
        copy.groupFields = groupFields;
        copy.orderFields = orderFields;
        copy.distinctFields = distinctFields;
        copy.filterIDs = filterIDs;
        copy.joinClasses = copyJoinClassReferences(joinClasses);
        copy.joinSourceFieldTypes = copyJoinFieldTypes(joinSourceFieldTypes);
        copy.joinMethods = joinMethods;
        copy.joinParentFields = joinParentFields;
        copy.joinChildFields = joinChildFields;
        copy.returnFields = returnFields;
        copy.indexedFields = indexedFields;
        copy.metrics = metrics;
        copy.timeBuckets = timeBuckets;
        copy.windows = windows;
        copy.allOfGroups = allOfGroups;
        copy.anyOfGroups = anyOfGroups;
        copy.havingAllOfGroups = havingAllOfGroups;
        copy.havingAnyOfGroups = havingAnyOfGroups;
        copy.qualifyAllOfGroups = qualifyAllOfGroups;
        copy.qualifyAnyOfGroups = qualifyAnyOfGroups;
        copy.limit = limit;
        copy.offset = offset;
        return copy;
    }

    void replaceWith(QuerySpec source) {
        replaceWith(source, true);
    }

    void replaceWith(QuerySpec source, boolean copyRows) {
        source.copyInto(this, copyRows);
    }

    private void copyInto(QuerySpec target, boolean copyRows) {
        target.rows = copyRows ? deepCopyRows(rows) : copyRowsReference(rows);
        replaceMap(target.sourceFieldTypes, sourceFieldTypes);
        replaceMap(target.fieldTypes, fieldTypes);
        replaceMap(target.filterRules, filterRules);
        replaceMap(target.havingRules, havingRules);
        replaceMap(target.qualifyRules, qualifyRules);
        replaceMap(target.filterValues, filterValues);
        replaceMap(target.filterFields, filterFields);
        replaceMap(target.filterClause, filterClause);
        replaceMap(target.filterSeparator, filterSeparator);
        replaceMap(target.havingValues, havingValues);
        replaceMap(target.havingFields, havingFields);
        replaceMap(target.havingClause, havingClause);
        replaceMap(target.havingSeparator, havingSeparator);
        replaceMap(target.qualifyValues, qualifyValues);
        replaceMap(target.qualifyFields, qualifyFields);
        replaceMap(target.qualifyClause, qualifyClause);
        replaceMap(target.qualifySeparator, qualifySeparator);
        replaceMap(target.havingDateFormats, havingDateFormats);
        replaceMap(target.havingIDs, copyFilterIds(havingIDs));
        replaceMap(target.qualifyDateFormats, qualifyDateFormats);
        replaceMap(target.qualifyIDs, copyFilterIds(qualifyIDs));
        replaceMap(target.filterDateFormats, filterDateFormats);
        replaceMap(target.groupFields, groupFields);
        replaceMap(target.orderFields, orderFields);
        replaceMap(target.distinctFields, distinctFields);
        replaceMap(target.filterIDs, copyFilterIds(filterIDs));
        replaceMap(target.joinClasses, copyRows ? copyJoinClasses(joinClasses) : copyJoinClassReferences(joinClasses));
        replaceMap(target.joinSourceFieldTypes, copyJoinFieldTypes(joinSourceFieldTypes));
        replaceMap(target.joinMethods, joinMethods);
        replaceMap(target.joinParentFields, joinParentFields);
        replaceMap(target.joinChildFields, joinChildFields);
        target.returnFields.clear();
        target.returnFields.addAll(returnFields);
        target.indexedFields.clear();
        target.indexedFields.addAll(indexedFields);
        target.metrics.clear();
        target.metrics.addAll(metrics);
        target.timeBuckets.clear();
        target.timeBuckets.putAll(timeBuckets);
        target.windows.clear();
        target.windows.addAll(windows);
        target.allOfGroups.clear();
        target.allOfGroups.addAll(copyRuleGroups(allOfGroups));
        target.anyOfGroups.clear();
        target.anyOfGroups.addAll(copyRuleGroups(anyOfGroups));
        target.havingAllOfGroups.clear();
        target.havingAllOfGroups.addAll(copyRuleGroups(havingAllOfGroups));
        target.havingAnyOfGroups.clear();
        target.havingAnyOfGroups.addAll(copyRuleGroups(havingAnyOfGroups));
        target.qualifyAllOfGroups.clear();
        target.qualifyAllOfGroups.addAll(copyRuleGroups(qualifyAllOfGroups));
        target.qualifyAnyOfGroups.clear();
        target.qualifyAnyOfGroups.addAll(copyRuleGroups(qualifyAnyOfGroups));
        target.limit = limit;
        target.offset = offset;
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

    private static Map<Integer, List<QueryRow>> copyJoinClassReferences(Map<Integer, List<QueryRow>> source) {
        Map<Integer, List<QueryRow>> copy = new HashMap<>(Math.max(16, source.size() * 2));
        for (Map.Entry<Integer, List<QueryRow>> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyRowsReference(entry.getValue()));
        }
        return copy;
    }

    private static Map<Integer, Map<String, Class<?>>> copyJoinFieldTypes(Map<Integer, Map<String, Class<?>>> source) {
        Map<Integer, Map<String, Class<?>>> copy = new HashMap<>(Math.max(16, source.size() * 2));
        for (Map.Entry<Integer, Map<String, Class<?>>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
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

    private static List<QueryRow> copyRowsReference(List<QueryRow> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(source);
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

