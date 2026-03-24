package laughing.man.commits.testutil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static laughing.man.commits.testutil.TestDateFixtures.utcDate;

/**
 * Shared fixture rows and builders for time-bucket aggregation tests.
 */
public final class TimeBucketTestFixtures {

    private TimeBucketTestFixtures() {
    }

    public static List<EmployeePoint> sampleRows() {
        List<EmployeePoint> rows = new ArrayList<>();
        rows.add(new EmployeePoint("Engineering", utcDate(2025, Calendar.JANUARY, 15, 10, 0), 100));
        rows.add(new EmployeePoint("Engineering", utcDate(2025, Calendar.JANUARY, 20, 12, 0), 200));
        rows.add(new EmployeePoint("Engineering", utcDate(2025, Calendar.FEBRUARY, 1, 0, 0), 150));
        rows.add(new EmployeePoint("Finance", utcDate(2025, Calendar.FEBRUARY, 5, 8, 30), 300));
        return rows;
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

    public static class NullableHireDatePoint {
        public String department;
        public Date hireDate;
        public int salary;

        public NullableHireDatePoint() {
        }

        public NullableHireDatePoint(String department, Date hireDate, int salary) {
            this.department = department;
            this.hireDate = hireDate;
            this.salary = salary;
        }
    }
}
