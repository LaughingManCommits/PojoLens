package laughing.man.commits.builder;

import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.time.TimeBucketPreset;

/**
 * Immutable descriptor for a configured time-bucket field.
 */
public final class QueryTimeBucket {

    private final String dateField;
    private final TimeBucketPreset preset;
    private final String alias;

    private QueryTimeBucket(String dateField, TimeBucketPreset preset, String alias) {
        this.dateField = dateField;
        this.preset = preset;
        this.alias = alias;
    }

    public static QueryTimeBucket of(String dateField, TimeBucket bucket, String alias) {
        return of(dateField, TimeBucketPreset.of(bucket), alias);
    }

    public static QueryTimeBucket of(String dateField, TimeBucketPreset preset, String alias) {
        return new QueryTimeBucket(dateField, preset, alias);
    }

    public String getDateField() {
        return dateField;
    }

    public TimeBucket getBucket() {
        return preset.bucket();
    }

    public TimeBucketPreset getPreset() {
        return preset;
    }

    public String getAlias() {
        return alias;
    }
}

