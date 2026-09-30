package laughing.man.commits.sqllike.internal.expression;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import laughing.man.commits.sqllike.internal.expression.ExpressionNode.Arithmetic;
import laughing.man.commits.sqllike.internal.expression.ExpressionNode.Call;
import laughing.man.commits.sqllike.internal.expression.ExpressionNode.Identifier;
import laughing.man.commits.sqllike.internal.expression.ExpressionNode.NullLiteral;
import laughing.man.commits.sqllike.internal.expression.ExpressionNode.NumberLiteral;
import laughing.man.commits.sqllike.internal.expression.ExpressionNode.TextLiteral;
import laughing.man.commits.sqllike.internal.expression.ExpressionNode.Unary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Single owner of expression parsing, static typing, evaluation, and output conversion.
 * Expressions combine numbers, {@code 'text'} and {@code null} literals, identifiers,
 * {@code + - * /}, and the functions in {@link ExpressionFunction}. Pure-numeric
 * expressions keep a {@code double} lane ({@link #compileNumeric}).
 */
public final class SqlExpressionEvaluator {

    private static final int TOKEN_CACHE_MAX_ENTRIES = 512;
    private static final int COMPILED_CACHE_MAX_ENTRIES = 512;
    private static final String BLANK_EXPRESSION_MESSAGE = "Expression must not be blank";
    private static final Cache<String, List<Token>> TOKEN_CACHE =
            Caffeine.newBuilder().maximumSize(TOKEN_CACHE_MAX_ENTRIES).recordStats().build();
    private static final Cache<String, CompiledExpression> COMPILED_CACHE =
            Caffeine.newBuilder().maximumSize(COMPILED_CACHE_MAX_ENTRIES).recordStats().build();

    private SqlExpressionEvaluator() {
    }

    /**
     * Returned by a {@link ValueResolver} for an identifier that does not exist, as opposed
     * to {@code null} for a field that exists but holds no value.
     */
    public static final Object UNKNOWN_IDENTIFIER = new Object();

    public interface ValueResolver {
        /**
         * @return the identifier's value, {@code null} when it has no value (the expression
         * then evaluates to {@code NaN}, see {@link #toNullable(double)}), or
         * {@link #UNKNOWN_IDENTIFIER} when the identifier does not exist
         */
        Object resolve(String identifier);
    }

    /**
     * Maps an evaluation result to a nullable value: a null operand propagates as
     * {@code NaN} through the arithmetic and comes back as {@code null}, like SQL NULL.
     */
    public static Double toNullable(double value) {
        return Double.isNaN(value) ? null : value;
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

    /**
     * Evaluates any expression to its typed value ({@code null} for SQL NULL). Arithmetic
     * results are {@code Double}.
     */
    public static Object evaluate(String expression, ValueResolver resolver) {
        return compile(expression).evaluateValue(resolver);
    }

    /**
     * Static result type of an expression over the given field types.
     *
     * @return {@code Object.class} when the type depends on a field of unknown type
     * @throws IllegalArgumentException when an operand or argument has the wrong kind
     */
    public static Class<?> resultType(String expression, Function<String, Class<?>> fieldTypes) {
        return compile(expression).resultType(fieldTypes);
    }

    /**
     * True for a text result or one of unknown type, which text operators
     * ({@code CONTAINS}, {@code MATCHES}, {@code LIKE}) accept.
     */
    public static boolean isTextOrUnknown(Class<?> type) {
        ExpressionTypes.Kind kind = ExpressionTypes.kindOf(type);
        return kind == ExpressionTypes.Kind.TEXT || kind == ExpressionTypes.Kind.ANY;
    }

    /**
     * Converts an evaluated value to an expression's output type (WP-29 D6), so a column
     * never mixes numeric classes: numbers convert (whole types round), text-kind values
     * become {@code String} for a {@code String} output.
     */
    public static Object coerce(Object value, Class<?> outputType) {
        return ExpressionTypes.coerce(value, outputType);
    }

    /**
     * Checks that an expression result can be stored as a declared output type: numbers
     * convert between numeric classes, text-kind values into {@code String}, and other
     * values need an assignable type. An unknown ({@code Object}) result is checked at
     * runtime instead.
     *
     * @param owner names the output in the message, such as {@code "Computed field 'x'"}
     * @throws IllegalArgumentException when the types cannot match
     */
    public static void requireOutputType(String owner, Class<?> resultType, Class<?> outputType) {
        if (!ExpressionTypes.acceptsOutput(resultType, outputType)) {
            throw new IllegalArgumentException(owner + " returns " + ExpressionTypes.describe(resultType)
                    + ", which cannot be stored as " + outputType.getSimpleName());
        }
    }

    /**
     * {@link #coerce} for the numeric lane: {@code NaN} (a null operand) becomes {@code null}.
     */
    public static Object coerceNumber(double value, Class<?> outputType) {
        return ExpressionTypes.coerceNumber(value, outputType);
    }

    /**
     * True when the text is an expression that only calls expression functions, as opposed
     * to a plain field name or an aggregate/window reference such as {@code sum(salary)}.
     * The text may still be invalid; {@link #compile} reports why.
     */
    public static boolean isScalarExpression(String text) {
        if (!looksLikeExpression(text)) {
            return false;
        }
        List<Token> tokens;
        try {
            tokens = tokensFor(text);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.type == TokenType.IDENTIFIER && isFunctionCall(tokens, i)
                    && ExpressionFunction.find(token.text) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Normalized text that is equal for equivalent spellings of an expression.
     *
     * @see CompiledExpression#canonical()
     */
    public static String canonical(String expression) {
        return compile(expression).canonical();
    }

    public static Set<String> collectIdentifiers(String expression) {
        return compile(expression).identifiers();
    }

    public static String rewriteIdentifiers(String expression, UnaryOperator<String> rewriter) {
        String validExpression = requireExpression(expression);
        List<Token> tokens = tokensFor(validExpression);
        StringBuilder rewritten = new StringBuilder(validExpression.length());
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            switch (token.type) {
                case EOF -> {
                    return rewritten.toString();
                }
                case IDENTIFIER -> rewritten.append(isFunctionCall(tokens, i) ? token.text : rewriter.apply(token.text));
                case TEXT -> rewritten.append(quote(token.text));
                default -> rewritten.append(token.text);
            }
        }
        return rewritten.toString();
    }

    public static CompiledExpression compile(String expression) {
        String validExpression = requireExpression(expression);
        return COMPILED_CACHE.get(validExpression, expr -> new Compiler(tokensFor(expr)).compile());
    }

    /**
     * Compiles an expression for the {@code double} lane.
     *
     * @throws IllegalArgumentException when the expression returns text or another non-number
     */
    public static CompiledExpression compileNumeric(String expression) {
        CompiledExpression compiled = compile(expression);
        if (!compiled.numeric) {
            throw new IllegalArgumentException("Expression '" + expression.trim() + "' returns "
                    + ExpressionTypes.describe(compiled.untypedResult) + ", not a number");
        }
        return compiled;
    }

    private static boolean isFunctionCall(List<Token> tokens, int index) {
        return index + 1 < tokens.size()
                && tokens.get(index + 1).type == TokenType.SYMBOL
                && "(".equals(tokens.get(index + 1).text);
    }

    static String quote(String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    private static List<Token> tokensFor(String expression) {
        return TOKEN_CACHE.get(expression, SqlExpressionEvaluator::tokenize);
    }

    private static List<Token> tokenize(String expression) {
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
            if (ch == '\'') {
                i = readText(expression, i + 1, tokens);
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
                String word = expression.substring(start, i);
                tokens.add(new Token(word, "null".equalsIgnoreCase(word) ? TokenType.NULL : TokenType.IDENTIFIER));
                continue;
            }
            throw new IllegalArgumentException("Unsupported character '" + ch + "' in expression");
        }
        tokens.add(new Token("", TokenType.EOF));
        return tokens;
    }

    /**
     * Reads a {@code 'text'} literal body ({@code ''} is an escaped quote) starting after the
     * opening quote.
     *
     * @return the index after the closing quote
     */
    private static int readText(String expression, int start, List<Token> tokens) {
        StringBuilder text = new StringBuilder();
        int i = start;
        while (i < expression.length()) {
            char ch = expression.charAt(i);
            if (ch != '\'') {
                text.append(ch);
                i++;
                continue;
            }
            if (i + 1 < expression.length() && expression.charAt(i + 1) == '\'') {
                text.append('\'');
                i += 2;
                continue;
            }
            tokens.add(new Token(text.toString(), TokenType.TEXT));
            return i + 1;
        }
        throw new IllegalArgumentException("Unterminated text literal in expression");
    }

    private static String requireExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException(BLANK_EXPRESSION_MESSAGE);
        }
        return expression;
    }

    private enum TokenType {
        NUMBER,
        TEXT,
        NULL,
        IDENTIFIER,
        SYMBOL,
        EOF
    }

    private record Token(String text, TokenType type) {
    }

    public static final class CompiledExpression {
        private final ExpressionNode root;
        private final List<String> identifierOrder;
        private final Set<String> identifiers;
        private final Class<?> untypedResult;
        private final boolean numeric;
        private final String canonical;

        private CompiledExpression(ExpressionNode root, List<String> identifierOrder) {
            this.root = root;
            this.identifierOrder = List.copyOf(identifierOrder);
            this.identifiers = Collections.unmodifiableSet(new LinkedHashSet<>(identifierOrder));
            // Typing without field types checks the literals and function results up front.
            this.untypedResult = resultType(identifier -> null);
            ExpressionTypes.Kind kind = ExpressionTypes.kindOf(untypedResult);
            this.numeric = kind == ExpressionTypes.Kind.NUMBER || kind == ExpressionTypes.Kind.ANY;
            this.canonical = ExpressionNode.canonical(root);
        }

        /**
         * Normalized text: upper-case function names, {@code double} numbers, and every
         * operation parenthesized, so spacing and redundant parentheses do not matter.
         */
        public String canonical() {
            return canonical;
        }

        /**
         * Numeric lane: a null operand gives {@code NaN}.
         */
        public double evaluate(ValueResolver resolver) {
            return root.number(null, null, resolver);
        }

        public Object evaluateValue(ValueResolver resolver) {
            return root.value(null, null, resolver);
        }

        /**
         * @see SqlExpressionEvaluator#resultType(String, Function)
         */
        public Class<?> resultType(Function<String, Class<?>> fieldTypes) {
            Class<?> type = root.type(fieldTypes);
            return type == null ? Object.class : type;
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
        private final ExpressionNode root;
        private final int[] identifierIndexes;

        private BoundExpression(ExpressionNode root, int[] identifierIndexes) {
            this.root = root;
            this.identifierIndexes = identifierIndexes;
        }

        public double evaluate(Object[] values) {
            return root.number(values, identifierIndexes, null);
        }

        public Object evaluateValue(Object[] values) {
            return root.value(values, identifierIndexes, null);
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
            ExpressionNode value = parseExpression();
            if (peek().type != TokenType.EOF) {
                throw new IllegalArgumentException("Unexpected expression token '" + peek().text + "'");
            }
            return new CompiledExpression(value, new ArrayList<>(identifierOrdinals.keySet()));
        }

        private ExpressionNode parseExpression() {
            ExpressionNode value = parseTerm();
            while (true) {
                if (matchSymbol("+")) {
                    value = new Arithmetic('+', value, parseTerm());
                    continue;
                }
                if (matchSymbol("-")) {
                    value = new Arithmetic('-', value, parseTerm());
                    continue;
                }
                break;
            }
            return value;
        }

        private ExpressionNode parseTerm() {
            ExpressionNode value = parseFactor();
            while (true) {
                if (matchSymbol("*")) {
                    value = new Arithmetic('*', value, parseFactor());
                    continue;
                }
                if (matchSymbol("/")) {
                    value = new Arithmetic('/', value, parseFactor());
                    continue;
                }
                break;
            }
            return value;
        }

        private ExpressionNode parseFactor() {
            if (matchSymbol("+")) {
                return new Unary(false, parseFactor());
            }
            if (matchSymbol("-")) {
                return new Unary(true, parseFactor());
            }
            if (matchSymbol("(")) {
                ExpressionNode value = parseExpression();
                expectSymbol(")");
                return value;
            }
            Token token = next();
            return switch (token.type) {
                case NUMBER -> new NumberLiteral(parseNumber(token.text));
                case TEXT -> new TextLiteral(token.text);
                case NULL -> new NullLiteral();
                case IDENTIFIER -> matchSymbol("(")
                        ? callFunction(token.text, parseFunctionArgs())
                        : new Identifier(token.text, identifierOrdinal(token.text));
                default -> throw new IllegalArgumentException("Expected expression term");
            };
        }

        private static double parseNumber(String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid numeric literal '" + text + "'");
            }
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

        private List<ExpressionNode> parseFunctionArgs() {
            List<ExpressionNode> args = new ArrayList<>();
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

        private ExpressionNode callFunction(String rawName, List<ExpressionNode> args) {
            ExpressionFunction function = ExpressionFunction.find(rawName);
            if (function == null) {
                throw new IllegalArgumentException("Unsupported expression function '" + rawName + "'");
            }
            function.requireArgumentCount(rawName, args.size());
            return new Call(function, function.prepare(args));
        }

        private Token peek() {
            return tokens.get(index);
        }

        private Token next() {
            Token token = tokens.get(index);
            if (token.type != TokenType.EOF) {
                index++;
            }
            return token;
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
    }
}
