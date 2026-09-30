package laughing.man.commits.sqllike.internal.expression;

import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator.ValueResolver;

import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;

/**
 * Compiled expression tree. Every node evaluates two ways: {@link #value} gives the typed
 * value ({@code null} for SQL NULL), and {@link #number} is the numeric lane, where a null
 * operand is {@code NaN}. Identifiers read either from a {@link ValueResolver} (when it is
 * non-null) or from {@code row[slots[ordinal]]} for bound array evaluation.
 */
sealed interface ExpressionNode {

    Object value(Object[] row, int[] slots, ValueResolver resolver);

    double number(Object[] row, int[] slots, ValueResolver resolver);

    /**
     * Static result type from the field types: {@code Object.class} when unknown, and
     * {@code null} only for the {@code null} literal.
     *
     * @throws IllegalArgumentException when an operand has the wrong kind
     */
    Class<?> type(Function<String, Class<?>> fieldTypes);

    /**
     * One normalized text per expression: function names in upper case (aliases resolved),
     * numbers as {@code double}, and every operation parenthesized. Spacing, letter case of
     * function names, and redundant parentheses therefore do not matter:
     * {@code Year( hireDate )} and {@code year(hireDate)} are the same expression.
     */
    static String canonical(ExpressionNode node) {
        return switch (node) {
            case NumberLiteral number -> Double.toString(number.literal());
            case TextLiteral text -> SqlExpressionEvaluator.quote(text.literal());
            case NullLiteral ignored -> "NULL";
            case ZoneLiteral zone -> SqlExpressionEvaluator.quote(zone.zone().getId());
            case Identifier identifier -> identifier.name();
            case Unary unary -> unary.negate() ? "(-" + canonical(unary.operand()) + ")" : canonical(unary.operand());
            case Arithmetic arithmetic -> "(" + canonical(arithmetic.left()) + " " + arithmetic.operator() + " "
                    + canonical(arithmetic.right()) + ")";
            case Call call -> call.function().name() + "(" + String.join(", ",
                    call.arguments().stream().map(ExpressionNode::canonical).toList()) + ")";
        };
    }

    static Class<?> requireNumber(Class<?> type, String context) {
        ExpressionTypes.Kind kind = ExpressionTypes.kindOf(type);
        if (kind != ExpressionTypes.Kind.NUMBER && kind != ExpressionTypes.Kind.ANY) {
            throw new IllegalArgumentException(context + " needs a number, not " + ExpressionTypes.describe(type));
        }
        return type;
    }

    record NumberLiteral(double literal) implements ExpressionNode {
        @Override
        public Object value(Object[] row, int[] slots, ValueResolver resolver) {
            return literal;
        }

        @Override
        public double number(Object[] row, int[] slots, ValueResolver resolver) {
            return literal;
        }

        @Override
        public Class<?> type(Function<String, Class<?>> fieldTypes) {
            return Double.class;
        }
    }

    record TextLiteral(String literal) implements ExpressionNode {
        @Override
        public Object value(Object[] row, int[] slots, ValueResolver resolver) {
            return literal;
        }

        @Override
        public double number(Object[] row, int[] slots, ValueResolver resolver) {
            throw new IllegalArgumentException("Text literal '" + literal + "' is not a number");
        }

        @Override
        public Class<?> type(Function<String, Class<?>> fieldTypes) {
            return String.class;
        }
    }

    record NullLiteral() implements ExpressionNode {
        @Override
        public Object value(Object[] row, int[] slots, ValueResolver resolver) {
            return null;
        }

        @Override
        public double number(Object[] row, int[] slots, ValueResolver resolver) {
            return Double.NaN;
        }

        @Override
        public Class<?> type(Function<String, Class<?>> fieldTypes) {
            return null;
        }
    }

    /**
     * A time zone parsed once at compile time from a date-part function's text literal.
     */
    record ZoneLiteral(ZoneId zone) implements ExpressionNode {
        @Override
        public Object value(Object[] row, int[] slots, ValueResolver resolver) {
            return zone;
        }

        @Override
        public double number(Object[] row, int[] slots, ValueResolver resolver) {
            throw new IllegalArgumentException("Time zone '" + zone + "' is not a number");
        }

        @Override
        public Class<?> type(Function<String, Class<?>> fieldTypes) {
            return ZoneId.class;
        }
    }

    record Identifier(String name, int ordinal) implements ExpressionNode {
        @Override
        public Object value(Object[] row, int[] slots, ValueResolver resolver) {
            if (resolver != null) {
                Object value = resolver.resolve(name);
                if (value == SqlExpressionEvaluator.UNKNOWN_IDENTIFIER) {
                    throw unknown();
                }
                return value;
            }
            int sourceIndex = ordinal < slots.length ? slots[ordinal] : -1;
            if (sourceIndex < 0 || row == null || sourceIndex >= row.length) {
                throw unknown();
            }
            return row[sourceIndex];
        }

        @Override
        public double number(Object[] row, int[] slots, ValueResolver resolver) {
            Object value = value(row, slots, resolver);
            if (value == null) {
                return Double.NaN;
            }
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException("Expression identifier '" + name + "' must be numeric");
            }
            return number.doubleValue();
        }

        @Override
        public Class<?> type(Function<String, Class<?>> fieldTypes) {
            Class<?> type = ExpressionTypes.boxed(fieldTypes.apply(name));
            return type == null ? Object.class : type;
        }

        private IllegalArgumentException unknown() {
            return new IllegalArgumentException("Unknown expression identifier '" + name + "'");
        }
    }

    record Unary(boolean negate, ExpressionNode operand) implements ExpressionNode {
        @Override
        public Object value(Object[] row, int[] slots, ValueResolver resolver) {
            return SqlExpressionEvaluator.toNullable(number(row, slots, resolver));
        }

        @Override
        public double number(Object[] row, int[] slots, ValueResolver resolver) {
            double value = operand.number(row, slots, resolver);
            return negate ? -value : value;
        }

        @Override
        public Class<?> type(Function<String, Class<?>> fieldTypes) {
            requireNumber(operand.type(fieldTypes), "Unary " + (negate ? "'-'" : "'+'"));
            return Double.class;
        }
    }

    record Arithmetic(char operator, ExpressionNode left, ExpressionNode right) implements ExpressionNode {
        private static final double DIVISION_BY_ZERO_EPSILON = 1e-12;

        @Override
        public Object value(Object[] row, int[] slots, ValueResolver resolver) {
            return SqlExpressionEvaluator.toNullable(number(row, slots, resolver));
        }

        @Override
        public double number(Object[] row, int[] slots, ValueResolver resolver) {
            double leftValue = left.number(row, slots, resolver);
            double rightValue = right.number(row, slots, resolver);
            if (operator == '+') {
                return leftValue + rightValue;
            }
            if (operator == '-') {
                return leftValue - rightValue;
            }
            if (operator == '*') {
                return leftValue * rightValue;
            }
            if (Math.abs(rightValue) < DIVISION_BY_ZERO_EPSILON) {
                throw new IllegalArgumentException("Division by zero in expression");
            }
            return leftValue / rightValue;
        }

        @Override
        public Class<?> type(Function<String, Class<?>> fieldTypes) {
            String context = "Operator '" + operator + "'";
            requireNumber(left.type(fieldTypes), context);
            requireNumber(right.type(fieldTypes), context);
            return Double.class;
        }
    }

    record Call(ExpressionFunction function, List<ExpressionNode> arguments) implements ExpressionNode {
        public Call {
            arguments = List.copyOf(arguments);
        }

        @Override
        public Object value(Object[] row, int[] slots, ValueResolver resolver) {
            return function.value(arguments, row, slots, resolver);
        }

        @Override
        public double number(Object[] row, int[] slots, ValueResolver resolver) {
            return function.number(arguments, row, slots, resolver);
        }

        @Override
        public Class<?> type(Function<String, Class<?>> fieldTypes) {
            return function.type(arguments, fieldTypes);
        }
    }
}
