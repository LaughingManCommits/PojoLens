package laughing.man.commits;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartQueryPreset;
import laughing.man.commits.chart.ChartQueryPresets;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ReportDefinitionTest {

    @Test
    public void sqlLikeReportDefinitionShouldBeReusableAcrossSnapshots() {
        ReportDefinition<DepartmentCountRow> report = PojoLens.report(
                PojoLens.parse("select department, count(*) as total group by department order by department asc"),
                DepartmentCountRow.class,
                ChartSpec.of(ChartType.BAR, "department", "total")
        );

        List<DepartmentCountRow> fullRows = report.rows(sampleEmployees());
        List<DepartmentCountRow> filteredRows = report.rows(sampleEmployees().stream()
                .filter(employee -> employee.active)
                .toList());
        ChartData chart = report.chart(sampleEmployees());

        assertEquals(2, fullRows.size());
        assertEquals("Engineering", fullRows.get(0).department);
        assertEquals(3L, fullRows.get(0).total);
        assertEquals(2, filteredRows.size());
        assertEquals(2L, filteredRows.get(0).total);
        assertEquals(List.of("Engineering", "Finance"), chart.getLabels());
    }

    @Test
    public void fluentReportDefinitionShouldRebuildQueryPerExecution() {
        ReportDefinition<DepartmentCountRow> report = PojoLens.report(
                DepartmentCountRow.class,
                builder -> builder
                        .addRule("active", true, Clauses.EQUAL)
                        .addGroup("department")
                        .addCount("total")
                        .addOrder("department", 1),
                ChartSpec.of(ChartType.BAR, "department", "total")
        );

        List<DepartmentCountRow> rows = report.rows(sampleEmployees());
        List<DepartmentCountRow> subsetRows = report.rows(List.of(
                new Employee(10, "X", "Support", 50000, null, true),
                new Employee(11, "Y", "Support", 51000, null, true)
        ));
        ChartData chart = report.chart(sampleEmployees());

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(2L, rows.get(0).total);
        assertEquals(1, subsetRows.size());
        assertEquals("Support", subsetRows.get(0).department);
        assertEquals(2L, subsetRows.get(0).total);
        assertEquals(List.of("Engineering", "Finance"), chart.getLabels());
    }

    @Test
    public void reportDefinitionWithoutChartSpecShouldRejectChartExecution() {
        ReportDefinition<Employee> report = PojoLens.report(
                PojoLens.parse("where active = true order by salary desc"),
                Employee.class
        );

        try {
            report.chart(sampleEmployees());
            fail("Expected missing chartSpec failure");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("chartSpec"));
        }
    }

    @Test
    public void sqlLikeReportDefinitionShouldSupportJoinBindings() {
        List<Company> companies = sampleCompanies();
        List<CompanyEmployee> employees = sampleCompanyEmployees();
        ReportDefinition<Company> report = PojoLens.report(
                PojoLens.parse("select * from companies left join employees on id = companyId where title = 'Engineer'"),
                Company.class
        );

        List<Company> rows = report.rows(companies, JoinBindings.of("employees", employees));

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
    }

    @Test
    public void chartQueryPresetShouldExposeReportDefinitionBridge() {
        ChartQueryPreset<DepartmentCountRow> preset = ChartQueryPresets
                .categoryCounts("department", "total", DepartmentCountRow.class);

        ReportDefinition<DepartmentCountRow> report = preset.reportDefinition();
        List<DepartmentCountRow> rows = report.rows(sampleEmployees());
        ChartData chart = report.chart(sampleEmployees());

        assertEquals(2, rows.size());
        assertEquals(ChartType.BAR, report.chartSpec().type());
        assertEquals(2, chart.getLabels().size());
    }

    @Test
    public void reportDefinitionShouldExposeOrderedSchemaMetadata() {
        ReportDefinition<DepartmentCountRow> report = PojoLens.report(
                PojoLens.parse("select department, count(*) as total group by department order by department asc"),
                DepartmentCountRow.class
        );

        TabularSchema schema = report.schema();

        assertEquals(List.of("department", "total"), schema.names());
        assertEquals("Department", schema.column("department").label());
        assertEquals("Total", schema.column("total").label());
        assertEquals(Long.class, schema.column("total").type());
        assertEquals("metric:COUNT", schema.column("total").formatHint());
    }

    @Test
    public void chartQueryPresetShouldExposeSchemaMetadata() {
        ChartQueryPreset<PeriodCountRow> preset = ChartQueryPresets
                .timeSeriesCounts("hireDate", TimeBucket.MONTH, "period", "total", PeriodCountRow.class);

        TabularSchema schema = preset.schema();

        assertEquals(List.of("period", "total"), schema.names());
        assertTrue(schema.column("period").formatHint().contains("time-bucket:MONTH"));
    }

    public static class DepartmentCountRow {
        public String department;
        public long total;

        public DepartmentCountRow() {
        }
    }

    public static class PeriodCountRow {
        public String period;
        public long total;

        public PeriodCountRow() {
        }
    }
}

