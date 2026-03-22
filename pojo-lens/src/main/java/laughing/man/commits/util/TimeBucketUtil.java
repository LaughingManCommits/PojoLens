package laughing.man.commits.util;

import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.time.TimeBucketPreset;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Date;

/**
 * Shared formatter for deterministic UTC time buckets.
 */
public final class TimeBucketUtil {

    private TimeBucketUtil() {
    }

    public static String bucketValue(Object rawValue, TimeBucket bucket) {
        return bucketValue(rawValue, TimeBucketPreset.of(bucket));
    }

    public static String bucketValue(Object rawValue, TimeBucketPreset preset) {
        if (rawValue == null) {
            return null;
        }
        if (!(rawValue instanceof Date)) {
            throw new IllegalArgumentException("Time bucket requires java.util.Date values");
        }
        if (preset == null) {
            throw new IllegalArgumentException("preset must not be null");
        }
        long epochMillis = ((Date) rawValue).getTime();
        if (ZoneOffset.UTC.equals(preset.zoneId())) {
            return bucketValueUtc(epochMillis, preset);
        }
        ZonedDateTime zonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(preset.zoneId());
        switch (preset.bucket()) {
            case DAY:
                return formatYearMonthDay(
                        zonedDateTime.getYear(),
                        zonedDateTime.getMonthValue(),
                        zonedDateTime.getDayOfMonth()
                );
            case WEEK:
                WeekFields weekFields = WeekFields.of(preset.weekStart(), 4);
                int year = zonedDateTime.get(weekFields.weekBasedYear());
                int week = zonedDateTime.get(weekFields.weekOfWeekBasedYear());
                return formatYearWeek(year, week);
            case MONTH:
                return formatYearMonth(zonedDateTime.getYear(), zonedDateTime.getMonthValue());
            case QUARTER:
                int quarter = ((zonedDateTime.getMonthValue() - 1) / 3) + 1;
                return formatYearQuarter(zonedDateTime.getYear(), quarter);
            case YEAR:
                return formatYear(zonedDateTime.getYear());
            default:
                throw new IllegalArgumentException("Unsupported time bucket: " + preset.bucket());
        }
    }

    private static String bucketValueUtc(long epochMillis, TimeBucketPreset preset) {
        LocalDate date = LocalDate.ofEpochDay(Math.floorDiv(epochMillis, 86_400_000L));
        switch (preset.bucket()) {
            case DAY:
                return formatYearMonthDay(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
            case WEEK:
                WeekFields weekFields = WeekFields.of(preset.weekStart(), 4);
                int year = date.get(weekFields.weekBasedYear());
                int week = date.get(weekFields.weekOfWeekBasedYear());
                return formatYearWeek(year, week);
            case MONTH:
                return formatYearMonth(date.getYear(), date.getMonthValue());
            case QUARTER:
                int quarter = ((date.getMonthValue() - 1) / 3) + 1;
                return formatYearQuarter(date.getYear(), quarter);
            case YEAR:
                return formatYear(date.getYear());
            default:
                throw new IllegalArgumentException("Unsupported time bucket: " + preset.bucket());
        }
    }

    private static String formatYearMonthDay(int year, int month, int day) {
        if (year >= 1000 && year <= 9999) {
            byte[] buf = new byte[10];
            writeYear(buf, 0, year);
            buf[4] = '-';
            writeTwoDigits(buf, 5, month);
            buf[7] = '-';
            writeTwoDigits(buf, 8, day);
            return new String(buf, 0, 10, StandardCharsets.ISO_8859_1);
        }
        StringBuilder sb = new StringBuilder(10);
        appendPaddedInt(sb, year, 4);
        sb.append('-');
        appendTwoDigits(sb, month);
        sb.append('-');
        appendTwoDigits(sb, day);
        return sb.toString();
    }

    private static String formatYearWeek(int year, int week) {
        if (year >= 1000 && year <= 9999) {
            byte[] buf = new byte[8];
            writeYear(buf, 0, year);
            buf[4] = '-';
            buf[5] = 'W';
            writeTwoDigits(buf, 6, week);
            return new String(buf, 0, 8, StandardCharsets.ISO_8859_1);
        }
        StringBuilder sb = new StringBuilder(8);
        appendPaddedInt(sb, year, 4);
        sb.append("-W");
        appendTwoDigits(sb, week);
        return sb.toString();
    }

    private static String formatYearMonth(int year, int month) {
        if (year >= 1000 && year <= 9999) {
            byte[] buf = new byte[7];
            writeYear(buf, 0, year);
            buf[4] = '-';
            writeTwoDigits(buf, 5, month);
            return new String(buf, 0, 7, StandardCharsets.ISO_8859_1);
        }
        StringBuilder sb = new StringBuilder(7);
        appendPaddedInt(sb, year, 4);
        sb.append('-');
        appendTwoDigits(sb, month);
        return sb.toString();
    }

    private static String formatYearQuarter(int year, int quarter) {
        if (year >= 1000 && year <= 9999) {
            byte[] buf = new byte[7];
            writeYear(buf, 0, year);
            buf[4] = '-';
            buf[5] = 'Q';
            buf[6] = (byte) ('0' + quarter);
            return new String(buf, 0, 7, StandardCharsets.ISO_8859_1);
        }
        StringBuilder sb = new StringBuilder(7);
        appendPaddedInt(sb, year, 4);
        sb.append("-Q").append(quarter);
        return sb.toString();
    }

    private static String formatYear(int year) {
        if (year >= 1000 && year <= 9999) {
            byte[] buf = new byte[4];
            writeYear(buf, 0, year);
            return new String(buf, 0, 4, StandardCharsets.ISO_8859_1);
        }
        StringBuilder sb = new StringBuilder(4);
        appendPaddedInt(sb, year, 4);
        return sb.toString();
    }

    private static void writeYear(byte[] buf, int offset, int year) {
        buf[offset]     = (byte) ('0' + year / 1000);
        buf[offset + 1] = (byte) ('0' + (year / 100) % 10);
        buf[offset + 2] = (byte) ('0' + (year / 10) % 10);
        buf[offset + 3] = (byte) ('0' + year % 10);
    }

    private static void writeTwoDigits(byte[] buf, int offset, int value) {
        buf[offset]     = value < 10 ? (byte) '0' : (byte) ('0' + value / 10);
        buf[offset + 1] = (byte) ('0' + value % 10);
    }

    private static void appendTwoDigits(StringBuilder sb, int value) {
        if (value >= 0 && value < 10) {
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

