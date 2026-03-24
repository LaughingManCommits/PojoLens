package laughing.man.commits.fluent;

import laughing.man.commits.PojoLens;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AggregationGroupByFluentTest {

    @Test
    public void groupedMetricsShouldWorkForSingleGroupKey() {
        List<Employee> employees = sampleEmployees();

        List<DepartmentStats> stats = PojoLens.newQueryBuilder(employees)
                .addGroup("department")
                .addOrder("department", 1)
                .addCount("employeeCount")
                .addMetric("salary", Metric.SUM, "totalSalary")
                .initFilter()
                .filter(Sort.ASC, DepartmentStats.class);

        assertEquals(2, stats.size());
        assertEquals("Engineering", stats.get(0).department);
        assertEquals(3L, stats.get(0).employeeCount);
        assertEquals(360000L, stats.get(0).totalSalary);
        assertEquals("Finance", stats.get(1).department);
        assertEquals(1L, stats.get(1).employeeCount);
        assertEquals(90000L, stats.get(1).totalSalary);
    }

    @Test
    public void groupedMetricsShouldComputeAllAggregatesInOneProjection() {
        List<Employee> employees = sampleEmployees();

        List<DepartmentAllStats> stats = PojoLens.newQueryBuilder(employees)
                .addGroup("department")
                .addOrder("department", 1)
                .addCount("employeeCount")
                .addMetric("salary", Metric.SUM, "totalSalary")
                .addMetric("salary", Metric.AVG, "avgSalary")
                .addMetric("salary", Metric.MIN, "minSalary")
                .addMetric("salary", Metric.MAX, "maxSalary")
                .initFilter()
                .filter(Sort.ASC, DepartmentAllStats.class);

        assertEquals(2, stats.size());
        assertEquals("Engineering", stats.get(0).department);
        assertEquals(3L, stats.get(0).employeeCount);
        assertEquals(360000L, stats.get(0).totalSalary);
        assertEquals(120000.0d, stats.get(0).avgSalary, 0.0001d);
        assertEquals(110000, stats.get(0).minSalary);
        assertEquals(130000, stats.get(0).maxSalary);

        assertEquals("Finance", stats.get(1).department);
        assertEquals(1L, stats.get(1).employeeCount);
        assertEquals(90000L, stats.get(1).totalSalary);
        assertEquals(90000.0d, stats.get(1).avgSalary, 0.0001d);
        assertEquals(90000, stats.get(1).minSalary);
        assertEquals(90000, stats.get(1).maxSalary);
    }

    @Test
    public void groupedMetricsShouldWorkForMultipleGroupKeys() {
        List<Employee> employees = sampleEmployees();

        List<DepartmentActiveStats> stats = PojoLens.newQueryBuilder(employees)
                .addGroup("department", 1)
                .addGroup("active", 2)
                .addOrder("department", 1)
                .addOrder("active", 2)
                .addCount("employeeCount")
                .initFilter()
                .filter(Sort.ASC, DepartmentActiveStats.class);

        assertEquals(3, stats.size());
        assertGroup(stats, "Engineering", false, 1L);
        assertGroup(stats, "Engineering", true, 2L);
        assertGroup(stats, "Finance", true, 1L);
    }

    @Test
    public void groupedMetricsShouldHandleNullGroupFieldsAndNullAggregates() {
        List<NullableSalaryRecord> rows = new ArrayList<>();
        rows.add(new NullableSalaryRecord(null, 100));
        rows.add(new NullableSalaryRecord(null, null));
        rows.add(new NullableSalaryRecord("HR", null));

        List<NullableDepartmentStats> stats = PojoLens.newQueryBuilder(rows)
                .addGroup("department")
                .addCount("rowCount")
                .addMetric("salary", Metric.AVG, "avgSalary")
                .initFilter()
                .filter(NullableDepartmentStats.class);

        assertEquals(2, stats.size());
        NullableDepartmentStats nullDepartment = find(stats, null);
        assertEquals(2L, nullDepartment.rowCount);
        assertEquals(100.0d, nullDepartment.avgSalary, 0.0001d);

        NullableDepartmentStats hrDepartment = find(stats, "HR");
        assertEquals(1L, hrDepartment.rowCount);
        assertNull(hrDepartment.avgSalary);
    }

    @Test
    public void noGroupMetricsShouldRemainGlobalRollup() {
        List<Employee> employees = sampleEmployees();

        List<GlobalRollupStats> stats = PojoLens.newQueryBuilder(employees)
                .addCount("employeeCount")
                .addMetric("salary", Metric.MAX, "maxSalary")
                .initFilter()
                .filter(GlobalRollupStats.class);

        assertEquals(1, stats.size());
        assertEquals(4L, stats.get(0).employeeCount);
        assertEquals(130000, stats.get(0).maxSalary);
    }

    @Test
    public void groupedMetricsShouldSupportHavingOrderAndLimit() {
        List<Employee> employees = sampleEmployees();

        List<DepartmentStats> stats = PojoLens.newQueryBuilder(employees)
                .addGroup("department")
                .addCount("employeeCount")
                .addHaving("employeeCount", 2, Clauses.BIGGER_EQUAL)
                .addOrder("employeeCount")
                .limit(1)
                .initFilter()
                .filter(Sort.DESC, DepartmentStats.class);

        assertEquals(1, stats.size());
        assertEquals("Engineering", stats.get(0).department);
        assertEquals(3L, stats.get(0).employeeCount);
    }

    @Test
    public void globalRollupShouldSupportHavingOnAggregateAlias() {
        List<Employee> employees = sampleEmployees();

        List<GlobalRollupStats> stats = PojoLens.newQueryBuilder(employees)
                .addCount("employeeCount")
                .addHaving("employeeCount", 3, Clauses.BIGGER_EQUAL)
                .initFilter()
                .filter(GlobalRollupStats.class);

        assertEquals(1, stats.size());
        assertEquals(4L, stats.get(0).employeeCount);
    }

    @Test
    public void groupedMetricsShouldOrderByMetricAliasWithoutHaving() {
        List<Employee> employees = sampleEmployees();

        List<DepartmentStats> stats = PojoLens.newQueryBuilder(employees)
                .addGroup("department")
                .addCount("employeeCount")
                .addMetric("salary", Metric.SUM, "totalSalary")
                .addOrder("totalSalary", 1)
                .limit(1)
                .initFilter()
                .filter(Sort.DESC, DepartmentStats.class);

        assertEquals(1, stats.size());
        assertEquals("Engineering", stats.get(0).department);
        assertEquals(360000L, stats.get(0).totalSalary);
    }

    private void assertGroup(List<DepartmentActiveStats> stats, String department, boolean active, long expectedCount) {
        for (DepartmentActiveStats row : stats) {
            if (department.equals(row.department) && active == row.active) {
                assertEquals(expectedCount, row.employeeCount);
                return;
            }
        }
        throw new AssertionError("Missing group: " + department + "/" + active);
    }

    private NullableDepartmentStats find(List<NullableDepartmentStats> rows, String department) {
        for (NullableDepartmentStats row : rows) {
            if (department == null) {
                if (row.department == null) {
                    return row;
                }
            } else if (department.equals(row.department)) {
                return row;
            }
        }
        throw new AssertionError("Missing department group: " + department);
    }

    public static class DepartmentStats {
        public String department;
        public long employeeCount;
        public long totalSalary;

        public DepartmentStats() {
        }
    }

    public static class DepartmentAllStats {
        public String department;
        public long employeeCount;
        public long totalSalary;
        public double avgSalary;
        public int minSalary;
        public int maxSalary;

        public DepartmentAllStats() {
        }
    }

    public static class DepartmentActiveStats {
        public String department;
        public boolean active;
        public long employeeCount;

        public DepartmentActiveStats() {
        }
    }

    public static class NullableSalaryRecord {
        public String department;
        public Integer salary;

        public NullableSalaryRecord() {
        }

        public NullableSalaryRecord(String department, Integer salary) {
            this.department = department;
            this.salary = salary;
        }
    }

    public static class NullableDepartmentStats {
        public String department;
        public long rowCount;
        public Double avgSalary;

        public NullableDepartmentStats() {
        }
    }

    public static class GlobalRollupStats {
        public long employeeCount;
        public int maxSalary;

        public GlobalRollupStats() {
        }
    }
}

