package laughing.man.commits.testutil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

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

    public static List<EmployeeEvent> interopEmployeeEvents() {
        List<EmployeeEvent> events = new ArrayList<>();
        String[] departments = new String[] {"Engineering", "Finance", "Operations"};
        Random random = new Random(20260301L);
        for (int month = 1; month <= 12; month++) {
            for (String department : departments) {
                int headcount = 6 + random.nextInt(8);
                int base = baseSalaryForDepartment(department);
                for (int i = 0; i < headcount; i++) {
                    int salary = base + random.nextInt(60_000) + (month * 450);
                    events.add(new EmployeeEvent(department, "2025-" + pad2(month), salary));
                }
            }
        }
        return events;
    }

    public static List<DepartmentPeriodPayrollRow> interopMonthlyDepartmentPayroll() {
        List<DepartmentPeriodPayrollRow> rows = new ArrayList<>();
        String[] departments = new String[] {"Engineering", "Finance", "Operations"};
        Random random = new Random(424242L);
        for (int month = 1; month <= 12; month++) {
            for (String department : departments) {
                int seasonal = 7_500 * ((month % 6) + 1);
                int payroll = basePayrollForDepartment(department) + seasonal + random.nextInt(85_000);
                rows.add(new DepartmentPeriodPayrollRow(department, "2025-" + pad2(month), payroll));
            }
        }
        return rows;
    }

    public static List<DepartmentPeriodPayrollRow> interopMonthlyDepartmentPayrollWithGaps() {
        List<DepartmentPeriodPayrollRow> rows = interopMonthlyDepartmentPayroll();
        rows.removeIf(row -> "Operations".equals(row.department)
                && Arrays.asList("2025-02", "2025-06", "2025-10").contains(row.period));
        return rows;
    }

    public static List<ScatterSignalRow> interopScatterSignals() {
        List<ScatterSignalRow> rows = new ArrayList<>();
        Random random = new Random(99L);
        String[] seriesNames = new String[] {"api", "db", "queue"};
        for (int i = 1; i <= 240; i++) {
            String series = seriesNames[i % 3];
            int x = i;
            double trend;
            if ("api".equals(series)) {
                trend = 18.0 + (x * 0.24);
            } else if ("db".equals(series)) {
                trend = 26.0 + (x * 0.21);
            } else {
                trend = 22.0 + (x * 0.23);
            }
            double noise = (random.nextDouble() - 0.5) * 4.0;
            rows.add(new ScatterSignalRow(x, trend + noise, series));
        }
        return rows;
    }

    private static int baseSalaryForDepartment(String department) {
        if ("Engineering".equals(department)) {
            return 110_000;
        }
        if ("Finance".equals(department)) {
            return 95_000;
        }
        return 88_000;
    }

    private static int basePayrollForDepartment(String department) {
        if ("Engineering".equals(department)) {
            return 220_000;
        }
        if ("Finance".equals(department)) {
            return 180_000;
        }
        return 160_000;
    }

    private static String pad2(int number) {
        return number < 10 ? "0" + number : String.valueOf(number);
    }

    public static class DepartmentPayrollRow {
        public String department;
        public long payroll;

        public DepartmentPayrollRow() {
        }
    }

    public static class EmployeeEvent {
        public String department;
        public String period;
        public long salary;

        public EmployeeEvent() {
        }

        public EmployeeEvent(String department, long salary) {
            this.department = department;
            this.salary = salary;
        }

        public EmployeeEvent(String department, String period, long salary) {
            this.department = department;
            this.period = period;
            this.salary = salary;
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

        public DepartmentPeriodPayrollRow(String department, String period, long payroll) {
            this.department = department;
            this.period = period;
            this.payroll = payroll;
        }
    }

    public static class PeriodSeriesPayrollRow {
        public String period;
        public String department;
        public long totalPayroll;

        public PeriodSeriesPayrollRow() {
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

    public static class ScatterSignalRow {
        public int x;
        public double y;
        public String series;

        public ScatterSignalRow() {
        }

        public ScatterSignalRow(int x, double y, String series) {
            this.x = x;
            this.y = y;
            this.series = series;
        }
    }
}


