package laughing.man.commits.publicapi;

import laughing.man.commits.PojoLensNatural;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.natural.NaturalVocabulary;
import laughing.man.commits.natural.NaturalQuery;
import laughing.man.commits.sqllike.SqlParams;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
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
}
