package laughing.man.commits.filter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;

/**
 * Join-key identity shared by the fast array join path and the legacy row join path, so
 * both match the same rows.
 *
 * <p>Numbers match by numeric value across types ({@code int 1}, {@code long 1L},
 * {@code 1.0}, {@code BigDecimal("1.00")}); whole values normalize to {@code Integer} when
 * they fit, keeping the dense int index usable. Instants match exactly across
 * {@code Date}, {@code Instant}, {@code OffsetDateTime}, and {@code ZonedDateTime}. Other
 * values (text, {@code LocalDate}, enums, ...) match by {@code equals}. {@code null} is
 * never a key: a null join value matches nothing.
 */
final class JoinKeys {

    private JoinKeys() {
    }

    static Object normalize(Object value) {
        return switch (value) {
            case null -> null;
            case Integer i -> i;
            case Long l -> wholeNumber(l);
            case Short s -> (int) s;
            case Byte b -> (int) b;
            case Double d -> floatingNumber(d, Double.toString(d));
            case Float f -> floatingNumber(f, Float.toString(f));
            case BigInteger i -> decimal(new BigDecimal(i));
            case BigDecimal d -> decimal(d);
            case Date date -> date.toInstant();
            case OffsetDateTime odt -> odt.toInstant();
            case ZonedDateTime zdt -> zdt.toInstant();
            case Character c -> String.valueOf(c);
            default -> value;
        };
    }

    private static Object wholeNumber(long value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (Object) (int) value : (Object) value;
    }

    private static Object floatingNumber(double value, String exactText) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        return decimal(new BigDecimal(exactText));
    }

    private static Object decimal(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            try {
                return wholeNumber(stripped.longValueExact());
            } catch (ArithmeticException beyondLong) {
                return stripped.toBigInteger();
            }
        }
        return stripped;
    }
}
