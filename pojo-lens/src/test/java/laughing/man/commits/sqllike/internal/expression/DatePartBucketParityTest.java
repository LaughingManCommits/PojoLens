package laughing.man.commits.sqllike.internal.expression;

import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.util.TimeBucketUtil;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WP-29 D5 invariant: a date part equals the matching component of the time bucket for the
 * same value and zone, for every supported value type.
 */
class DatePartBucketParityTest {

    private static final List<String> ZONES = List.of(
            "UTC", "Europe/Amsterdam", "America/New_York", "Asia/Kolkata", "Pacific/Chatham", "America/Santiago");
    private static final long MIN_EPOCH_SECOND = Instant.parse("1900-01-01T00:00:00Z").getEpochSecond();
    private static final long MAX_EPOCH_SECOND = Instant.parse("2100-01-01T00:00:00Z").getEpochSecond();
    private static final int SAMPLES = 3000;

    @Test
    void datePartsMatchBucketComponents() {
        for (Object value : sampleValues()) {
            for (String zone : ZONES) {
                String context = value.getClass().getSimpleName() + " " + value + " in " + zone;
                String hour = bucket(value, "hour", zone);

                assertEquals(Integer.parseInt(bucket(value, "year", zone)), part("year", value, zone), context);
                assertEquals(Integer.parseInt(bucket(value, "quarter", zone).substring(6)), part("quarter", value, zone), context);
                assertEquals(Integer.parseInt(bucket(value, "month", zone).substring(5)), part("month", value, zone), context);
                assertEquals(Integer.parseInt(bucket(value, "day", zone).substring(8)), part("day", value, zone), context);
                assertEquals(Integer.parseInt(hour.substring(11)), part("hour", value, zone), context);
                assertEquals(Integer.parseInt(hour.substring(8, 10)), part("day", value, zone), context);
            }
        }
    }

    private static List<Object> sampleValues() {
        Random random = new Random(29);
        List<Object> values = new ArrayList<>();
        for (String edge : List.of("2026-03-29T00:59:59Z", "2026-03-29T01:00:00Z", "2026-10-25T00:30:00Z",
                "2026-11-01T05:30:00Z", "2026-09-06T03:59:00Z", "1999-12-31T23:59:59.999Z", "2000-01-01T00:00:00Z")) {
            addAllTypes(values, Instant.parse(edge));
        }
        for (int i = 0; i < SAMPLES; i++) {
            long second = MIN_EPOCH_SECOND + (long) (random.nextDouble() * (MAX_EPOCH_SECOND - MIN_EPOCH_SECOND));
            addAllTypes(values, Instant.ofEpochSecond(second, random.nextInt(1_000_000_000)));
        }
        return values;
    }

    private static void addAllTypes(List<Object> values, Instant instant) {
        values.add(instant);
        values.add(Date.from(instant));
        values.add(OffsetDateTime.ofInstant(instant, ZoneOffset.ofHours(-7)));
        values.add(instant.atZone(ZoneId.of("Asia/Tokyo")));
        values.add(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
        values.add(LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toLocalDate());
    }

    private static String bucket(Object value, String granularity, String zone) {
        return TimeBucketUtil.bucketValue(value, TimeBucketPreset.parse(granularity, zone, null));
    }

    private static Object part(String function, Object value, String zone) {
        return SqlExpressionEvaluator.evaluate(function + "(t, '" + zone + "')", identifier -> value);
    }
}
