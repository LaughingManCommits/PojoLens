package laughing.man.commits.internal;

import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.util.ReflectionUtil;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Single owner of the {@code LAG}/{@code LEAD} default-value rule: the default must fit
 * the value field's type. Numeric defaults convert exactly to the field's numeric type
 * (a fractional default for an integral field is rejected), text needs a text field,
 * booleans need a boolean field, and any other field type accepts only {@code null}.
 */
public final class WindowOffsetDefaults {

    private WindowOffsetDefaults() {
    }

    /**
     * Returns {@code defaultValue} converted to {@code valueType}.
     *
     * @param valueType the value field's type, or {@code null} when unknown (the default is then kept as-is)
     * @throws IllegalArgumentException when the default does not fit the value type
     */
    public static Object coerce(WindowFunction function, String valueField, Class<?> valueType, Object defaultValue) {
        Class<?> target = ReflectionUtil.wrapPrimitive(valueType);
        if (defaultValue == null || target == null || target == Object.class || target.isInstance(defaultValue)) {
            return defaultValue;
        }
        if (defaultValue instanceof Number number && Number.class.isAssignableFrom(target)) {
            return convertNumber(function, valueField, number, target);
        }
        if (defaultValue instanceof CharSequence text && target == Character.class && text.length() == 1) {
            return text.charAt(0);
        }
        throw mismatch(function, valueField, target, defaultValue);
    }

    private static Object convertNumber(WindowFunction function, String valueField, Number number, Class<?> target) {
        BigDecimal decimal = number instanceof BigDecimal exact ? exact : new BigDecimal(number.toString());
        try {
            if (target == Integer.class) {
                return decimal.intValueExact();
            }
            if (target == Long.class) {
                return decimal.longValueExact();
            }
            if (target == Short.class) {
                return decimal.shortValueExact();
            }
            if (target == Byte.class) {
                return decimal.byteValueExact();
            }
            if (target == BigInteger.class) {
                return decimal.toBigIntegerExact();
            }
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(function + " default " + number + " does not fit "
                    + target.getSimpleName() + " field '" + valueField + "'", ex);
        }
        if (target == Double.class) {
            return decimal.doubleValue();
        }
        if (target == Float.class) {
            return decimal.floatValue();
        }
        if (target == BigDecimal.class) {
            return decimal;
        }
        return number;
    }

    private static IllegalArgumentException mismatch(WindowFunction function,
                                                     String valueField,
                                                     Class<?> target,
                                                     Object defaultValue) {
        return new IllegalArgumentException(function + " default " + describe(defaultValue)
                + " does not match " + target.getSimpleName() + " field '" + valueField + "'");
    }

    private static String describe(Object value) {
        return value instanceof CharSequence ? "'" + value + "'" : String.valueOf(value);
    }
}
