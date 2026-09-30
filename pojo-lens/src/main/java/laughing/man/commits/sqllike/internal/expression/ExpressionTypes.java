package laughing.man.commits.sqllike.internal.expression;

import laughing.man.commits.util.ReflectionUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.Date;

/**
 * Value kinds, runtime value conversion, and output coercion for expressions.
 */
final class ExpressionTypes {

    /**
     * Largest magnitude printed without a fraction when a whole {@code double} is shown as text.
     */
    private static final double WHOLE_NUMBER_TEXT_LIMIT = 1e15;

    private ExpressionTypes() {
    }

    /**
     * Static value kinds. {@link #ANY} is a field of unknown type, checked at runtime.
     */
    enum Kind {
        NUMBER,
        TEXT,
        TEMPORAL,
        OTHER,
        ANY
    }

    static Kind kindOf(Class<?> type) {
        Class<?> boxed = boxed(type);
        if (boxed == null || boxed == Object.class) {
            return Kind.ANY;
        }
        if (Number.class.isAssignableFrom(boxed)) {
            return Kind.NUMBER;
        }
        if (CharSequence.class.isAssignableFrom(boxed) || boxed == Character.class || boxed.isEnum()) {
            return Kind.TEXT;
        }
        if (Date.class.isAssignableFrom(boxed) || Temporal.class.isAssignableFrom(boxed)) {
            return Kind.TEMPORAL;
        }
        return Kind.OTHER;
    }

    static Class<?> boxed(Class<?> type) {
        return ReflectionUtil.wrapPrimitive(type);
    }

    static String describe(Class<?> type) {
        return switch (kindOf(type)) {
            case NUMBER -> "a number (" + type.getSimpleName() + ")";
            case TEXT -> "text (" + type.getSimpleName() + ")";
            case TEMPORAL -> "a date/time (" + type.getSimpleName() + ")";
            case OTHER -> type.getSimpleName();
            case ANY -> "a value";
        };
    }

    /**
     * Text form of a text-kind value: strings as-is, characters, and enum names.
     *
     * @return {@code null} for {@code null}
     */
    static String text(Object value, String functionName) {
        return switch (value) {
            case null -> null;
            case CharSequence sequence -> sequence.toString();
            case Character character -> String.valueOf(character);
            case Enum<?> constant -> constant.name();
            default -> throw new IllegalArgumentException("Function " + functionName
                    + " needs a text value, not " + value.getClass().getSimpleName());
        };
    }

    /**
     * {@code CONCAT}'s text form of any value. A whole {@code double} prints without
     * {@code .0}, because expression arithmetic is done in {@code double}:
     * {@code concat('#', id + 1)} gives {@code #2}.
     */
    static String displayText(Object value) {
        return switch (value) {
            case null -> null;
            case Enum<?> constant -> constant.name();
            case Double number -> wholeNumberText(number);
            case Float number -> wholeNumberText(number.doubleValue());
            default -> String.valueOf(value);
        };
    }

    private static String wholeNumberText(double value) {
        if (Double.isFinite(value) && Double.compare(value, Math.rint(value)) == 0
                && Math.abs(value) < WHOLE_NUMBER_TEXT_LIMIT) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    /**
     * Whether a result of {@code resultType} can be stored as {@code outputType}: numbers
     * convert between numeric classes, text-kind values into {@code String}, and other
     * values need an assignable type. An unknown result type is checked at runtime.
     */
    static boolean acceptsOutput(Class<?> resultType, Class<?> outputType) {
        Class<?> target = boxed(outputType);
        Class<?> result = boxed(resultType);
        return switch (kindOf(result)) {
            case ANY -> true;
            case NUMBER -> Number.class.isAssignableFrom(target) || target.isAssignableFrom(result);
            case TEXT -> target == String.class || target.isAssignableFrom(result);
            case TEMPORAL, OTHER -> target.isAssignableFrom(result);
        };
    }

    /**
     * Converts an evaluated value to the expression's output type. Numbers convert between
     * numeric classes (whole types round), text-kind values become {@code String} for a
     * {@code String} output, and anything else passes through unchanged.
     */
    static Object coerce(Object value, Class<?> outputType) {
        Class<?> target = boxed(outputType);
        if (value == null || target == null || target.isInstance(value)) {
            return value;
        }
        if (value instanceof Number number && Number.class.isAssignableFrom(target)) {
            return coerceNumber(number.doubleValue(), target);
        }
        if (target == String.class) {
            return switch (value) {
                case CharSequence sequence -> sequence.toString();
                case Character character -> String.valueOf(character);
                case Enum<?> constant -> constant.name();
                default -> value;
            };
        }
        return value;
    }

    /**
     * Numeric output conversion: {@code NaN} (a null operand) becomes {@code null}.
     */
    static Object coerceNumber(double value, Class<?> outputType) {
        if (Double.isNaN(value)) {
            return null;
        }
        Class<?> target = boxed(outputType);
        if (target == Integer.class) {
            return (int) Math.round(value);
        }
        if (target == Long.class) {
            return Math.round(value);
        }
        if (target == Float.class) {
            return (float) value;
        }
        if (target == Short.class) {
            return (short) Math.round(value);
        }
        if (target == Byte.class) {
            return (byte) Math.round(value);
        }
        if (target == BigDecimal.class) {
            return BigDecimal.valueOf(value);
        }
        if (target == BigInteger.class) {
            return BigInteger.valueOf(Math.round(value));
        }
        return value;
    }
}
