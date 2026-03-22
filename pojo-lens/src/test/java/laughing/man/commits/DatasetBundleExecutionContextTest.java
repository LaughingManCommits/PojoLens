package laughing.man.commits;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartQueryPreset;
import laughing.man.commits.chart.ChartQueryPresets;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DatasetBundleExecutionContextTest {

    @Test
    public void sqlLikeQueryShouldSupportBundleFilterBindChartAndExplain() {
        DatasetBundle bundle = PojoLens.bundle(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees())
        );

        SqlLikeQuery joinQuery = PojoLens
                .parse("select * from companies left join employees on id = companyId where title = 'Engineer'");
        List<Company> rows = joinQuery.filter(bundle, Company.class);
        List<Company> typedRows = joinQuery.bindTyped(bundle, Company.class).filter();

        SqlLikeQuery groupedQuery = PojoLens.parse(
                "select name, count(*) as total "
                        + "from companies left join employees on id = companyId "
                        + "group by name order by name asc"
        );
        ChartData chart = groupedQuery.chart(
                bundle,
                CompanyTotalRow.class,
                ChartSpec.of(ChartType.BAR, "name", "total")
        );
        Map<String, Object> explain = groupedQuery.explain(bundle, CompanyTotalRow.class);

        assertEquals(1, rows.size());
        assertEquals(1, typedRows.size());
        assertEquals("Acme", rows.get(0).name);
        assertEquals(List.of("Acme", "Globex"), chart.getLabels());
        assertTrue(explain.containsKey("stageRowCounts"));
        assertEquals(1, ((Map<?, ?>) explain.get("joinSourceBindings")).size());
    }

    @Test
    public void reportDefinitionsAndChartPresetsShouldSupportBundleExecution() {
        DatasetBundle joinBundle = PojoLens.bundle(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees())
        );
        ReportDefinition<Company> report = PojoLens.report(
                PojoLens.parse("select * from companies left join employees on id = companyId where title = 'Engineer'"),
                Company.class
        );

        List<Company> reportRows = report.rows(joinBundle);

        DatasetBundle employeeBundle = PojoLens.bundle(sampleEmployees());
        ChartQueryPreset<DepartmentCountRow> preset = ChartQueryPresets
                .categoryCounts("department", "total", DepartmentCountRow.class);
        List<DepartmentCountRow> presetRows = preset.rows(employeeBundle);
        ChartData presetChart = preset.chart(employeeBundle);

        assertEquals(1, reportRows.size());
        assertEquals("Acme", reportRows.get(0).name);
        assertEquals(2, presetRows.size());
        assertEquals(2, presetChart.getLabels().size());
    }

    @Test
    public void datasetBundleShouldSnapshotPrimaryAndJoinSourceLists() {
        List<Company> companies = new ArrayList<>(sampleCompanies());
        List<CompanyEmployee> employees = new ArrayList<>(sampleCompanyEmployees());
        DatasetBundle bundle = DatasetBundle.builder(companies)
                .add("employees", employees)
                .build();

        companies.clear();
        employees.clear();

        List<Company> rows = PojoLens
                .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .filter(bundle, Company.class);

        assertEquals(1, rows.size());
        assertEquals("Acme", rows.get(0).name);
    }

    @Test
    public void datasetBundleWithoutNamedSourcesShouldPreserveExplicitMissingSourceErrors() {
        DatasetBundle bundle = PojoLens.bundle(sampleCompanies());

        try {
            PojoLens.parse("where id in (select companyId from employees where title = 'Engineer')")
                    .filter(bundle, Company.class);
            fail("Expected missing subquery source binding error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Missing subquery source binding for 'employees'"));
        }
    }

    public static class CompanyTotalRow {
        public String name;
        public long total;

        public CompanyTotalRow() {
        }
    }

    public static class DepartmentCountRow {
        public String department;
        public long total;

        public DepartmentCountRow() {
        }
    }
}

