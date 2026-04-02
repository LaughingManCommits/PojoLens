package laughing.man.commits.natural;

import laughing.man.commits.PojoLensNatural;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NaturalQueryContractTest {

    @Test
    public void shouldExecuteWildcardFilterSortAndLimit() {
        List<Employee> rows = PojoLensNatural
                .parse("show employees where active is true sort by salary descending limit 2")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void shouldExecuteAliasedProjectionWithParameters() {
        List<EmployeeSummary> rows = PojoLensNatural
                .parse("show name as employee name, salary as annual salary "
                        + "where department is :dept and salary is at least :minSalary sort by salary ascending")
                .params(Map.of("dept", "Engineering", "minSalary", 120000))
                .filter(sampleEmployees(), EmployeeSummary.class);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).employeeName);
        assertEquals(120000, rows.get(0).annualSalary);
        assertEquals("Cara", rows.get(1).employeeName);
        assertEquals(130000, rows.get(1).annualSalary);
    }

    @Test
    public void explainShouldExposeNaturalTypeAndEquivalentSqlLike() {
        Map<String, Object> explain = PojoLensNatural
                .parse("show employees where active is true sort by salary descending limit 2")
                .explain(sampleEmployees(), Employee.class);

        assertEquals("natural", explain.get("type"));
        assertEquals("show employees where active is true sort by salary descending limit 2", explain.get("source"));
        assertEquals("select * where active = true order by salary desc limit 2", explain.get("normalizedQuery"));
        assertEquals(explain.get("normalizedQuery"), explain.get("equivalentSqlLike"));
    }

    @Test
    public void runtimeNaturalEntryPointShouldApplyTelemetryAndRemainChartCapable() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        NaturalQuery query = runtime.natural().parse("show salary, name where active is true sort by salary descending limit 2");

        ChartData chart = query.chart(
                sampleEmployees(),
                Employee.class,
                ChartSpec.of(ChartType.BAR, "name", "salary")
        );

        assertEquals(2, chart.getLabels().size());
        assertTrue(query.isStrictParameterTypesEnabled() == runtime.isStrictParameterTypes());
    }
}
