package laughing.man.commits.testutil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static laughing.man.commits.testutil.TestDateFixtures.utcDate;

/**
 * Shared chart/test projection rows and sample data builders.
 */
public final class ChartTestFixtures {

    private ChartTestFixtures() {
    }

    public static List<SalaryPoint> monthlySalaryPoints() {
        List<SalaryPoint> rows = new ArrayList<>();
        rows.add(new SalaryPoint(utcDate(2025, Calendar.JANUARY, 3), 100));
        rows.add(new SalaryPoint(utcDate(2025, Calendar.JANUARY, 20), 200));
        rows.add(new SalaryPoint(utcDate(2025, Calendar.FEBRUARY, 1), 150));
        return rows;
    }

    public static List<DepartmentSalaryPoint> departmentMonthlySalaryPoints() {
        List<DepartmentSalaryPoint> rows = new ArrayList<>();
        rows.add(new DepartmentSalaryPoint("Engineering", utcDate(2025, Calendar.JANUARY, 3), 100));
        rows.add(new DepartmentSalaryPoint("Engineering", utcDate(2025, Calendar.FEBRUARY, 1), 150));
        rows.add(new DepartmentSalaryPoint("Finance", utcDate(2025, Calendar.FEBRUARY, 5), 300));
        return rows;
    }

    public static List<SeriesMetricRow> periodSeriesRows() {
        List<SeriesMetricRow> rows = new ArrayList<>();
        rows.add(new SeriesMetricRow("Engineering", "2025-01", 300));
        rows.add(new SeriesMetricRow("Finance", "2025-01", 100));
        rows.add(new SeriesMetricRow("Engineering", "2025-02", 150));
        rows.add(new SeriesMetricRow("Finance", "2025-02", 150));
        return rows;
    }

    public static class DepartmentPayrollRow {
        public String department;
        public long payroll;

        public DepartmentPayrollRow() {
        }
    }

    public static class PeriodPayrollRow {
        public String period;
        public long payroll;

        public PeriodPayrollRow() {
        }
    }

    public static class SeriesMetricRow {
        public String department;
        public String period;
        public int payroll;

        public SeriesMetricRow() {
        }

        public SeriesMetricRow(String department, String period, int payroll) {
            this.department = department;
            this.period = period;
            this.payroll = payroll;
        }
    }

    public static class SalaryPoint {
        public java.util.Date hireDate;
        public int salary;

        public SalaryPoint() {
        }

        public SalaryPoint(java.util.Date hireDate, int salary) {
            this.hireDate = hireDate;
            this.salary = salary;
        }
    }

    public static class DepartmentSalaryPoint {
        public String department;
        public java.util.Date hireDate;
        public int salary;

        public DepartmentSalaryPoint() {
        }

        public DepartmentSalaryPoint(String department, java.util.Date hireDate, int salary) {
            this.department = department;
            this.hireDate = hireDate;
            this.salary = salary;
        }
    }

    public static class DepartmentHeadcountRow {
        public String department;
        public long headcount;

        public DepartmentHeadcountRow() {
        }
    }

    public static class DepartmentHeadcountAliasRow {
        public String dept;
        public long total;

        public DepartmentHeadcountAliasRow() {
        }
    }

    public static class DepartmentPeriodPayrollRow {
        public String department;
        public String period;
        public long payroll;

        public DepartmentPeriodPayrollRow() {
        }
    }

    public static class ScatterPoint {
        public int x;
        public int y;

        public ScatterPoint() {
        }

        public ScatterPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
