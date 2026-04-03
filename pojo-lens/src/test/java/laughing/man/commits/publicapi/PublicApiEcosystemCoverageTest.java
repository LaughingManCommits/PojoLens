package laughing.man.commits.publicapi;

import laughing.man.commits.PojoLensCore;
import laughing.man.commits.PojoLensNatural;
import laughing.man.commits.PojoLensSql;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.PojoLensRuntimePreset;
import laughing.man.commits.chart.ChartQueryPreset;
import laughing.man.commits.chart.ChartQueryPresets;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.snapshot.SnapshotComparison;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.telemetry.QueryTelemetryEvent;
import laughing.man.commits.testing.FluentSqlLikeParity;
import laughing.man.commits.testing.QueryRegressionFixture;
import laughing.man.commits.testing.QuerySnapshotFixture;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.PublicApiModels.ComputedSalaryRow;
import laughing.man.commits.testutil.PublicApiModels.StatsRow;
import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.stats.StatsTable;
import laughing.man.commits.stats.StatsViewPreset;
import laughing.man.commits.stats.StatsViewPresets;
import laughing.man.commits.metamodel.FieldMetamodel;
import laughing.man.commits.metamodel.FieldMetamodelGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublicApiEcosystemCoverageTest extends AbstractPublicApiCoverageTest {

    @Test
    public void runtimePolicyPresetsShouldBeUsableFromPublicApi() {
        PojoLensRuntime devRuntime = PojoLensRuntime.ofPreset(PojoLensRuntimePreset.DEV);
        assertTrue(devRuntime.isStrictParameterTypes());
        assertTrue(devRuntime.isLintMode());

        PojoLensRuntime prodRuntime = new PojoLensRuntime().applyPreset(PojoLensRuntimePreset.PROD);
        assertFalse(prodRuntime.isStrictParameterTypes());
        assertFalse(prodRuntime.isLintMode());
        assertTrue(prodRuntime.sqlLikeCache().isEnabled());
    }

    @Test
    public void parityHelperShouldBeUsableFromPublicApi() {
        FluentSqlLikeParity.assertUnorderedEquals(List.of("a", "b"), List.of("b", "a"));

        QuerySnapshotFixture snapshot = QuerySnapshotFixture.of("employees", sampleEmployees());
        FluentSqlLikeParity.assertUnorderedEquals(
                snapshot,
                StatsRow.class,
                builder -> builder.addGroup("department").addCount("total"),
                PojoLensSql.parse("select department, count(*) as total group by department"),
                row -> row.department + ":" + row.total
        );
    }

    @Test
    public void fieldMetamodelGeneratorShouldBeUsableFromPublicApi() {
        FieldMetamodel metamodel = FieldMetamodelGenerator.generate(Employee.class);
        assertEquals("EmployeeFields", metamodel.simpleName());
        assertTrue(metamodel.fieldNames().contains("department"));
        assertEquals("department", metamodel.constants().get("DEPARTMENT"));
    }

    @Test
    public void chartQueryPresetsShouldBeUsableFromPublicApi() {
        ChartQueryPreset<StatsRow> preset = ChartQueryPresets
                .categoryCounts("department", "total", StatsRow.class);

        assertEquals(ChartType.BAR, preset.chartSpec().type());
        assertTrue(preset.source().contains("count(*) as total"));
        assertEquals(2, preset.rows(sampleEmployees()).size());
    }

    @Test
    public void statsViewPresetsShouldBeUsableFromPublicApi() {
        StatsViewPreset<StatsRow> preset = StatsViewPresets.by("department", StatsRow.class);
        StatsTable<StatsRow> table = preset.table(sampleEmployees());

        assertEquals(2, table.rows().size());
        assertEquals(List.of("department", "total"), table.schema().names());
        assertEquals(4L, ((Number) table.totals().get("total")).longValue());
    }

    @Test
    public void reportDefinitionsShouldBeUsableFromPublicApi() {
        ReportDefinition<StatsRow> sqlReport = ReportDefinition.sql(
                PojoLensSql.parse("select department, count(*) as total group by department order by department asc"),
                StatsRow.class
        );
        assertEquals(2, sqlReport.rows(sampleEmployees()).size());

        ReportDefinition<StatsRow> naturalReport = ReportDefinition.natural(
                PojoLensNatural.parse(
                        "show department, count of employees as total "
                                + "group by department sort by department ascending"
                ),
                StatsRow.class
        );
        assertEquals(2, naturalReport.rows(sampleEmployees()).size());
        assertEquals("show department, count of employees as total group by department sort by department ascending",
                naturalReport.source());

        ReportDefinition<StatsRow> fluentReport = ReportDefinition.fluent(
                StatsRow.class,
                builder -> builder.addGroup("department").addCount("total")
        );
        assertEquals(2, fluentReport.rows(sampleEmployees()).size());
        assertEquals("fluent", fluentReport.source());
    }

    @Test
    public void datasetBundleFactoriesShouldBeUsableFromPublicApi() {
        DatasetBundle bundle = DatasetBundle.of(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees())
        );

        List<Company> rows = PojoLensSql.parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .filter(bundle, Company.class);

        assertEquals(1, rows.size());
        assertTrue(bundle.hasJoinSources());
        assertEquals(1, bundle.joinSourceCount());
    }

    @Test
    public void telemetryHooksShouldBeUsableFromPublicApi() {
        List<QueryTelemetryEvent> events = new ArrayList<>();

        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setTelemetryListener(events::add);
        runtime.parse("where salary >= 100000").filter(sampleEmployees(), Employee.class);
        assertFalse(events.isEmpty());

        events.clear();
        PojoLensCore.newQueryBuilder(sampleEmployees())
                .telemetry(events::add)
                .addRule("active", true, Clauses.EQUAL)
                .initFilter()
                .filter(Employee.class);
        assertFalse(events.isEmpty());
    }

    @Test
    public void computedFieldRegistryShouldBeUsableFromPublicApi() {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .build();

        List<ComputedSalaryRow> rows = PojoLensSql.parse("select name, adjustedSalary where adjustedSalary >= 120000 order by adjustedSalary desc")
                .computedFields(registry)
                .filter(sampleEmployees(), ComputedSalaryRow.class);

        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setComputedFieldRegistry(registry);
        assertEquals("adjustedSalary", runtime.getComputedFieldRegistry().get("adjustedSalary").name());
        assertEquals(3, rows.size());
    }

    @Test
    public void snapshotComparisonShouldBeUsableFromPublicApi() {
        List<Employee> employees = sampleEmployees();
        SnapshotComparison<Employee, Integer> comparison = SnapshotComparison
                .builder(employees, employees)
                .byKey(employee -> employee.id);

        assertEquals(4, comparison.rows().size());
        assertEquals(4, comparison.summary().unchangedCount());
    }

    @Test
    public void timeBucketPresetShouldBeUsableFromPublicApi() {
        TimeBucketPreset preset = TimeBucketPreset.week()
                .withZone("Europe/Amsterdam")
                .withWeekStart("sunday");

        assertEquals("WEEK", preset.bucket().name());
        assertTrue(PojoLensCore.newQueryBuilder(sampleEmployees())
                .addTimeBucket("hireDate", preset, "period")
                .addCount("total")
                .explain()
                .get("timeBuckets")
                .toString()
                .contains("Europe/Amsterdam"));
    }

    @Test
    public void tabularSchemaMetadataShouldBeUsableFromPublicApi() {
        TabularSchema fluentSchema = PojoLensCore.newQueryBuilder(sampleEmployees())
                .addGroup("department")
                .addCount("total")
                .schema(StatsRow.class);

        TabularSchema sqlSchema = PojoLensSql.parse("select department, count(*) as total group by department")
                .schema(StatsRow.class);

        ReportDefinition<StatsRow> report = ReportDefinition.sql(
                PojoLensSql.parse("select department, count(*) as total group by department"),
                StatsRow.class
        );

        assertEquals(List.of("department", "total"), fluentSchema.names());
        assertEquals(List.of("department", "total"), sqlSchema.names());
        assertEquals(List.of("department", "total"), report.schema().names());
        assertEquals("metric:COUNT", report.schema().column("total").formatHint());
    }

    @Test
    public void regressionFixturesShouldBeUsableFromPublicApi() {
        QuerySnapshotFixture snapshot = QuerySnapshotFixture.of("employees-regression", sampleEmployees());

        QueryRegressionFixture<StatsRow> fixture = QueryRegressionFixture.sql(
                snapshot,
                PojoLensSql.parse("select department, count(*) as total group by department order by department asc"),
                StatsRow.class
        );

        fixture
                .assertRowCount(2)
                .assertOrderedRows(row -> row.department + ":" + row.total,
                        "Engineering:3",
                        "Finance:1");
    }
}






