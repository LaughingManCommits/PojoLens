package laughing.man.commits.util;

import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.time.TimeBucketPreset;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Date;
import java.util.Locale;

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
                return String.format(Locale.ROOT, "%04d-%02d-%02d",
                        zonedDateTime.getYear(), zonedDateTime.getMonthValue(), zonedDateTime.getDayOfMonth());
            case WEEK:
                WeekFields weekFields = WeekFields.of(preset.weekStart(), 4);
                int year = zonedDateTime.get(weekFields.weekBasedYear());
                int week = zonedDateTime.get(weekFields.weekOfWeekBasedYear());
                return String.format(Locale.ROOT, "%04d-W%02d", year, week);
            case MONTH:
                return String.format(Locale.ROOT, "%04d-%02d", zonedDateTime.getYear(), zonedDateTime.getMonthValue());
            case QUARTER:
                int quarter = ((zonedDateTime.getMonthValue() - 1) / 3) + 1;
                return String.format(Locale.ROOT, "%04d-Q%d", zonedDateTime.getYear(), quarter);
            case YEAR:
                return String.format(Locale.ROOT, "%04d", zonedDateTime.getYear());
            default:
                throw new IllegalArgumentException("Unsupported time bucket: " + preset.bucket());
        }
    }
}

