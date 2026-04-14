package laughing.man.commits.fluent;

import laughing.man.commits.PojoLensCore;
import laughing.man.commits.builder.FluentQueryDefinition;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCountRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FluentQueryDefinitionTest {

    @Test
    public void preparedFluentDefinitionShouldReuseQueryShapeAcrossSnapshots() {
        FluentQueryDefinition<DepartmentCountRow> definition = PojoLensCore.prepare(
                DepartmentCountRow.class,
                builder -> builder
                        .addRule("active", true, Clauses.EQUAL)
                        .addGroup("department")
                        .addCount("total")
                        .addOrder("department", 1)
        );

        List<DepartmentCountRow> fullRows = definition.rows(sampleEmployees());
        List<DepartmentCountRow> subsetRows = definition.rows(List.of(
                new Employee(10, "X", "Support", 50000, null, true),
                new Employee(11, "Y", "Support", 51000, null, true)
        ));
        Map<String, Object> explain = definition.explain();

        assertEquals("fluent", definition.source());
        assertEquals(DepartmentCountRow.class, definition.projectionClass());
        assertEquals(List.of("department", "total"), definition.schema().names());
        assertEquals(2, fullRows.size());
        assertEquals("Engineering", fullRows.get(0).department);
        assertEquals(2L, fullRows.get(0).total);
        assertEquals(1, subsetRows.size());
        assertEquals("Support", subsetRows.get(0).department);
        assertEquals(2L, subsetRows.get(0).total);
        assertEquals("fluent", explain.get("type"));
        assertEquals(1, explain.get("whereRuleCount"));
    }

    @Test
    public void preparedFluentDefinitionShouldPromoteToReportDefinition() {
        FluentQueryDefinition<DepartmentCountRow> definition = FluentQueryDefinition.of(
                DepartmentCountRow.class,
                builder -> builder
                        .addRule("active", true, Clauses.EQUAL)
                        .addGroup("department")
                        .addCount("total")
                        .addOrder("department", 1)
        );

        ReportDefinition<DepartmentCountRow> report = definition.reportDefinition(
                ChartSpec.of(ChartType.BAR, "department", "total")
        );
        ChartData chart = report.chart(sampleEmployees());

        assertEquals("fluent", report.source());
        assertEquals(List.of("department", "total"), report.schema().names());
        assertEquals(List.of("Engineering", "Finance"), chart.getLabels());
    }

    @Test
    public void preparedFluentDefinitionShouldRejectNullSourceRows() {
        FluentQueryDefinition<DepartmentCountRow> definition = PojoLensCore.prepare(
                DepartmentCountRow.class,
                builder -> builder.addGroup("department").addCount("total")
        );

        assertThrows(NullPointerException.class, () -> definition.rows(null));
    }
}
