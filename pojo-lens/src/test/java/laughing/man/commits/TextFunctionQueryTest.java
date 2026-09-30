package laughing.man.commits;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.util.QueryFieldLookupUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-29 slice 1: text and null functions in SQL-like WHERE, SELECT, and HAVING.
 */
class TextFunctionQueryTest {

    @Test
    void textFunctionsNormalizeComparisons() {
        List<Employee> rows = sampleEmployees();

        assertEquals(List.of("Alice"), sql("where lower(name) = 'alice'", rows));
        assertEquals(List.of("Bob"), sql("where upper(department) = 'FINANCE'", rows));
        assertEquals(List.of("Bob", "Dan"), sql("where length(name) = 3 order by name", rows));
        assertEquals(List.of("Alice", "Bob"), sql("where substring(name, 1, 1) in ('A', 'B') order by name", rows));
        assertEquals(List.of("Cara"), sql("where concat(name, '-', department) = 'Cara-Engineering'", rows));
    }

    @Test
    void textOperatorsAcceptTextExpressions() {
        List<Employee> rows = sampleEmployees();

        assertEquals(List.of("Alice", "Cara", "Dan"), sql("where lower(department) like 'eng%' order by name", rows));
        assertEquals(List.of("Cara"), sql("where upper(name) contains 'AR'", rows));
        assertEquals(List.of("Bob", "Cara", "Dan"), sql("where lower(name) not ilike 'a%' order by name", rows));
        assertEquals(List.of("Bob"), sql("where trim(name) matches 'B.*'", rows));
    }

    @Test
    void nullFunctionsHandleMissingValues() {
        List<Employee> rows = withNullDepartment();

        assertEquals(List.of("Eve"), sql("where coalesce(department, 'Unassigned') = 'Unassigned'", rows));
        assertEquals(List.of("Bob", "Eve"), sql("where nullif(department, 'Finance') is null order by name", rows));
        assertEquals(List.of("Eve"), sql("where lower(department) is null", rows));
        assertEquals(List.of("Alice", "Cara", "Dan"), sql("where lower(department) != 'finance' order by name", rows));
    }

    @Test
    void parametersBindAgainstTextExpressions() {
        List<String> rows = names(PojoLensSql.parse("where lower(name) = :name")
                .params(Map.of("name", "cara"))
                .filter(sampleEmployees(), Employee.class));
        assertEquals(List.of("Cara"), rows);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse("where lower(name) = :name")
                        .strictParameterTypes()
                        .params(Map.of("name", 5))
                        .filter(sampleEmployees(), Employee.class));
        assertTrue(error.getMessage().contains(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH), error::getMessage);
        assertTrue(error.getMessage().contains("String value"), error::getMessage);
    }

    @Test
    void selectProjectsTypedTextValues() {
        List<Label> labels = PojoLensSql
                .parse("select name, upper(name) as shout, length(name) as size, "
                        + "concat(name, ' (', department, ')') as label where id = 1")
                .filter(sampleEmployees(), Label.class);

        assertEquals(1, labels.size());
        assertEquals("ALICE", labels.get(0).shout);
        assertEquals(5, labels.get(0).size);
        assertEquals("Alice (Engineering)", labels.get(0).label);
    }

    @Test
    void coalesceOutputKeepsTheFieldType() {
        List<Member> members = List.of(new Member("Ann", 7), new Member("Ben", null));

        List<MemberBonus> projected = PojoLensSql.parse("select name, coalesce(bonus, 0) as bonus order by name")
                .filter(members, MemberBonus.class);
        List<QueryRow> rows = PojoLensSql.parse("select name, coalesce(bonus, 0) as bonus order by name")
                .filter(members, QueryRow.class);

        assertEquals(List.of(7, 0), projected.stream().map(row -> row.bonus).toList());
        assertEquals(Integer.class, QueryFieldLookupUtil.findFieldValue(rows.get(1).getFields(), "bonus").getClass());
    }

    @Test
    void validationRejectsKindMismatches() {
        assertTrue(validationError("where lower(salary) = 'x'").contains("Function LOWER needs text, not a number"));
        assertTrue(validationError("where name * 2 > 1").contains("Operator '*' needs a number, not text"));
        assertTrue(validationError("where salary * 2 contains '1'")
                .contains("only support text operators on text expressions"));
        assertTrue(validationError("select coalesce(salary, name) as mixed").contains("must share one type"));
        assertTrue(validationError("where reverse(name) = 'x'").contains("Unsupported expression function 'reverse'"));
        assertTrue(validationError("where lower(nmae) = 'x'").contains("Unknown field 'nmae'"));
    }

    @Test
    void havingAcceptsTextFunctionsOnGroupedFields() {
        List<DepartmentCount> grouped = PojoLensSql
                .parse("select department, count(*) as total group by department having lower(department) = 'finance'")
                .filter(sampleEmployees(), DepartmentCount.class);

        assertEquals(1, grouped.size());
        assertEquals("Finance", grouped.get(0).department);
    }

    @Test
    void joinedFieldsResolveInsideFunctions() {
        List<Company> companies = PojoLensSql
                .parse("select * from companies left join staff on id = companyId where lower(staff.title) = 'engineer'")
                .filter(sampleCompanies(), JoinBindings.of("staff", sampleCompanyEmployees()), Company.class);

        assertEquals(List.of("Acme"), companies.stream().map(company -> company.name).toList());
    }

    @Test
    void schemaReportsInferredExpressionTypes() {
        TabularSchema schema = PojoLensSql
                .parse("select upper(name) as shout, salary * 2 as doubled, length(name) as size")
                .schema(QueryRow.class);

        assertEquals(String.class, schema.column("shout").type());
        assertEquals(Double.class, schema.column("doubled").type());
        assertEquals(Integer.class, schema.column("size").type());
    }

    @Test
    void numericRegistryFieldsMayUseTextFunctions() {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("nameLength", "length(name)", Integer.class)
                .build();

        List<String> rows = names(PojoLensSql.parse("where nameLength > 3 order by name")
                .computedFields(registry)
                .filter(sampleEmployees(), Employee.class));

        assertEquals(List.of("Alice", "Cara"), rows);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ComputedFieldRegistry.builder().add("lowerName", "lower(name)", Integer.class));
        assertTrue(error.getMessage().contains("returns text"), error::getMessage);
    }

    private static List<String> sql(String query, List<Employee> rows) {
        return names(PojoLensSql.parse(query).filter(rows, Employee.class));
    }

    private static String validationError(String query) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse(query).filter(sampleEmployees(), QueryRow.class));
        return error.getMessage();
    }

    private static List<Employee> withNullDepartment() {
        List<Employee> rows = new ArrayList<>(sampleEmployees());
        rows.add(new Employee(5, "Eve", null, 100000, null, true));
        return rows;
    }

    private static List<String> names(List<Employee> rows) {
        return rows.stream().map(row -> row.name).toList();
    }

    static class Label {
        String name;
        String shout;
        Integer size;
        String label;

        Label() {
        }
    }

    static class Member {
        String name;
        Integer bonus;

        Member() {
        }

        Member(String name, Integer bonus) {
            this.name = name;
            this.bonus = bonus;
        }
    }

    static class MemberBonus {
        String name;
        Integer bonus;

        MemberBonus() {
        }
    }

    static class DepartmentCount {
        String department;
        long total;

        DepartmentCount() {
        }
    }
}
