package laughing.man.commits.testutil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Shared fixture rows used by stats/chart preset and docs-example tests.
 */
public final class StatsExampleFixtures {

    private StatsExampleFixtures() {
    }

    public static List<EmployeeStatRow> employeeRows() {
        List<EmployeeStatRow> rows = new ArrayList<>();
        rows.add(new EmployeeStatRow(1, "Engineering", 120000, utcDate(2025, Calendar.JANUARY, 3)));
        rows.add(new EmployeeStatRow(2, "Engineering", 125000, utcDate(2025, Calendar.JANUARY, 20)));
        rows.add(new EmployeeStatRow(3, "Engineering", 160000, utcDate(2025, Calendar.FEBRUARY, 10)));
        rows.add(new EmployeeStatRow(4, "Finance", 70000, utcDate(2025, Calendar.FEBRUARY, 6)));
        rows.add(new EmployeeStatRow(5, "Finance", 80000, utcDate(2025, Calendar.MARCH, 1)));
        rows.add(new EmployeeStatRow(6, "HR", 90000, utcDate(2025, Calendar.MARCH, 14)));
        return rows;
    }

    public static List<UserActivity> userActivityRows() {
        List<UserActivity> rows = new ArrayList<>();
        rows.add(new UserActivity(1, utcDate(2025, Calendar.JANUARY, 2), true));
        rows.add(new UserActivity(2, utcDate(2025, Calendar.JANUARY, 5), true));
        rows.add(new UserActivity(3, utcDate(2025, Calendar.JANUARY, 9), true));
        rows.add(new UserActivity(4, utcDate(2025, Calendar.JANUARY, 10), false));
        rows.add(new UserActivity(5, utcDate(2025, Calendar.JANUARY, 11), true));
        rows.add(new UserActivity(6, utcDate(2025, Calendar.JANUARY, 12), true));
        return rows;
    }

    public static Date utcDate(int year, int month, int day) {
        return TestDateFixtures.utcDate(year, month, day);
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

    public static class PeriodHeadcountRow {
        public String period;
        public long headcount;

        public PeriodHeadcountRow() {
        }
    }

    public static class DepartmentActiveBreakdownRow {
        public boolean active;
        public String department;
        public long headcount;

        public DepartmentActiveBreakdownRow() {
        }
    }

    public static class SummaryRow {
        public long total;

        public SummaryRow() {
        }
    }

    public static class DepartmentTotalRow {
        public String department;
        public long total;

        public DepartmentTotalRow() {
        }
    }
}
