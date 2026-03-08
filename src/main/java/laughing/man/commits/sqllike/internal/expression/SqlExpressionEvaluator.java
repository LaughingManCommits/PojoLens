package laughing.man.commits.sqllike.internal.expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class SqlExpressionEvaluator {

    private static final int TOKEN_CACHE_MAX_ENTRIES = 512;
    private static final Map<String, List<Token>> TOKEN_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Token>> eldest) {
                    return size() > TOKEN_CACHE_MAX_ENTRIES;
                }
            });

    private SqlExpressionEvaluator() {
    }

    public interface ValueResolver {
        Object resolve(String identifier);
    }

    public static boolean looksLikeExpression(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == '(' || ch == ')') {
                return true;
            }
        }
        return false;
    }

    public static double evaluateNumeric(String expression, ValueResolver resolver) {
        Parser parser = new Parser(tokensFor(expression), resolver);
        return parser.parse();
    }

    public static Set<String> collectIdentifiers(String expression) {
        Parser parser = new Parser(tokensFor(expression), name -> null, false);
        return parser.collectIdentifiers();
    }

    public static String rewriteIdentifiers(String expression, UnaryOperator<String> rewriter) {
        List<Token> tokens = tokensFor(expression);
        StringBuilder rewritten = new StringBuilder(expression.length());
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.type == TokenType.EOF) {
                break;
            }
            if (token.type == TokenType.IDENTIFIER && !isFunctionCall(tokens, i)) {
                rewritten.append(rewriter.apply(token.text));
            } else {
                rewritten.append(token.text);
            }
        }
        return rewritten.toString();
    }

    private static boolean isFunctionCall(List<Token> tokens, int index) {
        return index + 1 < tokens.size()
                && tokens.get(index + 1).type == TokenType.SYMBOL
                && "(".equals(tokens.get(index + 1).text);
    }

    private static List<Token> tokensFor(String expression) {
        List<Token> cached = TOKEN_CACHE.get(expression);
        if (cached != null) {
            return cached;
        }
        List<Token> parsed = tokenize(expression);
        TOKEN_CACHE.put(expression, parsed);
        return parsed;
    }

    private static List<Token> tokenize(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Expression must not be blank");
        }
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < expression.length()) {
            char ch = expression.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            if (ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == '(' || ch == ')' || ch == ',') {
                tokens.add(new Token(String.valueOf(ch), TokenType.SYMBOL));
                i++;
                continue;
            }
            if (Character.isDigit(ch) || ch == '.') {
                int start = i++;
                while (i < expression.length() && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new Token(expression.substring(start, i), TokenType.NUMBER));
                continue;
            }
            if (Character.isLetter(ch) || ch == '_') {
                int start = i++;
                while (i < expression.length()) {
                    char c = expression.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                        i++;
                        continue;
                    }
                    break;
                }
                tokens.add(new Token(expression.substring(start, i), TokenType.IDENTIFIER));
                continue;
            }
            throw new IllegalArgumentException("Unsupported character '" + ch + "' in expression");
        }
        tokens.add(new Token("", TokenType.EOF));
        return tokens;
    }

    private enum TokenType {
        NUMBER,
        IDENTIFIER,
        SYMBOL,
        EOF
    }

    private static final class Token {
        private final String text;
        private final TokenType type;

        private Token(String text, TokenType type) {
            this.text = text;
            this.type = type;
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final ValueResolver resolver;
        private final boolean resolveValues;
        private int index;
        private final LinkedHashSet<String> identifiers = new LinkedHashSet<>();

        private Parser(List<Token> tokens, ValueResolver resolver) {
            this(tokens, resolver, true);
        }

        private Parser(List<Token> tokens, ValueResolver resolver, boolean resolveValues) {
            this.tokens = tokens;
            this.resolver = resolver;
            this.resolveValues = resolveValues;
        }

        private double parse() {
            double value = parseExpression();
            expect(TokenType.EOF, null);
            return value;
        }

        private Set<String> collectIdentifiers() {
            parse();
            return identifiers;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                if (matchSymbol("+")) {
                    value += parseTerm();
                    continue;
                }
                if (matchSymbol("-")) {
                    value -= parseTerm();
                    continue;
                }
                break;
            }
            return value;
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                if (matchSymbol("*")) {
                    value *= parseFactor();
                    continue;
                }
                if (matchSymbol("/")) {
                    double divisor = parseFactor();
                    if (resolveValues && Math.abs(divisor) < 1e-12) {
                        throw new IllegalArgumentException("Division by zero in expression");
                    }
                    value /= divisor;
                    continue;
                }
                break;
            }
            return value;
        }

        private double parseFactor() {
            if (matchSymbol("+")) {
                return parseFactor();
            }
            if (matchSymbol("-")) {
                return -parseFactor();
            }
            if (matchSymbol("(")) {
                double value = parseExpression();
                expectSymbol(")");
                return value;
            }
            Token token = peek();
            if (token.type == TokenType.NUMBER) {
                next();
                try {
                    return Double.parseDouble(token.text);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid numeric literal '" + token.text + "'");
                }
            }
            if (token.type == TokenType.IDENTIFIER) {
                String identifier = token.text;
                next();
                if (matchSymbol("(")) {
                    List<Double> args = parseFunctionArgs();
                    return callFunction(identifier, args);
                }
                identifiers.add(identifier);
                if (!resolveValues) {
                    return 0.0;
                }
                Object value = resolver.resolve(identifier);
                if (value == null) {
                    throw new IllegalArgumentException("Unknown expression identifier '" + identifier + "'");
                }
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException("Expression identifier '" + identifier + "' must be numeric");
                }
                return ((Number) value).doubleValue();
            }
            throw new IllegalArgumentException("Expected numeric expression term");
        }

        private List<Double> parseFunctionArgs() {
            List<Double> args = new ArrayList<>();
            if (matchSymbol(")")) {
                return args;
            }
            args.add(parseExpression());
            while (matchSymbol(",")) {
                args.add(parseExpression());
            }
            expectSymbol(")");
            return args;
        }

        private double callFunction(String rawName, List<Double> args) {
            String name = rawName.toUpperCase(Locale.ROOT);
            if ("ABS".equals(name)) {
                requireArgCount(name, args, 1);
                return Math.abs(args.get(0));
            }
            if ("ROUND".equals(name)) {
                requireArgCount(name, args, 1);
                return Math.rint(args.get(0));
            }
            if ("FLOOR".equals(name)) {
                requireArgCount(name, args, 1);
                return Math.floor(args.get(0));
            }
            if ("CEIL".equals(name) || "CEILING".equals(name)) {
                requireArgCount(name, args, 1);
                return Math.ceil(args.get(0));
            }
            throw new IllegalArgumentException("Unsupported expression function '" + rawName + "'");
        }

        private void requireArgCount(String functionName, List<Double> args, int expected) {
            if (args.size() != expected) {
                throw new IllegalArgumentException(
                        "Function " + functionName + " requires " + expected + " argument(s)"
                );
            }
        }

        private Token peek() {
            return tokens.get(index);
        }

        private Token next() {
            return tokens.get(index++);
        }

        private boolean matchSymbol(String symbol) {
            Token token = peek();
            if (token.type == TokenType.SYMBOL && symbol.equals(token.text)) {
                index++;
                return true;
            }
            return false;
        }

        private void expectSymbol(String symbol) {
            if (!matchSymbol(symbol)) {
                throw new IllegalArgumentException("Expected '" + symbol + "' in expression");
            }
        }

        private void expect(TokenType type, String text) {
            Token token = peek();
            if (token.type != type) {
                throw new IllegalArgumentException("Unexpected expression token '" + token.text + "'");
            }
            if (text != null && !text.equals(token.text)) {
                throw new IllegalArgumentException("Expected '" + text + "' in expression");
            }
        }
    }
}

