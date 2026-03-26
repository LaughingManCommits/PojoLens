package laughing.man.commits.examples.spring.boot.basic;

import laughing.man.commits.chart.ChartType;
import laughing.man.commits.chartjs.ChartJsPayload;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Example-only API payloads, reusable selector metadata, and projection rows.
 *
 * Read next:
 * - /docs/stats-presets.md
 * - /docs/charts.md
 * - /docs/reports.md
 */
public final class EmployeeExampleTypes {

    private EmployeeExampleTypes() {
    }

    public static final class Employee {
        public long id;
        public String name;
        public String department;
        public int salary;

        public Employee() {
        }

        public Employee(long id, String name, String department, int salary) {
            this.id = id;
            this.name = name;
            this.department = department;
            this.salary = salary;
        }

        public EmployeeView toView() {
            return new EmployeeView(id, name, department, salary);
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

    public record CreateEmployeeRequest(String name,
                                        String department,
                                        Integer salary) {
    }

    public record RuntimeInfo(boolean strictParameterTypes,
                              boolean lintMode,
                              boolean sqlLikeCacheEnabled,
                              boolean statsPlanCacheEnabled) {
    }

    public record StatsPayload(String view,
                               String title,
                               List<String> columns,
                               List<Map<String, Object>> rows,
                               Map<String, Object> totals,
                               String source) {
    }

    public record DashboardOption(String value,
                                  String label,
                                  String summary,
                                  String docPath) {
    }

    public record DashboardOptions(List<String> statsViews,
                                   List<String> chartTypes,
                                   List<DashboardOption> statsViewDetails,
                                   List<DashboardOption> chartTypeDetails,
                                   String defaultStatsView,
                                   String defaultChartType) {
    }

    public record DashboardPayload(List<EmployeeView> employees,
                                   StatsPayload stats,
                                   ChartJsPayload payrollChart,
                                   ChartJsPayload headcountChart,
                                   RuntimeInfo runtime,
                                   DashboardOptions options,
                                   String selectedStatsView,
                                   String selectedChartType) {
    }

    public enum StatsView {
        DEPARTMENT_PAYROLL(
                "Payroll by Department",
                "Focus the table on department payroll totals with an overall payroll summary.",
                "/docs/stats-presets.md"
        ),
        DEPARTMENT_HEADCOUNT(
                "Headcount by Department",
                "Focus the table on department employee counts with an overall headcount summary.",
                "/docs/stats-presets.md"
        ),
        TOP_3_PAYROLL_DEPARTMENTS(
                "Top 3 Payroll Departments",
                "Turn the table into a compact leaderboard of the highest-payroll departments.",
                "/docs/stats-presets.md"
        ),
        TEAM_SUMMARY(
                "Team Summary",
                "Show a compact one-row summary with headcount, payroll, and average salary.",
                "/docs/sql-like.md"
        );

        private final String label;
        private final String summary;
        private final String docPath;

        StatsView(String label, String summary, String docPath) {
            this.label = label;
            this.summary = summary;
            this.docPath = docPath;
        }

        public DashboardOption option() {
            return new DashboardOption(name(), label, summary, docPath);
        }

        public static StatsView defaultView() {
            return DEPARTMENT_PAYROLL;
        }

        public static List<String> names() {
            return Arrays.stream(values())
                    .map(Enum::name)
                    .toList();
        }

        public static List<DashboardOption> details() {
            return Arrays.stream(values())
                    .map(StatsView::option)
                    .toList();
        }
    }

    public enum ChartTypeOption {
        BAR(
                ChartType.BAR,
                "Bar Chart",
                "Compare departments side by side with a standard bar view.",
                "/docs/charts.md"
        ),
        PIE(
                ChartType.PIE,
                "Pie Chart",
                "Show how much each department contributes to the whole.",
                "/docs/charts.md"
        ),
        LINE(
                ChartType.LINE,
                "Line Chart",
                "Render the same department totals with a connected line view.",
                "/docs/charts.md"
        ),
        AREA(
                ChartType.AREA,
                "Area Chart",
                "Use a filled line/area presentation for the same department totals.",
                "/docs/charts.md"
        );

        private final ChartType chartType;
        private final String label;
        private final String summary;
        private final String docPath;

        ChartTypeOption(ChartType chartType, String label, String summary, String docPath) {
            this.chartType = chartType;
            this.label = label;
            this.summary = summary;
            this.docPath = docPath;
        }

        public ChartType chartType() {
            return chartType;
        }

        public DashboardOption option() {
            return new DashboardOption(name(), label, summary, docPath);
        }

        public static ChartTypeOption defaultType() {
            return BAR;
        }

        public static List<String> names() {
            return Arrays.stream(values())
                    .map(Enum::name)
                    .toList();
        }

        public static List<DashboardOption> details() {
            return Arrays.stream(values())
                    .map(ChartTypeOption::option)
                    .toList();
        }
    }
}
