package laughing.man.commits.dsl;

import laughing.man.commits.enums.TimeBucket;

import java.util.Objects;

/**
 * Structural description of a configured typed time bucket.
 */
public final class TypedPlanTimeBucket {

    private final String dateField;
    private final TimeBucket bucket;
    private final String alias;
    private final String presetToken;

    public TypedPlanTimeBucket(String dateField, TimeBucket bucket, String alias, String presetToken) {
        this.dateField = Objects.requireNonNull(dateField, "dateField must not be null");
        this.bucket = Objects.requireNonNull(bucket, "bucket must not be null");
        this.alias = Objects.requireNonNull(alias, "alias must not be null");
        this.presetToken = Objects.requireNonNull(presetToken, "presetToken must not be null");
    }

    public String dateField() {
        return dateField;
    }

    public TimeBucket bucket() {
        return bucket;
    }

    public String alias() {
        return alias;
    }

    public String presetToken() {
        return presetToken;
    }
}
