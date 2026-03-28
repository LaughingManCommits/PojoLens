package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SqlLikeJoinBindingsContractTest {

    @Test
    public void joinBindingsFromMapShouldMatchDirectTypedFilterExecution() {
        List<Company> companies = sampleCompanies();
        List<CompanyEmployee> employees = sampleCompanyEmployees();
        String sql = "select * from companies left join employees on id = companyId where title = 'Engineer'";

        Map<String, List<?>> joinSources = new HashMap<>();
        joinSources.put("employees", employees);
        JoinBindings adaptedBindings = JoinBindings.from(joinSources);

        JoinBindings joinBindings = JoinBindings.of("employees", employees);
        List<Company> adaptedRows = PojoLensSql.parse(sql).filter(companies, adaptedBindings, Company.class);
        List<Company> typedRows = PojoLensSql.parse(sql).filter(companies, joinBindings, Company.class);

        assertEquals(ids(adaptedRows), ids(typedRows));
    }

    @Test
    public void typedJoinBindingsShouldSupportBindTypedFlow() {
        List<Company> companies = sampleCompanies();
        JoinBindings joinBindings = JoinBindings.of("employees", sampleCompanyEmployees());

        List<Company> rows = PojoLensSql.parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .bindTyped(companies, Company.class, joinBindings)
                .filter();

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
    }

    @Test
    public void typedJoinBindingsShouldSupportChartFlow() {
        List<Company> companies = sampleCompanies();
        JoinBindings joinBindings = JoinBindings.of("employees", sampleCompanyEmployees());

        ChartData chart = PojoLensSql.parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .chart(companies, joinBindings, Company.class, ChartSpec.of(ChartType.BAR, "id", "id"));

        assertEquals(1, chart.getLabels().size());
        assertEquals("1", chart.getLabels().get(0));
    }

    @Test
    public void joinBindingsBuilderShouldRejectDuplicateSources() {
        try {
            JoinBindings.builder()
                    .add("employees", sampleCompanyEmployees())
                    .add("employees", sampleCompanyEmployees());
            fail("Expected duplicate source binding failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Duplicate JOIN source binding for 'employees'"));
        }
    }

    @Test
    public void joinBindingsBuilderShouldRejectBlankSourceName() {
        try {
            JoinBindings.builder().add("   ", sampleCompanyEmployees());
            fail("Expected blank source validation failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("sourceName must not be null/blank"));
        }
    }

    @Test
    public void missingJoinBindingShouldFailWithTypedBindings() {
        List<Company> companies = sampleCompanies();
        try {
            PojoLensSql.parse("select * from companies left join employees on id = companyId")
                    .filter(companies, JoinBindings.empty(), Company.class);
            fail("Expected missing join binding failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Missing JOIN source binding for 'employees'"));
        }
    }

    private static List<Integer> ids(List<Company> rows) {
        return rows.stream().map(r -> r.id).collect(Collectors.toList());
    }
}







