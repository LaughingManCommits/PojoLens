package laughing.man.commits.fluent;

import laughing.man.commits.PojoLens;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AggregationFluentTest {

    @Test
    public void fluentMetricsShouldComputeCorrectGlobalValues() {
        List<Employee> employees = sampleEmployees();

        List<EmployeeStats> stats = PojoLens.newQueryBuilder(employees)
                .addRule("active", true, Clauses.EQUAL, Separator.AND)
                .addCount("employeeCount")
                .addMetric("salary", Metric.SUM, "totalSalary")
                .addMetric("salary", Metric.AVG, "avgSalary")
                .addMetric("salary", Metric.MIN, "minSalary")
                .addMetric("salary", Metric.MAX, "maxSalary")
                .initFilter()
                .filter(EmployeeStats.class);

        assertEquals(1, stats.size());
        EmployeeStats row = stats.get(0);
        assertEquals(3L, row.employeeCount);
        assertEquals(340000L, row.totalSalary);
        assertEquals(113333.3333d, row.avgSalary, 0.001d);
        assertEquals(90000, row.minSalary);
        assertEquals(130000, row.maxSalary);
    }

    @Test
    public void metricAliasShouldProjectIntoDtoFields() {
        List<Employee> employees = sampleEmployees();

        List<AliasStats> stats = PojoLens.newQueryBuilder(employees)
                .addRule("department", "Engineering", Clauses.EQUAL, Separator.AND)
                .addCount("engineerCount")
                .addMetric("salary", Metric.SUM, "engineeringPayroll")
                .initFilter()
                .filter(AliasStats.class);

        assertEquals(1, stats.size());
        assertEquals(3L, stats.get(0).engineerCount);
        assertEquals(360000L, stats.get(0).engineeringPayroll);
    }

    @Test
    public void numericMetricValidationShouldRejectNonNumericAndUnknownFields() {
        List<Employee> employees = sampleEmployees();

        try {
            PojoLens.newQueryBuilder(employees)
                    .addMetric("department", Metric.SUM, "departmentSum");
            fail("Expected IllegalArgumentException for non-numeric metric field");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("requires numeric field"));
        }

        try {
            PojoLens.newQueryBuilder(employees)
                    .addMetric("missingSalary", Metric.MAX, "maxSalary");
            fail("Expected IllegalArgumentException for unknown metric field");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown metric field"));
        }
    }

    @Test
    public void numericMetricValidationShouldUseDeclaredFieldTypeWhenValuesAreNull() {
        List<NullableSalaryEmployee> employees = List.of(
                new NullableSalaryEmployee("Engineering", null)
        );

        assertDoesNotThrow(() -> PojoLens.newQueryBuilder(employees)
                .addMetric("salary", Metric.SUM, "totalSalary"));
    }

    public static class EmployeeStats {
        public long employeeCount;
        public long totalSalary;
        public double avgSalary;
        public int minSalary;
        public int maxSalary;

        public EmployeeStats() {
        }
    }

    public static class AliasStats {
        public long engineerCount;
        public long engineeringPayroll;

        public AliasStats() {
        }
    }

    public static class NullableSalaryEmployee {
        public String department;
        public Integer salary;

        public NullableSalaryEmployee() {
        }

        public NullableSalaryEmployee(String department, Integer salary) {
            this.department = department;
            this.salary = salary;
        }
    }
}

