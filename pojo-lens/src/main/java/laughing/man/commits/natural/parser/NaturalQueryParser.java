package laughing.man.commits.natural.parser;

import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.natural.NaturalWindowSupport;
import laughing.man.commits.natural.NaturalVocabularySupport;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.JoinAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.ParameterValueAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.util.StringUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parser for the controlled MVP plain-English query surface.
 */
public final class NaturalQueryParser {

    private static final Set<String> WILDCARD_TERMS = Set.of(
            "all",
            "rows",
            "records",
            "items",
            "results",
            "employees"
    );
    private static final Set<String> OPTIONAL_REFERENCE_FILLERS = Set.of(
            "the",
            "a",
            "an"
    );

    private NaturalQueryParser() {
    }

    public static QueryAst parse(String input) {
        return parseResult(input).ast();
    }

    public static NaturalQueryParseResult parseResult(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Natural query must not be null");
        }
        String normalized = input.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Natural query must not be blank");
        }
        return new Parser(normalized).parseQuery();
    }

    private static final class Parser {
        private final String input;
        private final List<Token> tokens;
        private final LinkedHashMap<String, String> sourceFieldPhrases = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> sourceNameByAlias = new LinkedHashMap<>();
        private int index;

        private Parser(String input) {
            this.input = input;
            this.tokens = tokenize(input);
        }

        private NaturalQueryParseResult parseQuery() {
            String rootSource = null;
            List<JoinAst> joins = List.of();
            if (matchWord("from")) {
                rootSource = parseSourceDefinition("FROM");
                joins = parseJoins(rootSource);
            }
            expectWord("show");
            matchWord("me");
            SelectAst select = parseSelect(rootSource);
            FilterExpressionAst whereExpression = null;
            List<FilterAst> filters = List.of();
            List<String> groupByFields = List.of();
            FilterExpressionAst havingExpression = null;
            List<FilterAst> havingFilters = List.of();
            FilterExpressionAst qualifyExpression = null;
            List<FilterAst> qualifyFilters = List.of();
            List<OrderAst> orders = List.of();
            Integer limit = null;
            String limitParameter = null;
            Integer offset = null;
            String offsetParameter = null;
            ChartType chartType = null;

            if (matchWord("where")) {
                whereExpression = parseBooleanExpression(false, "WHERE");
                filters = flatten(whereExpression);
            }
            if (matchWord("group")) {
                expectWord("by");
                groupByFields = parseGroupBy();
            }
            if (matchWord("having")) {
                havingExpression = parseBooleanExpression(true, "HAVING");
                havingFilters = flatten(havingExpression);
            }
            if (matchWord("qualify")) {
                qualifyExpression = parseBooleanExpression(false, "QUALIFY");
                qualifyFilters = flatten(qualifyExpression);
            }
            if (peekSortBy()) {
                next();
                expectWord("by");
                orders = parseSortBy();
            }
            if (matchWord("limit")) {
                PaginationValue parsed = parsePagination("LIMIT");
                limit = parsed.literal();
                limitParameter = parsed.parameterName();
            }
            if (matchWord("offset")) {
                PaginationValue parsed = parsePagination("OFFSET");
                offset = parsed.literal();
                offsetParameter = parsed.parameterName();
            }
            if (matchWord("as")) {
                chartType = parseChartPhrase();
            }
            if (!peek().isEof()) {
                throw error("Unexpected token '" + peek().text + "'", peek().position);
            }
            return new NaturalQueryParseResult(
                    new QueryAst(
                            select,
                            joins,
                            filters,
                            whereExpression,
                            groupByFields,
                            havingFilters,
                            havingExpression,
                            qualifyFilters,
                            qualifyExpression,
                            orders,
                            limit,
                            limitParameter,
                            offset,
                            offsetParameter
                    ),
                    sourceFieldPhrases,
                    chartType
            );
        }

        private SelectAst parseSelect(String sourceName) {
            List<List<Token>> items = new ArrayList<>();
            items.add(readClauseItem("SHOW"));
            while (match(TokenType.COMMA)) {
                items.add(readClauseItem("SHOW"));
            }
            if (items.isEmpty() || items.get(0).isEmpty()) {
                throw error("SHOW requires 'all' or one or more projected fields", peek().position);
            }
            if (items.size() == 1 && isWildcardItem(items.get(0))) {
                return new SelectAst(true, List.of(), sourceName);
            }

            ArrayList<SelectFieldAst> fields = new ArrayList<>(items.size());
            for (List<Token> item : items) {
                if (item.isEmpty()) {
                    throw error("SHOW projection item must not be blank", peek().position);
                }
                if (isWildcardItem(item)) {
                    throw error("SHOW wildcard cannot be combined with explicit fields", item.get(0).position);
                }
                fields.add(parseSelectField(item));
            }
            return new SelectAst(false, fields, sourceName);
        }

        private SelectFieldAst parseSelectField(List<Token> itemTokens) {
            int asIndex = lastIndexOfWord(itemTokens, "as");
            List<Token> fieldTokens = asIndex < 0 ? itemTokens : itemTokens.subList(0, asIndex);
            List<Token> aliasTokens = asIndex < 0 ? List.of() : itemTokens.subList(asIndex + 1, itemTokens.size());
            String alias = aliasTokens.isEmpty() ? null : normalizeAlias(aliasTokens);
            WindowPhrase windowPhrase = tryParseWindowPhrase(fieldTokens, "SHOW");
            if (windowPhrase != null) {
                if (alias == null) {
                    throw error("Window SELECT expressions require AS alias", fieldTokens.get(0).position);
                }
                return new SelectFieldAst(
                        NaturalWindowSupport.renderWindowExpression(
                                windowPhrase.function(),
                                windowPhrase.valueField(),
                                windowPhrase.countAll(),
                                windowPhrase.partitionFields(),
                                windowPhrase.orderFields()
                        ),
                        alias,
                        null,
                        false,
                        (TimeBucketPreset) null,
                        false,
                        windowPhrase.function(),
                        windowPhrase.partitionFields(),
                        windowPhrase.orderFields(),
                        windowPhrase.valueField(),
                        windowPhrase.countAll()
                );
            }
            MetricPhrase metricPhrase = tryParseMetricPhrase(fieldTokens, "SHOW");
            if (metricPhrase != null) {
                return new SelectFieldAst(
                        metricPhrase.field(),
                        alias,
                        metricPhrase.metric(),
                        metricPhrase.countAll(),
                        (TimeBucketPreset) null,
                        false
                );
            }
            TimeBucketPhrase timeBucketPhrase = tryParseTimeBucketPhrase(fieldTokens, "SHOW");
            if (timeBucketPhrase != null) {
                return new SelectFieldAst(
                        timeBucketPhrase.field(),
                        alias,
                        null,
                        false,
                        timeBucketPhrase.preset(),
                        false
                );
            }
            return new SelectFieldAst(
                    normalizeTrackedReference(fieldTokens),
                    alias,
                    null,
                    false,
                    (TimeBucketPreset) null,
                    false
            );
        }

        private FilterExpressionAst parseBooleanExpression(boolean allowAggregateReferences, String clauseName) {
            FilterExpressionAst expression = parseOrExpression(allowAggregateReferences, clauseName);
            if (expression == null) {
                throw error(clauseName + " requires at least one predicate", peek().position);
            }
            return expression;
        }

        private FilterExpressionAst parseOrExpression(boolean allowAggregateReferences, String clauseName) {
            FilterExpressionAst left = parseAndExpression(allowAggregateReferences, clauseName);
            while (matchWord("or")) {
                left = new FilterBinaryAst(left, parseAndExpression(allowAggregateReferences, clauseName), Separator.OR);
            }
            return left;
        }

        private FilterExpressionAst parseAndExpression(boolean allowAggregateReferences, String clauseName) {
            FilterExpressionAst left = parsePredicate(allowAggregateReferences, clauseName);
            while (matchWord("and")) {
                left = new FilterBinaryAst(left, parsePredicate(allowAggregateReferences, clauseName), Separator.AND);
            }
            return left;
        }

        private FilterExpressionAst parsePredicate(boolean allowAggregateReferences, String clauseName) {
            if (peek().type == TokenType.LEFT_PAREN || peek().type == TokenType.RIGHT_PAREN) {
                throw error("Parentheses are not supported in MVP natural queries", peek().position);
            }

            OperatorMatch operator = findOperator();
            if (operator == null) {
                throw error("Expected a supported operator phrase in " + clauseName, peek().position);
            }

            List<Token> fieldTokens = slice(index, operator.startIndex());
            if (fieldTokens.isEmpty()) {
                throw error("Expected field before operator", peek().position);
            }
            String field = parseReference(fieldTokens, allowAggregateReferences, clauseName);

            index = operator.endIndex();
            List<Token> valueTokens = readValueTokens();
            if (valueTokens.isEmpty()) {
                throw error("Expected value after operator", peek().position);
            }
            Object value = parseValue(valueTokens, operator.operator());
            return new FilterPredicateAst(new FilterAst(field, operator.operator().clause, value, null));
        }

        private List<String> parseGroupBy() {
            ArrayList<String> groups = new ArrayList<>();
            groups.add(parseGroupItem(readClauseItem("GROUP BY")));
            while (match(TokenType.COMMA)) {
                groups.add(parseGroupItem(readClauseItem("GROUP BY")));
            }
            return List.copyOf(groups);
        }

        private String parseGroupItem(List<Token> itemTokens) {
            if (itemTokens.isEmpty()) {
                throw error("GROUP BY item must not be blank", peek().position);
            }
            return normalizeTrackedReference(itemTokens);
        }

        private List<OrderAst> parseSortBy() {
            ArrayList<OrderAst> orders = new ArrayList<>();
            orders.add(parseSortItem(readClauseItem("SORT BY")));
            while (match(TokenType.COMMA)) {
                orders.add(parseSortItem(readClauseItem("SORT BY")));
            }
            return List.copyOf(orders);
        }

        private OrderAst parseSortItem(List<Token> itemTokens) {
            if (itemTokens.isEmpty()) {
                throw error("SORT BY item must not be blank", peek().position);
            }
            Sort sort = Sort.ASC;
            List<Token> fieldTokens = itemTokens;
            if (endsWithWord(itemTokens, "ascending") || endsWithWord(itemTokens, "asc")) {
                sort = Sort.ASC;
                fieldTokens = itemTokens.subList(0, itemTokens.size() - 1);
            } else if (endsWithWord(itemTokens, "descending") || endsWithWord(itemTokens, "desc")) {
                sort = Sort.DESC;
                fieldTokens = itemTokens.subList(0, itemTokens.size() - 1);
            }
            if (fieldTokens.isEmpty()) {
                throw error("SORT BY field must not be blank", peek().position);
            }
            return new OrderAst(parseReference(fieldTokens, true, "ORDER BY"), sort);
        }

        private PaginationValue parsePagination(String clauseName) {
            Token token = peek();
            if (token.isEof()) {
                throw error("Expected numeric or parameter " + clauseName + " value", token.position);
            }
            next();
            if (token.type != TokenType.RAW) {
                throw error("Expected numeric or parameter " + clauseName + " value", token.position);
            }
            if (token.text.startsWith(":")) {
                String parameterName = token.text.substring(1);
                if (parameterName.isEmpty()) {
                    throw error("Expected parameter name after ':'", token.position);
                }
                return PaginationValue.parameter(parameterName);
            }
            try {
                int parsed = Integer.parseInt(token.text);
                if (parsed < 0) {
                    throw error(clauseName + " must be >= 0", token.position);
                }
                return PaginationValue.literal(parsed);
            } catch (NumberFormatException ex) {
                throw error(clauseName + " must be an integer", token.position);
            }
        }

        private OperatorMatch findOperator() {
            int start = index;
            while (start < tokens.size()) {
                if (isBooleanBoundary(tokens.get(start)) || isClauseBoundary(start)) {
                    return null;
                }
                for (Operator operator : Operator.values()) {
                    if (matchesWords(start, operator.phrase)) {
                        return new OperatorMatch(operator, start, start + operator.phrase.length);
                    }
                }
                start++;
            }
            return null;
        }

        private List<Token> readClauseItem(String clauseName) {
            ArrayList<Token> item = new ArrayList<>();
            while (true) {
                Token token = peek();
                if (token.isEof() || token.type == TokenType.COMMA || isClauseBoundary(index)) {
                    break;
                }
                if (token.type == TokenType.LEFT_PAREN || token.type == TokenType.RIGHT_PAREN) {
                    throw error("Parentheses are not supported in " + clauseName, token.position);
                }
                item.add(next());
            }
            if (item.isEmpty()) {
                throw error(clauseName + " item must not be blank", peek().position);
            }
            return List.copyOf(item);
        }

        private List<Token> readValueTokens() {
            ArrayList<Token> value = new ArrayList<>();
            while (true) {
                Token token = peek();
                if (token.isEof() || isBooleanBoundary(token) || isClauseBoundary(index)) {
                    break;
                }
                if (token.type == TokenType.COMMA) {
                    break;
                }
                value.add(next());
            }
            return List.copyOf(value);
        }

        private boolean isWildcardItem(List<Token> itemTokens) {
            List<Token> normalizedTokens = stripLeadingReferenceFillers(itemTokens);
            if (normalizedTokens.size() != 1) {
                return false;
            }
            Token token = normalizedTokens.get(0);
            if (token.type == TokenType.STAR) {
                return true;
            }
            return token.type == TokenType.RAW
                    && (WILDCARD_TERMS.contains(token.text.toLowerCase(Locale.ROOT))
                    || resolveKnownSourceAlias(token.text) != null);
        }

        private boolean isBooleanBoundary(Token token) {
            return isWord(token, "and") || isWord(token, "or");
        }

        private boolean isClauseBoundary(int tokenIndex) {
            Token token = tokenAt(tokenIndex);
            if (token.isEof()) {
                return true;
            }
            if (isWord(token, "where")
                    || isWord(token, "having")
                    || isWord(token, "qualify")
                    || isWord(token, "limit")
                    || isWord(token, "offset")) {
                return true;
            }
            if (isWord(token, "group") && isWord(tokenAt(tokenIndex + 1), "by")) {
                return true;
            }
            if (isWord(token, "sort") && isWord(tokenAt(tokenIndex + 1), "by")) {
                return true;
            }
            return isChartBoundary(tokenIndex);
        }

        private List<JoinAst> parseJoins(String rootSource) {
            ArrayList<JoinAst> joins = new ArrayList<>();
            ArrayList<String> availableSources = new ArrayList<>();
            availableSources.add(rootSource);
            while (peekJoinStart()) {
                joins.add(parseJoin(availableSources));
            }
            return List.copyOf(joins);
        }

        private JoinAst parseJoin(List<String> availableSources) {
            Join joinType = parseJoinType();
            String childSource = parseSourceDefinition("JOIN");
            for (String availableSource : availableSources) {
                if (availableSource.equalsIgnoreCase(childSource)) {
                    throw error("JOIN source '" + childSource + "' is already used", peek().position);
                }
            }
            expectWord("on");
            JoinReference left = parseJoinReference();
            expectWord("equals");
            JoinReference right = parseJoinReference();
            ResolvedJoinFields resolved = resolveJoinFields(availableSources, childSource, left, right);
            availableSources.add(childSource);
            return new JoinAst(joinType, childSource, resolved.parentField(), resolved.childField());
        }

        private Join parseJoinType() {
            if (matchWord("left")) {
                expectWord("join");
                return Join.LEFT_JOIN;
            }
            if (matchWord("right")) {
                expectWord("join");
                return Join.RIGHT_JOIN;
            }
            if (matchWord("inner")) {
                expectWord("join");
                return Join.INNER_JOIN;
            }
            expectWord("join");
            return Join.INNER_JOIN;
        }

        private String parseSourceDefinition(String clauseName) {
            skipOptionalReferenceFillers();
            Token sourceToken = peek();
            if (sourceToken.type != TokenType.RAW) {
                throw error("Expected source name after " + clauseName, sourceToken.position);
            }
            String sourceName = sourceToken.text;
            next();
            registerSourceAlias(sourceName, sourceName);
            if (matchWord("as")) {
                Token labelToken = peek();
                if (labelToken.type != TokenType.RAW) {
                    throw error("Expected source label after AS in " + clauseName, labelToken.position);
                }
                registerSourceAlias(sourceName, labelToken.text);
                next();
            }
            return sourceName;
        }

        private void skipOptionalReferenceFillers() {
            while (isOptionalReferenceFiller(peek())) {
                next();
            }
        }

        private void registerSourceAlias(String sourceName, String alias) {
            String normalizedAlias = sourceAliasKey(alias);
            String existing = sourceNameByAlias.get(normalizedAlias);
            if (existing != null && !existing.equalsIgnoreCase(sourceName)) {
                throw error("Source label '" + alias + "' is already used for '" + existing + "'", peek().position);
            }
            sourceNameByAlias.put(normalizedAlias, sourceName);
        }

        private String resolveKnownSourceAlias(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return sourceNameByAlias.get(sourceAliasKey(value));
        }

        private String sourceAliasKey(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }

        private boolean peekJoinStart() {
            return isWord(peek(), "join")
                    || isWord(peek(), "left")
                    || isWord(peek(), "right")
                    || isWord(peek(), "inner");
        }

        private boolean peekSortBy() {
            return isWord(peek(), "sort") && isWord(tokenAt(index + 1), "by");
        }

        private boolean matchWord(String expected) {
            if (isWord(peek(), expected)) {
                next();
                return true;
            }
            return false;
        }

        private void expectWord(String expected) {
            if (!matchWord(expected)) {
                throw error("Expected '" + expected + "'", peek().position);
            }
        }

        private boolean match(TokenType expected) {
            if (peek().type == expected) {
                next();
                return true;
            }
            return false;
        }

        private Token peek() {
            return tokenAt(index);
        }

        private Token tokenAt(int tokenIndex) {
            if (tokenIndex < 0 || tokenIndex >= tokens.size()) {
                return tokens.get(tokens.size() - 1);
            }
            return tokens.get(tokenIndex);
        }

        private Token next() {
            return tokens.get(index++);
        }

        private List<Token> slice(int startInclusive, int endExclusive) {
            return List.copyOf(tokens.subList(startInclusive, endExclusive));
        }

        private void rememberSourceFieldPhrase(List<Token> fieldTokens, String normalizedReference) {
            if (fieldTokens == null || fieldTokens.isEmpty()) {
                return;
            }
            String original = joinTokens(fieldTokens);
            sourceFieldPhrases.putIfAbsent(original, normalizedReference);
        }

        private boolean matchesWords(int startIndex, String[] expected) {
            for (int i = 0; i < expected.length; i++) {
                Token token = tokenAt(startIndex + i);
                if (!isWord(token, expected[i])) {
                    return false;
                }
            }
            return true;
        }

        private IllegalArgumentException error(String message, int position) {
            int safe = Math.max(0, Math.min(position, input.length()));
            int line = 1;
            int column = 1;
            for (int i = 0; i < safe; i++) {
                if (input.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new IllegalArgumentException(
                    "Natural query parse error: " + message + " at line " + line + ", column " + column
            );
        }

        private ChartType parseChartPhrase() {
            Token typeToken = peek();
            if (typeToken.type != TokenType.RAW) {
                throw error("Expected chart type after 'as'", typeToken.position);
            }
            ChartType chartType = chartTypeFromWord(typeToken.text);
            if (chartType == null) {
                throw error("Unsupported natural chart type '" + typeToken.text + "'", typeToken.position);
            }
            next();
            expectWord("chart");
            return chartType;
        }

        private boolean isChartBoundary(int tokenIndex) {
            return isWord(tokenAt(tokenIndex), "as")
                    && chartTypeFromWord(tokenAt(tokenIndex + 1).text) != null
                    && isWord(tokenAt(tokenIndex + 2), "chart");
        }

        private String parseReference(List<Token> fieldTokens,
                                      boolean allowAggregateReferences,
                                      String clauseName) {
            if (fieldTokens == null || fieldTokens.isEmpty()) {
                throw error("Expected field/expression in " + clauseName, peek().position);
            }
            MetricPhrase metricPhrase = allowAggregateReferences ? tryParseMetricPhrase(fieldTokens, clauseName) : null;
            if (metricPhrase != null) {
                return renderMetricReference(metricPhrase);
            }
            return normalizeTrackedReference(fieldTokens);
        }

        private MetricPhrase tryParseMetricPhrase(List<Token> tokens, String clauseName) {
            if (tokens == null || tokens.isEmpty()) {
                return null;
            }
            Metric metric = metricFromWord(tokens.get(0));
            if (metric == null) {
                return null;
            }
            int fieldStart = 1;
            if (fieldStart < tokens.size() && isWord(tokens.get(fieldStart), "of")) {
                fieldStart++;
            }
            if (fieldStart >= tokens.size()) {
                throw error("Expected field after aggregate phrase in " + clauseName, tokens.get(0).position);
            }
            List<Token> fieldTokens = tokens.subList(fieldStart, tokens.size());
            if (metric == Metric.COUNT && isWildcardItem(fieldTokens)) {
                return new MetricPhrase(metric, true, "*");
            }
            if (isWildcardItem(fieldTokens)) {
                throw error(metric.name() + " of wildcard terms is not supported in " + clauseName, fieldTokens.get(0).position);
            }
            return new MetricPhrase(metric, false, normalizeTrackedReference(fieldTokens));
        }

        private TimeBucketPhrase tryParseTimeBucketPhrase(List<Token> tokens, String clauseName) {
            if (tokens == null || tokens.isEmpty() || !isWord(tokens.get(0), "bucket")) {
                return null;
            }
            int byIndex = indexOfWord(tokens, "by");
            if (byIndex < 0) {
                throw error("Expected 'by' in time-bucket phrase in " + clauseName, tokens.get(0).position);
            }
            if (byIndex == 1) {
                throw error("Expected field after 'bucket' in " + clauseName, tokens.get(0).position);
            }
            if (byIndex + 1 >= tokens.size()) {
                throw error("Expected bucket granularity after 'by' in " + clauseName, tokens.get(byIndex).position);
            }

            List<Token> fieldTokens = tokens.subList(1, byIndex);

            int cursor = byIndex + 1;
            Token bucketToken = tokens.get(cursor++);
            if (bucketToken.type != TokenType.RAW) {
                throw error("Expected bucket granularity after 'by' in " + clauseName, bucketToken.position);
            }

            String zoneValue = null;
            String weekStartValue = null;
            if (cursor < tokens.size() && isWord(tokens.get(cursor), "in")) {
                int zoneStart = ++cursor;
                while (cursor < tokens.size()
                        && !isWord(tokens.get(cursor), "starting")
                        && !(isWord(tokens.get(cursor), "week") && cursor + 1 < tokens.size()
                        && isWord(tokens.get(cursor + 1), "starting"))) {
                    cursor++;
                }
                if (zoneStart == cursor) {
                    throw error("Expected time zone after 'in' in " + clauseName, tokens.get(zoneStart - 1).position);
                }
                zoneValue = joinTokens(tokens.subList(zoneStart, cursor));
            }

            if (cursor < tokens.size()) {
                if (isWord(tokens.get(cursor), "week")
                        && cursor + 1 < tokens.size()
                        && isWord(tokens.get(cursor + 1), "starting")) {
                    cursor += 2;
                } else if (isWord(tokens.get(cursor), "starting")) {
                    cursor++;
                } else {
                    throw error("Unexpected token '" + tokens.get(cursor).text + "' in time-bucket phrase", tokens.get(cursor).position);
                }
                if (cursor >= tokens.size()) {
                    throw error("Expected week-start value in " + clauseName, tokens.get(tokens.size() - 1).position);
                }
                weekStartValue = joinTokens(tokens.subList(cursor, tokens.size()));
                cursor = tokens.size();
            }

            try {
                return new TimeBucketPhrase(
                        normalizeTrackedReference(fieldTokens),
                        TimeBucketPreset.parse(bucketToken.text, zoneValue, weekStartValue)
                );
            } catch (IllegalArgumentException ex) {
                throw error(ex.getMessage(), bucketToken.position);
            }
        }

        private WindowPhrase tryParseWindowPhrase(List<Token> tokens, String clauseName) {
            if (tokens == null || tokens.isEmpty()) {
                return null;
            }
            WindowFunctionStart functionStart = parseWindowFunctionStart(tokens, clauseName);
            if (functionStart == null) {
                return null;
            }

            int cursor = functionStart.nextIndex();
            String valueField = functionStart.valueField();
            boolean countAll = functionStart.countAll();
            List<String> partitionFields = List.of();
            if (cursor < tokens.size() && isWord(tokens.get(cursor), "by")) {
                int partitionStart = ++cursor;
                while (cursor < tokens.size()
                        && !(isWord(tokens.get(cursor), "ordered")
                        && cursor + 1 < tokens.size()
                        && isWord(tokens.get(cursor + 1), "by"))) {
                    cursor++;
                }
                if (partitionStart == cursor) {
                    throw error("Expected partition field after 'by' in " + clauseName, tokens.get(partitionStart - 1).position);
                }
                partitionFields = List.of(normalizeTrackedReference(tokens.subList(partitionStart, cursor)));
            }

            if (!(cursor < tokens.size()
                    && isWord(tokens.get(cursor), "ordered")
                    && cursor + 1 < tokens.size()
                    && isWord(tokens.get(cursor + 1), "by"))) {
                throw error("Window SELECT expressions require 'ordered by' clause", tokens.get(tokens.size() - 1).position);
            }
            cursor += 2;
            if (cursor >= tokens.size()) {
                throw error("Expected field after 'ordered by' in " + clauseName, tokens.get(tokens.size() - 1).position);
            }
            List<OrderAst> orderFields = parseWindowOrderFields(tokens.subList(cursor, tokens.size()), clauseName);
            return new WindowPhrase(
                    functionStart.function(),
                    valueField,
                    countAll,
                    partitionFields,
                    orderFields
            );
        }

        private WindowFunctionStart parseWindowFunctionStart(List<Token> tokens, String clauseName) {
            if (matchesWords(tokens, 0, "row", "number")) {
                return new WindowFunctionStart("ROW_NUMBER", null, false, 2);
            }
            if (matchesWords(tokens, 0, "dense", "rank")) {
                return new WindowFunctionStart("DENSE_RANK", null, false, 2);
            }
            if (isWord(tokens.get(0), "rank")) {
                return new WindowFunctionStart("RANK", null, false, 1);
            }
            if (!isWord(tokens.get(0), "running")) {
                return null;
            }
            if (tokens.size() == 1) {
                throw error("Expected running window function after 'running' in " + clauseName, tokens.get(0).position);
            }
            Metric metric = metricFromWord(tokens.get(1));
            if (metric == null) {
                throw error("Unsupported running window function '" + tokens.get(1).text + "'", tokens.get(1).position);
            }
            int cursor = 2;
            if (cursor < tokens.size() && isWord(tokens.get(cursor), "of")) {
                cursor++;
            }
            if (cursor >= tokens.size()) {
                throw error("Expected field after running window function in " + clauseName, tokens.get(tokens.size() - 1).position);
            }
            int valueStart = cursor;
            while (cursor < tokens.size()
                    && !isWord(tokens.get(cursor), "by")
                    && !(isWord(tokens.get(cursor), "ordered")
                    && cursor + 1 < tokens.size()
                    && isWord(tokens.get(cursor + 1), "by"))) {
                cursor++;
            }
            List<Token> valueTokens = tokens.subList(valueStart, cursor);
            if (valueTokens.isEmpty()) {
                throw error("Expected field after running window function in " + clauseName, tokens.get(valueStart).position);
            }
            if (metric == Metric.COUNT && isWildcardItem(valueTokens)) {
                return new WindowFunctionStart("COUNT", null, true, cursor);
            }
            if (isWildcardItem(valueTokens)) {
                throw error(metric.name() + " running window does not support wildcard terms", valueTokens.get(0).position);
            }
            return new WindowFunctionStart(
                    metric.name(),
                    normalizeTrackedReference(valueTokens),
                    false,
                    cursor
            );
        }

        private List<OrderAst> parseWindowOrderFields(List<Token> tokens, String clauseName) {
            ArrayList<OrderAst> orders = new ArrayList<>();
            int cursor = 0;
            while (cursor < tokens.size()) {
                int nextSeparator = cursor;
                while (nextSeparator < tokens.size() && !isWord(tokens.get(nextSeparator), "then")) {
                    nextSeparator++;
                }
                List<Token> orderTokens = tokens.subList(cursor, nextSeparator);
                orders.add(parseWindowOrderItem(orderTokens, clauseName));
                cursor = nextSeparator + 1;
            }
            return List.copyOf(orders);
        }

        private OrderAst parseWindowOrderItem(List<Token> itemTokens, String clauseName) {
            if (itemTokens.isEmpty()) {
                throw error("Expected field in window 'ordered by' clause", peek().position);
            }
            Sort sort = Sort.ASC;
            List<Token> fieldTokens = itemTokens;
            if (endsWithWord(itemTokens, "ascending") || endsWithWord(itemTokens, "asc")) {
                fieldTokens = itemTokens.subList(0, itemTokens.size() - 1);
            } else if (endsWithWord(itemTokens, "descending") || endsWithWord(itemTokens, "desc")) {
                sort = Sort.DESC;
                fieldTokens = itemTokens.subList(0, itemTokens.size() - 1);
            }
            if (fieldTokens.isEmpty()) {
                throw error("Expected field in window 'ordered by' clause", itemTokens.get(0).position);
            }
            return new OrderAst(normalizeTrackedReference(fieldTokens), sort);
        }

        private boolean matchesWords(List<Token> tokens, int startIndex, String... expected) {
            if (tokens.size() < startIndex + expected.length) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if (!isWord(tokens.get(startIndex + i), expected[i])) {
                    return false;
                }
            }
            return true;
        }

        private JoinReference parseJoinReference() {
            List<Token> referenceTokens = readJoinReferenceTokens();
            if (referenceTokens.isEmpty()) {
                throw error("Expected field reference in JOIN clause", peek().position);
            }
            QualifiedReference qualified = tryParseQualifiedReference(referenceTokens);
            if (qualified != null) {
                rememberSourceFieldPhrase(referenceTokens, qualified.reference());
                return new JoinReference(qualified.reference(), qualified.sourceName());
            }
            String normalized = normalizeFieldReference(referenceTokens);
            rememberSourceFieldPhrase(referenceTokens, normalized);
            return new JoinReference(normalized, null);
        }

        private List<Token> readJoinReferenceTokens() {
            ArrayList<Token> reference = new ArrayList<>();
            while (true) {
                Token token = peek();
                if (token.isEof()
                        || isWord(token, "equals")
                        || isWord(token, "show")
                        || peekJoinStart()) {
                    break;
                }
                if (token.type == TokenType.LEFT_PAREN || token.type == TokenType.RIGHT_PAREN || token.type == TokenType.COMMA) {
                    throw error("Unsupported token '" + token.text + "' in JOIN clause", token.position);
                }
                reference.add(next());
            }
            return List.copyOf(reference);
        }

        private ResolvedJoinFields resolveJoinFields(List<String> availableSources,
                                                     String childSource,
                                                     JoinReference left,
                                                     JoinReference right) {
            boolean leftIsChild = matchesSource(left.sourceName(), childSource);
            boolean rightIsChild = matchesSource(right.sourceName(), childSource);
            boolean leftIsParent = matchesAnySource(left.sourceName(), availableSources);
            boolean rightIsParent = matchesAnySource(right.sourceName(), availableSources);

            if (leftIsChild && rightIsChild) {
                throw error("JOIN ON fields must reference the existing plan and the new child source", peek().position);
            }
            if (leftIsChild) {
                return new ResolvedJoinFields(right.reference(), left.reference());
            }
            if (rightIsChild) {
                return new ResolvedJoinFields(left.reference(), right.reference());
            }
            if (leftIsParent && !rightIsParent) {
                return new ResolvedJoinFields(left.reference(), right.reference());
            }
            if (rightIsParent && !leftIsParent) {
                return new ResolvedJoinFields(right.reference(), left.reference());
            }
            return new ResolvedJoinFields(left.reference(), right.reference());
        }

        private boolean matchesSource(String sourceName, String expected) {
            return sourceName != null && expected.equalsIgnoreCase(sourceName);
        }

        private boolean matchesAnySource(String sourceName, List<String> availableSources) {
            if (sourceName == null) {
                return false;
            }
            for (String availableSource : availableSources) {
                if (availableSource.equalsIgnoreCase(sourceName)) {
                    return true;
                }
            }
            return false;
        }

        private String normalizeTrackedReference(List<Token> fieldTokens) {
            List<Token> normalizedTokens = stripLeadingReferenceFillers(fieldTokens);
            QualifiedReference qualified = tryParseQualifiedReference(normalizedTokens);
            String normalized = qualified != null
                    ? qualified.reference()
                    : normalizeFieldReference(normalizedTokens);
            rememberSourceFieldPhrase(fieldTokens, normalized);
            return normalized;
        }

        private QualifiedReference tryParseQualifiedReference(List<Token> fieldTokens) {
            List<Token> normalizedTokens = stripLeadingReferenceFillers(fieldTokens);
            if (normalizedTokens.isEmpty()) {
                return null;
            }
            if (normalizedTokens.size() == 1 && normalizedTokens.get(0).type == TokenType.RAW) {
                String raw = normalizedTokens.get(0).text;
                int separator = raw.indexOf('.');
                if (separator > 0 && separator + 1 < raw.length()) {
                    String sourceAlias = raw.substring(0, separator);
                    String sourceName = resolveKnownSourceAlias(sourceAlias);
                    if (sourceName == null) {
                        return null;
                    }
                    return new QualifiedReference(
                            sourceName,
                            sourceName + "." + NaturalVocabularySupport.normalizeNaturalFieldToken(raw.substring(separator + 1))
                    );
                }
            }
            if (normalizedTokens.get(0).type != TokenType.RAW || normalizedTokens.size() < 2) {
                return null;
            }
            String sourceName = resolveKnownSourceAlias(normalizedTokens.get(0).text);
            if (sourceName == null) {
                return null;
            }
            return new QualifiedReference(
                    sourceName,
                    sourceName + "." + normalizeFieldReference(normalizedTokens.subList(1, normalizedTokens.size()))
            );
        }

        private Metric metricFromWord(Token token) {
            if (token.type != TokenType.RAW) {
                return null;
            }
            String word = token.text.toLowerCase(Locale.ROOT);
            return switch (word) {
                case "count" -> Metric.COUNT;
                case "sum" -> Metric.SUM;
                case "average", "avg" -> Metric.AVG;
                case "minimum", "min" -> Metric.MIN;
                case "maximum", "max" -> Metric.MAX;
                default -> null;
            };
        }

        private String renderMetricReference(MetricPhrase metricPhrase) {
            return metricPhrase.metric().name().toLowerCase(Locale.ROOT)
                    + "("
                    + (metricPhrase.countAll() ? "*" : metricPhrase.field())
                    + ")";
        }
    }

    private static List<FilterAst> flatten(FilterExpressionAst expression) {
        if (expression == null) {
            return List.of();
        }
        ArrayList<FilterAst> filters = new ArrayList<>();
        flattenInto(expression, null, filters);
        return List.copyOf(filters);
    }

    private static void flattenInto(FilterExpressionAst expression,
                                    Separator separator,
                                    List<FilterAst> filters) {
        if (expression instanceof FilterPredicateAst predicateAst) {
            FilterAst filter = predicateAst.filter();
            filters.add(new FilterAst(
                    filter.field(),
                    filter.clause(),
                    filter.value(),
                    filters.isEmpty() ? null : separator
            ));
            return;
        }
        FilterBinaryAst binaryAst = (FilterBinaryAst) expression;
        flattenInto(binaryAst.left(), separator, filters);
        flattenInto(binaryAst.right(), binaryAst.operator(), filters);
    }

    private static int lastIndexOfWord(List<Token> tokens, String value) {
        for (int i = tokens.size() - 1; i >= 0; i--) {
            if (isWord(tokens.get(i), value)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfWord(List<Token> tokens, String value) {
        for (int i = 0; i < tokens.size(); i++) {
            if (isWord(tokens.get(i), value)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean endsWithWord(List<Token> tokens, String value) {
        return !tokens.isEmpty() && isWord(tokens.get(tokens.size() - 1), value);
    }

    private static boolean isOptionalReferenceFiller(Token token) {
        return token.type == TokenType.RAW
                && OPTIONAL_REFERENCE_FILLERS.contains(token.text.toLowerCase(Locale.ROOT));
    }

    private static boolean isWord(Token token, String expected) {
        return token.type == TokenType.RAW && token.text.equalsIgnoreCase(expected);
    }

    private static ChartType chartTypeFromWord(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "bar" -> ChartType.BAR;
            case "line" -> ChartType.LINE;
            case "area" -> ChartType.AREA;
            case "pie" -> ChartType.PIE;
            case "scatter" -> ChartType.SCATTER;
            default -> null;
        };
    }

    private static String normalizeFieldReference(List<Token> tokens) {
        List<Token> normalizedTokens = stripLeadingReferenceFillers(tokens);
        if (normalizedTokens.isEmpty()) {
            throw new IllegalArgumentException("Field reference must not be blank");
        }
        return NaturalVocabularySupport.normalizeNaturalFieldToken(joinTokens(normalizedTokens));
    }

    private static String normalizeAlias(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("Alias must not be blank");
        }
        return NaturalVocabularySupport.normalizeNaturalFieldToken(joinTokens(tokens));
    }

    private static Object parseValue(List<Token> tokens, Operator operator) {
        Objects.requireNonNull(operator, "operator must not be null");
        if (tokens.size() == 1) {
            Token token = tokens.get(0);
            if (token.type == TokenType.STRING) {
                return operator.transform(token.text);
            }
            String raw = token.text;
            if (raw.startsWith(":")) {
                String parameterName = raw.substring(1);
                if (parameterName.isEmpty()) {
                    throw new IllegalArgumentException("Expected parameter name after ':'");
                }
                return new ParameterValueAst(parameterName);
            }
            if ("true".equalsIgnoreCase(raw)) {
                return operator.transform(Boolean.TRUE);
            }
            if ("false".equalsIgnoreCase(raw)) {
                return operator.transform(Boolean.FALSE);
            }
            if ("null".equalsIgnoreCase(raw)) {
                return operator.transform(null);
            }
            Number number = tryParseNumber(raw);
            if (number != null) {
                return operator.transform(number);
            }
            return operator.transform(raw);
        }
        return operator.transform(joinTokens(tokens));
    }

    private static List<Token> stripLeadingReferenceFillers(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        int start = 0;
        while (start < tokens.size() && isOptionalReferenceFiller(tokens.get(start))) {
            start++;
        }
        return start == 0 ? tokens : List.copyOf(tokens.subList(start, tokens.size()));
    }

    private static Number tryParseNumber(String raw) {
        if (StringUtil.isNullOrBlank(raw)) {
            return null;
        }
        try {
            if (raw.contains(".")) {
                return Double.parseDouble(raw);
            }
            long parsed = Long.parseLong(raw);
            if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) {
                return (int) parsed;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String joinTokens(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Token token : tokens) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(token.text);
        }
        return sb.toString().trim();
    }

    private static List<Token> tokenize(String input) {
        ArrayList<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < input.length()) {
            char current = input.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == ',') {
                tokens.add(new Token(TokenType.COMMA, ",", index));
                index++;
                continue;
            }
            if (current == '*') {
                tokens.add(new Token(TokenType.STAR, "*", index));
                index++;
                continue;
            }
            if (current == '(') {
                tokens.add(new Token(TokenType.LEFT_PAREN, "(", index));
                index++;
                continue;
            }
            if (current == ')') {
                tokens.add(new Token(TokenType.RIGHT_PAREN, ")", index));
                index++;
                continue;
            }
            if (current == '\'' || current == '"') {
                char quote = current;
                int start = index;
                index++;
                StringBuilder value = new StringBuilder();
                boolean closed = false;
                while (index < input.length()) {
                    char next = input.charAt(index);
                    if (next == quote) {
                        if (index + 1 < input.length() && input.charAt(index + 1) == quote) {
                            value.append(quote);
                            index += 2;
                            continue;
                        }
                        index++;
                        closed = true;
                        break;
                    }
                    value.append(next);
                    index++;
                }
                if (!closed) {
                    throw new IllegalArgumentException("Natural query parse error: Unterminated string literal");
                }
                tokens.add(new Token(TokenType.STRING, value.toString(), start));
                continue;
            }

            int start = index;
            while (index < input.length()) {
                char next = input.charAt(index);
                if (Character.isWhitespace(next) || next == ',' || next == '(' || next == ')' || next == '*') {
                    break;
                }
                index++;
            }
            tokens.add(new Token(TokenType.RAW, input.substring(start, index), start));
        }
        tokens.add(new Token(TokenType.EOF, "", input.length()));
        return List.copyOf(tokens);
    }

    private enum TokenType {
        RAW,
        STRING,
        COMMA,
        STAR,
        LEFT_PAREN,
        RIGHT_PAREN,
        EOF
    }

    private record Token(TokenType type, String text, int position) {
        private boolean isEof() {
            return type == TokenType.EOF;
        }
    }

    private record OperatorMatch(Operator operator, int startIndex, int endIndex) {
    }

    private enum Operator {
        IS_NOT(new String[]{"is", "not"}, Clauses.NOT_EQUAL),
        IS_AT_LEAST(new String[]{"is", "at", "least"}, Clauses.BIGGER_EQUAL),
        IS_AT_MOST(new String[]{"is", "at", "most"}, Clauses.SMALLER_EQUAL),
        IS_MORE_THAN(new String[]{"is", "more", "than"}, Clauses.BIGGER),
        IS_LESS_THAN(new String[]{"is", "less", "than"}, Clauses.SMALLER),
        IS_ABOVE(new String[]{"is", "above"}, Clauses.BIGGER),
        IS_BELOW(new String[]{"is", "below"}, Clauses.SMALLER),
        IS_BEFORE(new String[]{"is", "before"}, Clauses.SMALLER),
        IS_AFTER(new String[]{"is", "after"}, Clauses.BIGGER),
        STARTS_WITH(new String[]{"starts", "with"}, Clauses.MATCHES) {
            @Override
            Object transform(Object value) {
                return toRegex(value, true, false);
            }
        },
        ENDS_WITH(new String[]{"ends", "with"}, Clauses.MATCHES) {
            @Override
            Object transform(Object value) {
                return toRegex(value, false, true);
            }
        },
        AT_LEAST(new String[]{"at", "least"}, Clauses.BIGGER_EQUAL),
        AT_MOST(new String[]{"at", "most"}, Clauses.SMALLER_EQUAL),
        MORE_THAN(new String[]{"more", "than"}, Clauses.BIGGER),
        LESS_THAN(new String[]{"less", "than"}, Clauses.SMALLER),
        ABOVE(new String[]{"above"}, Clauses.BIGGER),
        BELOW(new String[]{"below"}, Clauses.SMALLER),
        BEFORE(new String[]{"before"}, Clauses.SMALLER),
        AFTER(new String[]{"after"}, Clauses.BIGGER),
        CONTAINS(new String[]{"contains"}, Clauses.CONTAINS),
        IS(new String[]{"is"}, Clauses.EQUAL);

        private final String[] phrase;
        private final Clauses clause;

        Operator(String[] phrase, Clauses clause) {
            this.phrase = phrase;
            this.clause = clause;
        }

        Object transform(Object value) {
            return value;
        }

        private static String toRegex(Object value, boolean prefix, boolean suffix) {
            String raw = value == null ? "" : String.valueOf(value);
            StringBuilder regex = new StringBuilder();
            regex.append(prefix ? "^" : ".*");
            regex.append(Pattern.quote(raw));
            regex.append(suffix ? "$" : ".*");
            return regex.toString();
        }
    }

    private record PaginationValue(Integer literal, String parameterName) {
        private static PaginationValue literal(int value) {
            return new PaginationValue(value, null);
        }

        private static PaginationValue parameter(String value) {
            return new PaginationValue(null, value);
        }
    }

    private record MetricPhrase(Metric metric, boolean countAll, String field) {
    }

    private record TimeBucketPhrase(String field, TimeBucketPreset preset) {
    }

    private record WindowPhrase(String function,
                                String valueField,
                                boolean countAll,
                                List<String> partitionFields,
                                List<OrderAst> orderFields) {
    }

    private record WindowFunctionStart(String function,
                                       String valueField,
                                       boolean countAll,
                                       int nextIndex) {
    }

    private record JoinReference(String reference, String sourceName) {
    }

    private record QualifiedReference(String sourceName, String reference) {
    }

    private record ResolvedJoinFields(String parentField, String childField) {
    }
}
