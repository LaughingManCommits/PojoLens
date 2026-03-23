package laughing.man.commits;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SqlLikeWindowFunctionTest {

    @Test
    public void rowNumberShouldPartitionByAndOrderWithinEachPartition() {
        List<WindowEmployee> source = Arrays.asList(
                new WindowEmployee(1, "Alice", "Engineering", 120000, true),
                new WindowEmployee(2, "Bob", "Engineering", 120000, true),
                new WindowEmployee(3, "Cara", "Engineering", 110000, true),
                new WindowEmployee(4, "Dan", "Finance", 125000, true),
                new WindowEmployee(5, "Erin", "Finance", 100000, true)
        );

        List<WindowRowNumberProjection> rows = PojoLens
                .parse("select department as dept, name, salary, "
                        + "row_number() over (partition by department order by salary desc) as rn "
                        + "where active = true order by dept asc, rn asc")
                .filter(source, WindowRowNumberProjection.class);

        assertEquals(5, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals("Alice", rows.get(0).name);
        assertEquals(1L, rows.get(0).rn);
        assertEquals("Bob", rows.get(1).name);
        assertEquals(2L, rows.get(1).rn);
        assertEquals("Cara", rows.get(2).name);
        assertEquals(3L, rows.get(2).rn);
        assertEquals("Finance", rows.get(3).dept);
        assertEquals("Dan", rows.get(3).name);
        assertEquals(1L, rows.get(3).rn);
    }

    @Test
    public void rankAndDenseRankShouldHandleTies() {
        List<WindowEmployee> source = Arrays.asList(
                new WindowEmployee(1, "Alice", "Engineering", 120000, true),
                new WindowEmployee(2, "Bob", "Engineering", 120000, true),
                new WindowEmployee(3, "Cara", "Engineering", 130000, true),
                new WindowEmployee(4, "Dan", "Finance", 110000, true)
        );

        List<WindowRankProjection> rows = PojoLens
                .parse("select name, salary, "
                        + "rank() over (order by salary desc) as rk, "
                        + "dense_rank() over (order by salary desc) as dr "
                        + "where active = true order by rk asc, name asc")
                .filter(source, WindowRankProjection.class);

        assertEquals(4, rows.size());
        assertEquals("Cara", rows.get(0).name);
        assertEquals(1L, rows.get(0).rk);
        assertEquals(1L, rows.get(0).dr);
        assertEquals("Alice", rows.get(1).name);
        assertEquals(2L, rows.get(1).rk);
        assertEquals(2L, rows.get(1).dr);
        assertEquals("Bob", rows.get(2).name);
        assertEquals(2L, rows.get(2).rk);
        assertEquals(2L, rows.get(2).dr);
        assertEquals("Dan", rows.get(3).name);
        assertEquals(4L, rows.get(3).rk);
        assertEquals(3L, rows.get(3).dr);
    }

    @Test
    public void queryOrderByWindowAliasShouldApplyAfterWindowComputation() {
        List<WindowEmployee> source = Arrays.asList(
                new WindowEmployee(1, "Alice", "Engineering", 120000, true),
                new WindowEmployee(2, "Cara", "Engineering", 130000, true),
                new WindowEmployee(3, "Dan", "Finance", 100000, true),
                new WindowEmployee(4, "Erin", "Finance", 125000, true),
                new WindowEmployee(5, "Hank", "HR", 90000, true)
        );

        List<WindowRowNumberProjection> rows = PojoLens
                .parse("select department as dept, name, salary, "
                        + "row_number() over (partition by department order by salary desc) as rn "
                        + "where active = true order by rn asc, name asc limit 3")
                .filter(source, WindowRowNumberProjection.class);

        assertEquals(3, rows.size());
        assertEquals("Cara", rows.get(0).name);
        assertEquals("Erin", rows.get(1).name);
        assertEquals("Hank", rows.get(2).name);
        assertEquals(1L, rows.get(0).rn);
        assertEquals(1L, rows.get(1).rn);
        assertEquals(1L, rows.get(2).rn);
    }

    @Test
    public void windowFunctionWithoutOrderByShouldFailValidation() {
        List<WindowEmployee> source = Arrays.asList(
                new WindowEmployee(1, "Alice", "Engineering", 120000, true),
                new WindowEmployee(2, "Bob", "Engineering", 120000, true)
        );

        try {
            PojoLens
                    .parse("select name, row_number() over (partition by department) as rn where active = true")
                    .filter(source, WindowRowNumberProjection.class);
            fail("Expected validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Window SELECT expressions require OVER(... ORDER BY ...)"));
        }
    }

    @Test
    public void qualifyShouldFilterByWindowAlias() {
        List<WindowEmployee> source = Arrays.asList(
                new WindowEmployee(1, "Alice", "Engineering", 120000, true),
                new WindowEmployee(2, "Bob", "Engineering", 120000, true),
                new WindowEmployee(3, "Cara", "Engineering", 130000, true),
                new WindowEmployee(4, "Dan", "Finance", 110000, true),
                new WindowEmployee(5, "Erin", "Finance", 100000, true)
        );

        List<WindowRowNumberProjection> rows = PojoLens
                .parse("select department as dept, name, salary, "
                        + "row_number() over (partition by department order by salary desc) as rn "
                        + "where active = true qualify rn <= 1 order by dept asc")
                .filter(source, WindowRowNumberProjection.class);

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals("Cara", rows.get(0).name);
        assertEquals(1L, rows.get(0).rn);
        assertEquals("Finance", rows.get(1).dept);
        assertEquals("Dan", rows.get(1).name);
        assertEquals(1L, rows.get(1).rn);
    }

    @Test
    public void qualifyShouldSupportDirectWindowExpressionReference() {
        List<WindowEmployee> source = Arrays.asList(
                new WindowEmployee(1, "Alice", "Engineering", 120000, true),
                new WindowEmployee(2, "Bob", "Engineering", 120000, true),
                new WindowEmployee(3, "Cara", "Engineering", 130000, true),
                new WindowEmployee(4, "Dan", "Finance", 110000, true),
                new WindowEmployee(5, "Erin", "Finance", 100000, true)
        );

        List<WindowRowNumberProjection> rows = PojoLens
                .parse("select department as dept, name, salary, "
                        + "row_number() over (partition by department order by salary desc) as rn "
                        + "where active = true "
                        + "qualify row_number() over (partition by department order by salary desc) <= 1 "
                        + "order by dept asc")
                .filter(source, WindowRowNumberProjection.class);

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals("Cara", rows.get(0).name);
        assertEquals("Finance", rows.get(1).dept);
        assertEquals("Dan", rows.get(1).name);
    }

    @Test
    public void qualifyShouldRejectUnknownReference() {
        List<WindowEmployee> source = Arrays.asList(
                new WindowEmployee(1, "Alice", "Engineering", 120000, true),
                new WindowEmployee(2, "Bob", "Engineering", 120000, true)
        );

        try {
            PojoLens
                    .parse("select department as dept, name, salary, "
                            + "row_number() over (partition by department order by salary desc) as rn "
                            + "where active = true qualify missingRank <= 1")
                    .filter(source, WindowRowNumberProjection.class);
            fail("Expected validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown field 'missingRank' in QUALIFY clause"));
        }
    }

    @Test
    public void aggregateWindowsShouldComputeRunningMetricsWithExpectedTypesAndNullHandling() {
        List<WindowMetricInput> source = Arrays.asList(
                new WindowMetricInput("A", 1, 10),
                new WindowMetricInput("A", 2, null),
                new WindowMetricInput("A", 3, 5),
                new WindowMetricInput("B", 1, 2),
                new WindowMetricInput("B", 2, 3),
                new WindowMetricInput("C", 1, null)
        );

        List<WindowAggregateProjection> rows = PojoLens
                .parse("select department, seq, amount, "
                        + "sum(amount) over (partition by department order by seq asc "
                        + "rows between unbounded preceding and current row) as runningSum, "
                        + "count(amount) over (partition by department order by seq asc "
                        + "rows between unbounded preceding and current row) as runningCount, "
                        + "count(*) over (partition by department order by seq asc "
                        + "rows between unbounded preceding and current row) as runningCountAll, "
                        + "avg(amount) over (partition by department order by seq asc "
                        + "rows between unbounded preceding and current row) as runningAvg, "
                        + "min(amount) over (partition by department order by seq asc "
                        + "rows between unbounded preceding and current row) as runningMin, "
                        + "max(amount) over (partition by department order by seq asc "
                        + "rows between unbounded preceding and current row) as runningMax "
                        + "order by department asc, seq asc")
                .filter(source, WindowAggregateProjection.class);

        assertEquals(6, rows.size());

        assertEquals(10L, rows.get(0).runningSum);
        assertEquals(1L, rows.get(0).runningCount);
        assertEquals(1L, rows.get(0).runningCountAll);
        assertEquals(10D, rows.get(0).runningAvg);
        assertEquals(10, rows.get(0).runningMin);
        assertEquals(10, rows.get(0).runningMax);

        assertEquals(10L, rows.get(1).runningSum);
        assertEquals(1L, rows.get(1).runningCount);
        assertEquals(2L, rows.get(1).runningCountAll);
        assertEquals(10D, rows.get(1).runningAvg);
        assertEquals(10, rows.get(1).runningMin);
        assertEquals(10, rows.get(1).runningMax);

        assertEquals(15L, rows.get(2).runningSum);
        assertEquals(2L, rows.get(2).runningCount);
        assertEquals(3L, rows.get(2).runningCountAll);
        assertEquals(7.5D, rows.get(2).runningAvg);
        assertEquals(5, rows.get(2).runningMin);
        assertEquals(10, rows.get(2).runningMax);

        assertEquals(2L, rows.get(3).runningSum);
        assertEquals(1L, rows.get(3).runningCount);
        assertEquals(1L, rows.get(3).runningCountAll);
        assertEquals(2D, rows.get(3).runningAvg);
        assertEquals(2, rows.get(3).runningMin);
        assertEquals(2, rows.get(3).runningMax);

        assertEquals(5L, rows.get(4).runningSum);
        assertEquals(2L, rows.get(4).runningCount);
        assertEquals(2L, rows.get(4).runningCountAll);
        assertEquals(2.5D, rows.get(4).runningAvg);
        assertEquals(2, rows.get(4).runningMin);
        assertEquals(3, rows.get(4).runningMax);

        assertNull(rows.get(5).runningSum);
        assertEquals(0L, rows.get(5).runningCount);
        assertEquals(1L, rows.get(5).runningCountAll);
        assertNull(rows.get(5).runningAvg);
        assertNull(rows.get(5).runningMin);
        assertNull(rows.get(5).runningMax);
    }

    @Test
    public void aggregateWindowShouldRejectUnsupportedFrameExpression() {
        List<WindowMetricInput> source = Arrays.asList(
                new WindowMetricInput("A", 1, 10),
                new WindowMetricInput("A", 2, 20)
        );

        try {
            PojoLens
                    .parse("select department, "
                            + "sum(amount) over (partition by department order by seq asc "
                            + "rows between 1 preceding and current row) as runningSum")
                    .filter(source, WindowAggregateProjection.class);
            fail("Expected parse error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unsupported window frame expression"));
        }
    }

    public static class WindowEmployee {
        public int id;
        public String name;
        public String department;
        public int salary;
        public Date hireDate;
        public boolean active;

        public WindowEmployee() {
        }

        public WindowEmployee(int id, String name, String department, int salary, boolean active) {
            this.id = id;
            this.name = name;
            this.department = department;
            this.salary = salary;
            this.active = active;
            this.hireDate = new Date();
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

    public static class WindowAggregateProjection {
        public String department;
        public int seq;
        public Integer amount;
        public Long runningSum;
        public Long runningCount;
        public Long runningCountAll;
        public Double runningAvg;
        public Integer runningMin;
        public Integer runningMax;

        public WindowAggregateProjection() {
        }
    }
}
