package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;

import laughing.man.commits.PojoLens;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SqlLikeTemplateContractTest {

    @Test
    public void templateShouldSupportRepeatedExecutionWithDeterministicSchemaValidation() {
        SqlLikeTemplate template = PojoLensSql.template(
                "where department = :dept and salary >= :minSalary and active = :active order by salary desc",
                "dept",
                "minSalary",
                "active"
        );

        List<Employee> engineering = template
                .bind(Map.of("dept", "Engineering", "minSalary", 120000, "active", true))
                .filter(sampleEmployees(), Employee.class);

        List<Employee> finance = template
                .bind(SqlParams.builder()
                        .put("dept", "Finance")
                        .put("minSalary", 80000)
                        .put("active", true)
                        .build())
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), names(engineering));
        assertEquals(List.of("Bob"), names(finance));
    }

    @Test
    public void templateShouldFailForMissingSchemaParameters() {
        SqlLikeTemplate template = PojoLensSql.template(
                "where department = :dept and salary >= :minSalary",
                "dept",
                "minSalary"
        );
        try {
            template.bind(Map.of("dept", "Engineering"));
            fail("Expected missing parameter failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Missing SQL-like template parameter(s)"));
            assertTrue(ex.getMessage().contains("minSalary"));
        }
    }

    @Test
    public void templateShouldFailForUnknownSchemaParameters() {
        SqlLikeTemplate template = PojoLensSql.template(
                "where department = :dept and salary >= :minSalary",
                "dept",
                "minSalary"
        );
        try {
            template.bind(Map.of("dept", "Engineering", "minSalary", 100000, "active", true));
            fail("Expected unknown parameter failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown SQL-like template parameter(s)"));
            assertTrue(ex.getMessage().contains("active"));
        }
    }

    @Test
    public void templateCreationShouldFailWhenSchemaDoesNotMatchQueryParameters() {
        try {
            PojoLensSql.template("where department = :dept and salary >= :minSalary", "dept");
            fail("Expected template schema mismatch failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Template schema missing SQL-like parameter(s)"));
            assertTrue(ex.getMessage().contains("minSalary"));
        }

        try {
            PojoLensSql.template("where salary >= :minSalary", "minSalary", "dept");
            fail("Expected template schema mismatch failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Template schema declares unknown SQL-like parameter(s)"));
            assertTrue(ex.getMessage().contains("dept"));
        }

        try {
            PojoLensSql.template("where salary >= :minSalary", "minSalary", "minSalary");
            fail("Expected duplicate schema entry failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Duplicate SQL-like template parameter schema entry"));
            assertTrue(ex.getMessage().contains("minSalary"));
        }
    }

    @Test
    public void runtimeTemplateEntryPointShouldReuseRuntimeParsingCache() {
        PojoLensRuntime runtime = PojoLens.newRuntime();
        SqlLikeTemplate template = runtime.template(
                "where department = :dept and active = :active order by salary desc",
                "dept",
                "active"
        );

        List<Employee> rows = template
                .bind(Map.of("dept", "Engineering", "active", true))
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), names(rows));
    }

    private static List<String> names(List<Employee> rows) {
        return rows.stream().map(r -> r.name).collect(Collectors.toList());
    }
}




