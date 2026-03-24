package laughing.man.commits.testutil;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Shared UTC date builders for tests.
 */
public final class TestDateFixtures {

    private TestDateFixtures() {
    }

    public static Date utcDate(int year, int month, int day) {
        return utcDate(year, month, day, 0, 0);
    }

    public static Date utcDate(int year, int month, int day, int hour, int minute) {
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
