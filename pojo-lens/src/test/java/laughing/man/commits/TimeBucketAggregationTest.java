package laughing.man.commits;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.testutil.TimeBucketTestFixtures.DepartmentPeriodAgg;
import laughing.man.commits.testutil.TimeBucketTestFixtures.EmployeePoint;
import laughing.man.commits.testutil.TimeBucketTestFixtures.NullableHireDatePoint;
import laughing.man.commits.time.TimeBucketPreset;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static laughing.man.commits.testutil.TestDateFixtures.utcDate;
import static laughing.man.commits.testutil.TimeBucketTestFixtures.sampleRows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimeBucketAggregationTest {

    @Test
    public void fluentTimeBucketShouldGroupByMonthWithRegularGroupFields() {
        List<EmployeePoint> rows = sampleRows();

        List<DepartmentPeriodAgg> result = PojoLensCore.newQueryBuilder(rows)
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
            List<DepartmentPeriodAgg> honolulu = PojoLensCore.newQueryBuilder(rows)
                    .addGroup("department")
                    .addTimeBucket("hireDate", TimeBucket.MONTH, "period")
                    .addCount("total")
                    .initFilter()
                    .filter(DepartmentPeriodAgg.class);

            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            List<DepartmentPeriodAgg> berlin = PojoLensCore.newQueryBuilder(rows)
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

        List<DepartmentPeriodAgg> sqlLike = PojoLensSql.parse("select department, bucket(hireDate,'month') as period, count(*) as total, sum(salary) as payroll "
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
    public void fluentTimeBucketShouldAcceptInstantInputFields() {
        List<InstantEmployeePoint> rows = sampleInstantRows();

        List<DepartmentPeriodAgg> result = PojoLensCore.newQueryBuilder(rows)
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
    public void sqlLikeBucketFunctionShouldAcceptInstantInputFields() {
        List<InstantEmployeePoint> rows = sampleInstantRows();

        List<DepartmentPeriodAgg> sqlLike = PojoLensSql.parse("select department, bucket(hireDate,'month') as period, count(*) as total, sum(salary) as payroll "
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

        List<DepartmentPeriodAgg> fluent = PojoLensCore.newQueryBuilder(rows)
                .addGroup("department")
                .addTimeBucket("hireDate", TimeBucketPreset.month().withZone("Europe/Amsterdam"), "period")
                .addCount("total")
                .initFilter()
                .filter(DepartmentPeriodAgg.class);

        List<DepartmentPeriodAgg> sqlLike = PojoLensSql.parse("select department, bucket(hireDate,'month','Europe/Amsterdam') as period, count(*) as total "
                        + "group by department, period")
                .filter(rows, DepartmentPeriodAgg.class);

        assertEquals(List.of("Engineering|2025-02|2|0"), normalized(fluent));
        assertEquals(normalized(fluent), normalized(sqlLike));
    }

    @Test
    public void localDateTimeBucketsShouldUsePresetTimezoneAsLocalInterpretation() {
        List<LocalDateTimeEmployeePoint> rows = new ArrayList<>();
        rows.add(new LocalDateTimeEmployeePoint("Engineering", LocalDateTime.of(2025, 1, 31, 23, 30), 100));
        rows.add(new LocalDateTimeEmployeePoint("Engineering", LocalDateTime.of(2025, 2, 1, 0, 30), 200));

        List<DepartmentPeriodAgg> fluent = PojoLensCore.newQueryBuilder(rows)
                .addGroup("department")
                .addTimeBucket("hireDate", TimeBucketPreset.month().withZone("Europe/Amsterdam"), "period")
                .addCount("total")
                .initFilter()
                .filter(DepartmentPeriodAgg.class);

        assertEquals(List.of("Engineering|2025-01|1|0", "Engineering|2025-02|1|0"), normalized(fluent));
    }

    @Test
    public void weekPresetShouldRespectConfiguredWeekStart() {
        List<EmployeePoint> rows = new ArrayList<>();
        rows.add(new EmployeePoint("Engineering", utcDate(2025, Calendar.JANUARY, 5, 10, 0), 100));
        rows.add(new EmployeePoint("Engineering", utcDate(2025, Calendar.JANUARY, 6, 10, 0), 200));

        List<DepartmentPeriodAgg> mondayStart = PojoLensCore.newQueryBuilder(rows)
                .addTimeBucket("hireDate", TimeBucket.WEEK, "period")
                .addCount("total")
                .initFilter()
                .filter(DepartmentPeriodAgg.class);

        List<DepartmentPeriodAgg> sundayStart = PojoLensCore.newQueryBuilder(rows)
                .addTimeBucket("hireDate", TimeBucketPreset.week().withWeekStart(DayOfWeek.SUNDAY), "period")
                .addCount("total")
                .initFilter()
                .filter(DepartmentPeriodAgg.class);

        assertEquals(List.of("null|2025-W01|1|0", "null|2025-W02|1|0"), normalized(mondayStart));
        assertEquals(List.of("null|2025-W02|2|0"), normalized(sundayStart));
    }

    @Test
    public void timeBucketValidationShouldUseDeclaredDateTypeWhenValuesAreNull() {
        List<NullableHireDatePoint> rows = List.of(
                new NullableHireDatePoint("Engineering", null, 100)
        );

        assertDoesNotThrow(() -> PojoLensCore.newQueryBuilder(rows)
                .addTimeBucket("hireDate", TimeBucket.MONTH, "period"));
    }

    @Test
    public void timeBucketValidationShouldUseDeclaredInstantTypeWhenValuesAreNull() {
        List<NullableInstantHireDatePoint> rows = List.of(
                new NullableInstantHireDatePoint("Engineering", null, 100)
        );

        assertDoesNotThrow(() -> PojoLensCore.newQueryBuilder(rows)
                .addTimeBucket("hireDate", TimeBucket.MONTH, "period"));
    }

    private static List<String> normalized(List<DepartmentPeriodAgg> rows) {
        return rows.stream()
                .map(r -> r.department + "|" + r.period + "|" + r.total + "|" + r.payroll)
                .sorted()
                .collect(Collectors.toList());
    }

    private static List<InstantEmployeePoint> sampleInstantRows() {
        return sampleRows().stream()
                .map(row -> new InstantEmployeePoint(row.department, row.hireDate.toInstant(), row.salary))
                .collect(Collectors.toList());
    }

    public static class InstantEmployeePoint {
        public String department;
        public Instant hireDate;
        public int salary;

        public InstantEmployeePoint(String department, Instant hireDate, int salary) {
            this.department = department;
            this.hireDate = hireDate;
            this.salary = salary;
        }
    }

    public static class LocalDateTimeEmployeePoint {
        public String department;
        public LocalDateTime hireDate;
        public int salary;

        public LocalDateTimeEmployeePoint(String department, LocalDateTime hireDate, int salary) {
            this.department = department;
            this.hireDate = hireDate;
            this.salary = salary;
        }
    }

    public static class NullableInstantHireDatePoint {
        public String department;
        public Instant hireDate;
        public int salary;

        public NullableInstantHireDatePoint(String department, Instant hireDate, int salary) {
            this.department = department;
            this.hireDate = hireDate;
            this.salary = salary;
        }
    }
}
