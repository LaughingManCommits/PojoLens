package laughing.man.commits.examples.spring.boot.quickstart;

public final class QuickstartEmployeeTypes {

    private QuickstartEmployeeTypes() {
    }

    public static final class Employee {
        public long id;
        public String name;
        public String department;
        public int salary;

        public Employee() {
        }

        public Employee(long id, String name, String department, int salary) {
            this.id = id;
            this.name = name;
            this.department = department;
            this.salary = salary;
        }
    }

    public static final class EmployeeView {
        public long id;
        public String name;
        public String department;
        public int salary;

        public EmployeeView() {
        }

        public EmployeeView(long id, String name, String department, int salary) {
            this.id = id;
            this.name = name;
            this.department = department;
            this.salary = salary;
        }
    }

    public static final class RuntimeInfo {
        public boolean strictParameterTypes;
        public boolean lintMode;
        public boolean sqlLikeCacheEnabled;
        public boolean statsPlanCacheEnabled;

        public RuntimeInfo() {
        }

        public RuntimeInfo(boolean strictParameterTypes,
                           boolean lintMode,
                           boolean sqlLikeCacheEnabled,
                           boolean statsPlanCacheEnabled) {
            this.strictParameterTypes = strictParameterTypes;
            this.lintMode = lintMode;
            this.sqlLikeCacheEnabled = sqlLikeCacheEnabled;
            this.statsPlanCacheEnabled = statsPlanCacheEnabled;
        }
    }
}
