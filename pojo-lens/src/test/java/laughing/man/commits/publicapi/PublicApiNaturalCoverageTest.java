package laughing.man.commits.publicapi;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.PojoLensNatural;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.natural.NaturalVocabulary;
import laughing.man.commits.natural.NaturalQuery;
import laughing.man.commits.natural.NaturalTemplate;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlParams;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import laughing.man.commits.testutil.TimeBucketTestFixtures.DepartmentPeriodAgg;
import laughing.man.commits.testutil.WindowTestFixtures.WindowRowNumberProjection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static laughing.man.commits.testutil.TimeBucketTestFixtures.sampleRows;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublicApiNaturalCoverageTest extends AbstractPublicApiCoverageTest {

    @Test
    public void naturalQueryOfShouldNormalizeAndExposeSourceAndEquivalentSqlLike() {
        NaturalQuery query = NaturalQuery.of("  show employees where active is true limit 2  ");
        assertEquals("show employees where active is true limit 2", query.source());
        assertEquals("select * where active = true limit 2", query.equivalentSqlLike());
    }

    @Test
    public void naturalRuntimeShouldExposeConfiguredQueryControls() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setStrictParameterTypes(true);
        runtime.setLintMode(true);
        NaturalVocabulary vocabulary = NaturalVocabulary.builder().field("salary", "pay").build();
        runtime.setNaturalVocabulary(vocabulary);

        NaturalQuery query = runtime.natural().parse("show employees where salary is at least :minSalary");
        assertTrue(query.isStrictParameterTypesEnabled());
        assertTrue(query.isLintModeEnabled());
        assertEquals(vocabulary, runtime.getNaturalVocabulary());
    }

    @Test
    public void naturalQueryShouldSupportParamsFilterStreamAndExplainFromPublicApi() {
        NaturalQuery query = PojoLensNatural.parse(
                "show name as employee name, salary as annual salary "
                        + "where department is :dept and salary is at least :minSalary sort by salary ascending"
        );

        List<EmployeeSummary> rows = query
                .params(SqlParams.builder().put("dept", "Engineering").put("minSalary", 120000).build())
                .filter(sampleEmployees(), EmployeeSummary.class);
        assertEquals(2, rows.size());

        List<String> names = query
                .params(Map.of("dept", "Engineering", "minSalary", 120000))
                .stream(sampleEmployees(), EmployeeSummary.class)
                .map(row -> row.employeeName)
                .toList();
        assertEquals(List.of("Alice", "Cara"), names);

        Map<String, Object> explain = query
                .params(Map.of("dept", "Engineering", "minSalary", 120000))
                .explain(sampleEmployees(), EmployeeSummary.class);
        assertEquals("natural", explain.get("type"));
        assertEquals(query.equivalentSqlLike(), explain.get("equivalentSqlLike"));
    }

    @Test
    public void naturalQueryShouldRemainChartCapableFromPublicApi() {
        var chart = PojoLensNatural.parse("show name, salary where active is true sort by salary descending limit 2")
                .chart(sampleEmployees(), Employee.class, ChartSpec.of(ChartType.BAR, "name", "salary"));
        assertEquals(2, chart.getLabels().size());
    }

    @Test
    public void naturalQueryShouldSupportChartPhraseInferenceFromPublicApi() {
        var chart = PojoLensNatural.parse(
                        "show department, count of employees as total "
                                + "where active is true group by department sort by total descending as bar chart"
                )
                .chart(sampleEmployees(), DepartmentCount.class);
        assertEquals(ChartType.BAR, chart.getType());
        assertEquals(List.of("Engineering", "Finance"), chart.getLabels());
    }

    @Test
    public void runtimeNaturalVocabularyShouldBeUsableFromPublicApi() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("salary", "annual pay")
                .field("department", "team")
                .build());

        List<Employee> rows = runtime.natural()
                .parse("show employees where team is Engineering sort by annual pay descending limit 2")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void naturalTemplateShouldSupportPublicApiBindingAndSchemaValidation() {
        NaturalTemplate template = PojoLensNatural.template(
                "show employees where department is :dept and salary is at least :minSalary sort by salary descending",
                "dept",
                "minSalary"
        );

        List<Employee> rows = template
                .bind(SqlParams.builder().put("dept", "Engineering").put("minSalary", 120000).build())
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
        assertThrows(IllegalArgumentException.class, () -> template.bind(Map.of("dept", "Engineering")));
    }

    @Test
    public void naturalQueryShouldPromoteToReportDefinitionFromPublicApi() {
        ReportDefinition<DepartmentCount> report = ReportDefinition.natural(
                PojoLensNatural.parse(
                        "show department, count of employees as total "
                                + "where active is true group by department sort by department ascending"
                ),
                DepartmentCount.class,
                ChartSpec.of(ChartType.BAR, "department", "total")
        );

        List<DepartmentCount> rows = report.rows(sampleEmployees());
        assertEquals(List.of("Engineering", "Finance"), rows.stream().map(row -> row.department).toList());
        assertEquals(ChartType.BAR, report.chart(sampleEmployees()).getType());
    }

    @Test
    public void runtimeNaturalTemplateShouldCarryComputedFieldRegistryFromPublicApi() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setComputedFieldRegistry(ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .build());

        List<ComputedSalaryRow> rows = runtime.natural()
                .template(
                        "show name, adjusted salary "
                                + "where adjusted salary is at least :minSalary sort by adjusted salary descending",
                        "minSalary"
                )
                .bind(Map.of("minSalary", 130000.0))
                .filter(sampleEmployees(), ComputedSalaryRow.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
        assertEquals(List.of(143000.0, 132000.0), rows.stream().map(row -> row.adjustedSalary).toList());
    }

    @Test
    public void naturalQueryShouldSupportGroupedAggregatesFromPublicApi() {
        List<DepartmentCount> rows = PojoLensNatural.parse(
                        "show department, count of employees as total "
                                + "where active is true group by department having total is at least 2 sort by total descending"
                )
                .filter(sampleEmployees(), DepartmentCount.class);

        assertEquals(1, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(2L, rows.get(0).total);
    }

    @Test
    public void naturalQueryShouldSupportTimeBucketPhrasesFromPublicApi() {
        List<DepartmentPeriodAgg> rows = PojoLensNatural.parse(
                        "show department, bucket hire date by month as period, count of employees as total "
                                + "group by department, period sort by period ascending"
                )
                .filter(sampleRows(), DepartmentPeriodAgg.class);

        assertEquals(3, rows.size());
        assertEquals("2025-01", rows.get(0).period);
    }

    @Test
    public void naturalQueryShouldSupportJoinBindingsAndDatasetBundlesFromPublicApi() {
        NaturalQuery query = PojoLensNatural.parse(
                "from companies as company join employees as employee "
                        + "on company id equals employee company id "
                        + "show company where employee title is Engineer"
        );

        List<Company> rows = query.filter(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees()),
                Company.class
        );
        assertEquals(List.of("Acme"), rows.stream().map(row -> row.name).toList());

        DatasetBundle bundle = DatasetBundle.of(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees())
        );
        List<Company> typedRows = query.bindTyped(bundle, Company.class).filter();
        assertEquals(List.of("Acme"), typedRows.stream().map(row -> row.name).toList());
    }

    @Test
    public void naturalQueryShouldSupportWindowQualifyAndBoundExecutionFromPublicApi() {
        List<WindowRowNumberProjection> rows = PojoLensNatural.parse(
                        "show department as dept, name, salary, "
                                + "row number by department ordered by salary descending then id ascending as rn "
                                + "where active is true qualify rn is at most 1 sort by dept ascending"
                )
                .bindTyped(sampleEmployees(), WindowRowNumberProjection.class)
                .filter();

        assertEquals(2, rows.size());
        assertEquals(List.of("Cara", "Bob"), rows.stream().map(row -> row.name).toList());
        assertEquals(List.of(1L, 1L), rows.stream().map(row -> row.rn).toList());
    }

    public static class ComputedSalaryRow {
        public String name;
        public double adjustedSalary;

        public ComputedSalaryRow() {
        }
    }
}
