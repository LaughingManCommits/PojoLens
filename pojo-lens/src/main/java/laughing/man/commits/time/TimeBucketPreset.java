package laughing.man.commits.time;

import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.util.StringUtil;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable time-bucket preset with explicit calendar behavior.
 */
public final class TimeBucketPreset {

    private static final ZoneId DEFAULT_ZONE = ZoneOffset.UTC;
    private static final DayOfWeek DEFAULT_WEEK_START = DayOfWeek.MONDAY;

    private final TimeBucket bucket;
    private final ZoneId zoneId;
    private final DayOfWeek weekStart;

    private TimeBucketPreset(TimeBucket bucket, ZoneId zoneId, DayOfWeek weekStart) {
        this.bucket = Objects.requireNonNull(bucket, "bucket must not be null");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId must not be null");
        this.weekStart = Objects.requireNonNull(weekStart, "weekStart must not be null");
    }

    public static TimeBucketPreset of(TimeBucket bucket) {
        return new TimeBucketPreset(requireBucket(bucket), DEFAULT_ZONE, DEFAULT_WEEK_START);
    }

    public static TimeBucketPreset day() {
        return of(TimeBucket.DAY);
    }

    public static TimeBucketPreset week() {
        return of(TimeBucket.WEEK);
    }

    public static TimeBucketPreset month() {
        return of(TimeBucket.MONTH);
    }

    public static TimeBucketPreset quarter() {
        return of(TimeBucket.QUARTER);
    }

    public static TimeBucketPreset year() {
        return of(TimeBucket.YEAR);
    }

    public static TimeBucketPreset parse(String bucketValue, String zoneValue, String weekStartValue) {
        TimeBucket bucket = TimeBucket.fromString(bucketValue);
        ZoneId zoneId = zoneValue == null || zoneValue.isBlank()
                ? DEFAULT_ZONE
                : parseZoneId(zoneValue);
        DayOfWeek weekStart = weekStartValue == null || weekStartValue.isBlank()
                ? DEFAULT_WEEK_START
                : parseDayOfWeek(weekStartValue);
        if (bucket != TimeBucket.WEEK && weekStartValue != null && !weekStartValue.isBlank()) {
            throw new IllegalArgumentException("Week start is only supported for week time buckets");
        }
        return new TimeBucketPreset(bucket, zoneId, weekStart);
    }

    public TimeBucket bucket() {
        return bucket;
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    public DayOfWeek weekStart() {
        return weekStart;
    }

    public TimeBucketPreset withZone(ZoneId value) {
        return new TimeBucketPreset(bucket, Objects.requireNonNull(value, "zoneId must not be null"), weekStart);
    }

    public TimeBucketPreset withZone(String value) {
        return withZone(parseZoneId(value));
    }

    public TimeBucketPreset withWeekStart(DayOfWeek value) {
        if (bucket != TimeBucket.WEEK) {
            throw new IllegalStateException("Week start is only supported for week time buckets");
        }
        return new TimeBucketPreset(bucket, zoneId, Objects.requireNonNull(value, "weekStart must not be null"));
    }

    public TimeBucketPreset withWeekStart(String value) {
        return withWeekStart(parseDayOfWeek(value));
    }

    public String explainToken() {
        return bucket.name() + ":" + zoneId.getId() + ":" + weekStart.name();
    }

    public String sqlArgumentList() {
        StringBuilder sb = new StringBuilder()
                .append('\'')
                .append(bucket.name().toLowerCase(Locale.ROOT))
                .append('\'');
        if (!DEFAULT_ZONE.equals(zoneId) || (bucket == TimeBucket.WEEK && !DEFAULT_WEEK_START.equals(weekStart))) {
            sb.append(',')
                    .append('\'')
                    .append(zoneId.getId())
                    .append('\'');
        }
        if (bucket == TimeBucket.WEEK && !DEFAULT_WEEK_START.equals(weekStart)) {
            sb.append(',')
                    .append('\'')
                    .append(weekStart.name().toLowerCase(Locale.ROOT))
                    .append('\'');
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TimeBucketPreset that)) {
            return false;
        }
        return bucket == that.bucket
                && zoneId.equals(that.zoneId)
                && weekStart == that.weekStart;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucket, zoneId, weekStart);
    }

    @Override
    public String toString() {
        return explainToken();
    }

    private static TimeBucket requireBucket(TimeBucket bucket) {
        if (bucket == null) {
            throw new IllegalArgumentException("bucket must not be null");
        }
        return bucket;
    }

    private static ZoneId parseZoneId(String value) {
        if (StringUtil.isNullOrBlank(value)) {
            throw new IllegalArgumentException("Time zone is required");
        }
        try {
            return ZoneId.of(value.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unsupported time zone '" + value + "'");
        }
    }

    private static DayOfWeek parseDayOfWeek(String value) {
        if (StringUtil.isNullOrBlank(value)) {
            throw new IllegalArgumentException("Week start is required");
        }
        try {
            return DayOfWeek.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unsupported week start '" + value + "'");
        }
    }
}

