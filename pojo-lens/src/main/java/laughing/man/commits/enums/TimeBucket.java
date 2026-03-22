package laughing.man.commits.enums;

import java.util.Locale;

/**
 * Time bucket granularities for date grouping.
 */
public enum TimeBucket {
    DAY,
    WEEK,
    MONTH,
    QUARTER,
    YEAR;

    public static TimeBucket fromString(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Time bucket is required");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (TimeBucket bucket : values()) {
            if (bucket.name().equals(normalized)) {
                return bucket;
            }
        }
        throw new IllegalArgumentException("Unsupported time bucket '" + raw + "'");
    }
}

