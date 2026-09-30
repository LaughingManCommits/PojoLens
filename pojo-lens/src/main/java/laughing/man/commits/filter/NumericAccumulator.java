package laughing.man.commits.filter;

import laughing.man.commits.util.ObjectUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * Exact SUM/AVG/MIN/MAX state shared by grouped, global, and fast-stats aggregation.
 *
 * <p>Integral values sum as {@code long} (overflow is an error, never a silent wrap or
 * saturation), {@code BigDecimal}/{@code BigInteger} values sum exactly, and any
 * {@code float}/{@code double} value switches the column to {@code double} arithmetic.
 * MIN/MAX use {@link ObjectUtil#compareNumeric(Number, Number)}, so large {@code long}
 * values keep their order.
 */
final class NumericAccumulator {

    private final String fieldName;
    private long count;
    private Number min;
    private Number max;
    private long longSum;
    private BigDecimal exactSum;
    private double doubleSum;
    private boolean floating;
    private boolean decimal;

    NumericAccumulator(String fieldName) {
        this.fieldName = fieldName;
    }

    void add(Number value) {
        if (min == null || ObjectUtil.compareNumeric(value, min) < 0) {
            min = value;
        }
        if (max == null || ObjectUtil.compareNumeric(value, max) > 0) {
            max = value;
        }
        count++;
        doubleSum += value.doubleValue();
        if (value instanceof Double || value instanceof Float) {
            floating = true;
            return;
        }
        if (value instanceof BigDecimal || value instanceof BigInteger) {
            decimal = true;
            exactSum = exactSum().add(toBigDecimal(value));
            return;
        }
        if (exactSum != null) {
            exactSum = exactSum.add(BigDecimal.valueOf(value.longValue()));
            return;
        }
        try {
            longSum = Math.addExact(longSum, value.longValue());
        } catch (ArithmeticException overflow) {
            exactSum = BigDecimal.valueOf(longSum).add(BigDecimal.valueOf(value.longValue()));
        }
    }

    boolean present() {
        return count > 0;
    }

    Number min() {
        return min;
    }

    Number max() {
        return max;
    }

    /**
     * {@code Double} when any floating value was seen, {@code BigDecimal} for decimal
     * input, otherwise {@code Long}; {@code null} when no value was seen.
     */
    Object sum() {
        if (!present()) {
            return null;
        }
        if (floating) {
            return doubleSum;
        }
        if (decimal) {
            return exactSum;
        }
        if (exactSum != null) {
            throw new ArithmeticException("SUM(" + fieldName + ") exceeds the long range");
        }
        return longSum;
    }

    Double avg() {
        if (!present()) {
            return null;
        }
        if (floating) {
            return doubleSum / count;
        }
        BigDecimal total = exactSum != null ? exactSum : BigDecimal.valueOf(longSum);
        return total.divide(BigDecimal.valueOf(count), MathContext.DECIMAL64).doubleValue();
    }

    private BigDecimal exactSum() {
        return exactSum != null ? exactSum : BigDecimal.valueOf(longSum);
    }

    private static BigDecimal toBigDecimal(Number value) {
        return value instanceof BigDecimal d ? d : new BigDecimal((BigInteger) value);
    }
}
