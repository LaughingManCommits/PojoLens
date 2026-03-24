package laughing.man.commits;

import laughing.man.commits.sqllike.SqlParams;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SqlLikeParametersContractTest {

    @Test
    public void parameterizedWhereShouldMatchLiteralQueryResults() {
        List<Employee> source = sampleEmployees();
        String sql = "where department = :dept and salary >= :minSalary and active = :active order by salary desc";

        List<Employee> parameterized = PojoLens
                .parse(sql)
                .params(Map.of(
                        "dept", "Engineering",
                        "minSalary", 120000,
                        "active", true
                ))
                .filter(source, Employee.class);

        List<Employee> literal = PojoLens
                .parse("where department = 'Engineering' and salary >= 120000 and active = true order by salary desc")
                .filter(source, Employee.class);

        assertEquals(names(literal), names(parameterized));
    }

    @Test
    public void parameterizedHavingShouldExecuteWithBoundValues() {
        List<Employee> source = sampleEmployees();

        List<DepartmentCount> rows = PojoLens
                .parse("select department, count(*) as total group by department having total >= :minCount order by total desc")
                .params(Map.of("minCount", 2))
                .filter(source, DepartmentCount.class);

        assertEquals(1, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(3L, rows.get(0).total);
    }

    @Test
    public void parameterizedWhereShouldSupportSqlParamsBuilder() {
        List<Employee> source = sampleEmployees();

        List<Employee> rows = PojoLens
                .parse("where department = :dept and salary >= :minSalary and active = :active order by salary desc")
                .params(SqlParams.builder()
                        .put("dept", "Engineering")
                        .put("minSalary", 120000)
                        .put("active", true)
                        .build())
                .filter(source, Employee.class);

        assertEquals(Arrays.asList("Cara", "Alice"), names(rows));
    }

    @Test
    public void parameterizedQueryShouldSupportDateAndNullValues() {
        List<Employee> source = sampleEmployees();
        Date hiredAt = source.get(0).hireDate;

        List<Employee> dateRows = PojoLens
                .parse("where hireDate = :hiredAt")
                .params(Map.of("hiredAt", hiredAt))
                .filter(source, Employee.class);
        assertEquals(source.size(), dateRows.size());

        List<NullableBean> nullableRows = Arrays.asList(
                new NullableBean(1, null),
                new NullableBean(2, "x")
        );
        Map<String, Object> nullParams = new HashMap<>();
        nullParams.put("tag", null);
        List<NullableBean> nullMatch = PojoLens
                .parse("where tag = :tag")
                .params(nullParams)
                .filter(nullableRows, NullableBean.class);
        assertEquals(1, nullMatch.size());
        assertEquals(1, nullMatch.get(0).id);
    }

    @Test
    public void missingParameterShouldFailDeterministically() {
        try {
            PojoLens.parse("where salary >= :min and department = :dept")
                    .params(Map.of("min", 100000));
            fail("Expected missing parameter failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Missing SQL-like parameter(s)"));
            assertTrue(ex.getMessage().contains("dept"));
        }
    }

    @Test
    public void unknownParameterShouldFailDeterministically() {
        try {
            PojoLens.parse("where salary >= :min")
                    .params(Map.of("min", 100000, "extra", 1));
            fail("Expected unknown parameter failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown SQL-like parameter(s)"));
            assertTrue(ex.getMessage().contains("extra"));
        }
    }

    @Test
    public void sqlParamsBuilderShouldReuseMapValidationForUnknownParameters() {
        try {
            PojoLens.parse("where salary >= :min")
                    .params(SqlParams.builder()
                            .put("min", 100000)
                            .put("extra", 1)
                            .build());
            fail("Expected unknown parameter failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown SQL-like parameter(s)"));
            assertTrue(ex.getMessage().contains("extra"));
        }
    }

    @Test
    public void executingWithUnresolvedParametersShouldFail() {
        try {
            PojoLens.parse("where salary >= :min").filter(sampleEmployees(), Employee.class);
            fail("Expected unresolved parameter failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("unresolved SQL-like parameter"));
        }
    }

    private static List<String> names(List<Employee> rows) {
        return rows.stream().map(r -> r.name).collect(Collectors.toList());
    }

    public static class NullableBean {
        public int id;
        public String tag;

        public NullableBean() {
        }

        public NullableBean(int id, String tag) {
            this.id = id;
            this.tag = tag;
        }
    }
}

