package laughing.man.commits.filter;

import laughing.man.commits.EngineDefaults;
import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryRule;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.util.ObjectUtil;
import laughing.man.commits.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FilterCore {

    private static final Logger LOG = LoggerFactory.getLogger(FilterCore.class);

    private final FilterQueryBuilder builder;
    private final RuleCleaner cleaner;
    private final OrderEngine orderEngine;
    private final GroupEngine groupEngine;
    private final JoinEngine joinEngine;
    private final AggregationEngine aggregationEngine;

    public FilterCore(FilterQueryBuilder builder) {
        this.builder = builder;
        this.cleaner = new RuleCleaner(builder);
        this.orderEngine = new OrderEngine(builder);
        this.groupEngine = new GroupEngine(builder);
        this.joinEngine = new JoinEngine(builder);
        this.aggregationEngine = new AggregationEngine(builder);
    }

    /**
     * Removes rule entries that reference missing fields.
     */
    public Map<Integer, String> clean(List<? extends QueryField> allFields,
            Map<Integer, String> rules, String ruleType) {
        return cleaner.cleanRuleFields(allFields, rules, ruleType);
    }

    /**
     * Cleans query configuration against the provided row schema.
     */
    public void clean(QueryRow row) {
        cleaner.clean(row);
    }

    /**
     * Sorts rows using configured order columns.
     */
    public List<QueryRow> orderByFields(List<QueryRow> rows,
            Sort sortMethod) {
        return orderByFields(rows, sortMethod, buildExecutionPlan());
    }

    public List<QueryRow> orderByFields(List<QueryRow> rows,
                                           Sort sortMethod,
                                           FilterExecutionPlan plan) {
        return orderEngine.orderByFields(rows, sortMethod, plan);
    }

    /**
     * Applies configured joins to the provided rows.
     */
    public <T> List<QueryRow> join(List<T> bean) {
        return joinEngine.join(bean);
    }

    /**
     * Groups rows using configured group columns.
     */
    public Map<String, List<QueryRow>> groupByFields(List<QueryRow> rows, List<QueryRow> displayFields) {
        return groupByFields(rows, displayFields, buildExecutionPlan());
    }

    public Map<String, List<QueryRow>> groupByFields(List<QueryRow> rows,
                                                        List<QueryRow> displayFields,
                                                        FilterExecutionPlan plan) {
        return groupEngine.groupByFields(rows, displayFields, plan);
    }

    /**
     * Filters rows against configured rule criteria.
     */
    public List<QueryRow> filterFields(List<QueryRow> rows) {
        return filterFields(rows, buildExecutionPlan());
    }

    public List<QueryRow> filterFields(List<QueryRow> rows,
                                          FilterExecutionPlan plan) {
        return filterFields(rows, plan, plan.getRulesByFieldIndex(), true);
    }

    public List<QueryRow> filterHavingFields(List<QueryRow> rows) {
        return filterHavingFields(rows, buildExecutionPlan());
    }

    public List<QueryRow> filterHavingFields(List<QueryRow> rows,
                                             FilterExecutionPlan plan) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        if (builder.getHavingFields().isEmpty()) {
            if (!hasExplicitRuleGroups(builder.getHavingAllOfGroups(), builder.getHavingAnyOfGroups())) {
                return rows;
            }
        }
        if (hasExplicitRuleGroups(builder.getHavingAllOfGroups(), builder.getHavingAnyOfGroups())) {
            List<QueryRow> groupedResults = new ArrayList<>(rows.size());
            for (QueryRow row : rows) {
                if (matchesExplicitGroups(row, plan, builder.getHavingAllOfGroups(), builder.getHavingAnyOfGroups())) {
                    groupedResults.add(row);
                }
            }
            return groupedResults;
        }
        return filterFields(rows, plan, plan.getHavingRulesByFieldIndex(), false);
    }

    private List<QueryRow> filterFields(List<QueryRow> rows,
                                        FilterExecutionPlan plan,
                                        Map<Integer, List<CompiledRule>> rulesByField,
                                        boolean allowExplicitGroups) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }

        if (allowExplicitGroups && hasExplicitRuleGroups(builder.getAllOfGroups(), builder.getAnyOfGroups())) {
            List<QueryRow> groupedResults = new ArrayList<>(rows.size());
            for (QueryRow row : rows) {
                if (matchesExplicitGroups(row, plan, builder.getAllOfGroups(), builder.getAnyOfGroups())) {
                    groupedResults.add(row);
                }
            }
            return groupedResults;
        }

        if (!rulesByField.isEmpty()) {
            List<QueryRow> results = new ArrayList<>(rows.size());
            for (QueryRow row : rows) {
                List<? extends QueryField> fields = row.getFields();
                if (fields == null) {
                    continue;
                }
                boolean andMatched = false;
                boolean andFailed = false;
                boolean orMatched = false;

                outer:
                for (Map.Entry<Integer, List<CompiledRule>> ruleEntry : rulesByField.entrySet()) {
                    int fieldIndex = ruleEntry.getKey();
                    if (fieldIndex < 0 || fieldIndex >= fields.size()) {
                        continue;
                    }
                    List<CompiledRule> rules = ruleEntry.getValue();
                    if (rules == null || rules.isEmpty()) {
                        continue;
                    }
                    QueryField field = fields.get(fieldIndex);
                    Object fieldValue = field.getValue();
                    for (CompiledRule rule : rules) {
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
                if ((andMatched && !andFailed) || orMatched) {
                    results.add(row);
                }
            }
            return results;
        }
        return rows;
    }

    /**
     * Projects rows to configured return fields.
     */
    public List<QueryRow> filterDisplayFields(List<QueryRow> rows) {
        return filterDisplayFields(rows, buildExecutionPlan());
    }

    public List<QueryRow> filterDisplayFields(List<QueryRow> rows,
                                                 FilterExecutionPlan plan) {
        if (builder.getReturnFields().isEmpty()) {
            return rows;
        }
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> returnIndexes = plan.getReturnFieldIndexes();
        List<QueryRow> results = new ArrayList<>(rows.size());
        for (QueryRow row : rows) {
            List<? extends QueryField> allFields = row.getFields();
            if (allFields == null) {
                continue;
            }
            String rowId = row.getRowId();
            List<QueryField> fieldList = new ArrayList<>(returnIndexes.size());
            for (int fieldIndex : returnIndexes) {
                if (fieldIndex < allFields.size()) {
                    fieldList.add(allFields.get(fieldIndex));
                }
            }

            QueryRow projectedRow = new QueryRow();
            projectedRow.setFields(fieldList);
            projectedRow.setRowId(rowId);
            results.add(projectedRow);
        }
        return results;
    }

    /**
     * Aggregates filtered rows into a single metric projection row.
     */
    public List<QueryRow> aggregateMetrics(List<QueryRow> rows, FilterExecutionPlan plan) {
        return aggregationEngine.aggregateMetrics(rows, plan);
    }

    /**
     * Removes duplicate rows based on configured distinct columns.
     */
    public List<QueryRow> filterDistinctFields() {
        return filterDistinctFields(buildExecutionPlan());
    }

    public List<QueryRow> filterDistinctFields(FilterExecutionPlan plan) {
        try {
            if (!builder.getDistinctFields().isEmpty()) {
                Map<QueryKey, QueryRow> distinct = new LinkedHashMap<>(expectedMapSize(builder.getRows().size()));
                if (builder.getRows().isEmpty()) {
                    return builder.getRows();
                }
                List<Integer> fieldIndexes = plan.getDistinctFieldIndexes();
                if (fieldIndexes.isEmpty()) {
                    return new ArrayList<>();
                }
                int distinctFieldCount = fieldIndexes.size();

                for (QueryRow row : builder.getRows()) {
                    List<? extends QueryField> allFields = row.getFields();
                    if (allFields == null) {
                        continue;
                    }
                    String[] distValues = new String[distinctFieldCount];
                    int valueCount = 0;
                    for (int fieldIndex : fieldIndexes) {
                        if (fieldIndex >= allFields.size()) {
                            continue;
                        }
                        String fieldValue = ObjectUtil.castToString(allFields.get(fieldIndex).getValue());
                        if (!StringUtil.isNull(fieldValue)) {
                            distValues[valueCount++] = fieldValue;
                        }
                    }
                    QueryKey distKey = new QueryKey(distValues, valueCount);

                    if (!distKey.isEmpty()) {
                        distinct.put(distKey, row);
                    }
                }

                return new ArrayList<>(distinct.values());
            }
        } catch (Exception e) {
            LOG.error("Failed to remove distinct field values [" + builder.getRows() + "] ", e);
        }
        return builder.getRows();
    }

    public FilterQueryBuilder getBuilder() {
        return builder;
    }

    public FilterExecutionPlan buildExecutionPlan() {
        return new FilterExecutionPlan(builder);
    }

    private boolean hasExplicitRuleGroups(List<List<QueryRule>> allOfGroups, List<List<QueryRule>> anyOfGroups) {
        return !allOfGroups.isEmpty() || !anyOfGroups.isEmpty();
    }

    private boolean matchesExplicitGroups(QueryRow row,
                                          FilterExecutionPlan plan,
                                          List<List<QueryRule>> allOfGroups,
                                          List<List<QueryRule>> anyOfGroups) {
        boolean allOfSatisfied = allOfGroups.isEmpty();
        if (!allOfSatisfied) {
            for (List<QueryRule> group : allOfGroups) {
                if (matchesAllRules(row, group, plan)) {
                    allOfSatisfied = true;
                    break;
                }
            }
        }

        boolean anyOfSatisfied = anyOfGroups.isEmpty();
        if (!anyOfSatisfied) {
            for (List<QueryRule> group : anyOfGroups) {
                if (matchesAnyRule(row, group, plan)) {
                    anyOfSatisfied = true;
                    break;
                }
            }
        }
        return allOfSatisfied && anyOfSatisfied;
    }

    private boolean matchesAllRules(QueryRow row, List<QueryRule> rules, FilterExecutionPlan plan) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (QueryRule rule : rules) {
            if (!matchesRule(row, rule, plan)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAnyRule(QueryRow row, List<QueryRule> rules, FilterExecutionPlan plan) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (QueryRule rule : rules) {
            if (matchesRule(row, rule, plan)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesRule(QueryRow row, QueryRule rule, FilterExecutionPlan plan) {
        int fieldIndex = plan.findFieldIndex(rule.getColumn());
        Object fieldValue;
        if (fieldIndex < 0 || fieldIndex >= row.getFields().size()) {
            if (!SqlExpressionEvaluator.looksLikeExpression(rule.getColumn())) {
                return false;
            }
            fieldValue = SqlExpressionEvaluator.evaluateNumeric(
                    rule.getColumn(),
                    identifier -> resolveRowValue(row, plan, identifier)
            );
        } else {
            fieldValue = row.getFields().get(fieldIndex).getValue();
        }
        if (rule.getValue() == null) {
            if (Clauses.EQUAL.equals(rule.getClause())) {
                return fieldValue == null;
            }
            if (Clauses.NOT_EQUAL.equals(rule.getClause())) {
                return fieldValue != null;
            }
            return false;
        }
        if (fieldValue == null) {
            return Clauses.NOT_EQUAL.equals(rule.getClause());
        }
        String dateFormat = StringUtil.isNull(rule.getDateFormat()) ? EngineDefaults.SDF : rule.getDateFormat();
        return ObjectUtil.compareObject(fieldValue, rule.getValue(), rule.getClause(), dateFormat);
    }

    private Object resolveRowValue(QueryRow row, FilterExecutionPlan plan, String identifier) {
        int index = plan.findFieldIndex(identifier);
        List<? extends QueryField> fields = row.getFields();
        if (fields == null || index < 0 || index >= fields.size()) {
            return null;
        }
        return fields.get(index).getValue();
    }

    private int expectedMapSize(int sourceSize) {
        if (sourceSize <= 0) {
            return 16;
        }
        return (int) ((sourceSize / 0.75f) + 1.0f);
    }

}

