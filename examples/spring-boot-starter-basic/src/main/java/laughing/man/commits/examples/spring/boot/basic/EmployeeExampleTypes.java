package laughing.man.commits.examples.spring.boot.basic;

import laughing.man.commits.chartjs.ChartJsPayload;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Example-only API payloads, reusable mode metadata, and projection rows.
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

    public record StatsPayload(String mode,
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

    public record DashboardOptions(List<String> statsModes,
                                   List<String> chartModes,
                                   List<DashboardOption> statsModeDetails,
                                   List<DashboardOption> chartModeDetails,
                                   String defaultStatsMode,
                                   String defaultChartMode) {
    }

    public record DashboardPayload(List<EmployeeView> employees,
                                   StatsPayload stats,
                                   ChartJsPayload payrollChart,
                                   ChartJsPayload headcountChart,
                                   RuntimeInfo runtime,
                                   DashboardOptions options,
                                   String selectedStatsMode,
                                   String selectedChartMode) {
    }

    public enum StatsMode {
        DIRECT_SQL(
                "Direct runtime.parse(...)",
                "Build grouped stats from an ad hoc SQL-like query executed by the injected runtime.",
                "/docs/sql-like.md"
        ),
        PRESET_BY_PAYROLL(
                "StatsViewPresets.by(...)",
                "Reuse a grouped payroll table preset and render its schema and totals generically.",
                "/docs/stats-presets.md"
        ),
        PRESET_TOP3_PAYROLL(
                "StatsViewPresets.topNBy(...)",
                "Use a reusable top-N leaderboard preset to keep the query shape explicit and repeatable.",
                "/docs/stats-presets.md"
        ),
        PRESET_SUMMARY_HEADCOUNT(
                "StatsViewPresets.summary(...)",
                "Use a single-row summary preset to demonstrate totals-first dashboard output.",
                "/docs/stats-presets.md"
        );

        private final String label;
        private final String summary;
        private final String docPath;

        StatsMode(String label, String summary, String docPath) {
            this.label = label;
            this.summary = summary;
            this.docPath = docPath;
        }

        public DashboardOption option() {
            return new DashboardOption(name(), label, summary, docPath);
        }

        public static StatsMode defaultMode() {
            return PRESET_BY_PAYROLL;
        }

        public static List<String> names() {
            return Arrays.stream(values())
                    .map(Enum::name)
                    .toList();
        }

        public static List<DashboardOption> details() {
            return Arrays.stream(values())
                    .map(StatsMode::option)
                    .toList();
        }
    }

    public enum ChartMode {
        DIRECT_SQL(
                "runtime.parse(...) + ChartJsAdapter",
                "Execute an ad hoc SQL-like query and convert the PojoLens chart contract to a Chart.js payload.",
                "/docs/charts.md"
        ),
        PRESET_QUERY(
                "ChartQueryPreset.chartJs(...)",
                "Keep chart-first reusable presets as the primary API and emit a frontend-ready Chart.js payload.",
                "/docs/charts.md"
        ),
        PRESET_REPORT(
                "preset.reportDefinition().chartJs(...)",
                "Bridge a chart preset into the more general reusable report abstraction and keep the Chart.js payload.",
                "/docs/reports.md"
        );

        private final String label;
        private final String summary;
        private final String docPath;

        ChartMode(String label, String summary, String docPath) {
            this.label = label;
            this.summary = summary;
            this.docPath = docPath;
        }

        public DashboardOption option() {
            return new DashboardOption(name(), label, summary, docPath);
        }

        public static ChartMode defaultMode() {
            return PRESET_QUERY;
        }

        public static List<String> names() {
            return Arrays.stream(values())
                    .map(Enum::name)
                    .toList();
        }

        public static List<DashboardOption> details() {
            return Arrays.stream(values())
                    .map(ChartMode::option)
                    .toList();
        }
    }
}
