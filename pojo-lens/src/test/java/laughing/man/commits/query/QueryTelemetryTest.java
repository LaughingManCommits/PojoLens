package laughing.man.commits.query;

import laughing.man.commits.PojoLensCore;
import laughing.man.commits.PojoLensSql;

import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.telemetry.QueryTelemetryEvent;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCountRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

    public static class SalaryAliasRow {
        public String name;
        public int pay;

        public SalaryAliasRow() {
        }
    }
}







