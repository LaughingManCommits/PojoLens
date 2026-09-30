package laughing.man.commits.sqllike.internal.validation;

import laughing.man.commits.computed.ComputedFieldDefinition;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeFieldMessages;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;
import laughing.man.commits.time.TimeBucketPreset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Lowers expressions in {@code GROUP BY} and {@code ORDER BY}, and the computed
 * {@code SELECT} outputs of grouped queries, to query-scoped computed fields (WP-29).
 * Grouping, sorting, keysets, and both execution engines then only see columns, filled
 * before {@code WHERE} like registry fields. A computed {@code SELECT} output keeps its
 * alias as the column name; any other expression gets a hidden {@code __expr_*} column,
 * which never reaches the output. Expressions match by canonical text, so
 * {@code group by year(hireDate)} and {@code select YEAR( hireDate ) as y} share a column.
 */
final class SqlLikeExpressionFields {

    private static final String HIDDEN_PREFIX = "__expr_";
    private static final int MAX_HIDDEN_NAME_TEXT = 40;

    private SqlLikeExpressionFields() {
    }

    /**
     * The lowered AST, the user registry plus the query-scoped computed fields, and the
     * hidden column names.
     */
    record Lowered(QueryAst ast, ComputedFieldRegistry registry, Set<String> hiddenFields) {
    }

    /**
     * True when the query has items this class lowers; subqueries do not support them yet.
     */
    static boolean hasExpressionItems(QueryAst ast) {
        for (String group : ast.groupByFields()) {
            if (SqlExpressionEvaluator.isScalarExpression(group)) {
                return true;
            }
        }
        for (OrderAst order : ast.orders()) {
            if (SqlExpressionEvaluator.isScalarExpression(order.field())) {
                return true;
            }
        }
        return isGrouped(ast) && ast.select() != null && ast.select().hasComputedFields();
    }

    /**
     * @param fieldTypes         source field types plus the user's computed fields
     * @param dynamicProjection  results are {@code QueryRow}s, whose {@code SELECT *} output
     *                           must not carry hidden columns
     */
    static Lowered lower(QueryAst ast,
                         Map<String, Class<?>> fieldTypes,
                         ComputedFieldRegistry registry,
                         boolean dynamicProjection) {
        Lowering lowering = new Lowering(ast, fieldTypes);
        QueryAst lowered = lowering.run(dynamicProjection);
        if (lowering.definitions.isEmpty()) {
            return new Lowered(ast, registry, Set.of());
        }
        ComputedFieldRegistry.Builder merged = ComputedFieldRegistry.builder();
        registry.definitions().values().forEach(merged::add);
        lowering.definitions.forEach(merged::add);
        return new Lowered(lowered, merged.build(), Set.copyOf(lowering.hidden));
    }

    private static boolean isGrouped(QueryAst ast) {
        return ast.hasAggregation() || !ast.groupByFields().isEmpty();
    }

    private static final class Lowering {
        private final QueryAst ast;
        private final Map<String, Class<?>> fieldTypes;
        private final boolean grouped;
        private final boolean distinct;
        private final Map<String, SelectFieldAst> computedByAlias = new LinkedHashMap<>();
        private final Map<String, String> aliasByCanonical = new HashMap<>();
        private final Map<String, String> nameByCanonical = new HashMap<>();
        private final Map<String, String> groupNameByCanonical = new HashMap<>();
        private final Set<String> materializedAliases = new LinkedHashSet<>();
        private final List<ComputedFieldDefinition> definitions = new ArrayList<>();
        private final Set<String> hidden = new LinkedHashSet<>();

        private Lowering(QueryAst ast, Map<String, Class<?>> fieldTypes) {
            this.ast = ast;
            this.fieldTypes = fieldTypes;
            this.grouped = isGrouped(ast);
            this.distinct = ast.select() != null && ast.select().distinct();
        }

        private QueryAst run(boolean dynamicProjection) {
            SelectAst select = ast.select();
            if (select != null && !select.wildcard()) {
                for (SelectFieldAst field : select.fields()) {
                    if (field.computedField() && SqlExpressionEvaluator.isScalarExpression(field.field())) {
                        computedByAlias.put(field.outputName(), field);
                        aliasByCanonical.putIfAbsent(canonical(field.field()), field.outputName());
                    }
                }
            }
            List<String> groups = new ArrayList<>(ast.groupByFields().size());
            for (String group : ast.groupByFields()) {
                groups.add(groupItem(group));
            }
            if (grouped) {
                // Grouped rows no longer hold source fields, so a computed output over source
                // fields needs a column. Outputs over aggregate aliases (post-aggregation
                // expressions) stay computed and keep their validation error.
                for (Map.Entry<String, SelectFieldAst> entry : computedByAlias.entrySet()) {
                    if (fieldTypes.keySet().containsAll(identifiers(entry.getValue().field()))) {
                        materializeAlias(entry.getKey());
                    }
                }
            }
            List<OrderAst> orders = new ArrayList<>(ast.orders().size());
            for (OrderAst order : ast.orders()) {
                orders.add(orderItem(order));
            }
            if (definitions.isEmpty()) {
                return ast;
            }
            List<FilterAst> havingFilters = new ArrayList<>(ast.havingFilters().size());
            for (FilterAst filter : ast.havingFilters()) {
                havingFilters.add(havingFilter(filter));
            }
            return new QueryAst(
                    loweredSelect(select, dynamicProjection),
                    ast.joins(),
                    ast.filters(),
                    ast.whereExpression(),
                    groups,
                    havingFilters,
                    havingExpression(ast.havingExpression()),
                    ast.qualifyFilters(),
                    ast.qualifyExpression(),
                    orders,
                    ast.limit(),
                    ast.limitParameter(),
                    ast.offset(),
                    ast.offsetParameter()
            );
        }

        private String groupItem(String group) {
            if (SqlExpressionEvaluator.isScalarExpression(group)) {
                String name = nameFor(group, "GROUP BY");
                if (distinct && hidden.contains(name)) {
                    throw SqlLikeValidator.validation(SqlLikeErrorCodes.VALIDATION_DISTINCT,
                            "SELECT DISTINCT with GROUP BY must select every GROUP BY field; '" + group + "' is not selected");
                }
                groupNameByCanonical.put(canonical(group), name);
                return name;
            }
            if (SqlExpressionEvaluator.looksLikeExpression(group)) {
                canonical(group); // reports why the text is not a usable expression
            }
            if (computedByAlias.containsKey(group) && !fieldTypes.containsKey(group)) {
                materializeAlias(group);
                groupNameByCanonical.put(canonical(computedByAlias.get(group).field()), group);
            }
            return group;
        }

        private OrderAst orderItem(OrderAst order) {
            String field = order.field();
            if (SqlExpressionEvaluator.isScalarExpression(field)) {
                if (grouped) {
                    // Only a grouped expression is available after aggregation; others keep
                    // the aggregate ORDER BY validation error.
                    String name = groupNameByCanonical.get(canonical(field));
                    return name == null ? order : new OrderAst(name, order.sort());
                }
                String name = nameFor(field, "ORDER BY");
                if (distinct && hidden.contains(name)) {
                    throw SqlLikeValidator.validation(SqlLikeErrorCodes.VALIDATION_DISTINCT,
                            "ORDER BY '" + field + "' must reference a selected field or alias with SELECT DISTINCT");
                }
                return new OrderAst(name, order.sort());
            }
            if (!grouped && computedByAlias.containsKey(field) && !fieldTypes.containsKey(field)) {
                materializeAlias(field);
            }
            return order;
        }

        /**
         * The column for an expression: an equal computed SELECT output's alias, or a hidden
         * column.
         */
        private String nameFor(String expression, String clauseName) {
            String canonical = canonical(expression);
            String existing = nameByCanonical.get(canonical);
            if (existing != null) {
                return existing;
            }
            String alias = aliasByCanonical.get(canonical);
            String name;
            if (alias != null && !fieldTypes.containsKey(alias)) {
                materializeAlias(alias);
                name = alias;
            } else {
                name = hiddenName(canonical);
                define(name, expression, clauseName);
                hidden.add(name);
            }
            nameByCanonical.put(canonical, name);
            return name;
        }

        private void materializeAlias(String alias) {
            if (!materializedAliases.add(alias)) {
                return;
            }
            if (fieldTypes.containsKey(alias)) {
                throw SqlLikeValidator.validation(SqlLikeErrorCodes.VALIDATION_COMPUTED_SELECT,
                        "Computed SELECT alias '" + alias + "' is also a field name; "
                                + "use another alias to group or sort by the expression");
            }
            String expression = computedByAlias.get(alias).field();
            define(alias, expression, "SELECT");
            nameByCanonical.putIfAbsent(canonical(expression), alias);
        }

        private void define(String name, String expression, String clauseName) {
            for (String identifier : identifiers(expression)) {
                if (!fieldTypes.containsKey(identifier)) {
                    throw SqlLikeValidator.validation(SqlLikeErrorCodes.VALIDATION_UNKNOWN_FIELD,
                            SqlLikeFieldMessages.unknownField(identifier, clauseName, fieldTypes.keySet()));
                }
            }
            Class<?> outputType = expressionCall(() -> SqlExpressionEvaluator.resultType(expression, fieldTypes::get));
            definitions.add(ComputedFieldDefinition.of(name, expression, outputType));
        }

        private FilterAst havingFilter(FilterAst filter) {
            String name = groupExpressionName(filter.field());
            return name == null ? filter : new FilterAst(name, filter.clause(), filter.value(), filter.separator());
        }

        private FilterExpressionAst havingExpression(FilterExpressionAst expression) {
            return switch (expression) {
                case null -> null;
                case FilterPredicateAst predicate -> new FilterPredicateAst(havingFilter(predicate.filter()));
                case FilterBinaryAst binary -> new FilterBinaryAst(
                        havingExpression(binary.left()), havingExpression(binary.right()), binary.operator());
            };
        }

        /**
         * A HAVING expression equal to a grouped expression reads its column; any other text
         * is left for HAVING validation.
         */
        private String groupExpressionName(String reference) {
            if (!SqlExpressionEvaluator.isScalarExpression(reference)) {
                return null;
            }
            try {
                return groupNameByCanonical.get(SqlExpressionEvaluator.canonical(reference));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        private SelectAst loweredSelect(SelectAst select, boolean dynamicProjection) {
            if (select == null || select.wildcard()) {
                if (hidden.isEmpty() || !dynamicProjection) {
                    return select;
                }
                // SELECT * into QueryRow: list the fields so the hidden columns stay out.
                List<SelectFieldAst> fields = new ArrayList<>(fieldTypes.size());
                for (String field : fieldTypes.keySet()) {
                    fields.add(plainField(field));
                }
                return new SelectAst(false, fields, select == null ? null : select.sourceName(),
                        select != null && select.distinct());
            }
            if (materializedAliases.isEmpty()) {
                return select;
            }
            List<SelectFieldAst> fields = new ArrayList<>(select.fields().size());
            for (SelectFieldAst field : select.fields()) {
                boolean materialized = field.computedField() && materializedAliases.contains(field.outputName());
                fields.add(materialized ? plainField(field.outputName()) : field);
            }
            return new SelectAst(false, fields, select.sourceName(), select.distinct());
        }

        private static SelectFieldAst plainField(String name) {
            return new SelectFieldAst(name, null, null, false, (TimeBucketPreset) null, false);
        }

        private static String canonical(String expression) {
            return expressionCall(() -> SqlExpressionEvaluator.canonical(expression));
        }

        private static Set<String> identifiers(String expression) {
            return expressionCall(() -> SqlExpressionEvaluator.collectIdentifiers(expression));
        }

        private static <T> T expressionCall(Supplier<T> call) {
            try {
                return call.get();
            } catch (IllegalArgumentException ex) {
                throw SqlLikeValidator.validation(SqlLikeErrorCodes.VALIDATION_EXPRESSION_REFERENCE, ex.getMessage());
            }
        }

        private static String hiddenName(String canonical) {
            String text = canonical.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
            if (text.length() > MAX_HIDDEN_NAME_TEXT) {
                text = text.substring(0, MAX_HIDDEN_NAME_TEXT);
            }
            return HIDDEN_PREFIX + text + "_" + Integer.toHexString(canonical.hashCode());
        }
    }
}
