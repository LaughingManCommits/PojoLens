package laughing.man.commits.sqllike.parser;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.JoinAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.ParameterValueAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.time.TimeBucketPreset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SqlLikeParser {

    private static final int MAX_QUERY_LENGTH = 4000;
    private static final int MAX_TOKENS = 512;
    private static final int MAX_FILTER_PREDICATES = 100;
    private static final int MAX_ORDER_FIELDS = 20;
    private static final int MAX_SELECT_FIELDS = 100;
    private static final int MAX_GROUP_FIELDS = 20;
    private static final Set<String> KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "ORDER", "BY", "LIMIT", "ASC", "DESC",
            "AND", "OR", "TRUE", "FALSE", "NULL", "CONTAINS", "MATCHES", "IN", "AS",
            "GROUP", "HAVING", "LEFT", "RIGHT", "INNER", "ON", "JOIN",
            "COUNT", "SUM", "AVG", "MIN", "MAX", "BUCKET"
    );
    private static final Map<String, Metric> METRIC_BY_KEYWORD = buildMetricKeywordMap();

    private final String input;
    private final PositionMap positionMap;
    private final List<Token> tokens;
    private int index;
    private int clausePredicateCount;
    private String activeClause = "QUERY";
    private int activeClauseStart = 0;
    private int activeClauseEnd = 0;

    private SqlLikeParser(String input) {
        this.input = input;
        this.positionMap = PositionMap.build(input);
        this.tokens = tokenize(input);
        this.index = 0;
    }

    public static QueryAst parse(String input) {
        if (input.length() > MAX_QUERY_LENGTH) {
            PositionMap positionMap = PositionMap.build(input);
            throw new SqlLikeParseException(
                    SqlLikeErrorCodes.PARSE_QUERY_LENGTH,
                    "Query exceeds maximum length of " + MAX_QUERY_LENGTH,
                    MAX_QUERY_LENGTH,
                    positionMap.lineOf(MAX_QUERY_LENGTH),
                    positionMap.columnOf(MAX_QUERY_LENGTH)
            );
        }
        return new SqlLikeParser(input).parseQuery();
    }

    private QueryAst parseQuery() {
        SelectAst select = null;
        List<JoinAst> joins = new ArrayList<>();
        List<FilterAst> filters = new ArrayList<>();
        FilterExpressionAst whereExpression = null;
        List<String> groupByFields = new ArrayList<>();
        List<FilterAst> havingFilters = new ArrayList<>();
        FilterExpressionAst havingExpression = null;
        List<OrderAst> orders = new ArrayList<>();
        Integer limit = null;

        if (matchKeyword("SELECT")) {
            enterClause("SELECT", tokens.get(index - 1).position);
            select = parseSelect();
            exitClause();
        }
        if (peekJoinStart()) {
            enterClause("JOIN", peek().position);
            joins = parseJoins(select);
            exitClause();
        }
        if (matchKeyword("WHERE")) {
            enterClause("WHERE", tokens.get(index - 1).position);
            whereExpression = parseBooleanExpression(false, "WHERE");
            filters = flattenExpression(whereExpression);
            exitClause();
        }
        if (matchKeyword("GROUP")) {
            enterClause("GROUP BY", tokens.get(index - 1).position);
            expectKeyword("BY");
            groupByFields = parseGroupBy();
            exitClause();
        }
        if (matchKeyword("HAVING")) {
            enterClause("HAVING", tokens.get(index - 1).position);
            havingExpression = parseBooleanExpression(true, "HAVING");
            havingFilters = flattenExpression(havingExpression);
            exitClause();
        }
        if (matchKeyword("ORDER")) {
            enterClause("ORDER BY", tokens.get(index - 1).position);
            expectKeyword("BY");
            orders = parseOrderBy();
            exitClause();
        }
        if (matchKeyword("LIMIT")) {
            enterClause("LIMIT", tokens.get(index - 1).position);
            limit = parseLimit();
            exitClause();
        }

        Token extra = peek();
        if (extra.type != TokenType.EOF) {
            enterClause("QUERY", extra.position);
            if (isKeyword(extra, "JOIN")) {
                throw error("JOIN must appear after FROM and before WHERE/ORDER/LIMIT", extra.position);
            }
            throw error("Unexpected token '" + extra.text + "'", extra.position);
        }
        validateSelectOutputNames(select);
        return new QueryAst(select, joins, filters, whereExpression, groupByFields, havingFilters, havingExpression, orders, limit);
    }

    private boolean peekJoinStart() {
        Token token = peek();
        return isKeyword(token, "JOIN")
                || isKeyword(token, "LEFT")
                || isKeyword(token, "RIGHT")
                || isKeyword(token, "INNER");
    }

    private List<JoinAst> parseJoins(SelectAst select) {
        ArrayList<JoinAst> joins = new ArrayList<>();
        LinkedHashSet<String> availableSources = new LinkedHashSet<>();
        availableSources.add(select.sourceName());
        while (peekJoinStart()) {
            JoinAst join = parseJoin(select, availableSources);
            joins.add(join);
            availableSources.add(join.childSource());
        }
        return joins;
    }

    private JoinAst parseJoin(SelectAst select, Set<String> availableSources) {
        if (select == null || select.sourceName() == null) {
            throw error("JOIN requires SELECT ... FROM <source>", peek().position);
        }

        Join joinType = Join.INNER_JOIN;
        if (matchKeyword("LEFT")) {
            joinType = Join.LEFT_JOIN;
        } else if (matchKeyword("RIGHT")) {
            joinType = Join.RIGHT_JOIN;
        } else if (matchKeyword("INNER")) {
            joinType = Join.INNER_JOIN;
        }

        expectKeyword("JOIN");
        String childSource = expectIdentifier("Expected child source after JOIN");
        if (availableSources.contains(childSource)) {
            throw error("JOIN source '" + childSource + "' is already used", peek().position);
        }
        expectKeyword("ON");
        String left = expectIdentifier("Expected left field expression after ON");
        expectOperator("=");
        String right = expectIdentifier("Expected right field expression after '='");

        ResolvedJoinFields resolved = resolveJoinFields(availableSources, childSource, left, right);
        return new JoinAst(joinType, childSource, resolved.parentField, resolved.childField);
    }

    private void expectOperator(String operator) {
        Token token = peek();
        if (token.type != TokenType.OPERATOR || !operator.equals(token.text)) {
            throw error("Expected operator '" + operator + "'", token.position);
        }
        next();
    }

    private ResolvedJoinFields resolveJoinFields(Set<String> availableSources, String childSource, String left, String right) {
        QualifiedField lq = qualify(left, availableSources, childSource);
        QualifiedField rq = qualify(right, availableSources, childSource);

        boolean leftIsChild = matchesSource(lq.source, childSource);
        boolean rightIsChild = matchesSource(rq.source, childSource);
        boolean leftIsParent = matchesAnySource(lq.source, availableSources);
        boolean rightIsParent = matchesAnySource(rq.source, availableSources);

        if (leftIsChild && rightIsChild) {
            throw error("JOIN ON fields must reference the existing plan and the new child source", peek().position);
        }
        if (leftIsChild) {
            return new ResolvedJoinFields(right, lq.field);
        }
        if (rightIsChild) {
            return new ResolvedJoinFields(left, rq.field);
        }

        if (leftIsParent && !rightIsParent) {
            return new ResolvedJoinFields(left, right);
        }
        if (rightIsParent && !leftIsParent) {
            return new ResolvedJoinFields(right, left);
        }

        if (lq.source == null && rq.source == null) {
            return new ResolvedJoinFields(lq.field, rq.field);
        }

        throw error("JOIN ON fields must reference the current plan and the new child source", peek().position);
    }

    private boolean matchesSource(String source, String expected) {
        return source != null && expected.equalsIgnoreCase(source);
    }

    private boolean matchesAnySource(String source, Set<String> availableSources) {
        if (source == null) {
            return false;
        }
        for (String availableSource : availableSources) {
            if (availableSource.equalsIgnoreCase(source)) {
                return true;
            }
        }
        return false;
    }

    private QualifiedField qualify(String expression, Set<String> availableSources, String childSource) {
        int index = expression.indexOf('.');
        if (index < 0) {
            return new QualifiedField(null, expression);
        }
        String source = expression.substring(0, index);
        String field = expression.substring(index + 1);
        if (source.isEmpty() || field.isEmpty()) {
            throw error("Invalid qualified field '" + expression + "'", peek().position);
        }
        if (!matchesSource(source, childSource) && !matchesAnySource(source, availableSources)) {
            return new QualifiedField(null, expression);
        }
        return new QualifiedField(source, field);
    }

    private SelectAst parseSelect() {
        boolean wildcard = false;
        List<SelectFieldAst> fields = new ArrayList<>();
        String sourceName = null;

        if (match(TokenType.STAR)) {
            wildcard = true;
        } else {
            fields.add(parseSelectField());
            while (match(TokenType.COMMA)) {
                if (fields.size() >= MAX_SELECT_FIELDS) {
                    throw error(SqlLikeErrorCodes.PARSE_CLAUSE_LIMIT,
                            "Too many SELECT fields (max " + MAX_SELECT_FIELDS + ")",
                            peek().position);
                }
                fields.add(parseSelectField());
            }
        }

        if (matchKeyword("FROM")) {
            sourceName = expectIdentifier("Expected source name after FROM");
        }
        return new SelectAst(wildcard, fields, sourceName);
    }

    private SelectFieldAst parseSelectField() {
        Token token = peek();
        String field;
        Metric metric = null;
        boolean countAll = false;
        TimeBucketPreset timeBucketPreset = null;
        if (token.type == TokenType.KEYWORD && isMetricKeyword(token.text)) {
            metric = parseMetricKeyword(token);
            next();
            expect(TokenType.LEFT_PAREN, "Expected '(' after aggregate function");
            if (metric == Metric.COUNT && match(TokenType.STAR)) {
                field = "*";
                countAll = true;
            } else {
                field = expectIdentifier("Expected field inside aggregate function");
            }
            expect(TokenType.RIGHT_PAREN, "Expected ')' after aggregate function argument");
        } else if (token.type == TokenType.KEYWORD && "BUCKET".equalsIgnoreCase(token.text)) {
            next();
            expect(TokenType.LEFT_PAREN, "Expected '(' after bucket");
            field = expectIdentifier("Expected date field inside bucket");
            expect(TokenType.COMMA, "Expected ',' in bucket(dateField,'granularity')");
            Token bucketToken = peek();
            if (bucketToken.type != TokenType.STRING) {
                throw error("Expected string time bucket in bucket(dateField,'granularity')", bucketToken.position);
            }
            next();
            Token zoneToken = null;
            Token weekStartToken = null;
            String zoneValue = null;
            String weekStartValue = null;
            if (match(TokenType.COMMA)) {
                zoneToken = peek();
                if (zoneToken.type != TokenType.STRING) {
                    throw error("Expected string time zone in bucket(dateField,'granularity','zone')", zoneToken.position);
                }
                zoneValue = zoneToken.text;
                next();
                if (match(TokenType.COMMA)) {
                    weekStartToken = peek();
                    if (weekStartToken.type != TokenType.STRING) {
                        throw error("Expected string week start in bucket(dateField,'granularity','zone','weekStart')", weekStartToken.position);
                    }
                    weekStartValue = weekStartToken.text;
                    next();
                }
            }
            try {
                timeBucketPreset = TimeBucketPreset.parse(bucketToken.text, zoneValue, weekStartValue);
            } catch (IllegalArgumentException ex) {
                int position = weekStartToken != null
                        ? weekStartToken.position
                        : zoneToken != null ? zoneToken.position : bucketToken.position;
                throw error(ex.getMessage(), position);
            }
            expect(TokenType.RIGHT_PAREN, "Expected ')' after bucket arguments");
        } else {
            ParsedReference parsed = parseReferenceUntilSelectBoundary("SELECT");
            field = parsed.text;
            String alias = null;
            if (matchKeyword("AS")) {
                alias = expectIdentifier("Expected alias after AS");
            } else if (parsed.computed) {
                throw error("Computed SELECT expressions require AS alias", peek().position);
            }
            return parseSelectFieldWithAlias(field, alias, null, false, null, parsed.computed);
        }
        String alias = null;
        if (matchKeyword("AS")) {
            alias = expectIdentifier("Expected alias after AS");
        }
        return parseSelectFieldWithAlias(field, alias, metric, countAll, timeBucketPreset, false);
    }

    private SelectFieldAst parseSelectFieldWithAlias(String field,
                                                     String alias,
                                                     Metric metric,
                                                     boolean countAll,
                                                     TimeBucketPreset timeBucketPreset,
                                                     boolean computed) {
        return new SelectFieldAst(field, alias, metric, countAll, timeBucketPreset, computed);
    }

    private List<String> parseGroupBy() {
        List<String> fields = new ArrayList<>();
        fields.add(expectIdentifier("Expected field in GROUP BY"));
        while (match(TokenType.COMMA)) {
            if (fields.size() >= MAX_GROUP_FIELDS) {
                throw error(SqlLikeErrorCodes.PARSE_CLAUSE_LIMIT,
                        "Too many GROUP BY fields (max " + MAX_GROUP_FIELDS + ")",
                        peek().position);
            }
            fields.add(expectIdentifier("Expected field in GROUP BY"));
        }
        return fields;
    }

    private FilterExpressionAst parseBooleanExpression(boolean allowAggregateReference, String clauseName) {
        clausePredicateCount = 0;
        return parseOrExpression(allowAggregateReference, clauseName);
    }

    private FilterExpressionAst parseOrExpression(boolean allowAggregateReference, String clauseName) {
        FilterExpressionAst expression = parseAndExpression(allowAggregateReference, clauseName);
        while (matchKeyword("OR")) {
            FilterExpressionAst right = parseAndExpression(allowAggregateReference, clauseName);
            expression = new FilterBinaryAst(expression, right, Separator.OR);
        }
        return expression;
    }

    private FilterExpressionAst parseAndExpression(boolean allowAggregateReference, String clauseName) {
        FilterExpressionAst expression = parsePrimaryExpression(allowAggregateReference, clauseName);
        while (matchKeyword("AND")) {
            FilterExpressionAst right = parsePrimaryExpression(allowAggregateReference, clauseName);
            expression = new FilterBinaryAst(expression, right, Separator.AND);
        }
        return expression;
    }

    private FilterExpressionAst parsePrimaryExpression(boolean allowAggregateReference, String clauseName) {
        if (match(TokenType.LEFT_PAREN)) {
            FilterExpressionAst grouped = parseOrExpression(allowAggregateReference, clauseName);
            expect(TokenType.RIGHT_PAREN, "Expected ')' to close " + clauseName + " expression");
            return grouped;
        }
        return new FilterPredicateAst(parseCondition(allowAggregateReference, clauseName));
    }

    private FilterAst parseCondition(boolean allowAggregateReference, String clauseName) {
        if (clausePredicateCount >= MAX_FILTER_PREDICATES) {
            throw error(SqlLikeErrorCodes.PARSE_CLAUSE_LIMIT,
                    "Too many " + clauseName + " predicates (max " + MAX_FILTER_PREDICATES + ")",
                    peek().position);
        }
        clausePredicateCount++;
        String field = allowAggregateReference
                ? parseConditionReferenceInHaving(clauseName)
                : parseConditionReference(clauseName);
        Clauses clause = parseClause();
        Object value = parseValue(clause, clauseName);
        return new FilterAst(field, clause, value, null);
    }

    private String parseConditionReference(String clauseName) {
        ParsedReference reference = parseReferenceUntilComparison(clauseName);
        return reference.text;
    }

    private String parseConditionReferenceInHaving(String clauseName) {
        int start = index;
        if (peek().type == TokenType.KEYWORD && isMetricKeyword(peek().text)) {
            try {
                String aggregate = parseHavingReference();
                if (isComparisonOperatorToken(peek())) {
                    return aggregate;
                }
                index = start;
            } catch (IllegalArgumentException ex) {
                index = start;
            }
        }
        return parseConditionReference(clauseName);
    }

    private List<FilterAst> flattenExpression(FilterExpressionAst expression) {
        List<FilterAst> filters = new ArrayList<>();
        appendFlattened(expression, null, filters);
        return filters;
    }

    private void appendFlattened(FilterExpressionAst expression, Separator separator, List<FilterAst> out) {
        if (expression instanceof FilterPredicateAst) {
            FilterAst filter = ((FilterPredicateAst) expression).filter();
            out.add(new FilterAst(filter.field(), filter.clause(), filter.value(), separator));
            return;
        }
        FilterBinaryAst binary = (FilterBinaryAst) expression;
        appendFlattened(binary.left(), separator, out);
        appendFlattened(binary.right(), binary.operator(), out);
    }

    private String parseHavingReference() {
        Token token = peek();
        if (token.type == TokenType.KEYWORD && isMetricKeyword(token.text)) {
            Metric metric = parseMetricKeyword(token);
            next();
            expect(TokenType.LEFT_PAREN, "Expected '(' after aggregate function");
            String field;
            if (metric == Metric.COUNT && match(TokenType.STAR)) {
                field = "*";
            } else {
                field = expectIdentifier("Expected field inside aggregate function");
            }
            expect(TokenType.RIGHT_PAREN, "Expected ')' after aggregate function argument");
            return metric.name().toLowerCase(Locale.ROOT) + "(" + field + ")";
        }
        return expectIdentifier("Expected field or aggregate expression in HAVING clause");
    }

    private ParsedReference parseReferenceUntilComparison(String clauseName) {
        int start = index;
        int depth = 0;
        while (true) {
            Token token = peek();
            if (token.type == TokenType.EOF) {
                break;
            }
            if (depth == 0 && isComparisonOperatorToken(token)) {
                break;
            }
            if (token.type == TokenType.LEFT_PAREN) {
                depth++;
            } else if (token.type == TokenType.RIGHT_PAREN) {
                if (depth == 0) {
                    break;
                }
                depth--;
            }
            next();
        }
        if (start == index) {
            throw error("Expected field/expression in " + clauseName + " clause", peek().position);
        }
        return new ParsedReference(buildExpressionText(start, index), !isSimpleIdentifierSpan(start, index));
    }

    private ParsedReference parseReferenceUntilSelectBoundary(String clauseName) {
        int start = index;
        int depth = 0;
        while (true) {
            Token token = peek();
            if (token.type == TokenType.EOF) {
                break;
            }
            if (depth == 0) {
                if (token.type == TokenType.COMMA || isKeyword(token, "AS") || isClauseBoundaryKeyword(token.text)) {
                    break;
                }
            }
            if (token.type == TokenType.LEFT_PAREN) {
                depth++;
            } else if (token.type == TokenType.RIGHT_PAREN) {
                if (depth == 0) {
                    break;
                }
                depth--;
            }
            next();
        }
        if (start == index) {
            throw error("Expected field/expression in " + clauseName + " clause", peek().position);
        }
        return new ParsedReference(buildExpressionText(start, index), !isSimpleIdentifierSpan(start, index));
    }

    private boolean isSimpleIdentifierSpan(int start, int end) {
        return end - start == 1 && tokens.get(start).type == TokenType.IDENTIFIER;
    }

    private String buildExpressionText(int start, int end) {
        StringBuilder sb = new StringBuilder();
        TokenType previousType = null;
        for (int i = start; i < end; i++) {
            Token token = tokens.get(i);
            if (sb.length() > 0 && shouldAddSpace(previousType, token.type)) {
                sb.append(' ');
            }
            if (token.type == TokenType.STRING) {
                sb.append('\'').append(token.text.replace("'", "''")).append('\'');
            } else {
                sb.append(token.text);
            }
            previousType = token.type;
        }
        return sb.toString();
    }

    private boolean shouldAddSpace(TokenType previous, TokenType current) {
        if (previous == null) {
            return false;
        }
        return (previous == TokenType.IDENTIFIER
                || previous == TokenType.KEYWORD
                || previous == TokenType.NUMBER
                || previous == TokenType.STRING
                || previous == TokenType.PARAM)
                && (current == TokenType.IDENTIFIER
                || current == TokenType.KEYWORD
                || current == TokenType.NUMBER
                || current == TokenType.STRING
                || current == TokenType.PARAM);
    }

    private boolean isComparisonOperatorToken(Token token) {
        if (token.type == TokenType.OPERATOR) {
            return "=".equals(token.text)
                    || "!=".equals(token.text)
                    || "<>".equals(token.text)
                    || ">".equals(token.text)
                    || ">=".equals(token.text)
                    || "<".equals(token.text)
                    || "<=".equals(token.text);
        }
        if (token.type == TokenType.KEYWORD) {
            return "CONTAINS".equalsIgnoreCase(token.text)
                    || "MATCHES".equalsIgnoreCase(token.text)
                    || "IN".equalsIgnoreCase(token.text);
        }
        return false;
    }

    private boolean isClauseBoundaryKeyword(String value) {
        return "FROM".equalsIgnoreCase(value)
                || "WHERE".equalsIgnoreCase(value)
                || "GROUP".equalsIgnoreCase(value)
                || "HAVING".equalsIgnoreCase(value)
                || "ORDER".equalsIgnoreCase(value)
                || "LIMIT".equalsIgnoreCase(value);
    }

    private Clauses parseClause() {
        Token token = peek();
        if (token.type == TokenType.OPERATOR) {
            next();
            if ("=".equals(token.text)) {
                return Clauses.EQUAL;
            }
            if ("!=".equals(token.text) || "<>".equals(token.text)) {
                return Clauses.NOT_EQUAL;
            }
            if (">".equals(token.text)) {
                return Clauses.BIGGER;
            }
            if (">=".equals(token.text)) {
                return Clauses.BIGGER_EQUAL;
            }
            if ("<".equals(token.text)) {
                return Clauses.SMALLER;
            }
            if ("<=".equals(token.text)) {
                return Clauses.SMALLER_EQUAL;
            }
        } else if (token.type == TokenType.KEYWORD) {
            String keyword = token.text.toUpperCase(Locale.ROOT);
            if ("CONTAINS".equals(keyword)) {
                next();
                return Clauses.CONTAINS;
            }
            if ("MATCHES".equals(keyword)) {
                next();
                return Clauses.MATCHES;
            }
            if ("IN".equals(keyword)) {
                next();
                return Clauses.IN;
            }
        }
        throw error("Expected comparison operator", token.position);
    }

    private Object parseValue(Clauses clause, String clauseName) {
        if (Clauses.IN.equals(clause)) {
            return parseInValue(clauseName);
        }
        Token token = peek();
        if (token.type == TokenType.STRING) {
            next();
            return token.text;
        }
        if (token.type == TokenType.NUMBER) {
            next();
            return parseNumber(token);
        }
        if (token.type == TokenType.KEYWORD) {
            String keyword = token.text.toUpperCase(Locale.ROOT);
            if ("TRUE".equals(keyword)) {
                next();
                return Boolean.TRUE;
            }
            if ("FALSE".equals(keyword)) {
                next();
                return Boolean.FALSE;
            }
            if ("NULL".equals(keyword)) {
                next();
                return null;
            }
        }
        if (token.type == TokenType.IDENTIFIER) {
            next();
            return token.text;
        }
        if (token.type == TokenType.PARAM) {
            next();
            return new ParameterValueAst(token.text.substring(1));
        }
        throw error("Expected value in " + clauseName + " clause", token.position);
    }

    private Object parseInValue(String clauseName) {
        if (!match(TokenType.LEFT_PAREN)) {
            throw error("Expected '(' after IN", peek().position);
        }
        if (!isKeyword(peek(), "SELECT")) {
            throw error("IN currently requires a subquery starting with SELECT", peek().position);
        }

        int start = index;
        int depth = 1;
        while (depth > 0) {
            Token token = peek();
            if (token.type == TokenType.EOF) {
                throw error("Expected ')' to close IN subquery", token.position);
            }
            if (token.type == TokenType.LEFT_PAREN) {
                depth++;
            } else if (token.type == TokenType.RIGHT_PAREN) {
                depth--;
                if (depth == 0) {
                    break;
                }
            }
            next();
        }

        int end = index;
        String subquerySource = buildExpressionText(start, end);
        QueryAst subquery = SqlLikeParser.parse(subquerySource);
        expect(TokenType.RIGHT_PAREN, "Expected ')' to close IN subquery");
        return new SubqueryValueAst(subquerySource, subquery);
    }

    private Number parseNumber(Token token) {
        if (token.text.contains(".")) {
            try {
                return Double.parseDouble(token.text);
            } catch (NumberFormatException e) {
                throw error("Invalid number '" + token.text + "'", token.position);
            }
        }
        try {
            long asLong = Long.parseLong(token.text);
            if (asLong <= Integer.MAX_VALUE && asLong >= Integer.MIN_VALUE) {
                return (int) asLong;
            }
            return asLong;
        } catch (NumberFormatException e) {
            throw error("Invalid number '" + token.text + "'", token.position);
        }
    }

    private List<OrderAst> parseOrderBy() {
        List<OrderAst> orders = new ArrayList<>();
        orders.add(parseOrderItem());
        while (match(TokenType.COMMA)) {
            if (orders.size() >= MAX_ORDER_FIELDS) {
                throw error(SqlLikeErrorCodes.PARSE_CLAUSE_LIMIT,
                        "Too many ORDER BY fields (max " + MAX_ORDER_FIELDS + ")",
                        peek().position);
            }
            orders.add(parseOrderItem());
        }
        return orders;
    }

    private OrderAst parseOrderItem() {
        String field = parseOrderReference();
        Sort sort = Sort.ASC;
        if (matchKeyword("ASC")) {
            sort = Sort.ASC;
        } else if (matchKeyword("DESC")) {
            sort = Sort.DESC;
        }
        return new OrderAst(field, sort);
    }

    private String parseOrderReference() {
        int start = index;
        int depth = 0;
        while (true) {
            Token token = peek();
            if (token.type == TokenType.EOF) {
                break;
            }
            if (depth == 0) {
                if (token.type == TokenType.COMMA
                        || isKeyword(token, "ASC")
                        || isKeyword(token, "DESC")
                        || isClauseBoundaryKeyword(token.text)) {
                    break;
                }
            }
            if (token.type == TokenType.LEFT_PAREN) {
                depth++;
            } else if (token.type == TokenType.RIGHT_PAREN) {
                if (depth == 0) {
                    break;
                }
                depth--;
            }
            next();
        }
        if (start == index) {
            throw error("Expected field or aggregate expression in ORDER BY", peek().position);
        }
        return buildExpressionText(start, index);
    }

    private Integer parseLimit() {
        Token token = peek();
        if (token.type != TokenType.NUMBER) {
            throw error("Expected numeric LIMIT value", token.position);
        }
        next();
        if (token.text.contains(".")) {
            throw error("LIMIT must be an integer", token.position);
        }
        try {
            int limit = Integer.parseInt(token.text);
            if (limit < 0) {
                throw error("LIMIT must be >= 0", token.position);
            }
            return limit;
        } catch (NumberFormatException e) {
            throw error("LIMIT value is too large", token.position);
        }
    }

    private void expect(TokenType type, String message) {
        Token token = peek();
        if (token.type != type) {
            throw error(message, token.position);
        }
        next();
    }

    private String expectIdentifier(String message) {
        Token token = peek();
        if (token.type == TokenType.IDENTIFIER || token.type == TokenType.KEYWORD) {
            next();
            return token.text;
        }
        throw error(message, token.position);
    }

    private boolean matchKeyword(String keyword) {
        Token token = peek();
        if (isKeyword(token, keyword)) {
            next();
            return true;
        }
        return false;
    }

    private void expectKeyword(String keyword) {
        Token token = peek();
        if (!matchKeyword(keyword)) {
            throw error("Expected keyword '" + keyword + "'", token.position);
        }
    }

    private boolean isKeyword(Token token, String keyword) {
        return token.type == TokenType.KEYWORD
                && token.text.equalsIgnoreCase(keyword);
    }

    private boolean match(TokenType type) {
        if (peek().type == type) {
            next();
            return true;
        }
        return false;
    }

    private Token peek() {
        return tokens.get(index);
    }

    private Token next() {
        return tokens.get(index++);
    }

    private SqlLikeParseException error(String message, int position) {
        return error(SqlLikeErrorCodes.PARSE_SYNTAX, message, position);
    }

    private SqlLikeParseException error(String code, String message, int position) {
        int spanEnd = activeClauseEnd > activeClauseStart ? activeClauseEnd : input.length();
        return new SqlLikeParseException(
                code,
                message,
                position,
                positionMap.lineOf(position),
                positionMap.columnOf(position),
                activeClause,
                clamp(activeClauseStart),
                clamp(spanEnd),
                buildSnippet(position)
        );
    }

    private void enterClause(String clause, int startPosition) {
        this.activeClause = clause;
        this.activeClauseStart = clamp(startPosition);
        this.activeClauseEnd = input.length();
    }

    private void exitClause() {
        this.activeClauseEnd = clamp(peek().position);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(value, input.length()));
    }

    private String buildSnippet(int position) {
        int safe = clamp(position);
        int lineStart = safe;
        while (lineStart > 0 && input.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        int lineEnd = safe;
        while (lineEnd < input.length() && input.charAt(lineEnd) != '\n') {
            lineEnd++;
        }
        String line = input.substring(lineStart, lineEnd);
        StringBuilder caret = new StringBuilder();
        for (int i = lineStart; i < safe; i++) {
            caret.append(input.charAt(i) == '\t' ? '\t' : ' ');
        }
        caret.append('^');
        return line + System.lineSeparator() + caret;
    }

    private List<Token> tokenize(String value) {
        List<Token> output = new ArrayList<>();
        int i = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            if (ch == ',') {
                addToken(output, new Token(TokenType.COMMA, ",", i++, positionMap.lineOf(i - 1), positionMap.columnOf(i - 1)));
                continue;
            }
            if (ch == '(') {
                addToken(output, new Token(TokenType.LEFT_PAREN, "(", i++, positionMap.lineOf(i - 1), positionMap.columnOf(i - 1)));
                continue;
            }
            if (ch == ')') {
                addToken(output, new Token(TokenType.RIGHT_PAREN, ")", i++, positionMap.lineOf(i - 1), positionMap.columnOf(i - 1)));
                continue;
            }
            if (ch == '*') {
                addToken(output, new Token(TokenType.STAR, "*", i++, positionMap.lineOf(i - 1), positionMap.columnOf(i - 1)));
                continue;
            }
            if (ch == '\'') {
                int start = i++;
                StringBuilder sb = new StringBuilder();
                boolean terminated = false;
                while (i < value.length()) {
                    char c = value.charAt(i);
                    if (c == '\'') {
                        if (i + 1 < value.length() && value.charAt(i + 1) == '\'') {
                            sb.append('\'');
                            i += 2;
                            continue;
                        }
                        i++;
                        terminated = true;
                        break;
                    }
                    sb.append(c);
                    i++;
                }
                if (!terminated) {
                    throw error("Unterminated string literal", start);
                }
                addToken(output, new Token(TokenType.STRING, sb.toString(), start, positionMap.lineOf(start), positionMap.columnOf(start)));
                continue;
            }
            if (ch == ':') {
                int start = i++;
                if (i >= value.length() || !isIdentifierStart(value.charAt(i))) {
                    throw error("Expected parameter name after ':'", start);
                }
                while (i < value.length() && isIdentifierPart(value.charAt(i))) {
                    i++;
                }
                addToken(output, new Token(
                        TokenType.PARAM,
                        value.substring(start, i),
                        start,
                        positionMap.lineOf(start),
                        positionMap.columnOf(start)
                ));
                continue;
            }
            if (ch == '!' || ch == '<' || ch == '>' || ch == '=' || ch == '+' || ch == '/' || ch == '-') {
                int start = i;
                if (ch == '-' && i + 1 < value.length() && Character.isDigit(value.charAt(i + 1))) {
                    i++;
                    while (i < value.length() && Character.isDigit(value.charAt(i))) {
                        i++;
                    }
                    if (i < value.length() && value.charAt(i) == '.') {
                        i++;
                        if (i >= value.length() || !Character.isDigit(value.charAt(i))) {
                            throw error("Invalid decimal number", i);
                        }
                        while (i < value.length() && Character.isDigit(value.charAt(i))) {
                            i++;
                        }
                    }
                    addToken(output, new Token(TokenType.NUMBER, value.substring(start, i), start, positionMap.lineOf(start), positionMap.columnOf(start)));
                    continue;
                }
                if (i + 1 < value.length()) {
                    String two = value.substring(i, i + 2);
                    if ("!=".equals(two) || ">=".equals(two) || "<=".equals(two) || "<>".equals(two)) {
                        addToken(output, new Token(TokenType.OPERATOR, two, start, positionMap.lineOf(start), positionMap.columnOf(start)));
                        i += 2;
                        continue;
                    }
                }
                addToken(output, new Token(TokenType.OPERATOR, String.valueOf(ch), start, positionMap.lineOf(start), positionMap.columnOf(start)));
                i++;
                continue;
            }
            if (Character.isDigit(ch)) {
                int start = i;
                i++;
                while (i < value.length() && Character.isDigit(value.charAt(i))) {
                    i++;
                }
                if (i < value.length() && value.charAt(i) == '.') {
                    i++;
                    if (i >= value.length() || !Character.isDigit(value.charAt(i))) {
                        throw error("Invalid decimal number", i);
                    }
                    while (i < value.length() && Character.isDigit(value.charAt(i))) {
                        i++;
                    }
                }
                addToken(output, new Token(TokenType.NUMBER, value.substring(start, i), start, positionMap.lineOf(start), positionMap.columnOf(start)));
                continue;
            }
            if (isIdentifierStart(ch)) {
                int start = i++;
                while (i < value.length() && isIdentifierPart(value.charAt(i))) {
                    i++;
                }
                String word = value.substring(start, i);
                if (isKeyword(word)) {
                    addToken(output, new Token(TokenType.KEYWORD, word, start, positionMap.lineOf(start), positionMap.columnOf(start)));
                } else {
                    addToken(output, new Token(TokenType.IDENTIFIER, word, start, positionMap.lineOf(start), positionMap.columnOf(start)));
                }
                continue;
            }
            throw error("Unexpected character '" + ch + "'", i);
        }
        addToken(output, new Token(
                TokenType.EOF,
                "",
                value.length(),
                positionMap.lineOf(value.length()),
                positionMap.columnOf(value.length())
        ));
        return output;
    }

    private void addToken(List<Token> tokens, Token token) {
        if (tokens.size() >= MAX_TOKENS) {
            throw error(SqlLikeErrorCodes.PARSE_TOKEN_LIMIT,
                    "Query exceeds maximum token count of " + MAX_TOKENS,
                    token.position);
        }
        tokens.add(token);
    }

    private boolean isIdentifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    private boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '.';
    }

    private boolean isMetricKeyword(String word) {
        return METRIC_BY_KEYWORD.containsKey(word.toUpperCase(Locale.ROOT));
    }

    private Metric parseMetricKeyword(Token token) {
        Metric metric = METRIC_BY_KEYWORD.get(token.text.toUpperCase(Locale.ROOT));
        if (metric != null) {
            return metric;
        }
        throw error("Unsupported aggregate function '" + token.text + "'", token.position);
    }

    private void validateSelectOutputNames(SelectAst select) {
        if (select == null || select.wildcard()) {
            return;
        }
        Set<String> outputNames = new HashSet<>();
        for (SelectFieldAst field : select.fields()) {
            String output = field.outputName();
            if (!outputNames.add(output)) {
                throw error(SqlLikeErrorCodes.VALIDATION_DUPLICATE_SELECT_OUTPUT,
                        "Duplicate SELECT output name '" + output + "'",
                        peek().position);
            }
        }
    }

    private boolean isKeyword(String word) {
        return KEYWORDS.contains(word.toUpperCase(Locale.ROOT));
    }

    private static Map<String, Metric> buildMetricKeywordMap() {
        Map<String, Metric> metrics = new HashMap<>();
        metrics.put("COUNT", Metric.COUNT);
        metrics.put("SUM", Metric.SUM);
        metrics.put("AVG", Metric.AVG);
        metrics.put("MIN", Metric.MIN);
        metrics.put("MAX", Metric.MAX);
        return metrics;
    }

    private enum TokenType {
        IDENTIFIER,
        KEYWORD,
        STRING,
        NUMBER,
        PARAM,
        OPERATOR,
        COMMA,
        LEFT_PAREN,
        RIGHT_PAREN,
        STAR,
        EOF
    }

    private static final class Token {
        private final TokenType type;
        private final String text;
        private final int position;
        private final int line;
        private final int column;

        private Token(TokenType type, String text, int position, int line, int column) {
            this.type = type;
            this.text = text;
            this.position = position;
            this.line = line;
            this.column = column;
        }
    }

    private static final class PositionMap {
        private final int[] lines;
        private final int[] columns;

        private PositionMap(int[] lines, int[] columns) {
            this.lines = lines;
            this.columns = columns;
        }

        private static PositionMap build(String input) {
            int[] lines = new int[input.length() + 1];
            int[] columns = new int[input.length() + 1];
            int line = 1;
            int column = 1;
            for (int i = 0; i <= input.length(); i++) {
                lines[i] = line;
                columns[i] = column;
                if (i == input.length()) {
                    break;
                }
                char ch = input.charAt(i);
                if (ch == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new PositionMap(lines, columns);
        }

        private int lineOf(int index) {
            int safe = Math.max(0, Math.min(index, lines.length - 1));
            return lines[safe];
        }

        private int columnOf(int index) {
            int safe = Math.max(0, Math.min(index, columns.length - 1));
            return columns[safe];
        }
    }

    private static final class QualifiedField {
        private final String source;
        private final String field;

        private QualifiedField(String source, String field) {
            this.source = source;
            this.field = field;
        }
    }

    private static final class ResolvedJoinFields {
        private final String parentField;
        private final String childField;

        private ResolvedJoinFields(String parentField, String childField) {
            this.parentField = parentField;
            this.childField = childField;
        }
    }

    private static final class ParsedReference {
        private final String text;
        private final boolean computed;

        private ParsedReference(String text, boolean computed) {
            this.text = text;
            this.computed = computed;
        }
    }
}

