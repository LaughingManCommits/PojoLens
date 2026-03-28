package laughing.man.commits.chart;

import java.util.Date;

final class ChartResultMapperFixtures {

    private ChartResultMapperFixtures() {
    }

    public static class DepartmentMetricRow {
        public String department;
        public String period;
        public long total;
        public long payroll;

        DepartmentMetricRow() {
        }

        DepartmentMetricRow(String department, String period, long total, long payroll) {
            this.department = department;
            this.period = period;
            this.total = total;
            this.payroll = payroll;
        }
    }

    public static class DateMetricRow {
        public Date period;
        public long payroll;

        DateMetricRow() {
        }

        DateMetricRow(Date period, long payroll) {
            this.period = period;
            this.payroll = payroll;
        }
    }

    public static class InvalidMetricRow {
        public String department;
        public String payroll;

        InvalidMetricRow() {
        }

        InvalidMetricRow(String department, String payroll) {
            this.department = department;
            this.payroll = payroll;
        }
    }

    public static class UnsupportedXRow {
        public Object x;
        public int value;

        UnsupportedXRow() {
        }

        UnsupportedXRow(Object x, int value) {
            this.x = x;
            this.value = value;
        }
    }

    public static class NullableYRow {
        public String department;
        public Long payroll;

        NullableYRow() {
        }

        NullableYRow(String department, Long payroll) {
            this.department = department;
            this.payroll = payroll;
        }
    }

    public static class ScatterRow {
        public int x;
        public int y;

        ScatterRow() {
        }

        ScatterRow(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class TypedMetricRow {
        private String department;
        private String period;
        private long payroll;

        TypedMetricRow() {
        }

        TypedMetricRow(String department, String period, long payroll) {
            this.department = department;
            this.period = period;
            this.payroll = payroll;
        }

        public String getDepartment() {
            return department;
        }

        public String getPeriod() {
            return period;
        }

        public long getPayroll() {
            return payroll;
        }
    }
}


