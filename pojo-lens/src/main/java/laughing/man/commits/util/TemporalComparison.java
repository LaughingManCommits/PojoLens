package laughing.man.commits.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

/**
 * Default-precision temporal comparison used when no explicit date format is configured:
 * values compare exactly, and text literals compare at the precision they are written.
 */
final class TemporalComparison {

    private static final int LITERAL_CACHE_MAX_ENTRIES = 256;
    private static final int ISO_DATE_LENGTH = 10;
    private static final int MILLI_DIGITS = 3;
    private static final int MICRO_DIGITS = 6;
    private static final Cache<String, Literal> LITERALS =
            Caffeine.newBuilder().maximumSize(LITERAL_CACHE_MAX_ENTRIES).build();

    private TemporalComparison() {
    }

    /**
     * Exact ordering of two temporal values: local vs local on the local timeline,
     * otherwise as instants (local values placed in the system zone).
     */
    static int compareExact(Object left, Object right) {
        if (isLocal(left) && isLocal(right)) {
            return toLocalDateTime(left).compareTo(toLocalDateTime(right));
        }
        return toInstant(left).compareTo(toInstant(right));
    }

    /**
     * Parses an ISO-8601 literal ({@code 2024-01-02}, {@code 2024-01-02T10:15},
     * {@code 2024-01-02 10:15:30}, {@code 2024-01-02T10:15:30.5Z}, offsets, zone ids);
     * {@code null} when the text is not a date literal.
     */
    static Literal parseLiteral(String text) {
        return LITERALS.get(text, TemporalComparison::parse);
    }

    private static Literal parse(String text) {
        String value = text.trim();
        try {
            if (value.length() == ISO_DATE_LENGTH) {
                return new Literal(LocalDate.parse(value).atStartOfDay(), null, ChronoUnit.DAYS);
            }
            if (value.length() > ISO_DATE_LENGTH && value.charAt(ISO_DATE_LENGTH) == ' ') {
                value = value.substring(0, ISO_DATE_LENGTH) + 'T' + value.substring(ISO_DATE_LENGTH + 1);
            }
            TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(
                    value, ZonedDateTime::from, LocalDateTime::from);
            ChronoUnit precision = precisionOf(value);
            return parsed instanceof ZonedDateTime zoned
                    ? new Literal(null, zoned.toInstant(), precision)
                    : new Literal((LocalDateTime) parsed, null, precision);
        } catch (DateTimeException | StringIndexOutOfBoundsException notADate) {
            return null;
        }
    }

    private static ChronoUnit precisionOf(String isoDateTime) {
        String time = isoDateTime.substring(ISO_DATE_LENGTH + 1);
        int end = time.length();
        for (int i = 0; i < time.length(); i++) {
            char c = time.charAt(i);
            if (c == 'Z' || c == '+' || c == '-' || c == '[') {
                end = i;
                break;
            }
        }
        time = time.substring(0, end);
        int fraction = time.indexOf('.');
        if (fraction >= 0) {
            int digits = time.length() - fraction - 1;
            return digits <= MILLI_DIGITS ? ChronoUnit.MILLIS
                    : digits <= MICRO_DIGITS ? ChronoUnit.MICROS
                    : ChronoUnit.NANOS;
        }
        return time.indexOf(':') == time.lastIndexOf(':') ? ChronoUnit.MINUTES : ChronoUnit.SECONDS;
    }

    private static boolean isLocal(Object value) {
        return value instanceof LocalDate || value instanceof LocalDateTime;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        ZoneId zone = ZoneId.systemDefault();
        return switch (value) {
            case LocalDateTime ldt -> ldt;
            case LocalDate ld -> ld.atStartOfDay();
            case Date date -> LocalDateTime.ofInstant(date.toInstant(), zone);
            case Instant instant -> LocalDateTime.ofInstant(instant, zone);
            case OffsetDateTime odt -> LocalDateTime.ofInstant(odt.toInstant(), zone);
            case ZonedDateTime zdt -> LocalDateTime.ofInstant(zdt.toInstant(), zone);
            default -> throw new DateTimeException("Not a date/time value: " + value);
        };
    }

    private static Instant toInstant(Object value) {
        ZoneId zone = ZoneId.systemDefault();
        return switch (value) {
            case Instant instant -> instant;
            case Date date -> date.toInstant();
            case OffsetDateTime odt -> odt.toInstant();
            case ZonedDateTime zdt -> zdt.toInstant();
            case LocalDateTime ldt -> ldt.atZone(zone).toInstant();
            case LocalDate ld -> ld.atStartOfDay(zone).toInstant();
            default -> throw new DateTimeException("Not a date/time value: " + value);
        };
    }

    /**
     * A parsed literal: exactly one of {@code local} (no zone in the text) or
     * {@code instant} (offset or zone in the text) is set.
     */
    record Literal(LocalDateTime local, Instant instant, ChronoUnit precision) {

        /**
         * Orders a field value against this literal after truncating the field to the
         * literal's precision, so a literal names a whole day, second, millisecond, ...
         */
        int compareField(Object fieldValue) {
            if (local != null) {
                LocalDateTime field = toLocalDateTime(fieldValue);
                LocalDateTime truncated = precision == ChronoUnit.DAYS
                        ? field.toLocalDate().atStartOfDay()
                        : field.truncatedTo(precision);
                return truncated.compareTo(local);
            }
            return toInstant(fieldValue).truncatedTo(precision).compareTo(instant);
        }
    }
}
