package laughing.man.commits;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.stats.StatsTable;
import laughing.man.commits.stats.StatsViewPreset;
import laughing.man.commits.stats.StatsViewPresets;
import org.junit.jupiter.api.Test;
import laughing.man.commits.enums.Sort;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StatsDocsExamplesTest {

    @Test
    public void readmeHeadcountByDepartmentExampleShouldWork() {
        List<EmployeeStatRow> employees = employeeRows();

        List<HeadcountRow> rows = PojoLens
                .parse("select department, count(*) as headcount group by department")
                .filter(employees, HeadcountRow.class);

        List<String> normalized = rows.stream()
                .map(r -> r.department + ":" + r.headcount)
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of("Engineering:3", "Finance:2", "HR:1"), normalized);
    }

    @Test
    public void readmeSalaryTrendByMonthExampleShouldWork() {
        List<EmployeeStatRow> employees = employeeRows();

        List<TrendRow> rows = PojoLens
                .parse("select bucket(hireDate,'month') as period, sum(salary) as payroll group by period")
                .filter(employees, TrendRow.class);

        List<String> normalized = rows.stream()
                .map(r -> r.period + ":" + r.payroll)
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of("2025-01:245000", "2025-02:230000", "2025-03:170000"), normalized);
    }

    @Test
    public void readmeActiveUsersByWeekExampleShouldWork() {
        List<UserActivity> activity = userActivityRows();

        List<WeeklyActiveRow> rows = PojoLens.newQueryBuilder(activity)
                .addRule("active", true, Clauses.EQUAL, Separator.AND)
                .addTimeBucket("eventDate", TimeBucket.WEEK, "week")
                .addCount("activeUsers")
                .initFilter()
                .filter(WeeklyActiveRow.class);

        List<String> normalized = rows.stream()
                .map(r -> r.week + ":" + r.activeUsers)
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of("2025-W01:2", "2025-W02:3"), normalized);
    }

    @Test
    public void readmeTopNCategoriesByMetricExampleShouldWork() {
        List<EmployeeStatRow> employees = employeeRows();

        List<DepartmentPayrollRow> top2 = StatsViewPresets
                .topNBy("department", Metric.SUM, "salary", "payroll", 2, DepartmentPayrollRow.class)
                .rows(employees);

        assertEquals(2, top2.size());
        assertEquals("Engineering", top2.get(0).department);
        assertEquals(405000L, top2.get(0).payroll);
        assertEquals("Finance", top2.get(1).department);
        assertEquals(150000L, top2.get(1).payroll);
    }

    @Test
    public void readmeGroupedStatsTablePresetExampleShouldWork() {
        List<EmployeeStatRow> employees = employeeRows();
        StatsViewPreset<DepartmentPayrollRow> preset = StatsViewPresets.by(
                "department",
                Metric.SUM,
                "salary",
                "payroll",
                DepartmentPayrollRow.class
        );

        StatsTable<DepartmentPayrollRow> table = preset.table(employees);

        assertEquals(List.of("department", "payroll"), table.schema().names());
        assertEquals(3, table.rows().size());
        assertEquals("Engineering", table.rows().get(0).department);
        assertEquals(405000L, table.rows().get(0).payroll);
        assertEquals(645000L, ((Number) table.totals().get("payroll")).longValue());
    }

    @Test
    public void readmeFluentChartExampleShouldWork() {
        List<EmployeeStatRow> employees = employeeRows();

        ChartData chart = PojoLens.newQueryBuilder(employees)
                .addGroup("department")
                .addMetric("salary", Metric.SUM, "payroll")
                .addOrder("payroll")
                .initFilter()
                .chart(Sort.DESC,
                        DepartmentPayrollRow.class,
                        ChartSpec.of(ChartType.BAR, "department", "payroll"));

        assertEquals(3, chart.getLabels().size());
        assertEquals("Engineering", chart.getLabels().get(0));
        assertEquals(405000d, chart.getDatasets().get(0).getValues().get(0), 0.0001d);
    }

    private static List<EmployeeStatRow> employeeRows() {
        List<EmployeeStatRow> rows = new ArrayList<>();
        rows.add(new EmployeeStatRow(1, "Engineering", 120000, utcDate(2025, Calendar.JANUARY, 3)));
        rows.add(new EmployeeStatRow(2, "Engineering", 125000, utcDate(2025, Calendar.JANUARY, 20)));
        rows.add(new EmployeeStatRow(3, "Engineering", 160000, utcDate(2025, Calendar.FEBRUARY, 10)));
        rows.add(new EmployeeStatRow(4, "Finance", 70000, utcDate(2025, Calendar.FEBRUARY, 6)));
        rows.add(new EmployeeStatRow(5, "Finance", 80000, utcDate(2025, Calendar.MARCH, 1)));
        rows.add(new EmployeeStatRow(6, "HR", 90000, utcDate(2025, Calendar.MARCH, 14)));
        return rows;
    }

    private static List<UserActivity> userActivityRows() {
        List<UserActivity> rows = new ArrayList<>();
        rows.add(new UserActivity(1, utcDate(2025, Calendar.JANUARY, 2), true));
        rows.add(new UserActivity(2, utcDate(2025, Calendar.JANUARY, 5), true));
        rows.add(new UserActivity(3, utcDate(2025, Calendar.JANUARY, 9), true));
        rows.add(new UserActivity(4, utcDate(2025, Calendar.JANUARY, 10), false));
        rows.add(new UserActivity(5, utcDate(2025, Calendar.JANUARY, 11), true));
        rows.add(new UserActivity(6, utcDate(2025, Calendar.JANUARY, 12), true));
        return rows;
    }

    private static Date utcDate(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static class EmployeeStatRow {
        public int id;
        public String department;
        public int salary;
        public Date hireDate;

        public EmployeeStatRow() {
        }

        public EmployeeStatRow(int id, String department, int salary, Date hireDate) {
            this.id = id;
            this.department = department;
            this.salary = salary;
            this.hireDate = hireDate;
        }
    }

    public static class HeadcountRow {
        public String department;
        public long headcount;

        public HeadcountRow() {
        }
    }

    public static class TrendRow {
        public String period;
        public long payroll;

        public TrendRow() {
        }
    }

    public static class WeeklyActiveRow {
        public String week;
        public long activeUsers;

        public WeeklyActiveRow() {
        }
    }

    public static class DepartmentPayrollRow {
        public String department;
        public long payroll;

        public DepartmentPayrollRow() {
        }
    }

    public static class UserActivity {
        public int userId;
        public Date eventDate;
        public boolean active;

        public UserActivity() {
        }

        public UserActivity(int userId, Date eventDate, boolean active) {
            this.userId = userId;
            this.eventDate = eventDate;
            this.active = active;
        }
    }
}

