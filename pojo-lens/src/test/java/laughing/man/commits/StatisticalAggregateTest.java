package laughing.man.commits;

import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.report.ReportComparisons;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-30: MEDIAN, PERCENTILE, STDDEV/STDDEV_POP, and VARIANCE/VAR_POP.
 *
 * <p>Sample salaries: 90000, 110000, 120000, 130000 (mean 112500).</p>
 */
class StatisticalAggregateTest {

    private static final double DELTA = 1e-6;
    private static final TypedField<Employee, String> DEPARTMENT = TypedField.of("department", String.class);
    private static final TypedField<Employee, Integer> SALARY = TypedField.of("salary", Integer.class);

    @Test
    void globalStatisticsMatchHandComputedValues() {
        Stats stats = PojoLensSql.parse("select median(salary) as median, percentile(salary, 0.9) as p90, "
                        + "stddev(salary) as stddev, stddev_pop(salary) as stddevPop, "
                        + "variance(salary) as variance, var_pop(salary) as varPop")
                .filter(sampleEmployees(), Stats.class).get(0);

        assertEquals(115000d, stats.median, DELTA);
        // rank 0.9 * 3 = 2.7: 120000 + 0.7 * 10000
        assertEquals(127000d, stats.p90, DELTA);
        assertEquals(291666666.6666667, stats.variance, 1e-3);
        assertEquals(218750000d, stats.varPop, DELTA);
        assertEquals(Math.sqrt(291666666.6666667), stats.stddev, 1e-6);
        assertEquals(Math.sqrt(218750000d), stats.stddevPop, DELTA);
    }

    @Test
    void fastStatsPathAndFilteredPathAgree() {
        Stats fast = PojoLensSql.parse("select median(salary) as median, stddev(salary) as stddev")
                .filter(sampleEmployees(), Stats.class).get(0);
        Stats filtered = PojoLensSql.parse("select median(salary) as median, stddev(salary) as stddev where id > 0")
                .filter(sampleEmployees(), Stats.class).get(0);

        assertEquals(fast.median, filtered.median, DELTA);
        assertEquals(fast.stddev, filtered.stddev, DELTA);
    }

    @Test
    void groupedStatisticsWorkInHavingAndOrderBy() {
        List<GroupStats> rows = PojoLensSql.parse("select department, median(salary) as median "
                        + "group by department having stddev(salary) > 0 order by percentile(salary, 0.5) desc")
                .filter(sampleEmployees(), GroupStats.class);

        assertEquals(1, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(120000d, rows.get(0).median, DELTA);
    }

    @Test
    void sampleStatisticsOfOneValueAreNullAndEmptyInputIsNull() {
        GroupStats finance = PojoLensSql.parse("select department, stddev(salary) as median "
                        + "group by department having department = 'Finance'")
                .filter(sampleEmployees(), GroupStats.class).get(0);
        Stats none = PojoLensSql.parse("select median(salary) as median, var_pop(salary) as varPop where id > 99")
                .filter(sampleEmployees(), Stats.class).get(0);

        assertNull(finance.median);
        assertNull(none.median);
        assertNull(none.varPop);
    }

    @Test
    void nullValuesAreSkipped() {
        List<Employee> rows = new ArrayList<>(sampleEmployees());
        rows.add(new Employee(5, "Eve", null, 0, null, true));
        List<Wages> wages = rows.stream().map(e -> new Wages(e.department, e.id == 5 ? null : (double) e.salary)).toList();

        Stats stats = PojoLensSql.parse("select median(pay) as median").filter(wages, Stats.class).get(0);

        assertEquals(115000d, stats.median, DELTA);
    }

    @Test
    void sampleAliasesAndStatisticNamesAreNotReserved() {
        Stats aliases = PojoLensSql.parse("select stddev_samp(salary) as stddev, var_samp(salary) as variance")
                .filter(sampleEmployees(), Stats.class).get(0);
        List<Named> named = PojoLensSql.parse("select median, variance where median > 1")
                .filter(List.of(new Named(2, 3)), Named.class);

        assertEquals(Math.sqrt(291666666.6666667), aliases.stddev, 1e-6);
        assertEquals(291666666.6666667, aliases.variance, 1e-3);
        assertEquals(1, named.size());
    }

    @Test
    void invalidStatisticCallsFailClearly() {
        assertTrue(parseError("select percentile(salary) as p").contains("percentile(field, 0.9)"));
        assertTrue(parseError("select percentile(salary, 90) as p").contains("from 0 to 1"));
        assertTrue(parseError("select name, median(salary) over (partition by department) as m")
                .contains("not supported as a window function"));
        IllegalArgumentException text = assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse("select median(name) as median").filter(sampleEmployees(), Stats.class));
        assertTrue(text.getMessage().contains("requires numeric field"), text::getMessage);
    }

    @Test
    void typedAndNaturalMatchSqlLike() {
        List<GroupStats> typed = TypedQuery.from(Employee.class)
                .groupBy(DEPARTMENT)
                .metric(SALARY, Metric.MEDIAN, "median")
                .percentile(SALARY, 0.9, "p90")
                .orderBy(DEPARTMENT)
                .filter(sampleEmployees(), GroupStats.class);
        var natural = PojoLensNatural.parse("show department, median of salary as median, "
                + "90th percentile of salary as p90 group by department sort by department");
        List<GroupStats> naturalRows = natural.filter(sampleEmployees(), GroupStats.class);

        assertEquals(120000d, typed.get(0).median, DELTA);
        assertEquals(128000d, typed.get(0).p90, DELTA);
        assertEquals(typed.get(0).p90, naturalRows.get(0).p90, DELTA);
        assertEquals(typed.get(1).median, naturalRows.get(1).median, DELTA);
        assertTrue(natural.equivalentSqlLike().contains("percentile(salary, 0.9) as p90"), natural::equivalentSqlLike);
        assertThrows(IllegalArgumentException.class,
                () -> TypedQuery.from(Employee.class).metric(SALARY, Metric.PERCENTILE, "p"));
    }

    @Test
    void naturalStatisticPhrasesNeedOfSoFieldPhrasesKeepWorking() {
        Stats stats = PojoLensNatural.parse("show standard deviation of salary as stddev, "
                        + "population variance of salary as varPop")
                .filter(sampleEmployees(), Stats.class).get(0);

        assertEquals(Math.sqrt(291666666.6666667), stats.stddev, 1e-6);
        assertEquals(218750000d, stats.varPop, DELTA);
    }

    @Test
    void reportComparisonsSupportStatistics() {
        var comparison = ReportComparisons.compare(sampleEmployees(), sampleEmployees().subList(0, 2),
                "salary", Metric.MEDIAN);

        assertEquals(115000d, comparison.currentValue(), DELTA);
        assertEquals(105000d, comparison.previousValue(), DELTA);
    }

    private static String parseError(String query) {
        return assertThrows(IllegalArgumentException.class, () -> PojoLensSql.parse(query)).getMessage();
    }

    static class Stats {
        Double median;
        Double p90;
        Double stddev;
        Double stddevPop;
        Double variance;
        Double varPop;

        Stats() {
        }
    }

    static class GroupStats {
        String department;
        Double median;
        Double p90;

        GroupStats() {
        }
    }

    static class Wages {
        String department;
        Double pay;

        Wages() {
        }

        Wages(String department, Double pay) {
            this.department = department;
            this.pay = pay;
        }
    }

    static class Named {
        int median;
        int variance;

        Named() {
        }

        Named(int median, int variance) {
            this.median = median;
            this.variance = variance;
        }
    }
}
