package laughing.man.commits.examples.spring.boot.basic;

import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartQueryPreset;
import laughing.man.commits.chart.ChartQueryPresets;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.chartjs.ChartJsAdapter;
import laughing.man.commits.chartjs.ChartJsPayload;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.ChartMode;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.CreateEmployeeRequest;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.DashboardOptions;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.DashboardPayload;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.Employee;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.EmployeeView;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.RuntimeInfo;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.StatsMode;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.StatsPayload;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.stats.StatsTablePayload;
import laughing.man.commits.stats.StatsViewPreset;
import laughing.man.commits.stats.StatsViewPresets;
import laughing.man.commits.table.TabularRows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Centralizes the PojoLens-specific example logic so the controller stays focused on HTTP concerns.
 *
 * Read next:
 * - /docs/entry-points.md
 * - /docs/stats-presets.md
 * - /docs/charts.md
 * - /docs/reports.md
 */
@Service
class EmployeeDashboardService {

    private static final String TOP_PAID_QUERY = "select id, name, department, salary "
            + "where department = :department and salary >= :minSalary "
            + "order by salary desc limit :limit";
    private static final String DIRECT_STATS_QUERY = "select department, count(*) as headcount, sum(salary) as payroll, "
            + "avg(salary) as averageSalary group by department order by payroll desc";
    private static final String DIRECT_STATS_TOTALS_QUERY = "select count(*) as headcount, sum(salary) as payroll, "
            + "avg(salary) as averageSalary";
    private static final String DIRECT_PAYROLL_CHART_QUERY = "select department, sum(salary) as value "
            + "group by department order by value desc";
    private static final String DIRECT_HEADCOUNT_CHART_QUERY = "select department, count(*) as value "
            + "group by department order by value desc";
    private static final ChartSpec PAYROLL_CHART_SPEC = ChartSpec.of(ChartType.BAR, "department", "value")
            .withTitle("Payroll by Department")
            .withAxisLabels("Department", "Payroll");
    private static final ChartSpec HEADCOUNT_CHART_SPEC = ChartSpec.of(ChartType.PIE, "department", "value")
            .withTitle("Headcount by Department");

    private final EmployeeStore employeeStore;
    private final PojoLensRuntime pojoLensRuntime;

    // The example now keeps the common dashboard flows in projection-free presets.
    // Readers can start with QueryRow-backed presets and add typed projections only when needed.
    private final StatsViewPreset<QueryRow> payrollStatsPreset;
    private final StatsViewPreset<QueryRow> top3PayrollStatsPreset;
    private final StatsViewPreset<QueryRow> summaryHeadcountPreset;
    private final ChartQueryPreset<QueryRow> payrollChartPreset;
    private final ChartQueryPreset<QueryRow> headcountChartPreset;
    private final ReportDefinition<QueryRow> payrollChartReport;
    private final ReportDefinition<QueryRow> headcountChartReport;

    EmployeeDashboardService(EmployeeStore employeeStore, PojoLensRuntime pojoLensRuntime) {
        this.employeeStore = employeeStore;
        this.pojoLensRuntime = pojoLensRuntime;
        payrollStatsPreset = StatsViewPresets.by("department", Metric.SUM, "salary", "total");
        top3PayrollStatsPreset = StatsViewPresets.topNBy("department", Metric.SUM, "salary", "total", 3);
        summaryHeadcountPreset = StatsViewPresets.summary(Metric.COUNT, null, "total");
        payrollChartPreset = ChartQueryPresets
                .categoryTotals("department", Metric.SUM, "salary", "value", ChartType.BAR)
                .mapChartSpec(spec -> spec
                        .withTitle("Payroll by Department")
                        .withAxisLabels("Department", "Payroll"));
        headcountChartPreset = ChartQueryPresets
                .categoryTotals("department", Metric.COUNT, null, "value", ChartType.PIE)
                .mapChartSpec(spec -> spec.withTitle("Headcount by Department"));
        payrollChartReport = payrollChartPreset.reportDefinition();
        headcountChartReport = headcountChartPreset.reportDefinition();
    }

    List<EmployeeView> employees() {
        return employeeViews(employeeStore.snapshot());
    }

    List<String> departments() {
        return employeeStore.departments();
    }

    EmployeeView addEmployee(CreateEmployeeRequest request) {
        String name = requiredText(request.name(), "name");
        String department = requiredText(request.department(), "department");
        int salary = requiredSalary(request.salary());
        return employeeStore.add(name, department, salary).toView();
    }

    DashboardOptions dashboardOptions() {
        return new DashboardOptions(
                StatsMode.names(),
                ChartMode.names(),
                StatsMode.details(),
                ChartMode.details(),
                StatsMode.defaultMode().name(),
                ChartMode.defaultMode().name()
        );
    }

    DashboardPayload dashboard(String statsModeValue, String chartModeValue) {
        List<Employee> employees = employeeStore.snapshot();
        StatsMode statsMode = parseStatsMode(statsModeValue);
        ChartMode chartMode = parseChartMode(chartModeValue);
        DashboardCharts charts = buildCharts(employees, chartMode);
        return new DashboardPayload(
                employeeViews(employees),
                buildStatsPayload(employees, statsMode),
                charts.payrollChart(),
                charts.headcountChart(),
                runtime(),
                dashboardOptions(),
                statsMode.name(),
                chartMode.name()
        );
    }

    List<EmployeeView> topPaid(String department, int minSalary, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 25));
        return pojoLensRuntime
                .parse(TOP_PAID_QUERY)
                .params(Map.of(
                        "department", requiredText(department, "department"),
                        "minSalary", Math.max(0, minSalary),
                        "limit", cappedLimit
                ))
                .filter(employeeStore.snapshot(), EmployeeView.class);
    }

    RuntimeInfo runtime() {
        return new RuntimeInfo(
                pojoLensRuntime.isStrictParameterTypes(),
                pojoLensRuntime.isLintMode(),
                pojoLensRuntime.sqlLikeCache().isEnabled(),
                pojoLensRuntime.statsPlanCache().isEnabled()
        );
    }

    private StatsPayload buildStatsPayload(List<Employee> employees, StatsMode mode) {
        return switch (mode) {
            case DIRECT_SQL -> directSqlStats(employees);
            case PRESET_BY_PAYROLL -> presetStats(
                    payrollStatsPreset,
                    employees,
                    mode,
                    "Stats preset: by(department, sum(salary))"
            );
            case PRESET_TOP3_PAYROLL -> presetStats(
                    top3PayrollStatsPreset,
                    employees,
                    mode,
                    "Stats preset: topNBy(department, sum(salary), 3)"
            );
            case PRESET_SUMMARY_HEADCOUNT -> presetStats(
                    summaryHeadcountPreset,
                    employees,
                    mode,
                    "Stats preset: summary(count(*))"
            );
        };
    }

    private StatsPayload directSqlStats(List<Employee> employees) {
        var rowQuery = pojoLensRuntime.parse(DIRECT_STATS_QUERY);
        var totalsQuery = pojoLensRuntime.parse(DIRECT_STATS_TOTALS_QUERY);
        List<QueryRow> rows = rowQuery.filter(employees, QueryRow.class);
        return new StatsPayload(
                StatsMode.DIRECT_SQL.name(),
                "Direct SQL-like grouped stats",
                rowQuery.schema(QueryRow.class).names(),
                TabularRows.toMaps(rows, rowQuery.schema(QueryRow.class)),
                TabularRows.firstRowAsMap(
                        totalsQuery.filter(employees, QueryRow.class),
                        totalsQuery.schema(QueryRow.class)
                ),
                DIRECT_STATS_QUERY
        );
    }

    private StatsPayload presetStats(StatsViewPreset<QueryRow> preset,
                                     List<Employee> employees,
                                     StatsMode mode,
                                     String title) {
        StatsTablePayload payload = preset.tablePayload(employees);
        return new StatsPayload(mode.name(), title, payload.columns(), payload.rows(), payload.totals(), preset.source());
    }

    private DashboardCharts buildCharts(List<Employee> employees, ChartMode mode) {
        return switch (mode) {
            case DIRECT_SQL -> new DashboardCharts(
                    chartJsPayload(
                            pojoLensRuntime.parse(DIRECT_PAYROLL_CHART_QUERY)
                                    .chart(employees, QueryRow.class, PAYROLL_CHART_SPEC)
                    ),
                    chartJsPayload(
                            pojoLensRuntime.parse(DIRECT_HEADCOUNT_CHART_QUERY)
                                    .chart(employees, QueryRow.class, HEADCOUNT_CHART_SPEC)
                    )
            );
            case PRESET_QUERY -> new DashboardCharts(
                    payrollChartPreset.chartJs(employees),
                    headcountChartPreset.chartJs(employees)
            );
            case PRESET_REPORT -> new DashboardCharts(
                    payrollChartReport.chartJs(employees),
                    headcountChartReport.chartJs(employees)
            );
        };
    }

    private ChartJsPayload chartJsPayload(ChartData chartData) {
        return ChartJsAdapter.toPayload(chartData);
    }

    private List<EmployeeView> employeeViews(List<Employee> employees) {
        return employees.stream()
                .sorted(Comparator.comparingLong(employee -> employee.id))
                .map(Employee::toView)
                .toList();
    }

    private StatsMode parseStatsMode(String value) {
        try {
            return StatsMode.valueOf(requiredText(value, "statsMode"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown statsMode: " + value);
        }
    }

    private ChartMode parseChartMode(String value) {
        try {
            return ChartMode.valueOf(requiredText(value, "chartMode"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown chartMode: " + value);
        }
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private static int requiredSalary(Integer salary) {
        if (salary == null || salary < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "salary must be positive");
        }
        return salary;
    }

    private record DashboardCharts(ChartJsPayload payrollChart,
                                   ChartJsPayload headcountChart) {
    }
}
