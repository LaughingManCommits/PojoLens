package laughing.man.commits;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.stats.StatsTable;
import laughing.man.commits.stats.StatsViewPreset;
import laughing.man.commits.stats.StatsViewPresets;
import laughing.man.commits.testutil.StatsExampleFixtures.DepartmentPayrollRow;
import laughing.man.commits.testutil.StatsExampleFixtures.EmployeeStatRow;
import laughing.man.commits.testutil.StatsExampleFixtures.HeadcountRow;
import laughing.man.commits.testutil.StatsExampleFixtures.TrendRow;
import laughing.man.commits.testutil.StatsExampleFixtures.UserActivity;
import laughing.man.commits.testutil.StatsExampleFixtures.WeeklyActiveRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static laughing.man.commits.testutil.StatsExampleFixtures.employeeRows;
import static laughing.man.commits.testutil.StatsExampleFixtures.userActivityRows;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StatsDocsExamplesTest {

    @Test
    public void readmeHeadcountByDepartmentExampleShouldWork() {
        List<EmployeeStatRow> employees = employeeRows();

        List<HeadcountRow> rows = PojoLens
                .parse("select department, count(*) as headcount group by department")
                .filter(employees, HeadcountRow.class);

        List<String> normalized = rows.stream()
                .map(r -> r.department + ":" + r.headcount)
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of("Engineering:3", "Finance:2", "HR:1"), normalized);
    }

    @Test
    public void readmeSalaryTrendByMonthExampleShouldWork() {
        List<EmployeeStatRow> employees = employeeRows();

        List<TrendRow> rows = PojoLens
                .parse("select bucket(hireDate,'month') as period, sum(salary) as payroll group by period")
                .filter(employees, TrendRow.class);

        List<String> normalized = rows.stream()
                .map(r -> r.period + ":" + r.payroll)
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of("2025-01:245000", "2025-02:230000", "2025-03:170000"), normalized);
    }

    @Test
    public void readmeActiveUsersByWeekExampleShouldWork() {
        List<UserActivity> activity = userActivityRows();

        List<WeeklyActiveRow> rows = PojoLens.newQueryBuilder(activity)
                .addRule("active", true, Clauses.EQUAL, Separator.AND)
                .addTimeBucket("eventDate", TimeBucket.WEEK, "week")
                .addCount("activeUsers")
                .initFilter()
                .filter(WeeklyActiveRow.class);

        List<String> normalized = rows.stream()
                .map(r -> r.week + ":" + r.activeUsers)
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of("2025-W01:2", "2025-W02:3"), normalized);
    }

    @Test
    public void readmeTopNCategoriesByMetricExampleShouldWork() {
        List<EmployeeStatRow> employees = employeeRows();

        List<DepartmentPayrollRow> top2 = StatsViewPresets
                .topNBy("department", Metric.SUM, "salary", "payroll", 2, DepartmentPayrollRow.class)
                .rows(employees);

        assertEquals(2, top2.size());
        assertEquals("Engineering", top2.get(0).department);
        assertEquals(405000L, top2.get(0).payroll);
        assertEquals("Finance", top2.get(1).department);
        assertEquals(150000L, top2.get(1).payroll);
    }

    @Test
    public void readmeGroupedStatsTablePresetExampleShouldWork() {
        List<EmployeeStatRow> employees = employeeRows();
        StatsViewPreset<DepartmentPayrollRow> preset = StatsViewPresets.by(
                "department",
                Metric.SUM,
                "salary",
                "payroll",
                DepartmentPayrollRow.class
        );

        StatsTable<DepartmentPayrollRow> table = preset.table(employees);

        assertEquals(List.of("department", "payroll"), table.schema().names());
        assertEquals(3, table.rows().size());
        assertEquals("Engineering", table.rows().get(0).department);
        assertEquals(405000L, table.rows().get(0).payroll);
        assertEquals(645000L, ((Number) table.totals().get("payroll")).longValue());
    }

    @Test
    public void readmeFluentChartExampleShouldWork() {
        List<EmployeeStatRow> employees = employeeRows();

        ChartData chart = PojoLens.newQueryBuilder(employees)
                .addGroup("department")
                .addMetric("salary", Metric.SUM, "payroll")
                .addOrder("payroll")
                .initFilter()
                .chart(Sort.DESC,
                        DepartmentPayrollRow.class,
                        ChartSpec.of(ChartType.BAR, "department", "payroll"));

        assertEquals(3, chart.getLabels().size());
        assertEquals("Engineering", chart.getLabels().get(0));
        assertEquals(405000d, chart.getDatasets().get(0).getValues().get(0), 0.0001d);
    }
}

