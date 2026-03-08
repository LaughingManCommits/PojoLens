package laughing.man.commits;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.time.TimeBucketPreset;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.time.DayOfWeek;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimeBucketAggregationTest {

    @Test
    public void fluentTimeBucketShouldGroupByMonthWithRegularGroupFields() {
        List<EmployeePoint> rows = sampleRows();

        List<DepartmentPeriodAgg> result = PojoLens.newQueryBuilder(rows)
                .addGroup("department")
                .addTimeBucket("hireDate", TimeBucket.MONTH, "period")
                .addCount("total")
                .addMetric("salary", Metric.SUM, "payroll")
                .initFilter()
                .filter(DepartmentPeriodAgg.class);

        assertEquals(List.of(
                        "Engineering|2025-01|2|300",
                        "Engineering|2025-02|1|150",
                        "Finance|2025-02|1|300"
                ),
                normalized(result));
    }

    @Test
    public void timeBucketShouldBeTimezoneStableInUtc() {
        List<EmployeePoint> rows = sampleRows();
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            List<DepartmentPeriodAgg> honolulu = PojoLens.newQueryBuilder(rows)
                    .addGroup("department")
                    .addTimeBucket("hireDate", TimeBucket.MONTH, "period")
                    .addCount("total")
                    .initFilter()
                    .filter(DepartmentPeriodAgg.class);

            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            List<DepartmentPeriodAgg> berlin = PojoLens.newQueryBuilder(rows)
                    .addGroup("department")
                    .addTimeBucket("hireDate", TimeBucket.MONTH, "period")
                    .addCount("total")
                    .initFilter()
                    .filter(DepartmentPeriodAgg.class);

            assertEquals(normalized(honolulu), normalized(berlin));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void sqlLikeBucketFunctionShouldMatchFluentOutput() {
        List<EmployeePoint> rows = sampleRows();

        List<DepartmentPeriodAgg> sqlLike = PojoLens
                .parse("select department, bucket(hireDate,'month') as period, count(*) as total, sum(salary) as payroll "
                        + "group by department, period")
                .filter(rows, DepartmentPeriodAgg.class);

        assertEquals(List.of(
                        "Engineering|2025-01|2|300",
                        "Engineering|2025-02|1|150",
                        "Finance|2025-02|1|300"
                ),
                normalized(sqlLike));
    }

    @Test
    public void timeBucketPresetShouldRespectExplicitTimezoneBoundaries() {
        List<EmployeePoint> rows = new ArrayList<>();
        rows.add(new EmployeePoint("Engineering", utcDate(2025, Calendar.JANUARY, 31, 23, 30), 100));
        rows.add(new EmployeePoint("Engineering", utcDate(2025, Calendar.FEBRUARY, 1, 0, 30), 200));

        List<DepartmentPeriodAgg> fluent = PojoLens.newQueryBuilder(rows)
                .addGroup("department")
                .addTimeBucket("hireDate", TimeBucketPreset.month().withZone("Europe/Amsterdam"), "period")
                .addCount("total")
                .initFilter()
                .filter(DepartmentPeriodAgg.class);

        List<DepartmentPeriodAgg> sqlLike = PojoLens
                .parse("select department, bucket(hireDate,'month','Europe/Amsterdam') as period, count(*) as total "
                        + "group by department, period")
                .filter(rows, DepartmentPeriodAgg.class);

        assertEquals(List.of("Engineering|2025-02|2|0"), normalized(fluent));
        assertEquals(normalized(fluent), normalized(sqlLike));
    }

    @Test
    public void weekPresetShouldRespectConfiguredWeekStart() {
        List<EmployeePoint> rows = new ArrayList<>();
        rows.add(new EmployeePoint("Engineering", utcDate(2025, Calendar.JANUARY, 5, 10, 0), 100));
        rows.add(new EmployeePoint("Engineering", utcDate(2025, Calendar.JANUARY, 6, 10, 0), 200));

        List<DepartmentPeriodAgg> mondayStart = PojoLens.newQueryBuilder(rows)
                .addTimeBucket("hireDate", TimeBucket.WEEK, "period")
                .addCount("total")
                .initFilter()
                .filter(DepartmentPeriodAgg.class);

        List<DepartmentPeriodAgg> sundayStart = PojoLens.newQueryBuilder(rows)
                .addTimeBucket("hireDate", TimeBucketPreset.week().withWeekStart(DayOfWeek.SUNDAY), "period")
                .addCount("total")
                .initFilter()
                .filter(DepartmentPeriodAgg.class);

        assertEquals(List.of("null|2025-W01|1|0", "null|2025-W02|1|0"), normalized(mondayStart));
        assertEquals(List.of("null|2025-W02|2|0"), normalized(sundayStart));
    }

    private static List<String> normalized(List<DepartmentPeriodAgg> rows) {
        return rows.stream()
                .map(r -> r.department + "|" + r.period + "|" + r.total + "|" + r.payroll)
                .sorted()
                .collect(Collectors.toList());
    }

    private static List<EmployeePoint> sampleRows() {
        List<EmployeePoint> rows = new ArrayList<>();
        rows.add(new EmployeePoint("Engineering", utcDate(2025, Calendar.JANUARY, 15, 10, 0), 100));
        rows.add(new EmployeePoint("Engineering", utcDate(2025, Calendar.JANUARY, 20, 12, 0), 200));
        rows.add(new EmployeePoint("Engineering", utcDate(2025, Calendar.FEBRUARY, 1, 0, 0), 150));
        rows.add(new EmployeePoint("Finance", utcDate(2025, Calendar.FEBRUARY, 5, 8, 30), 300));
        return rows;
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

    public static class EmployeePoint {
        public String department;
        public Date hireDate;
        public int salary;

        public EmployeePoint() {
        }

        public EmployeePoint(String department, Date hireDate, int salary) {
            this.department = department;
            this.hireDate = hireDate;
            this.salary = salary;
        }
    }

    public static class DepartmentPeriodAgg {
        public String department;
        public String period;
        public long total;
        public long payroll;

        public DepartmentPeriodAgg() {
        }
    }
}

