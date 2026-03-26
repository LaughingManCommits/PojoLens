package laughing.man.commits.examples.spring.boot.basic;

import laughing.man.commits.PojoLensChart;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartDataset;
import laughing.man.commits.chart.ChartQueryPreset;
import laughing.man.commits.chart.ChartQueryPresets;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.stats.StatsTable;
import laughing.man.commits.stats.StatsViewPreset;
import laughing.man.commits.stats.StatsViewPresets;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.util.ReflectionUtil;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/employees")
public class EmployeeQueryController {

    private static final List<Employee> EMPLOYEES = new CopyOnWriteArrayList<>(List.of(
            new Employee(1L, "Alicia", "Engineering", 145_000),
            new Employee(2L, "Mateo", "Engineering", 126_000),
            new Employee(3L, "Priya", "Engineering", 112_000),
            new Employee(4L, "Elena", "Engineering", 98_000),
            new Employee(5L, "Jordan", "Sales", 119_000),
            new Employee(6L, "Noah", "Sales", 106_000),
            new Employee(7L, "Ava", "Marketing", 101_000)
    ));
    private static final AtomicLong NEXT_EMPLOYEE_ID = new AtomicLong(7L);

    private final PojoLensRuntime pojoLensRuntime;

    public EmployeeQueryController(PojoLensRuntime pojoLensRuntime) {
        this.pojoLensRuntime = pojoLensRuntime;
    }

    @GetMapping
    public List<EmployeeView> employees() {
        return employeeViews(snapshotEmployees());
    }

    @GetMapping("/departments")
    public List<String> departments() {
        return snapshotEmployees().stream()
                .map(employee -> employee.department)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeView addEmployee(@RequestBody CreateEmployeeRequest request) {
        String name = requiredText(request.name(), "name");
        String department = requiredText(request.department(), "department");
        int salary = requiredSalary(request.salary());
        Employee added = new Employee(NEXT_EMPLOYEE_ID.incrementAndGet(), name, department, salary);
        EMPLOYEES.add(added);
        return new EmployeeView(added.id, added.name, added.department, added.salary);
    }

    @GetMapping("/dashboard-options")
    public DashboardOptions dashboardOptions() {
        return new DashboardOptions(
                enumNames(StatsMode.values()),
                enumNames(ChartMode.values())
        );
    }

    @GetMapping("/dashboard")
    public DashboardPayload dashboard(@RequestParam(name = "statsMode", defaultValue = "PRESET_BY_PAYROLL")
                                      String statsMode,
                                      @RequestParam(name = "chartMode", defaultValue = "PRESET_QUERY")
                                      String chartMode) {
        List<Employee> employees = snapshotEmployees();
        StatsMode selectedStatsMode = parseStatsMode(statsMode);
        ChartMode selectedChartMode = parseChartMode(chartMode);
        StatsPayload stats = buildStatsPayload(employees, selectedStatsMode);
        DashboardCharts charts = buildCharts(employees, selectedChartMode);
        return new DashboardPayload(
                employeeViews(employees),
                stats,
                toChartJsPayload(charts.payrollChart()),
                toChartJsPayload(charts.headcountChart()),
                runtime(),
                dashboardOptions(),
                selectedStatsMode.name(),
                selectedChartMode.name()
        );
    }

    @GetMapping("/top-paid")
    public List<EmployeeView> topPaid(@RequestParam(name = "department", defaultValue = "Engineering")
                                      String department,
                                      @RequestParam(name = "minSalary", defaultValue = "100000")
                                      int minSalary,
                                      @RequestParam(name = "limit", defaultValue = "3")
                                      int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 25));
        return pojoLensRuntime
                .parse("select id, name, department, salary "
                        + "where department = :department and salary >= :minSalary "
                        + "order by salary desc limit :limit")
                .params(Map.of(
                        "department", department,
                        "minSalary", minSalary,
                        "limit", cappedLimit
                ))
                .filter(snapshotEmployees(), EmployeeView.class);
    }

    @GetMapping("/runtime")
    public RuntimeInfo runtime() {
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
            case PRESET_BY_PAYROLL -> presetByPayrollStats(employees);
            case PRESET_TOP3_PAYROLL -> presetTop3PayrollStats(employees);
            case PRESET_SUMMARY_HEADCOUNT -> presetSummaryHeadcountStats(employees);
        };
    }

    private StatsPayload directSqlStats(List<Employee> employees) {
        String source = "select department, count(*) as headcount, sum(salary) as payroll, "
                + "avg(salary) as averageSalary group by department order by payroll desc";
        List<DepartmentStatsView> rows = pojoLensRuntime
                .parse(source)
                .filter(employees, DepartmentStatsView.class);
        List<Map<String, Object>> mappedRows = rows.stream()
                .map(this::directStatsRowMap)
                .toList();
        Map<String, Object> totals = pojoLensRuntime
                .parse("select count(*) as headcount, sum(salary) as payroll, avg(salary) as averageSalary")
                .filter(employees, DirectStatsTotals.class)
                .stream()
                .findFirst()
                .map(this::directTotalsMap)
                .orElse(Map.of());
        return new StatsPayload(
                StatsMode.DIRECT_SQL.name(),
                "Direct SQL-like grouped stats",
                List.of("department", "headcount", "payroll", "averageSalary"),
                mappedRows,
                totals,
                source
        );
    }

    private StatsPayload presetByPayrollStats(List<Employee> employees) {
        StatsViewPreset<DepartmentMetricRow> preset = StatsViewPresets.by(
                "department",
                Metric.SUM,
                "salary",
                "total",
                DepartmentMetricRow.class
        );
        return fromStatsTable(
                preset.table(employees),
                StatsMode.PRESET_BY_PAYROLL.name(),
                "Stats preset: by(department, sum(salary))",
                preset.source()
        );
    }

    private StatsPayload presetTop3PayrollStats(List<Employee> employees) {
        StatsViewPreset<DepartmentMetricRow> preset = StatsViewPresets.topNBy(
                "department",
                Metric.SUM,
                "salary",
                "total",
                3,
                DepartmentMetricRow.class
        );
        return fromStatsTable(
                preset.table(employees),
                StatsMode.PRESET_TOP3_PAYROLL.name(),
                "Stats preset: topNBy(department, sum(salary), 3)",
                preset.source()
        );
    }

    private StatsPayload presetSummaryHeadcountStats(List<Employee> employees) {
        StatsViewPreset<SummaryMetricRow> preset = StatsViewPresets.summary(
                Metric.COUNT,
                null,
                "total",
                SummaryMetricRow.class
        );
        return fromStatsTable(
                preset.table(employees),
                StatsMode.PRESET_SUMMARY_HEADCOUNT.name(),
                "Stats preset: summary(count(*))",
                preset.source()
        );
    }

    private <T> StatsPayload fromStatsTable(StatsTable<T> table,
                                            String mode,
                                            String title,
                                            String source) {
        List<String> columns = table.schema().names();
        List<Map<String, Object>> rows = rowsToMaps(table.rows(), table.schema());
        return new StatsPayload(mode, title, columns, rows, table.totals(), source);
    }

    private DashboardCharts buildCharts(List<Employee> employees, ChartMode mode) {
        ChartQueryPreset<DepartmentValueRow> payrollPreset = ChartQueryPresets.categoryTotals(
                "department",
                Metric.SUM,
                "salary",
                "value",
                ChartType.BAR,
                DepartmentValueRow.class
        );
        ChartQueryPreset<DepartmentValueRow> headcountPreset = ChartQueryPresets.categoryTotals(
                "department",
                Metric.COUNT,
                null,
                "value",
                ChartType.PIE,
                DepartmentValueRow.class
        );
        return switch (mode) {
            case DIRECT_SQL -> {
                List<DepartmentValueRow> payrollRows = pojoLensRuntime
                        .parse("select department, sum(salary) as value "
                                + "group by department order by value desc")
                        .filter(employees, DepartmentValueRow.class);
                List<DepartmentValueRow> headcountRows = pojoLensRuntime
                        .parse("select department, count(*) as value "
                                + "group by department order by value desc")
                        .filter(employees, DepartmentValueRow.class);
                ChartData payrollChart = PojoLensChart.toChartData(
                        payrollRows,
                        ChartSpec.of(ChartType.BAR, "department", "value")
                                .withTitle("Payroll by Department")
                                .withAxisLabels("Department", "Payroll")
                );
                ChartData headcountChart = PojoLensChart.toChartData(
                        headcountRows,
                        ChartSpec.of(ChartType.PIE, "department", "value")
                                .withTitle("Headcount by Department")
                );
                yield new DashboardCharts(payrollChart, headcountChart);
            }
            case PRESET_QUERY -> {
                ChartData payrollChart = payrollPreset.chart(employees);
                ChartData headcountChart = headcountPreset.chart(employees);
                payrollChart.setTitle("Payroll by Department");
                payrollChart.setXLabel("Department");
                payrollChart.setYLabel("Payroll");
                headcountChart.setTitle("Headcount by Department");
                yield new DashboardCharts(payrollChart, headcountChart);
            }
            case PRESET_REPORT -> {
                ChartData payrollChart = payrollPreset.reportDefinition().chart(employees);
                ChartData headcountChart = headcountPreset.reportDefinition().chart(employees);
                payrollChart.setTitle("Payroll by Department");
                payrollChart.setXLabel("Department");
                payrollChart.setYLabel("Payroll");
                headcountChart.setTitle("Headcount by Department");
                yield new DashboardCharts(payrollChart, headcountChart);
            }
        };
    }

    private List<Employee> snapshotEmployees() {
        return new ArrayList<>(EMPLOYEES);
    }

    private List<EmployeeView> employeeViews(List<Employee> employees) {
        return employees.stream()
                .sorted(Comparator.comparingLong(employee -> employee.id))
                .map(employee -> new EmployeeView(employee.id, employee.name, employee.department, employee.salary))
                .toList();
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

    private List<Map<String, Object>> rowsToMaps(List<?> rows, TabularSchema schema) {
        List<Map<String, Object>> mapped = new ArrayList<>();
        List<String> names = schema.names();
        for (Object row : rows) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            for (String name : names) {
                values.put(name, fieldValue(row, name));
            }
            mapped.add(values);
        }
        return mapped;
    }

    private Map<String, Object> directStatsRowMap(DepartmentStatsView row) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("department", row.department);
        values.put("headcount", row.headcount);
        values.put("payroll", row.payroll);
        values.put("averageSalary", row.averageSalary);
        return values;
    }

    private Map<String, Object> directTotalsMap(DirectStatsTotals row) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
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

    private Map<String, Object> toChartJsPayload(ChartData chartData) {
        List<Map<String, Object>> datasets = new ArrayList<>();
        for (ChartDataset dataset : chartData.getDatasets()) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("label", dataset.getLabel());
            mapped.put("data", dataset.getValues());
            if (dataset.getColorHint() != null) {
                mapped.put("backgroundColor", dataset.getColorHint());
            } else if (chartData.getType() == ChartType.PIE) {
                mapped.put("backgroundColor", palette(chartData.getLabels().size()));
            }
            if (dataset.getStackGroupId() != null) {
                mapped.put("stack", dataset.getStackGroupId());
            }
            if (dataset.getAxisId() != null) {
                mapped.put("yAxisID", dataset.getAxisId());
            }
            datasets.add(mapped);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", toChartJsType(chartData.getType()));
        payload.put("data", Map.of(
                "labels", chartData.getLabels(),
                "datasets", datasets
        ));
        payload.put("options", chartOptions(chartData));
        return payload;
    }

    private String toChartJsType(ChartType type) {
        if (type == ChartType.AREA) {
            return "line";
        }
        return type.name().toLowerCase();
    }

    private Map<String, Object> chartOptions(ChartData chartData) {
        Map<String, Object> plugins = new LinkedHashMap<>();
        plugins.put("legend", Map.of("display", true, "position", "bottom"));
        if (chartData.getTitle() != null && !chartData.getTitle().isBlank()) {
            plugins.put("title", Map.of("display", true, "text", chartData.getTitle()));
        }
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("responsive", true);
        options.put("maintainAspectRatio", false);
        options.put("plugins", plugins);
        if (chartData.getType() != ChartType.PIE) {
            options.put("scales", Map.of(
                    "x", Map.of("stacked", chartData.isStacked()),
                    "y", Map.of("beginAtZero", true, "stacked", chartData.isStacked())
            ));
        }
        return options;
    }

    private List<String> palette(int size) {
        String[] colors = {
                "#0d6efd", "#198754", "#fd7e14", "#dc3545",
                "#6610f2", "#20c997", "#ffc107", "#6f42c1"
        };
        List<String> palette = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            palette.add(colors[i % colors.length]);
        }
        return palette;
    }

    private List<String> enumNames(Enum<?>[] values) {
        List<String> names = new ArrayList<>(values.length);
        for (Enum<?> value : values) {
            names.add(value.name());
        }
        return names;
    }

    public static final class Employee {
        public long id;
        public String name;
        public String department;
        public int salary;

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

    public static final class DepartmentStatsView {
        public String department;
        public long headcount;
        public double payroll;
        public double averageSalary;

        public DepartmentStatsView() {
        }
    }

    public static final class DepartmentMetricRow {
        public String department;
        public double total;

        public DepartmentMetricRow() {
        }
    }

    public static final class SummaryMetricRow {
        public double total;

        public SummaryMetricRow() {
        }
    }

    public static final class DepartmentValueRow {
        public String department;
        public double value;

        public DepartmentValueRow() {
        }
    }

    public static final class DirectStatsTotals {
        public long headcount;
        public double payroll;
        public double averageSalary;

        public DirectStatsTotals() {
        }
    }

    public record RuntimeInfo(boolean strictParameterTypes,
                              boolean lintMode,
                              boolean sqlLikeCacheEnabled,
                              boolean statsPlanCacheEnabled) {
    }

    public record CreateEmployeeRequest(String name,
                                        String department,
                                        Integer salary) {
    }

    public record StatsPayload(String mode,
                               String title,
                               List<String> columns,
                               List<Map<String, Object>> rows,
                               Map<String, Object> totals,
                               String source) {
    }

    public record DashboardOptions(List<String> statsModes,
                                   List<String> chartModes) {
    }

    public record DashboardPayload(List<EmployeeView> employees,
                                   StatsPayload stats,
                                   Map<String, Object> payrollChart,
                                   Map<String, Object> headcountChart,
                                   RuntimeInfo runtime,
                                   DashboardOptions options,
                                   String selectedStatsMode,
                                   String selectedChartMode) {
    }

    private record DashboardCharts(ChartData payrollChart,
                                   ChartData headcountChart) {
    }

    private enum StatsMode {
        DIRECT_SQL,
        PRESET_BY_PAYROLL,
        PRESET_TOP3_PAYROLL,
        PRESET_SUMMARY_HEADCOUNT
    }

    private enum ChartMode {
        DIRECT_SQL,
        PRESET_QUERY,
        PRESET_REPORT
    }
}
