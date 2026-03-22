package laughing.man.commits.testutil;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public final class BusinessFixtures {

    private BusinessFixtures() {
    }

    public static List<Employee> sampleEmployees() {
        Date now = new Date();
        return Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, false)
        );
    }

    public static List<Company> sampleCompanies() {
        return Arrays.asList(
                new Company(1, "Acme"),
                new Company(2, "Globex")
        );
    }

    public static List<CompanyEmployee> sampleCompanyEmployees() {
        return Arrays.asList(
                new CompanyEmployee(1, "Engineer"),
                new CompanyEmployee(2, "Analyst")
        );
    }

    public static class Employee {
        public int id;
        public String name;
        public String department;
        public int salary;
        public Date hireDate;
        public boolean active;

        public Employee() {
        }

        public Employee(int id, String name, String department, int salary, Date hireDate, boolean active) {
            this.id = id;
            this.name = name;
            this.department = department;
            this.salary = salary;
            this.hireDate = hireDate;
            this.active = active;
        }
    }

    public static class EmployeeSummary {
        public String employeeName;
        public int annualSalary;

        public EmployeeSummary() {
        }
    }

    public static class Company {
        public int id;
        public String name;

        public Company() {
        }

        public Company(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class CompanyEmployee {
        public int companyId;
        public String title;

        public CompanyEmployee() {
        }

        public CompanyEmployee(int companyId, String title) {
            this.companyId = companyId;
            this.title = title;
        }
    }
}

