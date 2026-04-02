package laughing.man.commits.natural.parser;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.FilterBinaryAst;
import laughing.man.commits.sqllike.ast.FilterExpressionAst;
import laughing.man.commits.sqllike.ast.FilterPredicateAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.ParameterValueAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.util.StringUtil;

import java.util.ArrayList;
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

    private NaturalQueryParser() {
    }

    public static QueryAst parse(String input) {
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
        private int index;

        private Parser(String input) {
            this.input = input;
            this.tokens = tokenize(input);
        }

        private QueryAst parseQuery() {
            expectWord("show");
            SelectAst select = parseSelect();
            FilterExpressionAst whereExpression = null;
            List<FilterAst> filters = List.of();
            List<OrderAst> orders = List.of();
            Integer limit = null;
            String limitParameter = null;
            Integer offset = null;
            String offsetParameter = null;

            if (matchWord("where")) {
                whereExpression = parseWhereExpression();
                filters = flatten(whereExpression);
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
            if (!peek().isEof()) {
                throw error("Unexpected token '" + peek().text + "'", peek().position);
            }
            return new QueryAst(
                    select,
                    List.of(),
                    filters,
                    whereExpression,
                    List.of(),
                    List.of(),
                    null,
                    List.of(),
                    null,
                    orders,
                    limit,
                    limitParameter,
                    offset,
                    offsetParameter
            );
        }

        private SelectAst parseSelect() {
            List<List<Token>> items = new ArrayList<>();
            items.add(readClauseItem("SHOW"));
            while (match(TokenType.COMMA)) {
                items.add(readClauseItem("SHOW"));
            }
            if (items.isEmpty() || items.get(0).isEmpty()) {
                throw error("SHOW requires 'all' or one or more projected fields", peek().position);
            }
            if (items.size() == 1 && isWildcardItem(items.get(0))) {
                return new SelectAst(true, List.of(), null);
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
            return new SelectAst(false, fields, null);
        }

        private SelectFieldAst parseSelectField(List<Token> itemTokens) {
            int asIndex = lastIndexOfWord(itemTokens, "as");
            List<Token> fieldTokens = asIndex < 0 ? itemTokens : itemTokens.subList(0, asIndex);
            List<Token> aliasTokens = asIndex < 0 ? List.of() : itemTokens.subList(asIndex + 1, itemTokens.size());
            String field = normalizeFieldReference(fieldTokens);
            String alias = aliasTokens.isEmpty() ? null : normalizeAlias(aliasTokens);
            return new SelectFieldAst(field, alias, null, false, (TimeBucketPreset) null, false);
        }

        private FilterExpressionAst parseWhereExpression() {
            FilterExpressionAst expression = parseOrExpression();
            if (expression == null) {
                throw error("WHERE requires at least one predicate", peek().position);
            }
            return expression;
        }

        private FilterExpressionAst parseOrExpression() {
            FilterExpressionAst left = parseAndExpression();
            while (matchWord("or")) {
                left = new FilterBinaryAst(left, parseAndExpression(), Separator.OR);
            }
            return left;
        }

        private FilterExpressionAst parseAndExpression() {
            FilterExpressionAst left = parsePredicate();
            while (matchWord("and")) {
                left = new FilterBinaryAst(left, parsePredicate(), Separator.AND);
            }
            return left;
        }

        private FilterExpressionAst parsePredicate() {
            if (peek().type == TokenType.LEFT_PAREN || peek().type == TokenType.RIGHT_PAREN) {
                throw error("Parentheses are not supported in MVP natural queries", peek().position);
            }

            OperatorMatch operator = findOperator();
            if (operator == null) {
                throw error("Expected a supported operator phrase in WHERE", peek().position);
            }

            List<Token> fieldTokens = slice(index, operator.startIndex());
            if (fieldTokens.isEmpty()) {
                throw error("Expected field before operator", peek().position);
            }
            String field = normalizeFieldReference(fieldTokens);

            index = operator.endIndex();
            List<Token> valueTokens = readValueTokens();
            if (valueTokens.isEmpty()) {
                throw error("Expected value after operator", peek().position);
            }
            Object value = parseValue(valueTokens, operator.operator());
            return new FilterPredicateAst(new FilterAst(field, operator.operator().clause, value, null));
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
            return new OrderAst(normalizeFieldReference(fieldTokens), sort);
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
            if (itemTokens.size() != 1) {
                return false;
            }
            Token token = itemTokens.get(0);
            if (token.type == TokenType.STAR) {
                return true;
            }
            return token.type == TokenType.RAW
                    && WILDCARD_TERMS.contains(token.text.toLowerCase(Locale.ROOT));
        }

        private boolean isBooleanBoundary(Token token) {
            return isWord(token, "and") || isWord(token, "or");
        }

        private boolean isClauseBoundary(int tokenIndex) {
            Token token = tokenAt(tokenIndex);
            if (token.isEof()) {
                return true;
            }
            if (isWord(token, "where") || isWord(token, "limit") || isWord(token, "offset")) {
                return true;
            }
            return isWord(token, "sort") && isWord(tokenAt(tokenIndex + 1), "by");
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

    private static boolean endsWithWord(List<Token> tokens, String value) {
        return !tokens.isEmpty() && isWord(tokens.get(tokens.size() - 1), value);
    }

    private static boolean isWord(Token token, String expected) {
        return token.type == TokenType.RAW && token.text.equalsIgnoreCase(expected);
    }

    private static String normalizeFieldReference(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("Field reference must not be blank");
        }
        String raw = joinTokens(tokens);
        if (raw.contains(".")) {
            String[] parts = raw.split("\\.");
            ArrayList<String> normalized = new ArrayList<>(parts.length);
            for (String part : parts) {
                normalized.add(normalizeIdentifierPhrase(part));
            }
            return String.join(".", normalized);
        }
        return normalizeIdentifierPhrase(raw);
    }

    private static String normalizeAlias(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("Alias must not be blank");
        }
        return normalizeIdentifierPhrase(joinTokens(tokens));
    }

    private static String normalizeIdentifierPhrase(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Identifier phrase must not be blank");
        }
        if (trimmed.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return trimmed;
        }
        String cleaned = trimmed.replace('-', ' ').replace('/', ' ');
        String[] parts = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].replaceAll("[^A-Za-z0-9_]", "");
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() == 0) {
                sb.append(part.substring(0, 1).toLowerCase(Locale.ROOT));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            } else {
                sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        if (sb.length() == 0) {
            throw new IllegalArgumentException("Identifier phrase must contain letters or digits");
        }
        return sb.toString();
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
}
