package laughing.man.commits;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartQueryPreset;
import laughing.man.commits.chart.ChartQueryPresets;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.chartjs.ChartJsPayload;
import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.natural.NaturalVocabulary;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCountRow;
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
        ReportDefinition<DepartmentCountRow> report = ReportDefinition.sql(
                PojoLensSql.parse("select department, count(*) as total group by department order by department asc"),
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
    public void naturalReportDefinitionShouldBeReusableAcrossSnapshots() {
        ReportDefinition<DepartmentCountRow> report = ReportDefinition.natural(
                PojoLensNatural.parse(
                        "show department, count of employees as total "
                                + "where active is true group by department sort by department ascending"
                ),
                DepartmentCountRow.class,
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
        ReportDefinition<Employee> report = ReportDefinition.sql(
                PojoLensSql.parse("where active = true order by salary desc"),
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
        ReportDefinition<Company> report = ReportDefinition.sql(
                PojoLensSql.parse("select * from companies left join employees on id = companyId where title = 'Engineer'"),
                Company.class
        );

        List<Company> rows = report.rows(companies, JoinBindings.of("employees", employees));

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
    }

    @Test
    public void naturalReportDefinitionShouldSupportJoinBindings() {
        List<Company> companies = sampleCompanies();
        List<CompanyEmployee> employees = sampleCompanyEmployees();
        ReportDefinition<Company> report = ReportDefinition.natural(
                PojoLensNatural.parse(
                        "from companies as company join employees as employee "
                                + "on company id equals employee company id "
                                + "show company where employee title is Engineer"
                ),
                Company.class
        );

        List<Company> rows = report.rows(companies, JoinBindings.of("employees", employees));

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
    }

    @Test
    public void naturalReportDefinitionShouldSupportRuntimeVocabularyWhenProjectionAliasesAreExplicit() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("department", "team")
                .build());

        ReportDefinition<DepartmentCountRow> report = ReportDefinition.natural(
                runtime.natural().parse(
                        "show team as department, count of employees as total "
                                + "where active is true group by team sort by department ascending"
                ),
                DepartmentCountRow.class
        );

        List<DepartmentCountRow> rows = report.rows(sampleEmployees());

        assertEquals(List.of("Engineering", "Finance"), rows.stream().map(row -> row.department).toList());
        assertEquals(List.of(2L, 1L), rows.stream().map(row -> row.total).toList());
        assertEquals(List.of("department", "total"), report.schema().names());
    }

    @Test
    public void typedReportDefinitionShouldBeReusableAcrossSnapshots() {
        TypedField<Employee, String> department = TypedField.of("department", String.class);
        TypedField<Employee, Boolean> active = TypedField.of("active", Boolean.class);
        TypedField<DepartmentCountRow, Long> total = TypedField.of("total", Long.class);
        ReportDefinition<DepartmentCountRow> report = ReportDefinition.typed(
                TypedQuery.from(Employee.class)
                        .where(active.eq(true))
                        .groupBy(department)
                        .count(total)
                        .orderBy(department),
                DepartmentCountRow.class,
                ChartSpec.of(ChartType.BAR, "department", "total")
        );

        List<DepartmentCountRow> rows = report.rows(sampleEmployees());
        List<DepartmentCountRow> subsetRows = report.rows(List.of(
                new Employee(10, "X", "Support", 50000, null, true),
                new Employee(11, "Y", "Support", 51000, null, true)
        ));
        ChartData chart = report.chart(sampleEmployees());

        assertEquals("typed:Employee", report.source());
        assertEquals(List.of("department", "total"), report.schema().names());
        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(2L, rows.get(0).total);
        assertEquals(1, subsetRows.size());
        assertEquals("Support", subsetRows.get(0).department);
        assertEquals(2L, subsetRows.get(0).total);
        assertEquals(List.of("Engineering", "Finance"), chart.getLabels());
        assertTrue(report.supportsJoinSources());
    }

    @Test
    public void typedReportDefinitionShouldSupportJoinBindings() {
        TypedField<Company, Integer> companyId = TypedField.of("id", Integer.class);
        TypedField<CompanyEmployee, Integer> employeeCompanyId = TypedField.of("companyId", Integer.class);
        TypedField<Company, String> joinedTitle = TypedField.of("title", String.class);
        ReportDefinition<Company> report = ReportDefinition.typed(
                TypedQuery.from(Company.class)
                        .join("employees", companyId, employeeCompanyId, Join.LEFT_JOIN)
                        .where(joinedTitle.eq("Engineer")),
                Company.class
        );

        List<Company> rows = report.rows(sampleCompanies(), JoinBindings.of("employees", sampleCompanyEmployees()));

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
        assertTrue(report.supportsJoinSources());
    }

    @Test
    public void chartQueryPresetShouldExposeReportDefinitionBridge() {
        ChartQueryPreset<DepartmentCountRow> preset = ChartQueryPresets
                .categoryCounts("department", "total", DepartmentCountRow.class)
                .mapChartSpec(spec -> spec.withTitle("Headcount by Department"));

        ReportDefinition<DepartmentCountRow> report = preset.reportDefinition()
                .mapChartSpec(spec -> spec.withAxisLabels("Department", "Headcount"));
        JoinBindings joinBindings = JoinBindings.of("employees", sampleCompanyEmployees());
        DatasetBundle datasetBundle = DatasetBundle.of(sampleEmployees(), joinBindings);
        List<DepartmentCountRow> rows = report.rows(sampleEmployees());
        ChartData chart = report.chart(sampleEmployees());
        ChartJsPayload payload = report.chartJs(sampleEmployees());

        assertEquals(preset.source(), report.source());
        assertEquals(preset.schema().names(), report.schema().names());
        assertEquals(
                preset.rows(sampleEmployees()).stream().map(row -> row.department + ":" + row.total).toList(),
                report.rows(sampleEmployees()).stream().map(row -> row.department + ":" + row.total).toList()
        );
        assertEquals(
                preset.rows(sampleEmployees(), joinBindings).stream().map(row -> row.department + ":" + row.total).toList(),
                report.rows(sampleEmployees(), joinBindings).stream().map(row -> row.department + ":" + row.total).toList()
        );
        assertEquals(
                preset.rows(datasetBundle).stream().map(row -> row.department + ":" + row.total).toList(),
                report.rows(datasetBundle).stream().map(row -> row.department + ":" + row.total).toList()
        );
        assertEquals(2, rows.size());
        assertEquals(ChartType.BAR, report.chartSpec().type());
        assertEquals(2, chart.getLabels().size());
        assertEquals("Headcount by Department", chart.getTitle());
        assertEquals("Department", chart.getXLabel());
        assertEquals("Headcount", chart.getYLabel());
        assertEquals("bar", payload.type());
        assertEquals(List.of("Engineering", "Finance"), payload.data().labels());
    }

    @Test
    public void reportDefinitionShouldExposeOrderedSchemaMetadata() {
        ReportDefinition<DepartmentCountRow> report = ReportDefinition.sql(
                PojoLensSql.parse("select department, count(*) as total group by department order by department asc"),
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

    public static class PeriodCountRow {
        public String period;
        public long total;

        public PeriodCountRow() {
        }
    }
}



