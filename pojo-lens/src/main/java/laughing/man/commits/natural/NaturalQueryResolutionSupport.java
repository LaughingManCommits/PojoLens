package laughing.man.commits.natural;

import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.natural.parser.NaturalQueryParseResult;
import laughing.man.commits.sqllike.internal.aggregate.AggregateExpressionSupport;
import laughing.man.commits.sqllike.internal.aggregate.AggregateExpressionSupport.ParsedAggregateExpression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

final class NaturalQueryResolutionSupport {

    private NaturalQueryResolutionSupport() {
    }

    static ResolvedNaturalQuery resolve(NaturalQueryParseResult parseResult,
                                        Set<String> sourceFields,
                                        NaturalVocabulary vocabulary) {
        Objects.requireNonNull(parseResult, "parseResult must not be null");
        Objects.requireNonNull(sourceFields, "sourceFields must not be null");
        Objects.requireNonNull(vocabulary, "vocabulary must not be null");

        Set<String> exactReferences = collectExactReferences(parseResult.ast());
        LinkedHashMap<String, String> resolvedByNaturalField = new LinkedHashMap<>();
        LinkedHashMap<String, String> resolvedByOriginalPhrase = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : parseResult.sourceFieldPhrases().entrySet()) {
            String originalPhrase = entry.getKey();
            String naturalField = entry.getValue();
            String resolvedField = resolveField(originalPhrase, naturalField, sourceFields, exactReferences, vocabulary);
            String existing = resolvedByNaturalField.putIfAbsent(naturalField, resolvedField);
            if (existing != null && !existing.equals(resolvedField)) {
                throw new IllegalArgumentException(
                        "Natural query resolution error: field term '" + originalPhrase
                                + "' resolved inconsistently to '" + existing + "' and '" + resolvedField + "'"
                );
            }
            resolvedByOriginalPhrase.put(originalPhrase, resolvedField);
        }

        QueryAst resolvedAst = rewrite(parseResult.ast(), resolvedByNaturalField);
        return new ResolvedNaturalQuery(
                resolvedAst,
                resolvedByOriginalPhrase,
                NaturalQueryRenderer.toSqlLike(resolvedAst)
        );
    }

    private static String resolveField(String originalPhrase,
                                       String naturalField,
                                       Set<String> sourceFields,
                                       Set<String> exactReferences,
                                       NaturalVocabulary vocabulary) {
        if (sourceFields.contains(naturalField) || exactReferences.contains(naturalField)) {
            return naturalField;
        }
        List<String> aliasTargets = NaturalVocabularySupport.filterAllowedTargets(
                vocabulary.resolveAliasTargets(naturalField),
                sourceFields
        );
        if (aliasTargets.size() == 1) {
            return aliasTargets.get(0);
        }
        if (aliasTargets.size() > 1) {
            throw new IllegalArgumentException(
                    "Ambiguous natural field term '" + originalPhrase + "' in natural query. Candidates: "
                            + new TreeSet<>(aliasTargets)
            );
        }
        TreeSet<String> allowed = new TreeSet<>(sourceFields);
        allowed.addAll(exactReferences);
        throw new IllegalArgumentException(
                "Unknown natural field term '" + originalPhrase + "' in natural query. Allowed fields: "
                        + allowed
        );
    }

    private static QueryAst rewrite(QueryAst ast, Map<String, String> resolvedByNaturalField) {
        if (resolvedByNaturalField.isEmpty()) {
            return ast;
        }
        SelectAst select = rewriteSelect(ast.select(), resolvedByNaturalField);
        List<String> groups = rewriteGroups(ast.groupByFields(), resolvedByNaturalField);
        List<FilterAst> filters = rewriteFilters(ast.filters(), resolvedByNaturalField);
        FilterExpressionAst whereExpression = rewriteExpression(ast.whereExpression(), resolvedByNaturalField);
        List<FilterAst> havingFilters = rewriteFilters(ast.havingFilters(), resolvedByNaturalField);
        FilterExpressionAst havingExpression = rewriteExpression(ast.havingExpression(), resolvedByNaturalField);
        List<FilterAst> qualifyFilters = rewriteFilters(ast.qualifyFilters(), resolvedByNaturalField);
        FilterExpressionAst qualifyExpression = rewriteExpression(ast.qualifyExpression(), resolvedByNaturalField);
        List<OrderAst> orders = rewriteOrders(ast.orders(), resolvedByNaturalField);
        return new QueryAst(
                select,
                ast.joins(),
                filters,
                whereExpression,
                groups,
                havingFilters,
                havingExpression,
                qualifyFilters,
                qualifyExpression,
                orders,
                ast.limit(),
                ast.limitParameter(),
                ast.offset(),
                ast.offsetParameter()
        );
    }

    private static SelectAst rewriteSelect(SelectAst select, Map<String, String> resolvedByNaturalField) {
        if (select == null || select.wildcard()) {
            return select;
        }
        ArrayList<SelectFieldAst> fields = new ArrayList<>(select.fields().size());
        for (SelectFieldAst field : select.fields()) {
            String resolvedField;
            if (field.metricField()) {
                resolvedField = field.countAll() ? field.field() : rewriteReference(field.field(), resolvedByNaturalField);
            } else if (field.timeBucketField()) {
                resolvedField = resolvedByNaturalField.getOrDefault(field.field(), field.field());
            } else if (field.computedField() || field.windowField()) {
                resolvedField = field.field();
            } else {
                resolvedField = rewriteReference(field.field(), resolvedByNaturalField);
            }
            fields.add(new SelectFieldAst(
                    resolvedField,
                    field.alias(),
                    field.metric(),
                    field.countAll(),
                    field.timeBucketPreset(),
                    field.computedField(),
                    field.windowFunction(),
                    field.windowPartitionFields(),
                    field.windowOrderFields(),
                    field.windowValueField(),
                    field.windowCountAll()
            ));
        }
        return new SelectAst(select.wildcard(), fields, select.sourceName());
    }

    private static List<String> rewriteGroups(List<String> groups, Map<String, String> resolvedByNaturalField) {
        if (groups.isEmpty()) {
            return groups;
        }
        ArrayList<String> rewritten = new ArrayList<>(groups.size());
        for (String group : groups) {
            rewritten.add(rewriteReference(group, resolvedByNaturalField));
        }
        return List.copyOf(rewritten);
    }

    private static List<FilterAst> rewriteFilters(List<FilterAst> filters, Map<String, String> resolvedByNaturalField) {
        if (filters.isEmpty()) {
            return filters;
        }
        ArrayList<FilterAst> rewritten = new ArrayList<>(filters.size());
        for (FilterAst filter : filters) {
            rewritten.add(new FilterAst(
                    rewriteReference(filter.field(), resolvedByNaturalField),
                    filter.clause(),
                    filter.value(),
                    filter.separator()
            ));
        }
        return List.copyOf(rewritten);
    }

    private static FilterExpressionAst rewriteExpression(FilterExpressionAst expression,
                                                         Map<String, String> resolvedByNaturalField) {
        if (expression == null) {
            return null;
        }
        if (expression instanceof FilterPredicateAst predicateAst) {
            FilterAst filter = predicateAst.filter();
            return new FilterPredicateAst(new FilterAst(
                    rewriteReference(filter.field(), resolvedByNaturalField),
                    filter.clause(),
                    filter.value(),
                    filter.separator()
            ));
        }
        FilterBinaryAst binaryAst = (FilterBinaryAst) expression;
        return new FilterBinaryAst(
                rewriteExpression(binaryAst.left(), resolvedByNaturalField),
                rewriteExpression(binaryAst.right(), resolvedByNaturalField),
                binaryAst.operator()
        );
    }

    private static List<OrderAst> rewriteOrders(List<OrderAst> orders, Map<String, String> resolvedByNaturalField) {
        if (orders.isEmpty()) {
            return orders;
        }
        ArrayList<OrderAst> rewritten = new ArrayList<>(orders.size());
        for (OrderAst order : orders) {
            rewritten.add(new OrderAst(
                    rewriteReference(order.field(), resolvedByNaturalField),
                    order.sort()
            ));
        }
        return List.copyOf(rewritten);
    }

    private static Set<String> collectExactReferences(QueryAst ast) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        SelectAst select = ast.select();
        if (select == null || select.wildcard()) {
            return references;
        }
        for (SelectFieldAst field : select.fields()) {
            if (field.aliased()
                    || field.metricField()
                    || field.timeBucketField()
                    || field.windowField()
                    || field.computedField()) {
                references.add(field.outputName());
            }
        }
        return references;
    }

    private static String rewriteReference(String reference, Map<String, String> resolvedByNaturalField) {
        ParsedAggregateExpression aggregateExpression = AggregateExpressionSupport.parse(reference);
        if (aggregateExpression == null) {
            return resolvedByNaturalField.getOrDefault(reference, reference);
        }
        if (aggregateExpression.countAll()) {
            return "count(*)";
        }
        return aggregateExpression.metric().name().toLowerCase(Locale.ROOT)
                + "("
                + resolvedByNaturalField.getOrDefault(aggregateExpression.field(), aggregateExpression.field())
                + ")";
    }

    static final class ResolvedNaturalQuery {
        private final QueryAst ast;
        private final Map<String, String> resolvedByOriginalPhrase;
        private final String equivalentSqlLike;

        private ResolvedNaturalQuery(QueryAst ast,
                                     Map<String, String> resolvedByOriginalPhrase,
                                     String equivalentSqlLike) {
            this.ast = ast;
            this.resolvedByOriginalPhrase = Map.copyOf(new LinkedHashMap<>(resolvedByOriginalPhrase));
            this.equivalentSqlLike = equivalentSqlLike;
        }

        QueryAst ast() {
            return ast;
        }

        Map<String, String> resolvedByOriginalPhrase() {
            return resolvedByOriginalPhrase;
        }

        String equivalentSqlLike() {
            return equivalentSqlLike;
        }
    }
}
