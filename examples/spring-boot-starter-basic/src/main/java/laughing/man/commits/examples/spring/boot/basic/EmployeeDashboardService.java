package laughing.man.commits.examples.spring.boot.basic;

import laughing.man.commits.PojoLensChart;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartQueryPreset;
import laughing.man.commits.chart.ChartQueryPresets;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.ChartMode;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.CreateEmployeeRequest;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.DashboardOptions;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.DashboardPayload;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.DepartmentMetricRow;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.DepartmentStatsView;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.DepartmentValueRow;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.DirectStatsTotals;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.Employee;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.EmployeeView;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.RuntimeInfo;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.StatsMode;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.StatsPayload;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.SummaryMetricRow;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.stats.StatsTable;
import laughing.man.commits.stats.StatsViewPreset;
import laughing.man.commits.stats.StatsViewPresets;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.util.ReflectionUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
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

    // Define reusable presets once and execute them against fresh snapshots per request.
    // See /docs/stats-presets.md and /docs/charts.md for the intended application pattern.
    private final StatsViewPreset<DepartmentMetricRow> payrollStatsPreset;
    private final StatsViewPreset<DepartmentMetricRow> top3PayrollStatsPreset;
    private final StatsViewPreset<SummaryMetricRow> summaryHeadcountPreset;
    private final ChartQueryPreset<DepartmentValueRow> payrollChartPreset;
    private final ChartQueryPreset<DepartmentValueRow> headcountChartPreset;
    private final ReportDefinition<DepartmentValueRow> payrollChartReport;
    private final ReportDefinition<DepartmentValueRow> headcountChartReport;

    EmployeeDashboardService(EmployeeStore employeeStore, PojoLensRuntime pojoLensRuntime) {
        this.employeeStore = employeeStore;
        this.pojoLensRuntime = pojoLensRuntime;
        payrollStatsPreset = StatsViewPresets.by(
                "department",
                Metric.SUM,
                "salary",
                "total",
                DepartmentMetricRow.class
        );
        top3PayrollStatsPreset = StatsViewPresets.topNBy(
                "department",
                Metric.SUM,
                "salary",
                "total",
                3,
                DepartmentMetricRow.class
        );
        summaryHeadcountPreset = StatsViewPresets.summary(
                Metric.COUNT,
                null,
                "total",
                SummaryMetricRow.class
        );
        payrollChartPreset = ChartQueryPresets.categoryTotals(
                "department",
                Metric.SUM,
                "salary",
                "value",
                ChartType.BAR,
                DepartmentValueRow.class
        );
        headcountChartPreset = ChartQueryPresets.categoryTotals(
                "department",
                Metric.COUNT,
                null,
                "value",
                ChartType.PIE,
                DepartmentValueRow.class
        );
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
                ChartJsPayloadMapper.toPayload(charts.payrollChart()),
                ChartJsPayloadMapper.toPayload(charts.headcountChart()),
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
        List<DepartmentStatsView> rows = pojoLensRuntime
                .parse(DIRECT_STATS_QUERY)
                .filter(employees, DepartmentStatsView.class);
        Map<String, Object> totals = pojoLensRuntime
                .parse(DIRECT_STATS_TOTALS_QUERY)
                .filter(employees, DirectStatsTotals.class)
                .stream()
                .findFirst()
                .map(this::directTotalsMap)
                .orElse(Map.of());
        return new StatsPayload(
                StatsMode.DIRECT_SQL.name(),
                "Direct SQL-like grouped stats",
                List.of("department", "headcount", "payroll", "averageSalary"),
                rows.stream().map(this::directStatsRowMap).toList(),
                totals,
                DIRECT_STATS_QUERY
        );
    }

    private <T> StatsPayload presetStats(StatsViewPreset<T> preset,
                                         List<Employee> employees,
                                         StatsMode mode,
                                         String title) {
        StatsTable<T> table = preset.table(employees);
        // StatsTable exposes both schema() and totals(), which is why the UI can stay generic.
        // See /docs/stats-presets.md for the table-first wrapper contract.
        return fromStatsTable(table, mode.name(), title, preset.source());
    }

    private <T> StatsPayload fromStatsTable(StatsTable<T> table,
                                            String mode,
                                            String title,
                                            String source) {
        return new StatsPayload(
                mode,
                title,
                table.schema().names(),
                rowsToMaps(table.rows(), table.schema()),
                table.totals(),
                source
        );
    }

    private DashboardCharts buildCharts(List<Employee> employees, ChartMode mode) {
        return switch (mode) {
            case DIRECT_SQL -> new DashboardCharts(
                    payrollChartFromRows(
                            pojoLensRuntime.parse(DIRECT_PAYROLL_CHART_QUERY)
                                    .filter(employees, DepartmentValueRow.class)
                    ),
                    headcountChartFromRows(
                            pojoLensRuntime.parse(DIRECT_HEADCOUNT_CHART_QUERY)
                                    .filter(employees, DepartmentValueRow.class)
                    )
            );
            case PRESET_QUERY -> new DashboardCharts(
                    payrollChart(payrollChartPreset.chart(employees)),
                    headcountChart(headcountChartPreset.chart(employees))
            );
            case PRESET_REPORT -> new DashboardCharts(
                    payrollChart(payrollChartReport.chart(employees)),
                    headcountChart(headcountChartReport.chart(employees))
            );
        };
    }

    private ChartData payrollChartFromRows(List<DepartmentValueRow> rows) {
        return PojoLensChart.toChartData(rows, PAYROLL_CHART_SPEC);
    }

    private ChartData headcountChartFromRows(List<DepartmentValueRow> rows) {
        return PojoLensChart.toChartData(rows, HEADCOUNT_CHART_SPEC);
    }

    private ChartData payrollChart(ChartData chartData) {
        chartData.setTitle("Payroll by Department");
        chartData.setXLabel("Department");
        chartData.setYLabel("Payroll");
        return chartData;
    }

    private ChartData headcountChart(ChartData chartData) {
        chartData.setTitle("Headcount by Department");
        return chartData;
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

    private List<Map<String, Object>> rowsToMaps(List<?> rows, TabularSchema schema) {
        return rows.stream()
                .map(row -> rowToMap(row, schema.names()))
                .toList();
    }

    private Map<String, Object> rowToMap(Object row, List<String> fieldNames) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String fieldName : fieldNames) {
            values.put(fieldName, fieldValue(row, fieldName));
        }
        return values;
    }

    private Map<String, Object> directStatsRowMap(DepartmentStatsView row) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("department", row.department);
        values.put("headcount", row.headcount);
        values.put("payroll", row.payroll);
        values.put("averageSalary", row.averageSalary);
        return values;
    }

    private Map<String, Object> directTotalsMap(DirectStatsTotals row) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("headcount", row.headcount);
        values.put("payroll", row.payroll);
        values.put("averageSalary", row.averageSalary);
        return values;
    }

    private Object fieldValue(Object row, String fieldName) {
        try {
            return ReflectionUtil.getFieldValue(row, fieldName);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read stats field '" + fieldName + "'", ex);
        }
    }

    private record DashboardCharts(ChartData payrollChart,
                                   ChartData headcountChart) {
    }
}
