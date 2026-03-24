package laughing.man.commits.testutil;

import java.util.List;

public final class WindowTestFixtures {

    private WindowTestFixtures() {
    }

    public static List<WindowMetricInput> sampleWindowMetricInputs() {
        return List.of(
                new WindowMetricInput("A", 1, 10),
                new WindowMetricInput("A", 2, null),
                new WindowMetricInput("A", 3, 5),
                new WindowMetricInput("B", 1, 2),
                new WindowMetricInput("B", 2, 3),
                new WindowMetricInput("C", 1, null)
        );
    }

    public static class DepartmentRank {
        public String department;
        public String name;
        public int salary;
        public long rn;

        public DepartmentRank() {
        }
    }

    public static class WindowEmployee {
        public int id;
        public String name;
        public String department;
        public int salary;
        public boolean active;

        public WindowEmployee() {
        }

        public WindowEmployee(int id, String name, String department, int salary, boolean active) {
            this.id = id;
            this.name = name;
            this.department = department;
            this.salary = salary;
            this.active = active;
        }
    }

    public static class WindowRowNumberProjection {
        public String dept;
        public String name;
        public int salary;
        public long rn;

        public WindowRowNumberProjection() {
        }
    }

    public static class WindowRankProjection {
        public String name;
        public int salary;
        public long rk;
        public long dr;

        public WindowRankProjection() {
        }
    }

    public static class WindowMetricInput {
        public String department;
        public int seq;
        public Integer amount;

        public WindowMetricInput() {
        }

        public WindowMetricInput(String department, int seq, Integer amount) {
            this.department = department;
            this.seq = seq;
            this.amount = amount;
        }
    }

    public static class WindowMetricProjection {
        public String department;
        public int seq;
        public Integer amount;
        public Long runningSum;
        public Long runningCount;
        public Long runningCountAll;
        public Double runningAvg;
        public Integer runningMin;
        public Integer runningMax;

        public WindowMetricProjection() {
        }
    }
}
