package laughing.man.commits.query;

import laughing.man.commits.PojoLensCore;
import laughing.man.commits.PojoLensSql;

import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.telemetry.QueryTelemetryEvent;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCountRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueryTelemetryTest {

    @Test
    public void runtimeParseAndSqlLikeChartShouldEmitTelemetryStages() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        List<QueryTelemetryEvent> events = new ArrayList<>();
        runtime.setTelemetryListener(events::add);

        ChartData chart = runtime
                .parse("select department, count(*) as total group by department order by department asc")
                .chart(sampleEmployees(), DepartmentCountRow.class, ChartSpec.of(ChartType.BAR, "department", "total"));

        assertEquals(List.of(
                        QueryTelemetryStage.PARSE,
                        QueryTelemetryStage.BIND,
                        QueryTelemetryStage.FILTER,
                        QueryTelemetryStage.AGGREGATE,
                        QueryTelemetryStage.ORDER,
                        QueryTelemetryStage.CHART
                ),
                events.stream().map(QueryTelemetryEvent::stage).toList());
        assertTrue(events.stream().allMatch(event -> "sql-like".equals(event.queryType())));
        assertEquals(4, events.get(2).rowCountBefore());
        assertEquals(2, events.get(3).rowCountAfter());
        assertEquals(2, chart.getLabels().size());
    }

    @Test
    public void fluentChartExecutionShouldEmitTelemetryStages() {
        List<QueryTelemetryEvent> events = new ArrayList<>();

        ChartData chart = PojoLensCore.newQueryBuilder(sampleEmployees())
                .telemetry(events::add)
                .addRule("active", true, Clauses.EQUAL)
                .addGroup("department")
                .addCount("total")
                .addOrder("department", 1)
                .initFilter()
                .chart(DepartmentCountRow.class, ChartSpec.of(ChartType.BAR, "department", "total"));

        assertEquals(List.of(
                        QueryTelemetryStage.FILTER,
                        QueryTelemetryStage.AGGREGATE,
                        QueryTelemetryStage.ORDER,
                        QueryTelemetryStage.CHART
                ),
                events.stream().map(QueryTelemetryEvent::stage).toList());
        assertTrue(events.stream().allMatch(event -> "fluent".equals(event.queryType())));
        assertEquals(4, events.get(0).rowCountBefore());
        assertEquals(3, events.get(0).rowCountAfter());
        assertEquals(2, chart.getLabels().size());
    }

    @Test
    public void aliasedSqlLikeExecutionShouldEmitBindFilterAndOrderTelemetry() {
        List<QueryTelemetryEvent> events = new ArrayList<>();
        SqlLikeQuery query = PojoLensSql.parse("select name, salary as pay where active = true order by salary desc")
                .telemetry(events::add);

        List<SalaryAliasRow> rows = query.filter(sampleEmployees(), SalaryAliasRow.class);

        assertEquals(List.of(
                        QueryTelemetryStage.BIND,
                        QueryTelemetryStage.FILTER,
                        QueryTelemetryStage.ORDER
                ),
                events.stream().map(QueryTelemetryEvent::stage).toList());
        assertEquals("sql-like", events.get(0).queryType());
        assertEquals("Cara", rows.get(0).name);
        assertEquals(130000, rows.get(0).pay);
    }

    @Test
    public void runtimeNaturalExecutionShouldEmitNaturalTelemetryStages() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        List<QueryTelemetryEvent> events = new ArrayList<>();
        runtime.setTelemetryListener(events::add);

        List<EmployeeSummaryRow> rows = runtime.natural()
                .parse("show name as employee name, salary as annual salary where active is true sort by salary desc limit 2")
                .filter(sampleEmployees(), EmployeeSummaryRow.class);

        assertEquals(List.of(
                        QueryTelemetryStage.PARSE,
                        QueryTelemetryStage.BIND,
                        QueryTelemetryStage.FILTER,
                        QueryTelemetryStage.ORDER
                ),
                events.stream().map(QueryTelemetryEvent::stage).toList());
        assertTrue(events.stream().allMatch(event -> "natural".equals(event.queryType())));
        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.employeeName).toList());
    }

    @Test
    public void runtimeGroupedNaturalExecutionShouldEmitAggregateTelemetryStages() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        List<QueryTelemetryEvent> events = new ArrayList<>();
        runtime.setTelemetryListener(events::add);

        List<DepartmentCountRow> rows = runtime.natural()
                .parse("show department, count of employees as total "
                        + "where active is true group by department having total is at least 1 sort by total descending")
                .filter(sampleEmployees(), DepartmentCountRow.class);

        assertEquals(List.of(
                        QueryTelemetryStage.PARSE,
                        QueryTelemetryStage.BIND,
                        QueryTelemetryStage.FILTER,
                        QueryTelemetryStage.AGGREGATE,
                        QueryTelemetryStage.ORDER
                ),
                events.stream().map(QueryTelemetryEvent::stage).toList());
        assertTrue(events.stream().allMatch(event -> "natural".equals(event.queryType())));
        assertEquals(List.of("Engineering", "Finance"), rows.stream().map(row -> row.department).toList());
    }

    @Test
    public void runtimeNaturalChartPhraseShouldEmitChartTelemetryStages() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        List<QueryTelemetryEvent> events = new ArrayList<>();
        runtime.setTelemetryListener(events::add);

        ChartData chart = runtime.natural()
                .parse("show department, count of employees as total "
                        + "where active is true group by department sort by total descending as bar chart")
                .chart(sampleEmployees(), DepartmentCountRow.class);

        assertEquals(List.of(
                        QueryTelemetryStage.PARSE,
                        QueryTelemetryStage.BIND,
                        QueryTelemetryStage.FILTER,
                        QueryTelemetryStage.AGGREGATE,
                        QueryTelemetryStage.ORDER,
                        QueryTelemetryStage.CHART
                ),
                events.stream().map(QueryTelemetryEvent::stage).toList());
        assertTrue(events.stream().allMatch(event -> "natural".equals(event.queryType())));
        assertEquals(2, chart.getLabels().size());
    }

    @Test
    public void runtimeNaturalJoinExecutionShouldEmitNaturalTelemetryStages() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        List<QueryTelemetryEvent> events = new ArrayList<>();
        runtime.setTelemetryListener(events::add);

        List<Company> rows = runtime.natural()
                .parse("from companies as company join employees as employee "
                        + "on company id equals employee company id "
                        + "show company where employee title is Engineer")
                .filter(
                        sampleCompanies(),
                        JoinBindings.of("employees", sampleCompanyEmployees()),
                        Company.class
                );

        assertEquals(List.of(
                        QueryTelemetryStage.PARSE,
                        QueryTelemetryStage.BIND,
                        QueryTelemetryStage.FILTER
                ),
                events.stream().map(QueryTelemetryEvent::stage).toList());
        assertTrue(events.stream().allMatch(event -> "natural".equals(event.queryType())));
        assertEquals(List.of("Acme"), rows.stream().map(row -> row.name).toList());
    }

    public static class SalaryAliasRow {
        public String name;
        public int pay;

        public SalaryAliasRow() {
        }
    }

    public static class EmployeeSummaryRow {
        public String employeeName;
        public int annualSalary;

        public EmployeeSummaryRow() {
        }
    }
}







