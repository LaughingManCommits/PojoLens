package laughing.man.commits.examples.spring.boot.basic;

import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartQueryPreset;
import laughing.man.commits.chart.ChartQueryPresets;
import laughing.man.commits.chartjs.ChartJsPayload;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.ChartTypeOption;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.CreateEmployeeRequest;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.DashboardOptions;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.DashboardPayload;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.Employee;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.EmployeeView;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.RuntimeInfo;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.StatsPayload;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.StatsView;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.stats.StatsTablePayload;
import laughing.man.commits.stats.StatsViewPreset;
import laughing.man.commits.stats.StatsViewPresets;
import laughing.man.commits.table.TabularRows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
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
    private static final String TEAM_SUMMARY_QUERY = "select count(*) as headcount, sum(salary) as payroll, "
            + "avg(salary) as averageSalary";

    private final EmployeeStore employeeStore;
    private final PojoLensRuntime pojoLensRuntime;

    private final StatsViewPreset<QueryRow> departmentPayrollStatsPreset;
    private final StatsViewPreset<QueryRow> departmentHeadcountStatsPreset;
    private final StatsViewPreset<QueryRow> top3PayrollStatsPreset;
    private final ChartQueryPreset<QueryRow> payrollChartPreset;
    private final ReportDefinition<QueryRow> headcountChartReport;

    EmployeeDashboardService(EmployeeStore employeeStore, PojoLensRuntime pojoLensRuntime) {
        this.employeeStore = employeeStore;
        this.pojoLensRuntime = pojoLensRuntime;
        departmentPayrollStatsPreset = StatsViewPresets.by("department", Metric.SUM, "salary", "payroll");
        departmentHeadcountStatsPreset = StatsViewPresets.by("department", Metric.COUNT, null, "headcount");
        top3PayrollStatsPreset = StatsViewPresets.topNBy("department", Metric.SUM, "salary", "payroll", 3);
        payrollChartPreset = ChartQueryPresets
                .categoryTotals("department", Metric.SUM, "salary", "value")
                .mapChartSpec(spec -> spec
                        .withTitle("Payroll by Department")
                        .withAxisLabels("Department", "Payroll"));
        headcountChartReport = ChartQueryPresets
                .categoryCounts("department", "value")
                .mapChartSpec(spec -> spec
                        .withTitle("Headcount by Department")
                        .withAxisLabels("Department", "Headcount"))
                .reportDefinition();
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
                StatsView.names(),
                ChartTypeOption.names(),
                StatsView.details(),
                ChartTypeOption.details(),
                StatsView.defaultView().name(),
                ChartTypeOption.defaultType().name()
        );
    }

    DashboardPayload dashboard(String statsViewValue, String chartTypeValue) {
        List<Employee> employees = employeeStore.snapshot();
        StatsView statsView = parseStatsView(statsViewValue);
        ChartTypeOption chartType = parseChartType(chartTypeValue);
        DashboardCharts charts = buildCharts(employees, chartType);
        return new DashboardPayload(
                employeeViews(employees),
                buildStatsPayload(employees, statsView),
                charts.payrollChart(),
                charts.headcountChart(),
                runtime(),
                dashboardOptions(),
                statsView.name(),
                chartType.name()
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

    private StatsPayload buildStatsPayload(List<Employee> employees, StatsView view) {
        return switch (view) {
            case DEPARTMENT_PAYROLL -> presetStats(
                    departmentPayrollStatsPreset,
                    employees,
                    view,
                    "Payroll by Department"
            );
            case DEPARTMENT_HEADCOUNT -> presetStats(
                    departmentHeadcountStatsPreset,
                    employees,
                    view,
                    "Headcount by Department"
            );
            case TOP_3_PAYROLL_DEPARTMENTS -> presetStats(
                    top3PayrollStatsPreset,
                    employees,
                    view,
                    "Top 3 Payroll Departments"
            );
            case TEAM_SUMMARY -> teamSummaryStats(employees, view);
        };
    }

    private StatsPayload teamSummaryStats(List<Employee> employees, StatsView view) {
        var query = pojoLensRuntime.parse(TEAM_SUMMARY_QUERY);
        List<QueryRow> rows = query.filter(employees, QueryRow.class);
        return new StatsPayload(
                view.name(),
                "Team Summary",
                query.schema(QueryRow.class).names(),
                TabularRows.toMaps(rows, query.schema(QueryRow.class)),
                Collections.emptyMap(),
                TEAM_SUMMARY_QUERY
        );
    }

    private StatsPayload presetStats(StatsViewPreset<QueryRow> preset,
                                     List<Employee> employees,
                                     StatsView view,
                                     String title) {
        StatsTablePayload payload = preset.tablePayload(employees);
        return new StatsPayload(view.name(), title, payload.columns(), payload.rows(), payload.totals(), preset.source());
    }

    private DashboardCharts buildCharts(List<Employee> employees, ChartTypeOption chartType) {
        return new DashboardCharts(
                payrollChartPreset
                        .mapChartSpec(spec -> spec.withType(chartType.chartType()))
                        .chartJs(employees),
                headcountChartReport
                        .mapChartSpec(spec -> spec.withType(chartType.chartType()))
                        .chartJs(employees)
        );
    }

    private List<EmployeeView> employeeViews(List<Employee> employees) {
        return employees.stream()
                .sorted(Comparator.comparingLong(employee -> employee.id))
                .map(Employee::toView)
                .toList();
    }

    private StatsView parseStatsView(String value) {
        try {
            return StatsView.valueOf(requiredText(value, "statsView"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown statsView: " + value);
        }
    }

    private ChartTypeOption parseChartType(String value) {
        try {
            return ChartTypeOption.valueOf(requiredText(value, "chartType"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown chartType: " + value);
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
