package laughing.man.commits.util;

import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.time.TimeBucketPreset;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Date;

/**
 * Shared formatter for deterministic UTC time buckets.
 */
public final class TimeBucketUtil {

    private static final String SUPPORTED_TIME_BUCKET_TYPES =
            "java.util.Date, Instant, LocalDate, LocalDateTime, OffsetDateTime, or ZonedDateTime";

    private static final int MONTHS_PER_QUARTER = 3;
    private static final int MIN_DAYS_IN_FIRST_WEEK = 4;
    private static final long MILLIS_PER_DAY = 86_400_000L;
    private static final int MIN_FOUR_DIGIT_YEAR = 1000;
    private static final int MAX_FOUR_DIGIT_YEAR = 9999;

    // formatYearMonthDay buffer layout: YYYY-MM-DD (10 chars)
    private static final int DATE_BUF_LENGTH = 10;
    private static final int DATE_BUF_SEP1_INDEX = 4;
    private static final int DATE_BUF_MONTH_OFFSET = 5;
    private static final int DATE_BUF_SEP2_INDEX = 7;
    private static final int DATE_BUF_DAY_OFFSET = 8;

    // formatYearWeek buffer layout: YYYY-Www (8 chars)
    private static final int WEEK_BUF_LENGTH = 8;
    private static final int WEEK_BUF_SEP_INDEX = 4;
    private static final int WEEK_BUF_W_INDEX = 5;
    private static final int WEEK_BUF_WEEK_OFFSET = 6;

    // formatYearMonth buffer layout: YYYY-MM (7 chars)
    private static final int MONTH_BUF_LENGTH = 7;
    private static final int MONTH_BUF_SEP_INDEX = 4;
    private static final int MONTH_BUF_MONTH_OFFSET = 5;

    // formatYearQuarter buffer layout: YYYY-Qn (7 chars)
    private static final int QUARTER_BUF_LENGTH = 7;
    private static final int QUARTER_BUF_SEP_INDEX = 4;
    private static final int QUARTER_BUF_Q_INDEX = 5;
    private static final int QUARTER_BUF_DIGIT_INDEX = 6;

    // formatYear buffer layout: YYYY (4 chars)
    private static final int YEAR_BUF_LENGTH = 4;

    // Year digit extraction divisors and offsets
    private static final int YEAR_THOUSANDS_DIVISOR = 1000;
    private static final int YEAR_HUNDREDS_DIVISOR = 100;
    private static final int YEAR_TENS_DIVISOR = 10;
    private static final int DECIMAL_RADIX = 10;
    private static final int YEAR_UNITS_OFFSET = 3;

    private TimeBucketUtil() {
    }

    public static String bucketValue(Object rawValue, TimeBucket bucket) {
        return bucketValue(rawValue, TimeBucketPreset.of(bucket));
    }

    public static boolean supportsTimeBucketType(Class<?> rawType) {
        if (rawType == null) {
            return false;
        }
        return Date.class.isAssignableFrom(rawType)
                || Instant.class.isAssignableFrom(rawType)
                || LocalDate.class.isAssignableFrom(rawType)
                || LocalDateTime.class.isAssignableFrom(rawType)
                || OffsetDateTime.class.isAssignableFrom(rawType)
                || ZonedDateTime.class.isAssignableFrom(rawType);
    }

    public static String bucketValue(Object rawValue, TimeBucketPreset preset) {
        if (rawValue == null) {
            return null;
        }
        if (preset == null) {
            throw new IllegalArgumentException("preset must not be null");
        }
        return formatBucketDate(normalizeBucketDate(rawValue, preset), preset);
    }

    private static LocalDate normalizeBucketDate(Object rawValue, TimeBucketPreset preset) {
        if (rawValue instanceof Date date) {
            return normalizeInstantBucketDate(Instant.ofEpochMilli(date.getTime()), preset);
        }
        if (rawValue instanceof Instant instant) {
            return normalizeInstantBucketDate(instant, preset);
        }
        if (rawValue instanceof LocalDate localDate) {
            return localDate;
        }
        if (rawValue instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(preset.zoneId()).toLocalDate();
        }
        if (rawValue instanceof OffsetDateTime offsetDateTime) {
            return normalizeInstantBucketDate(offsetDateTime.toInstant(), preset);
        }
        if (rawValue instanceof ZonedDateTime zonedDateTime) {
            return normalizeInstantBucketDate(zonedDateTime.toInstant(), preset);
        }
        throw new IllegalArgumentException("Time bucket requires " + SUPPORTED_TIME_BUCKET_TYPES + " values");
    }

    private static LocalDate normalizeInstantBucketDate(Instant instant, TimeBucketPreset preset) {
        if (ZoneOffset.UTC.equals(preset.zoneId())) {
            return LocalDate.ofEpochDay(Math.floorDiv(instant.toEpochMilli(), MILLIS_PER_DAY));
        }
        return instant.atZone(preset.zoneId()).toLocalDate();
    }

    private static String formatBucketDate(LocalDate date, TimeBucketPreset preset) {
        switch (preset.bucket()) {
            case DAY:
                return formatYearMonthDay(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
            case WEEK:
                WeekFields weekFields = WeekFields.of(preset.weekStart(), MIN_DAYS_IN_FIRST_WEEK);
                int year = date.get(weekFields.weekBasedYear());
                int week = date.get(weekFields.weekOfWeekBasedYear());
                return formatYearWeek(year, week);
            case MONTH:
                return formatYearMonth(date.getYear(), date.getMonthValue());
            case QUARTER:
                int quarter = ((date.getMonthValue() - 1) / MONTHS_PER_QUARTER) + 1;
                return formatYearQuarter(date.getYear(), quarter);
            case YEAR:
                return formatYear(date.getYear());
            default:
                throw new IllegalArgumentException("Unsupported time bucket: " + preset.bucket());
        }
    }

    private static String formatYearMonthDay(int year, int month, int day) {
        if (year >= MIN_FOUR_DIGIT_YEAR && year <= MAX_FOUR_DIGIT_YEAR) {
            byte[] buf = new byte[DATE_BUF_LENGTH];
            writeYear(buf, 0, year);
            buf[DATE_BUF_SEP1_INDEX] = '-';
            writeTwoDigits(buf, DATE_BUF_MONTH_OFFSET, month);
            buf[DATE_BUF_SEP2_INDEX] = '-';
            writeTwoDigits(buf, DATE_BUF_DAY_OFFSET, day);
            return new String(buf, 0, DATE_BUF_LENGTH, StandardCharsets.ISO_8859_1);
        }
        StringBuilder sb = new StringBuilder(DATE_BUF_LENGTH);
        appendPaddedInt(sb, year, YEAR_BUF_LENGTH);
        sb.append('-');
        appendTwoDigits(sb, month);
        sb.append('-');
        appendTwoDigits(sb, day);
        return sb.toString();
    }

    private static String formatYearWeek(int year, int week) {
        if (year >= MIN_FOUR_DIGIT_YEAR && year <= MAX_FOUR_DIGIT_YEAR) {
            byte[] buf = new byte[WEEK_BUF_LENGTH];
            writeYear(buf, 0, year);
            buf[WEEK_BUF_SEP_INDEX] = '-';
            buf[WEEK_BUF_W_INDEX] = 'W';
            writeTwoDigits(buf, WEEK_BUF_WEEK_OFFSET, week);
            return new String(buf, 0, WEEK_BUF_LENGTH, StandardCharsets.ISO_8859_1);
        }
        StringBuilder sb = new StringBuilder(WEEK_BUF_LENGTH);
        appendPaddedInt(sb, year, YEAR_BUF_LENGTH);
        sb.append("-W");
        appendTwoDigits(sb, week);
        return sb.toString();
    }

    private static String formatYearMonth(int year, int month) {
        if (year >= MIN_FOUR_DIGIT_YEAR && year <= MAX_FOUR_DIGIT_YEAR) {
            byte[] buf = new byte[MONTH_BUF_LENGTH];
            writeYear(buf, 0, year);
            buf[MONTH_BUF_SEP_INDEX] = '-';
            writeTwoDigits(buf, MONTH_BUF_MONTH_OFFSET, month);
            return new String(buf, 0, MONTH_BUF_LENGTH, StandardCharsets.ISO_8859_1);
        }
        StringBuilder sb = new StringBuilder(MONTH_BUF_LENGTH);
        appendPaddedInt(sb, year, YEAR_BUF_LENGTH);
        sb.append('-');
        appendTwoDigits(sb, month);
        return sb.toString();
    }

    private static String formatYearQuarter(int year, int quarter) {
        if (year >= MIN_FOUR_DIGIT_YEAR && year <= MAX_FOUR_DIGIT_YEAR) {
            byte[] buf = new byte[QUARTER_BUF_LENGTH];
            writeYear(buf, 0, year);
            buf[QUARTER_BUF_SEP_INDEX] = '-';
            buf[QUARTER_BUF_Q_INDEX] = 'Q';
            buf[QUARTER_BUF_DIGIT_INDEX] = (byte) ('0' + quarter);
            return new String(buf, 0, QUARTER_BUF_LENGTH, StandardCharsets.ISO_8859_1);
        }
        StringBuilder sb = new StringBuilder(QUARTER_BUF_LENGTH);
        appendPaddedInt(sb, year, YEAR_BUF_LENGTH);
        sb.append("-Q").append(quarter);
        return sb.toString();
    }

    private static String formatYear(int year) {
        if (year >= MIN_FOUR_DIGIT_YEAR && year <= MAX_FOUR_DIGIT_YEAR) {
            byte[] buf = new byte[YEAR_BUF_LENGTH];
            writeYear(buf, 0, year);
            return new String(buf, 0, YEAR_BUF_LENGTH, StandardCharsets.ISO_8859_1);
        }
        StringBuilder sb = new StringBuilder(YEAR_BUF_LENGTH);
        appendPaddedInt(sb, year, YEAR_BUF_LENGTH);
        return sb.toString();
    }

    private static void writeYear(byte[] buf, int offset, int year) {
        buf[offset]     = (byte) ('0' + year / YEAR_THOUSANDS_DIVISOR);
        buf[offset + 1] = (byte) ('0' + (year / YEAR_HUNDREDS_DIVISOR) % DECIMAL_RADIX);
        buf[offset + 2] = (byte) ('0' + (year / YEAR_TENS_DIVISOR) % DECIMAL_RADIX);
        buf[offset + YEAR_UNITS_OFFSET] = (byte) ('0' + year % DECIMAL_RADIX);
    }

    private static void writeTwoDigits(byte[] buf, int offset, int value) {
        buf[offset]     = value < DECIMAL_RADIX ? (byte) '0' : (byte) ('0' + value / DECIMAL_RADIX);
        buf[offset + 1] = (byte) ('0' + value % DECIMAL_RADIX);
    }

    private static void appendTwoDigits(StringBuilder sb, int value) {
        if (value >= 0 && value < DECIMAL_RADIX) {
            sb.append('0');
        }
        sb.append(value);
    }

    private static void appendPaddedInt(StringBuilder sb, int value, int width) {
        String digits = Integer.toString(value);
        int start = digits.startsWith("-") ? 1 : 0;
        if (start == 1) {
            sb.append('-');
        }
        for (int i = digits.length() - start; i < width; i++) {
            sb.append('0');
        }
        sb.append(digits, start, digits.length());
    }
}

