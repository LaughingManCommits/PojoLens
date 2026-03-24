package laughing.man.commits.testutil;

public final class CommonStatsProjections {

    private CommonStatsProjections() {
    }

    public static class DepartmentCount {
        public String department;
        public long total;

        public DepartmentCount() {
        }
    }

    public static class DepartmentCountAlias {
        public String dept;
        public long total;

        public DepartmentCountAlias() {
        }
    }

    public static class DepartmentCountRow {
        public String department;
        public long total;

        public DepartmentCountRow() {
        }
    }
}
