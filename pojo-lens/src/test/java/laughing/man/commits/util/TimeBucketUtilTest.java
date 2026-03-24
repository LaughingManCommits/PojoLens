package laughing.man.commits.util;

import laughing.man.commits.time.TimeBucketPreset;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.Calendar;
import java.util.Date;

import static laughing.man.commits.testutil.TestDateFixtures.utcDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
