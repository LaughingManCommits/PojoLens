package laughing.man.commits.testutil;

public final class PublicApiModels {

    private PublicApiModels() {
    }

    public static class StatsRow {
        public String department;
        public long total;

        public StatsRow() {
        }
    }

    public static class DepartmentMetricInput {
        public String department;
        public int amount;

        public DepartmentMetricInput() {
        }

        public DepartmentMetricInput(String department, int amount) {
            this.department = department;
            this.amount = amount;
        }

        public String getDepartment() {
            return department;
        }

        public int getAmount() {
            return amount;
        }
    }

    public static class DepartmentMetricResult {
        public String department;
        public long totalAmount;

        public DepartmentMetricResult() {
        }

        public String getDepartment() {
            return department;
        }

        public long getTotalAmount() {
            return totalAmount;
        }
    }

    public static class JoinParent {
        public int id;
        public String name;

        public JoinParent() {
        }

        public JoinParent(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }
    }

    public static class JoinChild {
        public int parentId;
        public String tag;

        public JoinChild() {
        }

        public JoinChild(int parentId, String tag) {
            this.parentId = parentId;
            this.tag = tag;
        }

        public int getParentId() {
            return parentId;
        }
    }

    public static class JoinProjection {
        public int id;
        public String name;
        public int parentId;
        public String tag;

        public JoinProjection() {
        }
    }

    public static class WindowRankRow {
        public String department;
        public String name;
        public long rn;

        public WindowRankRow() {
        }
    }

    public static class WindowAggregateInput {
        public String department;
        public int seq;
        public int amount;

        public WindowAggregateInput() {
        }

        public WindowAggregateInput(String department, int seq, int amount) {
            this.department = department;
            this.seq = seq;
            this.amount = amount;
        }
    }

    public static class WindowAggregateApiRow {
        public String department;
        public int seq;
        public long runningSum;
        public long runningRows;

        public WindowAggregateApiRow() {
        }
    }

    public static class SqlLikeRunningTotalRow {
        public String dept;
        public String name;
        public int salary;
        public long runningTotal;

        public SqlLikeRunningTotalRow() {
        }
    }

    public static class ComputedSalaryRow {
        public String name;
        public double adjustedSalary;

        public ComputedSalaryRow() {
        }
    }
}


