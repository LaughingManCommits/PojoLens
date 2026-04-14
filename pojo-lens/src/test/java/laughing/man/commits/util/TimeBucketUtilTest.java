package laughing.man.commits.util;

import laughing.man.commits.time.TimeBucketPreset;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.DayOfWeek;
import java.util.Calendar;
import java.util.Date;

import static laughing.man.commits.testutil.TestDateFixtures.utcDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TimeBucketUtilTest {

    @Test
    public void bucketValueShouldFormatCommonPresetsWithoutFormatterRoundTrip() {
        Date input = utcDate(2025, Calendar.FEBRUARY, 3, 10, 15);

        assertEquals("2025-02-03", TimeBucketUtil.bucketValue(input, TimeBucketPreset.day()));
        assertEquals("2025-02", TimeBucketUtil.bucketValue(input, TimeBucketPreset.month()));
        assertEquals("2025-Q1", TimeBucketUtil.bucketValue(input, TimeBucketPreset.quarter()));
        assertEquals("2025", TimeBucketUtil.bucketValue(input, TimeBucketPreset.year()));
    }

    @Test
    public void bucketValueShouldRespectConfiguredWeekStart() {
        Date input = utcDate(2025, Calendar.JANUARY, 5, 10, 0);

        assertEquals("2025-W01", TimeBucketUtil.bucketValue(input, TimeBucketPreset.week()));
        assertEquals(
                "2025-W02",
                TimeBucketUtil.bucketValue(input, TimeBucketPreset.week().withWeekStart(DayOfWeek.SUNDAY))
        );
    }

    @Test
    public void bucketValueShouldAcceptSupportedJavaTimeInputs() {
        Instant instant = utcDate(2025, Calendar.FEBRUARY, 3, 10, 15).toInstant();

        assertEquals("2025-02-03", TimeBucketUtil.bucketValue(instant, TimeBucketPreset.day()));
        assertEquals("2025-02-03", TimeBucketUtil.bucketValue(LocalDate.of(2025, 2, 3), TimeBucketPreset.day()));
        assertEquals("2025-02-03", TimeBucketUtil.bucketValue(LocalDateTime.of(2025, 2, 3, 10, 15), TimeBucketPreset.day()));
        assertEquals("2025-02-03", TimeBucketUtil.bucketValue(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC), TimeBucketPreset.day()));
        assertEquals("2025-02-03", TimeBucketUtil.bucketValue(ZonedDateTime.ofInstant(instant, ZoneId.of("UTC")), TimeBucketPreset.day()));
    }

    @Test
    public void bucketValueShouldNormalizeInstantBasedInputsToPresetTimezone() {
        Instant boundary = Instant.parse("2025-01-31T23:30:00Z");
        TimeBucketPreset preset = TimeBucketPreset.month().withZone("Europe/Amsterdam");

        assertEquals("2025-02", TimeBucketUtil.bucketValue(boundary, preset));
        assertEquals("2025-02", TimeBucketUtil.bucketValue(OffsetDateTime.ofInstant(boundary, ZoneOffset.UTC), preset));
        assertEquals("2025-02", TimeBucketUtil.bucketValue(ZonedDateTime.ofInstant(boundary, ZoneId.of("UTC")), preset));
    }

    @Test
    public void bucketValueShouldUsePresetTimezoneAsLocalInterpretationForLocalDateTime() {
        LocalDateTime boundary = LocalDateTime.of(2025, 1, 31, 23, 30);

        assertEquals("2025-01", TimeBucketUtil.bucketValue(boundary, TimeBucketPreset.month().withZone("Europe/Amsterdam")));
    }

    @Test
    public void bucketValueShouldRejectUnsupportedTypes() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> TimeBucketUtil.bucketValue("2025-02-03", TimeBucketPreset.day()));

        assertEquals(
                "Time bucket requires java.util.Date, Instant, LocalDate, LocalDateTime, OffsetDateTime, or ZonedDateTime values",
                ex.getMessage()
        );
    }
}


