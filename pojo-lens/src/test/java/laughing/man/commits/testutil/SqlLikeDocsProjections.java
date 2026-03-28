package laughing.man.commits.testutil;

public final class SqlLikeDocsProjections {

    private SqlLikeDocsProjections() {
    }

    public static class DepartmentHeadcount {
        public String department;
        public long headcount;

        public DepartmentHeadcount() {
        }
    }

    public static class DepartmentHeadcountByAlias {
        public String dept;
        public long headcount;

        public DepartmentHeadcountByAlias() {
        }
    }

    public static class PeriodHeadcount {
        public String period;
        public long headcount;

        public PeriodHeadcount() {
        }
    }

    public static class DepartmentSalaryRank {
        public String dept;
        public String name;
        public int salary;
        public long rn;

        public DepartmentSalaryRank() {
        }
    }

    public static class DepartmentDenseRank {
        public String dept;
        public String name;
        public int salary;
        public long dr;

        public DepartmentDenseRank() {
        }
    }

    public static class DepartmentRunningTotal {
        public String dept;
        public String name;
        public int salary;
        public long runningTotal;

        public DepartmentRunningTotal() {
        }
    }
}


