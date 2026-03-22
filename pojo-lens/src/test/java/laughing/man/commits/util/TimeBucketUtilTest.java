package laughing.man.commits.util;

import laughing.man.commits.time.TimeBucketPreset;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

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

    private static Date utcDate(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}
