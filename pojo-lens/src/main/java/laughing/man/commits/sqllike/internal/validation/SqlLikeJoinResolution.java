package laughing.man.commits.sqllike.internal.validation;

import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.JoinAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves SQL-like JOIN field references to the deterministic merged-field names
 * produced by the fluent join pipeline.
 */
public final class SqlLikeJoinResolution {

    private SqlLikeJoinResolution() {
    }

    public static Plan resolve(QueryAst ast, Class<?> sourceClass, Map<String, List<?>> joinSources) {
        if (ast == null || !ast.hasJoins()) {
            return Plan.empty();
        }
        String rootSource = ast.select() == null ? null : ast.select().sourceName();
        return resolve(rootSource, sourceClass, ast.joins(), joinSources);
    }

    public static Plan resolve(String rootSource,
                               Class<?> sourceClass,
                               List<JoinAst> joins,
                               Map<String, List<?>> joinSources) {
        if (joins == null || joins.isEmpty()) {
            return Plan.empty();
        }
        State state = new State();
        LinkedHashSet<String> rootFields = new LinkedHashSet<>(SqlLikeValidator.collectFields(sourceClass));
        LinkedHashMap<String, Class<?>> rootTypes = new LinkedHashMap<>(SqlLikeValidator.collectFieldTypes(sourceClass));
        state.addRoot(rootSource, rootFields, rootTypes);

        ArrayList<ResolvedJoin> resolvedJoins = new ArrayList<>(joins.size());
        for (JoinAst join : joins) {
            List<?> childRows = joinSources.get(join.childSource());
            if (childRows == null) {
                throw SqlLikeValidator.validation(SqlLikeErrorCodes.VALIDATION_MISSING_JOIN_SOURCE,
                        "Missing JOIN source binding for '" + join.childSource() + "'");
            }
            Class<?> childClass = SqlLikeValidator.inferListElementClass(childRows);
            LinkedHashSet<String> childFields = new LinkedHashSet<>(SqlLikeValidator.collectFields(childClass));
            LinkedHashMap<String, Class<?>> childTypes = new LinkedHashMap<>(SqlLikeValidator.collectFieldTypes(childClass));

            String parentField = state.resolveStrict(join.parentField(), "JOIN");
            String childField = normalizeChildReference(join.childSource(), join.childField());
            if (!childFields.contains(childField)) {
                throw SqlLikeValidator.validation(SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD,
                        "Unknown field '" + childField + "' in JOIN clause. Allowed fields: " + childFields);
            }

            resolvedJoins.add(new ResolvedJoin(join, parentField, childField));
            state.addChild(join.childSource(), childFields, childTypes);
        }

        return new Plan(resolvedJoins, state.directReferences(), state.fieldTypes(), state.ambiguousReferences());
    }

    public static QueryAst canonicalize(QueryAst ast, Plan plan) {
        if (ast == null || plan.isEmpty()) {
            return ast;
        }
        return new QueryAst(
                canonicalizeSelect(ast.select(), plan),
                ast.joins(),
                canonicalizeFilters(ast.filters(), plan, "WHERE"),
                canonicalizeExpression(ast.whereExpression(), plan, "WHERE"),
                canonicalizeGroupBy(ast.groupByFields(), plan),
                canonicalizeFilters(ast.havingFilters(), plan, "HAVING"),
                canonicalizeExpression(ast.havingExpression(), plan, "HAVING"),
                canonicalizeOrders(ast.orders(), plan),
                ast.limit(),
                ast.offset()
        );
    }

    private static SelectAst canonicalizeSelect(SelectAst select, Plan plan) {
        if (select == null || select.wildcard()) {
            return select;
        }
        ArrayList<SelectFieldAst> fields = new ArrayList<>(select.fields().size());
        for (SelectFieldAst field : select.fields()) {
            String resolvedField = field.field();
            String resolvedWindowFunction = field.windowFunction();
            List<String> resolvedWindowPartitions = field.windowPartitionFields();
            List<OrderAst> resolvedWindowOrders = field.windowOrderFields();
            if (field.metricField() && !field.countAll()) {
                resolvedField = plan.resolveOrSame(field.field(), "SELECT");
            } else if (field.timeBucketField()) {
                resolvedField = plan.resolveOrSame(field.field(), "SELECT");
            } else if (field.windowField()) {
                ArrayList<String> partitions = new ArrayList<>(field.windowPartitionFields().size());
                for (String partitionField : field.windowPartitionFields()) {
                    partitions.add(plan.resolveOrSame(partitionField, "SELECT"));
                }
                ArrayList<OrderAst> orders = new ArrayList<>(field.windowOrderFields().size());
                for (OrderAst order : field.windowOrderFields()) {
                    orders.add(new OrderAst(plan.resolveOrSame(order.field(), "SELECT"), order.sort()));
                }
                resolvedWindowPartitions = List.copyOf(partitions);
                resolvedWindowOrders = List.copyOf(orders);
                resolvedField = windowExpression(resolvedWindowFunction, resolvedWindowPartitions, resolvedWindowOrders);
            } else if (field.computedField()) {
                resolvedField = rewriteExpression(field.field(), plan, "SELECT");
            } else {
                resolvedField = plan.resolveOrSame(field.field(), "SELECT");
            }
            fields.add(new SelectFieldAst(
                    resolvedField,
                    field.alias(),
                    field.metric(),
                    field.countAll(),
                    field.timeBucketPreset(),
                    field.computedField(),
                    resolvedWindowFunction,
                    resolvedWindowPartitions,
                    resolvedWindowOrders
            ));
        }
        return new SelectAst(select.wildcard(), fields, select.sourceName());
    }

    private static String windowExpression(String function, List<String> partitionFields, List<OrderAst> orders) {
        StringBuilder expression = new StringBuilder(function).append("() OVER (");
        boolean wroteSegment = false;
        if (!partitionFields.isEmpty()) {
            expression.append("PARTITION BY ").append(String.join(", ", partitionFields));
            wroteSegment = true;
        }
        if (!orders.isEmpty()) {
            if (wroteSegment) {
                expression.append(' ');
            }
            expression.append("ORDER BY ");
            for (int i = 0; i < orders.size(); i++) {
                if (i > 0) {
                    expression.append(", ");
                }
                OrderAst order = orders.get(i);
                expression.append(order.field());
                if (order.sort() != null) {
                    expression.append(' ').append(order.sort().name());
                }
            }
        }
        expression.append(')');
        return expression.toString();
    }

    private static List<FilterAst> canonicalizeFilters(List<FilterAst> filters, Plan plan, String clauseName) {
        ArrayList<FilterAst> resolved = new ArrayList<>(filters.size());
        for (FilterAst filter : filters) {
            String field = SqlExpressionEvaluator.looksLikeExpression(filter.field())
                    ? rewriteExpression(filter.field(), plan, clauseName)
                    : plan.resolveOrSame(filter.field(), clauseName);
            Object value = filter.value();
            if (value instanceof SubqueryValueAst subqueryValueAst) {
                value = new SubqueryValueAst(subqueryValueAst.source(), canonicalize(subqueryValueAst.query(), Plan.empty()));
            }
            resolved.add(new FilterAst(field, filter.clause(), value, filter.separator()));
        }
        return resolved;
    }

    private static FilterExpressionAst canonicalizeExpression(FilterExpressionAst expression, Plan plan, String clauseName) {
        if (expression == null) {
            return null;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            FilterAst filter = predicateAst.filter();
            String field = SqlExpressionEvaluator.looksLikeExpression(filter.field())
                    ? rewriteExpression(filter.field(), plan, clauseName)
                    : plan.resolveOrSame(filter.field(), clauseName);
            Object value = filter.value();
            if (value instanceof SubqueryValueAst subqueryValueAst) {
                value = new SubqueryValueAst(subqueryValueAst.source(), canonicalize(subqueryValueAst.query(), Plan.empty()));
            }
            return new FilterPredicateAst(new FilterAst(field, filter.clause(), value, filter.separator()));
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        return new FilterBinaryAst(
                canonicalizeExpression(binary.left(), plan, clauseName),
                canonicalizeExpression(binary.right(), plan, clauseName),
                binary.operator()
        );
    }

    private static List<String> canonicalizeGroupBy(List<String> groupByFields, Plan plan) {
        ArrayList<String> groups = new ArrayList<>(groupByFields.size());
        for (String groupByField : groupByFields) {
            groups.add(plan.resolveOrSame(groupByField, "GROUP BY"));
        }
        return groups;
    }

    private static List<OrderAst> canonicalizeOrders(List<OrderAst> orders, Plan plan) {
        ArrayList<OrderAst> normalized = new ArrayList<>(orders.size());
        for (OrderAst order : orders) {
            normalized.add(new OrderAst(plan.resolveOrSame(order.field(), "ORDER BY"), order.sort()));
        }
        return normalized;
    }

    private static String rewriteExpression(String expression, Plan plan, String clauseName) {
        return SqlExpressionEvaluator.rewriteIdentifiers(expression, identifier -> plan.resolveOrSame(identifier, clauseName));
    }

    private static String normalizeChildReference(String childSource, String childField) {
        String prefix = childSource + ".";
        if (childField.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return childField.substring(prefix.length());
        }
        int separator = childField.indexOf('.');
        if (separator < 0) {
            return childField;
        }
        String source = childField.substring(0, separator);
        if (!childSource.equalsIgnoreCase(source)) {
            return childField;
        }
        return childField.substring(separator + 1);
    }

    public static final class Plan {
        private static final Plan EMPTY = new Plan(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet());

        private final List<ResolvedJoin> joins;
        private final Map<String, String> directReferences;
        private final Map<String, Class<?>> fieldTypes;
        private final Set<String> ambiguousReferences;

        private Plan(List<ResolvedJoin> joins,
                     Map<String, String> directReferences,
                     Map<String, Class<?>> fieldTypes,
                     Set<String> ambiguousReferences) {
            this.joins = List.copyOf(joins);
            this.directReferences = Collections.unmodifiableMap(new LinkedHashMap<>(directReferences));
            this.fieldTypes = Collections.unmodifiableMap(new LinkedHashMap<>(fieldTypes));
            this.ambiguousReferences = Collections.unmodifiableSet(new LinkedHashSet<>(ambiguousReferences));
        }

        public static Plan empty() {
            return EMPTY;
        }

        public boolean isEmpty() {
            return joins.isEmpty() && directReferences.isEmpty();
        }

        public List<ResolvedJoin> joins() {
            return joins;
        }

        public Set<String> mergedFieldNames() {
            return fieldTypes.keySet();
        }

        public Map<String, Class<?>> mergedFieldTypes() {
            return fieldTypes;
        }

        public String resolve(String reference) {
            return directReferences.get(reference);
        }

        public String resolveOrSame(String reference, String clauseName) {
            if (reference == null) {
                return null;
            }
            if (ambiguousReferences.contains(reference)) {
                throw SqlLikeValidator.validation(SqlLikeErrorCodes.VALIDATION_AMBIGUOUS_FIELD,
                        "Ambiguous field reference '" + reference + "' in " + clauseName
                                + " clause. Qualify it with <source>.<field> or use the deterministic merged field name.");
            }
            String resolved = resolve(reference);
            return resolved == null ? reference : resolved;
        }
    }

    public static final class ResolvedJoin {
        private final JoinAst join;
        private final String parentField;
        private final String childField;

        private ResolvedJoin(JoinAst join, String parentField, String childField) {
            this.join = join;
            this.parentField = parentField;
            this.childField = childField;
        }

        public JoinAst join() {
            return join;
        }

        public String parentField() {
            return parentField;
        }

        public String childField() {
            return childField;
        }
    }

    private static final class State {
        private final LinkedHashSet<String> mergedFieldNames = new LinkedHashSet<>();
        private final LinkedHashMap<String, String> directReferences = new LinkedHashMap<>();
        private final LinkedHashMap<String, Class<?>> fieldTypes = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> uniqueRawReferences = new LinkedHashMap<>();
        private final LinkedHashSet<String> ambiguousReferences = new LinkedHashSet<>();

        private void addRoot(String rootSource, Set<String> fields, Map<String, Class<?>> types) {
            for (String field : fields) {
                mergedFieldNames.add(field);
                directReferences.put(field, field);
                uniqueRawReferences.put(field, field);
                fieldTypes.put(field, types.get(field));
                if (rootSource != null) {
                    directReferences.put(rootSource + "." + field, field);
                }
            }
        }

        private String resolveStrict(String reference, String clauseName) {
            if (ambiguousReferences.contains(reference)) {
                throw SqlLikeValidator.validation(SqlLikeErrorCodes.VALIDATION_AMBIGUOUS_FIELD,
                        "Ambiguous field reference '" + reference + "' in " + clauseName
                                + " clause. Qualify it with <source>.<field> or use the deterministic merged field name.");
            }
            String direct = directReferences.get(reference);
            if (direct != null) {
                return direct;
            }
            String unique = uniqueRawReferences.get(reference);
            if (unique != null) {
                return unique;
            }
            throw SqlLikeValidator.validation(SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD,
                    "Unknown field '" + reference + "' in " + clauseName + " clause. Allowed fields: "
                            + new LinkedHashSet<>(directReferences.keySet()));
        }

        private void addChild(String childSource, Set<String> fields, Map<String, Class<?>> types) {
            for (String field : fields) {
                String mergedName = nextMergedName(field);
                mergedFieldNames.add(mergedName);
                directReferences.put(mergedName, mergedName);
                directReferences.put(childSource + "." + field, mergedName);
                fieldTypes.put(mergedName, types.get(field));
                if (ambiguousReferences.contains(field)) {
                    continue;
                }
                if (uniqueRawReferences.containsKey(field)) {
                    uniqueRawReferences.remove(field);
                    ambiguousReferences.add(field);
                } else {
                    uniqueRawReferences.put(field, mergedName);
                }
            }
        }

        private String nextMergedName(String baseName) {
            if (!mergedFieldNames.contains(baseName)) {
                return baseName;
            }
            String candidate = "child_" + baseName;
            int index = 1;
            while (mergedFieldNames.contains(candidate)) {
                candidate = "child_" + baseName + "_" + index;
                index++;
            }
            return candidate;
        }

        private Map<String, String> directReferences() {
            LinkedHashMap<String, String> references = new LinkedHashMap<>(directReferences);
            for (Map.Entry<String, String> entry : uniqueRawReferences.entrySet()) {
                references.putIfAbsent(entry.getKey(), entry.getValue());
            }
            return references;
        }

        private Map<String, Class<?>> fieldTypes() {
            return fieldTypes;
        }

        private Set<String> ambiguousReferences() {
            return ambiguousReferences;
        }
    }
}

