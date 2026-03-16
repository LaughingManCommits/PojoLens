package laughing.man.commits.util;

import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.time.TimeBucketPreset;

import java.time.Instant;
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
        ZonedDateTime zonedDateTime = Instant.ofEpochMilli(((Date) rawValue).getTime()).atZone(preset.zoneId());
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

    private static String formatYearMonthDay(int year, int month, int day) {
        StringBuilder sb = new StringBuilder(10);
        appendPaddedInt(sb, year, 4);
        sb.append('-');
        appendTwoDigits(sb, month);
        sb.append('-');
        appendTwoDigits(sb, day);
        return sb.toString();
    }

    private static String formatYearWeek(int year, int week) {
        StringBuilder sb = new StringBuilder(8);
        appendPaddedInt(sb, year, 4);
        sb.append("-W");
        appendTwoDigits(sb, week);
        return sb.toString();
    }

    private static String formatYearMonth(int year, int month) {
        StringBuilder sb = new StringBuilder(7);
        appendPaddedInt(sb, year, 4);
        sb.append('-');
        appendTwoDigits(sb, month);
        return sb.toString();
    }

    private static String formatYearQuarter(int year, int quarter) {
        StringBuilder sb = new StringBuilder(7);
        appendPaddedInt(sb, year, 4);
        sb.append("-Q").append(quarter);
        return sb.toString();
    }

    private static String formatYear(int year) {
        StringBuilder sb = new StringBuilder(4);
        appendPaddedInt(sb, year, 4);
        return sb.toString();
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

