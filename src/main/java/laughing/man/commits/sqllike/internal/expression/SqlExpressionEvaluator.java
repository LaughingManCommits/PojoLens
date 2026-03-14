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
    private static final int COMPILED_CACHE_MAX_ENTRIES = 512;
    private static final Map<String, List<Token>> TOKEN_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Token>> eldest) {
                    return size() > TOKEN_CACHE_MAX_ENTRIES;
                }
            });
    private static final Map<String, CompiledExpression> COMPILED_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CompiledExpression> eldest) {
                    return size() > COMPILED_CACHE_MAX_ENTRIES;
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
        return compileNumeric(expression).evaluate(resolver);
    }

    public static Set<String> collectIdentifiers(String expression) {
        return compileNumeric(expression).identifiers();
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

    public static CompiledExpression compileNumeric(String expression) {
        CompiledExpression cached = COMPILED_CACHE.get(expression);
        if (cached != null) {
            return cached;
        }
        CompiledExpression compiled = new Compiler(tokensFor(expression)).compile();
        COMPILED_CACHE.put(expression, compiled);
        return compiled;
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

    public static final class CompiledExpression {
        private final Node root;
        private final List<String> identifierOrder;
        private final Set<String> identifiers;

        private CompiledExpression(Node root, List<String> identifierOrder) {
            this.root = root;
            this.identifierOrder = List.copyOf(identifierOrder);
            this.identifiers = Collections.unmodifiableSet(new LinkedHashSet<>(identifierOrder));
        }

        public double evaluate(ValueResolver resolver) {
            return root.evaluate(resolver);
        }

        public BoundExpression bind(int[] identifierIndexes) {
            if (identifierIndexes == null) {
                throw new IllegalArgumentException("identifierIndexes must not be null");
            }
            if (identifierIndexes.length != identifierOrder.size()) {
                throw new IllegalArgumentException("identifierIndexes length must match expression identifiers");
            }
            return new BoundExpression(root, identifierIndexes.clone());
        }

        public Set<String> identifiers() {
            return identifiers;
        }
    }

    public static final class BoundExpression {
        private final Node root;
        private final int[] identifierIndexes;

        private BoundExpression(Node root, int[] identifierIndexes) {
            this.root = root;
            this.identifierIndexes = identifierIndexes;
        }

        public double evaluate(Object[] values) {
            return root.evaluate(values, identifierIndexes);
        }
    }

    private interface Node {
        double evaluate(ValueResolver resolver);

        double evaluate(Object[] values, int[] identifierIndexes);
    }

    private static final class NumberNode implements Node {
        private final double value;

        private NumberNode(double value) {
            this.value = value;
        }

        @Override
        public double evaluate(ValueResolver resolver) {
            return value;
        }

        @Override
        public double evaluate(Object[] values, int[] identifierIndexes) {
            return value;
        }
    }

    private static final class IdentifierNode implements Node {
        private final String identifier;
        private final int ordinal;

        private IdentifierNode(String identifier, int ordinal) {
            this.identifier = identifier;
            this.ordinal = ordinal;
        }

        @Override
        public double evaluate(ValueResolver resolver) {
            Object value = resolver.resolve(identifier);
            if (value == null) {
                throw new IllegalArgumentException("Unknown expression identifier '" + identifier + "'");
            }
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("Expression identifier '" + identifier + "' must be numeric");
            }
            return ((Number) value).doubleValue();
        }

        @Override
        public double evaluate(Object[] values, int[] identifierIndexes) {
            int sourceIndex = ordinal < identifierIndexes.length ? identifierIndexes[ordinal] : -1;
            Object value = sourceIndex >= 0 && values != null && sourceIndex < values.length ? values[sourceIndex] : null;
            if (value == null) {
                throw new IllegalArgumentException("Unknown expression identifier '" + identifier + "'");
            }
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("Expression identifier '" + identifier + "' must be numeric");
            }
            return ((Number) value).doubleValue();
        }
    }

    private static final class UnaryNode implements Node {
        private final boolean negate;
        private final Node operand;

        private UnaryNode(boolean negate, Node operand) {
            this.negate = negate;
            this.operand = operand;
        }

        @Override
        public double evaluate(ValueResolver resolver) {
            double value = operand.evaluate(resolver);
            return negate ? -value : value;
        }

        @Override
        public double evaluate(Object[] values, int[] identifierIndexes) {
            double value = operand.evaluate(values, identifierIndexes);
            return negate ? -value : value;
        }
    }

    private static final class BinaryNode implements Node {
        private final char operator;
        private final Node left;
        private final Node right;

        private BinaryNode(char operator, Node left, Node right) {
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        @Override
        public double evaluate(ValueResolver resolver) {
            double leftValue = left.evaluate(resolver);
            double rightValue = right.evaluate(resolver);
            if (operator == '+') {
                return leftValue + rightValue;
            }
            if (operator == '-') {
                return leftValue - rightValue;
            }
            if (operator == '*') {
                return leftValue * rightValue;
            }
            if (Math.abs(rightValue) < 1e-12) {
                throw new IllegalArgumentException("Division by zero in expression");
            }
            return leftValue / rightValue;
        }

        @Override
        public double evaluate(Object[] values, int[] identifierIndexes) {
            double leftValue = left.evaluate(values, identifierIndexes);
            double rightValue = right.evaluate(values, identifierIndexes);
            if (operator == '+') {
                return leftValue + rightValue;
            }
            if (operator == '-') {
                return leftValue - rightValue;
            }
            if (operator == '*') {
                return leftValue * rightValue;
            }
            if (Math.abs(rightValue) < 1e-12) {
                throw new IllegalArgumentException("Division by zero in expression");
            }
            return leftValue / rightValue;
        }
    }

    private static final class FunctionNode implements Node {
        private final String rawName;
        private final String normalizedName;
        private final List<Node> arguments;

        private FunctionNode(String rawName, String normalizedName, List<Node> arguments) {
            this.rawName = rawName;
            this.normalizedName = normalizedName;
            this.arguments = List.copyOf(arguments);
        }

        @Override
        public double evaluate(ValueResolver resolver) {
            if ("ABS".equals(normalizedName)) {
                return Math.abs(arguments.get(0).evaluate(resolver));
            }
            if ("ROUND".equals(normalizedName)) {
                return Math.rint(arguments.get(0).evaluate(resolver));
            }
            if ("FLOOR".equals(normalizedName)) {
                return Math.floor(arguments.get(0).evaluate(resolver));
            }
            if ("CEIL".equals(normalizedName) || "CEILING".equals(normalizedName)) {
                return Math.ceil(arguments.get(0).evaluate(resolver));
            }
            throw new IllegalArgumentException("Unsupported expression function '" + rawName + "'");
        }

        @Override
        public double evaluate(Object[] values, int[] identifierIndexes) {
            if ("ABS".equals(normalizedName)) {
                return Math.abs(arguments.get(0).evaluate(values, identifierIndexes));
            }
            if ("ROUND".equals(normalizedName)) {
                return Math.rint(arguments.get(0).evaluate(values, identifierIndexes));
            }
            if ("FLOOR".equals(normalizedName)) {
                return Math.floor(arguments.get(0).evaluate(values, identifierIndexes));
            }
            if ("CEIL".equals(normalizedName) || "CEILING".equals(normalizedName)) {
                return Math.ceil(arguments.get(0).evaluate(values, identifierIndexes));
            }
            throw new IllegalArgumentException("Unsupported expression function '" + rawName + "'");
        }
    }

    private static final class Compiler {
        private final List<Token> tokens;
        private int index;
        private final LinkedHashMap<String, Integer> identifierOrdinals = new LinkedHashMap<>();

        private Compiler(List<Token> tokens) {
            this.tokens = tokens;
        }

        private CompiledExpression compile() {
            Node value = parseExpression();
            expect(TokenType.EOF, null);
            return new CompiledExpression(value, new ArrayList<>(identifierOrdinals.keySet()));
        }

        private Node parseExpression() {
            Node value = parseTerm();
            while (true) {
                if (matchSymbol("+")) {
                    value = new BinaryNode('+', value, parseTerm());
                    continue;
                }
                if (matchSymbol("-")) {
                    value = new BinaryNode('-', value, parseTerm());
                    continue;
                }
                break;
            }
            return value;
        }

        private Node parseTerm() {
            Node value = parseFactor();
            while (true) {
                if (matchSymbol("*")) {
                    value = new BinaryNode('*', value, parseFactor());
                    continue;
                }
                if (matchSymbol("/")) {
                    value = new BinaryNode('/', value, parseFactor());
                    continue;
                }
                break;
            }
            return value;
        }

        private Node parseFactor() {
            if (matchSymbol("+")) {
                return new UnaryNode(false, parseFactor());
            }
            if (matchSymbol("-")) {
                return new UnaryNode(true, parseFactor());
            }
            if (matchSymbol("(")) {
                Node value = parseExpression();
                expectSymbol(")");
                return value;
            }
            Token token = peek();
            if (token.type == TokenType.NUMBER) {
                next();
                try {
                    return new NumberNode(Double.parseDouble(token.text));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid numeric literal '" + token.text + "'");
                }
            }
            if (token.type == TokenType.IDENTIFIER) {
                String identifier = token.text;
                next();
                if (matchSymbol("(")) {
                    List<Node> args = parseFunctionArgs();
                    return callFunction(identifier, args);
                }
                return new IdentifierNode(identifier, identifierOrdinal(identifier));
            }
            throw new IllegalArgumentException("Expected numeric expression term");
        }

        private int identifierOrdinal(String identifier) {
            Integer existing = identifierOrdinals.get(identifier);
            if (existing != null) {
                return existing;
            }
            int ordinal = identifierOrdinals.size();
            identifierOrdinals.put(identifier, ordinal);
            return ordinal;
        }

        private List<Node> parseFunctionArgs() {
            List<Node> args = new ArrayList<>();
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

        private Node callFunction(String rawName, List<Node> args) {
            String name = rawName.toUpperCase(Locale.ROOT);
            if ("ABS".equals(name)) {
                requireArgCount(name, args, 1);
                return new FunctionNode(rawName, name, args);
            }
            if ("ROUND".equals(name)) {
                requireArgCount(name, args, 1);
                return new FunctionNode(rawName, name, args);
            }
            if ("FLOOR".equals(name)) {
                requireArgCount(name, args, 1);
                return new FunctionNode(rawName, name, args);
            }
            if ("CEIL".equals(name) || "CEILING".equals(name)) {
                requireArgCount(name, args, 1);
                return new FunctionNode(rawName, name, args);
            }
            throw new IllegalArgumentException("Unsupported expression function '" + rawName + "'");
        }

        private void requireArgCount(String functionName, List<?> args, int expected) {
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

